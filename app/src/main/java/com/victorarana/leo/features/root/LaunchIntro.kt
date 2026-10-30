package com.victorarana.leo.features.root

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

// The launch intro of docs/Leo-Launch-Screen-Plan.md, section 3 (iOS `LaunchIntro.swift`): it starts on the launch
// screen's frame, a centered "Leo", and hands over to the first screen. On Welcome the wordmark moves into the
// header; on other screens it fades out. Everything else rises into place as one block. This file is the pure
// part (times, phases, rules); `LaunchIntroOverlay.kt` draws it.

private const val HEADER_TOLERANCE = 0.5f

object LaunchIntro {
    /** All the times (seconds) and curves in one place. */
    object Timing {
        /** The launch frame stays still before the move. */
        const val HOLD = 0.3
        const val MOVE_DURATION = 0.48

        /** How long after the move begins the page starts to rise. */
        const val PAGE_DELAY = 0.2
        const val PAGE_DURATION = 0.48

        /** How far below its place the page starts, in dp. */
        const val RISE = 24f

        /** With reduced motion: the wordmark and the page cross-fade. */
        const val REDUCED_MOTION_FADE = 0.2

        /**
         * After the move begins, the page is still [RISE] below its place for [PAGE_DELAY], less this margin for
         * the first animated frame. The header's frame is only comparable to the one measured on the launch frame
         * until then.
         */
        const val HEADER_CHECK_MARGIN = 0.02

        /** When the page finishes rising, from the first frame. The last thing to move. */
        const val PAGE_END = HOLD + PAGE_DELAY + PAGE_DURATION

        /**
         * When the header wordmark takes over from the moving one, and taps are enabled. The moving wordmark
         * waits at its place until the page settles under it, so the swap is exact.
         */
        const val SWAP = PAGE_END

        /** How long the intro runs after the hold. */
        const val PLAYING = SWAP - HOLD

        val moveCurve = CubicBezierEasing(0.65f, 0f, 0.25f, 1f)
        val pageCurve = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)
        val reducedMotionCurve = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    }

    enum class Phase {
        /** The first frame: the launch screen again, with the page hidden. */
        Launch,

        /** The wordmark moves or fades, and the page rises. */
        Playing,

        /** Everything at rest. */
        Done,
        ;

        val isPageShown: Boolean get() = this != Launch

        /** Taps wait for the swap (L3 of the plan). */
        val isPageInteractive: Boolean get() = this == Done

        val isRunning: Boolean get() = this != Done
    }

    /** How the centered wordmark leaves. */
    enum class Exit {
        /** It moves into the header wordmark, which stays hidden until the intro ends. */
        Move,

        /** It fades out where it is, while the header, part of the page, fades in with the rest. */
        Fade,
    }

    /**
     * The wordmark only moves when motion is allowed, there's a header to land on (Welcome is the only screen
     * with one) and the header hasn't moved since it was measured. A header that has moved would be missed, so
     * the wordmark fades instead for the rest of the intro.
     */
    fun exit(
        reduceMotion: Boolean,
        hasTarget: Boolean,
        headerMoved: Boolean,
    ): Exit = if (reduceMotion || !hasTarget || headerMoved) Exit.Fade else Exit.Move

    /**
     * Whether the header, measured while the page is below its place, can still be compared with its measurement
     * on the launch frame. The page is rising after that, and the rise moves the header too.
     */
    fun canCompareHeader(sinceMoveBegan: Double): Boolean =
        sinceMoveBegan < Timing.PAGE_DELAY - Timing.HEADER_CHECK_MARGIN

    /**
     * Whether the header's frame changed between two measurements. A rotating window moves it shortly after
     * launch. Sub-pixel noise doesn't count.
     */
    fun headerMoved(
        from: Rect,
        to: Rect,
    ): Boolean =
        abs(to.left - from.left) > HEADER_TOLERANCE ||
            abs(to.top - from.top) > HEADER_TOLERANCE ||
            abs(to.width - from.width) > HEADER_TOLERANCE ||
            abs(to.height - from.height) > HEADER_TOLERANCE

    /** The header wordmark is hidden only while the moving wordmark stands in for it. */
    fun hidesHeader(
        phase: Phase,
        exit: Exit,
    ): Boolean = phase.isRunning && exit == Exit.Move

    /** Where the centered wordmark ends up, and how much it shrinks, to land exactly on the header wordmark. */
    data class WordmarkMove(
        val center: Offset,
        val scale: Float,
    )

    /**
     * @param header The header wordmark's frame, measured while the page is [rise] below its place.
     * @param overlayHeight The centered wordmark's height before scaling.
     * @param rise [Timing.RISE] in the unit of [header] (pixels in the overlay, dp by default).
     *
     * The scale is the ratio of the two text heights, so it follows the font scale for the header.
     */
    fun wordmarkMove(
        header: Rect,
        overlayHeight: Float,
        rise: Float = Timing.RISE,
    ): WordmarkMove =
        WordmarkMove(
            center = Offset(header.center.x, header.center.y - rise),
            scale =
                header.height / overlayHeight,
        )
}
