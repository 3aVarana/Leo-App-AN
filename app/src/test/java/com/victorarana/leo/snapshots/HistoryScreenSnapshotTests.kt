package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundSummary
import com.victorarana.leo.features.history.HistoryScreen
import com.victorarana.leo.features.history.RoundHistoryUiState
import com.victorarana.leo.features.history.RoundHistoryViewModel
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Port of `HistoryViewSnapshotTests`, with the same dates: today is Friday 20 March 2026, in UTC with Monday first.
 * Row times are in UTC (D8), so they differ from the iOS images, which show the simulator's zone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class HistoryScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
        minute: Int = 12,
    ): Instant = LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC)

    private val now = date(2026, 3, 20)

    private fun state(rounds: List<Pair<Instant, List<Boolean>>>): RoundHistoryUiState {
        val summaries =
            rounds
                .map { (finishedAt, results) -> RoundSummary(UUID.randomUUID(), finishedAt, results) }
                .sortedByDescending { it.finishedAt }
        return RoundHistoryUiState(
            ageGroup = AgeGroup.NINE,
            summaries = summaries,
            isLoaded = true,
            sections = RoundHistoryViewModel.sections(summaries, now, ZoneOffset.UTC, DayOfWeek.MONDAY),
            zone = ZoneOffset.UTC,
        )
    }

    private fun snapshot(
        name: String,
        state: RoundHistoryUiState,
        heightDp: Int = 844,
        fontScale: Float = 1f,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        HistoryScreen(state = state, onOpen = {}, onDelete = {}, onBack = {})
    }

    /** This week, last week and an older month. */
    @Test
    fun threeSections() =
        snapshot(
            "three-sections",
            state(
                listOf(
                    now to listOf(true, true, true, true, true, true),
                    date(2026, 3, 16, 1, 0) to listOf(false, true, true, false, true, true),
                    date(2026, 3, 9, 1, 0) to listOf(true, true, false, true, true, true),
                    date(2026, 2, 10) to listOf(false, false, false, true, true, true),
                ),
            ),
            heightDp = 1700,
        )

    /** One round: the summary line uses the singular, and there's one section. */
    @Test
    fun oneRound() = snapshot("one-round", state(listOf(now to listOf(true, true, true, true, true, false))))

    @Test
    fun accessibilitySize() =
        snapshot(
            "one-round-fontScale2",
            state(listOf(now to listOf(false, true, true, true, true, true))),
            heightDp = 1100,
            fontScale = 2f,
        )

    /** Android only: the longest row, nine dots, at 200% (a gap the iOS plan notes). */
    @Test
    fun nineTextsAtLargeFontScale() =
        snapshot(
            "nine-texts-fontScale2",
            state(listOf(now to List(9) { it % 3 != 0 })),
            heightDp = 1100,
            fontScale = 2f,
        )

    /** Android only: a month of another year names its year. */
    @Test
    fun aMonthOfAnotherYear() =
        snapshot(
            "last-year",
            state(
                listOf(
                    date(2026, 3, 17, 1, 0) to listOf(true, true, true, true, true, true),
                    date(2025, 12, 5) to listOf(true, false, true, true, true, true),
                ),
            ),
        )
}
