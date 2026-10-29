package com.victorarana.leo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.leoScreenBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeoTheme {
                EmptyScreen()
            }
        }
    }
}

/** Phase 0 placeholder: the Broadsheet ground and nothing else. Screens arrive from phase 4. */
@Composable
private fun EmptyScreen() {
    Box(Modifier.fillMaxSize().leoScreenBackground())
}
