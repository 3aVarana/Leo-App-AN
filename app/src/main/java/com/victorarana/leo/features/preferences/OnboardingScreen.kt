package com.victorarana.leo.features.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.components.AgeRow
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.displayNameRes
import com.victorarana.leo.features.components.summaryRes
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.leoScreenBackground
import com.victorarana.leo.features.theme.toTextStyle

/**
 * Shown on first launch: the reader picks their age group, then their topics and how many texts a round has.
 * Port of `OnboardingView`. It owns a back stack of its own, so system back from the topics returns to the ages.
 * The picker's selection is screen state, not in the ViewModel (iOS `@State`), but survives rotation.
 */
@Composable
fun OnboardingScreen(
    editor: PreferencesEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    var selection by rememberSaveable { mutableStateOf<AgeGroup?>(null) }
    NavHost(navController = navController, startDestination = OnboardingRoute.AgeGroups, modifier = modifier) {
        composable<OnboardingRoute.AgeGroups> {
            AgeGroupPicker(
                selection = selection,
                onSelect = { selection = it },
                // Dropped while the screen is leaving RESUMED, so a double tap pushes Topics once.
                onContinue =
                    dropUnlessResumed {
                        selection?.let {
                            editor.setAgeGroup(it)
                            navController.navigate(OnboardingRoute.Topics(it))
                        }
                    },
            )
        }
        composable<OnboardingRoute.Topics> { entry ->
            // After process death the editor is new (draft: the default group) but the route remembers the pick.
            val ageGroup = entry.toRoute<OnboardingRoute.Topics>().ageGroup
            val isSynced =
                editor.uiState
                    .collectAsStateWithLifecycle()
                    .value.draft.ageGroup == ageGroup
            LaunchedEffect(ageGroup) { editor.setAgeGroup(ageGroup) }
            if (isSynced) OnboardingTopics(editor) else Box(Modifier.fillMaxSize().leoScreenBackground())
        }
    }
}

private const val AGE_TITLE_SIZE = 38f
private const val TOPICS_TITLE_SIZE = 34f

/** A screen's headline, announced by TalkBack as a heading. */
@Composable
internal fun Headline(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BasicText(text, modifier.semantics { heading() }, style = style.copy(color = LeoTheme.colors.ink))
}

@Composable
fun AgeGroupPicker(
    selection: AgeGroup?,
    onSelect: (AgeGroup) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LeoTheme.colors
    ScrollingScreen(
        modifier = modifier,
        bottomBar = {
            LeoButton(
                text = stringResource(R.string.onboarding_continue),
                kind = LeoButtonKind.Primary,
                onClick = onContinue,
                enabled = selection != null,
            )
        },
    ) {
        Column(Modifier.padding(top = 20.dp)) {
            Kicker(stringResource(R.string.preferences_welcome))
            Headline(
                stringResource(R.string.onboarding_age_title),
                LeoTextStyle.display(AGE_TITLE_SIZE).toTextStyle(),
                Modifier.padding(vertical = 12.dp),
            )
            BasicText(
                stringResource(R.string.onboarding_age_message),
                Modifier.padding(bottom = 24.dp),
                style = LeoTextStyle.body.toTextStyle().copy(color = colors.inkSoft),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (group in AgeGroup.entries) {
                    AgeRow(
                        label = stringResource(group.displayNameRes),
                        description =
                            stringResource(group.summaryRes, group.passageWordRange.first, group.passageWordRange.last),
                        isSelected = selection == group,
                        onClick = { onSelect(group) },
                    )
                }
            }
        }
    }
}

/** The second onboarding screen: topics, and how many texts a round has. System back returns to the ages. */
@Composable
fun OnboardingTopics(
    editor: PreferencesEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val state by editor.uiState.collectAsStateWithLifecycle()
    val colors = LeoTheme.colors
    ScrollingScreen(
        modifier = modifier,
        bottomBar = {
            LeoButton(
                text = stringResource(R.string.preferences_done),
                kind = LeoButtonKind.Primary,
                onClick = editor::save,
            )
        },
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            Kicker(stringResource(R.string.preferences_welcome))
            Headline(
                stringResource(R.string.onboarding_topics_title),
                LeoTextStyle.display(TOPICS_TITLE_SIZE).toTextStyle(),
                Modifier.padding(vertical = 10.dp),
            )
            BasicText(
                stringResource(R.string.onboarding_topics_count, state.enabledTopicCount),
                style = LeoTextStyle.bodySmall.toTextStyle().copy(color = colors.inkSoft),
            )
            MinimumTopicsWarning(editor, Modifier.padding(top = 6.dp))
            SectionKicker(
                stringResource(R.string.onboarding_suggested_for, stringResource(state.draft.ageGroup.displayNameRes)),
            )
            TopicsEditor(editor)
            OnboardingRoundSection(state.draft.exercisesPerRound, editor::setExercisesPerRound)
        }
    }
}

/** A kicker that is a heading, 22 above and 10 below, like the iOS section headings. */
@Composable
internal fun SectionKicker(
    text: String,
    modifier: Modifier = Modifier,
    top: Dp = 22.dp,
    bottom: Dp = 10.dp,
) {
    Box(
        modifier.padding(top = top, bottom = bottom).semantics(mergeDescendants = true) { heading() },
    ) {
        Kicker(text)
    }
}

/** The "Round" heading and the texts-per-round stepper at the end of the topics screen. */
@Composable
fun OnboardingRoundSection(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionKicker(stringResource(R.string.preferences_round))
        TextsPerRoundStepper(count, onCountChange)
    }
}
