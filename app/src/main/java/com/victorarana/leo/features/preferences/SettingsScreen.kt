package com.victorarana.leo.features.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorarana.leo.R
import com.victorarana.leo.features.components.AgeSegmentedControl
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.summaryRes
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.leoScreenBackground
import com.victorarana.leo.features.theme.toTextStyle

/**
 * Changes the age group, the number of texts per round, the reading timer and the topics. Nothing is saved until
 * Done. Port of `SettingsView`, as a full-screen destination instead of a sheet (D5): system back is Cancel.
 */
@Composable
fun SettingsScreen(
    editor: PreferencesEditorViewModel,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by editor.uiState.collectAsStateWithLifecycle()
    val colors = LeoTheme.colors
    val headline = LeoTextStyle(size = 17f, weight = LeoWeight.Semibold).toTextStyle()
    Column(
        modifier
            .fillMaxSize()
            .leoScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        Row(
            Modifier.fillMaxWidth().leoReadableWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeoButton(stringResource(R.string.settings_cancel), LeoButtonKind.Ghost, onClick = onCancel)
            Headline(
                stringResource(R.string.settings_title),
                headline.copy(textAlign = TextAlign.Center),
                Modifier.weight(1f),
            )
            LeoButton(
                stringResource(R.string.preferences_done),
                LeoButtonKind.Ghost,
                onClick = onDone,
            )
        }
        Column(
            Modifier
                .weight(1f)
                // The viewport shrinks with the keyboard, so the focused field is brought into view above it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .leoReadableWidth()
                .padding(top = 22.dp, bottom = 24.dp),
        ) {
            SectionKicker(stringResource(R.string.age_group_label), top = 0.dp, bottom = 10.dp)
            AgeSegmentedControl(selection = state.draft.ageGroup, onSelect = editor::setAgeGroup)
            val group = state.draft.ageGroup
            BasicText(
                stringResource(group.summaryRes, group.passageWordRange.first, group.passageWordRange.last),
                Modifier.padding(top = 8.dp),
                style = LeoTextStyle.caption.toTextStyle().copy(color = colors.inkMuted),
            )

            SectionKicker(stringResource(R.string.preferences_round), top = 28.dp, bottom = 6.dp)
            TextsPerRoundStepper(state.draft.exercisesPerRound, editor::setExercisesPerRound)

            SectionKicker(stringResource(R.string.settings_reading), top = 28.dp, bottom = 6.dp)
            ReadingTimerRow(state.draft.isReadingTimerOn, editor::setReadingTimerOn)

            Row(
                // 20 above a row that is itself at least 48 tall (Reset's touch area), so it never jumps.
                Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SectionKicker(
                        stringResource(R.string.settings_suggested_count, state.enabledSuggestedCount),
                        top = 0.dp,
                        bottom = 0.dp,
                    )
                }
                if (state.canResetSuggestedTopics) {
                    LeoButton(kind = LeoButtonKind.Ghost, onClick = editor::resetSuggestedTopics) {
                        Text(
                            stringResource(R.string.settings_reset),
                            fontSize = LeoTextStyle(size = 14f).toTextStyle().fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            MinimumTopicsWarning(editor, Modifier.padding(bottom = 8.dp))
            TopicsEditor(editor)
        }
    }
}

/** The timer's switch row: one TalkBack switch with its caption (iOS `Toggle` with a label). */
@Composable
private fun ReadingTimerRow(
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LeoTheme.colors
    val title = LeoTextStyle(size = 17f, weight = LeoWeight.Semibold).toTextStyle()
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = isOn, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                stringResource(R.string.settings_reading_timer),
                style = title.copy(color = colors.ink),
            )
            BasicText(
                stringResource(R.string.settings_reading_timer_caption),
                style = LeoTextStyle.caption.toTextStyle().copy(color = colors.inkMuted),
            )
        }
        Switch(
            checked = isOn,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colors.background,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = colors.accent,
                    uncheckedThumbColor = colors.neutral600,
                    uncheckedTrackColor = colors.background,
                    uncheckedBorderColor = colors.neutral600,
                ),
        )
    }
}
