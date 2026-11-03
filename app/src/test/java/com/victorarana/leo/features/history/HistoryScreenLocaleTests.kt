package com.victorarana.leo.features.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundSummary
import com.victorarana.leo.features.theme.LeoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Android only: the strings of History in Spanish and Brazilian Portuguese, with their plural forms. */
@RunWith(RobolectricTestRunner::class)
class HistoryScreenLocaleTests {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-03-20T12:00:00Z")

    private fun show(count: Int) {
        val summaries = List(count) { RoundSummary(UUID.randomUUID(), now.minusSeconds(it * 60L), List(6) { true }) }
        val state =
            RoundHistoryUiState(
                ageGroup = AgeGroup.NINE,
                summaries = summaries,
                isLoaded = true,
                sections = RoundHistoryViewModel.sections(summaries, now, ZoneOffset.UTC, DayOfWeek.MONDAY),
                zone = ZoneOffset.UTC,
            )
        composeRule.setContent { LeoTheme { HistoryScreen(state, onOpen = {}, onDelete = {}, onBack = {}) } }
    }

    @Test
    @Config(qualifiers = "es")
    fun spanish() {
        show(1)
        composeRule.onNodeWithText("Tus rondas").assertIsDisplayed()
        // The percent is written with a no-break space in Spanish, so only its ends are matched.
        composeRule.onNodeWithText("1 ronda leída · 100", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("respondido correctamente", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es")
    fun spanishPlural() {
        show(3)
        composeRule.onNodeWithText("3 rondas leídas", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun portuguese() {
        show(1)
        composeRule.onNodeWithText("Suas rodadas").assertIsDisplayed()
        composeRule.onNodeWithText("1 rodada lida", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun portuguesePlural() {
        show(2)
        composeRule.onNodeWithText("2 rodadas lidas", substring = true).assertIsDisplayed()
    }
}
