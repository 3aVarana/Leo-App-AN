package com.victorarana.leo.features.components

/** How an answer option is drawn. iOS nests this in `QuizViewModel`; phase 6 reuses it from here. */
enum class OptionState { Idle, Correct, Incorrect, Dimmed }
