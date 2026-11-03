package com.victorarana.leo.data.genai

import com.victorarana.leo.data.repositories.ModelAvailabilityProvider
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Debug builds only: an availability that doesn't ask AICore, for the stub model's walkthroughs. */
class StubModelAvailabilityProvider(
    private val initial: ModelAvailability,
) : ModelAvailabilityProvider {
    private val state = MutableStateFlow(initial)
    override val availability: StateFlow<ModelAvailability> = state.asStateFlow()

    override suspend fun refresh() = Unit

    /** A download of about five seconds, in ten steps, that ends `available`. */
    override suspend fun download() {
        repeat(STEPS) { step ->
            state.value = ModelAvailability.Downloading(fraction = step.toFloat() / STEPS)
            delay(STEP_MILLIS)
        }
        state.value = ModelAvailability.Available
    }

    companion object {
        private const val STEPS = 10
        private const val STEP_MILLIS = 500L

        fun named(name: String): StubModelAvailabilityProvider =
            StubModelAvailabilityProvider(
                when (name) {
                    "downloadable" -> ModelAvailability.Downloadable
                    "notEligible" -> ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE)
                    else -> ModelAvailability.Available
                },
            )
    }
}
