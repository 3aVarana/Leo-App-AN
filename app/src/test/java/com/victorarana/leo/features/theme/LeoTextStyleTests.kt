package com.victorarana.leo.features.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LeoTextStyleTests {
    @Test
    fun displayAt48UsesTheLargeHeadlineMetrics() {
        val style = LeoTextStyle.display(48f)
        assertEquals(49.92f, style.lineHeight!!, DELTA)
        assertEquals(-0.025f, style.tracking, DELTA)
        assertEquals(LeoWeight.Semibold, style.weight)
    }

    @Test
    fun displayBelow48UsesTheTighterRatio() {
        val style = LeoTextStyle.display(40f)
        assertEquals(43.2f, style.lineHeight!!, DELTA)
        assertEquals(-0.02f, style.tracking, DELTA)
    }

    @Test
    fun launchWordmarkNeverScales() {
        assertFalse(LeoTextStyle.launchWordmark.scalesWithFontSize)
    }

    @Test
    fun rolesWithoutALineHeightKeepTheFontsOwn() {
        listOf(
            LeoTextStyle.verdict,
            LeoTextStyle.wordmark,
            LeoTextStyle.caption,
            LeoTextStyle.note,
            LeoTextStyle.answerMarker,
            LeoTextStyle.kicker,
        ).forEach { assertNull(it.lineHeight) }
    }

    @Test
    fun trackingIsInEm() {
        assertEquals(0.08f, LeoTextStyle.kicker.tracking, DELTA)
        assertEquals(-0.02f, LeoTextStyle.title.tracking, DELTA)
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
