package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.ExerciseGenerationError
import com.victorarana.leo.data.repositories.ExerciseRepository
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.exerciseFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A fake [ExerciseRepository] that records each call. Calls are answered from `results` in
 * order while there are any; after that, each call waits until the test resumes it, so tests
 * control when an exercise is ready.
 *
 * Not thread-safe, like the `@MainActor` original: tests drive it from one test dispatcher.
 */
class StubExerciseRepository(
    results: List<Result<Exercise>> = emptyList(),
    /**
     * Keeps waiting calls waiting when their coroutine is cancelled, like a repository that
     * doesn't check for cancellation, so a late result can be delivered.
     */
    private val ignoresCancellation: Boolean = false,
) : ExerciseRepository {
    data class Call(
        val topics: List<RoundTopic>,
        val skill: ComprehensionSkill,
    )

    private val _calls = mutableListOf<Call>()
    val calls: List<Call> get() = _calls

    /** Waiting calls that were cancelled, which then threw [CancellationException]. */
    var cancelledCount = 0
        private set

    private val results = results.toMutableList()
    private val waiting = mutableListOf<CompletableDeferred<Exercise>>()

    /** The `onAttempt` of every call, in order, so tests can report a fallback. */
    private val _attemptHandlers = mutableListOf<(RoundTopic) -> Unit>()
    val attemptHandlers: List<(RoundTopic) -> Unit> get() = _attemptHandlers

    val waitingCount: Int get() = waiting.size

    /** Reports the first topic as tried. Tests report a fallback through [attemptHandlers]. */
    override suspend fun exercise(
        topics: List<RoundTopic>,
        skill: ComprehensionSkill,
        onAttempt: (RoundTopic) -> Unit,
    ): Exercise {
        _calls += Call(topics, skill)
        topics.firstOrNull()?.let(onAttempt)
        _attemptHandlers += onAttempt
        if (results.isNotEmpty()) return results.removeFirst().getOrThrow()

        val pending = CompletableDeferred<Exercise>()
        waiting += pending
        try {
            return if (ignoresCancellation) withContext(NonCancellable) { pending.await() } else pending.await()
        } catch (cancelled: CancellationException) {
            if (waiting.remove(pending)) cancelledCount++
            throw cancelled
        }
    }

    /** Answers the oldest waiting call. */
    fun resume(result: Result<Exercise>) {
        check(waiting.isNotEmpty()) { "No call is waiting" }
        val pending = waiting.removeFirst()
        result.fold(pending::complete, pending::completeExceptionally)
    }

    fun resume(exercise: Exercise) = resume(Result.success(exercise))

    companion object {
        /** Answers every call of a round right away. */
        fun ready(count: Int = RoundSettings.DEFAULT_EXERCISE_COUNT): StubExerciseRepository =
            StubExerciseRepository(List(count) { Result.success(exerciseFixture(title = "Exercise ${it + 1}")) })

        /**
         * Answers calls by a pattern such as `"S F S S"`: `S` succeeds with an exercise titled
         * "Call N", N being the call's position, and `F` fails. Later calls wait.
         */
        fun scripted(pattern: String): StubExerciseRepository =
            StubExerciseRepository(
                pattern.split(" ").filter { it.isNotEmpty() }.mapIndexed { index, step ->
                    require(step == "S" || step == "F") { "Unknown step $step" }
                    if (step == "S") {
                        Result.success(exerciseFixture(title = "Call ${index + 1}"))
                    } else {
                        Result.failure(ExerciseGenerationError())
                    }
                },
            )
    }
}

/**
 * Stands in for `QuizViewModel`'s repository factory: records the settings of every round and
 * hands out a new stub for each.
 */
class ExerciseRepositoryFactorySpy(
    private val makeStub: () -> StubExerciseRepository = { StubExerciseRepository() },
) {
    private val _settings = mutableListOf<RoundSettings>()
    val settings: List<RoundSettings> get() = _settings
    private val _stubs = mutableListOf<StubExerciseRepository>()
    val stubs: List<StubExerciseRepository> get() = _stubs

    val latest: StubExerciseRepository get() = _stubs.last()

    fun make(settings: RoundSettings): ExerciseRepository {
        _settings += settings
        return makeStub().also { _stubs += it }
    }
}
