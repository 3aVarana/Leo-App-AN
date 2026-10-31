package com.victorarana.leo.features.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * A vector drawable from `res/drawable/ic_*.xml`, tinted and sized, in place of an SF Symbol. Decorative unless
 * [contentDescription] is set, like `accessibilityHidden(true)` on the iOS symbols.
 */
@Composable
fun LeoIcon(
    @DrawableRes id: Int,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
