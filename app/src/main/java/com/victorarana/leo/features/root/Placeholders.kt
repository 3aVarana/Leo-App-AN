package com.victorarana.leo.features.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.leoScreenBackground
import com.victorarana.leo.features.theme.toTextStyle

// TEMPORARY. These stand in for screens later phases port, so the app shell and the navigation graph can be
// built, tested and run now. Verbatim text on purpose: no string resources, because each one is deleted when its
// real screen arrives.
//   HomePlaceholder        -> phase 6, the quiz screens (Welcome, Loading, Exercise, Result, Error)
//   HistoryPlaceholder     -> phase 7, the round history
//   OnboardingPlaceholder  -> phase 5, onboarding and the preferences editor

@Composable
private fun PlaceholderScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().leoScreenBackground().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}

@Composable
private fun PlaceholderText(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(text, modifier, style = LeoTextStyle.body.toTextStyle().copy(color = LeoTheme.colors.ink))
}

/** Phase 6 replaces this with the quiz screens. The link is how the graph is exercised until then. */
@Composable
fun HomePlaceholder(
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(modifier) {
        PlaceholderText("Home placeholder (phase 6)")
        PlaceholderText("Open history", Modifier.clickable(onClick = onHistory).padding(12.dp))
    }
}

/** Phase 7 replaces this with the round history. */
@Composable
fun HistoryPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(modifier) { PlaceholderText("History placeholder (phase 7)") }
}

/** Phase 5 replaces this with onboarding. */
@Composable
fun OnboardingPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(modifier) { PlaceholderText("Onboarding placeholder (phase 5)") }
}
