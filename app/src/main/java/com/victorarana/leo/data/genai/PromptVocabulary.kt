package com.victorarana.leo.data.genai

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill

// English prose that goes into the model prompts, kept out of the domain layer.
// Swift's `\` line continuations join lines with no newline: these are one-line strings joined with `+`.

/** Who the texts are written for, for use in English prompts. */
val AgeGroup.promptAudience: String
    get() =
        when (this) {
            AgeGroup.SIX -> "children aged 6 to 8"
            AgeGroup.NINE -> "children aged 9 to 11"
            AgeGroup.TWELVE -> "readers aged 12 to 14"
            AgeGroup.FIFTEEN -> "students aged 15 to 17"
            AgeGroup.ADULT -> "adult readers"
        }

/**
 * How many sentences to ask for alongside the word range. Without one the model stops well
 * under the word range, and on iOS 27.2 it ignores word counts but follows sentence counts.
 * There, without one, 15–17 passages were 4 to 5 sentences and 18+ passages about 9. Its
 * sentences there are short, about 7.5 words, so the older groups get more of them: asking for
 * longer sentences made them shorter still. (Measured on Apple's model; Gemini Nano is phase 9.)
 */
val AgeGroup.passageSentenceRange: IntRange
    get() =
        when (this) {
            AgeGroup.SIX, AgeGroup.NINE, AgeGroup.TWELVE -> 10..13
            AgeGroup.FIFTEEN -> 14..17
            AgeGroup.ADULT -> 18..22
        }

/** How the passages and questions should read, for use in English prompts. */
val AgeGroup.styleGuidance: String
    get() =
        when (this) {
            AgeGroup.SIX ->
                "Use short sentences and familiar, everyday words, with a warm and friendly tone. " +
                    "Keep the question and every answer simple and concrete."
            AgeGroup.NINE -> "Use simple sentences and everyday vocabulary, and focus on concrete ideas."
            AgeGroup.TWELVE ->
                "Write clear paragraphs that may introduce a few new words, explained by context. " +
                    "Questions may ask for light inference."
            AgeGroup.FIFTEEN ->
                "Write engaging, age-appropriate texts with clear structure and moderately rich vocabulary."
            AgeGroup.ADULT ->
                "Use varied sentence structure and precise vocabulary. " +
                    "Questions may require careful reading and an understanding of nuance."
        }

/**
 * Three parts: what the question asks, what the passage must do so the skill can be tested,
 * and what the wrong answers are. The model reads this before it writes the passage.
 */
val ComprehensionSkill.promptHint: String
    get() =
        when (this) {
            ComprehensionSkill.MAIN_IDEA ->
                "The question asks what the passage is mostly about, or what its central message is. " +
                    "The passage must not state its own main idea or moral in a sentence; the reader works it out " +
                    "from the whole text. The wrong answers are details from the passage that are true but too " +
                    "narrow, or ideas the passage never supports."
            ComprehensionSkill.DETAIL ->
                "The question asks about one specific, important fact or event in the passage. " +
                    "State that fact plainly in the passage. The question and the correct answer must rephrase it " +
                    "in different words. The wrong answers are other things from the passage, slightly changed " +
                    "so they are false."
            ComprehensionSkill.INFERENCE ->
                "The question asks about something the passage never says directly but clearly implies: " +
                    "how a character feels, why someone did something, what will probably happen next, " +
                    "or what caused something described. The passage must give the clues but never state the " +
                    "answer. The wrong answers are conclusions the clues do not support."
            ComprehensionSkill.VOCABULARY ->
                "Use one word or expression in the passage that is slightly above the reader's level, in a " +
                    "sentence whose context shows its meaning. Do not define it in the passage, do not mark it " +
                    "in any way, and do not pick the topic word. The question asks what that word means in the " +
                    "passage and repeats the word. The wrong answers are meanings that word could have in other " +
                    "contexts, or meanings that fit the sentence badly."
            ComprehensionSkill.PURPOSE ->
                "The question asks why the writer wrote the passage, or why the writer included a specific " +
                    "event, detail or example, and it MUST say \"the writer\". A character in a story is never " +
                    "the writer. The passage itself must never mention the writer, the reader, the passage or " +
                    "its purpose; it simply tells or explains. The wrong answers are purposes that sound " +
                    "reasonable but do not match what the passage actually does."
        }
