package com.victorarana.leo.data.genai

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * The raw structure the on-device model fills in.
 * The correct answer is generated separately from the distractors so the app,
 * not the model, decides where it lands among the options.
 * Properties are generated in declaration order: the passage first, so nothing written about it
 * steers it, and the title last, so it sums up the passage.
 *
 * Kept in `rules.keep`: R8 would otherwise rename the properties the schema and the parser use.
 */
@Generable
data class GeneratedExercise(
    @param:Guide(description = PASSAGE_GUIDE)
    val passage: String,
    @param:Guide(description = QUESTION_GUIDE)
    val question: String,
    @param:Guide(description = CORRECT_ANSWER_GUIDE)
    val correctAnswer: String,
    @param:Guide(
        description = INCORRECT_ANSWERS_GUIDE,
        minItems = INCORRECT_ANSWER_COUNT,
        maxItems = INCORRECT_ANSWER_COUNT,
    )
    val incorrectAnswers: List<String>,
    @param:Guide(description = EXPLANATION_GUIDE)
    val explanation: String,
    @param:Guide(description = TITLE_GUIDE)
    val title: String,
) {
    companion object {
        // The descriptions are word for word the iOS `@Guide` ones. Constants, because the same text
        // describes the fields in the streaming request (see `MlKitLanguageModel`).
        const val PASSAGE_GUIDE =
            "The reading passage: a story or an informative text, written exactly as it would appear in a book. " +
                "Plain text, no headings, no markdown, no commentary about the text itself"
        const val QUESTION_GUIDE =
            "One question that tests the skill requested in the prompt and can only be answered by someone " +
                "who understood the passage"
        const val CORRECT_ANSWER_GUIDE =
            "The correct answer: a short phrase or sentence, at most 12 words, in your own words rather " +
                "than copied from the passage"
        const val INCORRECT_ANSWERS_GUIDE =
            "Three wrong answers that a careless reader might pick: each about the topic, the same length, " +
                "grammatical form and punctuation as the correct answer, and clearly wrong according to the passage"
        const val EXPLANATION_GUIDE =
            "One or two short sentences explaining why the correct answer is right, pointing to what the " +
                "passage says"
        const val TITLE_GUIDE = "A short, engaging title for the passage, at most 6 words"
        const val INCORRECT_ANSWER_COUNT = 3
    }
}

/**
 * The text sent as the user prompt for an exercise: [prompt] (the iOS one) plus, on Android only, the JSON
 * format that structured output would enforce. Streaming has no structured output (plan D3), so the schema
 * travels as text. Covered by the `exerciseFormat` golden.
 */
internal fun exerciseRequestPrompt(prompt: String): String =
    prompt +
        "\n\n" +
        """
        Answer with only a JSON object with these keys, in this order:
        "passage" (string): ${GeneratedExercise.PASSAGE_GUIDE}
        "question" (string): ${GeneratedExercise.QUESTION_GUIDE}
        "correctAnswer" (string): ${GeneratedExercise.CORRECT_ANSWER_GUIDE}
        "incorrectAnswers" (array of exactly ${GeneratedExercise.INCORRECT_ANSWER_COUNT} strings): ${GeneratedExercise.INCORRECT_ANSWERS_GUIDE}
        "explanation" (string): ${GeneratedExercise.EXPLANATION_GUIDE}
        "title" (string): ${GeneratedExercise.TITLE_GUIDE}
        """.trimIndent()
