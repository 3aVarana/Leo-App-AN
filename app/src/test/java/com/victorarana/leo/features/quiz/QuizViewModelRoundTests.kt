package com.victorarana.leo.features.quiz

import com.victorarana.leo.data.repositories.ExerciseGenerationError
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.domain.topic
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.support.ExerciseRepositoryFactorySpy
import com.victorarana.leo.support.MainDispatcherRule
import com.victorarana.leo.support.StubExerciseRepository
import com.victorarana.leo.support.answer
import com.victorarana.leo.support.otherQuizSettings
import com.victorarana.leo.support.quizSettings
import com.victorarana.leo.support.state
import com.victorarana.leo.support.waitUntil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Port of `QuizViewModelRoundTests`: what `QuizViewModel` exposes about a round for the redesigned screens, the
 * answer history, the progress dots and the topic names.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuizViewModelRoundTests {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = quizSettings
    private val otherSettings = otherQuizSettings
    private val count = RoundSettings.DEFAULT_EXERCISE_COUNT

    private fun makeQuiz(
        spy: ExerciseRepositoryFactorySpy = ExerciseRepositoryFactorySpy(),
        now: () -> Instant = Instant::now,
        onRoundFinished: (RoundRecord) -> Unit = {},
    ) = QuizViewModel(makeRepository = { spy.make(it) }, now = now, onRoundFinished = onRoundFinished)

    /** Starts a round whose 6 exercises are ready, and waits until they are. */
    private fun TestScope.startedQuiz(
        spy: ExerciseRepositoryFactorySpy? = null,
        now: () -> Instant = Instant::now,
        onRoundFinished: (RoundRecord) -> Unit = {},
    ): QuizViewModel {
        val factory = spy ?: ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
        val quiz = makeQuiz(factory, now, onRoundFinished)
        quiz.configure(settings)
        waitUntil { factory.latest.calls.size == count }
        quiz.start()
        return quiz
    }

    /**
     * A quiz showing the failure of its second exercise: every exercise after the first fails, and so do the 3
     * replacements.
     */
    private fun TestScope.quizFailedAtSecondExercise(): QuizViewModel {
        val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F F F F F F F F") }
        val quiz = makeQuiz(spy)
        quiz.configure(settings)
        waitUntil { spy.latest.calls.size == 9 }
        quiz.start()
        quiz.finishReading()
        quiz.select(0)
        quiz.next()
        return quiz
    }

    /** A quiz showing the failure of its last exercise: the second exercise and its 3 replacements fail. */
    private fun TestScope.quizFailedAtLastExercise(spy: ExerciseRepositoryFactorySpy): QuizViewModel {
        val quiz = makeQuiz(spy)
        quiz.configure(settings)
        waitUntil { spy.latest.calls.size == 9 }
        quiz.start()
        repeat(count - 1) { quiz.answer(correctly = true) }
        return quiz
    }

    private val results = listOf(true, false, true, true, false, true)

    // MARK: - Round history

    @Test
    fun recordsAnswers() =
        runTest {
            val quiz = startedQuiz()
            for (correctly in results) quiz.answer(correctly)
            assertEquals((0 until count).toList(), quiz.state.answers.map { it.index })
            assertEquals(results, quiz.state.answers.map { it.isCorrect })
            assertEquals(listOf(1, 4), quiz.state.misses.map { it.index })
            assertEquals(AgeGroup.NINE, quiz.state.roundAgeGroup)
            val miss = quiz.state.misses.first()
            assertNotEquals(miss.exercise.correctIndex, miss.selectedOption)
            assertEquals("Exercise 2", miss.exercise.title)
        }

    /** Saving settings from Results prepares a new round, but Results and Review keep showing this one. */
    @Test
    fun historySurvivesNewSettings() =
        runTest {
            val quiz = startedQuiz()
            for (correctly in results) quiz.answer(correctly)
            quiz.configure(otherSettings)
            assertEquals(QuizViewModel.Phase.Finished, quiz.state.phase)
            assertEquals(count, quiz.state.answers.size)
            assertEquals(4, quiz.state.correctCount)
            assertEquals(AgeGroup.NINE, quiz.state.roundAgeGroup)
        }

    @Test
    fun startClearsHistory() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
            val quiz = startedQuiz(spy)
            repeat(count) { quiz.answer(correctly = false) }
            quiz.configure(otherSettings)
            quiz.start()
            assertTrue(quiz.state.answers.isEmpty())
            assertEquals(AgeGroup.TWELVE, quiz.state.roundAgeGroup)
        }

    // MARK: - Saving a finished round

    @Test
    fun finishingARoundReportsItOnce() =
        runTest {
            val finished = mutableListOf<RoundRecord>()
            val finishedAt = Instant.ofEpochSecond(1000)
            val quiz = startedQuiz(now = { finishedAt }, onRoundFinished = { finished += it })

            for (correctly in results) quiz.answer(correctly)

            assertEquals(1, finished.size)
            assertEquals(finishedAt, finished.first().finishedAt)
            assertEquals(AgeGroup.NINE, finished.first().ageGroup)
            assertEquals(results, finished.first().answers.map { it.isCorrect })
        }

    /**
     * A second `next()` on the finished round, such as a double tap during the transition to Results, must not
     * save a duplicate.
     */
    @Test
    fun secondNextOnAFinishedRoundDoesNotReportAgain() =
        runTest {
            val finished = mutableListOf<RoundRecord>()
            val quiz = startedQuiz(onRoundFinished = { finished += it })

            repeat(count) { quiz.answer(correctly = true) }
            quiz.next()
            quiz.next()

            assertEquals(1, finished.size)
        }

    @Test
    fun unfinishedRoundIsNotReported() =
        runTest {
            val finished = mutableListOf<RoundRecord>()
            val quiz = startedQuiz(onRoundFinished = { finished += it })

            repeat(count - 1) { quiz.answer(correctly = true) }

            assertTrue(finished.isEmpty())
        }

    @Test
    fun practiceAgainReportsANewRound() =
        runTest {
            val finished = mutableListOf<RoundRecord>()
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
            val quiz = startedQuiz(spy, onRoundFinished = { finished += it })
            repeat(count) { quiz.answer(correctly = true) }

            quiz.start()
            waitUntil { spy.latest.calls.size == count }
            repeat(count) { quiz.answer(correctly = true) }

            assertEquals(2, finished.size)
            assertNotEquals(finished[0].id, finished[1].id)
        }

    // MARK: - Progress dots

    private val QuizViewModel.dots: List<DotState> get() = state.dots

    private fun dots(vararg states: DotState) = states.toList()

    @Test
    fun dotsOnWelcome() {
        assertEquals(List(6) { DotState.Upcoming }, makeQuiz().dots)
    }

    @Test
    fun dotsWhileAnswering() =
        runTest {
            val quiz = startedQuiz()
            val upcoming = DotState.Upcoming
            assertEquals(dots(DotState.Current, upcoming, upcoming, upcoming, upcoming, upcoming), quiz.dots)

            val exercise = checkNotNull(quiz.state.currentExercise)
            quiz.finishReading()
            quiz.select((exercise.correctIndex + 1) % exercise.options.size)
            assertEquals(dots(DotState.CurrentMissed, upcoming, upcoming, upcoming, upcoming, upcoming), quiz.dots)

            quiz.next()
            assertEquals(dots(DotState.Done, DotState.Current, upcoming, upcoming, upcoming, upcoming), quiz.dots)

            quiz.finishReading()
            quiz.select(checkNotNull(quiz.state.currentExercise).correctIndex)
            assertEquals(
                dots(DotState.Done, DotState.CurrentCorrect, upcoming, upcoming, upcoming, upcoming),
                quiz.dots,
            )
        }

    @Test
    fun dotsWhileLoading() =
        runTest {
            val quiz = makeQuiz(ExerciseRepositoryFactorySpy())
            quiz.configure(settings)
            quiz.start()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            assertEquals(dots(DotState.Current, *Array(5) { DotState.Upcoming }), quiz.dots)
        }

    @Test
    fun dotsAfterFailure() =
        runTest {
            val quiz = quizFailedAtSecondExercise()
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
            assertEquals(dots(DotState.Done, DotState.Failed, *Array(4) { DotState.Upcoming }), quiz.dots)
        }

    @Test
    fun dotsWhenFinished() =
        runTest {
            val quiz = startedQuiz()
            repeat(count) { quiz.answer(correctly = true) }
            assertEquals(List(6) { DotState.Done }, quiz.dots)
        }

    // MARK: - Topic names

    /** Six topics give six different main topics, named as the reader sees them. */
    @Test
    fun plannedTopicNames() {
        val quiz = makeQuiz()
        assertTrue(quiz.state.plannedTopicNames.isEmpty())

        quiz.configure(settings)
        assertEquals(settings.topics.map { it.name }.toSet(), quiz.state.plannedTopicNames.toSet())
        assertEquals(count, quiz.state.plannedTopicNames.size)
    }

    /** Three topics for six exercises: each name is listed once. */
    @Test
    fun plannedTopicNamesHaveNoRepeats() {
        val quiz = makeQuiz()
        quiz.configure(otherSettings)
        assertEquals(3, quiz.state.plannedTopicNames.size)
        assertEquals(otherSettings.topics.map { it.name }.toSet(), quiz.state.plannedTopicNames.toSet())
    }

    @Test
    fun writingTopicNameFollowsFallback() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            quiz.start()
            waitUntil { spy.latest.waitingCount == 1 }
            val call = spy.latest.calls[0]
            assertEquals(call.topics[0].name, quiz.state.writingTopicName)

            val report = spy.latest.attemptHandlers.first()
            report(call.topics[1])
            assertEquals(call.topics[1].name, quiz.state.writingTopicName)

            spy.latest.resume(exerciseFixture())
            waitUntil { spy.latest.waitingCount == 1 }
            assertEquals(
                spy.latest.calls[1]
                    .topics[0]
                    .name,
                quiz.state.writingTopicName,
            )
        }

    /** A late fallback report from a replaced round doesn't change the name. */
    @Test
    fun writingTopicNameIgnoresReplacedRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }
            val first = spy.latest
            val report = first.attemptHandlers.first()

            quiz.configure(otherSettings)
            waitUntil { spy.latest.waitingCount == 1 }
            val current = quiz.state.writingTopicName
            report(topic("owls"))
            assertEquals(current, quiz.state.writingTopicName)
        }

    @Test
    fun failedTopicNameIsFirstFailedJob() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S F F") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }
            assertEquals(9, spy.latest.calls.size)
            assertNull(quiz.state.failedTopicName)

            spy.latest.resume(Result.failure(ExerciseGenerationError()))
            waitUntil { quiz.state.failedTopicName != null }
            assertEquals(
                spy.latest.calls[8]
                    .topics[0]
                    .name,
                quiz.state.failedTopicName,
            )
        }

    // MARK: - Retry with different topics

    @Test
    fun retryWithDifferentTopicsAvoidsFailedTopics() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S F F F") }
            val quiz = quizFailedAtLastExercise(spy)
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
            val stub = spy.latest
            val failedCall = stub.calls[8]

            quiz.retryWithDifferentTopics()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            waitUntil { stub.waitingCount == 1 }
            val retryCall = stub.calls[9]
            assertEquals(failedCall.skill, retryCall.skill)
            assertEquals(3, retryCall.topics.size)
            assertTrue(
                retryCall.topics
                    .toSet()
                    .intersect(failedCall.topics.toSet())
                    .isEmpty(),
            )
            assertTrue(settings.topics.containsAll(retryCall.topics))

            stub.resume(exerciseFixture(title = "Retried"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals("Retried", quiz.state.currentExercise?.title)
        }

    /** With only the 3 topics that failed, they're tried again in another order. */
    @Test
    fun retryWithDifferentTopicsWithoutOthers() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("F F F F F F F F F") }
            val quiz = makeQuiz(spy)
            quiz.configure(otherSettings)
            quiz.start()
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Failed }
            val stub = spy.latest

            quiz.retryWithDifferentTopics()
            waitUntil { stub.waitingCount == 1 }
            assertEquals(10, stub.calls.size)
            // Jobs 4 to 6 and the 3 replacements failed once the replacements ran out; job 4 is retried first.
            assertEquals(stub.calls[3].skill, stub.calls[9].skill)
            assertEquals(otherSettings.topics.toSet(), stub.calls[9].topics.toSet())
        }
}
