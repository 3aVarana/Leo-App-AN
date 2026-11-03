package com.victorarana.leo.features.quiz

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.topic
import com.victorarana.leo.features.components.OptionState
import com.victorarana.leo.support.MainDispatcherRule
import com.victorarana.leo.support.StubExerciseRepository
import com.victorarana.leo.support.state
import com.victorarana.leo.support.waitUntil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Port of `QuizViewModelOptionStateTests`: how `QuizViewModel` presents the options of the current exercise. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuizViewModelOptionStateTests {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** A started round whose exercises are all ready. The fixture exercise has 4 options. */
    private fun TestScope.startedQuiz(): QuizViewModel {
        val stub = StubExerciseRepository.ready()
        val quiz = QuizViewModel(makeRepository = { stub })
        quiz.configure(RoundSettings(AgeGroup.NINE, listOf("owls", "rivers", "kites").map(::topic)))
        waitUntil { stub.calls.size == RoundSettings.DEFAULT_EXERCISE_COUNT }
        quiz.start()
        return quiz
    }

    private fun optionStates(quiz: QuizViewModel): List<OptionState> =
        checkNotNull(quiz.state.currentExercise).options.indices.map(quiz.state::optionState)

    @Test
    fun optionStatesBeforeAnswering() =
        runTest {
            val quiz = startedQuiz()
            assertEquals(List(4) { OptionState.Idle }, optionStates(quiz))
            assertNull(quiz.state.isAnswerCorrect)
        }

    @Test
    fun optionStatesAfterCorrectAnswer() =
        runTest {
            val quiz = startedQuiz()
            val exercise = checkNotNull(quiz.state.currentExercise)
            quiz.finishReading()
            quiz.select(exercise.correctIndex)

            val expected = MutableList(exercise.options.size) { OptionState.Dimmed }
            expected[exercise.correctIndex] = OptionState.Correct
            assertEquals(expected, optionStates(quiz))
            assertEquals(true, quiz.state.isAnswerCorrect)
        }

    @Test
    fun optionStatesAfterWrongAnswer() =
        runTest {
            val quiz = startedQuiz()
            val exercise = checkNotNull(quiz.state.currentExercise)
            val wrong = (exercise.correctIndex + 1) % exercise.options.size
            quiz.finishReading()
            quiz.select(wrong)

            val expected = MutableList(exercise.options.size) { OptionState.Dimmed }
            expected[exercise.correctIndex] = OptionState.Correct
            expected[wrong] = OptionState.Incorrect
            assertEquals(expected, optionStates(quiz))
            assertEquals(false, quiz.state.isAnswerCorrect)
        }

    @Test
    fun optionStatesResetOnNext() =
        runTest {
            val quiz = startedQuiz()
            quiz.finishReading()
            quiz.select(0)
            quiz.next()
            assertEquals(List(4) { OptionState.Idle }, optionStates(quiz))
            assertNull(quiz.state.isAnswerCorrect)
        }
}
