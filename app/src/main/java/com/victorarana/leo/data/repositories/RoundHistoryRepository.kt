package com.victorarana.leo.data.repositories

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSummary
import java.util.UUID

/**
 * Saves and loads finished rounds. Errors from every call are logged by the caller; the reader
 * never sees an error for history.
 *
 * Suspending, unlike iOS, because Android forbids disk I/O on the main thread.
 */
interface RoundHistoryRepository {
    /** Saving a round whose id is already stored replaces it. */
    suspend fun save(round: RoundRecord)

    /** Rounds read at [ageGroup], newest first. */
    suspend fun summaries(ageGroup: AgeGroup): List<RoundSummary>

    /** `null` when it was deleted or can't be read. */
    suspend fun round(id: UUID): RoundRecord?

    /** Does nothing for an unknown id. */
    suspend fun delete(id: UUID)
}
