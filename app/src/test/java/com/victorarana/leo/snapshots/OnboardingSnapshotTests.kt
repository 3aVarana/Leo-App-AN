package com.victorarana.leo.snapshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.features.preferences.AgeGroupPicker
import com.victorarana.leo.features.preferences.OnboardingRoundSection
import com.victorarana.leo.features.preferences.OnboardingScreen
import com.victorarana.leo.features.preferences.OnboardingTopics
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.leoScreenBackground
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import com.victorarana.leo.support.preferencesEditorFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `OnboardingSnapshotTests`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class OnboardingSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    /** Continue is disabled until an age group is picked. */
    @Test
    fun pickerWithoutSelection() = composeRule.assertViewSnapshot(this::class, "none") { AgeGroupPicker(null, {}, {}) }

    @Test
    fun pickerWithSelection() =
        composeRule.assertViewSnapshot(this::class, "12-14") { AgeGroupPicker(AgeGroup.TWELVE, {}, {}) }

    @Test
    fun pickerAtLargeFontScale() =
        composeRule.assertViewSnapshot(this::class, "12-14-fontScale2", fontScale = 2f, heightDp = 2400) {
            AgeGroupPicker(AgeGroup.TWELVE, {}, {})
        }

    @Test
    fun firstScreen() =
        composeRule.assertViewSnapshot(this::class, "first-screen") {
            OnboardingScreen(preferencesEditorFixture(ReaderPreferences(AgeGroup.FIFTEEN)))
        }

    /** The second screen as it's pushed. */
    @Test
    fun topicsScreenNine() =
        composeRule.assertViewSnapshot(this::class, "topics-nine") {
            OnboardingTopics(preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE)))
        }

    @Test
    fun topicsScreenFifteen() =
        composeRule.assertViewSnapshot(this::class, "topics-fifteen", heightDp = 1450) {
            OnboardingTopics(preferencesEditorFixture(ReaderPreferences(AgeGroup.FIFTEEN)))
        }

    /** The round section below the topics, at a size where the stepper's controls go below its label. */
    @Test
    fun roundRowAtLargeFontScale() =
        composeRule.assertViewSnapshot(this::class, "round-fontScale2", fontScale = 2f, heightDp = 700) {
            Column(Modifier.fillMaxSize().leoScreenBackground().leoReadableWidth()) {
                OnboardingRoundSection(RoundSettings.DEFAULT_EXERCISE_COUNT, {})
            }
        }
}
