package com.victorarana.leo.domain

/**
 * What a round of exercises is generated from.
 *
 * @property exerciseCount The number of texts in the round.
 */
data class RoundSettings(
    val ageGroup: AgeGroup,
    val topics: List<RoundTopic>,
    val exerciseCount: Int = DEFAULT_EXERCISE_COUNT,
) {
    companion object {
        /** How many texts a reader can ask a round to have. */
        val EXERCISE_COUNT_RANGE = 3..9

        /** How many texts a round has until the reader changes it. */
        const val DEFAULT_EXERCISE_COUNT = 6
    }
}
