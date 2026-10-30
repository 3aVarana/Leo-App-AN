package com.victorarana.leo.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Guards the stored table and column names, the role `ReaderPreferencesSerializationTests.savedFormat`
 * plays for preferences: renaming a stored property without a migration fails here first.
 */
@RunWith(RobolectricTestRunner::class)
class RoundHistorySchemaTests {
    private fun columns(table: String): Set<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, RoundHistoryDatabase::class.java).build()
        try {
            database.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                return names
            }
        } finally {
            database.close()
        }
    }

    private fun identityHash(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, RoundHistoryDatabase::class.java).build()
        try {
            database.openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table").use { cursor ->
                cursor.moveToFirst()
                return cursor.getString(0)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun versionIsFrozen() {
        assertEquals(1, RoundHistoryDatabase.VERSION)
        // Gradle runs unit tests with the module directory (`app/`) as the working directory.
        val schema = File("schemas/com.victorarana.leo.data.room.RoundHistoryDatabase/1.json")
        assertTrue("Commit the exported schema ${schema.path}", schema.exists())
        // Changing an entity changes this hash (the Room plugin rewrites 1.json on every build, so it can't be
        // read from there). Bump `version`, add a migration, then update the literal on purpose.
        assertEquals("62731baa043d77f865065d1634186891", identityHash())
    }

    @Test
    fun storedRoundAttributes() {
        assertEquals(setOf("id", "finishedAt", "ageGroup", "results"), columns("StoredRound"))
    }

    @Test
    fun storedAnswerAttributes() {
        // The iOS attributes, with the `round` relationship stored as `roundId`, plus the row's own key.
        assertEquals(
            setOf(
                "index",
                "exerciseID",
                "topicPrompt",
                "topicName",
                "topicKind",
                "skill",
                "title",
                "passage",
                "question",
                "options",
                "correctIndex",
                "selectedOption",
                "explanation",
                "roundId",
                "rowId",
            ),
            columns("StoredAnswer"),
        )
    }
}
