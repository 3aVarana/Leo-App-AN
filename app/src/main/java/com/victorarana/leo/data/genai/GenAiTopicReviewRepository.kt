package com.victorarana.leo.data.genai

import com.victorarana.leo.data.repositories.TopicReviewRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicReviewOutcome

/**
 * Reviews topics with the on-device model.
 *
 * @param genericRejection The localized "That topic isn't a good fit..." text, read when needed so a change of
 * the app language applies (the container passes `context.getString(R.string.topic_review_generic_rejection)`).
 * @param language Resolved on every call, so a change to the device language applies to the next review.
 */
class GenAiTopicReviewRepository(
    private val genericRejection: () -> String,
    private val model: LanguageModel,
    private val language: () -> ContentLanguage = { ContentLanguage.current() },
) : TopicReviewRepository {
    override suspend fun review(
        text: String,
        group: AgeGroup,
    ): TopicReviewOutcome = TopicValidator(language(), model).review(text, group, genericRejection())
}
