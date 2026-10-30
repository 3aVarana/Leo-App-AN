package com.victorarana.leo.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.victorarana.leo.data.repositories.PreferencesRepository
import com.victorarana.leo.domain.LeoJson
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.ReaderPreferencesSerializer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

/**
 * Keeps the preferences as JSON text in a Preferences DataStore, under [KEY].
 *
 * A value that doesn't decode is left on disk and loads as `null`, like iOS. A damaged DataStore
 * file is replaced by an empty one.
 */
class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {
    override suspend fun load(): ReaderPreferences? =
        try {
            dataStore.data.first()[PREFERENCES_KEY]?.let { LeoJson.decodeFromString(ReaderPreferencesSerializer, it) }
        } catch (error: IOException) {
            Log.e(TAG, "Couldn't read the saved preferences: $error")
            null
        } catch (error: IllegalArgumentException) {
            // Includes SerializationException, and values that decode but break an invariant, such as a malformed UUID.
            Log.e(TAG, "Couldn't read the saved preferences: $error")
            null
        }

    override suspend fun save(preferences: ReaderPreferences) {
        try {
            val json = LeoJson.encodeToString(ReaderPreferencesSerializer, preferences)
            dataStore.edit { it[PREFERENCES_KEY] = json }
        } catch (error: IOException) {
            Log.e(TAG, "Couldn't save the preferences: $error")
        } catch (error: SerializationException) {
            // Encoding never fails for ReaderPreferences in practice; kept like iOS, which keeps the saved data.
            Log.e(TAG, "Couldn't save the preferences: $error")
        }
    }

    companion object {
        /** Never change it: existing installs read their preferences under this key. */
        const val KEY = "readerPreferences"

        private const val TAG = "PreferencesRepository"
        private const val FILE_NAME = "leo"
        private val PREFERENCES_KEY = stringPreferencesKey(KEY)

        /**
         * The on-disk store, `files/datastore/leo.preferences_pb`. Call once per process, from
         * `AppContainer`: two DataStores on one file throw.
         */
        fun create(
            context: Context,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): DataStorePreferencesRepository =
            DataStorePreferencesRepository(
                dataStore(
                    scope = CoroutineScope(ioDispatcher + SupervisorJob()),
                    produceFile = { context.preferencesDataStoreFile(FILE_NAME) },
                ),
            )

        /** The DataStore [create] uses, with its corruption handler. Internal so tests build the same one. */
        internal fun dataStore(
            scope: CoroutineScope,
            produceFile: () -> File,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler =
                    ReplaceFileCorruptionHandler {
                        Log.e(TAG, "The preferences file is damaged, starting empty: $it")
                        emptyPreferences()
                    },
                scope = scope,
                produceFile = produceFile,
            )
    }
}
