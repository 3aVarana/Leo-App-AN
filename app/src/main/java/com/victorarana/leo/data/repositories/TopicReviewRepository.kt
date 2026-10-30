package com.victorarana.leo.data.repositories

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicReviewOutcome

/** Checks that a topic the reader typed suits them, and tidies up its wording. */
interface TopicReviewRepository {
    /**
     * A model refusal is a [TopicReviewOutcome.Rejected] with the generic reason, not an error.
     *
     * @throws Exception any other failure of the model (Swift's `async throws`); callers must catch it.
     */
    suspend fun review(
        text: String,
        group: AgeGroup,
    ): TopicReviewOutcome
}
