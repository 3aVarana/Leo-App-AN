package com.victorarana.leo.support

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.topic
import com.victorarana.leo.features.quiz.QuizUiState
import com.victorarana.leo.features.quiz.QuizViewModel

// Helpers the Quiz ViewModel suites share. Swift repeats them privately in each suite.

/** The current state, the way the Swift tests read the ViewModel's properties. */
val QuizViewModel.state: QuizUiState get() = uiState.value

/** Six topics, so every exercise of a default round gets its own main topic. */
val quizSettings: RoundSettings =
    RoundSettings(
        ageGroup = AgeGroup.NINE,
        topics = listOf("owls", "rivers", "kites", "bread", "comets", "drums").map(::topic),
    )

/** Three topics for a different age group. */
val otherQuizSettings: RoundSettings =
    RoundSettings(
        ageGroup = AgeGroup.TWELVE,
        topics = listOf("chess", "glaciers", "radios").map(::topic),
    )

/** Answers the current exercise and moves on, correctly or not. */
fun QuizViewModel.answer(correctly: Boolean) {
    val exercise = checkNotNull(state.currentExercise)
    finishReading()
    select(if (correctly) exercise.correctIndex else (exercise.correctIndex + 1) % exercise.options.size)
    next()
}

/** Makes a quiz over a factory spy, the way each Swift suite's `makeQuiz` does. */
fun makeQuiz(
    spy: ExerciseRepositoryFactorySpy,
    onRoundFinished: (RoundRecord) -> Unit = {},
): QuizViewModel = QuizViewModel(makeRepository = { spy.make(it) }, onRoundFinished = onRoundFinished)

/** A quiz whose repository never answers, for tests of the screens around it. */
fun testQuiz(spy: ExerciseRepositoryFactorySpy = ExerciseRepositoryFactorySpy()): QuizViewModel = makeQuiz(spy)
