package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.features.preferences.OnboardingTopics
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.features.quiz.ExerciseScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import com.victorarana.leo.support.exerciseQuizState
import com.victorarana.leo.support.preferencesEditorFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The iOS Increase Contrast variants (accent, divider) on whole screens: Android 14's contrast setting, which the
 * theme reads from `UiModeManager`. Compare with the same screens in their own suites.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class HighContrastSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun correctAnswer() {
        val exercise = exerciseFixture()
        composeRule.assertViewSnapshot(this::class, "correct-highContrast", heightDp = 1250, highContrast = true) {
            ExerciseScreen(
                state = exerciseQuizState(exercise, selected = exercise.correctIndex),
                exercise = exercise,
                isReadingTimed = true,
                onFinishReading = {},
                onSelect = {},
                onNext = {},
            )
        }
    }

    @Test
    fun onboardingTopics() =
        composeRule.assertViewSnapshot(this::class, "topics-nine-highContrast", highContrast = true) {
            OnboardingTopics(preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE)))
        }

    @Test
    fun settings() =
        composeRule.assertViewSnapshot(this::class, "12-14-highContrast", heightDp = 1150, highContrast = true) {
            SettingsScreen(preferencesEditorFixture(ReaderPreferences(AgeGroup.TWELVE)), onCancel = {}, onDone = {})
        }
}
