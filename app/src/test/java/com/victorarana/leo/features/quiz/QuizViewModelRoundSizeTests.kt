package com.victorarana.leo.features.quiz

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.topic
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.support.ExerciseRepositoryFactorySpy
import com.victorarana.leo.support.MainDispatcherRule
import com.victorarana.leo.support.StubExerciseRepository
import com.victorarana.leo.support.settle
import com.victorarana.leo.support.state
import com.victorarana.leo.support.waitUntil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Port of `QuizViewModelRoundSizeTests`: rounds of other sizes than the default, 3 to 9 texts. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuizViewModelRoundSizeTests {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val topics = listOf("owls", "rivers", "kites", "bread", "comets", "drums").map(::topic)

    private fun settings(count: Int) = RoundSettings(AgeGroup.NINE, topics, exerciseCount = count)

    private fun makeQuiz(
        spy: ExerciseRepositoryFactorySpy,
        onRoundFinished: (RoundRecord) -> Unit = {},
    ) = QuizViewModel(makeRepository = { spy.make(it) }, onRoundFinished = onRoundFinished)

    /** Answers the current exercise and moves on. */
    private fun QuizViewModel.answerCorrectly() {
        val exercise = checkNotNull(state.currentExercise)
        finishReading()
        select(exercise.correctIndex)
        next()
    }

    @Test
    fun fullRoundOfAnySize() =
        runTest {
            for (count in listOf(3, 4, 9)) {
                val finished = mutableListOf<RoundRecord>()
                val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready(count) }
                val quiz = makeQuiz(spy) { finished += it }
                quiz.configure(settings(count))
                waitUntil { spy.latest.calls.size == count }
                assertEquals(count, quiz.state.exerciseCount)
                assertEquals(List(count) { DotState.Upcoming }, quiz.state.dots)

                quiz.start()
                for (index in 0 until count) {
                    assertEquals("count $count", QuizViewModel.Phase.Answering, quiz.state.phase)
                    assertEquals("count $count", index == count - 1, quiz.state.isLastExercise)
                    quiz.answerCorrectly()
                }

                assertEquals(QuizViewModel.Phase.Finished, quiz.state.phase)
                assertEquals(count, quiz.state.answers.size)
                assertEquals(1, finished.size)
                assertEquals(count, finished.first().answers.size)
                assertEquals(List(count) { DotState.Done }, quiz.state.dots)
            }
        }

    @Test
    fun plannedRoundHasTheSettingsCount() =
        runTest {
            val quiz = makeQuiz(ExerciseRepositoryFactorySpy())
            quiz.configure(settings(9))
            assertEquals(9, quiz.state.exerciseCount)
            assertEquals(topics.size, quiz.state.plannedTopicNames.size)
        }

    /** Settings saved from Welcome or Results with another round size prepare a new round of that size. */
    @Test
    fun newCountPreparesANewRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
            val quiz = makeQuiz(spy)
            quiz.configure(settings(6))
            waitUntil { spy.latest.calls.size == 6 }
            assertEquals(6, quiz.state.exerciseCount)

            quiz.configure(settings(9))
            assertEquals(listOf(settings(6), settings(9)), spy.settings)
            assertEquals(9, quiz.state.exerciseCount)
        }

    @Test
    fun sameCountKeepsTheRound() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy()
            val quiz = makeQuiz(spy)
            quiz.configure(settings(4))
            quiz.configure(settings(4))
            settle()
            assertEquals(listOf(settings(4)), spy.settings)
        }

    /**
     * Results keeps showing the finished round after the count changes, and Practice again gives a round of the
     * new size.
     */
    @Test
    fun resultsKeepTheOldRoundAfterACountChange() =
        runTest {
            val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready(count = 3) }
            val quiz = makeQuiz(spy)
            quiz.configure(settings(3))
            waitUntil { spy.latest.calls.size == 3 }
            quiz.start()
            repeat(3) { quiz.answerCorrectly() }

            quiz.configure(settings(5))
            assertEquals(QuizViewModel.Phase.Finished, quiz.state.phase)
            assertEquals(3, quiz.state.answers.size)

            quiz.start()
            assertEquals(5, quiz.state.exerciseCount)
            assertTrue(quiz.state.answers.isEmpty())
        }
}
