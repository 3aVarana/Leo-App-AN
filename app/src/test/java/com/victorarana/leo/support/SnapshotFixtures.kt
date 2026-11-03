package com.victorarana.leo.support

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.domain.RoundSummary
import com.victorarana.leo.features.history.RoundHistoryUiState
import com.victorarana.leo.features.history.RoundHistoryViewModel
import com.victorarana.leo.features.quiz.QuizUiState
import com.victorarana.leo.features.quiz.QuizViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

// The states the screens snapshot in more than one suite (tablet, landscape, languages, contrast).

/** The `QuizUiState` the ViewModel would be in on a text: reading it, or answered with [selected]. */
fun exerciseQuizState(
    exercise: Exercise,
    ageGroup: AgeGroup = AgeGroup.NINE,
    index: Int = 0,
    isReading: Boolean = false,
    selected: Int? = null,
) = QuizUiState(
    phase = QuizViewModel.Phase.Answering,
    currentIndex = index,
    currentExercise = exercise,
    selectedOption = selected,
    isReading = isReading,
    roundAgeGroup = ageGroup,
    answers = if (selected == null) emptyList() else listOf(RoundAnswer(index, exercise, selected)),
)

/** Today in the History snapshots: Friday 20 March 2026, 09:12 UTC. */
val SnapshotNow: Instant = LocalDateTime.of(2026, 3, 20, 9, 12).toInstant(ZoneOffset.UTC)

/** History with this week, last week and an older month, in UTC with Monday first. */
fun threeSectionHistoryState(): RoundHistoryUiState {
    fun date(
        month: Int,
        day: Int,
        hour: Int = 9,
        minute: Int = 12,
    ) = LocalDateTime.of(2026, month, day, hour, minute).toInstant(ZoneOffset.UTC)
    val rounds =
        listOf(
            SnapshotNow to listOf(true, true, true, true, true, true),
            date(3, 16, 1, 0) to listOf(false, true, true, false, true, true),
            date(3, 9, 1, 0) to listOf(true, true, false, true, true, true),
            date(2, 10) to listOf(false, false, false, true, true, true),
        )
    val summaries =
        rounds
            .map { (finishedAt, results) -> RoundSummary(UUID.randomUUID(), finishedAt, results) }
            .sortedByDescending { it.finishedAt }
    return RoundHistoryUiState(
        ageGroup = AgeGroup.NINE,
        summaries = summaries,
        isLoaded = true,
        sections = RoundHistoryViewModel.sections(summaries, SnapshotNow, ZoneOffset.UTC, DayOfWeek.MONDAY),
        zone = ZoneOffset.UTC,
    )
}
