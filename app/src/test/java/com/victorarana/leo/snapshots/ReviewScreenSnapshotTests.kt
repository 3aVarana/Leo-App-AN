package com.victorarana.leo.snapshots

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.R
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.domain.roundAnswers
import com.victorarana.leo.features.quiz.ReviewScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `ReviewViewSnapshotTests`: Review as Results pushes it, with its own back arrow and count. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class ReviewScreenSnapshotTests {
    private companion object {
        const val HEIGHT_DP = 1300
        const val LARGE_HEIGHT_DP = 2700
    }

    @get:Rule
    val composeRule = createComposeRule()

    private fun misses(results: List<Boolean>) = roundAnswers(results).filterNot { it.isCorrect }

    private fun snapshot(
        name: String,
        misses: List<RoundAnswer>,
        start: Int = 0,
        backToRound: Boolean = false,
        fontScale: Float = 1f,
    ) = composeRule.assertViewSnapshot(
        this::class,
        name,
        fontScale = fontScale,
        heightDp = if (fontScale > 1f) LARGE_HEIGHT_DP else HEIGHT_DP,
    ) {
        if (backToRound) {
            ReviewScreen(
                misses = misses,
                startIndex = start,
                onBack = {},
                backTitle = stringResource(R.string.review_back_to_round),
            )
        } else {
            ReviewScreen(misses = misses, startIndex = start, onBack = {})
        }
    }

    /** The only miss: "Back to results". */
    @Test
    fun oneMiss() = snapshot("1-of-1", misses(listOf(false, true, true, true, true, true)))

    /** The second of three misses: "Next missed text". */
    @Test
    fun secondOfThree() = snapshot("2-of-3", misses(listOf(true, false, true, false, false, true)), start = 1)

    @Test
    fun withoutExplanation() =
        snapshot(
            "no-explanation",
            listOf(RoundAnswer(index = 2, exercise = exerciseFixture(explanation = ""), selectedOption = 0)),
        )

    /** From the round page: "Back to round" instead of "Back to results". */
    @Test
    fun backToRound() =
        snapshot(
            "1-of-1-back-to-round",
            misses(listOf(false, true, true, true, true, true)),
            backToRound = true,
        )

    @Test
    fun largeFontScale() =
        snapshot("1-of-1-fontScale2", misses(listOf(false, true, true, true, true, true)), fontScale = 2f)
}
