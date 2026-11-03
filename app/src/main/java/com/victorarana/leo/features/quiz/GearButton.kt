package com.victorarana.leo.features.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.features.components.LeoIcon
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled

/**
 * The Settings button of Welcome and Results: the gear alone, 48dp to touch (iOS: `frame(minWidth: 44, minHeight: 44)`,
 * no outline, like the History button). [iconSize] is the symbol's size at the default font scale.
 */
@Composable
fun GearButton(
    onClick: () -> Unit,
    iconSize: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        LeoIcon(
            id = R.drawable.ic_settings,
            tint = LeoTheme.colors.ink,
            size = iconSize.dp.scaled(relativeTo = TITLE3),
            contentDescription = stringResource(R.string.settings_title),
        )
    }
}

/** The iOS `title3` size the gears scale with. */
private const val TITLE3 = 20f
