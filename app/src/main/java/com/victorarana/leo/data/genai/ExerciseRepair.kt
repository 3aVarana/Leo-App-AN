package com.victorarana.leo.data.genai

import com.victorarana.leo.domain.wordCount

/**
 * Why model output was not turned into an exercise. Logged, so on-device logs show which rule fired.
 * [rawValue]s are the iOS ones, so logs read the same.
 */
enum class ExerciseRejection(
    val rawValue: String,
) {
    EMPTY_FIELD("emptyField"),
    DISTRACTORS("distractors"),
    PASSAGE_LENGTH("passageLength"),
    SELF_REFERENCE("selfReference"),
    STANDOUT_ANSWER("standoutAnswer"),
}

/** Kotlin enums can't be throwable, so the rule travels inside an exception (Swift's `Error` enum). */
class ExerciseRejectedException(
    val rejection: ExerciseRejection,
) : Exception(rejection.rawValue)

/**
 * The repairs and checks applied to model output, as pure functions so they can be tested
 * without the model. Rules: docs/Leo-Exercise-Quality-Plan.md, section 2.4 (in the iOS repo).
 *
 * Regexes are the Swift ones. On Android `java.util.regex` is ICU, on the JVM it is not: `\p{..}` classes
 * and the patterns here behave the same on both, but `(?i)` and `\s` do not, so `(?i:title|t[ií]tulo)` is
 * spelled out as character classes and the JVM-only unit tests are repeated on a device
 * (`RegexAndWordCountDeviceTests`).
 */
@Suppress("TooManyFunctions")
object ExerciseRepair {
    // region Repairs

    /**
     * Removes a title the model put at the start of the passage: a `#` heading, a bold phrase on
     * its own line, a bold phrase of 2 or more words before a new sentence, a "Title:" line, or a
     * short first line without sentence punctuation or commas. The title is generated last, as
     * its own field, so the model sometimes writes one into the passage too. Bold words that
     * start a sentence, as in "**Volcanoes** are mountains" or "**Mount** Etna erupted", are left
     * for [removingMarkdown].
     */
    fun removingLeadingTitle(passage: String): String {
        val match = leadingTitlePattern.find(passage) ?: return passage
        return passage.substring(match.range.last + 1).trim()
    }

    private val leadingTitlePattern: Regex =
        run {
            val titles =
                listOf(
                    """#+[^\n]*\n""",
                    """\*\*[^*\n.!?]+\*\*[ \t]*\n""",
                    """\*\*[^*\s.!?]+(?:[ \t]+[^*\s.!?]+)+\*\*[ \t]*(?=\p{Lu})""",
                    """(?:[Tt][Ii][Tt][Ll][Ee]|[Tt][IiÍí][Tt][Uu][Ll][Oo])[ \t]*:[^\n]*\n""",
                    """[^\n.!?。！？:,，]{1,60}\n""",
                )
            Regex("""^\s*(?:""" + titles.joinToString("|") + ")")
        }

    /**
     * Joins lines the model broke after every sentence, which the reader would see as a list.
     * Blank lines between paragraphs are kept, as one blank line.
     */
    fun joiningLines(passage: String): String {
        val marked = paragraphBreak.replace(passage, " ")
        return lineBreak.replace(marked, " ").replace(" ", "\n\n")
    }

    private val paragraphBreak = Regex("""[ \t]*\n(?:[ \t]*\n)+[ \t]*""")
    private val lineBreak = Regex("""[ \t]*\n[ \t]*""")

    /**
     * Removes markdown the model sometimes adds: `*` and `_` used for emphasis, `#` headings and
     * backticks. An asterisk or underscore with letters or digits on both sides, as in "2*3" or
     * "snake_case", is left alone.
     */
    fun removingMarkdown(text: String): String =
        markdownPatterns
            .fold(text) { acc, (pattern, template) ->
                pattern.replace(acc, template)
            }.trim()

    private val markdownPatterns: List<Pair<Regex, String>> =
        listOf(
            """(?m)^[ \t]*#+[ \t]*""" to "",
            "`+" to "",
            """(?<![\p{L}\p{N}])\*+|\*+(?![\p{L}\p{N}])""" to "",
            """(?<![\p{L}\p{N}])_+(?=\S)|(?<=\S)_+(?![\p{L}\p{N}])""" to "",
            """(?m)^[ \t]+""" to "",
            """[ \t]{2,}""" to " ",
        ).map { (pattern, template) -> Regex(pattern) to template }

    /**
     * Gives all the answers the same ending, so the correct one doesn't stand out by its
     * punctuation: if at least half end in terminal punctuation, a period is added to the others;
     * otherwise the final period is removed from all of them.
     */
    fun matchingPunctuation(answers: List<String>): List<String> {
        val terminated = answers.filter(::endsSentence)
        if (terminated.size * 2 < answers.size) {
            return answers.map { answer ->
                // An ellipsis ("Wait...") keeps all its dots.
                val last = answer.lastOrNull()
                if (last == null || last !in periods || answer.dropLast(1).endsWith(last)) {
                    answer
                } else {
                    answer.dropLast(1).trimEnd(' ', '\t').trimStart(' ', '\t')
                }
            }
        }
        // Japanese and Chinese answers get their own full stop.
        val period = terminated.mapNotNull { it.lastOrNull() }.firstOrNull { it in periods } ?: '.'
        return answers.map { if (endsSentence(it)) it else it + period }
    }

    /**
     * An explanation cut off by the response token limit doesn't end a sentence. The finish reason isn't
     * relied on, so the cut-off is detected here and the explanation dropped; the reader sees no
     * explanation rather than half of one.
     */
    fun completeExplanation(explanation: String): String =
        if (explanation.isEmpty() || endsSentence(explanation)) explanation else ""

    private val periods = setOf('.', '。', '．')
    private val terminalPunctuation = setOf('.', '!', '?', '…', '。', '！', '？', '．')
    private val closingQuotes = setOf('"', '\'', '”', '’', '»', '」', '』')

    /**
     * Ends in terminal punctuation, possibly followed by closing quotes. A closing quote alone
     * counts too, since quoted speech often ends the sentence inside the quote in some languages.
     */
    private fun endsSentence(text: String): Boolean {
        val last = text.lastOrNull() ?: return false
        return last in terminalPunctuation || last in closingQuotes
    }

    // endregion

    // region Checks

    /**
     * Whether the passage talks about itself ("the author wanted to show..."), in the languages
     * with a phrase list. Case-insensitive, whole words only. Other languages always pass.
     */
    fun refersToItself(
        passage: String,
        language: ContentLanguage,
    ): Boolean {
        val pattern = selfReferencePatterns[language.locale.language] ?: return false
        return pattern.containsMatchIn(passage)
    }

    /**
     * Only phrases that make sense as self-reference: "the text" or "the writer" alone also
     * appear in ordinary passages ("She read the text on her phone"), so the author or writer
     * counts only with a verb about the passage.
     */
    private val selfReferencePatterns: Map<String, Regex> =
        mapOf(
            "en" to
                selfReferencePhrases(
                    fixed =
                        listOf(
                            "this passage",
                            "this text",
                            "this story",
                            "the main idea",
                            "the author's purpose",
                            "the author’s purpose",
                            "the writer's purpose",
                            "the writer’s purpose",
                        ),
                    subjects = listOf("the author", "the writer"),
                    verbs =
                        listOf(
                            "wants",
                            "wanted",
                            "explains",
                            "describes",
                            "is trying",
                            "tries",
                            "tells us",
                            "tells the reader",
                            "shows",
                            "includes",
                        ),
                ),
            "es" to
                selfReferencePhrases(
                    fixed =
                        listOf(
                            "este texto",
                            "este pasaje",
                            "esta historia",
                            "la idea principal",
                            "el propósito del autor",
                        ),
                    subjects = listOf("el autor", "la autora", "el escritor", "la escritora"),
                    verbs = listOf("quiere", "quiso", "explica", "describe", "intenta", "nos cuenta", "nos dice"),
                ),
            "pt" to
                selfReferencePhrases(
                    fixed =
                        listOf(
                            "este texto",
                            "esta passagem",
                            "esta história",
                            "a ideia principal",
                            "o objetivo do autor",
                        ),
                    subjects = listOf("o autor", "a autora", "o escritor", "a escritora"),
                    verbs = listOf("quer", "quis", "explica", "descreve", "tenta", "nos conta", "nos diz"),
                ),
        ).mapValues { (_, phrases) ->
            val alternatives = phrases.joinToString("|") { Regex.escape(it) }
            // IGNORE_CASE is Unicode-aware in Kotlin (it adds UNICODE_CASE), like Swift's `.caseInsensitive`.
            Regex("""(?<![\p{L}\p{N}])(?:$alternatives)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
        }

    private fun selfReferencePhrases(
        fixed: List<String>,
        subjects: List<String>,
        verbs: List<String>,
    ): List<String> = fixed + subjects.flatMap { subject -> verbs.map { "$subject $it" } }

    /**
     * Whether the correct answer is visibly longer than every distractor: more than one and a
     * half times the longest one, and at least 3 words longer, so short answers aren't rejected
     * by the ratio alone.
     */
    fun correctAnswerStandsOut(
        correct: String,
        distractors: List<String>,
    ): Boolean {
        val correctWords = correct.wordCount
        val longest = distractors.maxOfOrNull { it.wordCount } ?: 0
        return correctWords * RATIO_DENOMINATOR > longest * RATIO_NUMERATOR &&
            correctWords >= longest + MINIMUM_EXTRA_WORDS
    }

    // endregion

    private const val RATIO_NUMERATOR = 3
    private const val RATIO_DENOMINATOR = 2
    private const val MINIMUM_EXTRA_WORDS = 3
}
