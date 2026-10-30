package com.victorarana.leo.data.genai

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * The model's review of a topic the reader wants to add.
 * Filled in declaration order, so the verdict comes before the phrase and the reason.
 * Kept in `rules.keep`, like [GeneratedExercise].
 */
@Generable
data class TopicReview(
    @param:Guide(description = "Whether the topic is safe and suitable for reading texts for the given reader age")
    val isSuitable: Boolean,
    @param:Guide(
        description =
            "If suitable, the reader's topic tidied up as a short phrase of at most 6 words: fix spelling and " +
                "capitalization, keep the reader's own words and meaning, in the same language the reader typed " +
                "it in; otherwise empty",
    )
    val topic: String,
    @param:Guide(
        description =
            "If not suitable, one short friendly sentence for the reader explaining why, in the language the " +
                "instructions ask for; otherwise empty",
    )
    val reason: String,
)
