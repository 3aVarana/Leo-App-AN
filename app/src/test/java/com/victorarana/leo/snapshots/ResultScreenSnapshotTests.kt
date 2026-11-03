package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.roundAnswers
import com.victorarana.leo.features.quiz.ResultScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `ResultViewSnapshotTests`. The history button is passed, as on iOS, though the app hides it for now. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class ResultScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(
        name: String,
        correct: List<Boolean>,
        ageGroup: AgeGroup = AgeGroup.NINE,
        fontScale: Float = 1f,
        heightDp: Int = 1000,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        ResultScreen(
            answers = roundAnswers(correct),
            ageGroup = ageGroup,
            onRestart = {},
            onSettings = {},
            onReview = {},
            onHistory = {},
        )
    }

    /** Together these cover every message: 6/6 has no Review rows, 0/6 only Review rows. */
    @Test
    fun sixOfSix() = snapshot("6-of-6", listOf(true, true, true, true, true, true))

    @Test
    fun fiveOfSix() = snapshot("5-of-6", listOf(false, true, true, true, true, true))

    @Test
    fun threeOfSix() = snapshot("3-of-6", listOf(true, false, true, false, false, true))

    @Test
    fun zeroOfSix() = snapshot("0-of-6", listOf(false, false, false, false, false, false))

    /** The "Past rounds" text button, for 6-8. */
    @Test
    fun historyButtonForSix() =
        snapshot("6-of-6-six", listOf(true, true, true, true, true, true), ageGroup = AgeGroup.SIX)

    /** The rows stack, without leaders. */
    @Test
    fun largeFontScale() =
        snapshot("5-of-6-fontScale2", listOf(false, true, true, true, true, true), fontScale = 2f, heightDp = 2700)
}
