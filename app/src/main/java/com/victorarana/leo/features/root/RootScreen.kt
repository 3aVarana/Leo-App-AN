package com.victorarana.leo.features.root

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.features.theme.leoScreenBackground

/** Which screen the gates pick (iOS `RootView.body`'s switch). */
private enum class Gate { Loading, Onboarding, App }

/**
 * Picks between the unavailable screen, onboarding and the app, and plays the launch intro over all three.
 * Port of `RootView`: availability and onboarding are gates, not routes (D1).
 */
@Composable
fun RootScreen(
    viewModel: RootViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Back from another app, the model may have been downloaded or switched off meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    // Above the gates, so a check that briefly reports the model unavailable keeps the back stack and the
    // destinations' ViewModels. `rememberSaveable` state inside destinations still resets when the graph leaves
    // composition, so anything that must survive this (a round in progress) belongs in a ViewModel.
    val navController = rememberNavController()

    val availability = state.availability
    // The launch frame holds until the first screen is known: AICore has answered, and preferences matter only if
    // the app can open.
    val isReady =
        state.availabilityChecked &&
            (availability !is ModelAvailability.Available || state.preferences !is PreferencesState.Loading)
    LaunchIntroHost(
        modifier = modifier,
        isReady = isReady,
        // Read once, when the host starts (a plain property, not state): later changes don't need to recompose.
        hasPlayed = viewModel.introPlayed,
        onPlayed = viewModel::onIntroPlayed,
    ) {
        if (availability is ModelAvailability.Available) {
            val gate =
                when (state.preferences) {
                    PreferencesState.Loading -> Gate.Loading
                    PreferencesState.NotOnboarded -> Gate.Onboarding
                    is PreferencesState.Ready -> Gate.App
                }
            // The swap between onboarding and the app animates (`.animation(.default, value: preferences == nil)`).
            Crossfade(targetState = gate, label = "root-gate") {
                when (it) {
                    Gate.Loading -> Box(Modifier.fillMaxSize().leoScreenBackground())
                    Gate.Onboarding -> OnboardingPlaceholder()
                    Gate.App -> LeoNavHost(navController = navController)
                }
            }
        } else {
            UnavailableScreen(availability)
        }
    }
}
