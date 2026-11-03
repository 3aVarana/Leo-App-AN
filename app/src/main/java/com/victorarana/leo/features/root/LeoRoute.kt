package com.victorarana.leo.features.root

import kotlinx.serialization.Serializable

/**
 * The screens inside the navigation graph (iOS `NavigationStack` with `navigationDestination`). Availability and
 * onboarding are not routes: like iOS they are gates in `RootScreen`, outside the graph (D1).
 * `Review` and `HistoryRound(id)` join in later phases.
 */
@Serializable
sealed interface LeoRoute {
    /** The quiz: Welcome, Loading, Exercise, Result and Error, one at a time (phase 6). */
    @Serializable
    data object Home : LeoRoute

    /** The saved rounds (phase 7). */
    @Serializable
    data object History : LeoRoute

    /** The preferences editor, full screen over Home (iOS: a sheet). Back is Cancel. */
    @Serializable
    data object Settings : LeoRoute
}
