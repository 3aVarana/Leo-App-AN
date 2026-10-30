package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.PreferencesRepository
import com.victorarana.leo.domain.ReaderPreferences

/** An in-memory [PreferencesRepository] that records every save. */
class PreferencesRepositorySpy(
    /** What [load] returns. Updated by [save], like the real one. */
    var stored: ReaderPreferences? = null,
) : PreferencesRepository {
    private val _saved = mutableListOf<ReaderPreferences>()
    val saved: List<ReaderPreferences> get() = _saved

    override suspend fun load(): ReaderPreferences? = stored

    override suspend fun save(preferences: ReaderPreferences) {
        _saved += preferences
        stored = preferences
    }
}
