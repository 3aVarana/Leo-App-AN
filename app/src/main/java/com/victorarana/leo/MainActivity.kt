package com.victorarana.leo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.victorarana.leo.features.root.RootScreen
import com.victorarana.leo.features.root.RootViewModel
import com.victorarana.leo.features.theme.LeoTheme

class MainActivity : ComponentActivity() {
    // Built from the application's container; it survives rotation, like the iOS `@State` root.
    private val viewModel: RootViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as LeoApplication).container
                RootViewModel(container.preferences, container.availability)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Swaps the starting theme (the system splash: the ground) for the app theme; must precede super.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeoTheme {
                RootScreen(viewModel)
            }
        }
    }
}
