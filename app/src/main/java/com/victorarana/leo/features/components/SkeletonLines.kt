package com.victorarana.leo.features.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LocalAnimatesPlaceholders
import com.victorarana.leo.features.theme.rememberReduceMotion

private val BarWidths = listOf(1f, 0.96f, 1f, 0.88f, 1f, 0.72f)
private val BarHeight = 9.dp
private val BarGap = 12.dp
private const val PULSE_MILLIS = 900
private const val STAGGER_MILLIS = 150
private const val PULSE_LOW_ALPHA = 0.35f
private const val STATIC_ALPHA = 0.6f

// Ease in and out, like SwiftUI's `.easeInOut`.
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Six placeholder bars for a text that's being written. They pulse, one after another, unless the system asks
 * for no motion. Hidden from TalkBack.
 */
@Composable
fun SkeletonLines(modifier: Modifier = Modifier) {
    val isPulsing = !rememberReduceMotion() && LocalAnimatesPlaceholders.current
    // Fixed bar heights, so the reader only sizes the width.
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        BarWidths.forEachIndexed { index, fraction ->
            Bar(fraction = fraction, index = index, isPulsing = isPulsing)
        }
    }
}

@Composable
private fun Bar(
    fraction: Float,
    index: Int,
    isPulsing: Boolean,
) {
    val alpha =
        if (isPulsing) {
            rememberInfiniteTransition(label = "skeleton")
                .animateFloat(
                    initialValue = 1f,
                    targetValue = PULSE_LOW_ALPHA,
                    animationSpec =
                        InfiniteRepeatableSpec(
                            animation = tween(PULSE_MILLIS, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * STAGGER_MILLIS),
                        ),
                    label = "bar$index",
                ).value
        } else {
            STATIC_ALPHA
        }
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(BarHeight)
            .alpha(alpha)
            .background(LeoTheme.colors.neutral300, RoundedCornerShape(1.dp)),
    )
}
