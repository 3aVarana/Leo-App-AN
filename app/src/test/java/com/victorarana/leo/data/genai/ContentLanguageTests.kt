package com.victorarana.leo.data.genai

import android.icu.util.ULocale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** Robolectric, for `android.icu`. */
@RunWith(RobolectricTestRunner::class)
class ContentLanguageTests {
    private fun language(identifier: String) = ContentLanguage(ULocale(identifier))

    @Test
    fun name() {
        val cases =
            listOf(
                "en_US" to "English",
                "es_BO" to "Spanish",
                "pt_BR" to "Portuguese",
                "zh-Hans-CN" to "Chinese",
                // ICU's form; Apple's is "Chinese, Traditional" (plan Q6).
                "zh-Hant-TW" to "Chinese (Traditional)",
                "zh_TW" to "Chinese (Traditional)",
                "sr-Latn-RS" to "Serbian (Latin)",
                "sr-Cyrl" to "Serbian",
                "ja_JP" to "Japanese",
                "" to "English",
            )
        for ((identifier, expected) in cases) {
            assertEquals("'$identifier'", expected, language(identifier).name)
        }
    }

    /** Deviation from iOS ("Unknown language"): ICU turns "und" into an empty language, which reads as English. */
    @Test
    fun undeterminedLanguage() {
        assertEquals("English", language("und").name)
    }

    @Test
    fun localeInstruction() {
        assertEquals("", ContentLanguage.english.localeInstruction)
        assertEquals("The person's locale is es_ES.", language("es_ES").localeInstruction)
        assertEquals("The person's locale is en_GB.", language("en_GB").localeInstruction)
    }

    /** Device languages are BCP 47, but the phrase uses underscores. */
    @Test
    fun localeInstructionUsesUnderscores() {
        val cases = listOf("es-ES" to "es_ES", "es-419" to "es_419", "zh-Hans-CN" to "zh_Hans_CN")
        for ((preferred, phrase) in cases) {
            assertEquals(
                preferred,
                "The person's locale is $phrase.",
                ContentLanguage(ULocale.forLanguageTag(preferred)).localeInstruction,
            )
        }
    }

    @Test
    fun withLocaleInstruction() {
        assertEquals("Write.", ContentLanguage.english.withLocaleInstruction("Write."))
        assertEquals(
            "The person's locale is es_ES.\nWrite.",
            language("es_ES").withLocaleInstruction("Write."),
        )
    }

    @Test
    fun english() {
        assertEquals("en_US", ContentLanguage.english.locale.name)
    }

    // Android-only: iOS asks the model whether it supports the locale.

    @Test
    fun currentUsesTheFirstPreferredLanguageWhenSupported() {
        assertEquals("es_MX", ContentLanguage.current(Locale.forLanguageTag("es-MX")).locale.name)
        assertEquals("pt_BR", ContentLanguage.current(Locale.forLanguageTag("pt-BR")).locale.name)
    }

    @Test
    fun currentFallsBackToEnglish() {
        assertEquals(ContentLanguage.english, ContentLanguage.current(Locale.forLanguageTag("ja-JP")))
        assertEquals(ContentLanguage.english, ContentLanguage.current(null))
    }
}
