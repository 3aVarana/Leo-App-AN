package com.victorarana.leo.features.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.victorarana.leo.data.repositories.TopicReviewRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.CustomTopic
import com.victorarana.leo.domain.DefaultTopic
import com.victorarana.leo.domain.DefaultTopics
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.ReaderPreferences.NewTopicCheck
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.TopicReviewOutcome
import com.victorarana.leo.domain.check
import com.victorarana.leo.domain.enabledSuggestedTopicCount
import com.victorarana.leo.domain.enabledTopicCount
import com.victorarana.leo.domain.insertTopic
import com.victorarana.leo.domain.isAtMinimum
import com.victorarana.leo.domain.removeCustomTopic
import com.victorarana.leo.domain.resetSuggestedTopics
import com.victorarana.leo.domain.setEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Why the last topic wasn't added. iOS keeps a localized `String`; a ViewModel here has no `Context`, so the view
 * resolves the text (phase-5 D2). [Rejected] carries the model's reason verbatim.
 */
sealed interface TopicMessage {
    data object InvalidLength : TopicMessage

    data object AlreadyOnList : TopicMessage

    data object TooManyCustomTopics : TopicMessage

    data object ReviewFailed : TopicMessage

    data class Rejected(
        val reason: String,
    ) : TopicMessage
}

/**
 * What the editor screens draw from. The topics are those of `draft.ageGroup`. The derived properties are the
 * computed properties of the Swift ViewModel.
 */
data class PreferencesEditorUiState(
    val draft: ReaderPreferences,
    /** Why the last topic wasn't added, for the reader. */
    val message: TopicMessage? = null,
    val isReviewing: Boolean = false,
    /** Set when the reader tried to turn off a topic at the minimum, until a topic is added. */
    val isShowingMinimumWarning: Boolean = false,
    /** Counts refused attempts, so the view can play a haptic for each. */
    val minimumWarningCount: Int = 0,
) {
    private val group: AgeGroup get() = draft.ageGroup

    val suggestedTopics: List<DefaultTopic> get() = DefaultTopics.topics(group)
    val customTopics: List<CustomTopic> get() = draft.customTopics[group].orEmpty()

    /** Suggested and custom topics that are on. */
    val enabledTopicCount: Int get() = draft.enabledTopicCount(group)
    val enabledSuggestedCount: Int get() = draft.enabledSuggestedTopicCount(group)
    val isAtMinimum: Boolean get() = draft.isAtMinimum(group)
    val canResetSuggestedTopics: Boolean get() = !draft.disabledDefaultTopics[group].isNullOrEmpty()

    fun isEnabled(topic: DefaultTopic): Boolean = draft.isEnabled(topic, group)

    /** A topic that's on can't be turned off at the minimum. */
    fun isToggleDisabled(topic: DefaultTopic): Boolean = isEnabled(topic) && isAtMinimum
}

/**
 * Edits a draft of the reader's preferences, for onboarding and settings. Nothing reaches [onSave] until [save].
 * Port of `PreferencesEditorViewModel`; the Swift `didSet` rules live in [updateDraft] and [onNewTopicChange].
 */
@Suppress("TooManyFunctions") // The Swift ViewModel's whole surface: one function per reader action.
class PreferencesEditorViewModel(
    draft: ReaderPreferences,
    private val topicReviews: TopicReviewRepository,
    private val topicNames: TopicNames,
    private val onSave: (ReaderPreferences) -> Unit,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PreferencesEditorUiState(draft))
    val uiState: StateFlow<PreferencesEditorUiState> = _uiState.asStateFlow()

    /**
     * The topic being typed. Editing it clears the message. Compose state, not part of [uiState]: a text field
     * keeps its IME composition only when it reads and writes state synchronously.
     */
    var newTopic by mutableStateOf("")
        private set

    private var review: Job? = null

    private val state get() = _uiState.value
    private val group: AgeGroup get() = state.draft.ageGroup
    private val trimmedTopic: String get() = newTopic.trim()

    val canAdd: Boolean get() = trimmedTopic.isNotEmpty() && !state.isReviewing

    /** Edits the text. A different text clears the message. */
    fun onNewTopicChange(value: String) {
        if (value == newTopic) return
        newTopic = value
        _uiState.update { it.copy(message = null) }
    }

    // MARK: - The draft

    fun setAgeGroup(ageGroup: AgeGroup) = updateDraft { it.copy(ageGroup = ageGroup) }

    fun setExercisesPerRound(count: Int) = updateDraft { it.copy(exercisesPerRound = count) }

    fun setReadingTimerOn(isOn: Boolean) = updateDraft { it.copy(isReadingTimerOn = isOn) }

    /** Turning off a topic at the minimum is refused, and shows the minimum warning instead. */
    fun setEnabled(
        isOn: Boolean,
        topic: DefaultTopic,
    ) {
        if (!isOn && state.isToggleDisabled(topic)) {
            _uiState.update {
                it.copy(isShowingMinimumWarning = true, minimumWarningCount = it.minimumWarningCount + 1)
            }
            return
        }
        updateDraft { it.setEnabled(isOn, topic, it.ageGroup) }
    }

    fun resetSuggestedTopics() = updateDraft { it.resetSuggestedTopics(it.ageGroup) }

    /** Does nothing at the minimum, where the remove control is hidden. */
    fun removeCustomTopic(topic: CustomTopic) {
        if (state.isAtMinimum) return
        updateDraft { it.removeCustomTopic(topic.id, it.ageGroup) }
    }

    /**
     * Every draft change goes through here. Changing the age group cancels a pending review (it checks the topic
     * for the group it was typed under), and leaving the minimum hides its warning.
     */
    private fun updateDraft(transform: (ReaderPreferences) -> ReaderPreferences) {
        val old = state.draft
        val new = transform(old)
        if (new.ageGroup != old.ageGroup) cancelReview()
        _uiState.update {
            it.copy(
                draft = new,
                isShowingMinimumWarning = it.isShowingMinimumWarning && new.isAtMinimum(new.ageGroup),
            )
        }
    }

    // MARK: - Adding a topic

    /** Adds the typed topic after the local checks, asking the model to review it when needed. */
    fun add() {
        if (state.isReviewing) return
        val text = trimmedTopic
        if (text.isEmpty()) return
        when (state.draft.check(text, group, topicNames)) {
            NewTopicCheck.InvalidLength -> showMessage(TopicMessage.InvalidLength)
            NewTopicCheck.AlreadyOnList -> showMessage(TopicMessage.AlreadyOnList)
            is NewTopicCheck.ReEnablesSuggested -> insert(text, group)
            NewTopicCheck.TooManyCustomTopics -> showMessage(TopicMessage.TooManyCustomTopics)
            NewTopicCheck.NeedsReview -> startReview(text)
        }
    }

    fun cancelReview() {
        review?.cancel()
        review = null
        _uiState.update { it.copy(isReviewing = false) }
    }

    /** Hands the whole draft to the owner, and ends a pending review: the editor is done (phase-5 D4). */
    fun save() {
        cancelReview()
        onSave(state.draft)
    }

    private fun showMessage(message: TopicMessage?) = _uiState.update { it.copy(message = message) }

    /** Adds [name] unless it's already on the list, and clears the field when it was added. */
    private fun insert(
        name: String,
        group: AgeGroup,
    ) {
        val inserted = state.draft.insertTopic(name, group, topicNames)
        if (inserted != null) {
            updateDraft { inserted }
            onNewTopicChange("")
        } else {
            showMessage(TopicMessage.AlreadyOnList)
        }
    }

    /**
     * Asks the model whether [text] suits the reader, and adds its tidied-up phrase if it does. The review belongs
     * to the age group at this moment, and is dropped if it's cancelled.
     */
    @Suppress("TooGenericExceptionCaught") // Any failure of the model reads as "couldn't check"; cancel is rethrown.
    private fun startReview(text: String) {
        val group = group
        // Lazy, so `review` is set before the body can finish: on the immediate main dispatcher a repository that
        // never suspends would otherwise end the review before it was recorded.
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val result =
                    try {
                        Result.success(topicReviews.review(text, group))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                // `guard !Task.isCancelled`: a result that arrives after a cancel is dropped.
                ensureActive()
                review = null
                _uiState.update { it.copy(isReviewing = false) }
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is TopicReviewOutcome.Accepted -> insert(outcome.name, group)
                            is TopicReviewOutcome.Rejected -> showMessage(TopicMessage.Rejected(outcome.reason))
                        }
                    },
                    onFailure = { showMessage(TopicMessage.ReviewFailed) },
                )
            }
        review = job
        _uiState.update { it.copy(message = null, isReviewing = true) }
        job.start()
    }
}
