package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.features.history.HistoryScreen
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.features.quiz.ExerciseScreen
import com.victorarana.leo.features.quiz.WelcomeScreen
import com.victorarana.leo.support.SNAPSHOT_TABLET_QUALIFIERS
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

/**
 * Android only (iOS has no iPad snapshots): a Pixel Tablet in landscape, 1280x800dp. There is no tablet layout, as on
 * iOS: the same column, 648dp wide with its gutters, centered and start-aligned. A screen taller than 800dp scrolls,
 * so the image shows its top.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_TABLET_QUALIFIERS)
class TabletSnapshotTests {
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
    fun reading() =
        composeRule.assertViewSnapshot(this::class, "reading") {
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
    fun wrongAnswer() =
        composeRule.assertViewSnapshot(this::class, "wrong") {
            ExerciseScreen(
                state = exerciseQuizState(exercise, selected = 0),
                exercise = exercise,
                isReadingTimed = true,
                onFinishReading = {},
                onSelect = {},
                onNext = {},
            )
        }

    @Test
    fun settings() =
        composeRule.assertViewSnapshot(this::class, "settings") {
            SettingsScreen(preferencesEditorFixture(ReaderPreferences(AgeGroup.TWELVE)), onCancel = {}, onDone = {})
        }

    @Test
    fun history() =
        composeRule.assertViewSnapshot(this::class, "history") {
            HistoryScreen(state = threeSectionHistoryState(), onOpen = {}, onDelete = {}, onBack = {})
        }
}
