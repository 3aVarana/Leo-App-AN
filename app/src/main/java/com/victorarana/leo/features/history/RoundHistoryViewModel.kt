package com.victorarana.leo.features.history

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorarana.leo.data.repositories.RoundHistoryRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

private const val TAG = "RoundHistoryViewModel"

/** One group of rounds in the History list, newest group first. */
data class HistorySection(
    val kind: Kind,
    val rounds: List<RoundSummary>,
) {
    sealed interface Kind {
        data object ThisWeek : Kind

        data object LastWeek : Kind

        /** Earlier in the current month, not in [ThisWeek] or [LastWeek]. */
        data class EarlierThisMonth(
            val month: YearMonth,
        ) : Kind

        /** A past month; [showsYear] when it isn't in the current year. */
        data class Month(
            val month: YearMonth,
            val showsYear: Boolean,
        ) : Kind
    }
}

/**
 * Everything History and the round page draw from.
 *
 * @property ageGroup The age group History is showing. Null before onboarding.
 * @property isLoaded Whether [summaries] is what the store holds for [ageGroup]. False until the first load ends, so
 * a screen can tell "nothing yet" from "nothing saved" (iOS loads synchronously and has no such moment).
 * @property sections [summaries] grouped for the list, built in [zone].
 * @property zone The time zone the sections and the rows' clock times are written in.
 * @property openedRound The round the reader tapped, loaded before the round page opens (see
 * [RoundHistoryViewModel.open]).
 *
 * `@Immutable` because Compose can't tell that `ZoneId` and the domain lists never change, so History couldn't skip
 * recomposing for an equal state otherwise.
 */
@Immutable
data class RoundHistoryUiState(
    val ageGroup: AgeGroup? = null,
    val summaries: List<RoundSummary> = emptyList(),
    val isLoaded: Boolean = false,
    val sections: List<HistorySection> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val openedRound: RoundRecord? = null,
) {
    /** Drives the entry button (H6): hidden before there's a saved round to show. */
    val hasRounds: Boolean get() = summaries.isNotEmpty()

    val roundCount: Int get() = summaries.size

    /** The share of every answer, across every round, that was right. 0 with no rounds. */
    val correctShare: Double
        get() {
            val total = summaries.sumOf { it.results.size }
            if (total == 0) return 0.0
            return summaries.sumOf { it.correctCount }.toDouble() / total
        }
}

/**
 * The reader's finished rounds at one age group (H7): the History list and its totals. Port of
 * `RoundHistoryViewModel`, a ViewModel in the Activity's store like the quiz.
 *
 * The repository is suspending (disk I/O is off the main thread on Android), so loads are jobs: a new load replaces
 * the one running, and a late answer for another age group is dropped.
 *
 * @param storeScope Runs the writes to the store (the save of a finished round, a delete), so they complete even if
 * the Activity finishes right after "See results" or a swipe (the application's scope in the app; phase-6 D6).
 * @param now The providers stand for `.autoupdatingCurrent`: read each time, so a change of zone or locale applies.
 * @param firstDayOfWeek The device region's first weekday, from `localeFirstDayOfWeek(deviceLocale(context))`: the
 * caller owns the `Context`, the ViewModel only asks.
 */
class RoundHistoryViewModel(
    private val repository: RoundHistoryRepository,
    private val storeScope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val firstDayOfWeek: () -> DayOfWeek,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RoundHistoryUiState(zone = zone()))
    val uiState: StateFlow<RoundHistoryUiState> = mutableState.asStateFlow()

    private var loading: Job? = null

    /**
     * Rounds whose delete hasn't answered yet: no load shows them, even one that read the store before they left. Only
     * touched on the main thread: loads run on `viewModelScope` and writes on the application's scope, both Main.
     */
    private val pendingDeletes = mutableSetOf<UUID>()

    /** Sets the age group History shows. A different one reloads; the same one does nothing. Null shows nothing. */
    fun setAgeGroup(ageGroup: AgeGroup?) {
        if (ageGroup == mutableState.value.ageGroup) return
        publish { copy(ageGroup = ageGroup, summaries = emptyList(), isLoaded = false) }
        reload()
    }

    /**
     * Saves [round] and, when it was read at the current age group, reloads. A failed save is logged and leaves the
     * list as it is. The save runs on [storeScope], not the ViewModel's, so clearing the ViewModel doesn't lose it.
     */
    fun record(round: RoundRecord) {
        storeScope.launch {
            if (attempt("Couldn't save the finished round") { repository.save(round) }.isFailure) return@launch
            if (round.ageGroup == mutableState.value.ageGroup) reload()
        }
    }

    /** For the round page. Null when it was deleted or can't be read. */
    suspend fun round(id: UUID): RoundRecord? = attempt("Couldn't read round $id") { repository.round(id) }.getOrNull()

    /**
     * Loads round [id] and keeps it as [RoundHistoryUiState.openedRound], for the round page and its Review to
     * read. Null, and nothing opened, when it can't be loaded: History loads before it navigates, so there is no
     * blank first frame and no round page for a round that went missing meanwhile.
     */
    suspend fun open(id: UUID): RoundRecord? = round(id)?.also { opened -> publish { copy(openedRound = opened) } }

    /**
     * Deletes round [id], on [storeScope] like a save. The row leaves the list at once (a swiped row must not bounce
     * back while the store answers, nor through another load that lands meanwhile, see [pendingDeletes]). Then, as on
     * iOS, a delete that worked reads the list again, and one that failed is logged and doesn't: its row goes back
     * where it was, so a failed delete can't empty the list. A read that fails after a delete that worked shows no
     * rounds and closes History, like any failed load, as on iOS (`reload()` sets `summaries = []`).
     */
    fun delete(id: UUID) {
        // Already on its way (a swipe and the TalkBack action at once): a second call would end first and let a load
        // show the row while the first is still running.
        if (id in pendingDeletes) return
        val ageGroup = mutableState.value.ageGroup
        val removed = mutableState.value.summaries.firstOrNull { it.id == id }
        pendingDeletes += id
        // Not loaded again yet: the list is only a guess until the store answers, so History doesn't close on an
        // empty list that the failed delete is about to put the round back into.
        publish { copy(summaries = summaries.filterNot { it.id == id }, isLoaded = false) }
        storeScope.launch {
            val failed =
                try {
                    attempt("Couldn't delete round $id") { repository.delete(id) }.isFailure
                } finally {
                    pendingDeletes -= id
                }
            if (!failed) {
                reload()
            } else if (mutableState.value.ageGroup == ageGroup) {
                // Confirmed again unless another delete or a load is still on its way; those settle it.
                val settled = pendingDeletes.isEmpty() && loading?.isActive != true
                publish {
                    // The store's order (`RoundHistoryDao.summaries`: newest first by `finishedAt`, nothing else).
                    val restored = summaries.filterNot { it.id == id } + listOfNotNull(removed)
                    copy(summaries = restored.sortedByDescending { it.finishedAt }, isLoaded = isLoaded || settled)
                }
            }
        }
    }

    /**
     * Builds the sections, the zone and the first weekday again from the clock as it is now. Call it whenever History
     * is shown (iOS computes them each time the view's body runs): the ViewModel outlives screens and days, so a
     * round finished on Sunday must read "Last week" when History is opened on Monday.
     */
    fun refreshCalendar() = publish { this }

    private fun reload() {
        loading?.cancel()
        val ageGroup = mutableState.value.ageGroup
        if (ageGroup == null) {
            publish { copy(summaries = emptyList(), isLoaded = false) }
            return
        }
        loading =
            viewModelScope.launch {
                val summaries =
                    attempt("Couldn't load the round history") { repository.summaries(ageGroup) }
                        .getOrDefault(emptyList())
                // A cancelled load whose store call still returned, or an age group changed meanwhile.
                currentCoroutineContext().ensureActive()
                if (mutableState.value.ageGroup == ageGroup) {
                    // A delete still waiting for the store keeps its row out, and the list unconfirmed: the reload
                    // after that delete settles both.
                    val pending = pendingDeletes.toSet()
                    publish { copy(summaries = summaries.filterNot { it.id in pending }, isLoaded = pending.isEmpty()) }
                }
            }
    }

    private fun publish(change: RoundHistoryUiState.() -> RoundHistoryUiState) {
        // Read once, outside `update`, whose lambda can run again under contention: `firstDayOfWeek` is a binder
        // call on API 33+ (`deviceLocale`).
        val zone = zone()
        val now = now()
        val firstDay = firstDayOfWeek()
        mutableState.update {
            val next = it.change()
            next.copy(zone = zone, sections = sections(next.summaries, now, zone, firstDay))
        }
    }

    // Every failure of the store is logged, never shown: the reader never sees an error for history.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(
        message: String,
        block: suspend () -> T,
    ): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, message, error)
            Result.failure(error)
        }

    companion object {
        /**
         * [summaries] (newest first) grouped by week, then month, as in `docs/Leo-Round-History-Plan.md` 1.2. A
         * section with no rounds is left out, and "Last week" wins over "Earlier in <month>" when they overlap.
         * Weeks start on [firstDayOfWeek]; every boundary is local midnight in [zone], found with `java.time` so a
         * week with a daylight saving change is still a week (iOS `dateInterval(of: .weekOfYear)`).
         */
        internal fun sections(
            summaries: List<RoundSummary>,
            now: Instant,
            zone: ZoneId,
            firstDayOfWeek: DayOfWeek,
        ): List<HistorySection> {
            val today = now.atZone(zone).toLocalDate()
            val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).atStartOfDay(zone)
            val thisWeek = thisWeekStart.toInstant()..<thisWeekStart.plusWeeks(1).toInstant()
            val lastWeek = thisWeekStart.minusWeeks(1).toInstant()..<thisWeek.start
            val thisMonth = YearMonth.from(today)

            fun kind(date: Instant): HistorySection.Kind {
                val month = YearMonth.from(date.atZone(zone))
                return when {
                    date in thisWeek -> HistorySection.Kind.ThisWeek
                    date in lastWeek -> HistorySection.Kind.LastWeek
                    month == thisMonth -> HistorySection.Kind.EarlierThisMonth(month)
                    else -> HistorySection.Kind.Month(month, showsYear = month.year != thisMonth.year)
                }
            }
            val sections = mutableListOf<HistorySection>()
            for (summary in summaries) {
                val kind = kind(summary.finishedAt)
                val last = sections.lastOrNull()
                if (last?.kind == kind) {
                    sections[sections.lastIndex] = last.copy(rounds = last.rounds + summary)
                } else {
                    sections += HistorySection(kind, listOf(summary))
                }
            }
            return sections
        }
    }
}
