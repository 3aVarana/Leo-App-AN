package com.victorarana.leo.data.genai

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicReviewOutcome

/** Checks with the on-device model that a custom topic suits the reader, and tidies up its wording. */
class TopicValidator internal constructor(
    val language: ContentLanguage,
    private val model: LanguageModel,
) {
    /**
     * Reviews [text] for readers in [group].
     *
     * @param genericRejection The localized text for a topic with no usable reason (the string is a resource,
     * and the domain and data layers carry no `Context`).
     */
    suspend fun review(
        text: String,
        group: AgeGroup,
        genericRejection: String,
    ): TopicReviewOutcome {
        val instructions =
            language.withLocaleInstruction(
                "You review topics a reader wants to practice reading about. " +
                    "Each topic becomes short reading texts written for ${group.promptAudience}, at their level: " +
                    "a story, or an explanation of facts. So everyday, school, real-world and imaginative topics " +
                    "all work, and a topic doesn't need to be realistic. " +
                    "Accept a topic if it is appropriate reading material for ${group.promptAudience}. " +
                    "Write the topic phrase and the reason in ${language.name}.",
            )
        return try {
            val review =
                model.reviewTopic(
                    instructions = instructions,
                    prompt = "Topic: $text\nWrite the reason in ${language.name}.",
                    options = GenerationOptions(temperature = TEMPERATURE),
                )
            outcome(review, genericRejection)
        } catch (_: ModelRefusedException) {
            TopicReviewOutcome.Rejected(genericRejection)
        }
    }

    companion object {
        private const val TEMPERATURE = 0.2f

        /** Turns the model's review into what the reader sees. */
        fun outcome(
            review: TopicReview,
            genericRejection: String,
        ): TopicReviewOutcome {
            val topic = review.topic.trim { it.isWhitespace() || it.isPunctuation() }
            if (!review.isSuitable || topic.isEmpty()) {
                val reason = review.reason.trim()
                return TopicReviewOutcome.Rejected(reason.ifEmpty { genericRejection })
            }
            return TopicReviewOutcome.Accepted(topic)
        }

        /** Unicode punctuation (Swift's `CharacterSet.punctuationCharacters`: general categories P*). */
        private fun Char.isPunctuation(): Boolean =
            when (Character.getType(this).toByte()) {
                Character.CONNECTOR_PUNCTUATION,
                Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION,
                Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION,
                Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION,
                -> true
                else -> false
            }
    }
}
