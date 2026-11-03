package com.victorarana.leo.features.quiz

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.domain.topic
import com.victorarana.leo.domain.wordCount
import com.victorarana.leo.support.ExerciseRepositoryFactorySpy
import com.victorarana.leo.support.MainDispatcherRule
import com.victorarana.leo.support.StubExerciseRepository
import com.victorarana.leo.support.makeQuiz
import com.victorarana.leo.support.state
import com.victorarana.leo.support.waitUntil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Port of `QuizViewModelReadingTests`: each exercise shows its passage first, and its question once the reader
 * finishes reading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuizViewModelReadingTests {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = RoundSettings(AgeGroup.NINE, listOf("owls", "rivers", "kites").map(::topic))

    /** Starts a round whose 6 exercises are ready, and waits until they are. */
    private fun TestScope.startedQuiz(): QuizViewModel {
        val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
        val quiz = makeQuiz(spy)
        quiz.configure(settings)
        waitUntil { spy.latest.calls.size == RoundSettings.DEFAULT_EXERCISE_COUNT }
        quiz.start()
        return quiz
    }

    @Test
    fun exerciseStartsWithPassage() =
        runTest {
            val quiz = startedQuiz()
            assertEquals(QuizViewModel.Phase.Answering, quiz.state.phase)
            assertTrue(quiz.state.isReading)
        }

    @Test
    fun selectWhileReadingIsIgnored() =
        runTest {
            val quiz = startedQuiz()
            quiz.select(0)
            assertNull(quiz.state.selectedOption)
            assertTrue(quiz.state.answers.isEmpty())
            assertTrue(quiz.state.isReading)
        }

    @Test
    fun finishReadingShowsQuestion() =
        runTest {
            val quiz = startedQuiz()
            quiz.finishReading()
            assertFalse(quiz.state.isReading)
            quiz.select(0)
            assertEquals(0, quiz.state.selectedOption)
        }

    @Test
    fun nextExerciseStartsWithPassage() =
        runTest {
            val quiz = startedQuiz()
            quiz.finishReading()
            quiz.select(0)
            quiz.next()
            assertEquals(1, quiz.state.currentIndex)
            assertTrue(quiz.state.isReading)
        }

    /** The reader can't skip the passage of an exercise that isn't ready yet. */
    @Test
    fun finishReadingWhileLoadingDoesNothing() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings)
            quiz.start()
            assertEquals(QuizViewModel.Phase.Loading, quiz.state.phase)
            quiz.finishReading()

            waitUntil { spy.latest.waitingCount == 1 }
            spy.latest.resume(exerciseFixture())
            waitUntil { quiz.state.phase == QuizViewModel.Phase.Answering }
            assertTrue(quiz.state.isReading)
        }

    @Test
    fun readingTimeFollowsPassageAndRoundAgeGroup() =
        runTest {
            val quiz = startedQuiz()
            val exercise = checkNotNull(quiz.state.currentExercise)
            assertEquals(settings.ageGroup.readingTime(exercise.passage.wordCount), quiz.state.readingTime)
        }
}
