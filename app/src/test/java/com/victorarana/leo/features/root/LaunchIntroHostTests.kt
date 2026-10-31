package com.victorarana.leo.features.root

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.victorarana.leo.features.theme.LeoTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The host on a cold launch: the launch frame holds while the first screen is unknown, then plays and ends. */
@RunWith(RobolectricTestRunner::class)
class LaunchIntroHostTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** `isReady` starting false (AICore still answering) must not leave the intro stuck on its launch frame. */
    @Test
    fun introEndsOnceReadyTurnsTrueAfterTheFirstFrame() {
        var isReady by mutableStateOf(false)
        composeRule.setContent {
            LeoTheme {
                LaunchIntroHost(isReady = isReady) { BasicText("Page") }
            }
        }
        // Past the hold, still not ready: the page stays hidden from TalkBack.
        composeRule.mainClock.advanceTimeBy(HOLD_AND_MORE_MILLIS)
        composeRule.onNodeWithText("Page").assertDoesNotExist()

        isReady = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(INTRO_AND_MORE_MILLIS)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Page").assertExists()
    }

    /** A recreation during the hold must replay the intro (and wait for `isReady`), not skip to the page. */
    @Test
    fun introCountsAsPlayedOnlyOnceItFinishes() {
        var isReady by mutableStateOf(false)
        var played = false
        composeRule.setContent {
            LeoTheme {
                LaunchIntroHost(isReady = isReady, onPlayed = { played = true }) { BasicText("Page") }
            }
        }
        composeRule.mainClock.advanceTimeBy(HOLD_AND_MORE_MILLIS)
        assertFalse(played)

        isReady = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(INTRO_AND_MORE_MILLIS)
        composeRule.waitForIdle()
        assertTrue(played)
    }

    /** An Activity that already played the intro (after a rotation) shows the page at once. */
    @Test
    fun aPlayedIntroShowsThePageAtOnce() {
        composeRule.setContent {
            LeoTheme {
                LaunchIntroHost(isReady = false, hasPlayed = true) { BasicText("Page") }
            }
        }
        composeRule.onNodeWithText("Page").assertExists()
    }

    private companion object {
        const val HOLD_AND_MORE_MILLIS = 1_000L
        const val INTRO_AND_MORE_MILLIS = 3_000L
    }
}
