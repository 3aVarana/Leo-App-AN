package com.victorarana.leo.features.quiz

import android.icu.text.ListFormatter
import android.icu.text.NumberFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.components.HistoryButton
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.ProgressDots
import com.victorarana.leo.features.components.ProgressDotsSize
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.TopOffset
import com.victorarana.leo.features.components.displayNameRes
import com.victorarana.leo.features.preferences.Headline
import com.victorarana.leo.features.root.launchWordmarkTarget
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.rememberResolvedLocale
import com.victorarana.leo.features.theme.toTextStyle

private const val HEADLINE_SIZE = 48f
private const val GEAR_SIZE = 24f
private const val MAX_NAMED_TOPICS = 3

/**
 * The first screen of a round: its size and topics, and Start. Port of `WelcomeView`.
 *
 * @param exerciseCount How many texts the round has.
 * @param topicNames This round's planned topics, in order. Empty until the round is prepared.
 * @param onHistory `null` hides the button: the current age group has no saved round to show yet.
 */
@Composable
fun WelcomeScreen(
    ageGroup: AgeGroup,
    exerciseCount: Int,
    topicNames: List<String>,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onHistory: (() -> Unit)? = null,
) {
    val colors = LeoTheme.colors
    ScrollingScreen(
        modifier = modifier,
        bottomBar = { LeoButton(stringResource(R.string.quiz_start), LeoButtonKind.Primary, onStart) },
    ) {
        Row(
            Modifier.padding(top = 4.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = stringResource(R.string.app_name),
                modifier = Modifier.semantics { heading() }.launchWordmarkTarget(),
                style = LeoTextStyle.wordmark.toTextStyle().copy(color = colors.ink),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onHistory != null) HistoryButton(ageGroup = ageGroup, onClick = onHistory)
                GearButton(onClick = onSettings, iconSize = GEAR_SIZE)
            }
        }

        TopOffset(height = 120.dp)

        Kicker(
            text = stringResource(R.string.quiz_welcome_kicker, stringResource(ageGroup.displayNameRes)),
            color = colors.accent700,
        )
        Headline(
            text = stringResource(R.string.quiz_welcome_headline, spelledOutCount(exerciseCount)),
            style = LeoTextStyle.display(HEADLINE_SIZE).toTextStyle(),
            modifier = Modifier.padding(top = 12.dp),
        )
        ProgressDots(
            states = List(exerciseCount) { DotState.Upcoming },
            currentIndex = null,
            modifier = Modifier.padding(top = 28.dp),
            size = ProgressDotsSize.Large,
            alignment = Alignment.Start,
        )
        BasicText(
            text = topicSentence(topicNames),
            modifier = Modifier.padding(top = 24.dp),
            style = LeoTextStyle.bodyLarge.toTextStyle().copy(color = colors.inkSoft),
        )
        Spacer(Modifier.weight(1f, min = 24.dp))
    }
}

/**
 * "Six", in the reader's language. The words come from a string array for the round sizes 3 to 9 (ICU's spell-out
 * rules are not part of the Android SDK); any other count falls back to digits.
 */
@Composable
private fun spelledOutCount(count: Int): String {
    val locale = rememberResolvedLocale()
    val words = stringArrayResource(R.array.quiz_spelled_out_counts)
    return remember(locale, count, words) {
        val sizes = RoundSettings.EXERCISE_COUNT_RANGE
        val text =
            if (count in sizes) words[count - sizes.first] else NumberFormat.getInstance(locale).format(count.toLong())
        text.replaceFirstChar { it.titlecase(locale) }
    }
}

/** Names up to three of this round's topics, and how many more there are. */
@Composable
private fun topicSentence(topicNames: List<String>): String {
    val locale = rememberResolvedLocale()
    val shown = topicNames.take(MAX_NAMED_TOPICS)
    val more = topicNames.size - shown.size
    return when {
        topicNames.isEmpty() -> stringResource(R.string.quiz_topics_none)
        more <= 0 ->
            stringResource(
                R.string.quiz_topics_list,
                remember(locale, shown) { ListFormatter.getInstance(locale).format(shown) },
            )
        else ->
            pluralStringResource(
                R.plurals.quiz_topics_more,
                more,
                shown.joinToString(stringResource(R.string.quiz_topic_separator)),
                more,
            )
    }
}
