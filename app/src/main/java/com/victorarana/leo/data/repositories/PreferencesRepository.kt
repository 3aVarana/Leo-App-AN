package com.victorarana.leo.data.repositories

import com.victorarana.leo.domain.ReaderPreferences

/**
 * Loads and saves the reader's preferences. Stateless: `RootViewModel` holds the current value.
 *
 * Suspending, unlike iOS, because Android forbids disk I/O on the main thread.
 */
interface PreferencesRepository {
    /** `null` until onboarding is done, or when the saved data can't be read. */
    suspend fun load(): ReaderPreferences?

    /** Never throws: a failure is logged and the saved data stays as it was. */
    suspend fun save(preferences: ReaderPreferences)
}
