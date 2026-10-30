package com.victorarana.leo.domain

/**
 * An exercise of the round and the option the reader picked, for Results, Review and history.
 *
 * @property index The exercise's 0-based position in the round.
 */
data class RoundAnswer(
    val index: Int,
    val exercise: Exercise,
    val selectedOption: Int,
) {
    val id: Int
        get() = index

    val isCorrect: Boolean
        get() = selectedOption == exercise.correctIndex
}
