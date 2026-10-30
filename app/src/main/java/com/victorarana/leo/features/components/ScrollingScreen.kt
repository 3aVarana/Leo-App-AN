package com.victorarana.leo.features.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.leoScreenBackground
import kotlin.math.max

private val TopOffsetMinimum = 24.dp

/**
 * A screen's content in the readable column, scrolling when it doesn't fit. Flexible spacers inside
 * (`Spacer(Modifier.weight(1f))`, [TopOffset]) stretch to fill the screen, so they replace the mock's fixed top
 * offsets.
 *
 * The ground fills behind the system bars (edge to edge) and the content stays inside the safe area. The
 * [bottomBar] stays pinned below the content, above the navigation bar.
 */
@Composable
fun ScrollingScreen(
    modifier: Modifier = Modifier,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ScrollingScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .leoScreenBackground()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                ),
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val viewportHeight = maxHeight
            CompositionLocalProvider(LocalContentColor provides LeoTheme.colors.ink) {
                SpacerColumn(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .leoReadableWidth()
                        .heightIn(min = viewportHeight)
                        .then(
                            if (bottomBar == null) {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                            } else {
                                Modifier
                            },
                        ),
                    content = content,
                )
            }
        }
        if (bottomBar != null) LeoBottomBar(content = bottomBar)
    }
}

/** The scope of [ScrollingScreen]'s content: a column's `weight` and `align`, plus capped spacers. */
@LayoutScopeMarker
interface ScrollingScope {
    /** The child takes a share of the free height, like a `Spacer` in SwiftUI: [min] to [max] dp. */
    fun Modifier.weight(
        weight: Float = 1f,
        min: Dp = 0.dp,
        max: Dp = Dp.Infinity,
    ): Modifier

    fun Modifier.align(alignment: Alignment.Horizontal): Modifier
}

private val SpacerColumnScope: ScrollingScope =
    object : ScrollingScope {
        override fun Modifier.weight(
            weight: Float,
            min: Dp,
            max: Dp,
        ): Modifier = this.then(ScrollingChildData(weight = weight, min = min, max = max))

        override fun Modifier.align(alignment: Alignment.Horizontal): Modifier =
            this.then(ScrollingChildData(align = alignment))
    }

/** What a child tells [SpacerColumn]: its flexible share and cap, and where it sits across. */
private data class ScrollingChildData(
    val weight: Float = 0f,
    val min: Dp = 0.dp,
    val max: Dp = Dp.Infinity,
    val align: Alignment.Horizontal = Alignment.Start,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any {
        val previous = parentData as? ScrollingChildData ?: return this@ScrollingChildData
        // Two modifiers: the weight from one, the alignment from the other.
        return if (weight > 0f) copy(align = previous.align) else previous.copy(align = align)
    }
}

/**
 * A column that gives its free height to the weighted children the way SwiftUI does: every flexible child starts
 * at its minimum, the rest is shared by weight, and a child that would pass its maximum stops there and the others
 * share what it leaves. (Compose's own `Column` keeps the share a capped child doesn't use as empty space, so
 * "a top offset of at most 140, the rest below" can't be written with it.)
 */
@Composable
private fun SpacerColumn(
    modifier: Modifier,
    content: @Composable ScrollingScope.() -> Unit,
) {
    Layout(content = { SpacerColumnScope.content() }, modifier = modifier) { measurables, constraints ->
        val childConstraints = Constraints(maxWidth = constraints.maxWidth)
        val data = measurables.map { it.parentData as? ScrollingChildData ?: ScrollingChildData() }
        val fixed =
            measurables.indices
                .filter {
                    data[it].weight == 0f
                }.associateWith { measurables[it].measure(childConstraints) }
        val flexible = measurables.indices.filter { data[it].weight > 0f }
        val free = (constraints.minHeight - fixed.values.sumOf { it.height }).coerceAtLeast(0)
        val sizes = shareHeights(free, flexible.map { data[it] }, { with(this) { it.roundToPx() } })
        val flexibleSizes = flexible.zip(sizes).toMap()
        val placeables =
            measurables.mapIndexed { index, measurable ->
                fixed[index] ?: measurable.measure(
                    Constraints(
                        maxWidth = constraints.maxWidth,
                        minHeight = flexibleSizes.getValue(index),
                        maxHeight = flexibleSizes.getValue(index),
                    ),
                )
            }
        val width = constraints.maxWidth
        val height = max(constraints.minHeight, placeables.sumOf { it.height })
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                val x = data[index].align.align(placeable.width, width, layoutDirection)
                placeable.place(x, y)
                y += placeable.height
            }
        }
    }
}

/** The water-filling split: heights for [children], in order, from [free] pixels beyond the fixed children. */
private fun shareHeights(
    free: Int,
    children: List<ScrollingChildData>,
    toPx: (Dp) -> Int,
): List<Int> {
    val mins = children.map { toPx(it.min) }
    // A cap below the minimum cannot shrink the child under it.
    val maxes =
        children.indices.map { i ->
            if (children[i].max ==
                Dp.Infinity
            ) {
                Int.MAX_VALUE
            } else {
                maxOf(toPx(children[i].max), mins[i])
            }
        }
    val sizes = mins.toMutableList()
    val open = children.indices.toMutableList()
    var left = free - mins.sum()
    while (left > 0 && open.isNotEmpty()) {
        val totalWeight = open.sumOf { children[it].weight.toDouble() }
        var capped = false
        for (index in open.toList()) {
            val share = (left * children[index].weight / totalWeight).toInt()
            if (sizes[index] + share >= maxes[index]) {
                left -= maxes[index] - sizes[index]
                sizes[index] = maxes[index]
                open -= index
                capped = true
            }
        }
        if (!capped) {
            for (index in open) sizes[index] += (left * children[index].weight / totalWeight).toInt()
            break
        }
    }
    return sizes
}

/**
 * The space between the top of a screen and its headline: the mock's [height] when there's room, less on short
 * screens, never under 24dp. Call it inside a [ScrollingScreen].
 */
@Composable
fun ScrollingScope.TopOffset(height: Dp) {
    Spacer(Modifier.weight(1f, min = TopOffsetMinimum, max = height))
}

/** The screen's main buttons, pinned above the navigation bar. (iOS `leoBottomBar`, a safe-area inset.) */
@Composable
fun LeoBottomBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .background(LeoTheme.colors.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).leoReadableWidth()
                .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
