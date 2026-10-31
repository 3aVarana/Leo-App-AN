package com.victorarana.leo.features.root

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.victorarana.leo.features.root.LaunchIntro.Exit
import com.victorarana.leo.features.root.LaunchIntro.Phase
import com.victorarana.leo.features.root.LaunchIntro.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LaunchIntroTests {
    private fun rect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) = Rect(Offset(x, y), Size(width, height))

    @Test
    fun holdIsShortAndTheMoveStartsAfterIt() {
        assertEquals(0.3, Timing.HOLD, 0.0)
    }

    /** The page starts rising during the move, and its rise is the last thing to finish. */
    @Test
    fun pageRisesWhileTheWordmarkMovesAndFinishesLast() {
        val moveStart = Timing.HOLD
        val moveEnd = Timing.HOLD + Timing.MOVE_DURATION
        val pageStart = Timing.HOLD + Timing.PAGE_DELAY
        assertTrue(pageStart > moveStart)
        assertTrue(pageStart < moveEnd)
        assertTrue(Timing.PAGE_END > moveEnd)
    }

    /** The moving wordmark stays until the page has settled, so the header takes over without a jump. */
    @Test
    fun swapWaitsForThePage() {
        assertEquals(Timing.PAGE_END, Timing.SWAP, 0.0)
        assertEquals(Timing.SWAP, Timing.HOLD + Timing.PLAYING, 1e-9)
    }

    @Test
    fun pageIsInteractiveOnlyOnceTheIntroIsDone() {
        assertFalse(Phase.Launch.isPageInteractive)
        assertFalse(Phase.Playing.isPageInteractive)
        assertTrue(Phase.Done.isPageInteractive)
    }

    @Test
    fun pageIsHiddenOnlyOnTheFirstFrame() {
        assertFalse(Phase.Launch.isPageShown)
        assertTrue(Phase.Playing.isPageShown)
        assertTrue(Phase.Done.isPageShown)
    }

    @Test
    fun theIntroIsRunningUntilItIsDone() {
        assertTrue(Phase.Launch.isRunning)
        assertTrue(Phase.Playing.isRunning)
        assertFalse(Phase.Done.isRunning)
    }

    @Test
    fun wordmarkMovesOnlyWhenMotionIsAllowedAndThereIsAHeaderToLandOn() {
        assertEquals(Exit.Move, LaunchIntro.exit(reduceMotion = false, hasTarget = true, headerMoved = false))
        assertEquals(Exit.Fade, LaunchIntro.exit(reduceMotion = true, hasTarget = true, headerMoved = false))
        assertEquals(Exit.Fade, LaunchIntro.exit(reduceMotion = false, hasTarget = false, headerMoved = false))
        assertEquals(Exit.Fade, LaunchIntro.exit(reduceMotion = true, hasTarget = false, headerMoved = false))
    }

    /** A header that moved after it was measured would be missed, so the wordmark fades instead. */
    @Test
    fun wordmarkFadesOnceTheHeaderHasMoved() {
        assertEquals(Exit.Fade, LaunchIntro.exit(reduceMotion = false, hasTarget = true, headerMoved = true))
    }

    @Test
    fun headerCanBeComparedOnlyBeforeThePageStartsRising() {
        assertTrue(LaunchIntro.canCompareHeader(0.0))
        assertTrue(LaunchIntro.canCompareHeader(0.11))
        assertFalse(LaunchIntro.canCompareHeader(Timing.PAGE_DELAY))
        assertFalse(LaunchIntro.canCompareHeader(0.4))
    }

    @Test
    fun headerMovedIgnoresSubPixelNoiseOnly() {
        val header = rect(305f, 68.25f, 33.5f, 27.5f)
        assertFalse(LaunchIntro.headerMoved(header, header))
        assertFalse(LaunchIntro.headerMoved(header, header.translate(0.2f, -0.3f)))
        // A rotating window moved it by 22.
        assertTrue(LaunchIntro.headerMoved(header, header.translate(0f, -22f)))
        assertTrue(LaunchIntro.headerMoved(header, header.translate(10f, 0f)))
        assertTrue(LaunchIntro.headerMoved(header, rect(305f, 68.25f, 60f, 50f)))
    }

    /** Under reduced motion the header is part of the page and fades in with it. */
    @Test
    fun headerWordmarkIsHiddenOnlyWhileTheMovingOneStandsInForIt() {
        assertTrue(LaunchIntro.hidesHeader(Phase.Launch, Exit.Move))
        assertTrue(LaunchIntro.hidesHeader(Phase.Playing, Exit.Move))
        assertFalse(LaunchIntro.hidesHeader(Phase.Done, Exit.Move))
        for (phase in Phase.entries) assertFalse(LaunchIntro.hidesHeader(phase, Exit.Fade))
    }

    /** 20 / 48 at the default text size. */
    @Test
    fun moveScalesToTheHeaderWordmark() {
        val overlayHeight = 48f * 1.371f // Source Serif 4 line height
        val header = rect(24f, 80f, 33.2f, 20f * 1.371f)
        val move = LaunchIntro.wordmarkMove(header, overlayHeight)
        assertTrue(abs(move.scale - 20f / 48f) < 0.0001f)
    }

    /**
     * The header is measured while the page is still below its place, so the target is the resting place: the
     * measured center minus the rise.
     */
    @Test
    fun moveLandsWhereTheHeaderWillBeAtRest() {
        val header = rect(24f, 80f, 40f, 28f)
        val move = LaunchIntro.wordmarkMove(header, overlayHeight = 66f)
        assertEquals(Offset(44f, 94f - Timing.RISE), move.center)
    }

    /** At large font scales the header is taller, and the wordmark ends up larger, to match. */
    @Test
    fun moveFollowsTheFontScale() {
        val large = rect(24f, 80f, 40f, 28f)
        val accessibility = rect(24f, 80f, 90f, 62f)
        val small = LaunchIntro.wordmarkMove(large, overlayHeight = 66f).scale
        val big = LaunchIntro.wordmarkMove(accessibility, overlayHeight = 66f).scale
        assertTrue(big > small)
        assertTrue(abs(big * 66 - 62) < 0.0001)
    }
}
