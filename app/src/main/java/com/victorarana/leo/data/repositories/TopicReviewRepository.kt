package com.victorarana.leo.data.repositories

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicReviewOutcome

/** Checks that a topic the reader typed suits them, and tidies up its wording. */
interface TopicReviewRepository {
    suspend fun review(
        text: String,
        group: AgeGroup,
    ): TopicReviewOutcome
}
