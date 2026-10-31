package com.victorarana.leo.features.preferences

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.features.root.RootScreen
import com.victorarana.leo.features.root.RootViewModel
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.support.PreferencesRepositorySpy
import com.victorarana.leo.support.StubModelAvailabilityProvider
import com.victorarana.leo.support.StubTopicReviewRepository
import com.victorarana.leo.support.preferencesEditorFixture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Onboarding through the root gate: the picker, the topics screen, back, and Done ending onboarding. */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = PreferencesRepositorySpy()

    private fun show() {
        val root =
            RootViewModel(repository, StubModelAvailabilityProvider(), StubTopicReviewRepository(), TopicNames.English)
                .apply { onIntroPlayed() }
        composeRule.setContent { LeoTheme { RootScreen(root) } }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun continueIsDisabledUntilAnAgeGroupIsPicked() {
        show()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
        composeRule.onNodeWithText("9 to 11").performClick()
        composeRule.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun continueOpensTopicsAndBackReturnsToTheAges() {
        show()
        composeRule.onNodeWithText("9 to 11").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What do you like reading about?").assertIsDisplayed()

        back()
        composeRule.onNodeWithText("How old are you?").assertIsDisplayed()
        // The pick is still there.
        composeRule.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun doubleTapOnContinuePushesTopicsOnce() {
        show()
        composeRule.onNodeWithText("9 to 11").performClick()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        back()
        composeRule.onNodeWithText("How old are you?").assertIsDisplayed()
    }

    /** The nested back stack is saved with the Activity, the editor is not: the route carries the pick. */
    @Test
    fun topicsScreenRestoredAfterProcessDeathKeepsThePickedAgeGroup() {
        val tester = StateRestorationTester(composeRule)
        var editor = preferencesEditorFixture(ReaderPreferences(AgeGroup.FIFTEEN))
        tester.setContent { LeoTheme { OnboardingScreen(editor) } }
        composeRule.onNodeWithText("9 to 11").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()

        editor = preferencesEditorFixture(ReaderPreferences(AgeGroup.FIFTEEN))
        tester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What do you like reading about?").assertIsDisplayed()
        assertEquals(AgeGroup.NINE, editor.uiState.value.draft.ageGroup)
    }

    @Test
    fun doneSavesTheDraftAndTheAppAppears() {
        show()
        composeRule.onNodeWithText("9 to 11").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(AgeGroup.NINE), repository.saved.map { it.ageGroup })
        composeRule.onNodeWithText("Home placeholder (phase 6)").assertIsDisplayed()
    }
}
