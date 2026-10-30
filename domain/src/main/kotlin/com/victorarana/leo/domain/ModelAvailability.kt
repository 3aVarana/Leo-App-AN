package com.victorarana.leo.domain

/** Why the on-device model can't be used. */
enum class ModelUnavailableReason {
    DEVICE_NOT_ELIGIBLE,
    APPLE_INTELLIGENCE_NOT_ENABLED,
    MODEL_NOT_READY,

    /** A reason this version of Leo doesn't know about. */
    OTHER,
}

/** Whether the on-device model can be used, so views don't depend on the ML Kit GenAI library. */
sealed interface ModelAvailability {
    data object Available : ModelAvailability

    data class Unavailable(
        val reason: ModelUnavailableReason,
    ) : ModelAvailability
}
