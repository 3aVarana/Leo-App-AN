package com.victorarana.leo.snapshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.quiz.WelcomeScreen
import com.victorarana.leo.features.root.LaunchIntro
import com.victorarana.leo.features.root.LaunchIntroHost
import com.victorarana.leo.features.theme.LocalReduceMotion
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real Welcome screen, whose header wordmark is the launch target (the stand-in page of phase 4 is gone). */
@Composable
private fun TestPage() {
    WelcomeScreen(
        ageGroup = AgeGroup.NINE,
        exerciseCount = 6,
        topicNames = listOf("Volcanoes", "The solar system", "Pirates and treasure", "Inventors", "Rainforests"),
        onStart = {},
        onSettings = {},
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class LaunchIntroSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    /** The first frame, for comparing by eye against the launch screen: a centered "Leo" on the ground. */
    @Test
    fun launchFrame() =
        composeRule.assertViewSnapshot(this::class, "launch") {
            LaunchIntroHost(initialPhase = LaunchIntro.Phase.Launch, plays = false) { TestPage() }
        }

    /**
     * With reduced motion the header "Leo" is part of the page, so it's there as soon as the page is, not hidden
     * until the intro ends. Frozen frames show the end of the fade.
     */
    @Test
    fun reducedMotionShowsTheHeaderWithThePage() =
        composeRule.assertViewSnapshot(this::class, "reduced-motion") {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                LaunchIntroHost(initialPhase = LaunchIntro.Phase.Playing, plays = false) { TestPage() }
            }
        }

    /** The last frame is the page as it is without the intro. */
    @Test
    fun restFrame() =
        composeRule.assertViewSnapshot(this::class, "rest") {
            LaunchIntroHost(initialPhase = LaunchIntro.Phase.Done, plays = false) { TestPage() }
        }
}
