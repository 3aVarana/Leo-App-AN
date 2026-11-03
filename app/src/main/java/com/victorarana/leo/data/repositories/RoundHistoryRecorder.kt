package com.victorarana.leo.data.repositories

import android.util.Log
import com.victorarana.leo.domain.RoundRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "RoundHistoryRecorder"

/**
 * Saves each finished round for the quiz's `onRoundFinished` (iOS: `RoundHistoryViewModel.record`, which phase 7
 * brings with the history screens and which then takes this over). Fire and forget, like iOS: the reader never
 * sees an error for history, it is logged. [scope] is the application's, so a save started at "See results" still
 * completes if the Activity finishes right after (phase-6 D6).
 */
class RoundHistoryRecorder(
    private val repository: RoundHistoryRepository,
    private val scope: CoroutineScope,
) {
    @Suppress("TooGenericExceptionCaught") // Any failure of the store is logged, never shown.
    fun record(round: RoundRecord) {
        scope.launch {
            try {
                repository.save(round)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Couldn't save the finished round", error)
            }
        }
    }
}
