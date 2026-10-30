package com.victorarana.leo.features.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled

private const val LEADER_ALPHA = 0.45f

/** The dotted line that joins a title to its status on the Results screen. Hidden from TalkBack. */
@Composable
fun DottedLeader(modifier: Modifier = Modifier) {
    val color = LeoTheme.colors.ink.copy(alpha = LEADER_ALPHA)
    Spacer(
        modifier
            .height(1.dp)
            .clearAndSetSemantics {}
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 2.dp.toPx())),
                )
            },
    )
}

private val LeaderMinWidth = 20.dp
private val LeaderGap = 8.dp
private val LeaderRise = 8.dp
private const val BODY = 17f

/**
 * A title, a dotted leader and a status on one row, as in the Results rows (iOS: an `HStack` with the title at
 * `layoutPriority(1)` and a `DottedLeader` with `minWidth: 20`). The status keeps its own width at the trailing
 * edge, the title keeps its words and wraps only when it must, and the leader fills what is left (at least
 * 20dp). Written as a layout because Compose weights can't express "this child first, that one takes the rest".
 */
@Composable
fun DottedLeaderRow(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    status: @Composable () -> Unit,
) {
    // The dots sit a little above the text's bottom; iOS ties them to the last baseline, which moves with the text.
    val riseDp = LeaderRise.scaled(relativeTo = BODY)
    Layout(
        content = {
            Box(Modifier.layoutId(TITLE_ID)) { title() }
            DottedLeader(Modifier.layoutId(LEADER_ID))
            Box(Modifier.layoutId(STATUS_ID)) { status() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = LeaderGap.roundToPx()
        val minLeader = LeaderMinWidth.roundToPx()
        val rise = riseDp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val status = measurables.first { it.layoutId == STATUS_ID }.measure(loose)
        val bounded = constraints.hasBoundedWidth
        val titleRoom =
            if (bounded) {
                (constraints.maxWidth - status.width - 2 * gap - minLeader).coerceAtLeast(
                    0,
                )
            } else {
                Constraints.Infinity
            }
        val title = measurables.first { it.layoutId == TITLE_ID }.measure(loose.copy(maxWidth = titleRoom))
        // Unbounded (a horizontal scroll, an intrinsic query): the natural width, with the leader at its minimum.
        val width = if (bounded) constraints.maxWidth else title.width + status.width + 2 * gap + minLeader
        val leaderWidth = (width - title.width - status.width - 2 * gap).coerceAtLeast(minLeader)
        val leader =
            measurables
                .first {
                    it.layoutId == LEADER_ID
                }.measure(Constraints.fixedWidth(leaderWidth).copy(minHeight = 0))
        val height = maxOf(title.height, status.height)
        layout(width, height) {
            title.place(0, height - title.height)
            leader.place(title.width + gap, height - rise - leader.height)
            status.place(width - status.width, height - status.height)
        }
    }
}

private const val TITLE_ID = "title"
private const val LEADER_ID = "leader"
private const val STATUS_ID = "status"
