package com.victorarana.leo.features.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorarana.leo.data.repositories.ModelAvailabilityProvider
import com.victorarana.leo.data.repositories.PreferencesRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ReaderPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What `RootViewModel.preferences` (an optional) says on iOS, with the one state iOS doesn't have: loading is
 * asynchronous here (phase-2 D1), and the first screen must not flash onboarding while it runs.
 */
sealed interface PreferencesState {
    data object Loading : PreferencesState

    /** Loaded, and there is nothing saved: onboarding isn't done. */
    data object NotOnboarded : PreferencesState

    data class Ready(
        val preferences: ReaderPreferences,
    ) : PreferencesState
}

/** Everything `RootScreen` draws from. */
data class RootUiState(
    val preferences: PreferencesState,
    val availability: ModelAvailability,
    /**
     * Whether AICore has answered once. Until then [availability] is the provider's placeholder ("downloading"),
     * which must not reach the screen: the launch intro waits for the first real status.
     */
    val availabilityChecked: Boolean = true,
)

/**
 * Owns the reader's preferences and is the only place they are saved. The phase-4 subset of iOS `RootViewModel`:
 * the quiz, history and editors join it in phases 5 to 7.
 */
class RootViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val availabilityProvider: ModelAvailabilityProvider,
) : ViewModel() {
    private val preferences = MutableStateFlow<PreferencesState>(PreferencesState.Loading)
    private val availabilityChecked = MutableStateFlow(false)
    private var resumeCheck: Job? = null

    // Saves run one at a time, so a quick second save of the same preferences sees the first one's result.
    private val saving = Mutex()

    /** [availability] is combined, never copied, so the screen swaps as soon as the model becomes available. */
    val uiState: StateFlow<RootUiState> =
        combine(preferences, availabilityProvider.availability, availabilityChecked, ::RootUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    RootUiState(
                        preferences.value,
                        availabilityProvider.availability.value,
                        availabilityChecked = false,
                    ),
            )

    init {
        viewModelScope.launch {
            preferences.value =
                preferencesRepository.load()?.let(PreferencesState::Ready) ?: PreferencesState.NotOnboarded
        }
        // Checks AICore, then starts the download when the model is downloadable (phase-3 R2-N5). A slow AICore
        // is waited for only up to the ceiling, so the screen then shows what the provider says instead of the
        // bare ground; the check itself is never cancelled, and the download decision waits for its answer (on
        // the placeholder it would start a download that is not needed, or fail on a device that cannot run it).
        viewModelScope.launch {
            val check = launch { availabilityProvider.refresh() }
            withTimeoutOrNull(AVAILABILITY_CHECK_CEILING_MILLIS) { check.join() }
            availabilityChecked.value = true
            check.join()
            availabilityProvider.downloadIfNeeded()
        }
    }

    /**
     * Asks AICore again when the app comes back to the foreground, and starts the download if the model has become
     * downloadable meanwhile (after a failed download, or a device that was not ready). While a download runs,
     * `download()` starts no other one but suspends until it ends; a resume meanwhile (a rotation, say) starts no
     * second check while the first is still waiting.
     */
    fun onResume() {
        if (resumeCheck?.isActive == true) return
        resumeCheck =
            viewModelScope.launch {
                availabilityProvider.refresh()
                availabilityProvider.downloadIfNeeded()
            }
    }

    /** Saves and applies [newPreferences]. Does nothing when they haven't changed. */
    fun save(newPreferences: ReaderPreferences) {
        if ((preferences.value as? PreferencesState.Ready)?.preferences == newPreferences) return
        viewModelScope.launch {
            saving.withLock {
                if ((preferences.value as? PreferencesState.Ready)?.preferences == newPreferences) return@withLock
                preferencesRepository.save(newPreferences)
                preferences.value = PreferencesState.Ready(newPreferences)
            }
        }
    }

    companion object {
        /** How long the first launch frame waits for AICore's first answer. */
        const val AVAILABILITY_CHECK_CEILING_MILLIS = 2_000L

        /** The draft the onboarding editor starts from. Saving it ends onboarding. */
        val onboardingDraft = ReaderPreferences(ageGroup = AgeGroup.FIFTEEN)
    }
}
