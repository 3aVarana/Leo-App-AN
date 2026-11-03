package com.victorarana.leo.features.preferences

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.victorarana.leo.features.theme.LeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextsPerRoundStepperTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var count by mutableIntStateOf(6)

    private fun show(initial: Int = 6) {
        count = initial
        composeRule.setContent { LeoTheme { TextsPerRoundStepper(count, { count = it }) } }
    }

    @Test
    fun buttonsStepWithinTheRange() {
        show()
        composeRule.onNodeWithTag(STEPPER_PLUS_TAG, useUnmergedTree = true).performClick()
        assertEquals(7, count)
        composeRule.onNodeWithTag(STEPPER_MINUS_TAG, useUnmergedTree = true).performClick()
        assertEquals(6, count)
    }

    @Test
    fun minusIsDisabledAtThreeAndPlusAtNine() {
        show(3)
        composeRule.onNodeWithTag(STEPPER_MINUS_TAG, useUnmergedTree = true).assertIsNotEnabled()
        composeRule.onNodeWithTag(STEPPER_PLUS_TAG, useUnmergedTree = true).assertIsEnabled()
        count = 9
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STEPPER_PLUS_TAG, useUnmergedTree = true).assertIsNotEnabled()
        composeRule.onNodeWithTag(STEPPER_MINUS_TAG, useUnmergedTree = true).assertIsEnabled()
    }

    @Test
    fun isOneAdjustableNodeWithTheCountAsValue() {
        show()
        val node = composeRule.onNodeWithContentDescription("Texts per round", substring = true)
        val config = node.fetchSemanticsNode().config
        assertEquals("6", config[SemanticsProperties.StateDescription])
        val info = config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(3f..9f, info.range)
        assertEquals(5, info.steps)
        assertEquals(
            true,
            config[SemanticsProperties.ContentDescription].single().contains("How many short texts"),
        )
    }

    @Test
    fun setProgressAdjustsAndClamps() {
        show()
        val node = composeRule.onNodeWithContentDescription("Texts per round", substring = true)
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        assertEquals(8, count)
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        assertEquals(9, count)
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertEquals(3, count)
    }
}
