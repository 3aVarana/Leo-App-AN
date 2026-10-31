package com.victorarana.leo.features.preferences

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.DefaultTopic
import com.victorarana.leo.domain.DefaultTopics
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.TopicReviewOutcome
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.support.StubTopicReviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Port of `PreferencesEditorViewModelTests`: every test, same names, plus the Android-only ones at the end. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesEditorViewModelTests {
    private class ReviewFailed : Exception()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun makeEditor(
        draft: ReaderPreferences = ReaderPreferences(ageGroup = AgeGroup.NINE),
        reviews: StubTopicReviewRepository = StubTopicReviewRepository(),
        onSave: (ReaderPreferences) -> Unit = {},
    ) = PreferencesEditorViewModel(draft, reviews, TopicNames.English, onSave)

    private val PreferencesEditorViewModel.state get() = uiState.value
    private val PreferencesEditorViewModel.draft get() = state.draft
    private val PreferencesEditorViewModel.message get() = state.message
    private val PreferencesEditorViewModel.isReviewing get() = state.isReviewing

    /** Types [text] and taps Add. */
    private fun PreferencesEditorViewModel.add(text: String) {
        onNewTopicChange(text)
        add()
    }

    private fun customTopicNames(
        editor: PreferencesEditorViewModel,
        group: AgeGroup,
    ): List<String> =
        editor.draft.customTopics[group]
            .orEmpty()
            .map { it.name }

    private val volcanoes: DefaultTopic
        get() = DefaultTopics.topics(AgeGroup.NINE).first { it.id == "volcanoes" }

    @Test
    fun startingState() {
        val draft = readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.NINE to listOf("Chess")))
        val editor = makeEditor(draft)
        assertEquals(draft, editor.draft)
        assertTrue(editor.newTopic.isEmpty())
        assertNull(editor.message)
        assertFalse(editor.isReviewing)
        assertFalse(editor.canAdd)
        assertEquals(DefaultTopics.topics(AgeGroup.NINE).map { it.id }, editor.state.suggestedTopics.map { it.id })
        assertEquals(listOf("Chess"), editor.state.customTopics.map { it.name })
    }

    @Test
    fun topicsFollowAgeGroup() {
        val editor =
            makeEditor(
                readerPreferencesFixture(
                    AgeGroup.NINE,
                    custom = mapOf(AgeGroup.NINE to listOf("Chess"), AgeGroup.TWELVE to listOf("Origami")),
                ),
            )
        editor.setAgeGroup(AgeGroup.TWELVE)
        assertEquals(DefaultTopics.topics(AgeGroup.TWELVE).map { it.id }, editor.state.suggestedTopics.map { it.id })
        assertEquals(listOf("Origami"), editor.state.customTopics.map { it.name })
    }

    // MARK: - Local checks

    @Test
    fun addEmptyDoesNothing() {
        for (text in listOf("", "   ", " \n ")) {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.add(text)
            assertTrue(reviews.calls.isEmpty())
            assertNull(editor.message)
            assertEquals(text, editor.newTopic)
            assertFalse(editor.canAdd)
        }
    }

    @Test
    fun addTrimsBeforeReview() =
        runTest {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.add("  rockets \n")
            runCurrent()
            assertEquals(listOf(StubTopicReviewRepository.Call("rockets", AgeGroup.NINE)), reviews.calls)
        }

    @Test
    fun addInvalidLengthShowsMessage() {
        for (text in listOf("x", "x".repeat(61))) {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.add(text)
            assertEquals(TopicMessage.InvalidLength, editor.message)
            assertTrue(reviews.calls.isEmpty())
            assertEquals(text, editor.newTopic)
        }
    }

    /** Covers a suggested topic and a custom topic. */
    @Test
    fun addEnabledDuplicateShowsMessage() {
        for (text in listOf("volcanoes", "CHESS")) {
            val reviews = StubTopicReviewRepository()
            val editor =
                makeEditor(
                    readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.NINE to listOf("Chess"))),
                    reviews,
                )
            editor.add(text)
            assertEquals(TopicMessage.AlreadyOnList, editor.message)
            assertTrue(reviews.calls.isEmpty())
            assertEquals(text, editor.newTopic)
        }
    }

    @Test
    fun addDisabledSuggestedReEnablesWithoutReview() {
        val reviews = StubTopicReviewRepository()
        val editor =
            makeEditor(
                readerPreferencesFixture(AgeGroup.NINE, disabled = mapOf(AgeGroup.NINE to setOf("volcanoes"))),
                reviews,
            )
        editor.add("Volcanoes")
        assertTrue(editor.state.isEnabled(volcanoes))
        assertTrue(editor.newTopic.isEmpty())
        assertNull(editor.message)
        assertTrue(reviews.calls.isEmpty())
        assertTrue(editor.state.customTopics.isEmpty())
    }

    @Test
    fun addOverCustomLimitShowsMessage() {
        val reviews = StubTopicReviewRepository()
        val full = (1..ReaderPreferences.MAXIMUM_CUSTOM_TOPICS).map { "Topic $it" }
        val editor = makeEditor(readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.NINE to full)), reviews)
        editor.add("Rockets")
        assertEquals(TopicMessage.TooManyCustomTopics, editor.message)
        assertTrue(reviews.calls.isEmpty())
    }

    @Test
    fun editingFieldClearsMessage() {
        val editor = makeEditor()
        editor.add("x")
        assertTrue(editor.message != null)
        editor.onNewTopicChange("xy")
        assertNull(editor.message)
    }

    // MARK: - Reviews

    @Test
    fun acceptedReviewAddsPhrase() =
        runTest {
            val reviews =
                StubTopicReviewRepository(listOf(Result.success(TopicReviewOutcome.Accepted("Chess Openings"))))
            val editor = makeEditor(reviews = reviews)
            editor.add("chess openings")
            runCurrent()
            assertFalse(editor.isReviewing)
            assertEquals(listOf("Chess Openings"), customTopicNames(editor, AgeGroup.NINE))
            assertTrue(editor.newTopic.isEmpty())
            assertNull(editor.message)
        }

    @Test
    fun acceptedPhraseAlreadyOnListShowsMessage() =
        runTest {
            val reviews =
                StubTopicReviewRepository(listOf(Result.success(TopicReviewOutcome.Accepted("Dinosaurs and fossils"))))
            val editor = makeEditor(reviews = reviews)
            editor.add("dinos")
            runCurrent()
            assertEquals(TopicMessage.AlreadyOnList, editor.message)
            assertEquals("dinos", editor.newTopic)
            assertTrue(editor.state.customTopics.isEmpty())
            assertFalse(editor.isReviewing)
        }

    @Test
    fun acceptedPhraseReEnablesSuggested() =
        runTest {
            val reviews = StubTopicReviewRepository(listOf(Result.success(TopicReviewOutcome.Accepted("Volcanoes"))))
            val editor =
                makeEditor(
                    readerPreferencesFixture(AgeGroup.NINE, disabled = mapOf(AgeGroup.NINE to setOf("volcanoes"))),
                    reviews,
                )
            editor.add("lava mountains")
            runCurrent()
            assertFalse(editor.isReviewing)
            assertTrue(editor.state.isEnabled(volcanoes))
            assertTrue(editor.state.customTopics.isEmpty())
            assertTrue(editor.newTopic.isEmpty())
        }

    @Test
    fun rejectedReviewShowsReason() =
        runTest {
            val reviews =
                StubTopicReviewRepository(listOf(Result.success(TopicReviewOutcome.Rejected("Try something gentler."))))
            val draft = readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.NINE to listOf("Chess")))
            val editor = makeEditor(draft, reviews)
            editor.add("scary stuff")
            runCurrent()
            assertEquals(TopicMessage.Rejected("Try something gentler."), editor.message)
            assertEquals("scary stuff", editor.newTopic)
            assertEquals(draft, editor.draft)
            assertFalse(editor.isReviewing)
        }

    @Test
    fun failedReviewShowsGenericMessage() =
        runTest {
            val reviews = StubTopicReviewRepository(listOf(Result.failure(ReviewFailed())))
            val editor = makeEditor(reviews = reviews)
            editor.add("rockets")
            runCurrent()
            assertEquals(TopicMessage.ReviewFailed, editor.message)
            assertEquals("rockets", editor.newTopic)
            assertFalse(editor.isReviewing)
        }

    @Test
    fun isReviewingWhilePending() =
        runTest {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.add("rockets")
            assertTrue(editor.isReviewing)
            assertFalse(editor.canAdd)
            assertNull(editor.message)

            runCurrent()
            assertEquals(1, reviews.waitingCount)
            reviews.resume(TopicReviewOutcome.Accepted("Rockets"))
            runCurrent()
            assertFalse(editor.isReviewing)
            assertEquals(listOf("Rockets"), customTopicNames(editor, AgeGroup.NINE))
        }

    @Test
    fun addWhileReviewingIsIgnored() =
        runTest {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.add("rockets")
            runCurrent()
            assertEquals(1, reviews.waitingCount)
            editor.add("comets")
            runCurrent()
            assertEquals(1, reviews.calls.size)
            assertEquals("comets", editor.newTopic)
        }

    @Test
    fun changingAgeGroupCancelsReview() =
        runTest {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.add("rockets")
            runCurrent()
            assertEquals(1, reviews.waitingCount)

            editor.setAgeGroup(AgeGroup.TWELVE)
            assertFalse(editor.isReviewing)
            runCurrent()
            assertEquals(1, reviews.cancelledCount)
            assertTrue(editor.draft.customTopics.isEmpty())
            assertNull(editor.message)
            assertFalse(editor.isReviewing)
        }

    @Test
    fun lateResultAfterCancelIsDropped() =
        runTest {
            val reviews = StubTopicReviewRepository(ignoresCancellation = true)
            val editor = makeEditor(reviews = reviews)
            editor.add("rockets")
            runCurrent()
            assertEquals(1, reviews.waitingCount)

            editor.cancelReview()
            reviews.resume(TopicReviewOutcome.Accepted("Rockets"))
            runCurrent()
            assertTrue(editor.draft.customTopics.isEmpty())
            assertEquals("rockets", editor.newTopic)
            assertNull(editor.message)
            assertFalse(editor.isReviewing)
        }

    @Test
    fun reviewUsesGroupAtTimeOfAdd() =
        runTest {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.setAgeGroup(AgeGroup.ADULT)
            editor.add("rockets")
            runCurrent()
            assertEquals(listOf(StubTopicReviewRepository.Call("rockets", AgeGroup.ADULT)), reviews.calls)
        }

    /** The port of `deinitCancelsReview`: clearing the ViewModel (its owner going away) cancels the review. */
    @Test
    fun clearingTheViewModelCancelsReview() =
        runTest {
            val reviews = StubTopicReviewRepository()
            val store = ViewModelStore()
            val factory =
                viewModelFactory {
                    initializer { makeEditor(reviews = reviews) }
                }
            val editor = ViewModelProvider.create(store, factory)[PreferencesEditorViewModel::class.java]
            editor.add("rockets")
            runCurrent()
            assertEquals(1, reviews.waitingCount)

            store.clear()
            runCurrent()
            assertEquals(1, reviews.cancelledCount)
        }

    // MARK: - Toggles, reset, delete and save

    @Test
    fun toggleDisabledAtMinimum() {
        val ids = DefaultTopics.topics(AgeGroup.SIX).map { it.id }
        val editor =
            makeEditor(
                readerPreferencesFixture(AgeGroup.SIX, disabled = mapOf(AgeGroup.SIX to ids.dropLast(3).toSet())),
            )
        val enabledTopic = DefaultTopics.topics(AgeGroup.SIX).last()
        val disabledTopic = DefaultTopics.topics(AgeGroup.SIX).first()
        assertTrue(editor.state.isAtMinimum)
        assertTrue(editor.state.isToggleDisabled(enabledTopic))
        assertFalse(editor.state.isToggleDisabled(disabledTopic))

        editor.setEnabled(true, disabledTopic)
        assertFalse(editor.state.isAtMinimum)
        assertFalse(editor.state.isToggleDisabled(enabledTopic))

        editor.setEnabled(false, disabledTopic)
        assertTrue(editor.state.isAtMinimum)
    }

    @Test
    fun resetSuggestedTopics() {
        val editor =
            makeEditor(
                readerPreferencesFixture(
                    AgeGroup.NINE,
                    disabled = mapOf(AgeGroup.NINE to setOf("volcanoes"), AgeGroup.SIX to setOf("dinosaurs")),
                ),
            )
        assertTrue(editor.state.canResetSuggestedTopics)
        editor.resetSuggestedTopics()
        assertFalse(editor.state.canResetSuggestedTopics)
        assertTrue(editor.state.isEnabled(volcanoes))
        assertEquals(mapOf(AgeGroup.SIX to setOf("dinosaurs")), editor.draft.disabledDefaultTopics)
    }

    @Test
    fun removeCustomTopic() {
        val editor =
            makeEditor(
                readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.NINE to listOf("Chess", "Origami"))),
            )
        editor.removeCustomTopic(editor.state.customTopics.first())
        assertEquals(listOf("Origami"), editor.state.customTopics.map { it.name })
    }

    /** At the minimum the remove control is hidden; a stray call changes nothing. */
    @Test
    fun removeCustomTopicAtMinimumDoesNothing() {
        val ids = DefaultTopics.topics(AgeGroup.SIX).map { it.id }
        val editor =
            makeEditor(
                readerPreferencesFixture(
                    AgeGroup.SIX,
                    disabled = mapOf(AgeGroup.SIX to ids.dropLast(2).toSet()),
                    custom = mapOf(AgeGroup.SIX to listOf("Chess")),
                ),
            )
        assertTrue(editor.state.isAtMinimum)
        editor.removeCustomTopic(editor.state.customTopics.first())
        assertEquals(listOf("Chess"), editor.state.customTopics.map { it.name })
    }

    @Test
    fun counts() {
        val editor =
            makeEditor(
                readerPreferencesFixture(
                    AgeGroup.NINE,
                    disabled = mapOf(AgeGroup.NINE to setOf("volcanoes"), AgeGroup.SIX to setOf("dinosaurs")),
                    custom = mapOf(AgeGroup.NINE to listOf("Chess", "Origami")),
                ),
            )
        val suggested = DefaultTopics.topics(AgeGroup.NINE).size
        assertEquals(suggested - 1, editor.state.enabledSuggestedCount)
        assertEquals(suggested + 1, editor.state.enabledTopicCount)

        editor.setAgeGroup(AgeGroup.SIX)
        assertEquals(DefaultTopics.topics(AgeGroup.SIX).size - 1, editor.state.enabledSuggestedCount)
        assertEquals(DefaultTopics.topics(AgeGroup.SIX).size - 1, editor.state.enabledTopicCount)
    }

    @Test
    fun saveCallsOnSaveWithDraft() {
        val saved = mutableListOf<ReaderPreferences>()
        val editor = makeEditor(onSave = { saved += it })
        editor.setAgeGroup(AgeGroup.ADULT)
        editor.setEnabled(false, DefaultTopics.topics(AgeGroup.ADULT)[0])
        assertTrue(saved.isEmpty())

        editor.save()
        assertEquals(listOf(editor.draft), saved)
        assertEquals(AgeGroup.ADULT, saved[0].ageGroup)
    }

    @Test
    fun saveCallsOnSaveWithExercisesPerRound() {
        val saved = mutableListOf<ReaderPreferences>()
        val editor = makeEditor(onSave = { saved += it })
        assertEquals(6, editor.draft.exercisesPerRound)
        editor.setExercisesPerRound(9)
        assertTrue(saved.isEmpty())

        editor.save()
        assertEquals(listOf(9), saved.map { it.exercisesPerRound })
    }

    /** Every suggested topic in [AgeGroup.SIX] turned off except the last 3. */
    private val minimumDraft: ReaderPreferences
        get() {
            val ids = DefaultTopics.topics(AgeGroup.SIX).map { it.id }
            return readerPreferencesFixture(AgeGroup.SIX, disabled = mapOf(AgeGroup.SIX to ids.dropLast(3).toSet()))
        }

    @Test
    fun turningOffAtMinimumShowsWarning() {
        val editor = makeEditor(minimumDraft)
        val topic = DefaultTopics.topics(AgeGroup.SIX).last()
        assertFalse(editor.state.isShowingMinimumWarning)

        editor.setEnabled(false, topic)
        assertTrue(editor.state.isEnabled(topic))
        assertTrue(editor.state.isShowingMinimumWarning)
        assertEquals(1, editor.state.minimumWarningCount)

        editor.setEnabled(false, topic)
        assertEquals(2, editor.state.minimumWarningCount)
    }

    @Test
    fun warningHidesAboveMinimum() {
        val editor = makeEditor(minimumDraft)
        val topics = DefaultTopics.topics(AgeGroup.SIX)
        editor.setEnabled(false, topics.last())
        assertTrue(editor.state.isShowingMinimumWarning)

        editor.setEnabled(true, topics.first())
        assertFalse(editor.state.isShowingMinimumWarning)
    }

    @Test
    fun turningOnAtMinimumIsAllowed() {
        val editor = makeEditor(minimumDraft)
        val topic = DefaultTopics.topics(AgeGroup.SIX).first()
        editor.setEnabled(true, topic)
        assertTrue(editor.state.isEnabled(topic))
        assertFalse(editor.state.isShowingMinimumWarning)
    }

    // MARK: - Android only

    /** The editor outlives onboarding (it is kept in the Activity's store), so saving ends its review (D4). */
    @Test
    fun saveCancelsPendingReview() =
        runTest {
            val reviews = StubTopicReviewRepository()
            val editor = makeEditor(reviews = reviews)
            editor.add("rockets")
            runCurrent()
            assertEquals(1, reviews.waitingCount)

            editor.save()
            assertFalse(editor.isReviewing)
            runCurrent()
            assertEquals(1, reviews.cancelledCount)
        }

    /**
     * A repository that answers without suspending must not leave the editor stuck reviewing. Needs inline dispatch,
     * like `Dispatchers.Main.immediate` on a device: with a non-lazy launch the body would finish (and clear
     * `review`) before the job was recorded.
     */
    @Test
    fun synchronousReviewFinishes() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            val reviews = StubTopicReviewRepository(listOf(Result.success(TopicReviewOutcome.Accepted("Rockets"))))
            val editor = makeEditor(reviews = reviews)
            editor.add("rockets")
            runCurrent()
            assertFalse(editor.isReviewing)
            assertEquals(listOf("Rockets"), customTopicNames(editor, AgeGroup.NINE))
        }
}
