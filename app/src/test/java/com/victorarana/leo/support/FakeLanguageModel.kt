package com.victorarana.leo.support

import com.victorarana.leo.data.genai.ExerciseSnapshot
import com.victorarana.leo.data.genai.GeneratedExercise
import com.victorarana.leo.data.genai.GenerationOptions
import com.victorarana.leo.data.genai.LanguageModel
import com.victorarana.leo.data.genai.TopicReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [LanguageModel] that answers from a script and records every request. Android-only: iOS has no seam
 * below `ExerciseGenerator`, so its attempt loop is only covered by the live-model tests.
 *
 * Each call to [streamExercise] takes the next entry of [streams]; each call to [reviewTopic] the next of
 * [reviews]. Running out of entries fails the test.
 */
class FakeLanguageModel(
    streams: List<() -> Flow<ExerciseSnapshot>> = emptyList(),
    reviews: List<() -> TopicReview> = emptyList(),
) : LanguageModel {
    data class Request(
        val instructions: String,
        val prompt: String,
        val options: GenerationOptions,
    )

    private val streams = streams.toMutableList()
    private val reviews = reviews.toMutableList()

    private val _requests = mutableListOf<Request>()
    val requests: List<Request> get() = _requests

    /** How many snapshots of the streams were collected, over all calls. */
    var emittedSnapshots = 0
        private set

    override fun streamExercise(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): Flow<ExerciseSnapshot> {
        _requests += Request(instructions, prompt, options)
        check(streams.isNotEmpty()) { "No scripted stream for request ${_requests.size}" }
        val stream = streams.removeFirst()
        return flow {
            stream().collect {
                emittedSnapshots++
                emit(it)
            }
        }
    }

    override suspend fun reviewTopic(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): TopicReview {
        _requests += Request(instructions, prompt, options)
        check(reviews.isNotEmpty()) { "No scripted review for request ${_requests.size}" }
        return reviews.removeFirst()()
    }

    companion object {
        /** A stream that writes [exercise] in a few steps and ends with it. */
        fun completing(exercise: GeneratedExercise): () -> Flow<ExerciseSnapshot> =
            {
                flow {
                    emit(ExerciseSnapshot(passage = null))
                    emit(ExerciseSnapshot(passage = exercise.passage))
                    emit(ExerciseSnapshot(passage = exercise.passage, complete = exercise))
                }
            }

        /** A stream that fails with [error] after its first step. */
        fun failing(error: Throwable): () -> Flow<ExerciseSnapshot> =
            {
                flow {
                    emit(ExerciseSnapshot(passage = null))
                    throw error
                }
            }

        /** A stream that never ends and writes one more word of passage per step. */
        fun runaway(): () -> Flow<ExerciseSnapshot> =
            {
                flow {
                    var written = 0
                    while (true) {
                        written++
                        emit(
                            ExerciseSnapshot(
                                passage =
                                    com.victorarana.leo.domain
                                        .words(written),
                            ),
                        )
                    }
                }
            }
    }
}
