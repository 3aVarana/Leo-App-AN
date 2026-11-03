package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.RoundHistoryRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSummary
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

/**
 * An in-memory [RoundHistoryRepository] that records every save and delete. Set [errorToThrow] to make
 * every call fail instead, or [deleteError] to make only `delete` fail. A gate holds a call until the test completes
 * it, to check what happens while the store hasn't answered.
 */
class RoundHistoryRepositorySpy : RoundHistoryRepository {
    private val _saved = mutableListOf<RoundRecord>()
    private val _deleted = mutableListOf<UUID>()
    private val rounds = mutableMapOf<UUID, RoundRecord>()
    val saved: List<RoundRecord> get() = _saved
    val deleted: List<UUID> get() = _deleted
    var errorToThrow: Throwable? = null
    var deleteError: Throwable? = null

    /** When set, `summaries` suspends until it completes. */
    var summariesGate: CompletableDeferred<Unit>? = null

    /** When set, `delete` suspends until it completes, then fails if [deleteError] is set or removes the round. */
    var deleteGate: CompletableDeferred<Unit>? = null

    /** The calls to `delete`, failed ones included. */
    var deleteCalls = 0
        private set

    /** The calls to `summaries`, to check that a reload happened (or didn't). */
    var summariesCalls = 0
        private set

    override suspend fun save(round: RoundRecord) {
        errorToThrow?.let { throw it }
        _saved += round
        rounds[round.id] = round
    }

    override suspend fun summaries(ageGroup: AgeGroup): List<RoundSummary> {
        summariesCalls++
        errorToThrow?.let { throw it }
        val gate = summariesGate
        // What the store holds when the call arrives, like a query that has already read its rows.
        val held = rounds.values.toList()
        gate?.await()
        return held
            .filter { it.ageGroup == ageGroup }
            .sortedByDescending { it.finishedAt }
            .map { RoundSummary(id = it.id, finishedAt = it.finishedAt, results = it.answers.map { a -> a.isCorrect }) }
    }

    override suspend fun round(id: UUID): RoundRecord? {
        errorToThrow?.let { throw it }
        return rounds[id]
    }

    override suspend fun delete(id: UUID) {
        deleteCalls++
        errorToThrow?.let { throw it }
        // The gate first, then the failure: a store that is slow and then fails, as Room's IO hop is.
        deleteGate?.await()
        deleteError?.let { throw it }
        _deleted += id
        rounds.remove(id)
    }
}
