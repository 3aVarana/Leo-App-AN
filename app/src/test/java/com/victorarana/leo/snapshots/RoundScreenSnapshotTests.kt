package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.features.history.RoundScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

/**
 * Port of `RoundViewSnapshotTests`. The kicker's clock time is in UTC (D8), so it differs from the iOS images, which
 * show the simulator's zone; the bar with the back button is Android's stand-in for the navigation bar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class RoundScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(
        name: String,
        results: List<Boolean>,
        heightDp: Int,
        fontScale: Float = 1f,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        RoundScreen(
            round = roundRecordFixture(AgeGroup.NINE, results = results),
            onReview = {},
            onBack = {},
            zone = ZoneOffset.UTC,
        )
    }

    @Test
    fun twoMissed() = snapshot("2-missed", listOf(true, false, true, true, false, true), heightDp = 1700)

    /** No misses: no bottom button. */
    @Test
    fun sixOfSix() = snapshot("6-of-6", listOf(true, true, true, true, true, true), heightDp = 1500)

    @Test
    fun accessibilitySize() =
        snapshot("2-missed-fontScale2", listOf(true, false, true, true, false, true), heightDp = 2700, fontScale = 2f)
}
