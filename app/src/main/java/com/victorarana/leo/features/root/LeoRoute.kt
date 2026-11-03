package com.victorarana.leo.features.root

import kotlinx.serialization.Serializable

/**
 * The screens inside the navigation graph (iOS `NavigationStack` with `navigationDestination`). Availability and
 * onboarding are not routes: like iOS they are gates in `RootScreen`, outside the graph (D1).
 */
@Serializable
sealed interface LeoRoute {
    /** The quiz: Welcome, Loading, Exercise, Result and Error, one at a time (phase 6). */
    @Serializable
    data object Home : LeoRoute

    /** The saved rounds at the current age group, pushed from Welcome or Results. */
    @Serializable
    data object History : LeoRoute

    /**
     * One saved round, pushed from History. [id] is the round's UUID as text: a route carries only simple arguments.
     * The round itself is loaded before this is pushed and kept in `RoundHistoryViewModel`; a restored route loads it
     * again by [id].
     */
    @Serializable
    data class HistoryRound(
        val id: String,
    ) : LeoRoute

    /** The misses of the saved round [roundId], one page each, opened at [startIndex] (from the round page). */
    @Serializable
    data class HistoryReview(
        val roundId: String,
        val startIndex: Int,
    ) : LeoRoute

    /**
     * The texts the reader missed in the round just finished, one page each, pushed from Results (iOS: a
     * `navigationDestination` of `ResultView`). [startIndex] is the position in the round's misses to open at.
     * The misses themselves come from `QuizViewModel`, not the route: a route carries only simple arguments.
     */
    @Serializable
    data class Review(
        val startIndex: Int,
    ) : LeoRoute

    /** The preferences editor, full screen over Home (iOS: a sheet). Back is Cancel. */
    @Serializable
    data object Settings : LeoRoute
}
