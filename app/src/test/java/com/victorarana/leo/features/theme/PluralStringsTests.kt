package com.victorarana.leo.features.theme

import android.content.res.Configuration
import com.victorarana.leo.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/**
 * Android only (phase 8 Q1): the three counted sentences that are a plain string on iOS are `<plurals>` here, so "1
 * missed" agrees in gender and number. English reads the same either way.
 */
@RunWith(RobolectricTestRunner::class)
class PluralStringsTests {
    private fun resources(tag: String) =
        RuntimeEnvironment
            .getApplication()
            .createConfigurationContext(Configuration().apply { setLocale(Locale.forLanguageTag(tag)) })
            .resources

    @Test
    fun reviewMissedAgreesWithTheCount() {
        val words =
            mapOf(
                "en" to ("Review 1 missed" to "Review 2 missed"),
                "es" to ("Repasar 1 fallada" to "Repasar 2 falladas"),
                "pt-BR" to ("Revisar 1 errada" to "Revisar 2 erradas"),
            )
        for ((tag, expected) in words) {
            val resources = resources(tag)
            assertEquals(tag, expected.first, resources.getQuantityString(R.plurals.round_review_missed, 1, 1))
            assertEquals(tag, expected.second, resources.getQuantityString(R.plurals.round_review_missed, 2, 2))
        }
    }

    /** The plural keys on the total ("1 of 1 fallada"), not on the position. */
    @Test
    fun reviewCountKeysOnTheTotal() {
        val words =
            mapOf(
                "en" to ("1 of 1 missed" to "1 of 2 missed"),
                "es" to ("1 de 1 fallada" to "1 de 2 falladas"),
                "pt-BR" to ("1 de 1 errada" to "1 de 2 erradas"),
            )
        for ((tag, expected) in words) {
            val resources = resources(tag)
            assertEquals(tag, expected.first, resources.getQuantityString(R.plurals.review_count, 1, 1, 1))
            assertEquals(tag, expected.second, resources.getQuantityString(R.plurals.review_count, 2, 1, 2))
        }
    }

    @Test
    fun topicsMoreAgreesWithTheCount() {
        val one = "Volcanes"
        assertEquals(
            "Sobre Volcanes y 1 tema más de los tuyos. Una pregunta después de cada uno.",
            resources("es").getQuantityString(R.plurals.quiz_topics_more, 1, one, 1),
        )
        assertEquals(
            "Sobre Volcanes y 2 temas más de los tuyos. Una pregunta después de cada uno.",
            resources("es").getQuantityString(R.plurals.quiz_topics_more, 2, one, 2),
        )
        assertEquals(
            "Sobre Vulcões e mais 1 dos seus temas. Uma pergunta depois de cada um.",
            resources("pt-BR").getQuantityString(R.plurals.quiz_topics_more, 1, "Vulcões", 1),
        )
        assertEquals(
            "Drawn from Volcanoes and 1 more of your topics. One question after each.",
            resources("en").getQuantityString(R.plurals.quiz_topics_more, 1, "Volcanoes", 1),
        )
    }
}
