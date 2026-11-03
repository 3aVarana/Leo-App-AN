package com.victorarana.leo.features.quiz

import com.victorarana.leo.data.repositories.ExerciseGenerationError
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.support.ExerciseRepositoryFactorySpy
import com.victorarana.leo.support.MainDispatcherRule
import com.victorarana.leo.support.StubExerciseRepository
import com.victorarana.leo.support.answer
import com.victorarana.leo.support.makeQuiz
import com.victorarana.leo.support.quizSettings
import com.victorarana.leo.support.settle
import com.victorarana.leo.support.state
import com.victorarana.leo.support.waitUntil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Port of `QuizViewModelGenerationTests`: how `QuizViewModel` keeps generating after a failed exercise, replaces
 * it, and retries by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuizViewModelGenerationTests {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = quizSettings
    private val failure: Result<Exercise> = Result.failure(ExerciseGenerationError())
    private val count = RoundSettings.DEFAULT_EXERCISE_COUNT

    // MARK: - Failure and replacement

    @Test
    fun failureWhileWaitingShowsNextReady() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            quiz.start()
            waitUntil { spy.latest.waitingCount == 1 }

            spy.latest.resume(failure)
            waitUntil { spy.latest.waitingCount == 1 }
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            assertEquals(2, spy.latest.calls.size)

            spy.latest.resume(exerciseFixture(title = "Second"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals(0, quiz.state.currentIndex)
            assertEquals("Second", quiz.state.currentExercise?.title)
        }

    @Test
    fun failureIsReplacedWithSameSkill() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S S") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.calls.size == 7 }
            settle()
            val calls = spy.latest.calls
            assertEquals(7, calls.size)
            assertEquals(calls[1].skill, calls[6].skill)
            assertTrue(
                calls[6]
                    .topics
                    .toSet()
                    .intersect(calls[1].topics.toSet())
                    .isEmpty(),
            )

            quiz.start()
            repeat(count) {
                assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)
                quiz.answer(correctly = true)
            }
            assertEquals(QuizViewModel.Phase.Finished, quiz.state.phase)
        }

    @Test
    fun generationContinuesAfterFailure() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            val stub = spy.latest
            waitUntil { stub.waitingCount == 1 }
            stub.resume(failure)

            for (calls in 2..count) {
                waitUntil { stub.waitingCount == 1 }
                assertEquals(calls, stub.calls.size)
                stub.resume(exerciseFixture())
            }
            waitUntil { stub.waitingCount == 1 }
            assertEquals(7, stub.calls.size)
            assertEquals(stub.calls[0].skill, stub.calls[6].skill)
        }

    /** The reader waits on the second exercise; it fails and they get the third one in its place. */
    @Test
    fun readerGetsNextReadyExercise() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            val stub = spy.latest
            waitUntil { stub.waitingCount == 1 }
            stub.resume(exerciseFixture(title = "First"))
            waitUntil { stub.waitingCount == 1 }
            quiz.start()
            quiz.answer(correctly = true)
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)

            stub.resume(failure)
            waitUntil { stub.waitingCount == 1 }
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            stub.resume(exerciseFixture(title = "Third"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals(1, quiz.state.currentIndex)
            assertEquals("Third", quiz.state.currentExercise?.title)
        }

    @Test
    fun replacementLimitIsThreePerRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S F F F") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.calls.size == 9 }
            settle()
            assertEquals(9, spy.latest.calls.size)
            assertEquals(0, spy.latest.waitingCount)
            assertEquals(count, quiz.jobCount)

            quiz.start()
            repeat(count - 1) {
                assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)
                quiz.answer(correctly = true)
            }
            assertEquals(5, quiz.state.currentIndex)
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
        }

    /**
     * Jobs 1 to 4 fail, using up the replacements on the first 3, and jobs 5 and 6 still run. The replacement of
     * job 1 fails too, so the round ends 2 exercises short.
     */
    @Test
    fun failureAfterLimitStillGeneratesTheRest() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("F F F F S S F S") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            val stub = spy.latest
            waitUntil { stub.waitingCount == 1 }
            assertEquals(9, stub.calls.size)
            stub.resume(exerciseFixture())
            settle()
            assertEquals(9, stub.calls.size)
            assertEquals(count, quiz.jobCount)

            quiz.start()
            repeat(4) {
                assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)
                quiz.answer(correctly = true)
            }
            assertEquals(4, quiz.state.currentIndex)
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
        }

    @Test
    fun failureShowsWhenReaderReachesIt() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S F F F") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.calls.size == 9 }
            quiz.start()
            settle()
            assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)

            repeat(count - 2) { quiz.answer(correctly = true) }
            quiz.finishReading()
            quiz.select(0)
            assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)
            quiz.next()
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
        }

    @Test
    fun jobCountFollowsRoundSize() =
        runTest {
            for (size in listOf(3, 9)) {
                val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S S S S S S S") }
                val quiz = makeQuiz(spy)
                quiz.configure(RoundSettings(settings.ageGroup, settings.topics, exerciseCount = size))
                assertEquals(size, quiz.jobCount)
                waitUntil { spy.latest.calls.size == size + 1 }
                settle()
                assertEquals("size $size", size, quiz.jobCount)
                assertEquals("size $size", size + 1, spy.latest.calls.size)
            }
        }

    @Test
    fun jobCountInvariant() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            val stub = spy.latest
            assertEquals(count, quiz.jobCount)

            // Jobs 1, 3 and 4 are replaced, job 5 isn't, and the replacement of job 4 fails.
            for (succeeds in listOf(false, true, false, false, false, true, true, true, false)) {
                waitUntil { stub.waitingCount == 1 }
                assertEquals(count, quiz.jobCount)
                stub.resume(if (succeeds) Result.success(exerciseFixture()) else failure)
            }
            settle()
            assertEquals(count, quiz.jobCount)
            assertEquals(9, stub.calls.size)

            quiz.start()
            repeat(4) { quiz.answer(correctly = true) }
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)

            quiz.retry()
            for (succeeds in listOf(true, false)) {
                waitUntil { stub.waitingCount == 1 }
                assertEquals(count, quiz.jobCount)
                stub.resume(if (succeeds) Result.success(exerciseFixture()) else failure)
            }
            settle()
            assertEquals(count, quiz.jobCount)
            assertEquals(11, stub.calls.size)
        }

    // MARK: - Retry

    /** A quiz showing the failure of its last exercise: the second exercise and its 3 replacements fail. */
    private fun TestScope.quizFailedAtLastExercise(
        spy: ExerciseRepositoryFactorySpy =
            ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S F F F") },
    ): QuizViewModel {
        val quiz = makeQuiz(spy)
        quiz.configure(settings)
        waitUntil { spy.latest.calls.size == 9 }
        quiz.start()
        repeat(count - 1) { quiz.answer(correctly = true) }
        return quiz
    }

    @Test
    fun retryGeneratesFailedExerciseAgain() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S F F F") }
            val quiz = quizFailedAtLastExercise(spy)
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
            val stub = spy.latest
            val failedCall = stub.calls[8]

            quiz.retry()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            waitUntil { stub.waitingCount == 1 }
            assertEquals(10, stub.calls.size)
            assertEquals(failedCall, stub.calls[9])

            stub.resume(exerciseFixture(title = "Retried"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals(5, quiz.state.currentIndex)
            assertEquals("Retried", quiz.state.currentExercise?.title)
            assertEquals(1, spy.settings.size)
        }

    /**
     * A failed retry shows the failure again without an automatic replacement, and a second retry makes one call
     * again.
     */
    @Test
    fun manualRetryDoesNotUseOrResetLimit() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F S S S S F F F") }
            val quiz = quizFailedAtLastExercise(spy)
            val stub = spy.latest

            for (calls in listOf(10, 11)) {
                quiz.retry()
                waitUntil { stub.waitingCount == 1 }
                stub.resume(failure)
                waitUntil { quiz.state.phase == QuizViewModel.Phase.Failed }
                settle()
                assertEquals(calls, stub.calls.size)
                assertEquals(0, stub.waitingCount)
            }
        }

    /**
     * Job 2 and 3 fail, and so do the replacements: two failed jobs are left, and a retry requests both. The first
     * succeeds and the reader answers it; the second fails again.
     */
    @Test
    fun manualRetryRequeuesEveryFailedJob() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("S F F S S S F F F") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.calls.size == 9 }
            quiz.start()
            repeat(4) { quiz.answer(correctly = true) }
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
            val stub = spy.latest
            val failedCalls = stub.calls.slice(7..8)

            quiz.retry()
            waitUntil { stub.waitingCount == 1 }
            stub.resume(exerciseFixture(title = "Retried"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals(4, quiz.state.currentIndex)
            assertEquals("Retried", quiz.state.currentExercise?.title)

            waitUntil { stub.waitingCount == 1 }
            assertEquals(failedCalls, stub.calls.slice(9..10))
            stub.resume(failure)
            settle()
            quiz.answer(correctly = true)
            assertEquals(5, quiz.state.currentIndex)
            assertEquals(QuizViewModel.Phase.Failed, quiz.state.phase)
        }

    @Test
    fun retryWhenRoundIsGeneratedDoesNothing() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.calls.size == count }

            quiz.retry()
            settle()
            assertEquals(QuizViewModel.Phase.Welcome, quiz.state.phase)
            assertEquals(count, spy.latest.calls.size)
        }

    @Test
    fun retryWhileGeneratingDoesNothing() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("F") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }

            quiz.retry()
            quiz.retryWithDifferentTopics()
            settle()
            assertEquals(QuizViewModel.Phase.Welcome, quiz.state.phase)
            assertEquals(2, spy.latest.calls.size)
            assertEquals(0, spy.latest.cancelledCount)
        }
}
