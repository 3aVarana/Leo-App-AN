package com.victorarana.leo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundRecordTests {
    @Test
    fun correctCountAndMisses() {
        val round = roundRecordFixture(results = listOf(true, false, true, true, false, true))
        assertEquals(4, round.correctCount)
        assertEquals(listOf(1, 4), round.misses.map { it.index })
    }

    @Test
    fun allCorrectHasNoMisses() {
        val round = roundRecordFixture(results = List(6) { true })
        assertEquals(6, round.correctCount)
        assertTrue(round.misses.isEmpty())
    }

    @Test
    fun equalityAndHashUseTheIdOnly() {
        val round = roundRecordFixture()
        val sameId = RoundRecord(round.id, round.finishedAt, AgeGroup.ADULT, emptyList())
        assertEquals(round, sameId)
        assertEquals(round.hashCode(), sameId.hashCode())
        assertNotEquals(round, roundRecordFixture())
    }
}
