package com.victorarana.leo.support

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.features.preferences.PreferencesEditorViewModel

/** An editor over [draft] with a stubbed review and English topic names (iOS `PreferencesEditorViewModel.fixture`). */
fun preferencesEditorFixture(
    draft: ReaderPreferences = ReaderPreferences(ageGroup = AgeGroup.NINE),
    reviews: StubTopicReviewRepository = StubTopicReviewRepository(),
    onSave: (ReaderPreferences) -> Unit = {},
) = PreferencesEditorViewModel(draft, reviews, TopicNames.English, onSave)
