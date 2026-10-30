package com.victorarana.leo.domain

/** Why the on-device model can't be used. */
enum class ModelUnavailableReason {
    /** The device can't run Gemini Nano (ML Kit reports `FeatureStatus.UNAVAILABLE`). */
    DEVICE_NOT_ELIGIBLE,

    /** The model download failed. */
    DOWNLOAD_FAILED,

    /** A reason this version of Leo doesn't know about, e.g. AICore failed to answer. */
    OTHER,
}

/**
 * Whether the on-device model can be used, so views don't depend on the ML Kit GenAI library.
 * Unlike Apple's, the model may need downloading first, so there are two more states than on iOS.
 */
sealed interface ModelAvailability {
    data object Available : ModelAvailability

    /** The device supports the model, but it isn't on the device yet. */
    data object Downloadable : ModelAvailability

    /** @property fraction 0 to 1, or null while the size isn't known yet. */
    data class Downloading(
        val fraction: Float?,
    ) : ModelAvailability

    data class Unavailable(
        val reason: ModelUnavailableReason,
    ) : ModelAvailability
}
