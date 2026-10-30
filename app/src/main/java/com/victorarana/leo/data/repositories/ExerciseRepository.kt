package com.victorarana.leo.data.repositories

import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundTopic

/**
 * Supplies the exercises of one round. A new one is made for each round, so a change
 * to the device language applies to the next round.
 */
interface ExerciseRepository {
    /** Tries [topics] in order until one gives a usable exercise, calling [onAttempt] before each. */
    suspend fun exercise(
        topics: List<RoundTopic>,
        skill: ComprehensionSkill,
        onAttempt: (RoundTopic) -> Unit,
    ): Exercise
}

/**
 * No topic gave a usable exercise. The message the reader sees is `R.string.exercise_generation_failed`,
 * resolved by the UI (phase 6): data classes carry no `Context`.
 */
class ExerciseGenerationError : Exception("failed")
