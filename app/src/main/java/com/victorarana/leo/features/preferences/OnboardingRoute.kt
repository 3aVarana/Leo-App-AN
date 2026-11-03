package com.victorarana.leo.features.preferences

import com.victorarana.leo.domain.AgeGroup
import kotlinx.serialization.Serializable

/**
 * The two screens of onboarding's own back stack (iOS: the `NavigationStack` inside `OnboardingView`). Separate
 * from `LeoRoute`: onboarding is a gate, not a destination of the app's graph.
 */
@Serializable
sealed interface OnboardingRoute {
    @Serializable
    data object AgeGroups : OnboardingRoute

    /**
     * Carries the picked age group, so a Topics screen restored after process death (the nested back stack is
     * saved, the editor is new) can put it back into the editor's draft.
     */
    @Serializable
    data class Topics(
        /** [AgeGroup.rawValue]: an enum argument would need `@Keep` (lint), which the pure `:domain` can't have. */
        val rawAgeGroup: String,
    ) : OnboardingRoute {
        constructor(ageGroup: AgeGroup) : this(ageGroup.rawValue)

        val ageGroup: AgeGroup get() =
            checkNotNull(
                AgeGroup.fromRawValue(rawAgeGroup),
            ) { "Unknown age group $rawAgeGroup" }
    }
}
