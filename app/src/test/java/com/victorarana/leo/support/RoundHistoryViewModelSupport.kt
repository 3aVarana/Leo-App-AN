package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.RoundHistoryRepository
import com.victorarana.leo.features.history.RoundHistoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.DayOfWeek
import java.time.Instant

/**
 * A history over [repository] (an in-memory spy by default), for the tests of the screens and the root around it.
 * Saves run on the main dispatcher, like the application's scope in the app. Weeks start on Monday, as in the Swift
 * tests, so tests that don't run under Robolectric never ask Android for the locale's first day.
 */
fun testHistory(
    repository: RoundHistoryRepository = RoundHistoryRepositorySpy(),
    now: () -> Instant = Instant::now,
): RoundHistoryViewModel =
    RoundHistoryViewModel(
        repository,
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        now = now,
        firstDayOfWeek = { DayOfWeek.MONDAY },
    )
