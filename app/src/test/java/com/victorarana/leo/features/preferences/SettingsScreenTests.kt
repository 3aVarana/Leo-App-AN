package com.victorarana.leo.features.preferences

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.features.root.RootScreen
import com.victorarana.leo.features.root.RootViewModel
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.support.PreferencesRepositorySpy
import com.victorarana.leo.support.StubModelAvailabilityProvider
import com.victorarana.leo.support.StubTopicReviewRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings opened from Home: Cancel and back discard, Done saves once. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val preferences = readerPreferencesFixture(AgeGroup.NINE)
    private val repository = PreferencesRepositorySpy(stored = preferences)

    @Before
    fun setUp() {
        val root =
            RootViewModel(repository, StubModelAvailabilityProvider(), StubTopicReviewRepository(), TopicNames.English)
                .apply { onIntroPlayed() }
        composeRule.setContent { LeoTheme { RootScreen(root) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.waitForIdle()
    }

    private fun assertOnHome() = composeRule.onNodeWithText("Home placeholder (phase 6)").assertIsDisplayed()

    @Test
    fun opensWithTheCurrentPreferences() {
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Reading timer").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Volcanoes").assertExists()
    }

    @Test
    fun cancelDiscardsTheChanges() {
        composeRule.onNodeWithText("Reading timer").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertOnHome()
        assertTrue(repository.saved.isEmpty())

        // A new opening starts from the saved preferences again.
        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun systemBackDiscardsTheChanges() {
        composeRule.onNodeWithText("Reading timer").performClick()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertOnHome()
        assertTrue(repository.saved.isEmpty())
    }

    /** A second tap while Settings is still animating out must not pop Home as well (blank screen). */
    @Test
    fun doubleTapOnDoneLeavesHomeShowing() {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertOnHome()
    }

    @Test
    fun doubleTapOnOpenSettingsOpensItOnce() {
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertOnHome()
    }

    /** A sheet leaves its presenter in place: Home stays composed while Settings slides over it. */
    @Test
    fun homeStaysComposedWhileSettingsSlidesOver() {
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithText("Home placeholder (phase 6)").assertExists()
    }

    @Test
    fun doneSavesOnce() {
        composeRule.onNodeWithText("Reading timer").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        assertOnHome()
        assertEquals(listOf(!preferences.isReadingTimerOn), repository.saved.map { it.isReadingTimerOn })
    }
}
