package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.TopicReviewRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicReviewOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A fake [TopicReviewRepository] that records each call. Calls are answered from `results` in
 * order while there are any; after that, each call waits until the test resumes it, so tests
 * control when a review finishes.
 */
class StubTopicReviewRepository(
    results: List<Result<TopicReviewOutcome>> = emptyList(),
    /**
     * Keeps waiting calls waiting when their coroutine is cancelled, like a review that
     * doesn't check for cancellation, so a late result can be delivered.
     */
    private val ignoresCancellation: Boolean = false,
) : TopicReviewRepository {
    data class Call(
        val text: String,
        val group: AgeGroup,
    )

    private val _calls = mutableListOf<Call>()
    val calls: List<Call> get() = _calls

    /** Waiting calls that were cancelled, which then threw [CancellationException]. */
    var cancelledCount = 0
        private set

    private val results = results.toMutableList()
    private val waiting = mutableListOf<CompletableDeferred<TopicReviewOutcome>>()

    val waitingCount: Int get() = waiting.size

    override suspend fun review(
        text: String,
        group: AgeGroup,
    ): TopicReviewOutcome {
        _calls += Call(text, group)
        if (results.isNotEmpty()) return results.removeFirst().getOrThrow()

        val pending = CompletableDeferred<TopicReviewOutcome>()
        waiting += pending
        try {
            return if (ignoresCancellation) withContext(NonCancellable) { pending.await() } else pending.await()
        } catch (cancelled: CancellationException) {
            if (waiting.remove(pending)) cancelledCount++
            throw cancelled
        }
    }

    /** Answers the oldest waiting call. */
    fun resume(result: Result<TopicReviewOutcome>) {
        check(waiting.isNotEmpty()) { "No call is waiting" }
        val pending = waiting.removeFirst()
        result.fold(pending::complete, pending::completeExceptionally)
    }

    fun resume(outcome: TopicReviewOutcome) = resume(Result.success(outcome))
}
