package com.victorarana.leo.domain

import java.time.Instant
import java.util.UUID

/**
 * A finished round, saved once the reader reaches Results. A round left unfinished is never reported.
 *
 * Two records are the same round when their [id] matches; [answers] doesn't take part in equality.
 *
 * @property answers In round order, `index` 0 to the round's text count minus 1.
 */
class RoundRecord(
    val id: UUID,
    val finishedAt: Instant,
    val ageGroup: AgeGroup,
    val answers: List<RoundAnswer>,
) {
    val correctCount: Int
        get() = answers.count { it.isCorrect }

    val misses: List<RoundAnswer>
        get() = answers.filterNot { it.isCorrect }

    override fun equals(other: Any?): Boolean = other is RoundRecord && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "RoundRecord(id=$id, finishedAt=$finishedAt, ageGroup=$ageGroup)"
}
