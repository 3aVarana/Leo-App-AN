package com.victorarana.leo.features.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.support.PreferencesRepositorySpy
import com.victorarana.leo.support.StubModelAvailabilityProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each gate shows the right screen, and the graph navigates. No images: the screens have their own snapshots. */
@RunWith(RobolectricTestRunner::class)
class RootScreenTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = PreferencesRepositorySpy()
    private val availability = StubModelAvailabilityProvider()
    private val preferences = readerPreferencesFixture(AgeGroup.NINE)

    private fun show() {
        // The intro blocks taps while it plays; these tests are about the gates.
        val viewModel = RootViewModel(repository, availability).apply { onIntroPlayed() }
        composeRule.setContent { LeoTheme { RootScreen(viewModel) } }
        composeRule.waitForIdle()
    }

    @Test
    fun unavailableModelShowsTheUnavailableScreen() {
        availability.set(ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE))
        repository.stored = preferences
        show()
        composeRule.onNodeWithText("Gemini Nano needed").assertIsDisplayed()
    }

    @Test
    fun downloadingModelShowsProgressCopy() {
        availability.set(ModelAvailability.Downloading(fraction = 0.42f))
        show()
        composeRule.onNodeWithText("Getting Leo ready").assertIsDisplayed()
        // TalkBack reads the progress from the bar's own node, not from the visible percent.
        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "42%"))
            .assertIsDisplayed()
    }

    @Test
    fun availableWithoutPreferencesShowsOnboarding() {
        show()
        composeRule.onNodeWithText("Onboarding placeholder (phase 5)").assertIsDisplayed()
    }

    @Test
    fun availableWithPreferencesShowsTheApp() {
        repository.stored = preferences
        show()
        composeRule.onNodeWithText("Home placeholder (phase 6)").assertIsDisplayed()
    }

    /** The model finishing its download swaps the screen without a restart. */
    @Test
    fun screenSwapsToTheAppWhenTheModelBecomesAvailable() {
        repository.stored = preferences
        availability.set(ModelAvailability.Downloading(fraction = null))
        show()
        composeRule.onNodeWithText("Getting Leo ready").assertIsDisplayed()

        availability.set(ModelAvailability.Available)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home placeholder (phase 6)").assertIsDisplayed()
    }

    @Test
    fun homeNavigatesToHistoryAndBackPops() {
        repository.stored = preferences
        show()
        composeRule.onNodeWithText("Open history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("History placeholder (phase 7)").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home placeholder (phase 6)").assertIsDisplayed()
    }
}
