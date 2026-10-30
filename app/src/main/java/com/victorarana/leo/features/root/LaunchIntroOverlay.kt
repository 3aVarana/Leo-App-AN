package com.victorarana.leo.features.root

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.root.LaunchIntro.Exit
import com.victorarana.leo.features.root.LaunchIntro.Phase
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.rememberReduceMotion
import com.victorarana.leo.features.theme.toTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val MILLIS = 1000

/**
 * The header wordmark's frame in the window, written by `Modifier.launchWordmarkTarget()` (iOS: the
 * `LaunchWordmarkFrame` preference key and the `hidesLaunchWordmarkTarget` environment value in one object).
 * Screens without a header never write it, and the centered wordmark fades out instead.
 */
@Stable
class LaunchWordmarkTarget {
    var frame: Rect? by mutableStateOf(null)

    /** Whether the moving wordmark stands in for the header, which stays hidden, in its place, meanwhile. */
    var isHidden: Boolean by mutableStateOf(false)
}

val LocalLaunchWordmarkTarget = staticCompositionLocalOf<LaunchWordmarkTarget?> { null }

/**
 * Marks this view as the place the launch wordmark moves to, and hides it while the wordmark is moving there.
 * Without a [LaunchIntroHost] around it, it does nothing.
 */
@Composable
fun Modifier.launchWordmarkTarget(): Modifier {
    val target = LocalLaunchWordmarkTarget.current ?: return this
    // Read while drawing, not composing: the host sets `isHidden` after composition, before the frame is drawn.
    return graphicsLayer { alpha = if (target.isHidden) 0f else 1f }
        .onGloballyPositioned { target.frame = it.boundsInWindow() }
}

/**
 * Plays the launch intro over [content], the first time it's shown after the process starts.
 *
 * @param initialPhase `plays = false` freezes the intro at this phase, for snapshots and previews. Reduced motion
 * comes from `LocalReduceMotion` or the system, like everywhere else.
 * @param isReady The launch frame holds after [LaunchIntro.Timing.HOLD] until this is true, so the first screen
 * is known (Android loads the preferences asynchronously, so there'd be a flash of onboarding otherwise).
 *
 * One composable for the one iOS modifier (state, effects and layers), so it stays whole.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun LaunchIntroHost(
    modifier: Modifier = Modifier,
    initialPhase: Phase? = null,
    plays: Boolean = true,
    isReady: Boolean = true,
    content: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val target = remember { LaunchWordmarkTarget() }
    var phase by remember { mutableStateOf(initialPhase ?: if (LaunchIntro.hasPlayed) Phase.Done else Phase.Launch) }
    // The header wordmark's frame, kept from the first frame, while nothing has moved yet.
    var headerFrame by remember { mutableStateOf<Rect?>(null) }
    var overlayHeight by remember { mutableStateOf(0f) }
    var overlayBounds by remember { mutableStateOf(Rect.Zero) }
    // Set when the header moved just after the move began, so the move would miss it.
    var headerMoved by remember { mutableStateOf(false) }
    var moveBeganNanos by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val risePx =
        with(density) {
            LaunchIntro.Timing.RISE.dp
                .toPx()
        }

    val exit = LaunchIntro.exit(reduceMotion, hasTarget = headerFrame != null, headerMoved = headerMoved)
    SideEffect { target.isHidden = LaunchIntro.hidesHeader(phase, exit) }
    // The effect below outlives recompositions; it must see the latest value, not the first one.
    val currentIsReady by rememberUpdatedState(isReady)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)

    val reported = target.frame
    LaunchedEffect(reported) {
        when (phase) {
            Phase.Launch -> headerFrame = reported
            Phase.Playing -> {
                val first = headerFrame
                val elapsed = (System.nanoTime() - moveBeganNanos) / MILLIS.toDouble() / MILLIS / MILLIS
                if (exit == Exit.Move && movedWhileComparable(first, reported, elapsed)) {
                    headerMoved = true
                }
            }
            Phase.Done -> Unit
        }
    }

    LaunchedEffect(plays) {
        if (!plays || phase == Phase.Done) return@LaunchedEffect
        if (phase == Phase.Launch) {
            delay(seconds(LaunchIntro.Timing.HOLD))
            snapshotFlow { currentIsReady }.first { it }
            moveBeganNanos = System.nanoTime()
            phase = Phase.Playing
        }
        delay(
            seconds(if (currentReduceMotion) LaunchIntro.Timing.REDUCED_MOTION_FADE else LaunchIntro.Timing.PLAYING),
        )
        // Only now: an Activity recreated during the hold must wait for `isReady` again, not skip to the page.
        LaunchIntro.hasPlayed = true
        phase = Phase.Done
    }

    // The page: transparent and `rise` below its place on the first frame, then rising into it.
    val pageSpec =
        if (reduceMotion) {
            tween<Float>(
                seconds(LaunchIntro.Timing.REDUCED_MOTION_FADE).toInt(),
                easing = LaunchIntro.Timing.reducedMotionCurve,
            )
        } else {
            tween(
                durationMillis = seconds(LaunchIntro.Timing.PAGE_DURATION).toInt(),
                delayMillis = seconds(LaunchIntro.Timing.PAGE_DELAY).toInt(),
                easing = LaunchIntro.Timing.pageCurve,
            )
        }
    val pageProgress by animateFloatAsState(if (phase.isPageShown) 1f else 0f, pageSpec, label = "page")

    CompositionLocalProvider(LocalLaunchWordmarkTarget provides target) {
        // The ground is always there, so nothing flashes while the page is transparent.
        Box(modifier.fillMaxSize().background(LeoTheme.colors.background)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = pageProgress
                        translationY = if (reduceMotion) 0f else (1f - pageProgress) * risePx
                    }.then(if (phase.isPageInteractive) Modifier else Modifier.clearAndSetSemantics {}),
            ) { content() }
            if (phase.isRunning) {
                val move =
                    if (exit == Exit.Move && overlayHeight > 0f) {
                        headerFrame?.let { LaunchIntro.wordmarkMove(it, overlayHeight, risePx) }
                    } else {
                        null
                    }
                WordmarkOverlay(
                    isMoving = phase.isPageShown,
                    move = move,
                    reduceMotion = reduceMotion,
                    overlayCenter = overlayBounds.center,
                    onHeight = { overlayHeight = it },
                    onBounds = { overlayBounds = it },
                )
            }
        }
    }
}

/** A header that moved while the first measurement is still comparable would be missed by the move. */
private fun movedWhileComparable(
    first: Rect?,
    reported: Rect?,
    elapsedSeconds: Double,
): Boolean =
    first != null &&
        reported != null &&
        LaunchIntro.canCompareHeader(elapsedSeconds) &&
        LaunchIntro.headerMoved(first, reported)

private fun seconds(value: Double): Long = (value * MILLIS).toLong()

@Composable
private fun WordmarkOverlay(
    isMoving: Boolean,
    move: LaunchIntro.WordmarkMove?,
    reduceMotion: Boolean,
    overlayCenter: Offset,
    onHeight: (Float) -> Unit,
    onBounds: (Rect) -> Unit,
) {
    val easing: Easing = if (reduceMotion) LaunchIntro.Timing.reducedMotionCurve else LaunchIntro.Timing.moveCurve
    val duration =
        seconds(if (reduceMotion) LaunchIntro.Timing.REDUCED_MOTION_FADE else LaunchIntro.Timing.MOVE_DURATION)
    val progress by animateFloatAsState(
        if (isMoving) 1f else 0f,
        tween(duration.toInt(), easing = easing),
        label = "move",
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { onBounds(it.boundsInWindow()) }
                // Taps wait for the swap (L3): the overlay swallows every pointer event, over the page.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
                // Hidden from TalkBack: the page is the content.
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Leo",
            modifier =
                Modifier
                    .onSizeChanged { onHeight(it.height.toFloat()) }
                    .graphicsLayer {
                        val delta = move?.let { it.center - overlayCenter } ?: Offset.Zero
                        translationX = delta.x * progress
                        translationY = delta.y * progress
                        val scale = 1f + ((move?.scale ?: 1f) - 1f) * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = if (move == null) 1f - progress else 1f
                    },
            style = LeoTextStyle.launchWordmark.toTextStyle().copy(color = LeoTheme.colors.ink),
        )
    }
}
