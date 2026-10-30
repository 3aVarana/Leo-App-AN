package com.victorarana.leo.support

import com.victorarana.leo.data.repositories.ModelAvailabilityProvider
import com.victorarana.leo.domain.ModelAvailability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [ModelAvailabilityProvider] whose answer the test sets, and that counts refreshes and downloads. */
class StubModelAvailabilityProvider(
    availability: ModelAvailability = ModelAvailability.Available,
    /** When set, [refresh] waits for it, like AICore taking its time to answer. */
    private val refreshGate: CompletableDeferred<Unit>? = null,
    /** What the late answer says: set into [availability] when the gate opens. */
    private val refreshResult: ModelAvailability? = null,
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
        refreshGate?.await()
        refreshResult?.let { state.value = it }
    }

    override suspend fun download() {
        downloadCount++
    }
}
