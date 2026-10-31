package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.PreferencesRepository
import com.victorarana.leo.domain.ReaderPreferences
import kotlinx.coroutines.CompletableDeferred

/** An in-memory [PreferencesRepository] that records every save. */
class PreferencesRepositorySpy(
    /** What [load] returns. Updated by [save], like the real one. */
    var stored: ReaderPreferences? = null,
    /** When set, [load] waits for it, like a slow disk. */
    private val loadGate: CompletableDeferred<Unit>? = null,
) : PreferencesRepository {
    private val _saved = mutableListOf<ReaderPreferences>()
    val saved: List<ReaderPreferences> get() = _saved

    override suspend fun load(): ReaderPreferences? {
        val value = stored
        loadGate?.await()
        return value
    }

    override suspend fun save(preferences: ReaderPreferences) {
        _saved += preferences
        stored = preferences
    }
}
