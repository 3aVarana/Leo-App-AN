package com.victorarana.leo.features.components

/** How one dot of [ProgressDots] is drawn. `QuizViewModel.dotState(at:)` decides it. */
enum class DotState {
    /** A text already answered. Neutral, whatever the answer was. */
    Done,

    /** The text being written or read. */
    Current,

    /** The current text, answered correctly, while its feedback shows. */
    CurrentCorrect,

    /** The current text, answered wrongly, while its feedback shows. */
    CurrentMissed,

    /** The text that couldn't be written. */
    Failed,

    Upcoming,
}
