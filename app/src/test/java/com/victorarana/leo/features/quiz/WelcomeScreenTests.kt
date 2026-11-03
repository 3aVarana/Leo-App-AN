package com.victorarana.leo.features.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.features.theme.LeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android only: the sentences Welcome builds from the round, in English, Spanish and Portuguese. */
@RunWith(RobolectricTestRunner::class)
class WelcomeScreenTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        count: Int = 6,
        topics: List<String> = emptyList(),
        onStart: () -> Unit = {},
        onSettings: () -> Unit = {},
    ) = composeRule.setContent {
        LeoTheme {
            WelcomeScreen(AgeGroup.NINE, count, topics, onStart = onStart, onSettings = onSettings)
        }
    }

    @Test
    fun noTopicsYet() {
        show()
        composeRule.onNodeWithText("Drawn from your topics. One question after each.").assertIsDisplayed()
    }

    @Test
    fun threeTopicsOrFewerAreAllNamed() {
        show(topics = listOf("Volcanoes", "Inventors", "Rainforests"))
        composeRule
            .onNodeWithText("Drawn from Volcanoes, Inventors, and Rainforests. One question after each.")
            .assertIsDisplayed()
    }

    @Test
    fun moreThanThreeNameThreeAndCountTheRest() {
        show(topics = listOf("Volcanoes", "Inventors", "Rainforests", "Pirates", "Comets"))
        composeRule
            .onNodeWithText(
                "Drawn from Volcanoes, Inventors, Rainforests and 2 more of your topics. One question after each.",
            ).assertIsDisplayed()
    }

    @Test
    fun theCountIsSpelledOutForEveryRoundSize() {
        var count by mutableStateOf(3)
        composeRule.setContent { LeoTheme { WelcomeScreen(AgeGroup.NINE, count, emptyList(), {}, {}) } }
        val words = listOf("Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")
        for ((index, word) in words.withIndex()) {
            count = index + 3
            composeRule.onNodeWithText("$word short texts, written for you.").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "es")
    fun spanish() {
        show(count = 3, topics = listOf("Volcanes", "Piratas", "Cometas", "Dragones"))
        composeRule.onNodeWithText("Tres textos breves, escritos para ti.").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Sobre Volcanes, Piratas, Cometas y 1 temas más de los tuyos. Una pregunta después de cada uno.",
            ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun portuguese() {
        show(count = 3)
        composeRule.onNodeWithText("Três textos curtos, escritos para você.").assertIsDisplayed()
    }

    @Test
    fun startAndSettingsAreButtons() {
        var starts = 0
        var settings = 0
        show(onStart = { starts++ }, onSettings = { settings++ })
        composeRule.onNodeWithText("Start").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertEquals(1, starts)
        assertEquals(1, settings)
    }
}
