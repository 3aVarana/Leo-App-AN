package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.quiz.WelcomeScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `WelcomeViewSnapshotTests`. The history button is passed, as on iOS, though the app hides it for now. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class WelcomeScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private val topics = listOf("Volcanoes", "The solar system", "Pirates and treasure", "Inventors", "Rainforests")

    private fun snapshot(
        name: String,
        group: AgeGroup,
        count: Int = 6,
        topics: List<String> = this.topics,
        fontScale: Float = 1f,
        heightDp: Int = 844,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        WelcomeScreen(
            ageGroup = group,
            exerciseCount = count,
            topicNames = topics,
            onStart = {},
            onSettings = {},
            onHistory = {},
        )
    }

    /** Different `displayName` lengths. */
    @Test
    fun ageGroupSix() = snapshot("six", AgeGroup.SIX)

    @Test
    fun ageGroupAdult() = snapshot("adult", AgeGroup.ADULT)

    /** Three topics or fewer are all named. */
    @Test
    fun fewTopics() = snapshot("two-topics", AgeGroup.NINE, topics = listOf("Volcanoes", "Inventors"))

    /** Before the round is planned. */
    @Test
    fun noTopicsYet() = snapshot("no-topics", AgeGroup.NINE, topics = emptyList())

    /** The shortest and the longest round: the number in the heading and the dots. */
    @Test
    fun threeTexts() = snapshot("3-texts", AgeGroup.NINE, count = 3)

    @Test
    fun nineTexts() = snapshot("9-texts", AgeGroup.NINE, count = 9)

    /** Nine dots must still fit across the width. */
    @Test
    fun nineTextsLargeFontScale() =
        snapshot("nine-texts-fontScale2", AgeGroup.SIX, count = 9, fontScale = 2f, heightDp = 1750)

    @Test
    fun largeFontScale() = snapshot("six-fontScale2", AgeGroup.SIX, fontScale = 2f, heightDp = 1700)
}
