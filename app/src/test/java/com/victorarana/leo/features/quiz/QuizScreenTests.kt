package com.victorarana.leo.features.quiz

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.features.root.LeoNavHost
import com.victorarana.leo.features.root.LeoRoute
import com.victorarana.leo.features.root.RootScreen
import com.victorarana.leo.features.root.RootViewModel
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.support.ExerciseRepositoryFactorySpy
import com.victorarana.leo.support.PreferencesRepositorySpy
import com.victorarana.leo.support.RoundHistoryRepositorySpy
import com.victorarana.leo.support.StubExerciseRepository
import com.victorarana.leo.support.StubModelAvailabilityProvider
import com.victorarana.leo.support.StubTopicReviewRepository
import com.victorarana.leo.support.makeQuiz
import com.victorarana.leo.support.testHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Android only: the quiz through the real graph, from Welcome to Results and Review, with a stub repository.
 * Each screen has its own snapshots; these check that the pieces are wired together and that the phase changes
 * don't act twice.
 */
@RunWith(RobolectricTestRunner::class)
class QuizScreenTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val preferences = readerPreferencesFixture(AgeGroup.NINE)
    private val repository = PreferencesRepositorySpy(stored = preferences)
    private val correctOption = "To feel small shakes that could warn of danger"
    private val wrongOption = "To measure how much snow falls"

    private val historyRepository = RoundHistoryRepositorySpy()

    // Wired like `MainActivity`: a finished round goes to the history.
    private fun makeRoot(spy: ExerciseRepositoryFactorySpy): RootViewModel {
        val history = testHistory(historyRepository)
        return RootViewModel(
            repository,
            StubModelAvailabilityProvider(),
            StubTopicReviewRepository(),
            TopicNames.English,
            makeQuiz(spy, onRoundFinished = history::record),
            history,
        ).apply { onIntroPlayed() }
    }

    private fun show(
        spy: ExerciseRepositoryFactorySpy =
            ExerciseRepositoryFactorySpy {
                StubExerciseRepository.ready()
            },
    ): RootViewModel {
        val root = makeRoot(spy)
        composeRule.setContent { LeoTheme { RootScreen(root) } }
        composeRule.waitForIdle()
        return root
    }

    private fun tap(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun tapOption(text: String) {
        composeRule.onNodeWithContentDescription(text).performClick()
        composeRule.waitForIdle()
    }

    /** Reads, picks [option] and moves on with "Next text", from the passage to the next screen. */
    private fun answer(option: String) {
        tap("Answer the question")
        tapOption(option)
        tap("Next text")
    }

    @Test
    fun startShowsTheFirstExercise() {
        show()
        composeRule.onNodeWithText("Six short texts, written for you.").assertIsDisplayed()
        tap("Start")
        composeRule.onNodeWithText("Exercise 1").assertIsDisplayed()
        composeRule.onNodeWithText("Answer the question").assertIsDisplayed()
    }

    @Test
    fun startWhileTheFirstTextIsBeingWrittenShowsLoading() {
        show(ExerciseRepositoryFactorySpy())
        tap("Start")
        // One merged heading node that TalkBack announces again when the topic changes.
        val heading =
            composeRule.onNode(
                SemanticsMatcher("is the loading heading") {
                    it.config
                        .getOrNull(
                            SemanticsProperties.ContentDescription,
                        )?.firstOrNull()
                        ?.startsWith("Writing text 1 of 6, about ") ==
                        true
                },
            )
        heading.assertIsDisplayed()
        heading.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        heading.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun answerTheQuestionShowsTheOptionsAndAPickShowsTheFeedback() {
        show()
        tap("Start")
        tap("Answer the question")
        composeRule.onNodeWithContentDescription(correctOption).assertIsDisplayed()

        tapOption(wrongOption)
        composeRule.onNodeWithText("Not quite").assertIsDisplayed()
        composeRule.onNodeWithText("Next text").assertIsDisplayed()
        // The picked and the right answers carry their state for TalkBack.
        composeRule
            .onNodeWithContentDescription(wrongOption)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Your answer, incorrect"))
        composeRule
            .onNodeWithContentDescription(correctOption)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Correct answer"))

        tap("Next text")
        composeRule.onNodeWithText("Exercise 2").assertIsDisplayed()
    }

    /** A picked option can't be changed: only idle options take taps. */
    @Test
    fun anAnsweredOptionIsNotClickable() {
        show()
        tap("Start")
        tap("Answer the question")
        tapOption(wrongOption)
        composeRule
            .onNodeWithContentDescription(correctOption)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun aFullRoundShowsResultsAndAMissedRowOpensReviewAndBackReturns() {
        show()
        tap("Start")
        repeat(5) { answer(wrongOption) }
        tap("Answer the question")
        tapOption(wrongOption)
        tap("See results")
        composeRule.onNodeWithText("0 of 6").assertIsDisplayed()
        composeRule.onNodeWithText("Practice again").assertIsDisplayed()

        // The missed row is a button whose action is labelled "Review".
        val row = composeRule.onNodeWithContentDescription("Text 2, Exercise 2, missed")
        val label =
            row
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.OnClick)
                ?.label
        assertEquals("Review", label)
        row.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 of 6 missed").assertIsDisplayed()
        composeRule.onNodeWithText("Next missed text").assertIsDisplayed()
        // Headings for TalkBack's heading navigation: the page title and "From the text".
        composeRule
            .onNodeWithText("Exercise 2")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule
            .onNodeWithText("From the text")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        tap("Results")
        composeRule.onNodeWithText("Practice again").assertIsDisplayed()
    }

    /** The round is saved when Results opens, and only then does Results offer History (H6). */
    @Test
    fun resultsShowsHistoryOnceTheRoundIsSaved() {
        show()
        composeRule.onAllNodesWithContentDescription("Past rounds").assertCountEquals(0)
        tap("Start")
        repeat(5) { answer(correctOption) }
        tap("Answer the question")
        tapOption(correctOption)
        tap("See results")
        composeRule.onNodeWithText("6 of 6").assertIsDisplayed()
        assertEquals(1, historyRepository.saved.size)
        composeRule.onNodeWithContentDescription("Past rounds").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 round read · 100% answered right").assertIsDisplayed()
    }

    @Test
    fun practiceAgainStartsANewRound() {
        val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
        show(spy)
        tap("Start")
        repeat(5) { answer(correctOption) }
        tap("Answer the question")
        tapOption(correctOption)
        tap("See results")
        composeRule.onNodeWithText("6 of 6").assertIsDisplayed()

        tap("Practice again")
        composeRule.onNodeWithText("Exercise 1").assertIsDisplayed()
        assertEquals(2, spy.settings.size)
    }

    /** Welcome stays on screen while it fades out: a second tap on its Start must not prepare another round. */
    @Test
    fun aSecondTapOnStartWhileTheScreenFadesOutDoesNothing() {
        val spy = ExerciseRepositoryFactorySpy { StubExerciseRepository.ready() }
        show(spy)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Start").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        // The leaving Welcome is still composed, and its button still answers a tap.
        assertTrue(composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty())
        // By action, not by touch: a touch would land on the new screen's button above it.
        composeRule.onAllNodesWithText("Start")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, spy.settings.size)
        // Welcome is gone: the exercise is showing (its passage or, if the test clock ran its timer out, its question).
        composeRule.onAllNodesWithText("Start").assertCountEquals(0)
    }

    /** Back from another app: the generation stops while the app is stopped and goes on when it returns (Q2). */
    @Test
    fun leavingTheAppPausesGenerationAndReturningResumesIt() {
        val spy = ExerciseRepositoryFactorySpy()
        show(spy)
        assertEquals(1, spy.latest.calls.size)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        assertEquals(1, spy.latest.cancelledCount)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertEquals(2, spy.latest.calls.size)
        // The exercise that was being written is the one that is written again.
        assertEquals(spy.latest.calls[0], spy.latest.calls[1])
    }

    /**
     * A configuration change (rotation, dark mode, font size) stops the old Activity and starts a new one, but it is
     * not leaving the app: the request being written must keep going, not restart.
     */
    @Test
    fun recreatingTheActivityMidGenerationDoesNotCancelIt() {
        val spy = ExerciseRepositoryFactorySpy()
        val root = show(spy)
        assertEquals(1, spy.latest.calls.size)

        composeRule.activityRule.scenario.recreate()
        composeRule.setContent { LeoTheme { RootScreen(root) } }
        composeRule.waitForIdle()

        assertEquals(0, spy.latest.cancelledCount)
        assertEquals(1, spy.latest.calls.size)
        assertEquals(1, spy.settings.size)
    }

    /** A restored Review route with no round behind it (process death) leaves at once instead of showing nothing. */
    @Test
    fun aReviewRouteWithoutMissesPopsAtOnce() {
        val root = makeRoot(ExerciseRepositoryFactorySpy())
        var navController: NavHostController? = null
        composeRule.setContent {
            LeoTheme {
                val controller = rememberNavController()
                navController = controller
                LeoNavHost(navController = controller, root = root)
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { navController!!.navigate(LeoRoute.Review(0)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        assertTrue(
            navController!!
                .currentBackStackEntry
                ?.destination
                ?.route
                ?.contains("Review") != true,
        )
    }
}
