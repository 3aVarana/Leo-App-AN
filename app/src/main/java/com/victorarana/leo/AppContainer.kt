package com.victorarana.leo

import android.content.Context
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.victorarana.leo.data.datastore.DataStorePreferencesRepository
import com.victorarana.leo.data.genai.GenAiExerciseRepository
import com.victorarana.leo.data.genai.GenAiModelAvailabilityProvider
import com.victorarana.leo.data.genai.GenAiTopicReviewRepository
import com.victorarana.leo.data.genai.LanguageModel
import com.victorarana.leo.data.genai.MlKitLanguageModel
import com.victorarana.leo.data.genai.MlKitModelStatusSource
import com.victorarana.leo.data.repositories.ExerciseRepository
import com.victorarana.leo.data.repositories.ModelAvailabilityProvider
import com.victorarana.leo.data.repositories.PreferencesRepository
import com.victorarana.leo.data.repositories.RoundHistoryRepository
import com.victorarana.leo.data.repositories.TopicReviewRepository
import com.victorarana.leo.data.room.RoomRoundHistoryRepository
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.features.components.ResourceTopicNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The composition root: builds the repositories and hands them to the ViewModels. The port of `MyApp.init`,
 * written by hand instead of with Hilt (CLAUDE.md).
 *
 * Every field is `by lazy`, so `LeoApplication.onCreate` costs nothing and a Robolectric test that starts the
 * application never builds ML Kit, Room or DataStore (iOS: the test host shows nothing, `MyApp.swift:7`).
 * One [GenerativeModel] serves the language model and the availability source; DataStore allows one instance
 * per file and the history store one per process, so each is built here, once.
 */
class AppContainer(
    context: Context,
) {
    // The application context, so nothing built here can hold on to an Activity.
    private val appContext: Context = context.applicationContext

    private val generativeModel: GenerativeModel by lazy { Generation.getClient() }

    // The debug build can swap in a stub model (`StubModelHook`); the release one always has Gemini Nano.
    private val languageModel: LanguageModel by lazy {
        StubModelHook.languageModel()
            ?: MlKitLanguageModel(generativeModel)
    }

    val topicNames: ResourceTopicNames by lazy { ResourceTopicNames(appContext) }

    val preferences: PreferencesRepository by lazy { DataStorePreferencesRepository.create(appContext) }

    val availability: ModelAvailabilityProvider by lazy {
        StubModelHook.availability() ?: GenAiModelAvailabilityProvider(MlKitModelStatusSource(generativeModel))
    }

    val topicReviews: TopicReviewRepository by lazy {
        GenAiTopicReviewRepository(
            genericRejection = { appContext.getString(R.string.topic_review_generic_rejection) },
            model = languageModel,
        )
    }

    /**
     * For work that must outlive the Activity, such as saving the round the reader just finished. A failure of one
     * job doesn't cancel the others.
     */
    val applicationScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    val history: RoundHistoryRepository by lazy { RoomRoundHistoryRepository.live(appContext) }

    /** A new repository for each round, so a change of the device language applies to the next one. */
    fun makeExerciseRepository(settings: RoundSettings): ExerciseRepository =
        GenAiExerciseRepository(ageGroup = settings.ageGroup, model = languageModel)
}
