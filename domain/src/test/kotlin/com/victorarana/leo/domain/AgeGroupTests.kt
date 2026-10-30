package com.victorarana.leo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AgeGroupTests {
    /** The raw values are dictionary keys in saved preferences. */
    @Test
    fun rawValuesAreStable() {
        assertEquals(listOf("6-8", "9-11", "12-14", "15-17", "18+"), AgeGroup.entries.map { it.rawValue })
        assertEquals(AgeGroup.ADULT, AgeGroup.fromRawValue("18+"))
        assertNull(AgeGroup.fromRawValue("99"))
    }

    @Test
    fun acceptedWordCountContainsRequestedRange() {
        for (group in AgeGroup.entries) {
            assertTrue("$group", group.passageWordRange.first in group.acceptedWordCount)
            assertTrue("$group", group.passageWordRange.last in group.acceptedWordCount)
        }
    }

    @Test
    fun acceptedWordCount() {
        val expected =
            listOf(
                AgeGroup.SIX to 30..120,
                AgeGroup.NINE to 48..180,
                AgeGroup.TWELVE to 60..225,
                AgeGroup.FIFTEEN to 72..270,
                AgeGroup.ADULT to 96..345,
            )
        for ((group, range) in expected) assertEquals("$group", range, group.acceptedWordCount)
    }

    @Test
    fun readingTime() {
        val cases =
            listOf(
                Triple(AgeGroup.NINE, 100, 90.seconds),
                Triple(AgeGroup.ADULT, 200, 90.seconds),
                // 75 seconds, rounded up to 10.
                Triple(AgeGroup.SIX, 50, 80.seconds),
                Triple(AgeGroup.SIX, 80, 120.seconds),
                Triple(AgeGroup.ADULT, 345, 160.seconds),
                // Short passages get the minimum.
                Triple(AgeGroup.ADULT, 40, 30.seconds),
                Triple(AgeGroup.NINE, 0, 30.seconds),
            )
        for ((group, wordCount, expected) in cases) {
            assertEquals("$group $wordCount", expected, group.readingTime(wordCount))
        }
    }

    /** Older readers read faster, and get no more time for the same passage. */
    @Test
    fun readingPaceNeverDecreases() {
        for ((younger, older) in AgeGroup.entries.zipWithNext()) {
            assertTrue("$younger → $older", younger.readingWordsPerMinute < older.readingWordsPerMinute)
            assertTrue("$younger → $older", younger.readingTime(150) >= older.readingTime(150))
        }
    }

    @Test
    fun passageWordRangeNeverDecreases() {
        for ((younger, older) in AgeGroup.entries.zipWithNext()) {
            assertTrue("$younger → $older", younger.passageWordRange.first <= older.passageWordRange.first)
            assertTrue("$younger → $older", younger.passageWordRange.last <= older.passageWordRange.last)
        }
    }

    @Test
    fun minimumReadingTime() {
        val expected: Duration = 30.seconds
        assertEquals(expected, AgeGroup.MINIMUM_READING_TIME)
    }
}
