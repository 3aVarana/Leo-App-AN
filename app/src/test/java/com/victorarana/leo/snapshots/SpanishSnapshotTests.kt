package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.domain.roundAnswers
import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.features.history.HistoryScreen
import com.victorarana.leo.features.history.RoundScreen
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.features.quiz.ExerciseScreen
import com.victorarana.leo.features.quiz.ResultScreen
import com.victorarana.leo.features.quiz.WelcomeScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import com.victorarana.leo.support.exerciseQuizState
import com.victorarana.leo.support.preferencesEditorFixture
import com.victorarana.leo.support.threeSectionHistoryState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

/**
 * Android only (iOS has no non-English snapshots): the screens with the longest Spanish copy, to see that nothing
 * clips. The texts of the exercise are the English fixture; only the app's own strings are Spanish.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-$SNAPSHOT_DEVICE_QUALIFIERS")
class SpanishSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcome() =
        composeRule.assertViewSnapshot(this::class, "welcome") {
            WelcomeScreen(
                ageGroup = AgeGroup.NINE,
                exerciseCount = 6,
                topicNames = listOf("Volcanes", "El sistema solar", "Piratas y tesoros", "Inventores", "Selvas"),
                onStart = {},
                onSettings = {},
                onHistory = {},
            )
        }

    @Test
    fun wrongAnswer() {
        val exercise = exerciseFixture()
        composeRule.assertViewSnapshot(this::class, "wrong", heightDp = 1250) {
            ExerciseScreen(
                state = exerciseQuizState(exercise, selected = 0),
                exercise = exercise,
                isReadingTimed = true,
                onFinishReading = {},
                onSelect = {},
                onNext = {},
            )
        }
    }

    @Test
    fun results() =
        composeRule.assertViewSnapshot(this::class, "3-of-6", heightDp = 1000) {
            ResultScreen(
                answers = roundAnswers(listOf(true, false, true, false, false, true)),
                ageGroup = AgeGroup.NINE,
                onRestart = {},
                onSettings = {},
                onReview = {},
                onHistory = {},
            )
        }

    @Test
    fun settings() = settings("12-14", fontScale = 1f, heightDp = 1150)

    @Test
    fun settingsLargeFontScale() = settings("12-14-fontScale2", fontScale = 2f, heightDp = 2700)

    private fun settings(
        name: String,
        fontScale: Float,
        heightDp: Int,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        SettingsScreen(preferencesEditorFixture(ReaderPreferences(AgeGroup.TWELVE)), onCancel = {}, onDone = {})
    }

    @Test
    fun history() =
        composeRule.assertViewSnapshot(this::class, "three-sections", heightDp = 1700) {
            HistoryScreen(state = threeSectionHistoryState(), onOpen = {}, onDelete = {}, onBack = {})
        }

    @Test
    fun round() =
        composeRule.assertViewSnapshot(this::class, "2-missed", heightDp = 1700) {
            RoundScreen(
                round = roundRecordFixture(AgeGroup.NINE, results = listOf(true, false, true, true, false, true)),
                onReview = {},
                onBack = {},
                zone = ZoneOffset.UTC,
            )
        }
}
