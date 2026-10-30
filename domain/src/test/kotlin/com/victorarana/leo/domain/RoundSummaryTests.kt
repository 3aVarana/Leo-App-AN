package com.victorarana.leo.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class RoundSummaryTests {
    @Test
    fun correctCount() {
        val summary = RoundSummary(UUID.randomUUID(), Instant.now(), listOf(true, false, true))
        assertEquals(2, summary.correctCount)
    }
}
