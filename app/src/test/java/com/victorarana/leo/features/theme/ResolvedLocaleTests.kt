package com.victorarana.leo.features.theme

import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundSummary
import com.victorarana.leo.domain.roundRecordFixture
import com.victorarana.leo.features.history.HistoryScreen
import com.victorarana.leo.features.history.RoundHistoryUiState
import com.victorarana.leo.features.history.RoundHistoryViewModel
import com.victorarana.leo.features.history.RoundScreen
import com.victorarana.leo.features.quiz.WelcomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

/**
 * Android only (phase 8 Q2): formatters use the language of the strings on screen with the region of the device, like
 * iOS `Locale.current`.
 */
@RunWith(RobolectricTestRunner::class)
class ResolvedLocaleTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun resolved(
        resourceTag: String,
        device: String,
    ) = resolvedLocale(resourceTag, Locale.forLanguageTag(device)).toLanguageTag()

    @Test
    fun languageComesFromTheResourcesAndRegionFromTheDevice() {
        assertEquals("en-FR", resolved("en", "fr-FR"))
        assertEquals("es-MX", resolved("es", "es-MX"))
        assertEquals("pt-PT", resolved("pt-BR", "pt-PT"))
        assertEquals("en-GB", resolved("en", "en-GB"))
        assertEquals("es-FR", resolved("es", "fr-FR"))
    }

    /**
     * A device list such as [fr, es]: the first locale is the user's region, whatever language the resources got.
     * Robolectric qualifiers can't express a list, so this takes `list[0]` as `deviceLocale` does and does not
     * resolve resources.
     */
    @Test
    fun theFirstLocaleOfTheListGivesTheRegion() {
        val list = android.os.LocaleList.forLanguageTags("fr-FR,es-ES")
        assertEquals("es-FR", resolvedLocale("es", list[0]).toLanguageTag())
    }

    /** With a per-app language the system's list gives the region: the process's own starts with the app's locale. */
    @Test
    fun theSystemListWinsOverTheProcessesOnApi33() {
        val app = LocaleList.forLanguageTags("es")
        val system = LocaleList.forLanguageTags("fr-FR,es-ES")
        assertEquals("fr-FR", pickDeviceLocale(system, app).toLanguageTag())
        assertEquals("es", pickDeviceLocale(null, app).toLanguageTag())
        assertEquals("es-FR", resolvedLocale("es", pickDeviceLocale(system, app)).toLanguageTag())
    }

    /**
     * The API 33 path reads `LocaleManager.systemLocales` and still answers. Robolectric's `ShadowLocaleManager` can't
     * hold a system list apart from the app's, so the per-app case itself is the pure test above plus the QA step
     * with `cmd locale set-app-locales`.
     */
    @Test
    @Config(qualifiers = "fr-rFR", sdk = [Build.VERSION_CODES.TIRAMISU])
    fun theApi33PathAnswersWithTheSystemList() {
        assertEquals("fr-FR", deviceLocale(RuntimeEnvironment.getApplication()).toLanguageTag())
    }

    @Test
    fun anEmptyListFallsBackToTheProcessesThenTheJvmDefault() {
        val app = LocaleList.forLanguageTags("es")
        assertEquals("es", pickDeviceLocale(LocaleList.getEmptyLocaleList(), app).toLanguageTag())
        assertEquals(
            Locale.getDefault(),
            pickDeviceLocale(LocaleList.getEmptyLocaleList(), LocaleList.getEmptyLocaleList()),
        )
    }

    @Test
    fun aDeviceLocaleWithoutARegionKeepsTheResourceTagsOwn() {
        assertEquals("pt-BR", resolved("pt-BR", "pt"))
        assertEquals("en", resolved("en", "fr"))
        assertEquals("es", resolved("es", "es"))
    }

    /** Android 14's regional preferences ride on the device locale as unicode extensions. */
    @Test
    fun regionalPreferencesSurvive() {
        val locale = resolvedLocale("en", Locale.forLanguageTag("fr-FR-u-fw-mon-hc-h23"))
        assertEquals("mon", locale.getUnicodeLocaleType("fw"))
        assertEquals("h23", locale.getUnicodeLocaleType("hc"))
        assertEquals("en", locale.language)
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun theResourcesOfAFrenchDeviceAreEnglish() {
        var tag by mutableStateOf("")
        composeRule.setContent { tag = rememberResolvedLocale().toLanguageTag() }
        composeRule.waitForIdle()
        assertEquals("en-FR", tag)
    }

    /** Android matches pt-PT to the only Portuguese resources, `values-pt-rBR`; the region stays Portugal's. */
    @Test
    @Config(qualifiers = "pt-rPT")
    fun portugalGetsBrazilianStringsAndItsOwnRegion() {
        var tag by mutableStateOf("")
        composeRule.setContent {
            tag = rememberResolvedLocale().toLanguageTag()
            LeoTheme { WelcomeScreen(AgeGroup.NINE, 3, emptyList(), {}, {}) }
        }
        composeRule.waitForIdle()
        assertEquals("pt-PT", tag)
        composeRule.onNodeWithText("Três textos curtos, escritos para você.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es-rMX")
    fun aSpanishDeviceKeepsItsRegion() {
        var tag by mutableStateOf("")
        composeRule.setContent { tag = rememberResolvedLocale().toLanguageTag() }
        composeRule.waitForIdle()
        assertEquals("es-MX", tag)
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun welcomeOnAFrenchDeviceReadsAsEnglishWithEnglishAnd() {
        // en-FR has no serial comma, like en-GB; never the French "et".
        composeRule.setContent {
            LeoTheme {
                WelcomeScreen(AgeGroup.NINE, 6, listOf("Volcanoes", "Inventors", "Rainforests"), {}, {})
            }
        }
        composeRule
            .onNodeWithText("Drawn from Volcanoes, Inventors and Rainforests. One question after each.")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun historyOnAFrenchDeviceHasEnglishMonthsAndPercent() {
        val now = Instant.parse("2001-01-15T12:00:00Z")
        val summaries = listOf(RoundSummary(UUID.randomUUID(), now.minusSeconds(40L * 24 * 3600), List(6) { true }))
        val state =
            RoundHistoryUiState(
                ageGroup = AgeGroup.NINE,
                summaries = summaries,
                isLoaded = true,
                sections = RoundHistoryViewModel.sections(summaries, now, ZoneOffset.UTC, DayOfWeek.MONDAY),
                zone = ZoneOffset.UTC,
            )
        composeRule.setContent { LeoTheme { HistoryScreen(state, onOpen = {}, onDelete = {}, onBack = {}) } }
        composeRule.onNodeWithText("December 2000").assertIsDisplayed()
        // A French percent sign would be "100 %".
        composeRule.onNodeWithText("1 round read · 100% answered right").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun theRoundKickerOnAFrenchDeviceHasAnEnglishMonth() {
        composeRule.setContent {
            LeoTheme {
                RoundScreen(
                    round = roundRecordFixture(AgeGroup.NINE, finishedAt = Instant.parse("2001-01-15T12:00:00Z")),
                    onReview = {},
                    onBack = {},
                    zone = ZoneOffset.UTC,
                )
            }
        }
        // The region's order (day first) with the language's month.
        composeRule.onNodeWithText("15 Jan, 12:00", substring = true).assertIsDisplayed()
    }
}
