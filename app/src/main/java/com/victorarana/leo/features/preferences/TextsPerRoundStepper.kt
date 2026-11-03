package com.victorarana.leo.features.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.features.components.LeoIcon
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.toTextStyle
import kotlin.math.roundToInt

/** Test tags of the two buttons, which TalkBack never sees (the stepper is one adjustable node). */
const val STEPPER_MINUS_TAG = "texts-per-round-minus"
const val STEPPER_PLUS_TAG = "texts-per-round-plus"

// Where the controls go below the label: stands in for `dynamicTypeSize.isAccessibilitySize`.
private const val ACCESSIBILITY_FONT_SCALE = 1.3f
private val ButtonWidth = 48.dp
private val ButtonHeight = 48.dp
private val PillInset = 8.dp
private const val DISABLED_ALPHA = 0.35f

/**
 * The number of texts in a round, shared by onboarding and settings. The caller puts its own heading above.
 * Port of `TextsPerRoundStepper`: a hand-drawn copy of the iOS stepper (a grey pill with a divider), since Compose
 * has none (D6). TalkBack gets one adjustable node: the title, the count as its value, the caption as its hint.
 */
@Composable
fun TextsPerRoundStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = RoundSettings.EXERCISE_COUNT_RANGE
    val title = stringResource(R.string.texts_per_round)
    val caption = stringResource(R.string.texts_per_round_caption)
    val isLarge = LocalDensity.current.fontScale >= ACCESSIBILITY_FONT_SCALE
    val controls = @Composable {
        Controls(
            canDecrease = count > range.first,
            canIncrease = count < range.last,
            onDecrease = { onCountChange((count - 1).coerceAtLeast(range.first)) },
            onIncrease = { onCountChange((count + 1).coerceAtMost(range.last)) },
        )
    }
    val steps = range.last - range.first - 1
    // Not merged from the children: the visual label repeats the count, and the buttons are reached by adjusting.
    val semantics =
        Modifier.clearAndSetSemantics {
            contentDescription = "$title. $caption"
            stateDescription = count.toString()
            progressBarRangeInfo =
                ProgressBarRangeInfo(count.toFloat(), range.first.toFloat()..range.last.toFloat(), steps)
            setProgress { value ->
                val target = value.roundToInt().coerceIn(range)
                onCountChange(target)
                true
            }
        }
    if (isLarge) {
        // The text would be squeezed beside the buttons, so they go below it.
        Column(modifier.then(semantics), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(title, caption, count)
            controls()
        }
    } else {
        Row(
            modifier.then(semantics).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { Label(title, caption, count) }
            controls()
        }
    }
}

@Composable
private fun Label(
    title: String,
    caption: String,
    count: Int,
) {
    val colors = LeoTheme.colors
    val headline = LeoTextStyle(size = 17f, weight = LeoWeight.Semibold).toTextStyle()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(title, style = headline.copy(color = colors.ink), modifier = Modifier.alignByBaseline())
            BasicText(
                count.toString(),
                style = headline.copy(color = colors.accent, fontFeatureSettings = "tnum"),
                modifier = Modifier.alignByBaseline(),
            )
        }
        BasicText(caption, style = LeoTextStyle.caption.toTextStyle().copy(color = colors.inkMuted))
    }
}

@Composable
private fun Controls(
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colors = LeoTheme.colors
    val inset = with(LocalDensity.current) { PillInset.toPx() }
    Row(
        Modifier
            .height(ButtonHeight)
            .drawBehind {
                // The pill is 32dp tall inside the 48dp touch height, like iOS's stepper.
                val pill = Size(size.width, size.height - 2 * inset)
                drawRoundRect(colors.neutral300, Offset(0f, inset), pill, CornerRadius(pill.height / 2))
                drawLine(
                    colors.ink.copy(alpha = DIVIDER_ALPHA),
                    Offset(size.width / 2, inset + pill.height * DIVIDER_INSET),
                    Offset(size.width / 2, inset + pill.height * (1 - DIVIDER_INSET)),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Button(R.drawable.ic_remove, canDecrease, onDecrease, STEPPER_MINUS_TAG)
        Button(R.drawable.ic_add, canIncrease, onIncrease, STEPPER_PLUS_TAG)
    }
}

private const val DIVIDER_ALPHA = 0.4f
private const val DIVIDER_INSET = 0.2f

@Composable
private fun Button(
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        Modifier
            .width(ButtonWidth)
            .fillMaxHeight()
            .testTag(tag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        LeoIcon(icon, LeoTheme.colors.ink, 20.dp)
    }
}
