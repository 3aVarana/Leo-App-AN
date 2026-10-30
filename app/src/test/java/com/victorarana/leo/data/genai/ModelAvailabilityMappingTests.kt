package com.victorarana.leo.data.genai

import com.google.mlkit.genai.common.FeatureStatus
import com.victorarana.leo.domain.ModelAvailability
import com.victorarana.leo.domain.ModelUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One case per `FeatureStatus`, as iOS has one per `SystemLanguageModel.Availability`.
 * `OTHER` is the `@unknown default`.
 */
class ModelAvailabilityMappingTests {
    @Test
    fun available() {
        assertEquals(ModelAvailability.Available, modelAvailability(FeatureStatus.AVAILABLE))
    }

    @Test
    fun downloadable() {
        assertEquals(ModelAvailability.Downloadable, modelAvailability(FeatureStatus.DOWNLOADABLE))
    }

    @Test
    fun downloading() {
        assertEquals(ModelAvailability.Downloading(fraction = null), modelAvailability(FeatureStatus.DOWNLOADING))
    }

    @Test
    fun unavailableMeansDeviceNotEligible() {
        assertEquals(
            ModelAvailability.Unavailable(ModelUnavailableReason.DEVICE_NOT_ELIGIBLE),
            modelAvailability(FeatureStatus.UNAVAILABLE),
        )
    }

    @Test
    fun unknownStatusIsOther() {
        assertEquals(ModelAvailability.Unavailable(ModelUnavailableReason.OTHER), modelAvailability(-12345))
    }
}
