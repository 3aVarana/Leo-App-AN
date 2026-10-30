package com.victorarana.leo.data.genai

import android.util.Log
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * [LanguageModel] over Gemini Nano through ML Kit's Prompt API (AICore).
 *
 * The one place, with the `@Generable` classes and the availability source, that imports `com.google.mlkit`.
 *
 * - Topic reviews use structured output (`@Generable` [TopicReview]).
 * - Exercises are streamed as text, because the API has no streaming variant of structured output
 *   (`generateContent(GenerateTypedContentRequest)` is the only typed call). The prompt asks for a JSON object
 *   whose keys are the [GeneratedExercise] properties, and [PartialJson] reads it back (plan D3).
 */
class MlKitLanguageModel(
    private val client: GenerativeModel = Generation.getClient(),
) : LanguageModel {
    override fun streamExercise(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): Flow<ExerciseSnapshot> =
        flow {
            val request = request(instructions, exerciseRequestPrompt(prompt), options)
            var text = ""
            client.generateContentStream(request).collect { response ->
                val chunk =
                    response.candidates
                        .firstOrNull()
                        ?.text
                        .orEmpty()
                text = PartialJson.append(text, chunk)
                emit(ExerciseSnapshot(passage = PartialJson.passage(text)))
            }
            Log.d(TAG, "Streamed ${text.length} characters")
            emit(
                ExerciseSnapshot(
                    passage = PartialJson.passage(text),
                    complete = PartialJson.generatedExercise(text),
                ),
            )
        }

    override suspend fun reviewTopic(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): TopicReview {
        val typed = generateTypedContentRequest(request(instructions, prompt, options), TopicReview::class)
        val review =
            try {
                client
                    .generateContent(typed)
                    .candidates
                    .firstOrNull()
                    ?.response
            } catch (e: GenAiException) {
                if (e.errorCode == GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR) {
                    // Unverified on a device: this code may also cover plain inference failures (qa.md).
                    Log.w(TAG, "Treated as refusal: error code ${e.errorCode}, ${e.message}")
                    throw ModelRefusedException(e)
                }
                Log.e(TAG, "Topic review failed: error code ${e.errorCode}, ${e.message}")
                throw e
            }
        return review ?: error("The model gave no topic review")
    }

    /** One request with the instructions as the system prompt, or in front of the prompt if it has none. */
    private suspend fun request(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ) = if (hasSystemPrompt()) {
        generateContentRequest(SystemInstruction(instructions), TextPart(prompt)) { configure(options) }
    } else {
        generateContentRequest(TextPart(instructions + "\n\n" + prompt)) { configure(options) }
    }

    @Volatile
    private var systemPromptAvailable: Boolean? = null

    /** Read once per instance: the features of the installed model don't change while the app runs. */
    private suspend fun hasSystemPrompt(): Boolean =
        systemPromptAvailable
            ?: (Feature.SYSTEM_PROMPT in client.getAvailableFeatures()).also { systemPromptAvailable = it }

    private fun com.google.mlkit.genai.prompt.GenerateContentRequest.Builder.configure(options: GenerationOptions) {
        temperature = options.temperature
        options.maxOutputTokens?.let { maxOutputTokens = it }
    }

    companion object {
        /** One client for the process: `Generation.getClient()` is created on first use. */
        val shared: MlKitLanguageModel by lazy { MlKitLanguageModel() }

        private const val TAG = "MlKitLanguageModel"
    }
}
