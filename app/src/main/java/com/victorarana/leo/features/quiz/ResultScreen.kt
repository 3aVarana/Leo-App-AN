package com.victorarana.leo.features.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.features.components.DottedLeaderRow
import com.victorarana.leo.features.components.HistoryButton
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.ReviewLabel
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.displayNameRes
import com.victorarana.leo.features.preferences.Headline
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.rememberResolvedLocale
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle
import java.text.NumberFormat

private const val GEAR_SIZE = 22f
private const val CALLOUT = 16f
private const val ROW_MIN_HEIGHT = 44f
private const val TITLE_LINE_HEIGHT = 24f
private const val PERFECT = 1.0
private const val GREAT = 0.8
private const val GOOD = 0.5

/** The font scale from which a row stacks its status under the title (iOS: `isAccessibilitySize`). */
internal const val STACKED_ROW_FONT_SCALE = 1.3f

/**
 * The score of the round, a row per text, and Review for the texts the reader missed. Port of `ResultView`.
 *
 * @param ageGroup The round just finished, for the kicker. Can differ from [historyAgeGroup] when the reader changes
 * age group in Settings before leaving Results.
 * @param onReview Opens Review at this position in the round's misses.
 * @param onHistory `null` hides the button: the current age group has no saved round to show yet.
 * @param historyAgeGroup The age group in the current preferences, which picks the History button's variant.
 */
@Composable
fun ResultScreen(
    answers: List<RoundAnswer>,
    ageGroup: AgeGroup,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onReview: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onHistory: (() -> Unit)? = null,
    historyAgeGroup: AgeGroup? = null,
) {
    val colors = LeoTheme.colors
    val correctCount = answers.count { it.isCorrect }
    val misses = answers.filterNot { it.isCorrect }
    ScrollingScreen(
        modifier = modifier,
        bottomBar = {
            LeoButton(stringResource(R.string.result_practice_again), LeoButtonKind.Primary, onRestart)
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onHistory != null) HistoryButton(ageGroup = historyAgeGroup ?: ageGroup, onClick = onHistory)
                GearButton(onClick = onSettings, iconSize = GEAR_SIZE)
            }
        }

        Kicker(
            text = stringResource(R.string.result_kicker, stringResource(ageGroup.displayNameRes)),
            modifier = Modifier.padding(top = 28.dp),
        )
        Headline(
            text = stringResource(R.string.result_score, correctCount, answers.size),
            style = LeoTextStyle.score.toTextStyle(),
            modifier = Modifier.padding(top = 10.dp),
        )
        BasicText(
            text = stringResource(resultMessage(correctCount, answers.size)),
            modifier = Modifier.padding(top = 14.dp),
            style = LeoTextStyle.resultMessage.toTextStyle().copy(color = colors.ink),
        )

        Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
            for (answer in answers) {
                ResultRowItem(answer) { onReview(misses.indexOfFirst { it.index == answer.index }) }
            }
        }
        Spacer(Modifier.weight(1f, min = 24.dp))
    }
}

private fun resultMessage(
    correctCount: Int,
    total: Int,
): Int {
    val ratio = correctCount.toDouble() / maxOf(total, 1)
    return when {
        ratio == PERFECT -> R.string.result_perfect
        ratio >= GREAT -> R.string.result_great
        ratio >= GOOD -> R.string.result_good
        else -> R.string.result_keep_going
    }
}

/** A correct row is read as one node; a missed one is a button that opens Review, with the hint "Review". */
@Composable
private fun ResultRowItem(
    answer: RoundAnswer,
    onReview: () -> Unit,
) {
    val title = answer.exercise.title
    if (answer.isCorrect) {
        val description = stringResource(R.string.result_row_correct, answer.index + 1, title)
        ResultRow(answer, Modifier.clearAndSetSemantics { contentDescription = description })
    } else {
        val description = stringResource(R.string.result_row_missed, answer.index + 1, title)
        // TalkBack has no hint: the action's label is read after the description ("double tap to Review").
        val actionLabel = stringResource(R.string.review_label)
        ResultRow(
            answer,
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onReview,
                ).clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(label = actionLabel) {
                        onReview()
                        true
                    }
                },
        )
    }
}

/**
 * A text's number, its title, a dotted leader and how it went. At large font scales the status moves under the title
 * and the leader is dropped.
 */
@Composable
private fun ResultRow(
    answer: RoundAnswer,
    modifier: Modifier = Modifier,
) {
    val isStacked = LocalDensity.current.fontScale >= STACKED_ROW_FONT_SCALE
    val minHeight = ROW_MIN_HEIGHT.dp.scaled(relativeTo = CALLOUT)
    val titleLine = TITLE_LINE_HEIGHT.dp.scaled(relativeTo = CALLOUT)
    // Stacked, the padding is the Row's (iOS pads the whole HStack), so the number stays on the title's first line.
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .then(if (isStacked) Modifier.padding(vertical = 6.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = if (isStacked) Alignment.Top else Alignment.CenterVertically,
    ) {
        // The number sits on the title's first line.
        Box(Modifier.height(titleLine).widthIn(min = 16.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(
                text = rememberNumberFormat().format(answer.index + 1),
                style = LeoTextStyle.answerMarker.toTextStyle().copy(color = LeoTheme.colors.ink),
                softWrap = false,
            )
        }
        if (isStacked) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RowTitle(answer)
                RowStatus(answer)
            }
        } else {
            DottedLeaderRow(
                modifier = Modifier.weight(1f),
                title = { RowTitle(answer) },
                status = { RowStatus(answer) },
            )
        }
    }
}

@Composable
private fun RowTitle(answer: RoundAnswer) {
    BasicText(
        text = answer.exercise.title,
        style =
            LeoTextStyle(size = CALLOUT, lineHeight = TITLE_LINE_HEIGHT)
                .toTextStyle()
                .copy(color = LeoTheme.colors.ink),
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun RowStatus(answer: RoundAnswer) {
    if (answer.isCorrect) {
        BasicText(
            text = stringResource(R.string.result_correct),
            style =
                LeoTextStyle(size = 14f, weight = LeoWeight.Semibold)
                    .toTextStyle()
                    .copy(color = LeoTheme.colors.accent700),
            softWrap = false,
        )
    } else {
        ReviewLabel()
    }
}

@Composable
private fun rememberNumberFormat(): NumberFormat {
    val locale = rememberResolvedLocale()
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}
