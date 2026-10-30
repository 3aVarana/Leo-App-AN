package com.victorarana.leo.domain

/**
 * The comprehension skill each question targets, so a round covers different kinds of understanding.
 * Raw values are persisted with a saved round. Never change them. The localized name is
 * `ComprehensionSkill.displayName` in the app module.
 */
enum class ComprehensionSkill(
    val rawValue: String,
) {
    MAIN_IDEA("mainIdea"),
    DETAIL("detail"),
    INFERENCE("inference"),
    VOCABULARY("vocabulary"),
    PURPOSE("purpose"),
    ;

    companion object {
        fun fromRawValue(rawValue: String): ComprehensionSkill? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
