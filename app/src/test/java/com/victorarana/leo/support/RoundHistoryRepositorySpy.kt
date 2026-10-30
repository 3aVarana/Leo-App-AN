package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.RoundHistoryRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSummary
import java.util.UUID

/**
 * An in-memory [RoundHistoryRepository] that records every save and delete. Set [errorToThrow] to make
 * every call fail instead.
 */
class RoundHistoryRepositorySpy : RoundHistoryRepository {
    private val _saved = mutableListOf<RoundRecord>()
    private val _deleted = mutableListOf<UUID>()
    private val rounds = mutableMapOf<UUID, RoundRecord>()
    val saved: List<RoundRecord> get() = _saved
    val deleted: List<UUID> get() = _deleted
    var errorToThrow: Throwable? = null

    override suspend fun save(round: RoundRecord) {
        errorToThrow?.let { throw it }
        _saved += round
        rounds[round.id] = round
    }

    override suspend fun summaries(ageGroup: AgeGroup): List<RoundSummary> {
        errorToThrow?.let { throw it }
        return rounds.values
            .filter { it.ageGroup == ageGroup }
            .sortedByDescending { it.finishedAt }
            .map { RoundSummary(id = it.id, finishedAt = it.finishedAt, results = it.answers.map { a -> a.isCorrect }) }
    }

    override suspend fun round(id: UUID): RoundRecord? {
        errorToThrow?.let { throw it }
        return rounds[id]
    }

    override suspend fun delete(id: UUID) {
        errorToThrow?.let { throw it }
        _deleted += id
        rounds.remove(id)
    }
}
