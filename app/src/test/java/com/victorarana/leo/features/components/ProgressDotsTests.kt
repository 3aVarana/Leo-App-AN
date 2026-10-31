package com.victorarana.leo.features.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProgressDotsTests {
    private fun side(
        ideal: Float = 36f,
        count: Int = 6,
        width: Float?,
    ): Float = DotRow.side(ideal = ideal, count = count, spacing = 8f, proposedWidth = width)

    @Test
    fun dotsKeepTheirIdealDiameterWhenThereIsRoom() {
        assertEquals(36f, side(width = 354f), 0f)
        // Exactly enough: six 36 dp dots and five 8 dp gaps.
        assertEquals(36f, side(width = 256f), 0f)
    }

    @Test
    fun dotsShrinkEquallyToFillTheWidthWhenThereIsNot() {
        // The large dots at the largest standard size, on a 342 dp row.
        val ideal = 52.6f
        val side = side(ideal = ideal, width = 342f)
        assertTrue(side < ideal)
        assertTrue(abs(side * 6 + 8 * 5 - 342) < 0.001)
    }

    @Test
    fun dotsNeverGrowAboveTheirIdealDiameter() {
        assertEquals(36f, side(width = 10000f), 0f)
        assertEquals(28f, side(ideal = 28f, width = 400f), 0f)
    }

    @Test
    fun noProposedWidthMeansTheIdealDiameter() {
        assertEquals(36f, side(width = null), 0f)
        assertEquals(36f, side(width = Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun aRowTooNarrowForTheGapsGetsNoNegativeSide() {
        assertEquals(0f, side(width = 20f), 0f)
    }

    @Test
    fun anEmptyRowKeepsTheIdealDiameter() {
        assertEquals(36f, side(count = 0, width = 300f), 0f)
    }
}
