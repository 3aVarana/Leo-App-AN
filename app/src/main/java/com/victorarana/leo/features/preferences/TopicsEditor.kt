package com.victorarana.leo.features.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorarana.leo.R
import com.victorarana.leo.features.components.CustomTopicChip
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.ResourceTopicNames
import com.victorarana.leo.features.components.TopicChip
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LocalAnimatesPlaceholders
import com.victorarana.leo.features.theme.rememberReduceMotion
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle

private val FieldShape = RoundedCornerShape(2.dp)
private const val PLACEHOLDER_ALPHA = 0.65f
private const val STATIC_SPINNER_PROGRESS = 0.3f

/**
 * The topic chips, the reader's own topics and the field to add one, shared by onboarding and settings. The caller
 * puts its own heading above the suggested topics, and saves the ViewModel's draft when the reader is done.
 */
@Composable
fun TopicsEditor(
    viewModel: PreferencesEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    // A light haptic for each refused tap (iOS `sensoryFeedback(.impact(weight: .light))`); not for the first state.
    // Only for a new refusal: re-entering composition (rotation, back to the ages and on) must not replay it
    // (`sensoryFeedback(trigger:)` fires on a change).
    var played by rememberSaveable { mutableIntStateOf(state.minimumWarningCount) }
    LaunchedEffect(state.minimumWarningCount) {
        if (state.minimumWarningCount > played) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        played = state.minimumWarningCount
    }
    val minimumHint = stringResource(R.string.topics_minimum_hint)
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (topic in state.suggestedTopics) {
                TopicChip(
                    name = stringResource(ResourceTopicNames.nameRes(topic)),
                    isOn = state.isEnabled(topic),
                    onToggle = { viewModel.setEnabled(it, topic) },
                    hint = if (state.isToggleDisabled(topic)) minimumHint else null,
                )
            }
        }

        SectionKicker(stringResource(R.string.topics_your_topics))

        if (state.customTopics.isNotEmpty()) {
            FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (topic in state.customTopics) {
                    CustomTopicChip(
                        name = topic.name,
                        onRemove = if (state.isAtMinimum) null else ({ viewModel.removeCustomTopic(topic) }),
                    )
                }
            }
        }

        AddTopicField(viewModel)
    }
}

/** "Keep at least 3 topics on.", shown after the reader tried to turn off a topic at the minimum. */
@Composable
fun MinimumTopicsWarning(
    viewModel: PreferencesEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isShowingMinimumWarning) {
        BasicText(
            stringResource(R.string.topics_minimum_warning),
            modifier,
            style = LeoTextStyle.note.toTextStyle().copy(color = LeoTheme.colors.magenta700),
        )
    }
}

@Composable
private fun TopicMessage.text(): String =
    when (this) {
        TopicMessage.InvalidLength -> stringResource(R.string.topics_invalid_length)
        TopicMessage.AlreadyOnList -> stringResource(R.string.topics_already_on_list)
        TopicMessage.TooManyCustomTopics -> stringResource(R.string.topics_too_many)
        TopicMessage.ReviewFailed -> stringResource(R.string.topics_review_failed)
        is TopicMessage.Rejected -> reason
    }

@Composable
private fun AddTopicField(viewModel: PreferencesEditorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LeoTheme.colors
    // 44 at 1x as on iOS, but never under Android's 48dp touch size, which the Add button has.
    val height = maxOf(44.dp.scaled(relativeTo = 16f), 48.dp)
    val keyboard = LocalSoftwareKeyboardController.current
    val textStyle = LeoTextStyle(size = 16f).toTextStyle().copy(color = colors.ink)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = viewModel.newTopic,
                onValueChange = viewModel::onNewTopicChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = height)
                        .background(colors.surface, FieldShape)
                        .border(1.dp, colors.divider, FieldShape)
                        .padding(horizontal = 10.dp),
                enabled = !state.isReviewing,
                textStyle = textStyle,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            viewModel.add()
                            // `onSubmit` ends editing on iOS; a custom action replaces the default that hides it.
                            keyboard?.hide()
                        },
                    ),
                cursorBrush = SolidColor(colors.ink),
                decorationBox = { field ->
                    Box(Modifier.heightIn(min = height), contentAlignment = Alignment.CenterStart) {
                        if (viewModel.newTopic.isEmpty()) {
                            BasicText(
                                stringResource(R.string.topics_add_placeholder),
                                style = textStyle.copy(color = colors.ink.copy(alpha = PLACEHOLDER_ALPHA)),
                            )
                        }
                        field()
                    }
                },
            )
            if (state.isReviewing) {
                Spinner(Modifier.size(height))
            } else {
                LeoButton(
                    text = stringResource(R.string.topics_add),
                    kind = LeoButtonKind.Secondary,
                    onClick = viewModel::add,
                    enabled = viewModel.canAdd,
                )
            }
        }
        state.message?.let {
            BasicText(
                it.text(),
                // Read out when it appears: TalkBack users get no other sign that a review ended (Android addition).
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = LeoTextStyle.note.toTextStyle().copy(color = colors.magenta700),
            )
        }
    }
}

/**
 * The review's activity indicator, quiet and the same size as the Add button's touch area. A still arc when
 * placeholders don't animate (snapshots) or the reader asked for no motion: an endless animation would also keep
 * `waitForIdle` from returning.
 */
@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    val color = LeoTheme.colors.inkMuted
    val animates = LocalAnimatesPlaceholders.current && !rememberReduceMotion()
    Box(
        modifier.clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        contentAlignment = Alignment.Center,
    ) {
        if (animates) {
            CircularProgressIndicator(
                Modifier.size(24.dp),
                color = color,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
            )
        } else {
            CircularProgressIndicator(
                progress = { STATIC_SPINNER_PROGRESS },
                modifier = Modifier.size(24.dp),
                color = color,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
            )
        }
    }
}
