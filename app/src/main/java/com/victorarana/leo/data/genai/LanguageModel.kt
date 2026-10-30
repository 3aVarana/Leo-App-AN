package com.victorarana.leo.data.genai

import kotlinx.coroutines.flow.Flow

/** How the model samples, the port of FoundationModels' `GenerationOptions`. */
data class GenerationOptions(
    val temperature: Float,
    val maxOutputTokens: Int? = null,
)

/**
 * One step of a streamed exercise: the passage written so far, and on the last step the whole exercise.
 * Stands in for FoundationModels' partially generated snapshots, which carry typed partial content.
 */
data class ExerciseSnapshot(
    val passage: String?,
    val complete: GeneratedExercise? = null,
)

/**
 * The seam between the generators and ML Kit, so the attempt loop can be tested without a model.
 * `MlKitLanguageModel` is the only implementation that talks to Gemini Nano.
 */
interface LanguageModel {
    /**
     * Starts a fresh request for an exercise and streams it. Cancelling the collector abandons the request.
     */
    fun streamExercise(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): Flow<ExerciseSnapshot>

    /**
     * Asks for a topic review in one go.
     *
     * @throws ModelRefusedException when the model's safety filters or a refusal stop the answer.
     */
    suspend fun reviewTopic(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): TopicReview
}

/** The model declined to answer (guardrail violation or refusal on iOS). */
class ModelRefusedException(
    cause: Throwable? = null,
) : Exception("The model refused to answer", cause)
