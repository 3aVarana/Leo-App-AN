package com.victorarana.leo.features.root

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.victorarana.leo.features.preferences.PreferencesEditorViewModel
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.features.quiz.QuizScreen
import com.victorarana.leo.features.quiz.ReviewScreen
import com.victorarana.leo.features.theme.rememberReduceMotion

/**
 * The graph of the onboarded app: Home (the quiz), with Review, Settings and History pushed over it. Back from
 * History pops (iOS: the `NavigationStack`). History's placeholder is replaced in phase 7.
 *
 * @param navController Owned by the caller, above the gates, so the back stack outlives this graph leaving
 * composition (see `RootScreen`).
 */
@Composable
fun LeoNavHost(
    navController: NavHostController,
    root: RootViewModel,
    modifier: Modifier = Modifier,
) {
    // The sheet's slide, none when the reader asked for no motion.
    val reduceMotion = rememberReduceMotion()
    NavHost(navController = navController, startDestination = LeoRoute.Home, modifier = modifier) {
        // A sheet or a pushed page leaves its presenter still: Home neither fades out under Settings or Review nor
        // fades back in as they close, and stays composed (motionless) until the slide ends, not removed at once as
        // with `ExitTransition.None` (null keeps the NavHost default for every other destination).
        composable<LeoRoute.Home>(
            exitTransition = {
                if (targetState.destination.isOverHome()) ExitTransition.KeepUntilTransitionsFinished else null
            },
            popEnterTransition = {
                if (initialState.destination.isOverHome()) EnterTransition.None else null
            },
        ) {
            val state by root.uiState.collectAsStateWithLifecycle()
            val preferences = (state.preferences as? PreferencesState.Ready)?.preferences
            // Prepares a round on launch, after onboarding and whenever settings are saved (iOS
            // `.task(id: preferences.roundSettings)`). The quiz keeps the round it has when the settings are the same.
            LaunchedEffect(preferences) { root.preferencesDidChange() }
            if (preferences != null) {
                // `dropUnlessResumed` reads the entry's lifecycle, so it is made here, not above the NavHost: a tap
                // while the entry is leaving RESUMED (a second tap during an exit animation) is dropped. A second
                // pop would take Home off the stack and a second push would open the screen twice. iOS `dismiss()`
                // is idempotent.
                val owner = LocalLifecycleOwner.current
                QuizScreen(
                    quiz = root.quiz,
                    preferences = preferences,
                    onSettings = dropUnlessResumed { navController.navigate(LeoRoute.Settings) },
                    onReview = { index ->
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.navigate(LeoRoute.Review(index))
                        }
                    },
                )
            }
        }
        composable<LeoRoute.Review>(
            enterTransition = { if (reduceMotion) EnterTransition.None else slideInHorizontally { it } },
            popExitTransition = { if (reduceMotion) ExitTransition.None else slideOutHorizontally { it } },
        ) { entry ->
            val startIndex = entry.toRoute<LeoRoute.Review>().startIndex
            val misses =
                root.quiz.uiState
                    .collectAsStateWithLifecycle()
                    .value.misses
            if (misses.isEmpty()) {
                // A restored route after process death: the round it belonged to is gone.
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ReviewScreen(
                    misses = misses,
                    startIndex = startIndex,
                    onBack = dropUnlessResumed { navController.popBackStack() },
                )
            }
        }
        composable<LeoRoute.History> { HistoryPlaceholder() }
        composable<LeoRoute.Settings>(
            enterTransition = { if (reduceMotion) EnterTransition.None else slideInVertically { it } },
            popExitTransition = { if (reduceMotion) ExitTransition.None else slideOutVertically { it } },
        ) {
            // Scoped to this back-stack entry: each opening starts from the current preferences, and leaving
            // (Cancel, Done, back) clears the draft and any pending review.
            val editor =
                viewModel<PreferencesEditorViewModel>(
                    factory =
                        viewModelFactory {
                            initializer { requireNotNull(root.makeSettingsEditor()) { "Settings before onboarding" } }
                        },
                )
            val cancel = dropUnlessResumed { navController.popBackStack() }
            // Saving is inside the dropped tap too, so a double tap on Done saves once.
            val done =
                dropUnlessResumed {
                    editor.save()
                    navController.popBackStack()
                }
            SettingsScreen(
                editor = editor,
                onCancel = cancel,
                onDone = done,
            )
        }
    }
}

/** The destinations that open over Home and leave it where it is. */
private fun NavDestination.isOverHome(): Boolean = hasRoute<LeoRoute.Settings>() || hasRoute<LeoRoute.Review>()
