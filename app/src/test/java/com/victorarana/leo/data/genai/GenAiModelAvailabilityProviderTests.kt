package com.victorarana.leo.data.genai

import com.google.mlkit.genai.common.FeatureStatus
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Android-only: iOS reads a property, here the status and the download come from AICore. Robolectric, for `Log`. */
@RunWith(RobolectricTestRunner::class)
class GenAiModelAvailabilityProviderTests {
    private class FakeSource(
        var status: () -> Int = { FeatureStatus.AVAILABLE },
        var events: () -> Flow<ModelDownloadEvent> = { flowOf(ModelDownloadEvent.Completed) },
    ) : ModelStatusSource {
        var downloads = 0

        override suspend fun checkStatus(): Int = status()

        override fun download(): Flow<ModelDownloadEvent> {
            downloads++
            return events()
        }
    }

    @Test
    fun startsAsGettingReady() {
        assertEquals(
            ModelAvailability.Downloading(null),
            GenAiModelAvailabilityProvider(FakeSource()).availability.value,
        )
    }

    @Test
    fun refreshReadsTheStatusEveryTime() =
        runTest {
            var status = FeatureStatus.DOWNLOADABLE
            val provider = GenAiModelAvailabilityProvider(FakeSource(status = { status }))

            provider.refresh()
            assertEquals(ModelAvailability.Downloadable, provider.availability.value)

            status = FeatureStatus.AVAILABLE
            provider.refresh()
            assertEquals(ModelAvailability.Available, provider.availability.value)
        }

    @Test
    fun failingStatusCheckIsOther() =
        runTest {
            val provider =
                GenAiModelAvailabilityProvider(FakeSource(status = { throw IllegalStateException("AICore") }))

            provider.refresh()

            assertEquals(ModelAvailability.Unavailable(ModelUnavailableReason.OTHER), provider.availability.value)
        }

    @Test
    fun downloadReportsProgressThenRefreshes() =
        runTest {
            val seen = mutableListOf<ModelAvailability>()
            lateinit var provider: GenAiModelAvailabilityProvider
            val source =
                FakeSource(
                    status = { FeatureStatus.AVAILABLE },
                    events = {
                        flow {
                            emit(ModelDownloadEvent.Started(bytesToDownload = 200))
                            seen += provider.availability.value
                            emit(ModelDownloadEvent.Progress(bytesDownloaded = 50))
                            seen += provider.availability.value
                            emit(ModelDownloadEvent.Progress(bytesDownloaded = 500))
                            seen += provider.availability.value
                            emit(ModelDownloadEvent.Completed)
                        }
                    },
                )
            provider = GenAiModelAvailabilityProvider(source)

            provider.download()

            assertEquals(
                listOf(
                    ModelAvailability.Downloading(0f),
                    ModelAvailability.Downloading(0.25f),
                    ModelAvailability.Downloading(1f),
                ),
                seen,
            )
            assertEquals(ModelAvailability.Available, provider.availability.value)
        }

    @Test
    fun failedDownload() =
        runTest {
            val provider =
                GenAiModelAvailabilityProvider(
                    FakeSource(events = { flowOf(ModelDownloadEvent.Failed(IllegalStateException("network"))) }),
                )

            provider.download()

            assertEquals(
                ModelAvailability.Unavailable(ModelUnavailableReason.DOWNLOAD_FAILED),
                provider.availability.value,
            )
        }

    @Test
    fun failedDownloadStaysFailed() =
        runTest {
            val events =
                flowOf(
                    ModelDownloadEvent.Started(bytesToDownload = 100),
                    ModelDownloadEvent.Failed(IllegalStateException("network")),
                    ModelDownloadEvent.Progress(bytesDownloaded = 50),
                )
            val source = FakeSource(status = { FeatureStatus.AVAILABLE }, events = { events })
            val provider = GenAiModelAvailabilityProvider(source)

            provider.download()

            assertEquals(
                ModelAvailability.Unavailable(ModelUnavailableReason.DOWNLOAD_FAILED),
                provider.availability.value,
            )
        }

    @Test
    fun secondDownloadWhileOneRunsDoesNotStartAnother() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val source =
                FakeSource(
                    events = {
                        flow {
                            emit(ModelDownloadEvent.Started(bytesToDownload = 100))
                            emit(ModelDownloadEvent.Progress(bytesDownloaded = 50))
                            gate.await()
                            emit(ModelDownloadEvent.Completed)
                        }
                    },
                )
            val provider = GenAiModelAvailabilityProvider(source)

            val first = launch { provider.download() }
            advanceUntilIdle()
            provider.download()

            assertEquals(1, source.downloads)
            assertEquals(ModelAvailability.Downloading(0.5f), provider.availability.value)
            gate.complete(Unit)
            first.join()
            assertEquals(ModelAvailability.Available, provider.availability.value)
        }

    @Test
    fun downloadFlowThatThrowsIsAFailedDownload() =
        runTest {
            val provider =
                GenAiModelAvailabilityProvider(FakeSource(events = { flow { throw IllegalStateException("boom") } }))

            provider.download()

            assertEquals(
                ModelAvailability.Unavailable(ModelUnavailableReason.DOWNLOAD_FAILED),
                provider.availability.value,
            )
        }

    @Test
    fun prepareDownloadsOnlyADownloadableModel() =
        runTest {
            val downloadable = FakeSource(status = { FeatureStatus.DOWNLOADABLE })
            GenAiModelAvailabilityProvider(downloadable).prepare()
            assertEquals(1, downloadable.downloads)

            val available = FakeSource(status = { FeatureStatus.AVAILABLE })
            GenAiModelAvailabilityProvider(available).prepare()
            assertEquals(0, available.downloads)

            val unsupported = FakeSource(status = { FeatureStatus.UNAVAILABLE })
            val provider = GenAiModelAvailabilityProvider(unsupported)
            provider.prepare()
            assertEquals(0, unsupported.downloads)
            assertEquals(
                ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE),
                provider.availability.value,
            )
        }

    @Test
    fun downloadFlowThatEndsWithoutCompletedStillRefreshes() =
        runTest {
            val provider =
                GenAiModelAvailabilityProvider(
                    FakeSource(
                        status = { FeatureStatus.AVAILABLE },
                        events = { flowOf(ModelDownloadEvent.Started(bytesToDownload = 10)) },
                    ),
                )

            provider.download()

            assertEquals(ModelAvailability.Available, provider.availability.value)
        }

    @Test
    fun prepareAttachesToADownloadAlreadyInProgress() =
        runTest {
            val source = FakeSource(status = { FeatureStatus.DOWNLOADING })

            GenAiModelAvailabilityProvider(source).prepare()

            assertEquals(1, source.downloads)
        }
}
