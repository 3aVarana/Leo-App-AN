package com.victorarana.leo.features.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.features.theme.LeoColors
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle

private const val DIMMED_ALPHA = 0.7f
private const val ICON_ALPHA_HIDDEN = 0f
private const val ANSWER_LINE_HEIGHT = 24f

/**
 * An answer option: a marker (a letter, or "You" and "Answer" in Review), the text, and an icon once the question
 * is answered. Correct and incorrect never rely on color alone.
 *
 * @param note A line under the text, such as "Your answer" on a wrong pick.
 * @param readsMarker Letters are hidden from TalkBack, which reads the text and the state instead.
 */
@Composable
fun AnswerRow(
    marker: String,
    text: String,
    state: OptionState,
    modifier: Modifier = Modifier,
    note: String? = null,
    readsMarker: Boolean = false,
) {
    val palette = state.palette(LeoTheme.colors)
    val icon = state.iconRes
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (state == OptionState.Dimmed) DIMMED_ALPHA else 1f)
                .background(palette.fill, RoundedCornerShape(2.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        // The marker and the text share a line height, which lines their baselines up (iOS: firstTextBaseline).
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(
            text = marker,
            modifier = if (readsMarker) Modifier else Modifier.clearAndSetSemantics {},
            style =
                LeoTextStyle.answerMarker
                    .copy(
                        lineHeight = ANSWER_LINE_HEIGHT,
                    ).toTextStyle()
                    .copy(color = palette.marker),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(text = text, style = LeoTextStyle.answer.toTextStyle().copy(color = palette.text))
            if (note != null) {
                BasicText(text = note, style = LeoTextStyle.note.toTextStyle().copy(color = LeoTheme.colors.magenta700))
            }
        }
        // Always laid out, so the text keeps the same width when the icon appears.
        Box(
            Modifier.height(ANSWER_LINE_HEIGHT.dp.scaled(relativeTo = ANSWER_LINE_HEIGHT)),
            contentAlignment = Alignment.Center,
        ) {
            LeoIcon(
                id = icon ?: R.drawable.ic_check_circle,
                tint = if (icon == null) Color.Transparent else palette.icon,
                size = 22.dp.scaled(relativeTo = 17f),
                modifier = Modifier.alpha(if (icon == null) ICON_ALPHA_HIDDEN else 1f).clearAndSetSemantics {},
            )
        }
    }
}

private data class AnswerPalette(
    val fill: Color,
    val text: Color,
    val marker: Color,
    val icon: Color,
)

private val OptionState.iconRes: Int?
    get() =
        when (this) {
            OptionState.Correct -> R.drawable.ic_check_circle
            OptionState.Incorrect -> R.drawable.ic_cancel
            OptionState.Idle, OptionState.Dimmed -> null
        }

private fun OptionState.palette(colors: LeoColors): AnswerPalette =
    when (this) {
        OptionState.Correct -> AnswerPalette(colors.accent100, colors.accent900, colors.accent700, colors.accent700)
        OptionState.Incorrect ->
            AnswerPalette(
                colors.magenta100,
                colors.magenta900,
                colors.magenta700,
                colors.magenta600,
            )
        OptionState.Idle -> AnswerPalette(colors.surface, colors.ink, colors.accent700, colors.accent700)
        OptionState.Dimmed -> AnswerPalette(colors.surface, colors.ink, colors.ink, colors.accent700)
    }
