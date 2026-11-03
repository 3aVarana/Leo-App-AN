package com.victorarana.leo.features.quiz

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.theme.LeoTextStyle

/** The passage text for this group: larger with more leading for younger readers. (`AgeGroup+Typography.swift`.) */
val AgeGroup.passageStyle: LeoTextStyle
    get() =
        when (this) {
            AgeGroup.SIX -> LeoTextStyle(size = 21f, lineHeight = 34f)
            AgeGroup.NINE, AgeGroup.TWELVE -> LeoTextStyle(size = 18f, lineHeight = 29f)
            AgeGroup.FIFTEEN, AgeGroup.ADULT -> LeoTextStyle(size = 17f, lineHeight = 27f)
        }
