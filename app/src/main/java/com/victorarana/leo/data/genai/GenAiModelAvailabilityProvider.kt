package com.victorarana.leo.data.genai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.victorarana.leo.data.repositories.ModelAvailabilityProvider
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** What AICore reports while it downloads the model, without ML Kit types, so the provider can be tested. */
sealed interface ModelDownloadEvent {
    data class Started(
        val bytesToDownload: Long,
    ) : ModelDownloadEvent

    data class Progress(
        val bytesDownloaded: Long,
    ) : ModelDownloadEvent

    data object Completed : ModelDownloadEvent

    data class Failed(
        val cause: Throwable,
    ) : ModelDownloadEvent
}

/** The two calls the provider makes on ML Kit's `GenerativeModel`. */
interface ModelStatusSource {
    /** A `FeatureStatus` constant. */
    suspend fun checkStatus(): Int

    fun download(): Flow<ModelDownloadEvent>
}

/** [ModelStatusSource] over the shared Gemini Nano client. */
class MlKitModelStatusSource(
    private val client: GenerativeModel = Generation.getClient(),
) : ModelStatusSource {
    override suspend fun checkStatus(): Int = client.checkStatus()

    override fun download(): Flow<ModelDownloadEvent> =
        client.download().map { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> ModelDownloadEvent.Started(status.bytesToDownload)
                is DownloadStatus.DownloadProgress -> ModelDownloadEvent.Progress(status.totalBytesDownloaded)
                is DownloadStatus.DownloadCompleted -> ModelDownloadEvent.Completed
                is DownloadStatus.DownloadFailed -> ModelDownloadEvent.Failed(status.e)
            }
        }
}

/** Gemini Nano's availability, from AICore. See [ModelAvailabilityProvider] for how it differs from iOS. */
class GenAiModelAvailabilityProvider(
    private val source: ModelStatusSource = MlKitModelStatusSource(),
) : ModelAvailabilityProvider {
    // Until the first check the screen shows an indeterminate "getting ready" state rather than an error.
    private val state = MutableStateFlow<ModelAvailability>(ModelAvailability.Downloading(fraction = null))
    override val availability: StateFlow<ModelAvailability> = state.asStateFlow()

    override suspend fun refresh() {
        state.value =
            try {
                modelAvailability(source.checkStatus())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Exception,
            ) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "checkStatus failed: $error")
                ModelAvailability.Unavailable(ModelUnavailableReason.OTHER)
            }
    }

    override suspend fun download() {
        state.value = ModelAvailability.Downloading(fraction = null)
        var total = 0L
        try {
            source.download().collect { event ->
                when (event) {
                    is ModelDownloadEvent.Started -> {
                        total = event.bytesToDownload
                        state.value = ModelAvailability.Downloading(fraction = if (total > 0) 0f else null)
                    }
                    is ModelDownloadEvent.Progress ->
                        if (total > 0) {
                            val fraction = event.bytesDownloaded.toFloat() / total
                            state.value = ModelAvailability.Downloading(fraction.coerceIn(0f, 1f))
                        }
                    // Read again below, once the flow has ended.
                    ModelDownloadEvent.Completed -> Unit
                    is ModelDownloadEvent.Failed -> fail(event.cause)
                }
            }
            // Whatever the flow ended with, AICore has the last word.
            if (state.value !is ModelAvailability.Unavailable) refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            // ML Kit may report a cancelled download as an exception; cancellation wins.
            currentCoroutineContext().ensureActive()
            fail(error)
        }
    }

    private fun fail(cause: Throwable) {
        Log.e(TAG, "Model download failed: $cause")
        state.value = ModelAvailability.Unavailable(ModelUnavailableReason.DOWNLOAD_FAILED)
    }

    private companion object {
        const val TAG = "GenAiModelAvailability"
    }
}
