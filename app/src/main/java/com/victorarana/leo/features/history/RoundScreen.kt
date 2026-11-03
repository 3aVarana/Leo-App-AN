package com.victorarana.leo.features.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.features.components.BackBar
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.ReviewLabel
import com.victorarana.leo.features.components.RoundResultDots
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.displayNameRes
import com.victorarana.leo.features.preferences.Headline
import com.victorarana.leo.features.quiz.STACKED_ROW_FONT_SCALE
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.toTextStyle
import java.time.ZoneId

private const val TITLE_SIZE = 17f
private const val TITLE_LINE_HEIGHT = 24f
private const val STATUS_SIZE = 14f

/**
 * One saved round: the score and dots, then each text with its topic, skill and how it went. Port of `RoundView`.
 * History loads the round before it navigates here, so there is no blank first frame.
 *
 * @param onReview Opens Review at this position in the round's misses ("Review N missed" opens it at 0).
 * @param zone The time zone of the kicker's date and time (the one History's rows use).
 */
@Composable
fun RoundScreen(
    round: RoundRecord,
    onReview: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val misses = round.misses
    ScrollingScreen(
        modifier = modifier,
        topBar = { BackBar(parentTitle = stringResource(R.string.navigation_back), onBack = onBack) },
        bottomBar =
            if (misses.isEmpty()) {
                null
            } else {
                {
                    LeoButton(
                        text = stringResource(R.string.round_review_missed, misses.size),
                        kind = LeoButtonKind.Secondary,
                        onClick = { onReview(0) },
                    )
                }
            },
    ) {
        Kicker(text = kickerText(round, zone), modifier = Modifier.padding(top = 28.dp))
        Headline(
            text = stringResource(R.string.result_score, round.correctCount, round.answers.size),
            style = LeoTextStyle.score.toTextStyle(),
        )
        RoundResultDots(results = round.answers.map { it.isCorrect }, modifier = Modifier.padding(top = 14.dp))
        Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            for (answer in round.answers) {
                RoundAnswerRow(answer) { onReview(misses.indexOfFirst { it.index == answer.index }) }
            }
        }
        Spacer(Modifier.weight(1f, min = 24.dp))
    }
}

@Composable
private fun kickerText(
    round: RoundRecord,
    zone: ZoneId,
): String {
    val locale = currentLocale()
    val is24Hour =
        android.text.format.DateFormat
            .is24HourFormat(LocalContext.current)
    val dateTime =
        remember(round.finishedAt, zone, locale, is24Hour) { roundKicker(round.finishedAt, zone, locale, is24Hour) }
    return stringResource(R.string.round_kicker, dateTime, stringResource(round.ageGroup.displayNameRes))
}

/** A correct row is read as one node; a missed one is a button that opens Review at that miss. */
@Composable
private fun RoundAnswerRow(
    answer: RoundAnswer,
    onReview: () -> Unit,
) {
    val subtitle = subtitle(answer)
    if (answer.isCorrect) {
        val description =
            stringResource(R.string.round_row_correct, answer.index + 1, answer.exercise.title, subtitle)
        RoundResultRow(answer, subtitle, Modifier.clearAndSetSemantics { contentDescription = description })
    } else {
        val description =
            stringResource(R.string.round_row_missed, answer.index + 1, answer.exercise.title, subtitle)
        // TalkBack has no hint: the action's label is read after the description ("double tap to Review").
        val actionLabel = stringResource(R.string.review_label)
        RoundResultRow(
            answer,
            subtitle,
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

@Composable
private fun subtitle(answer: RoundAnswer): String =
    stringResource(
        R.string.round_topic_skill,
        answer.exercise.topic.name,
        stringResource(answer.exercise.skill.displayNameRes),
    )

/**
 * A text's number, its title over its topic and skill, and how it went. At large font scales the status moves under
 * the title, like the rows on Results.
 */
@Composable
private fun RoundResultRow(
    answer: RoundAnswer,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val isStacked = LocalDensity.current.fontScale >= STACKED_ROW_FONT_SCALE
    val colors = LeoTheme.colors
    val number = rememberNumber(answer.index + 1)
    val numberText: @Composable (Modifier) -> Unit = { modifier ->
        BasicText(
            text = number,
            modifier = modifier.widthIn(min = 16.dp),
            style = LeoTextStyle.answerMarker.toTextStyle().copy(color = colors.ink),
            softWrap = false,
        )
    }
    val title: @Composable (Modifier) -> Unit = { modifier ->
        BasicText(
            text = answer.exercise.title,
            modifier = modifier,
            style =
                LeoTextStyle(size = TITLE_SIZE, lineHeight = TITLE_LINE_HEIGHT)
                    .toTextStyle()
                    .copy(color = colors.ink),
        )
    }
    val subtitleText: @Composable () -> Unit = {
        BasicText(subtitle, style = LeoTextStyle.caption.toTextStyle().copy(color = colors.inkSoft))
    }
    val status: @Composable () -> Unit = {
        if (answer.isCorrect) {
            BasicText(
                text = stringResource(R.string.result_correct),
                style =
                    LeoTextStyle(size = STATUS_SIZE, weight = LeoWeight.Semibold)
                        .toTextStyle()
                        .copy(color = colors.accent700),
                softWrap = false,
            )
        } else {
            ReviewLabel()
        }
    }
    if (isStacked) {
        Column(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // iOS: `HStack(alignment: .firstTextBaseline)`.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                numberText(Modifier.alignByBaseline())
                title(Modifier.alignByBaseline())
            }
            subtitleText()
            status()
        }
    } else {
        Row(
            modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            numberText(Modifier)
            // iOS: `Spacer(minLength: 8)` between two 8 spacings, so at least 24 before the status.
            Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                title(Modifier)
                subtitleText()
            }
            status()
        }
    }
}

@Composable
private fun rememberNumber(number: Int): String {
    val locale = currentLocale()
    return remember(number, locale) {
        java.text.NumberFormat
            .getIntegerInstance(locale)
            .format(number)
    }
}
