package com.victorarana.leo.features.root

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.victorarana.leo.R
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.features.history.HistoryScreen
import com.victorarana.leo.features.history.RoundHistoryViewModel
import com.victorarana.leo.features.history.RoundScreen
import com.victorarana.leo.features.quiz.ReviewScreen
import com.victorarana.leo.features.theme.leoScreenBackground
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * History, the round page and its Review: the three screens pushed one over the other (iOS: `HistoryView`'s
 * `navigationDestination(item:)` and `RoundView`'s). Each slides in from the side, none under reduced motion, and the
 * one below stays where it is, composed (`KeepUntilTransitionsFinished`) until the slide over it ends.
 */
internal fun NavGraphBuilder.historyDestinations(
    navController: NavHostController,
    history: RoundHistoryViewModel,
    reduceMotion: Boolean,
) {
    val enter: () -> EnterTransition = { if (reduceMotion) EnterTransition.None else slideInHorizontally { it } }
    val popExit: () -> ExitTransition = { if (reduceMotion) ExitTransition.None else slideOutHorizontally { it } }
    composable<LeoRoute.History>(
        enterTransition = { enter() },
        popExitTransition = { popExit() },
        exitTransition = {
            if (targetState.destination.hasRoute<LeoRoute.HistoryRound>()) {
                ExitTransition.KeepUntilTransitionsFinished
            } else {
                null
            }
        },
        popEnterTransition = {
            if (initialState.destination.hasRoute<LeoRoute.HistoryRound>()) EnterTransition.None else null
        },
    ) { entry -> HistoryDestination(navController, entry, history) }
    composable<LeoRoute.HistoryRound>(
        enterTransition = { enter() },
        popExitTransition = { popExit() },
        exitTransition = {
            if (targetState.destination.hasRoute<LeoRoute.HistoryReview>()) {
                ExitTransition.KeepUntilTransitionsFinished
            } else {
                null
            }
        },
        popEnterTransition = {
            if (initialState.destination.hasRoute<LeoRoute.HistoryReview>()) EnterTransition.None else null
        },
    ) { entry -> RoundDestination(navController, entry, history) }
    composable<LeoRoute.HistoryReview>(
        enterTransition = { enter() },
        popExitTransition = { popExit() },
    ) { entry -> HistoryReviewDestination(navController, entry, history) }
}

@Composable
private fun HistoryDestination(
    navController: NavHostController,
    entry: NavBackStackEntry,
    history: RoundHistoryViewModel,
) {
    val state by history.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // A tap loads the round first and pushes only if it can be read (a round that went missing isn't opened); taps
    // while a load runs are ignored, so one tap can't push the page twice.
    var isOpening by remember { mutableStateOf(false) }
    // The week and the clock change under a ViewModel that outlives days: read them again every time History shows.
    LifecycleResumeEffect(history) {
        history.refreshCalendar()
        onPauseOrDispose {}
    }
    HistoryScreen(
        state = state,
        onOpen = { id ->
            // Also not while the page is entering (History stays tappable under it): that would replace the round the
            // entering page is showing.
            if (!isOpening && entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                isOpening = true
                scope.launch {
                    try {
                        val round = history.open(id)
                        if (round != null && entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.navigate(LeoRoute.HistoryRound(id.toString()))
                        }
                    } finally {
                        isOpening = false
                    }
                }
            }
        },
        onDelete = history::delete,
        onBack = dropUnlessResumed { navController.popBackStack() },
        // Nothing to show: leave even while still entering (a restored route), but only if this is the screen on top.
        onEmpty = { navController.popIfOnTop(entry) },
    )
}

@Composable
private fun RoundDestination(
    navController: NavHostController,
    entry: NavBackStackEntry,
    history: RoundHistoryViewModel,
) {
    val id = entry.toRoute<LeoRoute.HistoryRound>().id
    val round =
        rememberRound(history, id) { navController.popIfOnTop(entry) }
    val state by history.uiState.collectAsStateWithLifecycle()
    if (round == null) {
        Box(Modifier.fillMaxSize().leoScreenBackground())
    } else {
        RoundScreen(
            round = round,
            onReview = { index ->
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    navController.navigate(LeoRoute.HistoryReview(id, index))
                }
            },
            onBack = dropUnlessResumed { navController.popBackStack() },
            zone = state.zone,
        )
    }
}

@Composable
private fun HistoryReviewDestination(
    navController: NavHostController,
    entry: NavBackStackEntry,
    history: RoundHistoryViewModel,
) {
    val route = entry.toRoute<LeoRoute.HistoryReview>()
    val round =
        rememberRound(history, route.roundId) { navController.popIfOnTop(entry) }
    val misses = round?.misses.orEmpty()
    if (round != null && misses.isEmpty()) {
        // A round without misses has nothing to review.
        LaunchedEffect(Unit) { navController.popIfOnTop(entry) }
    }
    if (misses.isEmpty()) {
        Box(Modifier.fillMaxSize().leoScreenBackground())
    } else {
        ReviewScreen(
            misses = misses,
            startIndex = route.startIndex,
            onBack = dropUnlessResumed { navController.popBackStack() },
            backTitle = stringResource(R.string.review_back_to_round),
            parentTitle = stringResource(R.string.navigation_back),
        )
    }
}

/**
 * The round [id] the route is about: the one History just opened, or, when the route was restored after the process
 * died, the one loaded again by id. Null while loading; [onMissing] when it can't be loaded (deleted, unreadable) or
 * the id isn't one, so a restored route never shows a blank screen for good.
 */
@Composable
private fun rememberRound(
    history: RoundHistoryViewModel,
    id: String,
    onMissing: () -> Unit,
): RoundRecord? {
    val opened =
        history.uiState
            .collectAsStateWithLifecycle()
            .value.openedRound
    val match = opened?.takeIf { it.id.toString() == id }
    LaunchedEffect(id, match == null) {
        if (match == null) {
            val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            if (uuid == null || history.open(uuid) == null) onMissing()
        }
    }
    return match
}

/** Leaves [entry], even while it is still entering, but only if it is the screen on top: never pops another one. */
private fun NavHostController.popIfOnTop(entry: NavBackStackEntry) {
    if (currentBackStackEntry == entry) popBackStack()
}
