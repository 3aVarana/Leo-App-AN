package com.victorarana.leo.domain

import java.util.UUID

/**
 * A ready-to-present exercise with shuffled options.
 *
 * Builds an exercise as given, without validation or shuffling beyond the index check. For tests
 * and previews; model output goes through `ExerciseRepair`.
 *
 * @property id Defaults to a new one. A restored exercise passes its stored id, so it keeps a stable
 * identity across reads.
 * @property explanation Why the correct answer is right, shown after an incorrect pick. May be empty.
 */
data class Exercise(
    val id: UUID = UUID.randomUUID(),
    val topic: RoundTopic,
    val skill: ComprehensionSkill,
    val title: String,
    val passage: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
) {
    init {
        require(correctIndex in options.indices) { "correctIndex $correctIndex is not an index of options" }
    }
}
