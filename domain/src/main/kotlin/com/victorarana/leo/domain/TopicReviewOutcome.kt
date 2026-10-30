package com.victorarana.leo.domain

/** The verdict on a topic the reader wants to add. */
sealed interface TopicReviewOutcome {
    /** The cleaned-up phrase to store and show. */
    data class Accepted(
        val name: String,
    ) : TopicReviewOutcome

    /** Why the topic can't be added, for the reader. */
    data class Rejected(
        val reason: String,
    ) : TopicReviewOutcome
}
