package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import com.victorarana.leo.features.root.UnavailableScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class UnavailableScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(
        name: String,
        availability: ModelAvailability,
        fontScale: Float = 1f,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale) { UnavailableScreen(availability) }

    private fun unavailable(reason: ModelUnavailableReason) = ModelAvailability.Unavailable(reason)

    @Test
    fun deviceNotEligible() = snapshot("device-not-eligible", unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE))

    @Test
    fun downloadFailed() = snapshot("download-failed", unavailable(ModelUnavailableReason.DOWNLOAD_FAILED))

    @Test
    fun other() = snapshot("other", unavailable(ModelUnavailableReason.OTHER))

    @Test
    fun downloadable() = snapshot("downloadable", ModelAvailability.Downloadable)

    @Test
    fun downloadingIndeterminate() =
        snapshot("downloading-indeterminate", ModelAvailability.Downloading(fraction = null))

    @Test
    fun downloadingFortyTwoPercent() = snapshot("downloading-42", ModelAvailability.Downloading(fraction = 0.42f))

    @Test
    fun deviceNotEligibleAt200Percent() =
        snapshot(
            "device-not-eligible-fontScale2",
            unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE),
            fontScale = 2f,
        )

    @Test
    fun downloadingFortyTwoPercentAt200Percent() =
        snapshot("downloading-42-fontScale2", ModelAvailability.Downloading(fraction = 0.42f), fontScale = 2f)
}
