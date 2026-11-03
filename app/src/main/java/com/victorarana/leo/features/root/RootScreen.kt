package com.victorarana.leo.features.root

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.features.preferences.OnboardingScreen
import com.victorarana.leo.features.preferences.PreferencesEditorViewModel
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
    // On-device inference is refused from the background: stop writing when the app leaves the screen and go on
    // when it returns, instead of failing every topic of the round (phase-6 D7).
    // A configuration change (rotation, dark mode, font size) also stops the old Activity, but the ViewModel survives
    // it and the new Activity starts again at once: pausing would cancel the Gemini Nano request and write the same
    // text from scratch. Only a real departure pauses; the new Activity's `resume()` is then a no-op.
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.quiz.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.quiz.resume() }
    // Above the gates, so a check that briefly reports the model unavailable keeps the back stack and the
    // destinations' ViewModels. `rememberSaveable` state inside destinations still resets when the graph leaves
    // composition, so anything that must survive this (a round in progress) belongs in a ViewModel.
    val navController = rememberNavController()
    // Onboarding's back stack and age pick, hoisted for the same reason: coming back from another app mid-onboarding
    // shouldn't restart it at the age picker.
    val onboardingNavController = rememberNavController()
    var onboardingSelection by rememberSaveable { mutableStateOf<AgeGroup?>(null) }

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
                    Gate.Onboarding -> {
                        // In the Activity's store (the gate isn't a destination), so a rotation keeps the draft.
                        // It then outlives onboarding until the Activity finishes; harmless, since the gate never
                        // comes back and `save()` already cancelled its review.
                        val editor =
                            viewModel<PreferencesEditorViewModel>(
                                key = "onboarding",
                                factory = viewModelFactory { initializer { viewModel.makeOnboardingEditor() } },
                            )
                        OnboardingScreen(
                            editor = editor,
                            navController = onboardingNavController,
                            selection = onboardingSelection,
                            onSelect = { group -> onboardingSelection = group },
                        )
                    }
                    Gate.App -> LeoNavHost(navController = navController, root = viewModel)
                }
            }
        } else {
            UnavailableScreen(availability, isAnswered = state.availabilityAnswered)
        }
    }
}
