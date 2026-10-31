package com.victorarana.leo.snapshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.components.AgeRow
import com.victorarana.leo.features.components.AgeSegmentedControl
import com.victorarana.leo.features.components.AnswerRow
import com.victorarana.leo.features.components.CustomTopicChip
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.components.DottedLeaderRow
import com.victorarana.leo.features.components.HistoryButton
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.OptionState
import com.victorarana.leo.features.components.ProgressDots
import com.victorarana.leo.features.components.ProgressDotsSize
import com.victorarana.leo.features.components.ReviewLabel
import com.victorarana.leo.features.components.RoundResultDots
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.SkeletonLines
import com.victorarana.leo.features.components.TopicChip
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.toTextStyle
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val sixDots =
    listOf(
        DotState.Done,
        DotState.Current,
        DotState.Failed,
        DotState.CurrentCorrect,
        DotState.CurrentMissed,
        DotState.Upcoming,
    )
private val upcomingDots = List(6) { DotState.Upcoming }

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class ComponentsSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gallery() = composeRule.assertViewSnapshot(this::class, "gallery", heightDp = GALLERY_HEIGHT) { Gallery() }

    @Test
    fun galleryAt200Percent() =
        composeRule.assertViewSnapshot(
            this::class,
            "gallery-fontScale2",
            fontScale = 2f,
            heightDp = GALLERY_HEIGHT_2X,
        ) {
            Gallery()
        }

    /** At the largest font scale, in a 320dp frame (split screen): one row inside the frame. */
    @Test
    fun dotsFitANarrowFrameAt200Percent() =
        composeRule.assertViewSnapshot(this::class, "dots-narrow-fontScale2", fontScale = 2f) { NarrowDots() }

    /** Android only: segments at 1x, and the menu the control falls back to when they don't fit at 2x. */
    @Test
    fun ageSegmentedControl() =
        composeRule.assertViewSnapshot(this::class, "ageSegmentedControl", heightDp = SHORT) { AgeControl() }

    @Test
    fun ageSegmentedControlAt200Percent() =
        composeRule.assertViewSnapshot(
            this::class,
            "ageSegmentedControl-fontScale2",
            fontScale = 2f,
            heightDp = SHORT,
        ) {
            AgeControl()
        }

    /** At 2x in a 220dp frame the segments can't fit, so the control becomes a menu. */
    @Test
    fun ageSegmentedControlFallsBackToAMenu() =
        composeRule.assertViewSnapshot(this::class, "ageSegmentedControl-menu", fontScale = 2f, heightDp = SHORT) {
            AgeControl(Modifier.width(MENU_FRAME.dp))
        }

    private companion object {
        const val MENU_FRAME = 270
        const val GALLERY_HEIGHT = 1500
        const val GALLERY_HEIGHT_2X = 2200
        const val SHORT = 300
    }
}

/** Every shared component in its states, on the Broadsheet ground. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gallery() {
    ScrollingScreen {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 16.dp)) {
            Kicker("Now writing", color = LeoTheme.colors.accent700)
            ProgressDots(states = sixDots, currentIndex = 1)
            ProgressDots(states = upcomingDots, currentIndex = null, size = ProgressDotsSize.Large)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TopicChip(name = "Volcanoes", isOn = true, onToggle = {})
                TopicChip(name = "Pirates and treasure", isOn = false, onToggle = {})
                CustomTopicChip(name = "Chess", onRemove = {})
                CustomTopicChip(name = "Origami", onRemove = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnswerRow(marker = "A", text = "To measure how much snow falls", state = OptionState.Idle)
                AnswerRow(marker = "B", text = "To feel small shakes", state = OptionState.Correct)
                AnswerRow(
                    marker = "C",
                    text = "To find the best path",
                    state = OptionState.Incorrect,
                    note = "Your answer",
                )
                AnswerRow(marker = "D", text = "To keep the magma from moving", state = OptionState.Dimmed)
            }
            AgeRow(label = "9 to 11", description = "Everyday words · 80–120 words", isSelected = true, onClick = {})
            AgeRow(
                label = "12 to 14",
                description = "Some new vocabulary · 100–150 words",
                isSelected = false,
                onClick = {},
            )
            RoundResultDots(results = listOf(true, false, true, true, false, true))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryButton(ageGroup = AgeGroup.SIX, onClick = {})
                HistoryButton(ageGroup = AgeGroup.NINE, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ReviewLabel()
            }
            SkeletonLines()
            DottedLeaderRow(
                title = {
                    BasicText(
                        "The Sleeping Mountain",
                        style = LeoTextStyle.body.toTextStyle().copy(color = LeoTheme.colors.ink),
                    )
                },
                status = {
                    BasicText(
                        "Correct",
                        style = LeoTextStyle.body.toTextStyle().copy(color = LeoTheme.colors.ink),
                    )
                },
            )
            BasicText(
                "Explanations are set in the true italic.",
                style = LeoTextStyle.explanation.toTextStyle().copy(color = LeoTheme.colors.ink),
            )
            LeoButton(text = "Start", kind = LeoButtonKind.Primary, onClick = {})
            LeoButton(text = "Continue", kind = LeoButtonKind.Primary, onClick = {}, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LeoButton(text = "Add", kind = LeoButtonKind.Secondary, onClick = {})
                LeoButton(text = "Try a different topic", kind = LeoButtonKind.Ghost, onClick = {})
            }
        }
    }
}

@Composable
private fun NarrowDots() {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.width(320.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressDots(states = sixDots, currentIndex = 1)
            ProgressDots(states = upcomingDots, currentIndex = null, size = ProgressDotsSize.Large)
        }
    }
}

@Composable
private fun AgeControl(modifier: Modifier = Modifier) {
    var selection by remember { mutableStateOf(AgeGroup.NINE) }
    Column(modifier.padding(24.dp)) { AgeSegmentedControl(selection = selection, onSelect = { selection = it }) }
}
