package com.victorarana.leo.features.root

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.support.PreferencesRepositorySpy
import com.victorarana.leo.support.StubModelAvailabilityProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTests {
    private val preferences = readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.NINE to listOf("Chess")))
    private val repository = PreferencesRepositorySpy()
    private val availability = StubModelAvailabilityProvider()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.makeRoot(): RootViewModel =
        RootViewModel(repository, availability).also { advanceUntilIdle() }

    private val RootViewModel.preferencesState get() = uiState.value.preferences

    @Test
    fun loadsPreferencesAtInit() =
        runTest {
            assertEquals(PreferencesState.NotOnboarded, makeRoot().preferencesState)
            repository.stored = preferences
            assertEquals(PreferencesState.Ready(preferences), makeRoot().preferencesState)
        }

    /** Android only: loading is asynchronous, so the first state is neither onboarding nor the app. */
    @Test
    fun loadingStateBeforePreferencesLoad() =
        runTest {
            repository.stored = preferences
            val root = RootViewModel(repository, availability)
            assertEquals(PreferencesState.Loading, root.preferencesState)
            advanceUntilIdle()
            assertEquals(PreferencesState.Ready(preferences), root.preferencesState)
        }

    @Test
    fun saveSetsAndPersists() =
        runTest {
            val root = makeRoot()
            root.save(preferences)
            advanceUntilIdle()
            assertEquals(PreferencesState.Ready(preferences), root.preferencesState)
            assertEquals(listOf(preferences), repository.saved)
        }

    @Test
    fun savingEqualPreferencesDoesNotPersist() =
        runTest {
            repository.stored = preferences
            val root = makeRoot()
            root.save(preferences)
            advanceUntilIdle()
            assertEquals(emptyList<Any>(), repository.saved)
            assertEquals(PreferencesState.Ready(preferences), root.preferencesState)
        }

    @Test
    fun availabilityFollowsProvider() =
        runTest {
            val root = makeRoot()
            assertEquals(ModelAvailability.Available, root.uiState.value.availability)
            val unavailable = ModelAvailability.Unavailable(ModelUnavailableReason.OTHER)
            availability.set(unavailable)
            advanceUntilIdle()
            assertEquals(unavailable, root.uiState.value.availability)
        }

    /** Android only: the model may need to be downloaded, so the container prepares it as the app starts. */
    @Test
    fun preparesAvailabilityAtInit() =
        runTest {
            makeRoot()
            assertEquals(1, availability.refreshCount)
            assertEquals(0, availability.downloadCount)
        }

    @Test
    fun downloadsAtInitWhenTheModelIsDownloadable() =
        runTest {
            availability.set(ModelAvailability.Downloadable)
            makeRoot()
            assertEquals(1, availability.downloadCount)
        }

    /** The provider starts at "downloading": that placeholder must not reach the screen before AICore answers. */
    @Test
    fun availabilityIsNotCheckedUntilTheFirstRefreshReturns() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val root =
                RootViewModel(repository, StubModelAvailabilityProvider(ModelAvailability.Downloading(null), gate))
            // Not advanceUntilIdle: that would also run the ceiling's virtual clock out.
            runCurrent()
            assertEquals(false, root.uiState.value.availabilityChecked)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(true, root.uiState.value.availabilityChecked)
        }

    /** A stuck AICore is given up on after the ceiling, so the launch frame doesn't hold forever. */
    @Test
    fun availabilityCheckGivesUpAfterTheCeiling() =
        runTest {
            val root =
                RootViewModel(
                    repository,
                    StubModelAvailabilityProvider(ModelAvailability.Downloading(null), CompletableDeferred()),
                )
            advanceTimeBy(RootViewModel.AVAILABILITY_CHECK_CEILING_MILLIS - 1)
            assertEquals(false, root.uiState.value.availabilityChecked)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(true, root.uiState.value.availabilityChecked)
        }

    /** Giving up the wait must not act on the placeholder: no download until AICore has answered. */
    @Test
    fun noDownloadStartsBeforeASlowCheckAnswers() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val provider =
                StubModelAvailabilityProvider(ModelAvailability.Downloading(null), gate, ModelAvailability.Downloadable)
            val root = RootViewModel(repository, provider)
            advanceTimeBy(RootViewModel.AVAILABILITY_CHECK_CEILING_MILLIS + 1)
            runCurrent()
            assertEquals(true, root.uiState.value.availabilityChecked)
            assertEquals(0, provider.downloadCount)
            gate.complete(Unit)
            runCurrent()
            assertEquals(1, provider.downloadCount)
        }

    @Test
    fun notEligibleAfterASlowCheckShowsNotEligibleWithoutDownloading() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val notEligible = ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE)
            val provider = StubModelAvailabilityProvider(ModelAvailability.Downloading(null), gate, notEligible)
            val root = RootViewModel(repository, provider)
            advanceTimeBy(RootViewModel.AVAILABILITY_CHECK_CEILING_MILLIS + 1)
            gate.complete(Unit)
            runCurrent()
            assertEquals(notEligible, root.uiState.value.availability)
            assertEquals(0, provider.downloadCount)
        }

    @Test
    fun availableAfterASlowCheckNeverDownloads() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val provider =
                StubModelAvailabilityProvider(ModelAvailability.Downloading(null), gate, ModelAvailability.Available)
            val root = RootViewModel(repository, provider)
            advanceTimeBy(RootViewModel.AVAILABILITY_CHECK_CEILING_MILLIS + 1)
            gate.complete(Unit)
            runCurrent()
            assertEquals(ModelAvailability.Available, root.uiState.value.availability)
            assertEquals(0, provider.downloadCount)
        }

    @Test
    fun refreshesAvailabilityOnResume() =
        runTest {
            val root = makeRoot()
            root.onResume()
            advanceUntilIdle()
            assertEquals(2, availability.refreshCount)
        }

    /** A model that became downloadable while the app was away (a failed download, say) is downloaded on resume. */
    @Test
    fun downloadsOnResumeWhenTheModelBecameDownloadable() =
        runTest {
            availability.set(ModelAvailability.Unavailable(ModelUnavailableReason.DOWNLOAD_FAILED))
            val root = makeRoot()
            assertEquals(0, availability.downloadCount)

            availability.set(ModelAvailability.Downloadable)
            root.onResume()
            advanceUntilIdle()
            assertEquals(1, availability.downloadCount)
        }

    @Test
    fun resumeDoesNotDownloadAnAvailableModel() =
        runTest {
            val root = makeRoot()
            root.onResume()
            advanceUntilIdle()
            assertEquals(0, availability.downloadCount)
        }

    /** Android only: two quick saves of the same preferences persist once (iOS saves synchronously). */
    @Test
    fun twoQuickEqualSavesPersistOnce() =
        runTest {
            val root = makeRoot()
            root.save(preferences)
            root.save(preferences)
            advanceUntilIdle()
            assertEquals(listOf(preferences), repository.saved)
        }

    /** The first ON_RESUME comes right after launch: it must not ask AICore a second time. */
    @Test
    fun resumeDuringTheLaunchCheckStartsNoSecondCheck() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slow = StubModelAvailabilityProvider(refreshGate = gate)
            val root = RootViewModel(repository, slow)
            root.onResume()
            runCurrent()
            assertEquals(1, slow.refreshCount)

            gate.complete(Unit)
            advanceUntilIdle()
            root.onResume()
            advanceUntilIdle()
            assertEquals(2, slow.refreshCount)
        }

    /** A resume while the previous resume waits on a running download starts no second check. */
    @Test
    fun resumeWhileTheLastResumeWaitsOnADownloadStartsNoSecondCheck() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val provider = StubModelAvailabilityProvider(downloadGate = gate)
            val root = RootViewModel(repository, provider)
            advanceUntilIdle()

            provider.set(ModelAvailability.Downloadable)
            root.onResume()
            runCurrent()
            assertEquals(2, provider.refreshCount)
            assertEquals(1, provider.downloadCount)

            root.onResume()
            runCurrent()
            assertEquals(2, provider.refreshCount)

            gate.complete(Unit)
            advanceUntilIdle()
            root.onResume()
            advanceUntilIdle()
            assertEquals(3, provider.refreshCount)
        }

    /** A launch check that never returns stops resume checks only until the ceiling. */
    @Test
    fun resumeAfterTheCeilingAsksAgainWhenTheLaunchCheckHangs() =
        runTest {
            val slow = StubModelAvailabilityProvider(refreshGate = CompletableDeferred())
            val root = RootViewModel(repository, slow)
            root.onResume()
            runCurrent()
            assertEquals(1, slow.refreshCount)

            advanceTimeBy(RootViewModel.AVAILABILITY_CHECK_CEILING_MILLIS + 1)
            root.onResume()
            runCurrent()
            assertEquals(2, slow.refreshCount)
        }

    /** A save that lands while preferences are still loading isn't overwritten by the load. */
    @Test
    fun saveDuringTheLoadIsNotOverwritten() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slowDisk = PreferencesRepositorySpy(loadGate = gate)
            val root = RootViewModel(slowDisk, availability)
            runCurrent()
            val newer = readerPreferencesFixture(AgeGroup.FIFTEEN)
            root.save(newer)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(PreferencesState.Ready(newer), root.preferencesState)
        }

    /** Past the ceiling the screen may show the placeholder, but it isn't an answer until AICore gives one. */
    @Test
    fun availabilityIsAnsweredOnlyWhenTheCheckReturns() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slow = StubModelAvailabilityProvider(refreshGate = gate)
            val root = RootViewModel(repository, slow)
            advanceTimeBy(RootViewModel.AVAILABILITY_CHECK_CEILING_MILLIS + 1)
            assertEquals(true, root.uiState.value.availabilityChecked)
            assertEquals(false, root.uiState.value.availabilityAnswered)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(true, root.uiState.value.availabilityAnswered)
        }

    @Test
    fun onboardingDraftIsFifteen() {
        assertEquals(AgeGroup.FIFTEEN, RootViewModel.onboardingDraft.ageGroup)
    }
}
