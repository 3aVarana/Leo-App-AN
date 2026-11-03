package com.victorarana.leo.features.root

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.victorarana.leo.features.preferences.PreferencesEditorViewModel
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.features.theme.rememberReduceMotion

/**
 * The graph of the onboarded app: Home, with History and Settings pushed over it. Back from History pops (iOS: the
 * `NavigationStack`). The placeholders are replaced in phases 6 and 7.
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
        // A sheet leaves its presenter still: Home neither fades out under Settings nor fades back in as it closes,
        // and stays composed (motionless) until the slide ends, not removed at once as with `ExitTransition.None`
        // (null keeps the NavHost default for every other destination).
        composable<LeoRoute.Home>(
            exitTransition = {
                if (targetState.destination.hasRoute<LeoRoute.Settings>()) {
                    ExitTransition.KeepUntilTransitionsFinished
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.hasRoute<LeoRoute.Settings>()) EnterTransition.None else null
            },
        ) {
            // `dropUnlessResumed` reads the entry's lifecycle, so it is made here, not above the NavHost: a tap while
            // the entry is leaving RESUMED (a second tap during an exit animation) is dropped. A second pop would
            // take Home off the stack and a second push would open the screen twice. iOS `dismiss()` is idempotent.
            HomePlaceholder(
                onHistory = dropUnlessResumed { navController.navigate(LeoRoute.History) },
                onSettings = dropUnlessResumed { navController.navigate(LeoRoute.Settings) },
            )
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
