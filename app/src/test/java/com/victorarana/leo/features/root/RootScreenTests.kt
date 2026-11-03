package com.victorarana.leo.features.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.support.PreferencesRepositorySpy
import com.victorarana.leo.support.RoundHistoryRepositorySpy
import com.victorarana.leo.support.StubModelAvailabilityProvider
import com.victorarana.leo.support.StubTopicReviewRepository
import com.victorarana.leo.support.testHistory
import com.victorarana.leo.support.testQuiz
import kotlinx.coroutines.runBlocking
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

    private val history = RoundHistoryRepositorySpy()

    private fun show() {
        // The intro blocks taps while it plays; these tests are about the gates.
        val viewModel =
            RootViewModel(
                repository,
                availability,
                StubTopicReviewRepository(),
                TopicNames.English,
                testQuiz(),
                testHistory(history),
            ).apply { onIntroPlayed() }
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
        composeRule.onNodeWithText("How old are you?").assertIsDisplayed()
    }

    @Test
    fun availableWithPreferencesShowsTheApp() {
        repository.stored = preferences
        show()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
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
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    /** A check that briefly says "unavailable" doesn't reset the back stack. */
    @Test
    fun theBackStackSurvivesTheModelBrieflyBecomingUnavailable() {
        repository.stored = preferences
        show()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        availability.set(ModelAvailability.Unavailable(ModelUnavailableReason.OTHER))
        composeRule.waitForIdle()
        availability.set(ModelAvailability.Available)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reading timer").assertIsDisplayed()
    }

    /** The same check mid-onboarding keeps the reader on the topics screen, with their age still picked. */
    @Test
    fun onboardingProgressSurvivesTheModelBrieflyBecomingUnavailable() {
        show()
        composeRule.onNodeWithText("9 to 11").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()

        availability.set(ModelAvailability.Unavailable(ModelUnavailableReason.OTHER))
        composeRule.waitForIdle()
        availability.set(ModelAvailability.Available)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What do you like reading about?").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").assertIsEnabled()
    }

    /** With a saved round, Home's History button opens History, and back pops to Home (iOS: `isShowingHistory`). */
    @Test
    fun homeNavigatesToHistoryAndBackPops() {
        repository.stored = preferences
        runBlocking { history.save(roundRecordFixture(AgeGroup.NINE)) }
        show()
        composeRule.onNodeWithContentDescription("Past rounds").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    /** H6: nothing to show yet, no button. */
    @Test
    fun historyButtonHiddenWithoutRounds() {
        repository.stored = preferences
        show()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Past rounds").assertCountEquals(0)
    }

    /** Rounds of another age group don't count: the button follows the current preferences. */
    @Test
    fun historyButtonIgnoresRoundsOfAnotherAgeGroup() {
        repository.stored = preferences
        runBlocking { history.save(roundRecordFixture(AgeGroup.TWELVE)) }
        show()
        composeRule.onAllNodesWithContentDescription("Past rounds").assertCountEquals(0)
    }
}
