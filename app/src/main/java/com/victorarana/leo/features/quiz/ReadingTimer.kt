package com.victorarana.leo.features.quiz

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.victorarana.leo.R
import com.victorarana.leo.features.components.Kicker
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LeoWeight
import com.victorarana.leo.features.theme.toTextStyle
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.time.Duration

private const val STEP_MILLIS = 1000L
private const val SECONDS_PER_MINUTE = 60
private const val TIME_SIZE = 15f

/** The time of the display, "1:05". Digits follow [locale]. */
internal fun formatMinuteSecond(
    totalSeconds: Long,
    locale: Locale,
): String = String.format(locale, "%d:%02d", totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)

/** What TalkBack reads: "1 minute, 5 seconds", zero units left out, in the reader's language. */
internal fun spokenDuration(
    totalSeconds: Long,
    locale: Locale,
): String {
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val measures =
        buildList {
            if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
            if (seconds > 0 || minutes == 0L) add(Measure(seconds, MeasureUnit.SECOND))
        }
    // At most two measures: the copy the spread makes is nothing.
    @Suppress("SpreadOperator")
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).formatMeasures(*measures.toTypedArray())
}

/**
 * The time left to read, and a bar that empties with it. Counts down only while the screen is resumed (iOS: while
 * `scenePhase == .active`), and calls [onTimeUp] at zero. Port of the private `ReadingTimer`.
 *
 * The seconds left are `rememberSaveable`, keyed by [resetKey] (the exercise), so a rotation keeps them and each
 * exercise starts full. A partial second is lost whenever the timer pauses, exactly as on iOS.
 */
@Composable
fun ReadingTimer(
    total: Duration,
    resetKey: Any,
    onTimeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = total.inWholeSeconds
    var remaining by rememberSaveable(resetKey) { mutableLongStateOf(totalSeconds) }
    val currentOnTimeUp by rememberUpdatedState(onTimeUp)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, resetKey) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (remaining > 0) {
                delay(STEP_MILLIS)
                remaining -= 1
            }
            currentOnTimeUp()
        }
    }

    val colors = LeoTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val label = stringResource(R.string.quiz_time_left)
    val spoken = remember(remaining, locale) { spokenDuration(remaining, locale) }
    val fraction by animateFloatAsState(
        targetValue = if (totalSeconds > 0) remaining.toFloat() / totalSeconds else 0f,
        animationSpec = tween(durationMillis = STEP_MILLIS.toInt(), easing = LinearEasing),
        label = "reading-timer-bar",
    )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // One node, like iOS: the visible kicker and digits are replaced by a label and a spoken value.
                .clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = spoken
                },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Kicker(stringResource(R.string.quiz_time_to_read), Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            BasicText(
                text = remember(remaining, locale) { formatMinuteSecond(remaining, locale) },
                modifier = Modifier.alignByBaseline(),
                style =
                    LeoTextStyle(size = TIME_SIZE, weight = LeoWeight.Semibold)
                        .toTextStyle()
                        .copy(color = colors.ink, fontFeatureSettings = "tnum"),
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.divider)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(colors.accent))
        }
    }
}
