package com.victorarana.leo.features.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.theme.CappedFontScale
import com.victorarana.leo.features.theme.DOT_FONT_SCALE_CAP
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled

private const val FOOTNOTE = 13f

/**
 * A saved round's score as dots: one per answer, filled for right, ringed for missed. Unlike [ProgressDots], these
 * carry no numbers and no current position, just the shape of the result.
 *
 * @param results Whether each answer was right, in round order.
 */
@Composable
fun RoundResultDots(
    results: List<Boolean>,
    modifier: Modifier = Modifier,
) {
    val colors = LeoTheme.colors
    // Capped like `ProgressDots`, so a row of up to nine still fits across a phone.
    CappedFontScale(DOT_FONT_SCALE_CAP) {
        // The row and column already say the score; TalkBack doesn't need the dots too.
        DotRow(
            ideal = 8.dp.scaled(relativeTo = FOOTNOTE),
            spacing = 4.dp,
            modifier = modifier.clearAndSetSemantics {},
        ) {
            for (correct in results) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (correct) colors.ink else Color.Transparent, CircleShape)
                        .then(if (correct) Modifier else Modifier.border(1.5.dp, colors.magenta, CircleShape)),
                )
            }
        }
    }
}
