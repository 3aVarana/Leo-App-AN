package com.victorarana.leo.features.quiz

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorarana.leo.data.repositories.ExerciseRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.wordCount
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.components.OptionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration

private const val TAG = "QuizViewModel"

/**
 * What the quiz screens draw from: the Swift ViewModel's published properties plus its computed ones. The
 * derived values ([optionState], [dotState], [correctCount], ...) are members, so a screen reads one snapshot.
 */
data class QuizUiState(
    /** The number of texts in the prepared round, set when the round is prepared. */
    val exerciseCount: Int = RoundSettings.DEFAULT_EXERCISE_COUNT,
    val phase: QuizViewModel.Phase = QuizViewModel.Phase.Welcome,
    val currentIndex: Int = 0,
    val currentExercise: Exercise? = null,
    val selectedOption: Int? = null,
    /**
     * Whether the reader is still on the passage of the current exercise. Its question shows, and can be answered,
     * once they finish reading.
     */
    val isReading: Boolean = false,
    /**
     * The answers of the round started last, kept after it ends, and when the settings change, so Results and
     * Review still show them.
     */
    val answers: List<RoundAnswer> = emptyList(),
    /** The age group of the round started last. */
    val roundAgeGroup: AgeGroup? = null,
    /** The topic of the exercise being generated, updated when the generator falls back to a spare one. */
    val writingTopicName: String? = null,
    /** The main topic of each exercise of the prepared round, without repeats, in round order. */
    val plannedTopicNames: List<String> = emptyList(),
    /** The main topic of the first exercise that couldn't be generated, for the failure screen. */
    val failedTopicName: String? = null,
) {
    val correctCount: Int get() = answers.count { it.isCorrect }

    val misses: List<RoundAnswer> get() = answers.filterNot { it.isCorrect }

    val isLastExercise: Boolean get() = currentIndex == exerciseCount - 1

    /** How long the reader gets to read the current passage, for the reading timer. */
    val readingTime: Duration?
        get() {
            val exercise = currentExercise ?: return null
            val ageGroup = roundAgeGroup ?: return null
            return ageGroup.readingTime(exercise.passage.wordCount)
        }

    /** Whether the picked answer is right. `null` before answering. */
    val isAnswerCorrect: Boolean?
        get() {
            val selected = selectedOption ?: return null
            val exercise = currentExercise ?: return null
            return selected == exercise.correctIndex
        }

    /** How the option at [index] of the current exercise is shown. */
    fun optionState(index: Int): OptionState {
        val selected = selectedOption
        val exercise = currentExercise
        return when {
            selected == null || exercise == null -> OptionState.Idle
            index == exercise.correctIndex -> OptionState.Correct
            index == selected -> OptionState.Incorrect
            else -> OptionState.Dimmed
        }
    }

    /**
     * How the dot of the exercise at [index] is drawn. Dots of answered exercises are neutral; the current one shows
     * the verdict only while its feedback is on screen.
     */
    fun dotState(index: Int): DotState =
        when (phase) {
            QuizViewModel.Phase.Welcome -> DotState.Upcoming
            QuizViewModel.Phase.Finished -> DotState.Done
            QuizViewModel.Phase.Loading, QuizViewModel.Phase.Answering, QuizViewModel.Phase.Failed ->
                when {
                    index < currentIndex -> DotState.Done
                    index > currentIndex -> DotState.Upcoming
                    else -> currentDotState()
                }
        }

    private fun currentDotState(): DotState =
        when (phase) {
            QuizViewModel.Phase.Failed -> DotState.Failed
            QuizViewModel.Phase.Answering ->
                when (isAnswerCorrect) {
                    true -> DotState.CurrentCorrect
                    false -> DotState.CurrentMissed
                    null -> DotState.Current
                }
            QuizViewModel.Phase.Welcome, QuizViewModel.Phase.Loading, QuizViewModel.Phase.Finished -> DotState.Current
        }

    /** The dots of the whole round, for the screens' progress row. */
    val dots: List<DotState> get() = List(exerciseCount, ::dotState)
}

/**
 * Drives a round of exercises. A round's exercises are generated one after another in the background, starting
 * before the student taps Start, so they are ready when needed. An exercise that can't be generated is replaced by
 * one with the same skill and other topics, up to [MAX_REPLACEMENTS] times per round, and the reader gets exercises
 * in the order they're ready. Port of `QuizViewModel`; it lives in the Activity's store (phase-6 D1).
 *
 * @param makeRepository Makes the repository for each round.
 * @param now When a round finishes, for `RoundRecord.finishedAt`.
 * @param random Shuffles the plan and the replacement topics.
 * @param onRoundFinished Called once, with the round just finished, before the phase becomes [Phase.Finished].
 */
@Suppress("TooManyFunctions") // The Swift ViewModel's whole surface: one function per reader action.
class QuizViewModel(
    private val makeRepository: (RoundSettings) -> ExerciseRepository,
    private val now: () -> Instant = Instant::now,
    private val random: Random = Random.Default,
    private val onRoundFinished: (RoundRecord) -> Unit = {},
) : ViewModel() {
    enum class Phase { Welcome, Loading, Answering, Finished, Failed }

    /** The topics to try for one exercise, main topic first, and the skill its question targets. */
    data class PlanItem(
        val topics: List<RoundTopic>,
        val skill: ComprehensionSkill,
    )

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var exerciseCount = RoundSettings.DEFAULT_EXERCISE_COUNT
    private var phase = Phase.Welcome
    private var currentIndex = 0
    private var currentExercise: Exercise? = null
    private var selectedOption: Int? = null
    private var isReading = false
    private var answers: List<RoundAnswer> = emptyList()
    private var roundAgeGroup: AgeGroup? = null
    private var writingTopicName: String? = null

    private var settings: RoundSettings? = null
    private var plan: List<PlanItem> = emptyList()
    private var repository: ExerciseRepository? = null

    /** Exercises still to generate, in order. The first one is being generated. */
    private var queue: List<PlanItem> = emptyList()

    /** Generated exercises, in the order they were ready. The reader's is at [currentIndex]. */
    private var exercises: List<Exercise> = emptyList()

    /**
     * Exercises that failed once the round's replacements ran out, kept for a manual retry. [exercises], [queue]
     * and [failedJobs] always add up to [exerciseCount].
     */
    private var failedJobs: List<PlanItem> = emptyList()

    /** How many more failed exercises this round replaces automatically. */
    private var replacementsLeft = MAX_REPLACEMENTS
    private var generation: Job? = null

    /** Counts generations, so a late report from a replaced one is ignored. */
    private var generationNumber = 0
    private var isRoundStarted = false

    /** Whether the app is in the background, where no generation starts (phase-6 D7). */
    private var isPaused = false

    /** Whether generation stopped short of a full round, so only a manual retry can go on. */
    private val isStuck: Boolean get() = queue.isEmpty() && failedJobs.isNotEmpty()

    /** Generated, pending and failed exercises together, which is always the round's exercise count. */
    @get:VisibleForTesting
    internal val jobCount: Int get() = exercises.size + queue.size + failedJobs.size

    // MARK: - Reader actions

    /**
     * Starts generating a round ahead of time with these settings. Does nothing if a round with the same settings
     * is already prepared.
     */
    fun configure(newSettings: RoundSettings) {
        if (plan.isNotEmpty() && newSettings == settings) return
        settings = newSettings
        prepareRound()
        publish()
    }

    fun start() {
        val settings = settings ?: return
        // A round is used once; "Practice again" generates a new one.
        if (plan.isEmpty() || isRoundStarted) prepareRound()
        isRoundStarted = true
        currentIndex = 0
        answers = emptyList()
        roundAgeGroup = settings.ageGroup
        showCurrent()
        publish()
    }

    /** Hides the passage and shows the question, when the reader is done or their time is up. */
    fun finishReading() {
        if (phase != Phase.Answering) return
        isReading = false
        publish()
    }

    fun select(option: Int) {
        val exercise = currentExercise
        if (isReading || selectedOption != null || exercise == null) return
        selectedOption = option
        answers = answers + RoundAnswer(index = currentIndex, exercise = exercise, selectedOption = option)
        publish()
    }

    fun next() {
        if (phase != Phase.Answering || selectedOption == null) return
        if (currentIndex == exerciseCount - 1) {
            roundAgeGroup?.let { onRoundFinished(RoundRecord(UUID.randomUUID(), now(), it, answers)) }
            phase = Phase.Finished
            publish()
            return
        }
        currentIndex += 1
        showCurrent()
        publish()
    }

    /**
     * Stops the generation in flight while the app is in the background, where on-device inference is refused
     * (phase-6 D7). Not a failure: the exercise being written keeps its place at the head of the queue.
     */
    fun pause() {
        isPaused = true
        generation?.cancel()
        // A late attempt report from the cancelled generation must not change the topic name.
        generationNumber += 1
    }

    /** Goes on with the queue after [pause], when the app is back in front. Does nothing while generating. */
    fun resume() {
        isPaused = false
        if (repository == null || queue.isEmpty() || generation?.isActive == true) return
        generateRemaining()
    }

    // MARK: - Rounds

    private fun prepareRound() {
        val settings = settings ?: return
        exerciseCount = settings.exerciseCount
        plan = makePlan(settings.topics, settings.exerciseCount, random)
        // Made per round, so a change to the device language applies to the next round.
        repository = makeRepository(settings)
        queue = plan
        exercises = emptyList()
        failedJobs = emptyList()
        replacementsLeft = MAX_REPLACEMENTS
        isRoundStarted = false
        generateRemaining()
    }

    private fun requeueFailedJobs() {
        queue = failedJobs
        failedJobs = emptyList()
        phase = Phase.Loading
        generateRemaining()
        publish()
    }

    /**
     * Generates the queued exercises one at a time. A failure doesn't stop the others: it's replaced at the back of
     * the queue while the round has replacements left.
     *
     * The job is recorded before it runs (`LAZY`): a repository that never suspends would otherwise finish before
     * `generation` was assigned, on an immediate dispatcher (phase-5 D3).
     */
    private fun generateRemaining() {
        generation?.cancel()
        generationNumber += 1
        val number = generationNumber
        val repository = repository ?: return
        if (isPaused) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) { generate(repository, number) }
        generation = job
        job.start()
    }

    // The loop of the generation job; `isActive` is this job's own, so only its own cancellation ends it.
    @Suppress(
        "TooGenericExceptionCaught", // Any failure of the repository counts as a failed exercise.
        "ReturnCount", // One exit per way the loop ends: nothing left to write, or this job was cancelled.
    )
    private suspend fun CoroutineScope.generate(
        repository: ExerciseRepository,
        number: Int,
    ) {
        while (true) {
            val item = queue.firstOrNull() ?: return
            writingTopicName = item.topics.firstOrNull()?.name
            publish()
            try {
                val exercise =
                    repository.exercise(item.topics, item.skill) { topic ->
                        // A newer round, a retry or a pause replaced this generation.
                        if (number != generationNumber) return@exercise
                        writingTopicName = topic.name
                        publish()
                    }
                if (!isActive) return
                queue = queue.drop(1)
                exercises = exercises + exercise
            } catch (error: CancellationException) {
                // Our own cancellation ends the loop. One thrown by the repository while we are still active (a
                // timeout, a cancelled AICore future) is a failed exercise, not a reason to stop (Q1).
                if (!isActive) throw error
                queue = queue.drop(1)
                handleFailure(item, error)
            } catch (error: Exception) {
                if (!isActive) return
                queue = queue.drop(1)
                handleFailure(item, error)
            }
            if (phase == Phase.Loading) showCurrent()
            publish()
        }
    }

    /**
     * Queues a replacement with the same skill and other topics while the round has some left, and otherwise keeps
     * the exercise for a manual retry.
     */
    private fun handleFailure(
        item: PlanItem,
        error: Throwable,
    ) {
        val failure = "exercise (${item.skill}, ${item.topics.firstOrNull()?.name ?: "no topic"})"
        val settings = settings
        if (settings == null || replacementsLeft <= 0) {
            failedJobs = failedJobs + item
            Log.e(TAG, "Couldn't generate an $failure, no replacements left", error)
            return
        }
        replacementsLeft -= 1
        val topics = replacementTopics(item.topics, settings.topics, random)
        queue = queue + PlanItem(topics, item.skill)
        Log.e(TAG, "Couldn't generate an $failure, replacing it, $replacementsLeft left", error)
    }

    /**
     * Shows the current exercise from its passage if it's ready, otherwise waits for the next one to be ready, or
     * reports the failure once nothing is left to generate.
     */
    private fun showCurrent() {
        selectedOption = null
        isReading = true
        if (currentIndex < exercises.size) {
            currentExercise = exercises[currentIndex]
            phase = Phase.Answering
        } else {
            currentExercise = null
            phase = if (queue.isEmpty()) Phase.Failed else Phase.Loading
        }
    }

    private fun publish() {
        _uiState.value =
            QuizUiState(
                exerciseCount = exerciseCount,
                phase = phase,
                currentIndex = currentIndex,
                currentExercise = currentExercise,
                selectedOption = selectedOption,
                isReading = isReading,
                answers = answers,
                roundAgeGroup = roundAgeGroup,
                writingTopicName = writingTopicName,
                plannedTopicNames = plan.mapNotNull { it.topics.firstOrNull()?.name }.distinct(),
                failedTopicName =
                    failedJobs
                        .firstOrNull()
                        ?.topics
                        ?.firstOrNull()
                        ?.name,
            )
    }

    // MARK: - Retry with different topics

    /**
     * Generates the failed exercises again with the same topics. Generation is sampled, so a second try often
     * works. Doesn't use or reset the round's automatic replacements.
     */
    fun retry() {
        if (settings == null || !isStuck) return
        requeueFailedJobs()
    }

    /**
     * Generates the failed exercises again with other topics, preferring ones they didn't just try, and the same
     * skills. Doesn't use or reset the round's automatic replacements.
     */
    fun retryWithDifferentTopics() {
        val settings = settings ?: return
        if (!isStuck) return
        failedJobs = failedJobs.map { it.copy(topics = replacementTopics(it.topics, settings.topics, random)) }
        requeueFailedJobs()
    }

    companion object {
        /** How many failed exercises a round replaces automatically. */
        const val MAX_REPLACEMENTS = 3

        private const val SPARE_TOPICS = 2
        private const val REPLACEMENT_TOPIC_COUNT = 3

        /**
         * Up to 3 topics for another try at a failed exercise, those it didn't just try first.
         */
        fun replacementTopics(
            failed: List<RoundTopic>,
            topics: List<RoundTopic>,
            random: Random,
        ): List<RoundTopic> {
            val failedSet = failed.toSet()
            val untried = topics.filter { it !in failedSet }.shuffled(random)
            val tried = topics.filter { it in failedSet }.shuffled(random)
            return (untried + tried).take(REPLACEMENT_TOPIC_COUNT)
        }

        /**
         * The topics and skill for each exercise of a round. Each exercise gets its own topic while there are
         * enough, cycling through them otherwise without repeating a topic back to back, plus two other topics to
         * fall back on if generation fails. Every skill is used at least once when [count] is at least the number of
         * skills; a shorter round gets [count] different skills.
         */
        fun makePlan(
            topics: List<RoundTopic>,
            count: Int,
            random: Random,
        ): List<PlanItem> {
            val shuffled = topics.shuffled(random)
            val topicCount = shuffled.size
            val skills = mutableListOf<ComprehensionSkill>()
            while (skills.size < count) skills += ComprehensionSkill.entries.shuffled(random)
            return skills.take(count).mapIndexed { i, skill ->
                val primary = (i + i / topicCount) % topicCount
                val spares =
                    shuffled.indices
                        .filter { it != primary }
                        .shuffled(random)
                        .take(SPARE_TOPICS)
                        .map { shuffled[it] }
                PlanItem(listOf(shuffled[primary]) + spares, skill)
            }
        }
    }
}
