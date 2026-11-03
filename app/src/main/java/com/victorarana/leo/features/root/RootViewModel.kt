package com.victorarana.leo.features.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorarana.leo.data.repositories.ModelAvailabilityProvider
import com.victorarana.leo.data.repositories.PreferencesRepository
import com.victorarana.leo.data.repositories.TopicReviewRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.features.preferences.PreferencesEditorViewModel
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
     * Whether the launch frame may stop waiting: AICore has answered, or the launch check reached its ceiling.
     * Until then [availability] is the provider's placeholder ("downloading"), which must not reach the screen.
     */
    val availabilityChecked: Boolean,
    /**
     * Whether AICore has really answered. After a slow launch check the screen shows [availability] while this is
     * still false: the placeholder, which must not claim a download that hasn't started.
     */
    val availabilityAnswered: Boolean,
)

/**
 * Owns the reader's preferences and is the only place they are saved. The phase-5 subset of iOS `RootViewModel`:
 * the quiz and history join it in phases 6 and 7.
 */
class RootViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val availabilityProvider: ModelAvailabilityProvider,
    private val topicReviews: TopicReviewRepository,
    private val topicNames: TopicNames,
) : ViewModel() {
    private val preferences = MutableStateFlow<PreferencesState>(PreferencesState.Loading)
    private val availabilityChecked = MutableStateFlow(false)
    private val availabilityAnswered = MutableStateFlow(false)
    private var resumeCheck: Job? = null

    /**
     * Whether this Activity played the launch intro. Kept here, not process-wide: a rotation keeps it, a new
     * Activity (a warm start after backing out, which shows the system splash again) plays the intro again.
     */
    var introPlayed = false
        private set

    // Saves run one at a time, so a quick second save of the same preferences sees the first one's result.
    private val saving = Mutex()

    /** [availability] is combined, never copied, so the screen swaps as soon as the model becomes available. */
    val uiState: StateFlow<RootUiState> =
        combine(
            preferences,
            availabilityProvider.availability,
            availabilityChecked,
            availabilityAnswered,
            ::RootUiState,
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                RootUiState(
                    preferences.value,
                    availabilityProvider.availability.value,
                    availabilityChecked = false,
                    availabilityAnswered = false,
                ),
        )

    init {
        // Under the save lock, so a save waits for the load. The `Loading` check is defensive: on the main dispatcher
        // the load takes the lock before any save can run.
        viewModelScope.launch {
            saving.withLock {
                val loaded = preferencesRepository.load()?.let(PreferencesState::Ready) ?: PreferencesState.NotOnboarded
                if (preferences.value == PreferencesState.Loading) preferences.value = loaded
            }
        }
        // Checks AICore, then starts the download when the model is downloadable (phase-3 R2-N5). A slow AICore
        // is waited for only up to the ceiling, so the screen then shows what the provider says instead of the
        // bare ground; the check itself is never cancelled, and the download decision waits for its answer (on
        // the placeholder it would start a download that is not needed, or fail on a device that cannot run it).
        viewModelScope.launch {
            val check =
                launch {
                    availabilityProvider.refresh()
                    availabilityAnswered.value = true
                }
            withTimeoutOrNull(AVAILABILITY_CHECK_CEILING_MILLIS) { check.join() }
            availabilityChecked.value = true
            check.join()
            availabilityProvider.downloadIfNeeded()
        }
    }

    /**
     * Asks AICore again when the app comes back to the foreground, and starts the download if the model has become
     * downloadable meanwhile (after a failed download, or a device that was not ready).
     *
     * Skipped until the launch check has answered or reached its ceiling, so the first ON_RESUME (right after launch)
     * doesn't ask AICore a second time; after the ceiling it does, so a launch check that never returns doesn't stop
     * every later one. While a download runs, `download()` starts no other one but suspends until it ends; a resume
     * meanwhile (a rotation, say) starts no second check while that one is still waiting.
     */
    fun onResume() {
        if (!availabilityChecked.value || resumeCheck?.isActive == true) return
        resumeCheck =
            viewModelScope.launch {
                availabilityProvider.refresh()
                availabilityAnswered.value = true
                availabilityProvider.downloadIfNeeded()
            }
    }

    fun onIntroPlayed() {
        introPlayed = true
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

    /** An editor for the first launch. Saving it ends onboarding. */
    fun makeOnboardingEditor(): PreferencesEditorViewModel = makeEditor(onboardingDraft)

    /** An editor over the current preferences. Null before onboarding is done. */
    fun makeSettingsEditor(): PreferencesEditorViewModel? =
        (preferences.value as? PreferencesState.Ready)?.let { makeEditor(it.preferences) }

    // Saves through this ViewModel, which outlives every editor (iOS captures `self` weakly; the editors here are
    // owned by the Activity's store or a back-stack entry, both inside this one's lifetime).
    private fun makeEditor(draft: ReaderPreferences) =
        PreferencesEditorViewModel(draft, topicReviews, topicNames, onSave = ::save)

    companion object {
        /** How long the first launch frame waits for AICore's first answer. */
        const val AVAILABILITY_CHECK_CEILING_MILLIS = 2_000L

        /** The draft the onboarding editor starts from. Saving it ends onboarding. */
        val onboardingDraft = ReaderPreferences(ageGroup = AgeGroup.FIFTEEN)
    }
}
