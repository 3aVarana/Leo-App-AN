package com.victorarana.leo.features.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.toTextStyle

/** The bordered "Review" chip on a missed row, in Results and the round page. */
@Composable
fun ReviewLabel(modifier: Modifier = Modifier) {
    val colors = LeoTheme.colors
    BasicText(
        text = stringResource(R.string.review_label),
        modifier =
            modifier
                .border(1.dp, colors.divider, RoundedCornerShape(2.dp))
                .padding(vertical = 4.dp, horizontal = 10.dp),
        style =
            LeoTextStyle(size = 14f, weight = LeoWeight.Semibold)
                .toTextStyle()
                .copy(color = colors.magenta700),
        // `fixedSize()`: never wraps.
        softWrap = false,
        maxLines = 1,
    )
}
