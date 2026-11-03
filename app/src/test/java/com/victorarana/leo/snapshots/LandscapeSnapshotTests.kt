package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.features.quiz.ExerciseScreen
import com.victorarana.leo.features.quiz.WelcomeScreen
import com.victorarana.leo.support.OnboardingScreenHost
import com.victorarana.leo.support.SNAPSHOT_LANDSCAPE_QUALIFIERS
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
 * Android only: a phone on its side, 844x390dp. The screens scroll and the bottom bars stay in reach; a screen taller
 * than 390dp shows its top, and at 200% most of it is out of view by design.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_LANDSCAPE_QUALIFIERS)
class LandscapeSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private val exercise = exerciseFixture()

    @Test
    fun welcome() =
        composeRule.assertViewSnapshot(this::class, "welcome") {
            WelcomeScreen(
                ageGroup = AgeGroup.NINE,
                exerciseCount = 6,
                topicNames =
                    listOf(
                        "Volcanoes",
                        "The solar system",
                        "Pirates and treasure",
                        "Inventors",
                        "Rainforests",
                    ),
                onStart = {},
                onSettings = {},
                onHistory = {},
            )
        }

    @Test
    fun reading() = reading("reading", fontScale = 1f)

    @Test
    fun readingLargeFontScale() = reading("reading-fontScale2", fontScale = 2f)

    private fun reading(
        name: String,
        fontScale: Float,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale) {
        ExerciseScreen(
            state = exerciseQuizState(exercise, isReading = true),
            exercise = exercise,
            isReadingTimed = true,
            onFinishReading = {},
            onSelect = {},
            onNext = {},
        )
    }

    @Test
    fun onboardingAge() =
        composeRule.assertViewSnapshot(this::class, "onboarding-age") {
            OnboardingScreenHost(preferencesEditorFixture(ReaderPreferences(AgeGroup.FIFTEEN)))
        }

    @Test
    fun settings() =
        composeRule.assertViewSnapshot(this::class, "settings") {
            SettingsScreen(preferencesEditorFixture(ReaderPreferences(AgeGroup.TWELVE)), onCancel = {}, onDone = {})
        }
}
