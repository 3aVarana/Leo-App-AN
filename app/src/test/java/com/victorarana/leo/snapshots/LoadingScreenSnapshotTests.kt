package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.quiz.LoadingScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `LoadingViewSnapshotTests`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class LoadingScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun dots(current: Int): List<DotState> =
        List(6) {
            when {
                it < current -> DotState.Done
                it == current -> DotState.Current
                else -> DotState.Upcoming
            }
        }

    private fun snapshot(
        name: String,
        index: Int,
        topic: String = "Volcanoes",
        group: AgeGroup = AgeGroup.NINE,
        fontScale: Float = 1f,
        heightDp: Int = 844,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        LoadingScreen(index = index, dots = dots(index), topicName = topic, ageGroup = group)
    }

    @Test
    fun firstOfSix() = snapshot("1-of-6", 0)

    @Test
    fun sixthOfSix() = snapshot("6-of-6", 5)

    /** A long topic name wraps in the headline. */
    @Test
    fun longTopic() =
        snapshot(
            "long-topic",
            1,
            topic = "A short fictional story about a teenager facing a challenge",
            group = AgeGroup.FIFTEEN,
        )

    @Test
    fun largeFontScale() = snapshot("2-of-6-fontScale2", 1, fontScale = 2f, heightDp = 1700)
}
