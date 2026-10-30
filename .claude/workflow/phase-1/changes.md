# Phase 1: Domain, changes

Branch `port/phase-1-domain`. Nothing committed.

## Files added

`:domain` main (`domain/src/main/kotlin/com/victorarana/leo/domain/`): `AgeGroup.kt`, `ComprehensionSkill.kt`, `Exercise.kt`, `ModelAvailability.kt`, `ReaderPreferences.kt`, `ReaderPreferencesSerializer.kt` (with `LeoJson`), `ReaderPreferencesTopicEditing.kt`, `RoundAnswer.kt`, `RoundRecord.kt`, `RoundSettings.kt`, `RoundSummary.kt`, `RoundTopic.kt` (with `TopicKind`), `TextMatching.kt`, `Topic.kt` (`DefaultTopic`, `TopicNames`, `UuidSerializer`, `CustomTopic`, `DefaultTopics`, 85 topics generated from `Topic.swift`), `TopicReviewOutcome.kt`, `WordCount.kt`. The two `.gitkeep` files were removed.

`:domain` test fixtures (`domain/src/testFixtures/kotlin/.../Fixtures.kt`): `words`, `topic`, `readerPreferencesFixture`, `exerciseFixture`, `roundAnswers`, `roundAnswerTitles`, `roundRecordFixture`.

`:domain` tests (`domain/src/test/kotlin/.../`): `AgeGroupTests`, `WordCountTests`, `TextMatchingTests`, `ExerciseTests`, `RoundRecordTests`, `RoundSummaryTests`, `DefaultTopicsTests`, `ReaderPreferencesTests`, `ReaderPreferencesSerializationTests`, `ReaderPreferencesTopicEditingTests`. Goldens copied from iOS to `domain/src/test/resources/golden/` (`savedIds.1.txt`, `savedFormat.1.json`).

`:app`: `res/values/strings.xml` (extended), `res/values-es/strings.xml`, `res/values-pt-rBR/strings.xml` (96 strings each beyond `app_name`: 15 age group labels, 5 skills, 76 distinct topic names, copied from `Localizable.xcstrings`); `features/components/DomainLabels.kt`, `features/components/ResourceTopicNames.kt`; test `features/components/DomainLabelsTests.kt` (Robolectric, en/es/pt-BR).

## Files changed

- `domain/build.gradle.kts`: `java-test-fixtures` and `kotlin-serialization` plugins, `kotlinx-serialization-json` as `implementation` (and for test fixtures).
- `gradle/libs.versions.toml`: `kotlinxSerialization` 1.12.0 -> 1.11.0 (see deviation 1).

## Deviations from the plan or from iOS

1. **kotlinx-serialization version.** The catalog's 1.12.0 was built with Kotlin 2.4 (metadata 2.4.0) and pulls in stdlib 2.4.10, which the project's Kotlin 2.2.10 compiler cannot read. Lowered to 1.11.0 (next newest; resolves to stdlib 2.3.20, readable by 2.2.x). This is a version change of an approved dependency; revisit if the Kotlin version is raised.
2. **UUIDs are written lowercase** (user decision, Q3); iOS writes uppercase. Decoding accepts both (`UuidSerializer`, test `decodesUpperCaseUuidsWrittenByIos`). The `savedFormat` test compares to the copied iOS golden with every `id` value lowercased on both sides (`withLowercaseIds`). The golden's ids are all digits, so the golden file itself is unchanged. An invalid UUID throws `SerializationException`.
3. **Fixtures are top-level functions** (`readerPreferencesFixture`, `exerciseFixture`, `roundRecordFixture`, `topic`, `words`), not `X.fixture(...)`, because Kotlin extensions on a type's companion need a companion object that `Exercise` and `RoundRecord` don't have.
4. **`RoundRecord` is a plain `class`** (equality by `id` only, as in iOS); `toString` omits `answers`.
5. **Value semantics (D6).** `ReaderPreferences` is an immutable `data class`; the mutating functions return a new value. `insertTopic` returns `ReaderPreferences?` (null = iOS `false`). `check(newTopic:in:)` is `check(newTopic, group, names)`, an extension that shadows nothing but is easy to confuse with `kotlin.check`.
6. **`DefaultTopic`** has `englishName` instead of `LocalizedStringResource`; `roundTopic(names, group)` replaces the `roundTopic` property. `TopicNames` is passed explicitly everywhere it is needed: `enabledTopics(group, names)`, `roundSettings(names)`, `match`, `check`, `insertTopic`. `TopicNames.English` is for tests; `ResourceTopicNames(context)` in `:app` is the real one, keyed by English name.
7. **Labels** (`displayName`, `shortName`, `summary` of `AgeGroup`, `displayName` of `ComprehensionSkill`) are in `:app` `DomainLabels.kt` as `@StringRes` properties plus `Context` functions. Summary strings use `%1$d–%2$d`.
8. **Enum entries** are `UPPER_SNAKE` (`AgeGroup.SIX`, `TopicKind.INFORMATIVE`, `ModelUnavailableReason.APPLE_INTELLIGENCE_NOT_ENABLED` kept verbatim until phase 3, Q4). `ComprehensionSkill.promptHint` and `AgeGroup.passageSentenceRange`/`promptAudience`/`styleGuidance` are phase 3.
9. **Word count (D4)** uses `java.text.BreakIterator.getWordInstance(Locale.ROOT)` counting segments with a letter or digit. On the JVM (JDK 21) all `WordCountTests` cases pass, including `"Don't stop, it's 3:45 p.m."` = 6 and the Japanese sample. Android's ICU-backed `BreakIterator` can differ; phase 3 should add an instrumented check.
10. **Text matching (D5)**: NFD, strip `\p{Mn}`, `lowercase(Locale.ROOT)`; character count uses `BreakIterator.getCharacterInstance` (grapheme clusters; extended clusters need JDK 20+, the project builds on 21; Android's ICU handles them).
11. **Lint**: the five `age_group_summary_*` strings carry `tools:ignore="PluralsCandidate"` (the numbers are a range, not a count, as on iOS). `app_name` was added to `values-es` and `values-pt-rBR` ("Leo") because `MissingTranslation` is an error now that those locales exist.
12. Added tests beyond iOS: `ExerciseTests.initRejectsOutOfRangeCorrectIndex`, `eachExerciseGetsANewId`; `RoundRecordTests.equalityAndHashUseTheIdOnly`; `ReaderPreferencesTests` (`decodesUpperCaseUuidsWrittenByIos`, `roundSettingsClampAnUnclampedCount`, `enabledTopicsUseTheGivenNames`); `ReaderPreferencesSerializationTests`; `TextMatchingTests`; `DefaultTopicsTests.eightyFiveTopicsInAllGroups`, `roundTopicUsesTheGroupsName`; editing tests `matchUsesTheGivenNames`, `checkLengthCountsCharacters`, `removingTheLastIdLeavesAnEmptySetInPlace`; app tests for topic name resources in three languages.

## Deferred tests (24, to phase 3: they test `Data/FoundationModels` code)

`ExerciseTests` (19, need `GeneratedExercise` / `ExerciseRepair`): `validInput`, `trimsWhitespaceAndNewlines`, `dropsEmptyDistractors`, `dropsDuplicateDistractorsIgnoringCase`, `dropsDistractorEqualToCorrectAnswerIgnoringCase`, `keepsTwoDistractorsAfterDroppingADuplicate`, `tooFewDistractorsAfterDroppingCorrectAnswer`, `wrongDistractorCount`, `emptyCorrectAnswer`, `emptyQuestion`, `passageWordCount`, `emptyExplanationIsAccepted`, `emptyTitleIsAccepted`, `rejectionReasons`, `distractorDifferingOnlyByFinalPeriodIsADuplicate`, `matchesPunctuationAndKeepsTheCorrectIndex`, `removesMarkdown`, `dropsTruncatedExplanation`, `correctAnswerLandsInEveryPosition`.

`AgeGroupTests` (2, need `PromptVocabulary`): `passageSentenceRange`, `textsAreNotEmpty` (the prompt-part half; the `displayName` half is covered by `DomainLabelsTests`).

`DefaultTopicsTests` (3): `comprehensionSkills` (`promptHint`; the display-name half is in `DomainLabelsTests.skillNames`), `promptHintHasThreeParts`, `generationErrorDescription`.

## Snapshots

No Roborazzi snapshots were recorded or changed.

## Commands run

- `./gradlew :domain:test --console=plain -q` (first run failed: serialization 1.12.0 vs Kotlin 2.2.10, deviation 1; passed after the downgrade): 77 domain tests, 0 failures.
- `./gradlew ktlintFormat`, then `./gradlew ktlintCheck`: clean after fixing one over-long test line.
- `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --continue`: first run reported detekt (`MagicNumber` in `AgeGroup.readingTime`, `ReturnCount` in `check`) and lint (`MissingTranslation` for `app_name`, `PluralsCandidate`); all fixed.
- Final `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --continue --console=plain -q`: all pass, no output. Test counts: `:domain` 77, `:app` 32 (including the new `DomainLabelsTests`), 0 failures.

## Review follow-up (non-blocking findings, same day)

Fixed after `review.md`:
1. Fixture `finishedAt` defaults to `2001-01-01T00:00:00Z` (iOS reference date).
2. `LeoJson` has `coerceInputValues = true`: explicit `null` falls back to the default; `ageGroup: null` still throws. Tests `nullValuesFallBackToDefaults`, `nullAgeGroupThrows`.
3. `sameTopicName` folds with `uppercase().lowercase()` after stripping accents, so `Straße` matches `STRASSE` (test added).
4. `wordCount` checks letters/digits by code point (test `lettersOutsideTheBasicPlane`).
5. Comment on `characterCount` records the JDK 20+ grapheme dependency (guarded by the family-emoji test; no toolchain change).
6. `ResourceTopicNames` keeps `applicationContext`. `DomainLabelsTests` therefore sets the locale on the application via `RuntimeEnvironment.setQualifiers` instead of a configuration context. Phase 5 should check that a per-app language change reaches these resources.
7. Removed the vacuous `assertNotNull`; 8. added the missing `roundSettings` equality expectation; 9. `ReaderPreferences.DEFAULT_IS_READING_TIMER_ON` is shared by the class and the serializer surrogate.
10. Left as is: `androidx.test.core` (`ApplicationProvider`) has no catalog entry, so no dependency was added.
11. `libs.versions.toml` has a comment next to `kotlinxSerialization` explaining 1.11.0.

Re-run of `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --continue`: all pass; `:domain` 80 tests, `:app` 32, 0 failures.
- Not run: `connectedDebugAndroidTest` (nothing in this phase is instrumented).
