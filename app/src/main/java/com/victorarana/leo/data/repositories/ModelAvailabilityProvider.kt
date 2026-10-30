package com.victorarana.leo.data.repositories

import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelAvailability.Downloadable
import com.victorarana.leo.domain.ModelAvailability.Downloading
import kotlinx.coroutines.flow.StateFlow

/**
 * Tells whether the on-device model can be used right now.
 *
 * iOS reads a synchronous property that never needs downloading; here the status is a `suspend` call to
 * AICore and the model may have to be downloaded, so the answer is a flow that [refresh] and [download] update.
 */
interface ModelAvailabilityProvider {
    val availability: StateFlow<ModelAvailability>

    /** Asks AICore again. Call at launch and when the app comes back to the foreground. */
    suspend fun refresh()

    /** Downloads the model, reporting progress through [availability], and returns when it ends. */
    suspend fun download()

    /**
     * Plan Q2(a): checks the status and starts the download if the model is [Downloadable]. If AICore is already
     * downloading it (app restarted mid-download, or another app started it), `download()` attaches to that one
     * so the screen gets progress. To confirm on a device (qa.md).
     */
    suspend fun prepare() {
        refresh()
        downloadIfNeeded()
    }

    /** The second half of [prepare], for a caller that needs to know when the status has been read. */
    suspend fun downloadIfNeeded() {
        val status = availability.value
        if (status == Downloadable || status is Downloading) download()
    }
}
