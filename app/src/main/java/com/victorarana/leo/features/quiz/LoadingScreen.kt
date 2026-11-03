package com.victorarana.leo.features.quiz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.ProgressDots
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.SkeletonLines
import com.victorarana.leo.features.components.TopOffset
import com.victorarana.leo.features.components.displayNameRes
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.toTextStyle

private const val HEADLINE_SIZE = 40f

/**
 * Shown while the text the reader reached is still being written. Port of `LoadingView`.
 *
 * @param topicName The topic being tried, which changes when the generator falls back to a spare one.
 */
@Composable
fun LoadingScreen(
    index: Int,
    dots: List<DotState>,
    topicName: String?,
    ageGroup: AgeGroup,
    modifier: Modifier = Modifier,
) {
    val colors = LeoTheme.colors
    val topic = topicName ?: stringResource(R.string.quiz_next_text_placeholder)
    val range = ageGroup.passageWordRange
    val wordCount = (range.first + range.last) / 2
    val label = stringResource(R.string.quiz_loading_accessibility, index + 1, dots.size, topic)
    ScrollingScreen(modifier) {
        ProgressDots(states = dots, currentIndex = index, modifier = Modifier.padding(top = 12.dp))

        TopOffset(height = 140.dp)

        // One heading node. A polite live region announces it again when the topic changes (iOS posts an
        // announcement from `onChange(of: topicName)`).
        Column(
            Modifier.clearAndSetSemantics {
                heading()
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Kicker(stringResource(R.string.quiz_now_writing), color = colors.accent700)
            BasicText(
                text = topic,
                modifier = Modifier.padding(vertical = 10.dp),
                style = LeoTextStyle.display(HEADLINE_SIZE).toTextStyle().copy(color = colors.ink),
            )
            BasicText(
                text =
                    stringResource(
                        R.string.quiz_loading_message,
                        wordCount,
                        stringResource(ageGroup.displayNameRes),
                    ),
                style = LeoTextStyle.body.toTextStyle().copy(color = colors.inkSoft),
            )
        }

        SkeletonLines(Modifier.padding(top = 32.dp))
        Spacer(Modifier.weight(1f, min = 24.dp))
    }
}
