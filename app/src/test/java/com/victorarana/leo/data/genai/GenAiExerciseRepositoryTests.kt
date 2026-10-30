package com.victorarana.leo.data.genai

import android.icu.util.ULocale
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.support.FakeLanguageModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GenAiExerciseRepositoryTests {
    /** The language is picked when the repository is made, once per round. */
    @Test
    fun usesRoundAgeGroupAndLanguage() {
        val repository = GenAiExerciseRepository(AgeGroup.NINE, ContentLanguage.english, FakeLanguageModel())
        assertEquals(AgeGroup.NINE, repository.generator.ageGroup)
        assertEquals(ContentLanguage.english.locale, repository.generator.language.locale)

        val spanish = ContentLanguage(ULocale("es_ES"))
        assertEquals(spanish, GenAiExerciseRepository(AgeGroup.SIX, spanish, FakeLanguageModel()).generator.language)
    }

    /** The `generationErrorDescription` test deferred from phase 1: the copy exists in every language. */
    @Test
    fun generationErrorDescription() {
        val application = RuntimeEnvironment.getApplication()
        val expected =
            mapOf(
                "" to "We couldn't create this exercise. Please try again.",
                "+es" to "No pudimos crear este ejercicio. Inténtalo de nuevo.",
                "+pt-rBR" to "Não conseguimos criar este exercício. Tente novamente.",
            )
        for ((qualifiers, text) in expected) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            val description = application.getString(R.string.exercise_generation_failed)
            assertFalse(qualifiers, description.isEmpty())
            assertEquals(qualifiers, text, description)
        }
        RuntimeEnvironment.setQualifiers("")
        assertTrue(application.getString(R.string.topic_review_generic_rejection).isNotEmpty())
    }
}
