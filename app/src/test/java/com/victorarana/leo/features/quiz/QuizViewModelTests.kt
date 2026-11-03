package com.victorarana.leo.features.quiz

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.victorarana.leo.data.repositories.ExerciseGenerationError
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.features.components.OptionState
import com.victorarana.leo.support.ExerciseRepositoryFactorySpy
import com.victorarana.leo.support.MainDispatcherRule
import com.victorarana.leo.support.StubExerciseRepository
import com.victorarana.leo.support.answer
import com.victorarana.leo.support.makeQuiz
import com.victorarana.leo.support.otherQuizSettings
import com.victorarana.leo.support.quizSettings
import com.victorarana.leo.support.settle
import com.victorarana.leo.support.state
import com.victorarana.leo.support.waitUntil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Port of `QuizViewModelTests`, plus the Android-only tests at the end. Robolectric because the ViewModel logs a
 * failed exercise with `android.util.Log`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuizViewModelTests {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = quizSettings
    private val otherSettings = otherQuizSettings

    private fun assertStartingState(quiz: QuizViewModel) {
        val state = quiz.state
        assertEquals(QuizViewModel.Phase.Welcome, state.phase)
        assertEquals(0, state.currentIndex)
        assertNull(state.currentExercise)
        assertNull(state.selectedOption)
        assertEquals(0, state.correctCount)
        assertFalse(state.isLastExercise)
        assertNull(state.isAnswerCorrect)
        assertFalse(state.isReading)
        assertNull(state.readingTime)
        assertEquals(OptionState.Idle, state.optionState(0))
    }

    /** A quiz whose repository is never used. */
    private fun makeUnusedQuiz(): QuizViewModel = QuizViewModel(makeRepository = { StubExerciseRepository() })

    @Test
    fun startingState() = assertStartingState(makeUnusedQuiz())

    @Test
    fun startBeforeConfigure() {
        val quiz = makeUnusedQuiz()
        quiz.start()
        assertStartingState(quiz)
    }

    @Test
    fun selectWithoutExercise() {
        val quiz = makeUnusedQuiz()
        quiz.select(0)
        assertStartingState(quiz)
    }

    @Test
    fun nextWithoutSelection() {
        val quiz = makeUnusedQuiz()
        quiz.next()
        assertStartingState(quiz)
    }

    @Test
    fun retryBeforeConfigure() {
        val quiz = makeUnusedQuiz()
        quiz.retry()
        assertStartingState(quiz)
    }

    // MARK: - With a stub repository

    /** Starts a round whose 6 exercises are ready, and waits until they are. */
    private fun TestScope.startedQuiz(spy: ExerciseRepositoryFactorySpy? = null): QuizViewModel {
        val factory = spy ?: ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
        val quiz = makeQuiz(factory)
        quiz.configure(settings)
        waitUntil { factory.latest.calls.size == RoundSettings.DEFAULT_EXERCISE_COUNT }
        quiz.start()
        return quiz
    }

    // MARK: - Configure

    @Test
    fun configureStartsGenerating() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)

            assertEquals(listOf(settings), spy.settings)
            waitUntil { spy.latest.calls.size == 1 }
            val call = spy.latest.calls[0]
            assertEquals(3, call.topics.size)
            assertTrue(settings.topics.containsAll(call.topics))
            assertEquals(QuizViewModel.Phase.Welcome, quiz.state.phase)
        }

    @Test
    fun configureWithSameSettingsKeepsRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            quiz.configure(settings)
            settle()
            assertEquals(listOf(settings), spy.settings)
            assertEquals(1, spy.latest.calls.size)
        }

    @Test
    fun configureWithNewSettingsCancelsEarlierRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }
            val first = spy.latest

            quiz.configure(otherSettings)
            assertEquals(listOf(settings, otherSettings), spy.settings)
            waitUntil { first.cancelledCount == 1 }
            waitUntil { spy.latest.calls.size == 1 }
            assertTrue(otherSettings.topics.containsAll(spy.latest.calls[0].topics))
        }

    @Test
    fun lateResultOfEarlierRoundIsNotShown() =
        runTest {
            val stubs = mutableListOf(StubExerciseRepository(ignoresCancellation = true), StubExerciseRepository())
            val spy = ExerciseRepositoryFactorySpy { stubs.removeFirst() }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }
            val first = spy.latest

            quiz.configure(otherSettings)
            quiz.start()
            first.resume(exerciseFixture(title = "Late"))
            settle()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            assertEquals(1, first.calls.size)

            waitUntil { spy.latest.waitingCount == 1 }
            spy.latest.resume(exerciseFixture(title = "New"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals("New", quiz.state.currentExercise?.title)
        }

    // MARK: - Generation order

    @Test
    fun generatesOneAtATime() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            val stub = spy.latest

            for (index in 0 until RoundSettings.DEFAULT_EXERCISE_COUNT) {
                waitUntil { stub.waitingCount == 1 }
                settle()
                assertEquals(index + 1, stub.calls.size)
                stub.resume(exerciseFixture())
            }
            settle()
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT, stub.calls.size)
        }

    @Test
    fun generatesWholeRoundBeforeStart() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.calls.size == RoundSettings.DEFAULT_EXERCISE_COUNT }
            settle()
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT, spy.latest.calls.size)
            assertEquals(QuizViewModel.Phase.Welcome, quiz.state.phase)
        }

    // MARK: - Start

    @Test
    fun startBeforeFirstExerciseIsReady() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            quiz.start()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            assertNull(quiz.state.currentExercise)

            waitUntil { spy.latest.waitingCount == 1 }
            spy.latest.resume(exerciseFixture(title = "First"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals("First", quiz.state.currentExercise?.title)
            assertEquals(0, quiz.state.currentIndex)
        }

    @Test
    fun startWhenFirstExerciseIsReady() =
        runTest {
            val quiz = startedQuiz()
            assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)
            assertEquals("Exercise 1", quiz.state.currentExercise?.title)
        }

    @Test
    fun startUsesPreparedRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
            startedQuiz(spy)
            settle()
            assertEquals(1, spy.settings.size)
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT, spy.latest.calls.size)
        }

    // MARK: - Answering

    @Test
    fun selectCorrectAnswer() =
        runTest {
            val quiz = startedQuiz()
            val exercise = checkNotNull(quiz.state.currentExercise)
            quiz.finishReading()
            quiz.select(exercise.correctIndex)
            assertEquals(exercise.correctIndex, quiz.state.selectedOption)
            assertEquals(1, quiz.state.correctCount)
        }

    @Test
    fun secondSelectionIsIgnored() =
        runTest {
            val quiz = startedQuiz()
            val exercise = checkNotNull(quiz.state.currentExercise)
            val wrong = (exercise.correctIndex + 1) % exercise.options.size
            quiz.finishReading()
            quiz.select(wrong)
            quiz.select(exercise.correctIndex)
            assertEquals(wrong, quiz.state.selectedOption)
            assertEquals(0, quiz.state.correctCount)
        }

    @Test
    fun selectWrongAnswer() =
        runTest {
            val quiz = startedQuiz()
            val exercise = checkNotNull(quiz.state.currentExercise)
            quiz.finishReading()
            quiz.select((exercise.correctIndex + 1) % exercise.options.size)
            assertEquals(0, quiz.state.correctCount)
        }

    // MARK: - Next

    @Test
    fun nextShowsNextExercise() =
        runTest {
            val quiz = startedQuiz()
            quiz.finishReading()
            quiz.select(0)
            quiz.next()
            assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)
            assertEquals(1, quiz.state.currentIndex)
            assertEquals("Exercise 2", quiz.state.currentExercise?.title)
            assertNull(quiz.state.selectedOption)
        }

    @Test
    fun nextWaitsForExerciseThatIsNotReady() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }
            spy.latest.resume(exerciseFixture(title = "First"))
            waitUntil { spy.latest.waitingCount == 1 }
            quiz.start()
            quiz.finishReading()
            quiz.select(0)

            quiz.next()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            assertEquals(1, quiz.state.currentIndex)
            assertNull(quiz.state.selectedOption)

            spy.latest.resume(exerciseFixture(title = "Second"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals("Second", quiz.state.currentExercise?.title)
        }

    @Test
    fun lastExerciseFinishesRound() =
        runTest {
            val quiz = startedQuiz()
            for (index in 0 until RoundSettings.DEFAULT_EXERCISE_COUNT - 1) {
                assertFalse("index $index", quiz.state.isLastExercise)
                quiz.answer(correctly = true)
            }
            assertEquals(5, quiz.state.currentIndex)
            assertTrue(quiz.state.isLastExercise)
            quiz.answer(correctly = true)
            assertEquals(QuizViewModel.Phase.Finished, quiz.state.phase)
        }

    @Test
    fun countsCorrectAnswers() =
        runTest {
            val quiz = startedQuiz()
            for (correctly in listOf(true, false, true, true, false, true)) {
                quiz.answer(correctly)
            }
            assertEquals(QuizViewModel.Phase.Finished, quiz.state.phase)
            assertEquals(4, quiz.state.correctCount)
        }

    // MARK: - Practice again

    @Test
    fun practiceAgainStartsNewRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
            val quiz = startedQuiz(spy)
            repeat(RoundSettings.DEFAULT_EXERCISE_COUNT) { quiz.answer(correctly = true) }
            assertEquals(QuizViewModel.Phase.Finished, quiz.state.phase)

            quiz.start()
            assertEquals(listOf(settings, settings), spy.settings)
            assertEquals(0, quiz.state.currentIndex)
            assertEquals(0, quiz.state.correctCount)
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
        }

    /** Each round gets 3 automatic replacements, however many the one before used. */
    @Test
    fun newRoundResetsReplacementLimit() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.scripted("F F F S S S S S S") }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            for (round in 1..2) {
                waitUntil { spy.latest.calls.size == 9 }
                if (round == 1) quiz.start()
                waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
                repeat(RoundSettings.DEFAULT_EXERCISE_COUNT) {
                    assertEquals("round $round", QuizViewModel.Phase.Answering, quiz.state.phase)
                    quiz.answer(correctly = true)
                }
                assertEquals("round $round", QuizViewModel.Phase.Finished, quiz.state.phase)
                if (round == 1) quiz.start()
            }
            assertEquals(2, spy.stubs.size)
        }

    // MARK: - Android only: pausing in the background (phase-6 D7, Q2)

    /** The generation in flight is cancelled without counting a failure, and its exercise keeps its place. */
    @Test
    fun pauseKeepsTheQueueAndDoesNotCountAFailure() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }
            val stub = spy.latest
            val firstCall = stub.calls[0]

            quiz.pause()
            waitUntil { stub.cancelledCount == 1 }
            settle()
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT, quiz.jobCount)
            assertNull(quiz.state.failedTopicName)
            assertEquals(1, stub.calls.size)
            assertEquals(QuizViewModel.Phase.Welcome, quiz.state.phase)

            quiz.resume()
            waitUntil { stub.waitingCount == 1 }
            assertEquals(2, stub.calls.size)
            assertEquals(firstCall, stub.calls[1])
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT, quiz.jobCount)
        }

    /** The reader who started the round and left keeps waiting on Loading, and gets the exercise on return. */
    @Test
    fun resumeContinuesGeneration() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            quiz.start()
            waitUntil { spy.latest.waitingCount == 1 }

            quiz.pause()
            settle()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)

            quiz.resume()
            waitUntil { spy.latest.waitingCount == 1 }
            spy.latest.resume(exerciseFixture(title = "After"))
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertEquals("After", quiz.state.currentExercise?.title)
        }

    @Test
    fun resumeWhileGeneratingDoesNotRestartIt() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }

            quiz.resume()
            settle()
            assertEquals(1, spy.latest.calls.size)
            assertEquals(0, spy.latest.cancelledCount)
        }

    @Test
    fun resumeWithoutARoundDoesNothing() =
        runTest {
            val quiz = makeUnusedQuiz()
            quiz.pause()
            quiz.resume()
            settle()
            assertStartingState(quiz)
        }

    /** A round prepared while the app is in the background starts generating when it returns. */
    @Test
    fun configureWhilePausedWaitsForResume() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.pause()
            quiz.configure(settings)
            settle()
            assertEquals(0, spy.latest.calls.size)

            quiz.resume()
            waitUntil { spy.latest.calls.size == 1 }
        }

    /** The late report of a generation that was paused must not rename the exercise being written. */
    @Test
    fun pausedGenerationNoLongerChangesTheWritingTopic() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }
            val report = spy.latest.attemptHandlers.first()
            val before = quiz.state.writingTopicName

            quiz.pause()
            report(otherSettings.topics.first())
            assertEquals(before, quiz.state.writingTopicName)
        }

    // MARK: - Android only: cancellation (Q1) and the ViewModel's own lifetime

    /** Phase-5 hardening: a cancel thrown by the repository while the round's job is active is a failed exercise. */
    @Test
    fun cancellationThrownByTheRepositoryCountsAsAFailure() =
        runTest {
            val stub =
                StubExerciseRepository(
                    listOf(Result.failure<Exercise>(CancellationException("timeout"))) +
                        List(8) { Result.success(exerciseFixture(title = "Exercise ${it + 1}")) },
                )
            val spy = ExerciseRepositoryFactorySpy { stub }
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            quiz.start()
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            waitUntil { stub.calls.size == RoundSettings.DEFAULT_EXERCISE_COUNT + 1 }

            // The first item was replaced at the back of the queue, so the round still has its 6 exercises.
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT, quiz.jobCount)
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT + 1, stub.calls.size)
            assertEquals(stub.calls[0].skill, stub.calls[RoundSettings.DEFAULT_EXERCISE_COUNT].skill)
        }

    @Test
    fun cancellationThrownByTheRepositoryWithNoReplacementsLeftIsKeptForRetry() =
        runTest {
            val failures = List(4) { Result.failure<Exercise>(CancellationException("x")) }
            val stub = StubExerciseRepository(failures + List(5) { Result.success(exerciseFixture()) })
            val quiz = makeQuiz(ExerciseRepositoryFactorySpy { stub })
            quiz.configure(settings)
            waitUntil { stub.calls.size == 9 }
            settle()
            assertEquals(RoundSettings.DEFAULT_EXERCISE_COUNT, quiz.jobCount)
            assertNotNull(quiz.state.failedTopicName)
        }

    @Test
    fun clearingTheViewModelCancelsGeneration() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val store = ViewModelStore()
            val factory = viewModelFactory { initializer { makeQuiz(spy) } }
            val quiz = ViewModelProvider.create(store, factory)[QuizViewModel::class.java]
            quiz.configure(settings)
            waitUntil { spy.latest.waitingCount == 1 }

            store.clear()
            waitUntil { spy.latest.cancelledCount == 1 }
        }

    @Test
    fun generationErrorsAreNotThrownAtTheCaller() =
        runTest {
            val stub = StubExerciseRepository(List(9) { Result.failure(ExerciseGenerationError()) })
            val quiz = makeQuiz(ExerciseRepositoryFactorySpy { stub })
            quiz.configure(settings)
            quiz.start()
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Failed }
        }
}
