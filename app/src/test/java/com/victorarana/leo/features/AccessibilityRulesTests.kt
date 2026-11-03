package com.victorarana.leo.features

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.domain.roundAnswers
import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.history.HistoryScreen
import com.victorarana.leo.features.history.RoundScreen
import com.victorarana.leo.features.preferences.OnboardingTopics
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.features.quiz.ExerciseScreen
import com.victorarana.leo.features.quiz.GenerationFailedScreen
import com.victorarana.leo.features.quiz.LoadingScreen
import com.victorarana.leo.features.quiz.ResultScreen
import com.victorarana.leo.features.quiz.ReviewScreen
import com.victorarana.leo.features.quiz.WelcomeScreen
import com.victorarana.leo.features.root.UnavailableScreen
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.support.OnboardingScreenHost
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.exerciseQuizState
import com.victorarana.leo.support.preferencesEditorFixture
import com.victorarana.leo.support.threeSectionHistoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/**
 * Android only (phase 8 Q7, hand-written instead of the Accessibility Test Framework checks, which are not an approved
 * dependency): the rules of iOS `CLAUDE.md` that a screen's semantics can show without a screen reader. On every
 * screen, with the fixtures of the snapshots:
 *  - every control that can be clicked has a label (text or `contentDescription`), so TalkBack never says only
 *    "button";
 *  - every control is at least 48dp by 48dp to touch (the icon-only gear and History buttons, the Back bar, the
 *    stepper's controls, the chips, the age segments);
 *  - the screen's title is a heading, for the headings menu.
 * What TalkBack actually says is a device check, in the QA list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class AccessibilityRulesTests {
    @get:Rule
    val composeRule = createComposeRule()

    /** A screen, and the headings it declares (each one's spoken text). */
    private class Screen(
        val name: String,
        val headings: List<String>,
        val content: @Composable () -> Unit,
    )

    private val exercise = exerciseFixture()
    private val topics = listOf("Volcanoes", "The solar system", "Inventors")

    private fun dots(
        index: Int,
        current: DotState,
    ) = List(6) {
        when {
            it < index -> DotState.Done
            it == index -> current
            else -> DotState.Upcoming
        }
    }

    private val screens =
        listOf(
            Screen("Welcome", listOf("Leo", "Six short texts, written for you.")) {
                WelcomeScreen(AgeGroup.NINE, 6, topics, onStart = {}, onSettings = {}, onHistory = {})
            },
            Screen("Loading", listOf("Writing text 2 of 6, about Volcanoes")) {
                LoadingScreen(
                    index = 1,
                    dots = dots(1, DotState.Current),
                    topicName = "Volcanoes",
                    ageGroup = AgeGroup.NINE,
                )
            },
            Screen("Reading", listOf("The Sleeping Mountain")) {
                ExerciseScreen(exerciseQuizState(exercise, isReading = true), exercise, true, {}, {}, {})
            },
            Screen("Question", listOf("Why do scientists place sensors on the volcano?")) {
                ExerciseScreen(exerciseQuizState(exercise), exercise, true, {}, {}, {})
            },
            Screen(
                "Answered",
                listOf("Why do scientists place sensors on the volcano?", "Not quite", "The Sleeping Mountain"),
            ) {
                ExerciseScreen(exerciseQuizState(exercise, selected = 0), exercise, true, {}, {}, {})
            },
            Screen("GenerationFailed", listOf("We couldn't write this one.")) {
                GenerationFailedScreen(1, dots(1, DotState.Failed), "Volcanoes", onRetry = {}, onTryDifferentTopic = {})
            },
            Screen("Result", listOf("3 of 6")) {
                ResultScreen(
                    answers = roundAnswers(listOf(true, false, true, false, false, true)),
                    ageGroup = AgeGroup.NINE,
                    onRestart = {},
                    onSettings = {},
                    onReview = {},
                    onHistory = {},
                )
            },
            Screen("Review", listOf("Pirates of the Silver Bay", "From the text")) {
                ReviewScreen(
                    roundAnswers(listOf(true, false, true, false, false, true)).filterNot { it.isCorrect },
                    0,
                    {},
                )
            },
            Screen("Round", listOf("4 of 6")) {
                RoundScreen(roundRecordFixture(AgeGroup.NINE), onReview = {}, onBack = {}, zone = ZoneOffset.UTC)
            },
            Screen("History", listOf("Your rounds", "This week", "Last week", "February")) {
                HistoryScreen(threeSectionHistoryState(), onOpen = {}, onDelete = {}, onBack = {})
            },
            Screen(
                "Settings",
                listOf("Settings", "Age group", "Round", "Reading", "Suggested · 15 on", "Your topics"),
            ) {
                SettingsScreen(preferencesEditorFixture(ReaderPreferences(AgeGroup.TWELVE)), onCancel = {}, onDone = {})
            },
            Screen("OnboardingAge", listOf("How old are you?")) {
                OnboardingScreenHost(preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE)))
            },
            Screen(
                "OnboardingTopics",
                listOf("What do you like reading about?", "Suggested for 9 to 11", "Your topics", "Round"),
            ) {
                OnboardingTopics(preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE)))
            },
            Screen("UnavailableNotEligible", listOf("Gemini Nano needed")) {
                UnavailableScreen(ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE))
            },
            Screen("UnavailableDownloading", listOf("Getting Leo ready")) {
                UnavailableScreen(ModelAvailability.Downloading(fraction = 0.42f))
            },
        )

    private var current by mutableStateOf(screens.first())

    private fun forEachScreen(check: (Screen) -> Unit) {
        composeRule.setContent {
            LeoTheme { key(current) { current.content() } }
        }
        for (screen in screens) {
            current = screen
            composeRule.waitForIdle()
            check(screen)
        }
    }

    private fun SemanticsNode.spoken(): String =
        (
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)
        ).joinToString(" ").trim()

    /**
     * What TalkBack can focus, merged as it merges. The unmerged tree also shows the pieces inside a merged
     * control.
     */
    private fun clickables(unmerged: Boolean = false) =
        composeRule
            .onAllNodes(hasClickAction(), useUnmergedTree = unmerged)
            .fetchSemanticsNodes()

    @Test
    fun everyControlHasALabel() =
        forEachScreen { screen ->
            val unlabeled = clickables().filter { it.spoken().isEmpty() }.map { it.config.toString() }
            assertTrue("${screen.name}: clickable without a label: $unlabeled", unlabeled.isEmpty())
        }

    @Test
    fun everyControlIsAtLeast48dpToTouch() =
        forEachScreen { screen ->
            val density =
                RuntimeEnvironment
                    .getApplication()
                    .resources.displayMetrics.density
            val minimum = 48.dp.value * density
            val small =
                clickables(unmerged = true)
                    .filter { it.touchBoundsInRoot.width < minimum - 1 || it.touchBoundsInRoot.height < minimum - 1 }
                    .map {
                        "${it.spoken()} ${it.touchBoundsInRoot.size / density}"
                    }
            assertTrue("${screen.name}: smaller than 48dp: $small", small.isEmpty())
        }

    @Test
    fun theTitleIsAHeading() =
        forEachScreen { screen ->
            val headings =
                composeRule
                    .onAllNodes(isHeading())
                    .fetchSemanticsNodes()
                    .map { it.spoken() }
            assertEquals(screen.name, screen.headings, headings)
        }

    /** The stepper is one adjustable node: a name, the count as its value and a way to set it. */
    @Test
    fun adjustableControlsHaveANameAndAValue() {
        val adjustable = mutableListOf<SemanticsNode>()
        forEachScreen {
            adjustable +=
                composeRule
                    .onAllNodes(
                        androidx.compose.ui.test.SemanticsMatcher
                            .keyIsDefined(SemanticsActions.SetProgress),
                    ).fetchSemanticsNodes()
        }
        // Settings and the topics screen of onboarding each have the stepper.
        assertEquals(2, adjustable.size)
        for (node in adjustable) {
            assertTrue(node.spoken().isNotEmpty())
            assertTrue(node.config.getOrNull(SemanticsProperties.StateDescription) != null)
        }
    }

    /** A guard for the checks above: the screens that have controls must show them, or the rules pass on nothing. */
    @Test
    fun theControlsAreFoundOnTheScreens() {
        val found = mutableMapOf<String, List<String>>()
        forEachScreen { found[it.name] = clickables().map { node -> node.spoken() } }
        assertTrue(found.getValue("Welcome").containsAll(listOf("Settings", "Start")))
        assertTrue("$found", found.getValue("History").any { it.contains("Back") })
        assertTrue("$found", found.getValue("Result").any { it.contains("Practice again") })
        assertTrue("$found", found.getValue("Settings").any { it.contains("Space exploration") })
        // The age control measures its segments once and shows them once: each option is in the tree once.
        assertEquals(1, found.getValue("Settings").count { it == "6 to 8" })
        assertTrue("$found", found.getValue("OnboardingAge").any { it.contains("12 to 14") || it.contains("9 to 11") })
    }

    /** A node that says it is a button, a radio button, a switch... must be one TalkBack can activate. */
    @Test
    fun everyNodeWithAnInteractiveRoleCanBeActivated() =
        forEachScreen { screen ->
            val interactive =
                setOf(Role.Button, Role.RadioButton, Role.Switch, Role.Checkbox, Role.DropdownList, Role.Tab)
            val withRole =
                composeRule
                    .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .filter { it.config.getOrNull(SemanticsProperties.Role) in interactive }
            val withoutAction = withRole.filter { it.config.getOrNull(SemanticsActions.OnClick) == null }
            assertTrue(
                "${screen.name}: a role without an action: ${withoutAction.map { it.spoken() }}",
                withoutAction.isEmpty(),
            )
        }

    /** Every extra action (History's "Delete", a chip's "Remove") has a name TalkBack can read out. */
    @Test
    fun everyCustomActionHasALabel() {
        var found = 0
        forEachScreen { screen ->
            val actions =
                composeRule
                    .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .flatMap { it.config.getOrNull(SemanticsActions.CustomActions).orEmpty() }
            found += actions.size
            val unlabeled = actions.filter { it.label.isBlank() }
            assertTrue("${screen.name}: a custom action without a label", unlabeled.isEmpty())
        }
        // History's rows are in the screen list, so the rule must have seen their "Delete".
        assertTrue("No custom action found on any screen", found > 0)
    }
}
