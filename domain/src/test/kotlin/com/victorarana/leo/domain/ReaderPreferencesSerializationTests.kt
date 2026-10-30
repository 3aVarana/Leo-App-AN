package com.victorarana.leo.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android-only: the cases `JSONDecoder` and `JSONEncoder` handle for free on iOS. */
class ReaderPreferencesSerializationTests {
    @Test
    fun defaultsAreEncoded() {
        val json = LeoJson.encodeToJsonElement(ReaderPreferences(AgeGroup.SIX)) as JsonObject
        assertEquals(
            setOf("ageGroup", "disabledDefaultTopics", "customTopics", "isReadingTimerOn", "exercisesPerRound"),
            json.keys,
        )
    }

    @Test
    fun unknownKeysAreIgnored() {
        val preferences =
            LeoJson.decodeFromString<ReaderPreferences>(
                """{"ageGroup":"18+","somethingFromTheFuture":{"a":1},"exercisesPerRound":4}""",
            )
        assertEquals(AgeGroup.ADULT, preferences.ageGroup)
        assertEquals(4, preferences.exercisesPerRound)
    }

    @Test
    fun nullValuesFallBackToDefaults() {
        val preferences =
            LeoJson.decodeFromString<ReaderPreferences>(
                """{"ageGroup":"9-11","isReadingTimerOn":null,"exercisesPerRound":null,"customTopics":null}""",
            )
        assertEquals(ReaderPreferences(AgeGroup.NINE), preferences)
    }

    @Test
    fun nullAgeGroupThrows() {
        assertThrows(SerializationException::class.java) {
            LeoJson.decodeFromString<ReaderPreferences>("""{"ageGroup":null}""")
        }
    }

    @Test
    fun missingAgeGroupThrows() {
        assertThrows(SerializationException::class.java) {
            LeoJson.decodeFromString<ReaderPreferences>("""{"exercisesPerRound":4}""")
        }
    }

    @Test
    fun invalidUuidThrows() {
        val json = """{"ageGroup":"9-11","customTopics":{"9-11":[{"id":"nope","name":"Chess"}]}}"""
        assertThrows(SerializationException::class.java) { LeoJson.decodeFromString<ReaderPreferences>(json) }
    }

    @Test
    fun mapsAreKeyedByRawValue() {
        val preferences = ReaderPreferences(AgeGroup.SIX, disabledDefaultTopics = mapOf(AgeGroup.ADULT to setOf("x")))
        assertTrue(LeoJson.encodeToString(preferences).contains(""""disabledDefaultTopics":{"18+":["x"]}"""))
    }
}
