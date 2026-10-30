package com.victorarana.leo.data.genai

import com.google.mlkit.genai.common.FeatureStatus
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason

/** The port of `ModelAvailability.init(_: SystemLanguageModel.Availability)`, from ML Kit's `FeatureStatus`. */
fun modelAvailability(featureStatus: Int): ModelAvailability =
    when (featureStatus) {
        FeatureStatus.AVAILABLE -> ModelAvailability.Available
        FeatureStatus.DOWNLOADABLE -> ModelAvailability.Downloadable
        // The size isn't known until the download reports it.
        FeatureStatus.DOWNLOADING -> ModelAvailability.Downloading(fraction = null)
        FeatureStatus.UNAVAILABLE -> ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE)
        else -> ModelAvailability.Unavailable(ModelUnavailableReason.OTHER)
    }
