package com.victorarana.leo.features.quiz

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.components.LeoButton
import com.victorarana.leo.features.components.LeoButtonKind
import com.victorarana.leo.features.components.LeoIcon
import com.victorarana.leo.features.components.ProgressDots
import com.victorarana.leo.features.components.ScrollingScreen
import com.victorarana.leo.features.components.TopOffset
import com.victorarana.leo.features.preferences.Headline
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle

private const val HEADLINE_SIZE = 40f
private const val LARGE_TITLE = 34f
private const val BODY = 17f

/**
 * Shown when a text couldn't be written, with a retry on the same topics and one on others.
 * Port of `GenerationFailedView`.
 *
 * @param topicName The main topic planned for this text.
 */
@Composable
fun GenerationFailedScreen(
    index: Int,
    dots: List<DotState>,
    topicName: String?,
    onRetry: () -> Unit,
    onTryDifferentTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LeoTheme.colors
    ScrollingScreen(
        modifier = modifier,
        bottomBar = {
            LeoButton(kind = LeoButtonKind.Primary, onClick = onRetry) {
                LeoIcon(
                    id = R.drawable.ic_refresh,
                    tint = LocalContentColor.current,
                    size = 20.dp.scaled(relativeTo = BODY),
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quiz_try_again))
            }
            LeoButton(
                text = stringResource(R.string.quiz_try_different_topic),
                kind = LeoButtonKind.Ghost,
                onClick = onTryDifferentTopic,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
        },
    ) {
        ProgressDots(states = dots, currentIndex = index, modifier = Modifier.padding(top = 12.dp))

        TopOffset(height = 140.dp)

        LeoIcon(
            id = R.drawable.ic_error,
            tint = colors.magenta600,
            size = 40.dp.scaled(relativeTo = LARGE_TITLE),
            modifier = Modifier.clearAndSetSemantics {},
        )
        Kicker(
            text = stringResource(R.string.quiz_failed_kicker, index + 1),
            modifier = Modifier.padding(top = 16.dp),
            color = colors.magenta700,
        )
        Headline(
            text = stringResource(R.string.quiz_failed_title),
            style = LeoTextStyle.display(HEADLINE_SIZE).toTextStyle(),
            modifier = Modifier.padding(vertical = 10.dp),
        )
        BasicText(
            text = failureMessage(index, topicName),
            style = LeoTextStyle.body.toTextStyle().copy(color = colors.inkSoft),
        )
        Spacer(Modifier.weight(1f, min = 24.dp))
    }
}

/** What went wrong, and that earlier answers are kept. Nothing is answered before text 2. */
@Composable
private fun failureMessage(
    index: Int,
    topicName: String?,
): String {
    val failure =
        if (topicName != null) {
            stringResource(R.string.quiz_failed_topic, topicName)
        } else {
            stringResource(R.string.quiz_failed_generic)
        }
    val reassurance =
        when (index) {
            0 -> null
            1 -> stringResource(R.string.quiz_failed_saved_one)
            else -> stringResource(R.string.quiz_failed_saved_many)
        }
    return listOfNotNull(failure, reassurance).joinToString(" ")
}
