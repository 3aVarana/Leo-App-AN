package com.victorarana.leo.features.history

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.victorarana.leo.R
import com.victorarana.leo.domain.RoundSummary
import com.victorarana.leo.features.components.BackBar
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoIcon
import com.victorarana.leo.features.components.RoundResultDots
import com.victorarana.leo.features.preferences.Headline
import com.victorarana.leo.features.quiz.STACKED_ROW_FONT_SCALE
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.leoScreenBackground
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

private const val CALLOUT = 16f
private const val BODY = 17f
private const val ROW_TEXT_SIZE = 17f
private const val TITLE_SIZE = 40f

// What a plain iOS `List` adds above each section header (about 20 dp in the references), on top of the header's own
// padding, including above the first one.
private val SECTION_HEADER_EXTRA_TOP = 20.dp

// The chevron's glyph sits inside its 24 dp box with about 7 dp each side: moved out so the glyph, not the box, ends at
// the gutter like `Image(systemName:)`, with the gap to the dots taken in from the spacer.
private val ChevronInset = 7.dp

/**
 * Every finished round at the current age group (H7): the header totals, then the rounds grouped by when they were
 * read. Swipe a row to delete it (H3), no confirmation. Port of `HistoryView`.
 *
 * Settings isn't reachable from here (no gear), so the age group can't change while History, or a round opened from
 * it, is on screen. If that ever changes, this screen would also need to react to the age group changing underneath
 * it, not just to the list becoming empty.
 *
 * Deleting the last round closes History ([onEmpty]): the button that opened it is hidden now. The same goes for a
 * restored History with nothing to show, so it never draws an empty list.
 *
 * @param onOpen Tapped a round. The caller loads it first and only then navigates.
 * @param onEmpty The list is loaded and has no rounds. Unlike [onBack], not a tap, so the caller must not drop it
 * for a screen still entering.
 */
@Composable
fun HistoryScreen(
    state: RoundHistoryUiState,
    onOpen: (UUID) -> Unit,
    onDelete: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEmpty: () -> Unit = onBack,
) {
    LaunchedEffect(state.isLoaded, state.hasRounds) {
        if (state.isLoaded && !state.hasRounds) onEmpty()
    }
    val locale = currentLocale()
    val is24Hour = rememberIs24HourClock()
    Column(
        modifier
            .fillMaxSize()
            .leoScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        BackBar(parentTitle = stringResource(R.string.navigation_back), onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            item(key = "header") { Header(state) }
            for ((index, section) in state.sections.withIndex()) {
                // Pinned while its rounds scroll under it, like a plain `List`'s section headers (Q3). The ground
                // behind it hides what passes. The key is text: a lazy list saves its keys. It is the section's
                // first round, not its kind: a kind can come back further down (a round dated after this week, from
                // a clock moved back, is "Earlier in <month>" above "This week"), and a repeated key crashes a lazy
                // list. Unlike an index, it stays the same when a section above is deleted.
                stickyHeader(key = "section-${section.rounds.first().id}") {
                    SectionHeader(section.kind, isFirst = index == 0, locale = locale)
                }
                items(section.rounds, key = { it.id }) { summary ->
                    SwipeToDelete(onDelete = { onDelete(summary.id) }) {
                        HistoryRow(
                            summary = summary,
                            zone = state.zone,
                            is24Hour = is24Hour,
                            onOpen = { onOpen(summary.id) },
                            onDelete = { onDelete(summary.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: RoundHistoryUiState) {
    val locale = currentLocale()
    val percent = remember(state.correctShare, locale) { percentText(state.correctShare, locale) }
    Column(
        Modifier.leoReadableWidth().padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Headline(text = stringResource(R.string.history_title), style = LeoTextStyle.display(TITLE_SIZE).toTextStyle())
        // iOS only shows History with rounds, read at once. Here the list can be empty for a moment (a restored route
        // before its load, the last round's delete before the store answers): no "0 rounds read" then.
        if (state.hasRounds) {
            BasicText(
                text = pluralStringResource(R.plurals.history_summary, state.roundCount, state.roundCount, percent),
                style = LeoTextStyle.bodyLarge.toTextStyle().copy(color = LeoTheme.colors.inkSoft),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    kind: HistorySection.Kind,
    isFirst: Boolean,
    locale: Locale,
) {
    val title =
        when (kind) {
            HistorySection.Kind.ThisWeek -> stringResource(R.string.history_this_week)
            HistorySection.Kind.LastWeek -> stringResource(R.string.history_last_week)
            is HistorySection.Kind.EarlierThisMonth ->
                stringResource(R.string.history_earlier_in, monthName(kind.month, showingYear = false, locale))
            is HistorySection.Kind.Month -> monthName(kind.month, kind.showsYear, locale)
        }
    Box(Modifier.fillMaxWidth().background(LeoTheme.colors.background)) {
        Kicker(
            text = title,
            modifier =
                Modifier
                    .leoReadableWidth()
                    .padding(top = (if (isFirst) 0 else 20).dp + SECTION_HEADER_EXTRA_TOP, bottom = 8.dp)
                    .semantics { heading() },
        )
    }
}

/**
 * Swiping from the end deletes (iOS `.swipeActions`, whose full swipe also deletes: Q1). The row's own TalkBack
 * action deletes too, since a swipe is not something TalkBack can do.
 */
@Composable
private fun SwipeToDelete(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        // Only the end-to-start swipe has an anchor, so any dismissal is a delete. The row is put back at rest at
        // once: if the store then fails, the reload brings the same row back (same key, same saved swipe state), and
        // a row restored in its swiped state would delete itself again.
        onDismiss = {
            onDelete()
            scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
        },
        backgroundContent = {
            // The theme's error pair (magenta and the ground): 4.5:1 or more in both themes, which the ground on
            // `magenta600` is not in light mode.
            val colors = LeoTheme.colors
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.magenta)
                    .clearAndSetSemantics {}
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                BasicText(
                    text = stringResource(R.string.history_delete),
                    style =
                        LeoTextStyle(size = CALLOUT, weight = LeoWeight.Semibold)
                            .toTextStyle()
                            .copy(color = colors.background),
                    softWrap = false,
                )
            }
        },
        content = { content() },
    )
}

/**
 * One row: the date and time, the score over the round's dots, a chevron. At large font scales the row stacks
 * instead (iOS: accessibility sizes).
 */
@Composable
private fun HistoryRow(
    summary: RoundSummary,
    zone: ZoneId,
    is24Hour: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = currentLocale()
    val date = remember(summary.finishedAt, zone, locale) { rowDate(summary.finishedAt, zone, locale) }
    val time =
        remember(summary.finishedAt, zone, locale, is24Hour) { rowTime(summary.finishedAt, zone, locale, is24Hour) }
    val spokenDay = remember(summary.finishedAt, zone, locale) { accessibilityDay(summary.finishedAt, zone, locale) }
    val description =
        stringResource(
            R.string.history_row_accessibility,
            spokenDay,
            time,
            summary.correctCount,
            summary.results.size,
        )
    // TalkBack has no hint: the action's label is read after the description ("double tap to Show round").
    val openLabel = stringResource(R.string.history_row_action)
    val deleteLabel = stringResource(R.string.history_delete)
    val isStacked = LocalDensity.current.fontScale >= STACKED_ROW_FONT_SCALE
    Box(
        Modifier
            .fillMaxWidth()
            .background(LeoTheme.colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ).clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = openLabel) {
                    onOpen()
                    true
                }
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
            },
    ) {
        if (isStacked) {
            Column(
                Modifier.leoReadableWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DateAndTime(date, time)
                ScoreAndDots(summary)
            }
        } else {
            Row(
                Modifier.leoReadableWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DateAndTime(date, time)
                // iOS: `Spacer(minLength: 8)` between two 12 spacings.
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(32.dp))
                ScoreAndDots(summary)
                Spacer(Modifier.width(12.dp - ChevronInset))
                LeoIcon(
                    id = R.drawable.ic_chevron_right,
                    tint = LeoTheme.colors.inkSoft,
                    size = 22.dp.scaled(relativeTo = BODY),
                    modifier = Modifier.offset(x = ChevronInset),
                )
            }
        }
    }
}

@Composable
private fun DateAndTime(
    date: String,
    time: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(date, style = rowTextStyle())
        BasicText(time, style = LeoTextStyle.bodySmall.toTextStyle().copy(color = LeoTheme.colors.inkSoft))
    }
}

@Composable
private fun ScoreAndDots(summary: RoundSummary) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            stringResource(R.string.result_score, summary.correctCount, summary.results.size),
            style = rowTextStyle(),
            softWrap = false,
        )
        RoundResultDots(results = summary.results)
    }
}

@Composable
private fun rowTextStyle() =
    LeoTextStyle(size = ROW_TEXT_SIZE, weight = LeoWeight.Semibold).toTextStyle().copy(color = LeoTheme.colors.ink)

/**
 * The device's 12 or 24 hour clock. It isn't part of the configuration, so changing it recomposes nothing: it is read
 * again each time the screen resumes, as History's sections are.
 */
@Composable
internal fun rememberIs24HourClock(): Boolean {
    val context = LocalContext.current
    var is24Hour by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LifecycleResumeEffect(context) {
        is24Hour = DateFormat.is24HourFormat(context)
        onPauseOrDispose {}
    }
    return is24Hour
}
