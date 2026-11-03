package com.victorarana.leo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.victorarana.leo.features.quiz.QuizViewModel
import com.victorarana.leo.features.root.RootScreen
import com.victorarana.leo.features.root.RootViewModel
import com.victorarana.leo.features.theme.LeoTheme

class MainActivity : ComponentActivity() {
    // The quiz is a ViewModel in the Activity's store, owned by the root like on iOS (`RootViewModel.quiz`): it
    // survives rotation, and clearing it when the Activity finishes cancels the generation (phase-6 D1).
    private val quiz: QuizViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as LeoApplication).container
                QuizViewModel(
                    makeRepository = container::makeExerciseRepository,
                    onRoundFinished = container.roundHistoryRecorder::record,
                )
            }
        }
    }

    // Built from the application's container; it survives rotation, like the iOS `@State` root.
    private val viewModel: RootViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as LeoApplication).container
                RootViewModel(
                    container.preferences,
                    container.availability,
                    container.topicReviews,
                    container.topicNames,
                    quiz,
                )
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
