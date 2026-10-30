# Phase 1: Domain, plan

iOS spec: the 13 files in `Leo/Domain/`, tests in `LeoTests/Domain/` (7 files), fixtures `LeoTests/Support/Fixtures.swift`, `roundAnswers` (`LeoTests/Snapshots/ResultViewSnapshotTests.swift:8-14`), goldens `LeoTestsSnapshots/{ReaderPreferencesTests/savedFormat.1.json, DefaultTopicsTests/savedIds.1.txt}`.
Already here (phase 0): `:domain` kotlin-jvm module, JVM 17, JUnit 4 + coroutines-test, detekt/ktlint; empty `com.victorarana.leo.domain` packages. Catalog already has `kotlinx-serialization-json` 1.12.0 and the `kotlin-serialization` plugin (unused). Nothing in `:app` to reuse.

## File map

All in `:domain`, `domain/src/main/kotlin/com/victorarana/leo/domain/`, package `com.victorarana.leo.domain`.

| iOS | Kotlin |
|---|---|
| `AgeGroup.swift` | `AgeGroup.kt`: `@Serializable enum class AgeGroup(val rawValue: String)` with `@SerialName("6-8")`..., `passageWordRange: IntRange`, `acceptedWordCount`, `readingWordsPerMinute`, `readingTime(wordCount): kotlin.time.Duration`, `companion MINIMUM_READING_TIME`. Labels: see D2 |
| `ComprehensionSkill.swift` | `ComprehensionSkill.kt`: `enum class ComprehensionSkill(val rawValue: String)` (`mainIdea`, `detail`, ...). Label: D2 |
| `Exercise.swift` | `Exercise.kt` (`data class Exercise`, `require` in `init`); `WordCount.kt` (`val String.wordCount`, D4) |
| `ModelAvailability.swift` | `ModelAvailability.kt`: `enum class ModelUnavailableReason`, `sealed interface ModelAvailability { data object Available; data class Unavailable(reason) }` (Q4) |
| `ReaderPreferences.swift` | `ReaderPreferences.kt`: `data class` + `companion` (`MINIMUM_ENABLED_TOPICS`, `MAXIMUM_CUSTOM_TOPICS`, `TOPIC_NAME_LENGTH`), nested `TopicMatch`, `NewTopicCheck`; `ReaderPreferencesSerializer.kt` (D1) |
| `ReaderPreferencesTopicEditing.swift` | `ReaderPreferencesTopicEditing.kt`: extension funs; `TextMatching.kt`: `String.sameTopicName(other)`, `String.characterCount` (D5) |
| `RoundAnswer.swift` | `RoundAnswer.kt` (`data class`, `id = index`, `isCorrect`) |
| `RoundRecord.swift` | `RoundRecord.kt` (plain `class`, `equals`/`hashCode` on `id` only) |
| `RoundSettings.swift` | `RoundSettings.kt` (`data class`, `companion EXERCISE_COUNT_RANGE = 3..9`, `DEFAULT_EXERCISE_COUNT = 6`) |
| `RoundSummary.swift` | `RoundSummary.kt` (`data class`, `finishedAt: java.time.Instant`) |
| `RoundTopic.swift` | `RoundTopic.kt`: `enum class TopicKind(rawValue)`, `data class RoundTopic(prompt, name, kind = INFORMATIVE)` |
| `Topic.swift` | `Topic.kt`: `DefaultTopic(id, englishName, prompt, kind)`, `@Serializable CustomTopic(id: UUID, name)`, `object DefaultTopics`, `fun interface TopicNames` (D3) |
| `TopicReviewOutcome.swift` | `TopicReviewOutcome.kt`: `sealed interface { data class Accepted(name); data class Rejected(reason) }` |

Test support: `domain/src/testFixtures/kotlin/.../Fixtures.kt` (`words(n)`, `topic(prompt)`, `ReaderPreferences.fixture`, `Exercise.fixture`, `RoundRecord.fixture`, `roundAnswers`), via Gradle's built-in `java-test-fixtures` plugin so phases 5-7 reuse them from `:app` (D7). Goldens copied to `domain/src/test/resources/golden/`.

## Behavior to preserve

- Raw values persisted, never change: `AgeGroup.swift:6-10` ("6-8".."18+"), `ComprehensionSkill.swift:6`, `TopicKind` `RoundTopic.swift:5`, every `DefaultTopic.id` (`Topic.swift:5`, golden `savedIds.1.txt`).
- Word ranges `AgeGroup.swift:47-55`; accepted = `lower*6/10 .. upper*3/2`, integer math (`:59-61`); wpm 60/100/140/170/200 (`:64-72`); reading time `tens = (wc*9 + wpm - 1)/wpm`, `max(tens*10 s, 30 s)` (`:74-83`).
- Labels and copy: `AgeGroup.swift:12-45` (en dash in `shortName` and `summary`, " · " separator), `ComprehensionSkill.swift:8-16`; summary figures come from `passageWordRange` (`:33-34`).
- `Exercise` init: `correctIndex in options.indices` precondition (`Exercise.swift:23`); default `id` is new (`:20`).
- Word count is linguistic, not space-based (`Exercise.swift:36-44`): `""`→0, `"Don't stop, it's 3:45 p.m."`→6, Japanese >1 (`WordCountTests.swift:5-23`).
- `RoundRecord` equality/hash by `id` only (`RoundRecord.swift:20-28`); `misses` keeps round order (`:16-18`).
- Defaults: `isReadingTimerOn = true`, `exercisesPerRound = 6` (`ReaderPreferences.swift:10-12`); limits 3 and 20 (`:14-15`); count range 3...9, default 6 (`RoundSettings.swift:4-6`); topic length 2...60 (`ReaderPreferencesTopicEditing.swift:8`).
- Decoding: `ageGroup` required, unknown value fails (`ReaderPreferences.swift:23`, test `unknownAgeGroupThrows`); every other key optional with the defaults above (`:24-31`); stored `exercisesPerRound` clamped into 3...9, not reset (`:30-36`); `roundSettings` clamps again (`:60`). Unknown keys ignored (JSONDecoder default).
- Encoding shape = `savedFormat.1.json`: all 5 keys always written, maps keyed by `AgeGroup` raw value, `Set<String>` as array, `CustomTopic` as `{id, name}`.
- `enabledTopics`: suggested (list order, minus disabled) then custom; if < 3, top up with the first disabled suggested topics (`ReaderPreferences.swift:43-54`). Unknown disabled ids ignored.
- `DefaultTopic.roundTopic` uses the localized name (`Topic.swift:23-25`), and that name is what history stores; `CustomTopic.roundTopic` is `(name, name, OPEN)` (`:43-45`). Topic lists and kinds exactly `Topic.swift:60-221` (85 entries; one id can have different names per group, e.g. `music-history` `:132` vs `:149`).
- `match`: case- and diacritic-insensitive, suggested topics by localized name first, then custom (`ReaderPreferencesTopicEditing.swift:40-48`). `check` order: length (character count, `:52`), enabled dup, disabled suggested, custom limit, needsReview (`:51-63`).
- Mutation edge cases: `setEnabled(true)` on a group with no entry changes nothing; removing an id leaves an empty set in place (`:66-72`); `resetSuggestedTopics` removes the key (`:74-76`); `removeCustomTopic` in another group does nothing (`:79-81`); `insertTopic` returns false and changes nothing when already enabled (`:85-95`).

## Tests to port (`domain/src/test/kotlin/com/victorarana/leo/domain/`)

| iOS | Kotlin | Notes |
|---|---|---|
| `AgeGroupTests` | `AgeGroupTests` | Parameterized cases as loops or `@RunWith(Parameterized)`. `passageSentenceRange`, `textsAreNotEmpty` (prompt parts) → phase 3 (Q1). Label tests: D2/Q2 |
| `ReaderPreferencesTests` | `ReaderPreferencesTests` | `savedFormat` compares to the copied iOS golden as `JsonElement` (key order free). `unknownAgeGroupThrows` expects `SerializationException` |
| `ReaderPreferencesTopicEditingTests` | same name | English names via `TopicNames.English` |
| `DefaultTopicsTests` | same name | `savedIds` vs copied golden. `comprehensionSkills` (`promptHint`), `promptHintHasThreeParts`, `generationErrorDescription` → phase 3 (Q1) |
| `ExerciseTests` | `ExerciseTests` | Only `nonRandomInitKeepsValues` + a new `initRejectsOutOfRangeCorrectIndex`; the other 19 need `GeneratedExercise`/`ExerciseRepair` → phase 3 (Q1) |
| `RoundRecordTests`, `RoundSummaryTests` | same names | |
| `WordCountTests` | same name | incl. `words(n)` fixture check for 1, 29, 60, 121, 345 |
| Android-only | `ReaderPreferencesSerializationTests` | defaults are encoded; unknown key ignored; missing `ageGroup` throws |
| Android-only | `TextMatchingTests` | `"Música"`/`"MUSICA"`, an emoji counts as 1 character |

## Android decisions

- **D1 Serialization in `:domain`.** Options: (a) apply `kotlin-serialization` + `kotlinx-serialization-json` to `:domain`, `@Serializable` on the domain types like iOS `Codable`; (b) keep domain plain, DTO in phase 2. Recommend (a): both are approved and pure JVM, and the JSON tests stay in Domain. `ReaderPreferences` uses a custom `KSerializer` over a private surrogate (all fields defaulted except `ageGroup`), clamping on decode. Shared `val LeoJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }` in `ReaderPreferencesSerializer.kt`. `UuidSerializer` for `java.util.UUID` (Q3).
- **D2 Localized labels.** `:domain` has no resources. Options: (a) domain keeps only the data; `:app` maps `AgeGroup`/`ComprehensionSkill` to `@StringRes` (`features/components/DomainLabels.kt`, `summary` as `%1$d–%2$d`), and the label tests (`shortNames`, `summaryExample`, `summaryMatchesPassageWordRange`, displayName parts) become Robolectric tests in `:app`; (b) English strings in domain, localization later. Recommend (a) (Q2 on timing).
- **D3 Topic names.** `String(localized:)` is used inside domain logic (`Topic.swift:24`, `ReaderPreferencesTopicEditing.swift:44`). Options: (a) `fun interface TopicNames { fun name(topic: DefaultTopic, group: AgeGroup): String }` passed explicitly to `enabledTopics`, `roundSettings(names)`, `match`, `check`, `insertTopic`, `roundTopic`; `TopicNames.English` returns `englishName`; `:app` supplies a resource-backed one; (b) a global holder set by `LeoApplication`, English by default (closer to iOS call sites, but global mutable state). Recommend (a).
- **D4 Word count.** No ICU4J (not approved). Use `java.text.BreakIterator.getWordInstance(locale)`, counting segments that contain a letter or digit. JDK and Android (ICU-backed) rules can differ (`3:45`, `p.m.`); JVM tests decide here, and phase 3 adds an instrumented check. Record in `changes.md`.
- **D5 Text matching.** `compare(.caseInsensitive, .diacriticInsensitive)` → `Normalizer.normalize(NFD)`, strip `\p{Mn}`, `lowercase(Locale.ROOT)`, then `==`. Swift `String.count` (graphemes) → `BreakIterator.getCharacterInstance()` count, not `length`.
- **D6 Value semantics.** Swift `mutating` funcs on a struct → immutable `data class` (read-only `Map`/`Set`/`List`) with functions returning a new value, same names: `setEnabled`, `resetSuggestedTopics`, `removeCustomTopic` return `ReaderPreferences`; `insertTopic` returns `ReaderPreferences?` (null = iOS `false`). Statics in `companion object`, `UPPER_SNAKE`. `Duration` → `kotlin.time.Duration`; `Date` → `Instant`; `ClosedRange<Int>` → `IntRange`; `UUID` → `java.util.UUID`.
- **D7 Test fixtures.** `java-test-fixtures` is a core Gradle plugin, not a library; recommend it over copying fixtures into `:app` later.

## Open questions for the user

1. Scope: 24 test functions in `LeoTests/Domain/` test code from `Leo/Data/FoundationModels/` (`PromptVocabulary.swift`, `ExerciseRepair.swift`, `GeneratedExercise.swift`, `ExerciseRepository.swift:14`): 19 of 20 in `ExerciseTests`, `AgeGroupTests.passageSentenceRange`/`textsAreNotEmpty`, `DefaultTopicsTests.comprehensionSkills`/`promptHintHasThreeParts`/`generationErrorDescription`. Defer them to phase 3 (listed in `changes.md`), or port `PromptVocabulary` into `:domain` now? Recommend defer; iOS keeps that prose out of Domain on purpose (`PromptVocabulary.swift:3`).
2. D2/D3 timing: add `strings.xml` (en + es + pt-BR copied from `Localizable.xcstrings`) for the 20 labels and the 85 topic names, plus the resource-backed `TopicNames`, in this phase (so the label tests port now), or in phase 5 with the editors? Recommend now; the copy and tests already exist.
3. UUID: `java.util.UUID` writes lowercase, Swift writes uppercase. Write uppercase for parity with iOS JSON (reading accepts both), or lowercase? Recommend uppercase.
4. `ModelUnavailableReason.appleIntelligenceNotEnabled` has no Android meaning. Keep the iOS cases verbatim now and reshape in phase 3 (when `checkStatus()` and download states are mapped), or rename now? Recommend keep now.
5. D3 (a) changes signatures: `roundSettings` becomes `roundSettings(names)` instead of a property. OK?

## Acceptance criteria

1. Each of the 13 iOS files has its Kotlin counterpart per the file map; `:domain` depends only on Kotlin stdlib, kotlinx-serialization-json (D1) and test deps.
2. All 85 default topics match `Topic.swift:60-221` in id, English name, prompt and kind; `savedIds` golden equals the iOS file.
3. `savedFormat` encodes JSON equal to iOS `savedFormat.1.json`; v1 JSON decodes with defaults; clamping cases `(1,3) (0,3) (-5,3) (3,3) (9,9) (10,9) (42,9)` pass.
4. Every test listed above passes; every deferred test is listed by name in `changes.md` with its target phase.
5. `AgeGroupTests.readingTime` (`AgeGroupTests.swift:28-41`) and `acceptedWordCount` (`:17-26`) cases pass exactly.
6. No Android, `java.awt` or `println` in `:domain`.
7. `./gradlew assembleDebug`, `./gradlew :domain:test testDebugUnitTest`, `./gradlew detekt ktlintCheck lintDebug` pass.

## Tasks

1. `domain/build.gradle.kts`: `kotlin-serialization` plugin, `implementation(libs.kotlinx.serialization.json)`, `java-test-fixtures`. Build passes.
2. Enums and value types: `RoundTopic.kt`, `ComprehensionSkill.kt`, `ModelAvailability.kt`, `TopicReviewOutcome.kt`, `RoundSettings.kt`. Build passes.
3. `AgeGroup.kt` + `AgeGroupTests` (ranges, reading time, monotonic tests). Tests pass.
4. `WordCount.kt`, `TextMatching.kt` + `WordCountTests`, `TextMatchingTests`. Tests pass.
5. `Exercise.kt`, `RoundAnswer.kt`, `RoundRecord.kt`, `RoundSummary.kt`; fixtures `words`, `topic`, `Exercise.fixture`, `roundAnswers`, `RoundRecord.fixture`; `ExerciseTests` (subset), `RoundRecordTests`, `RoundSummaryTests`. Tests pass.
6. `Topic.kt` (85 topics, `TopicNames.English`, `UuidSerializer`) + `DefaultTopicsTests` with the `savedIds` golden. Tests pass.
7. `ReaderPreferences.kt` + `ReaderPreferencesSerializer.kt` + `ReaderPreferences.fixture`; `ReaderPreferencesTests`, `ReaderPreferencesSerializationTests` with the `savedFormat` golden. Tests pass.
8. `ReaderPreferencesTopicEditing.kt` + `ReaderPreferencesTopicEditingTests`. Tests pass.
9. If Q2 says now: `:app` `strings.xml` (en/es/pt-BR), `DomainLabels.kt`, resource-backed `TopicNames`, Robolectric label tests. Lint passes.
10. Run the three commands; write `changes.md` (D1-D7, deferred tests, commands and results).

## Decisions (user, 2026-10-29)

- **Q1 Scope:** defer the 24 tests that need `Data/FoundationModels` code to phase 3; list each by name in `changes.md`.
- **Q2 Strings:** now. Add `strings.xml` (en, es, pt-BR from `Localizable.xcstrings`), `DomainLabels.kt`, the resource-backed `TopicNames`, and the Robolectric label tests in this phase (task 9).
- **Q3 UUID:** lowercase (Java default). Diverges from iOS's uppercase output; decoding must still accept uppercase ids written by iOS-format JSON. The `savedFormat` golden comparison must account for this (compare ids case-insensitively, or lowercase the copied golden's ids), and `changes.md` must record the deviation.
- **Q4 `appleIntelligenceNotEnabled`:** keep the iOS cases verbatim until phase 3 (default recommendation, not asked separately).
- **Q5 / D3 Topic names:** pass `TopicNames` explicitly (option a); `roundSettings` becomes `roundSettings(names)`.
- D1, D2 (a), D4–D7 as recommended.
