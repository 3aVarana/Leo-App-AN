package com.victorarana.leo.support

import com.victorarana.leo.data.genai.GeneratedExercise
import com.victorarana.leo.domain.words

/** Valid model output. The default passage is 60 words, inside `AgeGroup.SIX.acceptedWordCount`. */
fun generatedExerciseFixture(
    title: String = "The Quiet Field",
    passage: String = words(60),
    question: String = "Where did the fox run?",
    correctAnswer: String = "Across a field",
    incorrectAnswers: List<String> = listOf("Into a cave", "Up a tree", "Under a bridge"),
    explanation: String = "The passage says the fox ran across a quiet green field.",
): GeneratedExercise =
    GeneratedExercise(
        passage = passage,
        question = question,
        correctAnswer = correctAnswer,
        incorrectAnswers = incorrectAnswers,
        explanation = explanation,
        title = title,
    )
