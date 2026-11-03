package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.roundAnswers
import com.victorarana.leo.features.preferences.OnboardingTopics
import com.victorarana.leo.features.quiz.ReviewScreen
import com.victorarana.leo.features.root.UnavailableScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import com.victorarana.leo.support.preferencesEditorFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Android only: Brazilian Portuguese, the other language of the catalog (files end in `-pt-BR`, like iOS). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "pt-rBR-$SNAPSHOT_DEVICE_QUALIFIERS")
class PortugueseSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingTopics() =
        composeRule.assertViewSnapshot(this::class, "topics-nine") {
            OnboardingTopics(preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE)))
        }

    @Test
    fun unavailableDeviceNotEligible() =
        composeRule.assertViewSnapshot(this::class, "device-not-eligible") {
            UnavailableScreen(ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE))
        }

    @Test
    fun review() =
        composeRule.assertViewSnapshot(this::class, "2-of-3", heightDp = 1300) {
            ReviewScreen(
                misses = roundAnswers(listOf(true, false, true, false, false, true)).filterNot { it.isCorrect },
                startIndex = 1,
                onBack = {},
            )
        }
}
