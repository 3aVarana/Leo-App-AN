package com.victorarana.leo.features.quiz

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LocalTalkBackOn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/**
 * Android only (phase-6 D3): the timer is a composable with the countdown inside `repeatOnLifecycle(RESUMED)`, so
 * it is tested with the Compose test clock and a lifecycle of the test's own.
 */
@RunWith(RobolectricTestRunner::class)
class ReadingTimerTests {
    @get:Rule
    val composeRule = createComposeRule()

    private val label = "Time left to read"

    private fun assertTimeLeft(spoken: String) =
        composeRule
            .onNodeWithContentDescription(label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, spoken))

    private class TestOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    @Test
    fun countsDownOneSecondAtATime() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { LeoTheme { ReadingTimer(total = 30.seconds, onTimeUp = {}) } }
        composeRule.mainClock.advanceTimeByFrame()
        assertTimeLeft("30 seconds")
        composeRule.mainClock.advanceTimeBy(1_000)
        assertTimeLeft("29 seconds")
        composeRule.mainClock.advanceTimeBy(2_000)
        assertTimeLeft("27 seconds")
    }

    @Test
    fun spokenValueUsesMinutesAndSecondsAndOmitsZeroUnits() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { LeoTheme { ReadingTimer(total = 90.seconds, onTimeUp = {}) } }
        composeRule.mainClock.advanceTimeByFrame()
        assertTimeLeft("1 minute, 30 seconds")
        composeRule.mainClock.advanceTimeBy(30_000)
        assertTimeLeft("1 minute")
    }

    @Test
    fun callsTimeUpOnceAtZero() {
        var timeUps = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { LeoTheme { ReadingTimer(total = 3.seconds, onTimeUp = { timeUps++ }) } }
        composeRule.mainClock.advanceTimeBy(2_900)
        assertEquals(0, timeUps)
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, timeUps)
        composeRule.mainClock.advanceTimeBy(10_000)
        assertEquals(1, timeUps)
        assertTimeLeft("0 seconds")
    }

    /** Below RESUMED nothing counts, and the partial second is lost, like `scenePhase` on iOS. */
    @Test
    fun pausesWhileTheScreenIsNotResumed() {
        val owner = TestOwner()
        composeRule.runOnUiThread { owner.moveTo(Lifecycle.State.RESUMED) }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LeoTheme { ReadingTimer(total = 30.seconds, onTimeUp = {}) }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        assertTimeLeft("28 seconds")

        composeRule.runOnUiThread { owner.moveTo(Lifecycle.State.STARTED) }
        composeRule.mainClock.advanceTimeBy(20_000)
        assertTimeLeft("28 seconds")

        composeRule.runOnUiThread { owner.moveTo(Lifecycle.State.RESUMED) }
        composeRule.mainClock.advanceTimeBy(500)
        // The half second before the pause was lost, so the next tick comes a full second after resuming.
        assertTimeLeft("28 seconds")
        composeRule.mainClock.advanceTimeBy(600)
        assertTimeLeft("27 seconds")
    }

    @Test
    fun keepsTheRemainderAcrossARecreation() {
        val tester = StateRestorationTester(composeRule)
        composeRule.mainClock.autoAdvance = false
        tester.setContent { LeoTheme { ReadingTimer(total = 30.seconds, onTimeUp = {}) } }
        composeRule.mainClock.advanceTimeBy(5_000)
        assertTimeLeft("25 seconds")

        tester.emulateSavedInstanceStateRestore()
        composeRule.mainClock.advanceTimeByFrame()
        assertTimeLeft("25 seconds")
    }

    /** `QuizScreen` wraps each exercise in `key(exercise.id)`; that is what gives every exercise a full timer. */
    @Test
    fun aNewExerciseStartsFull() {
        val exercise = exerciseFixture()
        composeRule.mainClock.autoAdvance = false
        var exerciseId by mutableStateOf(exercise.id)
        composeRule.setContent { LeoTheme { key(exerciseId) { ReadingTimer(total = 30.seconds, onTimeUp = {}) } } }
        composeRule.mainClock.advanceTimeBy(5_000)
        assertTimeLeft("25 seconds")
        exerciseId = exerciseFixture().id
        composeRule.mainClock.advanceTimeByFrame()
        assertTimeLeft("30 seconds")
    }

    // MARK: - On the exercise screen: the timer is absent when it is off in Settings or TalkBack is on

    private fun showExercise(
        isReadingTimed: Boolean,
        talkBackOn: Boolean,
    ) = showExercise(isReadingTimed, mutableStateOf(talkBackOn))

    private fun showExercise(
        isReadingTimed: Boolean,
        talkBackOn: State<Boolean>,
    ) {
        val exercise = exerciseFixture()
        val state =
            QuizUiState(
                phase = QuizViewModel.Phase.Answering,
                currentExercise = exercise,
                isReading = true,
                roundAgeGroup = AgeGroup.NINE,
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalTalkBackOn provides talkBackOn.value) {
                LeoTheme {
                    ExerciseScreen(
                        state = state,
                        exercise = exercise,
                        isReadingTimed = isReadingTimed,
                        onFinishReading = {},
                        onSelect = {},
                        onNext = {},
                    )
                }
            }
        }
    }

    @Test
    fun timerShowsWhenTimedAndTalkBackIsOff() {
        showExercise(isReadingTimed = true, talkBackOn = false)
        composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }

    @Test
    fun timerIsAbsentWhenTheSettingIsOff() {
        showExercise(isReadingTimed = false, talkBackOn = false)
        composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
    }

    @Test
    fun timerIsAbsentWithTalkBackOn() {
        showExercise(isReadingTimed = true, talkBackOn = true)
        composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
    }

    /** Switching TalkBack on and off mid-passage hides and shows the timer without restarting it. */
    @Test
    fun timerKeepsItsTimeAcrossATalkBackToggle() {
        val talkBackOn = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        showExercise(isReadingTimed = true, talkBackOn = talkBackOn)
        composeRule.mainClock.advanceTimeBy(5_000)
        val afterFiveSeconds =
            composeRule
                .onNodeWithContentDescription(label)
                .fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription]

        // With the clock paused nothing else flushes snapshot writes, so each toggle is applied as a snapshot.
        Snapshot.withMutableSnapshot { talkBackOn.value = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(10_000)

        Snapshot.withMutableSnapshot { talkBackOn.value = false }
        composeRule.mainClock.advanceTimeByFrame()
        assertTimeLeft(afterFiveSeconds)
    }
}
