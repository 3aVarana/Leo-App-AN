package com.victorarana.leo.features.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.features.components.AnswerRow
import com.victorarana.leo.features.components.BackBar
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoBottomBar
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.OptionState
import com.victorarana.leo.features.components.displayNameRes
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.leoScreenBackground
import com.victorarana.leo.features.theme.rememberReduceMotion
import com.victorarana.leo.features.theme.toTextStyle
import kotlinx.coroutines.launch

private const val CALLOUT = 16f

/**
 * The texts the reader missed, one page each: their answer, the right one, why, and the text. Port of `ReviewView`.
 * The round page of History reuses it.
 *
 * @param startIndex The position in [misses] to open at.
 * @param backTitle The bottom button's label once every miss has been seen. "Back to round" from the round page.
 * @param parentTitle The label beside the back arrow: the title of the screen below (iOS: the system back button
 * shows it). Results by default.
 */
@Composable
fun ReviewScreen(
    misses: List<RoundAnswer>,
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTitle: String = stringResource(R.string.review_back_to_results),
    parentTitle: String = stringResource(R.string.result_title),
) {
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, maxOf(misses.size - 1, 0))) { misses.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val selection = pagerState.currentPage
    Column(
        modifier
            .fillMaxSize()
            .leoScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        BackBar(
            parentTitle = parentTitle,
            onBack = onBack,
            trailing = pluralStringResource(R.plurals.review_count, misses.size, selection + 1, misses.size),
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            ReviewPage(misses[page])
        }
        LeoBottomBar {
            if (selection < misses.size - 1) {
                LeoButton(
                    text = stringResource(R.string.review_next_missed),
                    kind = LeoButtonKind.Primary,
                    onClick = {
                        scope.launch {
                            if (reduceMotion) {
                                pagerState.scrollToPage(selection + 1)
                            } else {
                                pagerState.animateScrollToPage(selection + 1)
                            }
                        }
                    },
                )
            } else {
                LeoButton(text = backTitle, kind = LeoButtonKind.Primary, onClick = onBack)
            }
        }
    }
}

@Composable
private fun ReviewPage(answer: RoundAnswer) {
    val colors = LeoTheme.colors
    val exercise = answer.exercise
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .leoReadableWidth()
            .padding(bottom = 24.dp),
    ) {
        Kicker(
            text =
                stringResource(
                    R.string.review_kicker,
                    answer.index + 1,
                    stringResource(exercise.skill.displayNameRes),
                ),
            modifier = Modifier.padding(top = 20.dp),
            color = colors.magenta700,
        )
        BasicText(
            text = exercise.title,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp).semantics { heading() },
            style = LeoTextStyle.title.toTextStyle().copy(color = colors.ink),
        )
        BasicText(
            text = exercise.question,
            modifier = Modifier.padding(bottom = 12.dp),
            style = LeoTextStyle.reviewQuestion.toTextStyle().copy(color = colors.ink),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnswerRow(
                marker = stringResource(R.string.review_you),
                text = exercise.options[answer.selectedOption],
                state = OptionState.Incorrect,
                readsMarker = true,
            )
            AnswerRow(
                marker = stringResource(R.string.review_answer),
                text = exercise.options[exercise.correctIndex],
                state = OptionState.Correct,
                readsMarker = true,
            )
        }
        if (exercise.explanation.isNotEmpty()) {
            BasicText(
                text = exercise.explanation,
                modifier = Modifier.padding(top = 14.dp),
                style = LeoTextStyle.reviewExplanation.toTextStyle().copy(color = colors.ink),
            )
        }
        Kicker(
            text = stringResource(R.string.review_from_the_text),
            modifier = Modifier.padding(top = 24.dp).semantics { heading() },
        )
        SelectionContainer(Modifier.padding(top = 8.dp)) {
            BasicText(
                text = exercise.passage,
                style = LeoTextStyle(size = CALLOUT, lineHeight = 25f).toTextStyle().copy(color = colors.ink),
            )
        }
    }
}
