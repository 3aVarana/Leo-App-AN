package com.victorarana.leo.features.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.features.root.LeoNavHost
import com.victorarana.leo.features.root.LeoRoute
import com.victorarana.leo.features.root.RootScreen
import com.victorarana.leo.features.root.RootViewModel
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.support.PreferencesRepositorySpy
import com.victorarana.leo.support.RoundHistoryRepositorySpy
import com.victorarana.leo.support.StubModelAvailabilityProvider
import com.victorarana.leo.support.StubTopicReviewRepository
import com.victorarana.leo.support.testHistory
import com.victorarana.leo.support.testQuiz
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

/**
 * Android only: History and the round page through the real graph, with an in-memory store. The screens have their own
 * snapshots; these check the wiring (what a tap, a swipe or a TalkBack action does) and the restored routes.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryScreenTests {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = RoundHistoryRepositorySpy()
    private val preferences = PreferencesRepositorySpy(stored = readerPreferencesFixture(AgeGroup.NINE))

    // Mid-month, so the month doesn't depend on the zone the test runs in.
    private val finishedAt = Instant.parse("2001-01-15T12:00:00Z")
    private val twoMisses =
        roundRecordFixture(AgeGroup.NINE, finishedAt, results = listOf(true, false, true, true, false, true))
    private val perfect = roundRecordFixture(AgeGroup.NINE, finishedAt, results = List(6) { true })

    private fun makeRoot() =
        RootViewModel(
            preferences,
            StubModelAvailabilityProvider(),
            StubTopicReviewRepository(),
            TopicNames.English,
            testQuiz(),
            testHistory(repository),
        ).apply { onIntroPlayed() }

    private fun show(vararg saved: com.victorarana.leo.domain.RoundRecord): RootViewModel {
        runBlocking { saved.forEach { repository.save(it) } }
        val root = makeRoot()
        composeRule.setContent { LeoTheme { RootScreen(root) } }
        composeRule.waitForIdle()
        return root
    }

    private fun openHistory() {
        composeRule.onNodeWithContentDescription("Past rounds").performClick()
        composeRule.waitForIdle()
    }

    private fun rows() =
        composeRule.onAllNodes(
            SemanticsMatcher("a history row") {
                it.config
                    .getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
                    ?.endsWith("of 6 right") ==
                    true
            },
        )

    private fun tap(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun tappingARowOpensTheRoundAndBackReturns() {
        show(twoMisses)
        openHistory()
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()
        composeRule.onNodeWithText("1 round read · 67% answered right").assertIsDisplayed()

        rows()[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4 of 6").assertIsDisplayed()
        composeRule.onNodeWithText("Review 2 missed").assertIsDisplayed()

        tap("Back")
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()
        tap("Back")
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun aRowIsAButtonThatShowsTheRound() {
        show(twoMisses)
        openHistory()
        val row = rows()[0]
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val label =
            row
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.OnClick)
                ?.label
        assertEquals("Show round", label)
        val description =
            row
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .first()
        assertTrue(description, description.endsWith(", 4 of 6 right"))
    }

    @Test
    fun theHeaderAndTheSectionTitlesAreHeadings() {
        show(twoMisses)
        openHistory()
        composeRule
            .onNodeWithText("Your rounds")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        // 2001 is long ago: the section is its month and year, drawn in capitals but read as written.
        composeRule
            .onNode(
                SemanticsMatcher("a section title") {
                    it.config.getOrNull(SemanticsProperties.Heading) != null &&
                        it.config
                            .getOrNull(SemanticsProperties.Text)
                            ?.firstOrNull()
                            ?.text == "January 2001"
                },
            ).assertExists()
    }

    /** Q3: like a plain iOS `List`, the section's title stays at the top while its rounds scroll under it. */
    @Test
    fun theSectionTitleStaysPinnedWhileItsRoundsScroll() {
        val rounds = List(15) { roundRecordFixture(AgeGroup.NINE, finishedAt.minusSeconds(it * 3600L)) }
        show(*rounds.toTypedArray())
        openHistory()
        composeRule.onNodeWithText("January 2001").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(15)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rounds").assertIsNotDisplayed()
        composeRule.onNodeWithText("January 2001").assertIsDisplayed()
    }

    @Test
    fun aMissedRowOpensReviewAtThatMissAndBackToRoundReturns() {
        show(twoMisses)
        openHistory()
        rows()[0].performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithContentDescription(
                "Text 5, A Walk Through the Rainforest Canopy at Dawn, Volcanoes · Key detail, missed",
            ).performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        // The fifth text is the second miss, and the last: its button leads back to the round.
        composeRule.onNodeWithText("2 of 2 missed").assertIsDisplayed()
        composeRule.onNodeWithText("Back to round").assertIsDisplayed()
        // The arrow's label is the screen below's title, which has none: "Back" (Q2).
        composeRule.onNodeWithText("Back").assertIsDisplayed()

        tap("Back to round")
        // Still scrolled to the fifth text on this small screen: the score is above the fold.
        composeRule.onNodeWithText("4 of 6").assertExists()
        composeRule.onNodeWithText("Review 2 missed").assertIsDisplayed()
    }

    @Test
    fun reviewNMissedOpensTheFirstMiss() {
        show(twoMisses)
        openHistory()
        rows()[0].performClick()
        composeRule.waitForIdle()
        tap("Review 2 missed")
        composeRule.onNodeWithText("1 of 2 missed").assertIsDisplayed()
        composeRule.onNodeWithText("Next missed text").assertIsDisplayed()
    }

    @Test
    fun aRoundWithoutMissesHasNoReviewButton() {
        show(perfect)
        openHistory()
        rows()[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("6 of 6").assertIsDisplayed()
        composeRule.onAllNodesWithText("Review 0 missed").assertCountEquals(0)
    }

    @Test
    fun swipingARowFromTheEndDeletesIt() {
        val older = roundRecordFixture(AgeGroup.NINE, finishedAt = twoMisses.finishedAt.minusSeconds(86_400))
        show(twoMisses, older)
        openHistory()
        composeRule.onNodeWithText("2 rounds read · 67% answered right").assertIsDisplayed()

        rows()[0].performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 round read · 67% answered right").assertIsDisplayed()
        assertEquals(listOf(twoMisses.id), repository.deleted)
    }

    /** Only the end-to-start swipe deletes. */
    @Test
    fun swipingFromTheStartDoesNothing() {
        show(twoMisses)
        openHistory()
        rows()[0].performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(emptyList<Any>(), repository.deleted)
        rows().assertCountEquals(1)
    }

    /** TalkBack can't swipe a row: it offers "Delete" as a custom action instead. */
    @Test
    fun theTalkBackDeleteActionDeletesTheRound() {
        val older = roundRecordFixture(AgeGroup.NINE, finishedAt = twoMisses.finishedAt.minusSeconds(86_400))
        show(twoMisses, older)
        openHistory()

        val actions =
            rows()[0]
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
        assertEquals(listOf("Delete"), actions?.map { it.label })
        composeRule.runOnUiThread { actions!!.single().action() }
        composeRule.waitForIdle()
        rows().assertCountEquals(1)
        assertEquals(listOf(twoMisses.id), repository.deleted)
    }

    @Test
    fun deletingTheLastRoundReturnsToHomeAndHidesTheButton() {
        show(twoMisses)
        openHistory()
        rows()[0].performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Past rounds").assertCountEquals(0)
    }

    /**
     * The store answers after the swipe, like Room's IO hop, and then fails: the row comes back at rest, and one
     * delete was tried (a row restored in its swiped state would delete itself again, forever).
     */
    @Test
    fun aFailedDeleteBringsTheRowBackAtRestWithoutTryingAgain() {
        val older = roundRecordFixture(AgeGroup.NINE, finishedAt.minusSeconds(86_400))
        show(twoMisses, older)
        openHistory()
        val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }
        repository.deleteError = IllegalStateException("locked")

        rows()[0].performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        rows().assertCountEquals(1)

        gate.complete(Unit)
        composeRule.waitForIdle()
        rows().assertCountEquals(2)
        rows()[0].assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsNotDisplayed()
        assertEquals(1, repository.deleteCalls)
        assertEquals(emptyList<Any>(), repository.deleted)
    }

    /** The last round: History stays until the store says the list is empty, and stays if the delete fails. */
    @Test
    fun deletingTheLastRoundWaitsForTheStoreAndStaysIfItFails() {
        show(twoMisses)
        openHistory()
        val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }
        repository.deleteError = IllegalStateException("locked")

        rows()[0].performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()

        gate.complete(Unit)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()
        rows().assertCountEquals(1)
        assertEquals(1, repository.deleteCalls)
    }

    @Test
    fun deletingTheLastRoundClosesHistoryOnceTheStoreHasDeletedIt() {
        show(twoMisses)
        openHistory()
        val gate = CompletableDeferred<Unit>().also { repository.deleteGate = it }

        rows()[0].performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()

        gate.complete(Unit)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Past rounds").assertCountEquals(0)
    }

    /** The round went missing between the list and the tap: nothing opens. */
    @Test
    fun aRoundDeletedBeforeItLoadsIsNotOpened() {
        show(twoMisses)
        openHistory()
        runBlocking { repository.delete(twoMisses.id) }
        rows()[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()
        composeRule.onAllNodesWithText("Review 2 missed").assertCountEquals(0)
    }

    @Test
    fun historyHasNoGearBecauseTheAgeGroupCannotChangeThere() {
        show(twoMisses)
        openHistory()
        composeRule.onAllNodesWithContentDescription("Settings").assertCountEquals(0)
    }

    // MARK: - Restored routes (process death)

    private fun showGraph(vararg saved: com.victorarana.leo.domain.RoundRecord): NavHostController {
        runBlocking { saved.forEach { repository.save(it) } }
        val root = makeRoot()
        var navController: NavHostController? = null
        composeRule.setContent {
            LeoTheme {
                val controller = rememberNavController()
                navController = controller
                LeoNavHost(navController = controller, root = root)
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(navController)
    }

    /** History restored with Home not composed under it still gets its age group, and loads its list. */
    @Test
    fun aRestoredHistoryRouteLoadsItsRounds() {
        val navController = showGraph(twoMisses)
        composeRule.runOnUiThread { navController.navigate(LeoRoute.History) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rounds").assertIsDisplayed()
        rows().assertCountEquals(1)
    }

    /** A restored History with nothing to show leaves instead of drawing an empty list. */
    @Test
    fun aRestoredHistoryRouteWithNoRoundsPops() {
        val navController = showGraph()
        composeRule.runOnUiThread { navController.navigate(LeoRoute.History) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        assertTrue(
            navController.currentBackStackEntry
                ?.destination
                ?.route
                ?.contains("History") != true,
        )
    }

    @Test
    fun aRestoredRoundRouteLoadsTheRoundById() {
        val navController = showGraph(twoMisses)
        composeRule.runOnUiThread { navController.navigate(LeoRoute.HistoryRound(twoMisses.id.toString())) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4 of 6").assertIsDisplayed()
    }

    @Test
    fun aRestoredRoundRouteForAMissingRoundPops() {
        val navController = showGraph(twoMisses)
        composeRule.runOnUiThread { navController.navigate(LeoRoute.HistoryRound(UUID.randomUUID().toString())) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun aRoundRouteWithAnInvalidIdPops() {
        val navController = showGraph(twoMisses)
        composeRule.runOnUiThread { navController.navigate(LeoRoute.HistoryRound("not-a-uuid")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun aRestoredReviewRouteLoadsTheRoundById() {
        val navController = showGraph(twoMisses)
        composeRule.runOnUiThread { navController.navigate(LeoRoute.HistoryReview(twoMisses.id.toString(), 1)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 of 2 missed").assertIsDisplayed()
        composeRule.onNodeWithText("Back to round").assertIsDisplayed()
    }

    @Test
    fun aReviewRouteForARoundWithoutMissesPops() {
        val navController = showGraph(perfect)
        composeRule.runOnUiThread { navController.navigate(LeoRoute.HistoryReview(perfect.id.toString(), 0)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        assertNotNull(navController.currentBackStackEntry)
    }
}
