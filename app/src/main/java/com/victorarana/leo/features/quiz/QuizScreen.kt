package com.victorarana.leo.features.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorarana.leo.domain.ReaderPreferences

private const val PHASE_FADE_MILLIS = 300

/**
 * The quiz as the Home destination shows it: one screen for the current phase, cross-fading as the phase changes
 * (iOS `RootView.content` and `.animation(.default, value: quiz.phase)`).
 *
 * `AnimatedContent` is keyed by the phase, not by the whole state: while a screen fades out it keeps the state it
 * had, so Exercise never draws without its exercise, and a change inside a phase (an option picked) recomposes the
 * screen in place instead of cross-fading.
 *
 * @param onHistory `null` hides the History buttons: the current age group has no saved round to show yet.
 * @param onReview Opens Review at this position in the round's misses.
 */
@Suppress("CyclomaticComplexMethod") // One branch per phase, like the iOS switch, each with its guarded actions.
@Composable
fun QuizScreen(
    quiz: QuizViewModel,
    preferences: ReaderPreferences,
    onSettings: () -> Unit,
    onReview: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onHistory: (() -> Unit)? = null,
) {
    val latest by quiz.uiState.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = latest,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(PHASE_FADE_MILLIS)) togetherWith fadeOut(tween(PHASE_FADE_MILLIS)) },
        contentKey = { it.phase },
        label = "quiz-phase",
    ) { shown ->
        // A screen that is fading out must not act: a second tap on Start would otherwise prepare another round.
        val isCurrent = shown.phase == latest.phase

        fun guarded(action: () -> Unit): () -> Unit = { if (isCurrent) action() }
        when (shown.phase) {
            QuizViewModel.Phase.Welcome ->
                WelcomeScreen(
                    ageGroup = preferences.ageGroup,
                    exerciseCount = ReaderPreferences.clampedExerciseCount(preferences.exercisesPerRound),
                    topicNames = shown.plannedTopicNames,
                    onStart = guarded(quiz::start),
                    onSettings = guarded(onSettings),
                    onHistory = onHistory?.let { guarded(it) },
                )
            QuizViewModel.Phase.Loading ->
                LoadingScreen(
                    index = shown.currentIndex,
                    dots = shown.dots,
                    topicName = shown.writingTopicName,
                    ageGroup = shown.roundAgeGroup ?: preferences.ageGroup,
                )
            QuizViewModel.Phase.Answering ->
                shown.currentExercise?.let { exercise ->
                    // A new screen, with its own scroll position and timer, for each exercise.
                    key(exercise.id) {
                        ExerciseScreen(
                            state = shown,
                            exercise = exercise,
                            isReadingTimed = preferences.isReadingTimerOn,
                            onFinishReading = guarded(quiz::finishReading),
                            onSelect = { option -> if (isCurrent) quiz.select(option) },
                            onNext = guarded(quiz::next),
                        )
                    }
                }
            QuizViewModel.Phase.Finished ->
                ResultScreen(
                    answers = shown.answers,
                    ageGroup = shown.roundAgeGroup ?: preferences.ageGroup,
                    onRestart = guarded(quiz::start),
                    onSettings = guarded(onSettings),
                    onReview = { index -> if (isCurrent) onReview(index) },
                    onHistory = onHistory?.let { guarded(it) },
                    historyAgeGroup = preferences.ageGroup,
                )
            QuizViewModel.Phase.Failed ->
                GenerationFailedScreen(
                    index = shown.currentIndex,
                    dots = shown.dots,
                    topicName = shown.failedTopicName,
                    onRetry = guarded(quiz::retry),
                    onTryDifferentTopic = guarded(quiz::retryWithDifferentTopics),
                )
        }
    }
}
