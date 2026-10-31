package com.victorarana.leo.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.preferences.OnboardingScreen
import com.victorarana.leo.features.preferences.PreferencesEditorViewModel

/** `OnboardingScreen` with its back stack and selection held here, as `RootScreen` holds them in the app. */
@Composable
fun OnboardingScreenHost(editor: PreferencesEditorViewModel) {
    var selection by rememberSaveable { mutableStateOf<AgeGroup?>(null) }
    OnboardingScreen(
        editor = editor,
        navController = rememberNavController(),
        selection = selection,
        onSelect = { selection = it },
    )
}
