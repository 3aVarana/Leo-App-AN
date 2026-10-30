package com.victorarana.leo.features.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle

private const val PRESSED_ALPHA = 0.7f
private const val CHIP_SYMBOL_SIZE = 18f
private val ChipShape = RoundedCornerShape(2.dp)

/** A suggested topic the reader turns on or off. TalkBack reads it as a switch. */
@Composable
fun TopicChip(
    name: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    ChipLabel(
        name = name,
        isOn = isOn,
        leadingIcon = if (isOn) R.drawable.ic_check else R.drawable.ic_add,
        modifier =
            modifier
                .alpha(if (isPressed) PRESSED_ALPHA else 1f)
                .toggleable(
                    value = isOn,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = onToggle,
                ),
    )
}

/** A topic the reader added. Tapping it removes it; without [onRemove] it can't be removed. */
@Composable
fun CustomTopicChip(
    name: String,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onRemove == null) {
        ChipLabel(name = name, isOn = true, modifier = modifier)
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    ChipLabel(
        name = name,
        isOn = true,
        trailingIcon = R.drawable.ic_close,
        modifier =
            modifier
                .alpha(if (isPressed) PRESSED_ALPHA else 1f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    // TalkBack reads "Double tap to remove this topic": the Android form of iOS's hint.
                    onClickLabel = stringResource(R.string.custom_topic_chip_remove_action),
                    onClick = onRemove,
                ),
    )
}

/** On: accent fill. Off: a hairline border and quieter text. */
@Composable
private fun ChipLabel(
    name: String,
    isOn: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    trailingIcon: Int? = null,
) {
    val colors = LeoTheme.colors
    val foreground = if (isOn) colors.accent800 else colors.neutral700
    // iOS draws a 14pt medium-weight symbol, whose mark fills most of its box; a Material glyph fills about 70%.
    val symbolSize = CHIP_SYMBOL_SIZE.dp.scaled(relativeTo = 15f)
    Row(
        // Every chip reserves Android's 48dp touch height, the static one too, so chips in a row line up; the
        // drawn chip keeps its iOS size, centered.
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .background(if (isOn) colors.accent100 else Color.Transparent, ChipShape)
                .then(if (isOn) Modifier else Modifier.border(1.dp, colors.divider, ChipShape))
                .padding(vertical = 7.dp, horizontal = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = name },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { LeoIcon(it, foreground, symbolSize, Modifier.clearAndSetSemantics {}) }
        BasicText(
            text = name,
            modifier = Modifier.clearAndSetSemantics {},
            style = LeoTextStyle(size = 15f).toTextStyle().copy(color = foreground, textAlign = TextAlign.Start),
        )
        trailingIcon?.let { LeoIcon(it, foreground, symbolSize, Modifier.clearAndSetSemantics {}) }
    }
}
