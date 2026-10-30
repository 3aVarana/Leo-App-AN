package com.victorarana.leo.features.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.theme.CappedFontScale
import com.victorarana.leo.features.theme.HEADER_BUTTON_FONT_SCALE_CAP
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled

/**
 * The entry into round history, beside the gear on Welcome and Results. For [AgeGroup.SIX], a text button with the
 * clock icon; every other age group gets the icon alone, like the gear.
 */
@Composable
fun HistoryButton(
    ageGroup: AgeGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.history_button_label)
    if (ageGroup == AgeGroup.SIX) {
        // Shares the header with the wordmark and the gear: stops growing past this size so the row keeps
        // fitting, like `ProgressDots`.
        CappedFontScale(HEADER_BUTTON_FONT_SCALE_CAP) {
            LeoButton(
                kind = LeoButtonKind.Ghost,
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                LeoIcon(
                    id = R.drawable.ic_history,
                    tint = LeoTheme.colors.accentLink,
                    size = 20.dp.scaled(relativeTo = 15f),
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.width(6.dp))
                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
            }
        }
    } else {
        Box(
            modifier =
                modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            LeoIcon(
                id = R.drawable.ic_history,
                tint = LeoTheme.colors.ink,
                size = 24.dp.scaled(relativeTo = 20f),
                contentDescription = label,
            )
        }
    }
}
