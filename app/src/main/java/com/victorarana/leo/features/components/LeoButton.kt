package com.victorarana.leo.features.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle

private const val DISABLED_ALPHA = 0.45f
private const val SECONDARY_PRESSED_ALPHA = 0.14f
private const val GHOST_PRESSED_ALPHA = 0.18f
private val ButtonShape = RoundedCornerShape(2.dp)

/**
 * A Broadsheet button with any [content], laid out in a centered row. Hand-built instead of Material's `Button`
 * so nothing looks stock: no ripple, no elevation, no 48dp pill. The content reads its text style and color from
 * the locals, so a plain `Text` is styled for the [kind].
 */
@Composable
fun LeoButton(
    kind: LeoButtonKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LeoTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primaryHeight = 52.dp.scaled(relativeTo = 17f)
    val secondaryHeight = 44.dp.scaled(relativeTo = 15f)

    val textStyle: LeoTextStyle
    val foreground: Color
    val fill: Color
    val frame: Modifier
    val horizontalPadding =
        when (kind) {
            LeoButtonKind.Primary -> 16.dp
            LeoButtonKind.Secondary -> 14.dp
            LeoButtonKind.Ghost -> 4.dp
        }
    when (kind) {
        LeoButtonKind.Primary -> {
            textStyle = LeoTextStyle(size = 17f, weight = LeoWeight.Semibold)
            foreground = colors.background
            fill = if (isPressed) colors.accentPressed else colors.accent
            frame = Modifier.fillMaxWidth().heightIn(min = primaryHeight)
        }
        LeoButtonKind.Secondary -> {
            textStyle = LeoTextStyle(size = 15f, weight = LeoWeight.Semibold)
            foreground = colors.ink
            fill = if (isPressed) colors.ink.copy(alpha = SECONDARY_PRESSED_ALPHA) else Color.Transparent
            frame = Modifier.heightIn(min = secondaryHeight)
        }
        LeoButtonKind.Ghost -> {
            textStyle = LeoTextStyle(size = 16f, weight = LeoWeight.Semibold)
            foreground = colors.accentLink
            fill = if (isPressed) colors.accent.copy(alpha = GHOST_PRESSED_ALPHA) else Color.Transparent
            frame = Modifier.heightIn(min = secondaryHeight)
        }
    }

    // At least 48dp to touch (Android's minimum, `minimumInteractiveComponentSize`): the 44dp secondary and ghost
    // frames keep their size and are centered in it.
    CompositionLocalProvider(
        LocalContentColor provides foreground,
        LocalTextStyle provides textStyle.toTextStyle().copy(textAlign = TextAlign.Center),
    ) {
        Row(
            modifier =
                modifier
                    .minimumInteractiveComponentSize()
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    .then(frame)
                    .background(fill, ButtonShape)
                    .then(
                        if (kind == LeoButtonKind.Secondary) {
                            Modifier.border(BorderStroke(1.dp, colors.divider), ButtonShape)
                        } else {
                            Modifier
                        },
                    ).clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    ).padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A button whose label is [text]. */
@Composable
fun LeoButton(
    text: String,
    kind: LeoButtonKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LeoButton(kind = kind, onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
}
