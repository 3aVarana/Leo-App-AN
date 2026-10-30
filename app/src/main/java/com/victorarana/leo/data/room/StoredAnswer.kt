package com.victorarana.leo.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One answer of a finished round: what the reader saw, as they saw it. Nothing here is re-localized
 * later. The port of `StoredAnswer`; the iOS `round` relationship is [roundId], and deleting the round
 * deletes its answers.
 *
 * @property index The exercise's 0-based position in the round. Rows come back unordered, so answers
 * are sorted by this when read.
 * @property topicKind `TopicKind.rawValue`.
 * @property skill `ComprehensionSkill.rawValue`.
 */
@Entity(
    tableName = "StoredAnswer",
    foreignKeys =
        [
            ForeignKey(
                entity = StoredRound::class,
                parentColumns = ["id"],
                childColumns = ["roundId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("roundId")],
)
@Suppress("LongParameterList")
data class StoredAnswer(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val roundId: UUID,
    val index: Int,
    val exerciseID: UUID,
    val topicPrompt: String,
    val topicName: String,
    val topicKind: String,
    val skill: String,
    val title: String,
    val passage: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val selectedOption: Int,
    val explanation: String,
)
