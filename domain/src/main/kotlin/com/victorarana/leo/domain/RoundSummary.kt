package com.victorarana.leo.domain

import java.time.Instant
import java.util.UUID

/**
 * What a History row needs, without the texts.
 *
 * @property results Whether each answer was right, in round order. Drives the score and the dots.
 */
data class RoundSummary(
    val id: UUID,
    val finishedAt: Instant,
    val results: List<Boolean>,
) {
    val correctCount: Int
        get() = results.count { it }
}
