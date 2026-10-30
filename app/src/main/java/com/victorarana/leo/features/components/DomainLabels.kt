package com.victorarana.leo.features.components

import android.content.Context
import androidx.annotation.StringRes
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill

// The localized labels of domain types. The domain module has no resources, so the `displayName`,
// `shortName` and `summary` properties of the Swift `AgeGroup` and `ComprehensionSkill` live here.

/** The name in a sentence, for example "9 to 11". */
@get:StringRes
val AgeGroup.displayNameRes: Int
    get() =
        when (this) {
            AgeGroup.SIX -> R.string.age_group_display_name_six
            AgeGroup.NINE -> R.string.age_group_display_name_nine
            AgeGroup.TWELVE -> R.string.age_group_display_name_twelve
            AgeGroup.FIFTEEN -> R.string.age_group_display_name_fifteen
            AgeGroup.ADULT -> R.string.age_group_display_name_adult
        }

/** The label of a segment in the settings age picker. */
@get:StringRes
val AgeGroup.shortNameRes: Int
    get() =
        when (this) {
            AgeGroup.SIX -> R.string.age_group_short_name_six
            AgeGroup.NINE -> R.string.age_group_short_name_nine
            AgeGroup.TWELVE -> R.string.age_group_short_name_twelve
            AgeGroup.FIFTEEN -> R.string.age_group_short_name_fifteen
            AgeGroup.ADULT -> R.string.age_group_short_name_adult
        }

/** What the texts are like, with the passage length as `%1$d` and `%2$d`. */
@get:StringRes
val AgeGroup.summaryRes: Int
    get() =
        when (this) {
            AgeGroup.SIX -> R.string.age_group_summary_six
            AgeGroup.NINE -> R.string.age_group_summary_nine
            AgeGroup.TWELVE -> R.string.age_group_summary_twelve
            AgeGroup.FIFTEEN -> R.string.age_group_summary_fifteen
            AgeGroup.ADULT -> R.string.age_group_summary_adult
        }

fun AgeGroup.displayName(context: Context): String = context.getString(displayNameRes)

fun AgeGroup.shortName(context: Context): String = context.getString(shortNameRes)

/**
 * What the texts are like, for example "Everyday words · 80–120 words". The figures come from
 * [AgeGroup.passageWordRange], so they always match what the model is asked for.
 */
fun AgeGroup.summary(context: Context): String =
    context.getString(summaryRes, passageWordRange.first, passageWordRange.last)

@get:StringRes
val ComprehensionSkill.displayNameRes: Int
    get() =
        when (this) {
            ComprehensionSkill.MAIN_IDEA -> R.string.comprehension_skill_main_idea
            ComprehensionSkill.DETAIL -> R.string.comprehension_skill_detail
            ComprehensionSkill.INFERENCE -> R.string.comprehension_skill_inference
            ComprehensionSkill.VOCABULARY -> R.string.comprehension_skill_vocabulary
            ComprehensionSkill.PURPOSE -> R.string.comprehension_skill_purpose
        }

fun ComprehensionSkill.displayName(context: Context): String = context.getString(displayNameRes)
