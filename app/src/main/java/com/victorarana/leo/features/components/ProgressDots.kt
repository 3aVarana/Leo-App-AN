package com.victorarana.leo.features.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorarana.leo.R
import com.victorarana.leo.features.theme.CappedFontScale
import com.victorarana.leo.features.theme.DOT_FONT_SCALE_CAP
import com.victorarana.leo.features.theme.LeoColors
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

/** The numbered circles of a round, from three to nine. */
enum class ProgressDotsSize(
    val diameter: Dp,
    val numberSize: Float,
) {
    /** 28dp, above Loading, Error and Exercise. */
    Regular(28.dp, 13f),

    /** 36dp with Ink numbers, on Welcome. */
    Large(36.dp, 15f),
}

private val DotSpacing = 8.dp
private val DotRingWidth = 1.5.dp
private val DotUpcomingRingWidth = 1.dp
private const val MIN_NUMBER_SCALE = 0.5f
private const val FOOTNOTE = 13f

/**
 * @param currentIndex Read by TalkBack as "Text 2 of 6". `null` hides the dots from TalkBack.
 * @param alignment Centered above Loading, Error and Exercise; start on Welcome.
 */
@Composable
fun ProgressDots(
    states: List<DotState>,
    currentIndex: Int?,
    modifier: Modifier = Modifier,
    size: ProgressDotsSize = ProgressDotsSize.Regular,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val label = currentIndex?.let { stringResource(R.string.progress_dots_accessibility_label, it + 1, states.size) }
    // Up to nine dots must fit across a phone, so the circles and their numbers stop growing past the largest
    // standard size, and the row shrinks all of them equally below that when it must.
    CappedFontScale(DOT_FONT_SCALE_CAP) {
        DotRow(
            ideal = size.diameter.scaled(relativeTo = FOOTNOTE),
            spacing = DotSpacing,
            modifier =
                modifier
                    .fillMaxWidth()
                    .wrapContentWidth(alignment)
                    .clearAndSetSemantics { label?.let { contentDescription = it } },
        ) {
            states.forEachIndexed { index, state -> Dot(number = index + 1, state = state, size = size) }
        }
    }
}

@Composable
private fun Dot(
    number: Int,
    state: DotState,
    size: ProgressDotsSize,
) {
    val style = state.dotStyle(size, LeoTheme.colors)
    val numberStyle =
        LeoTextStyle(size = size.numberSize, weight = LeoWeight.Semibold)
            .toTextStyle()
            .copy(color = style.number, textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(style.fill, CircleShape)
                .then(style.ring?.let { (color, width) -> Modifier.border(width, color, CircleShape) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = rememberIntegerFormat().format(number),
            style = numberStyle,
            maxLines = 1,
            // `minimumScaleFactor(0.5)`: the number shrinks with its circle, to half its size.
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize = (size.numberSize * MIN_NUMBER_SCALE).sp,
                    maxFontSize = numberStyle.fontSize,
                ),
        )
    }
}

/**
 * A single row of square dots. Every dot gets the same side: [ideal], or less when the row is narrower than all
 * of them side by side. (iOS `DotRow`, a SwiftUI `Layout`.)
 */
@Composable
fun DotRow(
    ideal: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val idealPx = with(density) { ideal.toPx() }
        val spacingPx = with(density) { spacing.toPx() }
        val proposed = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else null
        val side = DotRow.side(idealPx, measurables.size, spacingPx, proposed).toInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(side, side)) }
        val gaps = spacingPx.toInt() * max(0, placeables.size - 1)
        layout(side * placeables.size + gaps, side) {
            var x = 0
            for (placeable in placeables) {
                placeable.place(x, 0)
                x += side + spacingPx.toInt()
            }
        }
    }
}

object DotRow {
    /** The side of each dot. `null` for [proposedWidth] means no limit, so the ideal diameter. */
    fun side(
        ideal: Float,
        count: Int,
        spacing: Float,
        proposedWidth: Float?,
    ): Float {
        if (proposedWidth == null || !proposedWidth.isFinite() || count <= 0) return ideal
        val fitting = (proposedWidth - spacing * (count - 1)) / count
        return max(0f, min(ideal, fitting))
    }
}

private data class DotStyle(
    val number: Color,
    val fill: Color,
    val ring: Pair<Color, Dp>?,
)

private fun DotState.dotStyle(
    size: ProgressDotsSize,
    colors: LeoColors,
): DotStyle =
    when (this) {
        DotState.Done -> DotStyle(colors.background, colors.ink, null)
        DotState.CurrentCorrect -> DotStyle(colors.background, colors.accent, null)
        DotState.CurrentMissed -> DotStyle(colors.background, colors.magenta, null)
        DotState.Current -> DotStyle(colors.accent700, Color.Transparent, colors.accent to DotRingWidth)
        DotState.Failed -> DotStyle(colors.magenta700, Color.Transparent, colors.magenta to DotRingWidth)
        DotState.Upcoming ->
            DotStyle(
                number = if (size == ProgressDotsSize.Large) colors.ink else colors.neutral700,
                fill = Color.Transparent,
                ring = colors.divider to DotUpcomingRingWidth,
            )
    }

/** The app's locale, like the strings around the dots (a per-app language can differ from the JVM default). */
@Composable
private fun rememberIntegerFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}
