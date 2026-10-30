package com.victorarana.leo.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.readerPreferencesFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

// Robolectric only so android.util.Log works on the JVM; the DataStore itself is plain.
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePreferencesRepositoryTests {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val key = stringPreferencesKey("readerPreferences")

    private fun TestScope.dataStore(file: File = temporaryFolder.newFile("t.preferences_pb").also { it.delete() }) =
        DataStorePreferencesRepository.dataStore(scope = backgroundScope, produceFile = { file })

    private fun TestScope.repository(store: DataStore<Preferences> = dataStore()) =
        DataStorePreferencesRepository(store)

    @Test
    fun emptyStoreLoadsNull() =
        runTest {
            assertNull(repository().load())
        }

    @Test
    fun savesAndLoads() =
        runTest {
            val preferences =
                readerPreferencesFixture(
                    ageGroup = AgeGroup.TWELVE,
                    disabled = mapOf(AgeGroup.TWELVE to setOf("mythology")),
                    custom = mapOf(AgeGroup.TWELVE to listOf("Chess")),
                )
            val repository = repository()
            repository.save(preferences)
            assertEquals(preferences, repository.load())
        }

    @Test
    fun corruptDataLoadsNull() =
        runTest {
            val store = dataStore()
            store.edit { it[key] = "not json" }
            assertNull(repository(store).load())
            // The bad value stays on disk, like iOS.
            assertEquals("not json", store.data.first()[key])
        }

    @Test
    fun loadsVersion1Data() =
        runTest {
            val store = dataStore()
            store.edit { it[key] = """{"ageGroup":"12-14"}""" }
            assertEquals(ReaderPreferences(ageGroup = AgeGroup.TWELVE), repository(store).load())
        }

    /** Existing installs keep their settings only while the key stays the same. */
    @Test
    fun usesReaderPreferencesKey() =
        runTest {
            val store = dataStore()
            repository(store).save(ReaderPreferences(ageGroup = AgeGroup.SIX))
            assertEquals("readerPreferences", DataStorePreferencesRepository.KEY)
            assertNotNull(store.data.first()[key])
        }

    @Test
    fun corruptFileLoadsNull() =
        runTest {
            val file = temporaryFolder.newFile("damaged.preferences_pb")
            file.writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04, 0x05))
            val repository = repository(dataStore(file))
            assertNull(repository.load())
            // The production corruption handler replaced the file, so saving works again.
            val preferences = ReaderPreferences(ageGroup = AgeGroup.NINE)
            repository.save(preferences)
            assertEquals(preferences, repository.load())
        }

    @Test
    fun nullWhenReadFails() =
        runTest {
            val failing =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { throw IOException("disk gone") }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        throw IOException("disk gone")
                }
            val repository = repository(failing)
            assertNull(repository.load())
            // save never throws either.
            repository.save(ReaderPreferences(ageGroup = AgeGroup.SIX))
        }
}
