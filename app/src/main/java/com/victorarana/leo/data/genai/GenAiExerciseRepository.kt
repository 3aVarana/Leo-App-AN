package com.victorarana.leo.data.genai

import com.victorarana.leo.data.repositories.ExerciseRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundTopic

/** Writes each exercise with the on-device model. The port of `FoundationModelsExerciseRepository`. */
class GenAiExerciseRepository(
    ageGroup: AgeGroup,
    /** Picked when the repository is made, once per round. */
    language: ContentLanguage = ContentLanguage.current(),
    model: LanguageModel = MlKitLanguageModel.shared,
) : ExerciseRepository {
    /** Internal, so tests can check how it was built. */
    internal val generator = ExerciseGenerator(language, ageGroup, model)

    override suspend fun exercise(
        topics: List<RoundTopic>,
        skill: ComprehensionSkill,
        onAttempt: (RoundTopic) -> Unit,
    ): Exercise = generator.generate(topics, skill, onAttempt)
}
