package com.victorarana.leo.data.repositories

import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.support.RoundHistoryRepositorySpy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Android only: the recorder stands in for `RoundHistoryViewModel.record` until phase 7 (phase-6 D6). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoundHistoryRecorderTests {
    private val repository = RoundHistoryRepositorySpy()

    @Test
    fun savesTheRoundOnce() =
        runTest {
            val recorder =
                RoundHistoryRecorder(
                    repository,
                    CoroutineScope(
                        SupervisorJob() + StandardTestDispatcher(testScheduler),
                    ),
                )
            val round = roundRecordFixture()
            recorder.record(round)
            assertEquals(emptyList<Any>(), repository.saved)
            advanceUntilIdle()
            assertEquals(listOf(round), repository.saved)
        }

    /** The reader never sees an error for history: it is logged, and the next round still saves. */
    @Test
    fun aFailedSaveIsLoggedNotThrown() =
        runTest {
            val recorder =
                RoundHistoryRecorder(
                    repository,
                    CoroutineScope(
                        SupervisorJob() + StandardTestDispatcher(testScheduler),
                    ),
                )
            repository.errorToThrow = IllegalStateException("disk full")
            recorder.record(roundRecordFixture())
            advanceUntilIdle()
            assertEquals(emptyList<Any>(), repository.saved)

            repository.errorToThrow = null
            val next = roundRecordFixture()
            recorder.record(next)
            advanceUntilIdle()
            assertEquals(listOf(next), repository.saved)
        }
}
