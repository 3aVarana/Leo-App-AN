package com.victorarana.leo.features.components

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.DefaultTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class DomainLabelsTests {
    private val base: Context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun context(tag: String): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }

    /** `ResourceTopicNames` keeps the application context, so the locale is set on the application. */
    private fun namesIn(tag: String): ResourceTopicNames {
        RuntimeEnvironment.setQualifiers("+${tag.replace("-", "-r")}")
        return ResourceTopicNames(base)
    }

    private val english get() = context("en")

    @Test
    fun displayNames() {
        assertEquals(
            listOf("6 to 8", "9 to 11", "12 to 14", "15 to 17", "18 or older"),
            AgeGroup.entries.map { it.displayName(english) },
        )
    }

    @Test
    fun shortNames() {
        assertEquals(
            listOf("6–8", "9–11", "12–14", "15–17", "18+"),
            AgeGroup.entries.map { it.shortName(english) },
        )
    }

    /** The figures in the summary are the ones the model is asked for. */
    @Test
    fun summaryMatchesPassageWordRange() {
        for (group in AgeGroup.entries) {
            val range = group.passageWordRange
            assertTrue("$group", group.summary(english).endsWith(" · ${range.first}–${range.last} words"))
        }
    }

    @Test
    fun summaryExample() {
        assertEquals("Everyday words · 80–120 words", AgeGroup.NINE.summary(english))
    }

    @Test
    fun summaryInOtherLanguages() {
        assertEquals("Palabras cotidianas · 80–120 palabras", AgeGroup.NINE.summary(context("es")))
        assertEquals("Palavras do dia a dia · 80–120 palavras", AgeGroup.NINE.summary(context("pt-BR")))
    }

    @Test
    fun skillNames() {
        assertEquals(
            listOf("Main idea", "Key detail", "Inference", "Vocabulary in context", "Author's purpose"),
            ComprehensionSkill.entries.map { it.displayName(english) },
        )
        assertEquals("Idea principal", ComprehensionSkill.MAIN_IDEA.displayName(context("es")))
        assertEquals("Propósito do autor", ComprehensionSkill.PURPOSE.displayName(context("pt-BR")))
    }

    @Test
    fun everyLabelIsNotEmptyInEveryLanguage() {
        for (tag in listOf("en", "es", "pt-BR")) {
            val context = context(tag)
            for (group in AgeGroup.entries) {
                assertTrue("$tag $group", group.displayName(context).isNotEmpty())
                assertTrue("$tag $group", group.shortName(context).isNotEmpty())
                assertTrue("$tag $group", group.summary(context).isNotEmpty())
            }
            for (skill in ComprehensionSkill.entries) {
                assertTrue("$tag $skill", skill.displayName(context).isNotEmpty())
            }
        }
    }

    /** The English resources hold exactly the names of the domain's topics. */
    @Test
    fun topicNamesInEnglishMatchTheDomain() {
        val names = namesIn("en")
        for (group in AgeGroup.entries) {
            for (topic in DefaultTopics.topics(group)) {
                assertEquals("${group.rawValue} ${topic.id}", topic.englishName, names.name(topic, group))
            }
        }
    }

    @Test
    fun topicNamesAreTranslated() {
        val dinosaurs = DefaultTopics.topics(AgeGroup.SIX).first { it.id == "dinosaurs" }
        for ((tag, expected) in listOf("es" to "Dinosaurios", "pt-BR" to "Dinossauros")) {
            val names = namesIn(tag)
            assertEquals(tag, expected, names.name(dinosaurs, AgeGroup.SIX))
            for (group in AgeGroup.entries) {
                for (topic in DefaultTopics.topics(group)) {
                    assertTrue("$tag ${topic.id}", names.name(topic, group).isNotEmpty())
                }
            }
        }
    }
}
