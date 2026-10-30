package com.victorarana.leo.features.root

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoIcon
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.TopOffset
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LocalAnimatesPlaceholders
import com.victorarana.leo.features.theme.rememberReduceMotion
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle
import java.text.NumberFormat

private const val TITLE_SIZE = 40f
private const val PULSE_MILLIS = 900
private const val PULSE_LOW_ALPHA = 0.35f
private const val STATIC_ALPHA = 0.6f

/** The words of one state: a title, the message, and for [UnavailableCopy.detail] a second paragraph. */
private data class UnavailableCopy(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
    @param:StringRes val detail: Int? = null,
)

private fun ModelAvailability.unavailableCopy(): UnavailableCopy? =
    when (this) {
        ModelAvailability.Available -> null
        ModelAvailability.Downloadable, is ModelAvailability.Downloading ->
            UnavailableCopy(R.string.unavailable_preparing_title, R.string.unavailable_downloading_message)
        is ModelAvailability.Unavailable ->
            when (reason) {
                ModelUnavailableReason.DEVICE_NOT_ELIGIBLE ->
                    UnavailableCopy(
                        R.string.unavailable_title,
                        R.string.unavailable_device_not_eligible_message,
                        R.string.unavailable_device_not_eligible_detail,
                    )
                ModelUnavailableReason.DOWNLOAD_FAILED ->
                    UnavailableCopy(R.string.unavailable_title, R.string.unavailable_download_failed_message)
                ModelUnavailableReason.OTHER ->
                    UnavailableCopy(R.string.unavailable_title, R.string.unavailable_other_message)
            }
    }

/**
 * Shown instead of the app when the on-device model can't be used yet: not on this device, failed, or still
 * downloading. No button, like iOS (it can't act either, and a failed download gets no retry: plan Q3).
 * The port of `UnavailableView`, which takes a reason; here the model may also be on its way, so it takes the
 * whole [ModelAvailability] (D7). Draws nothing for [ModelAvailability.Available]: the root swaps screens first.
 */
@Composable
fun UnavailableScreen(
    availability: ModelAvailability,
    modifier: Modifier = Modifier,
) {
    val copy = availability.unavailableCopy() ?: return
    val colors = LeoTheme.colors
    ScrollingScreen(modifier) {
        TopOffset(height = 140.dp)
        LeoIcon(
            id = R.drawable.ic_auto_awesome,
            tint = colors.accent700,
            size = 40.dp.scaled(relativeTo = 34f),
            modifier =
                Modifier
                    .clearAndSetSemantics {
                    },
        )
        Kicker(text = stringResource(R.string.app_name), modifier = Modifier.padding(top = 16.dp))
        BasicText(
            text = stringResource(copy.title),
            modifier = Modifier.padding(vertical = 10.dp).semantics { heading() },
            style = LeoTextStyle.display(TITLE_SIZE).toTextStyle().copy(color = colors.ink),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                text = stringResource(copy.message),
                style = LeoTextStyle.body.toTextStyle().copy(color = colors.inkSoft),
            )
            copy.detail?.let {
                BasicText(
                    text = stringResource(it),
                    style = LeoTextStyle.body.toTextStyle().copy(color = colors.inkSoft),
                )
            }
            if (availability is ModelAvailability.Downloading || availability == ModelAvailability.Downloadable) {
                DownloadProgress(fraction = (availability as? ModelAvailability.Downloading)?.fraction)
            }
        }
        Spacer(Modifier.weight(1f, min = 24.dp))
    }
}

/** A square bar: determinate with the percent when the size is known, a pulse when not. */
@Composable
private fun DownloadProgress(fraction: Float?) {
    val colors = LeoTheme.colors
    val description = stringResource(R.string.unavailable_download_progress)
    val percent = fraction?.let { NumberFormat.getPercentInstance().format(it.toDouble()) }
    Column(
        modifier =
            Modifier
                .padding(top = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (fraction != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        stateDescription = percent.orEmpty()
                    } else {
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    }
                },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.neutral300)
                .clipToBounds(),
        ) {
            if (fraction != null) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(colors.accent))
            } else {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .pulsing()
                        .background(colors.accent),
                )
            }
        }
        if (percent != null) {
            BasicText(
                text = percent,
                modifier = Modifier.clearAndSetSemantics {},
                style = LeoTextStyle.caption.toTextStyle().copy(color = colors.inkMuted),
            )
        }
    }
}

/**
 * Pulses like [com.victorarana.leo.features.components.SkeletonLines]: still under reduced motion or in snapshots.
 * The value is read in `graphicsLayer`, so the pulse redraws the bar without recomposing it.
 */
@Composable
private fun Modifier.pulsing(): Modifier {
    if (rememberReduceMotion() || !LocalAnimatesPlaceholders.current) return graphicsLayer { alpha = STATIC_ALPHA }
    val pulse =
        rememberInfiniteTransition(label = "download").animateFloat(
            initialValue = 1f,
            targetValue = PULSE_LOW_ALPHA,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
            label = "pulse",
        )
    return graphicsLayer { alpha = pulse.value }
}
