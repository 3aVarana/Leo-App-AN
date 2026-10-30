package com.victorarana.leo.data.room

import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import java.util.UUID

@Dao
interface RoundHistoryDao {
    @Insert
    suspend fun insertRound(round: StoredRound)

    @Insert
    suspend fun insertAnswers(answers: List<StoredAnswer>)

    /** Deletes the round's answers too (foreign key cascade). */
    @Query("DELETE FROM StoredRound WHERE id = :id")
    suspend fun deleteRound(id: UUID)

    /**
     * Inserts or replaces a round and its answers in one transaction. Not `OnConflictStrategy.REPLACE`:
     * its implicit delete doesn't reliably run the cascade.
     */
    @Transaction
    suspend fun replaceRound(
        round: StoredRound,
        answers: List<StoredAnswer>,
    ) {
        deleteRound(round.id)
        insertRound(round)
        insertAnswers(answers)
    }

    /** Never reads the answers table. */
    @Query("SELECT id, finishedAt, results FROM StoredRound WHERE ageGroup = :ageGroup ORDER BY finishedAt DESC")
    suspend fun summaries(ageGroup: String): List<StoredRoundSummary>

    @Transaction
    @Query("SELECT * FROM StoredRound WHERE id = :id")
    suspend fun round(id: UUID): StoredRoundWithAnswers?

    /** For tests: how many answer rows exist in all. */
    @VisibleForTesting
    @Query("SELECT COUNT(*) FROM StoredAnswer")
    suspend fun answerCount(): Int
}
