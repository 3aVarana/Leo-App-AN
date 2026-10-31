package com.victorarana.leo.features.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle

/** An age group in onboarding: its label, a description, and a check or an empty ring. */
@Composable
fun AgeRow(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LeoTheme.colors
    val checkSize = 28.dp.scaled(relativeTo = 22f)
    val ringSize = 24.dp.scaled(relativeTo = 22f)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // One TalkBack node that says whether it is selected (iOS: `.combine` and `.isSelected`).
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = label,
                style =
                    LeoTextStyle.ageLabel
                        .toTextStyle()
                        .copy(color = if (isSelected) colors.accent700 else colors.ink),
            )
            BasicText(text = description, style = LeoTextStyle.caption.toTextStyle().copy(color = colors.inkMuted))
        }
        if (isSelected) {
            LeoIcon(id = R.drawable.ic_check_circle, tint = colors.accent, size = checkSize)
        } else {
            Box(
                Modifier
                    .padding(horizontal = (checkSize - ringSize) / 2)
                    .size(ringSize)
                    .border(1.5.dp, colors.neutral600, CircleShape),
            )
        }
    }
}
