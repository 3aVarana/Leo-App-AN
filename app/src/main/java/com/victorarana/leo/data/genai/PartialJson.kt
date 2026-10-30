package com.victorarana.leo.data.genai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reading the model's JSON while it is still being written. The Prompt API streams text only, not partially
 * generated typed objects (FoundationModels' snapshots), so the runaway check reads the passage out of the
 * text so far.
 */
internal object PartialJson {
    private val passageMarker = Regex("\"passage\"\\s*:\\s*\"")

    /** The passage written so far, or null if it hasn't started. */
    fun passage(json: String): String? = stringValue(json, "passage")

    /**
     * Adds a streamed [chunk] to the [text] so far. Chunks are new text; if the API repeats everything so far
     * in each chunk instead, the longer chunk replaces the text. Unverified on a device which one ML Kit does.
     */
    fun append(
        text: String,
        chunk: String,
    ): String = if (chunk.length > text.length && chunk.startsWith(text)) chunk else text + chunk

    /**
     * The value of the top-level string property [key] in [json], as far as it has been written, or null
     * if the property has not started yet. An unfinished escape sequence at the end is left out.
     */
    fun stringValue(
        json: String,
        key: String,
    ): String? {
        val marker = if (key == "passage") passageMarker else Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"")
        val start =
            marker
                .find(json)
                ?.range
                ?.last
                ?.plus(1)
        return start?.let { readString(json, it) }
    }

    /** The string that starts at [start], up to its closing quote or the end of [json]. */
    private fun readString(
        json: String,
        start: Int,
    ): String {
        val value = StringBuilder()
        var index = start
        var done = false
        while (index < json.length && !done) {
            val char = json[index]
            if (char == '"') {
                done = true
            } else if (char == '\\') {
                val consumed = appendEscape(json, index, value)
                // 0: the escape is cut off by the end of the text, so the value ends here.
                done = consumed == 0
                index += consumed
            } else {
                value.append(char)
                index++
            }
        }
        return value.toString()
    }

    /** Appends the escape at [index] (a backslash) and returns its length, or 0 if it isn't complete yet. */
    private fun appendEscape(
        json: String,
        index: Int,
        out: StringBuilder,
    ): Int {
        val next = json.getOrNull(index + 1)
        val digits = json.substring(minOf(json.length, index + 2), minOf(json.length, index + UNICODE_ESCAPE_LENGTH))
        val isCut = next == null || (next == 'u' && digits.length < UNICODE_DIGITS)
        if (!isCut) {
            val code = if (next == 'u') digits.toIntOrNull(HEX) else null
            when {
                code != null -> out.append(code.toChar())
                // A malformed \uXXXX keeps its raw characters instead of failing the attempt.
                next == 'u' -> out.append("\\u").append(digits)
                else -> out.append(unescaped(checkNotNull(next)))
            }
        }
        return when {
            isCut -> 0
            next == 'u' -> UNICODE_ESCAPE_LENGTH
            else -> 2
        }
    }

    private fun unescaped(char: Char): Char =
        when (char) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            'b' -> '\b'
            'f' -> '\u000C'
            else -> char
        }

    /**
     * Reads a finished [GeneratedExercise] from the model's text, tolerating prose or a code fence around
     * the object. Null when a field is missing, so the attempt counts as failed (as a structured response
     * the framework can't fill does on iOS).
     */
    fun generatedExercise(text: String): GeneratedExercise? =
        try {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) {
                null
            } else {
                val json = JSONObject(text.substring(start, end + 1))
                GeneratedExercise(
                    passage = json.getString("passage"),
                    question = json.getString("question"),
                    correctAnswer = json.getString("correctAnswer"),
                    // The schema fixes the count at 3 on iOS; unconstrained JSON may return more.
                    incorrectAnswers =
                        json
                            .getJSONArray("incorrectAnswers")
                            .strings()
                            .take(GeneratedExercise.INCORRECT_ANSWER_COUNT),
                    explanation = json.getString("explanation"),
                    title = json.getString("title"),
                )
            }
        } catch (_: JSONException) {
            null
        }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private const val UNICODE_DIGITS = 4
    private const val UNICODE_ESCAPE_LENGTH = 6
    private const val HEX = 16
}
