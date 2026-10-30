package com.victorarana.leo.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.util.UUID

/**
 * A finished round. The port of `StoredRound`; the unique `id` is the primary key, so saving the same id
 * again replaces the round.
 *
 * @property ageGroup `AgeGroup.rawValue`. Kept as text so an unknown value is found on read.
 * @property results Whether each answer was right, in round order. Duplicates what the answers say, on
 * purpose: the History list reads this without loading up to nine passages per row.
 */
@Entity(tableName = "StoredRound", indices = [Index("ageGroup")])
data class StoredRound(
    @PrimaryKey val id: UUID,
    val finishedAt: Instant,
    val ageGroup: String,
    val results: List<Boolean>,
)

/**
 * What a History row reads: a round without its answers. [id] and [results] stay raw text and are
 * decoded in the mapping, so one unreadable row is skipped instead of failing the whole list.
 */
data class StoredRoundSummary(
    val id: String,
    val finishedAt: Instant,
    val results: String,
)

/** A round with its answers, in no particular order (sort by `index`). */
data class StoredRoundWithAnswers(
    @Embedded val round: StoredRound,
    @Relation(parentColumn = "id", entityColumn = "roundId") val answers: List<StoredAnswer>,
)
