package com.victorarana.leo.features.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The spacer split in `ScrollingScreen`, which SwiftUI does for free. 1dp = 1px here. */
class ShareHeightsTests {
    private val toPx: (Dp) -> Int = { it.value.toInt() }

    @Test
    fun equalWeightsShareEqually() {
        val sizes = shareHeights(100, listOf(ScrollingChildData(weight = 1f), ScrollingChildData(weight = 1f)), toPx)
        assertEquals(listOf(50, 50), sizes)
    }

    @Test
    fun aCappedChildLeavesTheRestToTheOthers() {
        val children = listOf(ScrollingChildData(weight = 1f, max = 20.dp), ScrollingChildData(weight = 1f))
        assertEquals(listOf(20, 80), shareHeights(100, children, toPx))
    }

    @Test
    fun minimumsHoldWhenTheyExceedTheFreeSpace() {
        val children =
            listOf(ScrollingChildData(weight = 1f, min = 24.dp), ScrollingChildData(weight = 1f, min = 24.dp))
        assertEquals(listOf(24, 24), shareHeights(30, children, toPx))
    }

    @Test
    fun aMaximumBelowTheMinimumKeepsTheMinimum() {
        val children =
            listOf(ScrollingChildData(weight = 1f, min = 24.dp, max = 10.dp), ScrollingChildData(weight = 1f))
        assertEquals(listOf(24, 76), shareHeights(100, children, toPx))
    }
}
