package com.victorarana.leo.features.quiz

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.features.components.AnswerRow
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoBottomBar
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.LeoIcon
import com.victorarana.leo.features.components.OptionState
import com.victorarana.leo.features.components.ProgressDots
import com.victorarana.leo.features.components.displayNameRes
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.leoScreenBackground
import com.victorarana.leo.features.theme.rememberReduceMotion
import com.victorarana.leo.features.theme.rememberTalkBackOn
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration

private const val FADE_MILLIS = 250
private const val SHAKE_MILLIS = 400
private const val SHAKE_DISTANCE_DP = 8f
private const val SHAKES = 3f
private const val TITLE3 = 20f
private const val VERDICT_ICON = 22f

/**
 * An exercise in two steps: the reader reads the passage, then answers its question. After answering, the passage
 * shows again under the feedback. Port of `ExerciseView`: stateless, the ViewModel's actions come in as lambdas.
 *
 * @param isReadingTimed Whether the question replaces the passage when its reading time runs out.
 */
@Composable
fun ExerciseScreen(
    state: QuizUiState,
    exercise: Exercise,
    isReadingTimed: Boolean,
    onFinishReading: () -> Unit,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val questionFocus = remember { FocusRequester() }
    val feedbackInView = remember { BringIntoViewRequester() }
    val isReading = state.isReading
    val verdict = state.isAnswerCorrect

    ExerciseEffects(isReading, verdict, scrollState, questionFocus, feedbackInView)

    val talkBackOn = rememberTalkBackOn()
    val readingTime = state.readingTime.takeIf { isReadingTimed && !talkBackOn }
    // Held here, not in the timer, so switching TalkBack on and off mid-passage doesn't restart the count.
    // `QuizScreen` keys this screen by exercise, which is what gives every exercise a full timer.
    val timerState = rememberReadingTimerState(state.readingTime ?: Duration.ZERO)
    val hasBottomBar = isReading || verdict != null
    Column(
        modifier
            .fillMaxSize()
            .leoScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .leoReadableWidth()
                .then(
                    if (hasBottomBar) {
                        Modifier
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    },
                ).padding(bottom = 24.dp),
        ) {
            ProgressDots(
                states = state.dots,
                currentIndex = state.currentIndex,
                modifier = Modifier.padding(top = 12.dp),
            )
            Crossfade(
                targetState = isReading,
                animationSpec = tween(FADE_MILLIS),
                label = "exercise-step",
            ) { reading ->
                Column {
                    if (reading) {
                        Passage(exercise, state.roundAgeGroup)
                    } else {
                        Question(exercise, state, onSelect, questionFocus)
                        if (verdict != null) {
                            Feedback(
                                isCorrect = verdict,
                                exercise = exercise,
                                modifier = Modifier.padding(top = 26.dp).bringIntoViewRequester(feedbackInView),
                            )
                            Passage(exercise, state.roundAgeGroup)
                        }
                    }
                }
            }
        }
        ExerciseBottomBar(state, verdict, readingTime, timerState, onFinishReading, onNext)
    }
}

/** The effects that fire when the step or the verdict changes. */
@Composable
private fun ExerciseEffects(
    isReading: Boolean,
    verdict: Boolean?,
    scrollState: ScrollState,
    questionFocus: FocusRequester,
    feedbackInView: BringIntoViewRequester,
) {
    val haptics = LocalHapticFeedback.current
    // What changed since the last composition that saw this exercise: these effects fire on a change only, never
    // when the screen is restored after a rotation (iOS `onChange`). Saved, so they survive it.
    var wasReading by rememberSaveable { mutableStateOf(isReading) }
    var hadVerdict by rememberSaveable { mutableStateOf(verdict != null) }
    LaunchedEffect(isReading) {
        if (wasReading && !isReading) {
            scrollState.scrollTo(0)
            // The question heading takes accessibility focus, so TalkBack reads it first.
            runCatching { questionFocus.requestFocus() }
        }
        wasReading = isReading
    }
    LaunchedEffect(verdict) {
        if (!hadVerdict && verdict != null) {
            haptics.performHapticFeedback(if (verdict) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            // After the feedback and the "Next text" bar are laid out. The scroll animates unless the reader
            // asked for no animations, which Compose then plays at zero duration.
            withFrameNanos { }
            feedbackInView.bringIntoView()
        }
        hadVerdict = verdict != null
    }
}

/** "Answer the question" with the timer while reading, "Next text" or "See results" once answered. */
@Composable
private fun ExerciseBottomBar(
    state: QuizUiState,
    verdict: Boolean?,
    readingTime: Duration?,
    timerState: ReadingTimerState,
    onFinishReading: () -> Unit,
    onNext: () -> Unit,
) {
    if (state.isReading) {
        LeoBottomBar {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (readingTime != null) {
                    ReadingTimer(total = readingTime, onTimeUp = onFinishReading, state = timerState)
                }
                LeoButton(
                    text = stringResource(R.string.quiz_answer_question),
                    kind = LeoButtonKind.Primary,
                    onClick = onFinishReading,
                )
            }
        }
    } else if (verdict != null) {
        LeoBottomBar {
            LeoButton(
                text =
                    stringResource(
                        if (state.isLastExercise) R.string.quiz_see_results else R.string.quiz_next_text,
                    ),
                kind = LeoButtonKind.Primary,
                onClick = onNext,
            )
        }
    }
}

/** The topic, the title and the passage, in the round's age group's text size. */
@Composable
private fun Passage(
    exercise: Exercise,
    ageGroup: AgeGroup?,
) {
    val colors = LeoTheme.colors
    Column {
        Kicker(text = exercise.topic.name, modifier = Modifier.padding(top = 28.dp))
        BasicText(
            text = exercise.title,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp).semantics { heading() },
            style = LeoTextStyle.title.toTextStyle().copy(color = colors.ink),
        )
        SelectionContainer {
            BasicText(
                text = exercise.passage,
                style = (ageGroup ?: AgeGroup.NINE).passageStyle.toTextStyle().copy(color = colors.ink),
            )
        }
    }
}

@Composable
private fun Question(
    exercise: Exercise,
    state: QuizUiState,
    onSelect: (Int) -> Unit,
    questionFocus: FocusRequester,
) {
    val colors = LeoTheme.colors
    Column {
        Kicker(
            text = stringResource(exercise.skill.displayNameRes),
            modifier = Modifier.padding(top = 28.dp),
            color = colors.accent700,
        )
        BasicText(
            text = exercise.question,
            modifier =
                Modifier
                    .padding(top = 8.dp, bottom = 14.dp)
                    .focusRequester(questionFocus)
                    .focusable()
                    .semantics { heading() },
            style = LeoTextStyle.question.toTextStyle().copy(color = colors.ink),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.options.forEachIndexed { index, option ->
                OptionButton(
                    letter = letter(index),
                    text = option,
                    state = state.optionState(index),
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/** A, B, C, D. */
private fun letter(index: Int): String = ('A' + index).toString()

@Composable
private fun Feedback(
    isCorrect: Boolean,
    exercise: Exercise,
    modifier: Modifier = Modifier,
) {
    val colors = LeoTheme.colors
    val tint = if (isCorrect) colors.accent700 else colors.magenta700
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeoIcon(
                id = if (isCorrect) R.drawable.ic_check_circle else R.drawable.ic_cancel,
                tint = tint,
                size = VERDICT_ICON.dp.scaled(relativeTo = TITLE3),
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = stringResource(if (isCorrect) R.string.quiz_correct_verdict else R.string.quiz_not_quite),
                style = LeoTextStyle.verdict.toTextStyle().copy(color = tint),
            )
        }
        if (!isCorrect && exercise.explanation.isNotEmpty()) {
            BasicText(
                text = exercise.explanation,
                style = LeoTextStyle.explanation.toTextStyle().copy(color = colors.ink),
            )
        }
    }
}

/**
 * An answer: the row, a note "Your answer" on a wrong pick, a shake when it is wrong, and taps only while idle
 * (without greying the highlighted ones: the row draws its own states). TalkBack reads the text, with the state as
 * its value.
 */
@Composable
private fun OptionButton(
    letter: String,
    text: String,
    state: OptionState,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val shakes = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (state == OptionState.Incorrect && previous != OptionState.Incorrect && !reduceMotion) {
            shakes.snapTo(0f)
            shakes.animateTo(1f, tween(SHAKE_MILLIS, easing = LinearEasing))
        }
        previous = state
    }
    val value =
        when (state) {
            OptionState.Correct -> stringResource(R.string.quiz_correct_answer)
            OptionState.Incorrect -> stringResource(R.string.quiz_your_answer_incorrect)
            OptionState.Idle, OptionState.Dimmed -> ""
        }
    val isIdle = state == OptionState.Idle
    Box(
        Modifier
            .shake { shakes.value }
            .then(
                if (isIdle) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ).clearAndSetSemantics {
                contentDescription = text
                if (value.isNotEmpty()) stateDescription = value
                if (isIdle) {
                    role = Role.Button
                    onClick {
                        onClick()
                        true
                    }
                }
            },
    ) {
        AnswerRow(
            marker = letter,
            text = text,
            state = state,
            note = if (state == OptionState.Incorrect) stringResource(R.string.quiz_your_answer) else null,
        )
    }
}

/** Shakes the view horizontally three times as [shakes] goes from 0 to 1 (`ShakeEffect`: 8 * sin(shakes * 6 pi)). */
private fun Modifier.shake(shakes: () -> Float): Modifier =
    graphicsLayer { translationX = SHAKE_DISTANCE_DP.dp.toPx() * sin(shakes() * PI.toFloat() * 2f * SHAKES) }
