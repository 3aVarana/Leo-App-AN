package com.victorarana.leo.features.history

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.features.history.HistorySection.Kind
import com.victorarana.leo.support.MainDispatcherRule
import com.victorarana.leo.support.RoundHistoryRepositorySpy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Port of `RoundHistoryViewModelTests`, with the Android-only cases (suspending store, save scope, optimistic delete)
 * after it. The calendar of the Swift tests, UTC with Monday first, is the default here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoundHistoryViewModelTests {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = RoundHistoryRepositorySpy()

    private fun date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Instant = LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC)

    private fun TestScope.makeViewModel(
        now: Instant,
        zone: ZoneId = ZoneOffset.UTC,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ) = RoundHistoryViewModel(
        repository,
        storeScope(),
        now = { now },
        zone = { zone },
        firstDayOfWeek = { firstDayOfWeek },
    )

    // Not `backgroundScope`: `advanceUntilIdle` doesn't run what is left in it.
    private fun TestScope.storeScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private val RoundHistoryViewModel.state get() = uiState.value

    private fun TestScope.setAgeGroup(
        viewModel: RoundHistoryViewModel,
        ageGroup: AgeGroup?,
    ) {
        viewModel.setAgeGroup(ageGroup)
        advanceUntilIdle()
    }

    private fun TestScope.record(
        viewModel: RoundHistoryViewModel,
        round: RoundRecord,
    ) {
        viewModel.record(round)
        advanceUntilIdle()
    }

    private val march20 = date(2026, 3, 20)

    // MARK: - Age group and totals

    @Test
    fun hasRoundsFollowsTheAgeGroup() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            record(viewModel, roundRecordFixture(AgeGroup.NINE))
            assertTrue(viewModel.state.hasRounds)

            setAgeGroup(viewModel, AgeGroup.TWELVE)
            assertFalse(viewModel.state.hasRounds)

            setAgeGroup(viewModel, AgeGroup.NINE)
            assertTrue(viewModel.state.hasRounds)
        }

    @Test
    fun ageGroupChangeReloads() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            record(viewModel, roundRecordFixture(AgeGroup.NINE))
            setAgeGroup(viewModel, AgeGroup.TWELVE)
            record(viewModel, roundRecordFixture(AgeGroup.TWELVE))

            setAgeGroup(viewModel, AgeGroup.NINE)
            assertEquals(1, viewModel.state.roundCount)
            setAgeGroup(viewModel, AgeGroup.TWELVE)
            assertEquals(1, viewModel.state.roundCount)
            setAgeGroup(viewModel, AgeGroup.SIX)
            assertEquals(0, viewModel.state.roundCount)
        }

    @Test
    fun totals() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            assertEquals(0, viewModel.state.roundCount)
            assertEquals(0.0, viewModel.state.correctShare, 0.0)

            record(viewModel, roundRecordFixture(AgeGroup.NINE, results = listOf(true, false, true, true, false, true)))
            record(viewModel, roundRecordFixture(AgeGroup.NINE, results = listOf(true, true, true)))

            assertEquals(2, viewModel.state.roundCount)
            assertEquals(7.0 / 9.0, viewModel.state.correctShare, 0.0)
        }

    @Test
    fun recordForAnotherAgeGroupDoesNotShow() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            record(viewModel, roundRecordFixture(AgeGroup.TWELVE))
            assertFalse(viewModel.state.hasRounds)
            assertEquals(0, viewModel.state.roundCount)
        }

    @Test
    fun deleteReloads() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            val round = roundRecordFixture(AgeGroup.NINE)
            record(viewModel, round)
            assertEquals(1, viewModel.state.roundCount)

            viewModel.delete(round.id)
            advanceUntilIdle()
            assertEquals(0, viewModel.state.roundCount)
            assertNull(viewModel.round(round.id))
        }

    @Test
    fun saveFailureKeepsTheList() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            record(viewModel, roundRecordFixture(AgeGroup.NINE))
            assertEquals(1, viewModel.state.roundCount)

            repository.errorToThrow = IllegalStateException("disk full")
            record(viewModel, roundRecordFixture(AgeGroup.NINE))
            assertEquals(1, viewModel.state.roundCount)
        }

    // MARK: - Sections

    private fun TestScope.kindsOf(
        now: Instant,
        vararg finishedAt: Instant,
        zone: ZoneId = ZoneOffset.UTC,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): List<Kind> {
        val viewModel = makeViewModel(now, zone, firstDayOfWeek)
        viewModel.setAgeGroup(AgeGroup.NINE)
        for (instant in finishedAt) {
            viewModel.record(roundRecordFixture(AgeGroup.NINE, finishedAt = instant))
        }
        advanceUntilIdle()
        return viewModel.state.sections.map { it.kind }
    }

    @Test
    fun sectionsGroupByWeekThenMonth() =
        runTest {
            // Friday 20 March 2026; weeks start on Monday, so this week began on the 16th.
            val viewModel = makeViewModel(march20)
            viewModel.setAgeGroup(AgeGroup.NINE)
            for (finishedAt in listOf(
                date(2026, 3, 16, 1),
                date(2026, 3, 9, 1),
                date(2026, 3, 1, 1),
                date(2026, 2, 10),
                date(2025, 12, 5),
            )) {
                viewModel.record(roundRecordFixture(AgeGroup.NINE, finishedAt = finishedAt))
            }
            advanceUntilIdle()

            val sections = viewModel.state.sections
            assertEquals(
                listOf(
                    Kind.ThisWeek,
                    Kind.LastWeek,
                    Kind.EarlierThisMonth(YearMonth.of(2026, 3)),
                    Kind.Month(YearMonth.of(2026, 2), showsYear = false),
                    Kind.Month(YearMonth.of(2025, 12), showsYear = true),
                ),
                sections.map { it.kind },
            )
            assertEquals(listOf(1, 1, 1, 1, 1), sections.map { it.rounds.size })
            // No round falls in January 2026: its section is left out entirely.
            assertFalse(sections.map { it.kind }.contains(Kind.Month(YearMonth.of(2026, 1), showsYear = false)))
        }

    @Test
    fun roundsOfTheSameKindShareASection() =
        runTest {
            val viewModel = makeViewModel(march20)
            viewModel.setAgeGroup(AgeGroup.NINE)
            for (finishedAt in listOf(march20, date(2026, 3, 17), date(2026, 2, 10), date(2026, 2, 3))) {
                viewModel.record(roundRecordFixture(AgeGroup.NINE, finishedAt = finishedAt))
            }
            advanceUntilIdle()
            val sections = viewModel.state.sections
            assertEquals(
                listOf(Kind.ThisWeek, Kind.Month(YearMonth.of(2026, 2), showsYear = false)),
                sections.map { it.kind },
            )
            assertEquals(listOf(2, 2), sections.map { it.rounds.size })
        }

    /** When last week reaches back into the previous month, a round from those days is still "Last week". */
    @Test
    fun lastWeekWinsOverThePreviousMonth() =
        runTest {
            // Tuesday 3 March 2026: last week began on Monday 23 February.
            val lastWeekStart = date(2026, 2, 23)
            assertEquals(
                listOf(Kind.LastWeek),
                kindsOf(date(2026, 3, 3), lastWeekStart.plusSeconds(3600)),
            )
        }

    /**
     * A round dated after this week (the clock was moved back after it was saved) is "Earlier in <month>", as on iOS,
     * so that kind can appear twice; the list's keys must not rely on kinds being unique.
     */
    @Test
    fun aRoundDatedAfterThisWeekRepeatsEarlierThisMonth() =
        runTest {
            val march = Kind.EarlierThisMonth(YearMonth.of(2026, 3))
            assertEquals(
                listOf(march, Kind.ThisWeek, march),
                kindsOf(march20, date(2026, 3, 25), date(2026, 3, 17), date(2026, 3, 5)),
            )
        }

    /** Android only: the first weekday comes from the device's locale, so the same round sits in different weeks. */
    @Test
    fun sectionsFollowTheFirstDayOfWeek() =
        runTest {
            val wednesday = date(2026, 3, 18)
            val sunday = date(2026, 3, 15, 12)
            assertEquals(listOf(Kind.LastWeek), kindsOf(wednesday, sunday, firstDayOfWeek = DayOfWeek.MONDAY))
            assertEquals(listOf(Kind.ThisWeek), kindsOf(wednesday, sunday, firstDayOfWeek = DayOfWeek.SUNDAY))
        }

    /** The week of a clock change has 167 hours: boundaries are local midnights, not multiples of 24 hours. */
    @Test
    fun weeksFollowTheLocalClockAcrossADaylightSavingChange() =
        runTest {
            val newYork = ZoneId.of("America/New_York")
            // Tuesday 10 March 2026, after the clock change of Sunday the 8th. This week began Monday the 9th at
            // 00:00 EDT (04:00Z).
            val now = date(2026, 3, 10, 15)
            val sundayNight = Instant.parse("2026-03-09T03:30:00Z") // 23:30 EDT on the 8th
            val mondayNight = Instant.parse("2026-03-09T04:30:00Z") // 00:30 EDT on the 9th
            assertEquals(listOf(Kind.ThisWeek, Kind.LastWeek), kindsOf(now, mondayNight, sundayNight, zone = newYork))
        }

    /** Port of `monthNameFollowsTheCalendarTimeZone`: the month is the one in the zone, not in UTC. */
    @Test
    fun theMonthFollowsTheTimeZone() =
        runTest {
            val lima = ZoneOffset.ofHours(-5)
            val firstMidnightInLima = Instant.parse("2026-02-01T05:00:00Z")
            val lastMinuteOfJanuary = Instant.parse("2026-02-01T04:59:00Z")
            assertEquals(
                listOf(Kind.Month(YearMonth.of(2026, 2), false), Kind.Month(YearMonth.of(2026, 1), false)),
                kindsOf(march20, firstMidnightInLima, lastMinuteOfJanuary, zone = lima),
            )
        }

    // MARK: - Android only

    @Test
    fun savesTheRoundOnce() =
        runTest {
            val viewModel = makeViewModel(march20)
            val round = roundRecordFixture()
            viewModel.record(round)
            assertEquals(emptyList<Any>(), repository.saved)
            advanceUntilIdle()
            assertEquals(listOf(round), repository.saved)
        }

    /** The reader never sees an error for history: it is logged, and the next round still saves. */
    @Test
    fun aFailedSaveIsLoggedNotThrown() =
        runTest {
            val viewModel = makeViewModel(march20)
            repository.errorToThrow = IllegalStateException("disk full")
            viewModel.record(roundRecordFixture())
            advanceUntilIdle()
            assertEquals(emptyList<Any>(), repository.saved)
            assertEquals(
                listOf("Couldn't save the finished round"),
                ShadowLog.getLogsForTag("RoundHistoryViewModel").map { it.msg },
            )

            repository.errorToThrow = null
            val next = roundRecordFixture()
            viewModel.record(next)
            advanceUntilIdle()
            assertEquals(listOf(next), repository.saved)
        }

    /** "See results" then leaving the app: the save belongs to the application's scope, not the ViewModel's. */
    @Test
    fun recordCompletesAfterTheViewModelIsCleared() =
        runTest {
            val store = ViewModelStore()
            val viewModel =
                ViewModelProvider.create(
                    store,
                    viewModelFactory { initializer { RoundHistoryViewModel(repository, storeScope()) } },
                )[RoundHistoryViewModel::class]
            viewModel.setAgeGroup(AgeGroup.NINE)
            advanceUntilIdle()
            val round = roundRecordFixture(AgeGroup.NINE)
            viewModel.record(round)
            store.clear()
            advanceUntilIdle()
            assertEquals(listOf(round), repository.saved)
        }

    @Test
    fun isLoadedWaitsForTheFirstLoad() =
        runTest {
            val viewModel = makeViewModel(march20)
            assertFalse(viewModel.state.isLoaded)
            setAgeGroup(viewModel, null)
            assertFalse(viewModel.state.isLoaded)

            val gate = CompletableDeferred<Unit>().also { repository.summariesGate = it }
            viewModel.setAgeGroup(AgeGroup.NINE)
            runCurrent()
            assertFalse(viewModel.state.isLoaded)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.state.isLoaded)
            assertFalse(viewModel.state.hasRounds)
        }

    @Test
    fun settingTheSameAgeGroupDoesNotReload() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            assertEquals(1, repository.summariesCalls)
            setAgeGroup(viewModel, AgeGroup.NINE)
            assertEquals(1, repository.summariesCalls)
            setAgeGroup(viewModel, AgeGroup.TWELVE)
            assertEquals(2, repository.summariesCalls)
        }

    @Test
    fun anAgeGroupChangeShowsNothingUntilItsRoundsLoad() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            record(viewModel, roundRecordFixture(AgeGroup.NINE))
            setAgeGroup(viewModel, AgeGroup.TWELVE)
            record(viewModel, roundRecordFixture(AgeGroup.TWELVE))

            repository.summariesGate = CompletableDeferred()
            viewModel.setAgeGroup(AgeGroup.NINE)
            runCurrent()
            assertFalse(viewModel.state.hasRounds)
            assertFalse(viewModel.state.isLoaded)
        }

    @Test
    fun lateReloadForAPreviousAgeGroupIsDropped() =
        runTest {
            val viewModel = makeViewModel(march20)
            val nine = roundRecordFixture(AgeGroup.NINE)
            val twelve = roundRecordFixture(AgeGroup.TWELVE)
            repository.save(nine)
            repository.save(twelve)

            val gate = CompletableDeferred<Unit>().also { repository.summariesGate = it }
            viewModel.setAgeGroup(AgeGroup.NINE)
            runCurrent()
            viewModel.setAgeGroup(AgeGroup.TWELVE)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(AgeGroup.TWELVE, viewModel.state.ageGroup)
            assertEquals(listOf(twelve.id), viewModel.state.summaries.map { it.id })
        }

    @Test
    fun loadFailureShowsNoRounds() =
        runTest {
            val viewModel = makeViewModel(march20)
            repository.save(roundRecordFixture(AgeGroup.NINE))
            repository.errorToThrow = IllegalStateException("unreadable")
            setAgeGroup(viewModel, AgeGroup.NINE)
            assertTrue(viewModel.state.isLoaded)
            assertFalse(viewModel.state.hasRounds)
            assertTrue(
                ShadowLog.getLogsForTag("RoundHistoryViewModel").any {
                    it.msg ==
                        "Couldn't load the round history"
                },
            )
        }

    @Test
    fun deleteFailureRestoresTheRound() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            record(viewModel, roundRecordFixture(AgeGroup.NINE))

            repository.deleteError = IllegalStateException("locked")
            viewModel.delete(
                viewModel.state.summaries
                    .single()
                    .id,
            )
            advanceUntilIdle()
            assertEquals(1, viewModel.state.roundCount)
            assertEquals(emptyList<Any>(), repository.deleted)
        }

    /**
     * As on iOS, a failed delete doesn't read the list again: its row goes back from memory. A store that also fails
     * to read can't empty the list then, which would close History and hide its button.
     */
    @Test
    fun aFailedDeleteKeepsTheListWhenTheStoreCantBeReadEither() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            val older = roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 17))
            val newer = roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 19))
            record(viewModel, older)
            record(viewModel, newer)
            val loads = repository.summariesCalls

            repository.errorToThrow = IllegalStateException("disk I/O error")
            viewModel.delete(older.id)
            advanceUntilIdle()
            assertEquals(listOf(newer.id, older.id), viewModel.state.summaries.map { it.id })
            assertTrue(viewModel.state.isLoaded)
            assertEquals(loads, repository.summariesCalls)
        }

    /** A swiped row must not bounce back while the store answers. */
    @Test
    fun deleteRemovesTheRowBeforeTheStoreAnswers() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            val round = roundRecordFixture(AgeGroup.NINE)
            record(viewModel, round)

            val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }
            viewModel.delete(round.id)
            runCurrent()
            assertEquals(0, viewModel.state.roundCount)
            assertEquals(emptyList<Any>(), repository.deleted)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(0, viewModel.state.roundCount)
            assertEquals(listOf(round.id), repository.deleted)
        }

    /** What closes History: loaded, and nothing left. */
    @Test
    fun deletingTheLastRoundLeavesALoadedEmptyList() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            val round = roundRecordFixture(AgeGroup.NINE)
            record(viewModel, round)
            viewModel.delete(round.id)
            advanceUntilIdle()
            assertTrue(viewModel.state.isLoaded)
            assertFalse(viewModel.state.hasRounds)
            assertEquals(emptyList<Any>(), viewModel.state.sections)
        }

    @Test
    fun roundReturnsNullOnError() =
        runTest {
            val viewModel = makeViewModel(march20)
            val round = roundRecordFixture()
            repository.save(round)
            assertEquals(round, viewModel.round(round.id))
            repository.errorToThrow = IllegalStateException("unreadable")
            assertNull(viewModel.round(round.id))
            assertTrue(
                ShadowLog.getLogsForTag("RoundHistoryViewModel").any { it.msg.startsWith("Couldn't read round") },
            )
        }

    @Test
    fun openKeepsTheRoundForThePageAndLeavesItAloneWhenItCantLoad() =
        runTest {
            val viewModel = makeViewModel(march20)
            val round = roundRecordFixture()
            repository.save(round)
            assertEquals(round, viewModel.open(round.id))
            assertEquals(round, viewModel.state.openedRound)

            repository.delete(round.id)
            assertNull(viewModel.open(round.id))
            assertEquals(round, viewModel.state.openedRound)
        }

    @Test
    fun theStateCarriesTheZoneTheSectionsWereBuiltIn() =
        runTest {
            val lima = ZoneOffset.ofHours(-5)
            assertEquals(lima, makeViewModel(march20, zone = lima).state.zone)
        }

    /** What closes History is "loaded and empty": a delete that hasn't been confirmed isn't that yet. */
    @Test
    fun deletingTheLastRoundIsNotLoadedUntilTheStoreAnswers() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            val round = roundRecordFixture(AgeGroup.NINE)
            record(viewModel, round)

            val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }
            repository.deleteError = IllegalStateException("locked")
            viewModel.delete(round.id)
            runCurrent()
            assertEquals(0, viewModel.state.roundCount)
            assertFalse(viewModel.state.isLoaded)

            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.state.isLoaded)
            assertEquals(1, viewModel.state.roundCount)
        }

    /**
     * Android only: swiping a second row before the first delete answers. The first one's reload mustn't bring the
     * second back.
     */
    @Test
    fun overlappingDeletesDontBringTheSecondRowBack() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            val first = roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 19))
            val second = roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 18))
            val kept = roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 17))
            listOf(first, second, kept).forEach { record(viewModel, it) }
            val firstGate = CompletableDeferred<Unit>().also { repository.deleteGates[first.id] = it }
            val secondGate = CompletableDeferred<Unit>().also { repository.deleteGates[second.id] = it }

            viewModel.delete(first.id)
            viewModel.delete(second.id)
            runCurrent()
            firstGate.complete(Unit)
            advanceUntilIdle()
            // The store still holds the second round: its delete hasn't answered.
            assertEquals(listOf(kept.id), viewModel.state.summaries.map { it.id })
            assertFalse(viewModel.state.isLoaded)

            secondGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(kept.id), viewModel.state.summaries.map { it.id })
            assertTrue(viewModel.state.isLoaded)
        }

    /** Android only: the reload after a save, landing while a delete waits, mustn't bring the deleted row back. */
    @Test
    fun aSaveDuringADeleteDoesntBringTheRowBack() =
        runTest {
            val viewModel = makeViewModel(march20)
            setAgeGroup(viewModel, AgeGroup.NINE)
            val deleted = roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 18))
            record(viewModel, deleted)
            val gate = CompletableDeferred<Unit>().also { repository.deleteGates[deleted.id] = it }

            viewModel.delete(deleted.id)
            runCurrent()
            val saved = roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 19))
            record(viewModel, saved)
            assertEquals(listOf(saved.id), viewModel.state.summaries.map { it.id })

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(saved.id), viewModel.state.summaries.map { it.id })
            assertTrue(viewModel.state.isLoaded)
        }

    /** Swiping and leaving the app: the delete belongs to the application's scope, not the ViewModel's. */
    @Test
    fun deleteCompletesAfterTheViewModelIsCleared() =
        runTest {
            val store = ViewModelStore()
            val viewModel =
                ViewModelProvider.create(
                    store,
                    viewModelFactory { initializer { RoundHistoryViewModel(repository, storeScope()) } },
                )[RoundHistoryViewModel::class]
            viewModel.setAgeGroup(AgeGroup.NINE)
            val round = roundRecordFixture(AgeGroup.NINE)
            advanceUntilIdle()
            viewModel.record(round)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }
            viewModel.delete(round.id)
            runCurrent()
            store.clear()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(round.id), repository.deleted)
        }

    /** A round finished on Sunday evening reads "Last week" when History is opened on Monday, with no load between. */
    @Test
    fun refreshingTheCalendarRebuildsTheSectionsFromTheClockNow() =
        runTest {
            var clock = date(2026, 3, 15, 23)
            val viewModel =
                RoundHistoryViewModel(
                    repository,
                    storeScope(),
                    now = { clock },
                    zone = { ZoneOffset.UTC },
                    firstDayOfWeek = { DayOfWeek.MONDAY },
                )
            viewModel.setAgeGroup(AgeGroup.NINE)
            viewModel.record(roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 15, 22)))
            advanceUntilIdle()
            assertEquals(listOf(Kind.ThisWeek), viewModel.state.sections.map { it.kind })
            val calls = repository.summariesCalls

            clock = date(2026, 3, 16, 1)
            assertEquals(listOf(Kind.ThisWeek), viewModel.state.sections.map { it.kind })
            viewModel.refreshCalendar()
            assertEquals(listOf(Kind.LastWeek), viewModel.state.sections.map { it.kind })
            assertEquals(calls, repository.summariesCalls)
        }

    /** The zone and the first weekday are read again too (a trip, or the "First day of week" setting). */
    @Test
    fun refreshingTheCalendarReadsTheZoneAndTheFirstWeekdayAgain() =
        runTest {
            var zone: ZoneId = ZoneOffset.UTC
            var firstDay = DayOfWeek.MONDAY
            val viewModel =
                RoundHistoryViewModel(
                    repository,
                    storeScope(),
                    now = { date(2026, 3, 18) },
                    zone = { zone },
                    firstDayOfWeek = { firstDay },
                )
            viewModel.setAgeGroup(AgeGroup.NINE)
            viewModel.record(roundRecordFixture(AgeGroup.NINE, finishedAt = date(2026, 3, 15, 12)))
            advanceUntilIdle()
            assertEquals(listOf(Kind.LastWeek), viewModel.state.sections.map { it.kind })

            zone = ZoneOffset.ofHours(-5)
            firstDay = DayOfWeek.SUNDAY
            viewModel.refreshCalendar()
            assertEquals(ZoneOffset.ofHours(-5), viewModel.state.zone)
            assertEquals(listOf(Kind.ThisWeek), viewModel.state.sections.map { it.kind })
        }
}
