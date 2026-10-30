package com.victorarana.leo.data.genai

import android.icu.util.ULocale
import android.os.LocaleList
import java.util.Locale

/**
 * The language exercises are written in: the device's preferred language when the
 * on-device model supports it, English otherwise.
 *
 * Uses ICU's `ULocale` (part of Android since API 24) for names and identifiers, as Apple's `Locale`
 * does on iOS. Unit tests need Robolectric for it, because the JVM has no `android.icu`.
 */
data class ContentLanguage(
    val locale: ULocale,
) {
    /**
     * The language's name in English, e.g. "Spanish", for use in prompts. Leaves out the region:
     * with "English (Bolivia)" the model writes about Bolivia instead of the reader's topic.
     * Keeps a non-default script, e.g. "Chinese (Traditional)" (ICU's form; Apple's is "Chinese, Traditional").
     */
    val name: String
        get() {
            val code = locale.language
            if (code.isEmpty()) return "English"
            var identifier = code
            val script = scriptOf(locale)
            if (script.isNotEmpty() && script != scriptOf(ULocale(code))) identifier += "_$script"
            return ULocale(identifier).getDisplayName(ULocale.ENGLISH)
        }

    /**
     * Apple's locale phrase for readers outside US English, empty for en_US. It comes from Apple's model
     * training, kept as is until the prompts are tuned for Gemini Nano (phase 9). It goes first in the
     * instructions. The ICU form is used: BCP 47 "es-ES" is written "es_ES".
     */
    val localeInstruction: String
        get() = if (isEquivalentToUsEnglish()) "" else "The person's locale is ${locale.baseName}."

    /** [instructions] with [localeInstruction] as its first line, when there is one. */
    fun withLocaleInstruction(instructions: String): String =
        localeInstruction.let { if (it.isEmpty()) instructions else it + "\n" + instructions }

    /** Same language, script and region once ICU fills in what the identifier leaves out ("en" is en_US). */
    private fun isEquivalentToUsEnglish(): Boolean {
        val maximized = ULocale.addLikelySubtags(locale)
        val english = ULocale.addLikelySubtags(ENGLISH_LOCALE)
        return maximized.language == english.language &&
            maximized.script == english.script &&
            maximized.country == english.country
    }

    companion object {
        private val ENGLISH_LOCALE = ULocale("en_US")

        val english = ContentLanguage(ENGLISH_LOCALE)

        /**
         * The languages Gemini Nano writes exercises in. ML Kit has no `supportsLocale` (iOS asks the model),
         * so this is a fixed list: the languages Leo's UI is localized into. Others fall back to English.
         */
        val SUPPORTED_LANGUAGES: Set<String> = setOf("en", "es", "pt")

        /**
         * The language selected on the device (including a per-app language), rather than the languages the
         * app's UI is localized into.
         *
         * @param preferred The reader's first language, if any.
         */
        fun current(
            preferred: Locale? = LocaleList.getDefault().takeUnless { it.isEmpty }?.get(0),
            supportedLanguages: Set<String> = SUPPORTED_LANGUAGES,
        ): ContentLanguage {
            val locale = preferred?.let { ULocale.forLocale(it) } ?: return english
            return if (locale.language in supportedLanguages) ContentLanguage(locale) else english
        }

        /** The script of [locale], the likely one when the identifier names none. */
        private fun scriptOf(locale: ULocale): String =
            locale.script.ifEmpty { ULocale.addLikelySubtags(locale).script }
    }
}
