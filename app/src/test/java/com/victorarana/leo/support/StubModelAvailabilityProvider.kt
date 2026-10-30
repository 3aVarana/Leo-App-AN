package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.ModelAvailabilityProvider
import com.victorarana.leo.domain.ModelAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [ModelAvailabilityProvider] whose answer the test sets, and that counts refreshes and downloads. */
class StubModelAvailabilityProvider(
    availability: ModelAvailability = ModelAvailability.Available,
) : ModelAvailabilityProvider {
    private val state = MutableStateFlow(availability)
    override val availability: StateFlow<ModelAvailability> get() = state

    var refreshCount = 0
        private set
    var downloadCount = 0
        private set

    fun set(availability: ModelAvailability) {
        state.value = availability
    }

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun download() {
        downloadCount++
    }
}
