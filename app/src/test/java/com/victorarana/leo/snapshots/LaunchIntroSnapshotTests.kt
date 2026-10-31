package com.victorarana.leo.snapshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.components.DotState
import com.victorarana.leo.features.components.ProgressDots
import com.victorarana.leo.features.root.LaunchIntro
import com.victorarana.leo.features.root.LaunchIntroHost
import com.victorarana.leo.features.root.launchWordmarkTarget
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LocalReduceMotion
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.toTextStyle
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A stand-in for Welcome (phase 6), which re-records these against the real screen: a header with the wordmark
 * marked as the launch target, so the intro has somewhere to move to.
 */
@Composable
private fun TestPage() {
    Column(Modifier.leoReadableWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "Leo",
                modifier = Modifier.launchWordmarkTarget(),
                style = LeoTextStyle.wordmark.toTextStyle().copy(color = LeoTheme.colors.ink),
            )
        }
        ProgressDots(
            states = List(6) { DotState.Upcoming },
            currentIndex = null,
            alignment = Alignment.Start,
        )
        BasicText(
            text = "A test page with a header wordmark",
            style = LeoTextStyle.body.toTextStyle().copy(color = LeoTheme.colors.ink),
        )
    }
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
