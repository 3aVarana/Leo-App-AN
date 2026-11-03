package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.quiz.GenerationFailedScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `GenerationFailedViewSnapshotTests`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class GenerationFailedScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(
        name: String,
        index: Int,
        fontScale: Float = 1f,
        heightDp: Int = 844,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        val dots =
            List(6) {
                when {
                    it < index -> DotState.Done
                    it == index -> DotState.Failed
                    else -> DotState.Upcoming
                }
            }
        GenerationFailedScreen(
            index = index,
            dots = dots,
            topicName = "Volcanoes",
            onRetry = {},
            onTryDifferentTopic = {},
        )
    }

    /** Text 1 has no earlier answers, text 2 one, text 3 several: each has its own reassurance. */
    @Test
    fun textOne() = snapshot("text-1", 0)

    @Test
    fun textTwo() = snapshot("text-2", 1)

    @Test
    fun textThree() = snapshot("text-3", 2)

    @Test
    fun largeFontScale() = snapshot("text-2-fontScale2", 1, fontScale = 2f, heightDp = 2000)
}
