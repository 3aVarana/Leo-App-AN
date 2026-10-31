package com.victorarana.leo.features.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.toTextStyle

private val ControlShape = RoundedCornerShape(2.dp)

// Android's minimum touch target; iOS draws the segments shorter (about 34pt).
private val MIN_TOUCH_HEIGHT = 48.dp

private enum class AgeControlSlot { Fit, Segments, Menu }

/**
 * The five age groups side by side. Falls back to a menu when the segments don't fit, at large font sizes.
 * (iOS `ViewThatFits`: a [SubcomposeLayout] measures the segments at their natural width first.)
 */
@Composable
fun AgeSegmentedControl(
    selection: AgeGroup,
    onSelect: (AgeGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val natural =
            subcompose(AgeControlSlot.Fit) { Segments(selection, onSelect) }
                .maxOf { it.measure(Constraints()).width }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else natural
        val slot = if (natural <= width) AgeControlSlot.Segments else AgeControlSlot.Menu
        val placeable =
            subcompose(slot) {
                if (slot == AgeControlSlot.Segments) {
                    Segments(selection, onSelect)
                } else {
                    AgeMenu(selection, onSelect)
                }
            }.first().measure(Constraints.fixedWidth(width).copy(maxHeight = constraints.maxHeight))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * The segments in one box with a hairline between them. Each is at least as wide as its label; when the row is
 * wider than the labels need, the segments are equal, or, if some label needs more than an equal share, every
 * segment gets the room its label needs plus an equal part of the rest (SwiftUI's flexible `HStack`).
 */
@Composable
private fun Segments(
    selection: AgeGroup,
    onSelect: (AgeGroup) -> Unit,
) {
    val colors = LeoTheme.colors
    Layout(
        content = {
            AgeGroup.entries.forEachIndexed { index, group ->
                if (index > 0) Box(Modifier.background(colors.divider))
                Segment(group, group == selection) { onSelect(group) }
            }
        },
        modifier = Modifier.selectableGroup().clip(ControlShape).border(1.dp, colors.divider, ControlShape),
    ) { all, constraints ->
        val divider = 1.dp.roundToPx()
        val dividers = all.filterIndexed { index, _ -> index % 2 == 1 }
        val segments = all.filterIndexed { index, _ -> index % 2 == 0 }
        val naturals = segments.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val gaps = divider * dividers.size
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth - gaps else naturals.sum()
        val share = available / segments.size
        val divided =
            if (naturals.all { it <= share }) {
                List(segments.size) { share }
            } else {
                val extra = (available - naturals.sum()).coerceAtLeast(0) / segments.size
                naturals.map { it + extra }
            }
        // The pixels the integer division leaves go to the last segment, so the control spans the whole row.
        val leftover = (available - divided.sum()).coerceAtLeast(0)
        val widths = divided.mapIndexed { index, width -> if (index == divided.lastIndex) width + leftover else width }
        val height = segments.zip(widths).maxOf { (m, w) -> m.minIntrinsicHeight(w) }
        val segmentPlaceables = segments.zip(widths).map { (m, w) -> m.measure(Constraints.fixed(w, height)) }
        val dividerPlaceables = dividers.map { it.measure(Constraints.fixed(divider, height)) }
        layout(segmentPlaceables.sumOf { it.width } + gaps, height) {
            var x = 0
            segmentPlaceables.forEachIndexed { index, placeable ->
                placeable.place(x, 0)
                x += placeable.width
                dividerPlaceables.getOrNull(index)?.let {
                    it.place(x, 0)
                    x += divider
                }
            }
        }
    }
}

@Composable
private fun Segment(
    group: AgeGroup,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LeoTheme.colors
    val displayName = stringResource(group.displayNameRes)
    Box(
        modifier =
            Modifier
                .heightIn(min = MIN_TOUCH_HEIGHT)
                .background(if (isSelected) colors.accent else Color.Transparent)
                .selectable(
                    selected = isSelected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).semantics { contentDescription = displayName }
                .padding(vertical = 9.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(group.shortNameRes),
            modifier = Modifier.clearAndSetSemantics {},
            style =
                LeoTextStyle.caption
                    .toTextStyle()
                    .copy(color = if (isSelected) colors.background else colors.ink),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AgeMenu(
    selection: AgeGroup,
    onSelect: (AgeGroup) -> Unit,
) {
    val colors = LeoTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.age_group_label)
    val value = stringResource(selection.displayNameRes)
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_HEIGHT)
                    .border(1.dp, colors.divider, ControlShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.DropdownList,
                    ) { expanded = true }
                    .semantics {
                        contentDescription = label
                        stateDescription = value
                    }.padding(vertical = 9.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = value,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
                style = LeoTextStyle.body.toTextStyle().copy(color = colors.ink),
            )
            LeoIcon(R.drawable.ic_unfold_more, colors.ink, 24.dp, Modifier.clearAndSetSemantics {})
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = ControlShape,
            containerColor = colors.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            for (group in AgeGroup.entries) {
                val name = stringResource(group.displayNameRes)
                DropdownMenuItem(
                    text = {
                        BasicText(
                            text = name,
                            style =
                                LeoTextStyle.body
                                    .toTextStyle()
                                    .copy(color = if (group == selection) colors.accent700 else colors.ink),
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(group)
                    },
                    // The Picker's checkmark: shown, and said, not only a color change.
                    trailingIcon = {
                        if (group ==
                            selection
                        ) {
                            LeoIcon(R.drawable.ic_check, colors.accent700, 20.dp, Modifier.clearAndSetSemantics {})
                        }
                    },
                    modifier = Modifier.semantics { selected = group == selection },
                )
            }
        }
    }
}
