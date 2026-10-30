# Phase 3: On-device model, plan

iOS spec: `Leo/Data/FoundationModels/{ExerciseGenerator,GeneratedExercise,ExerciseRepair,ContentLanguage,PromptVocabulary,TopicValidator}.swift`, `Leo/Data/Repositories/{ExerciseRepository,TopicReviewRepository,ModelAvailabilityProvider}.swift`. Tests: `LeoTests/Data/{ExerciseRepairTests,ExerciseGeneratorPromptTests,ContentLanguageTests,TopicValidatorTests,ModelAvailabilityTests,FoundationModelsExerciseRepositoryTests}.swift`, `LeoTests/LiveModel/*` (3), goldens `LeoTestsSnapshots/ExerciseGeneratorPromptTests/promptSnapshot-_.<group>.txt` (5), fakes `LeoTests/Support/{StubExerciseRepository,StubTopicReviewRepository,StubModelAvailabilityProvider,TestEnvironment,Fixtures}.swift`, plus the 24 tests phase 1 deferred here (`phase-1/changes.md` "Deferred tests").

Reuse: `:domain` `Exercise` (`require` on `correctIndex`), `RoundTopic`/`TopicKind`, `ComprehensionSkill`, `AgeGroup.passageWordRange`/`acceptedWordCount`, `String.wordCount`, `TopicReviewOutcome`, `ModelAvailability`/`ModelUnavailableReason` (kept iOS-shaped for this phase, phase-1 Q4), `DefaultTopics` + `TopicNames.English`; fixtures `words(n)`, `topic(prompt)`; goldens pattern `src/test/resources/golden/` (phase 1). Catalog already has `mlkit-genai-prompt` 1.0.0-beta5 and `mlkit-genai-schema-compiler` 1.0.0-alpha2; KSP is applied to `:app` (Room). Manifest already strips `INTERNET` (`AndroidManifest.xml:6-8`).

**ML Kit API not verified.** Neither artifact is in `~/.gradle/caches/modules-2` (never resolved), and planning has no shell. Task 1 resolves them and checks every assumption marked (A) below against the AAR classes; any that fails stops the phase for the user (Q1).

## File map

`:app`, `app/src/main/java/com/victorarana/leo/data/`. "GenAi" replaces "FoundationModels" in type names, as phase 2 replaced UserDefaults/SwiftData.

| iOS | Kotlin |
|---|---|
| `ExerciseRepository.swift:5-12` | `repositories/ExerciseRepository.kt`: `suspend fun exercise(topics: List<RoundTopic>, skill: ComprehensionSkill, onAttempt: (RoundTopic) -> Unit): Exercise` |
| `ExerciseRepository.swift:14-20` | same file: `class ExerciseGenerationError : Exception("failed")`; copy is `R.string.exercise_generation_failed` (UI resolves it, phase 6) |
| `ExerciseRepository.swift:23-39` | `genai/GenAiExerciseRepository.kt` (`internal val generator`; `language: ContentLanguage = ContentLanguage.current()`) |
| `TopicReviewRepository.swift:2-12` | `repositories/TopicReviewRepository.kt` (`suspend fun review(text: String, group: AgeGroup): TopicReviewOutcome`), `genai/GenAiTopicReviewRepository.kt` (language per call, `:9`) |
| `ModelAvailabilityProvider.swift:4-14` | `repositories/ModelAvailabilityProvider.kt`, `genai/GenAiModelAvailabilityProvider.kt` (D4) |
| `ModelAvailabilityProvider.swift:16-36` | `genai/ModelAvailabilityMapping.kt`: `fun modelAvailability(featureStatus: Int): ModelAvailability` (pure) |
| `ContentLanguage.swift` | `genai/ContentLanguage.kt` (`android.icu.util.ULocale`, D6) |
| `PromptVocabulary.swift` | `genai/PromptVocabulary.kt`: extension vals `AgeGroup.promptAudience`, `.passageSentenceRange`, `.styleGuidance`, `ComprehensionSkill.promptHint` |
| `GeneratedExercise.swift:9-39` | `genai/GeneratedExercise.kt`: `@Generable data class` with the six `@Guide` descriptions verbatim, same declaration order (A) |
| `GeneratedExercise.swift:41-90` | `genai/ExerciseValidation.kt`: `fun GeneratedExercise.toExercise(topic, skill, acceptedWordCount: IntRange, language, random: Random = Random.Default): Exercise`, throws `ExerciseRejectedException(rejection)` |
| `ExerciseRepair.swift:4-10` | `genai/ExerciseRepair.kt`: `enum class ExerciseRejection(val rawValue)` + `class ExerciseRejectedException(val rejection) : Exception` |
| `ExerciseRepair.swift:14-178` | `genai/ExerciseRepair.kt`: `object ExerciseRepair` (same five repairs, two checks) |
| `ExerciseGenerator.swift` | `genai/ExerciseGenerator.kt` (`wordRange`, `instructions`, `prompt`, `generate`); model calls via `genai/LanguageModel.kt` (D2) |
| `TopicValidator.swift` | `genai/TopicReview.kt` (`@Generable`), `genai/TopicValidator.kt` (`companion fun outcome(review, genericRejection)`) |
| — | `genai/MlKitLanguageModel.kt`: the only file importing `com.google.mlkit.genai.prompt` besides the provider and `@Generable` classes |
| `app/src/main/keepRules/rules.keep` | keep `GeneratedExercise`, `TopicReview` and their generated schema classes |
| strings | `exercise_generation_failed`, `topic_review_generic_rejection` in values / `-es` / `-pt-rBR` (copy from `Localizable.xcstrings:2449`, `:2913`) |
| `LeoTests/Support/Stub*.swift` | `app/src/test/.../support/StubExerciseRepository.kt` (+ `ExerciseRepositoryFactorySpy`), `StubTopicReviewRepository.kt`, `StubModelAvailabilityProvider.kt`, `FakeLanguageModel.kt` (Android-only) |

## Behavior to preserve

- Prompts word for word: `wordRange` "between L and U words long, about M words in a to b sentences", M = (L+U)/2 (`ExerciseGenerator.swift:11-19`); instructions (`:23-37`); prompt with word range repeated (`:40-47`); passage kind per `TopicKind` (`:51-67`); `promptAudience`, `passageSentenceRange` 10-13/14-17/18-22, `styleGuidance`, `promptHint` (`PromptVocabulary.swift:7-99`). Swift `\` line continuations join with no newline: compare against goldens, not by eye.
- Generation: at most 3 topics, in order, `onAttempt` before each (`:69`, `:109-117`); cancellation checked first and rethrown (`:110`, `:157-159`; Kotlin: `ensureActive()`, never swallow `CancellationException`); a fresh request per exercise (`:134-135`); temperature 0.8, max 1200 output tokens (`:74-77`) (A); stream and abandon once the partial passage's `wordCount > acceptedWordCount.last` (`:183-185`); no content → failed (`:188`); all topics fail → `ExerciseGenerationError` (`:118-119`). Log tag `ExerciseGenerator`: "Rejected <skill> exercise (<rule>) for topic '<prompt>'", "Runaway passage for topic '<prompt>'", "Generation failed for topic '<prompt>': <error>" (`:150-161`).
- Validation order (`GeneratedExercise.swift:51-88`): clean = trim then `removingMarkdown`; dedupe distractors by `lowercase` with `.`/`。` trimmed, correct answer seeded first, blanks dropped; passage = clean(joiningLines(removingLeadingTitle)); empty correct/question → `emptyField`; distractors not 2..3 → `distractors`; word count outside accepted → `passageLength`; `selfReference`; `standoutAnswer`; then `matchingPunctuation([correct] + distractors)`, shuffle, correct index tracked; title cleaned (empty allowed); explanation `completeExplanation(clean(...))`.
- Repairs: leading-title regex alternatives exactly (`ExerciseRepair.swift:31-41`), paragraph/line joins (`:45-57`), markdown patterns in order (`:72-82`), punctuation majority rule incl. ellipsis and the answer's own full stop (`:87-101`), sets (`:110-112`), truncated explanation → "" (`:106-108`), self-reference lists for en/es/pt only, whole words, case-insensitive, by language code (`:125-168`), standout `c*2 > l*3 && c >= l+3` (`:173-177`).
- `ContentLanguage`: English = `en_US` (`:9`); first preferred device language if supported else English (`:13-17`); `name` in English without region, keeps non-default script, "" → "English" (`:22-29`); locale phrase "The person's locale is <ICU id>." empty when equivalent to en_US, prepended with "\n" (`:34-43`).
- Topic review (`TopicValidator.swift`): guides verbatim (`:8-19`); instructions + prompt "Topic: <text>\nWrite the reason in <name>." at temperature 0.2 (`:41-55`); `outcome` trims whitespace + Unicode punctuation from topic, unsuitable or empty → trimmed reason or the localized generic rejection (`:31-38`); guardrail/refusal → generic rejection, other errors rethrown (`:57-68`) (A: which `GenAiException` codes).
- Availability read fresh, never cached on iOS (`ModelAvailabilityProvider.swift:9-13`).

## Tests to port

All in `app/src/test/java/com/victorarana/leo/data/genai/` unless noted; JUnit 4 + `runTest`; Robolectric only where ICU/resources are needed.

| iOS | Kotlin | Notes |
|---|---|---|
| `ExerciseRepairTests` (all cases) | `ExerciseRepairTests` | Parameterized cases as lists looped in one test each, failure message names the case |
| `ExerciseTests` 19 deferred | `GeneratedExerciseTests` | Fixture `generatedExerciseFixture(...)` in test `support/`; uses `words(n)`, `topic("foxes")`, `AgeGroup.SIX.acceptedWordCount`; `correctAnswerLandsInEveryPosition` keeps 200 runs |
| `ExerciseGeneratorPromptTests` | `ExerciseGeneratorPromptTests` | Goldens copied to `app/src/test/resources/golden/ExerciseGeneratorPromptTests/promptSnapshot-<group>.txt`, exact match; `spanishInstructionsStartWithTheLocale` |
| `ContentLanguageTests` | `ContentLanguageTests` (Robolectric) | zh-Hant cases per Q6 |
| `TopicValidatorTests` | `TopicValidatorTests` (Robolectric, en) | generic rejection read from resources |
| `ModelAvailabilityTests` | `ModelAvailabilityMappingTests` | One per `FeatureStatus` constant, plus unknown int → `OTHER` |
| `FoundationModelsExerciseRepositoryTests` | `GenAiExerciseRepositoryTests` | |
| `AgeGroupTests` 2 + `DefaultTopicsTests` 3 deferred | `PromptVocabularyTests` | `promptHintHasThreeParts` via `BreakIterator.getSentenceInstance(Locale.ENGLISH)`; `generationErrorDescription` checks the string in 3 locales |
| Android-only | `ExerciseGeneratorTests` (`FakeLanguageModel`) | 3-topic cap, `onAttempt` order, rejection/exception → next topic, runaway abandons stream, cancellation propagates, all fail → `ExerciseGenerationError` |
| Android-only | `GenAiModelAvailabilityProviderTests` | download states → availability (D4) via fake status source |
| `LiveModel/*` (3 suites) | `app/src/androidTest/.../livemodel/{ExerciseGeneratorLiveTests,TopicValidatorLiveTests,PassageLengthLiveTests}` | `@LiveModel` annotation + `assumeTrue(checkStatus()==AVAILABLE)` rule, `Timeout` 1 / 2 min; passage lengths logged with tag `PassageLength` |
| Android-only | `app/src/androidTest/.../RegexAndWordCountDeviceTests` | Android's regex and `BreakIterator` are ICU, the JVM's aren't: run the Unicode-sensitive `ExerciseRepair` and `WordCount` cases on a device/emulator (phase-1 deviation 9) |

## Android decisions

- **D1 Regex.** Kotlin `Regex`, patterns copied; ICU's `\s`, `(?i)` are Unicode-aware, JDK's aren't: use `(?iu:...)` for `title|t[ií]tulo`, `RegexOption.IGNORE_CASE` (Kotlin adds UNICODE_CASE) for self-reference, `Regex.escape` per phrase. Trimming with `trim()` (Unicode whitespace).
- **D2 Model seam.** `internal interface LanguageModel { fun streamExercise(instructions, prompt, options): Flow<PartialExercise>; suspend fun respondExercise(...): GeneratedExercise; suspend fun reviewTopic(...): TopicReview }`, real `MlKitLanguageModel` over one `GenerativeModel` (`Generation.getClient()`), fake in tests. iOS has no unit test of the attempt loop; this adds one without the model.
- **D3 Instructions (A).** If the Prompt API takes no separate system instruction, send `instructions + "\n\n" + prompt` as one request; the strings themselves (and goldens) are unchanged. Streaming with structured output (A): if the stream doesn't yield partial typed objects, stream text and read the passage prefix from the partial JSON for the runaway check.
- **D4 Availability + download.** iOS is a synchronous property; `checkStatus()` is `suspend`. `ModelAvailabilityProvider { val availability: StateFlow<ModelAvailability>; suspend fun refresh(); suspend fun download() }`; `download()` collects the ML Kit download flow into `Downloading(progress)` then `refresh()`. Needs `ModelAvailability` reshaped (Q3). Nothing in the app needs `INTERNET`: AICore downloads (A).
- **D5 Errors.** `ExerciseRejection` is an enum (Kotlin enums can't extend `Throwable`), thrown wrapped in `ExerciseRejectedException`. Localized error copy stays in resources; data classes carry no `Context`.
- **D6 ContentLanguage.** Device language from `LocaleList.getDefault()[0]` (reflects a per-app language, as `Locale.preferredLanguages` does on iOS). Name and ICU id via `ULocale` (`addLikelySubtags` for the default-script check; extensions stripped). Supported set is a hard-coded allowlist (Q4); ML Kit has no `supportsLocale` (A).
- **D7 Signposts.** `OSSignposter` → `android.os.Trace.beginAsyncSection/endAsyncSection` (API 29, no new dependency), names "Exercise"/"Exercise attempt". Or drop (Q8).

## Open questions for the user

1. ML Kit surface is unverified (not cached locally). Approve: task 1 resolves 1.0.0-beta5/alpha2 and records the real API in `changes.md`; fallbacks D3 apply automatically; stop and ask if `maxOutputTokens` can't reach 1200 (the Prompt API has had a low output cap, which would truncate most passages), if `@Generable` lacks a list-count guide (`.count(3)`), or if KSP 2.3.12 can't run the schema compiler.
2. `DOWNLOADABLE`: (a) start the download automatically at launch and show progress on the unavailable screen (closest to iOS "the system gets it ready"), or (b) a "Download" button? Recommend (a).
3. Reshape `ModelAvailability` (domain): `Available`, `Downloadable`, `Downloading(fraction: Float?)`, `Unavailable(reason)` with reasons `DEVICE_NOT_ELIGIBLE` (status `UNAVAILABLE`), `DOWNLOAD_FAILED`, `OTHER` (exception from `checkStatus`); drop `APPLE_INTELLIGENCE_NOT_ENABLED` and `MODEL_NOT_READY`. Approve, or keep iOS cases and map `DOWNLOADING` → `MODEL_NOT_READY`?
4. Content languages: which languages does Gemini Nano officially support for the Prompt API? Until known, allowlist `en` only (others fall back to English, as iOS does for unsupported ones), or `en/es/pt` to match the app's UI languages?
5. Keep Apple's locale phrase ("The person's locale is es_ES.") verbatim for now and measure in phase 9? Recommend keep (parity; port plan "Known differences").
6. ICU names `zh-Hant` "Chinese (Traditional)" where Apple says "Chinese, Traditional": accept ICU and adjust those two test expectations, or hard-code Apple's form? Recommend ICU (only reachable if Chinese is supported).
7. Instrumented tests need `androidx.test:runner` + `androidx.test.ext:junit`, not on the approved list (`testInstrumentationRunner` is set but unresolved). Approve both as `androidTestImplementation`, or rely on them transitively through the approved `compose-ui-test-junit4`? Recommend approve explicitly.
8. Signposts: `android.os.Trace` (D7) or drop?
9. iOS logs topic text with default (private) redaction; `Log` doesn't redact. Log topic text only when `BuildConfig.DEBUG`? Recommend yes.

## Acceptance criteria

1. Every file in the file map exists; `com.google.mlkit` imports only in `data/genai/` (detekt `ForbiddenImport` green).
2. The five prompt goldens match the iOS files byte for byte; `@Guide` descriptions equal the iOS strings.
3. All ported tests above (ExerciseRepair, 19 deferred Exercise, prompts, ContentLanguage, TopicValidator, availability mapping, repository, 5 deferred vocabulary) and the Android-only `ExerciseGeneratorTests` and provider tests pass.
4. Generation: 3-topic cap, temperature 0.8, 1200 max tokens, streaming runaway check, app-side shuffle, `CancellationException` never swallowed.
5. `GeneratedExercise` and `TopicReview` are in `rules.keep`; `assembleRelease` (R8) builds.
6. Merged manifest has no `INTERNET` (`verify*NoInternetPermission` passes); only approved (or newly approved) dependencies.
7. Stubs ported to `app/src/test/.../support/`; dispatchers injected, no `println`.
8. `./gradlew assembleDebug`, `./gradlew :domain:test testDebugUnitTest`, `./gradlew detekt ktlintCheck lintDebug` pass; `connectedDebugAndroidTest` run on a supported device, or reported as left for the user; `changes.md` lists D1-D7, the verified ML Kit API, answers to Q1-Q9; `docs/learning/phase-3.md` written.

## Tasks

1. Gradle: `implementation(libs.mlkit.genai.prompt)`, `ksp(libs.mlkit.genai.schema.compiler)`, androidTest deps per Q7. Build, then inspect the resolved AAR (`GenerativeModel`, `FeatureStatus`, `DownloadStatus`, request builder, stream type, `GenAiException` codes, `@Generable`/`@Guide`); write findings into `changes.md`. Stop if Q1 conditions hit. `assembleDebug` passes.
2. Domain reshape of `ModelAvailability` (Q3) + its `:domain` tests. Tests pass.
3. `PromptVocabulary.kt`, `ContentLanguage.kt`, strings; `PromptVocabularyTests`, `ContentLanguageTests`. Tests pass.
4. `ExerciseRepair.kt`; `ExerciseRepairTests`. Tests pass.
5. `GeneratedExercise.kt` (`@Generable`), `ExerciseValidation.kt`, keep rules; `GeneratedExerciseTests`. Tests pass.
6. `ExerciseGenerator.kt` (strings), goldens copied; `ExerciseGeneratorPromptTests`. Tests pass.
7. `LanguageModel.kt`, `MlKitLanguageModel.kt`, `generate()`; `FakeLanguageModel`, `ExerciseGeneratorTests`. Tests pass.
8. Repository interfaces, `GenAiExerciseRepository`, `TopicReview.kt`, `TopicValidator.kt`, `GenAiTopicReviewRepository`; `TopicValidatorTests`, `GenAiExerciseRepositoryTests`; the three stubs. Tests pass.
9. `ModelAvailabilityMapping.kt`, `GenAiModelAvailabilityProvider.kt`; mapping + provider tests. Tests pass.
10. androidTest: `@LiveModel`, three live suites, `RegexAndWordCountDeviceTests`. `assembleDebugAndroidTest` passes; run `connectedDebugAndroidTest` if a supported device is attached.
11. Run the three commands + `assembleRelease`; fix lint; write `changes.md` and `docs/learning/phase-3.md` (`@Generable` via KSP, AICore/`checkStatus`/download flow, `suspend`/`Flow` streaming vs `AsyncSequence`, ICU on device vs JDK in unit tests).

## Decisions

User answers, 2026-10-30:

- Q2: (a) start the download automatically at launch and show progress on the unavailable screen.
- Q3: Android shape approved: `Available`, `Downloadable`, `Downloading(fraction: Float?)`, `Unavailable(reason)` with `DEVICE_NOT_ELIGIBLE`, `DOWNLOAD_FAILED`, `OTHER`.
- Q4: allowlist `en`, `es`, `pt`; others fall back to English. Task 1 checks ML Kit's documented Prompt API languages and narrows the list (recorded in `changes.md`) if one of these is unsupported.
- Q7: `androidx.test:runner` and `androidx.test.ext:junit` approved as `androidTestImplementation`, declared in the version catalog.
- Q1: approved. Task 1 verifies the ML Kit API and applies D3 fallbacks automatically; it stops for the user if `maxOutputTokens` can't reach 1200, if there is no list-count guide (`.count(3)`), or if KSP can't run the schema compiler.
- Q5: keep Apple's locale phrase word for word until phase 9.
- Q6: use ICU's "Chinese (Traditional)"; adjust those two test expectations.
- Q8: port signposts to `android.os.Trace` (D7).
- Q9: log topic text only when `BuildConfig.DEBUG`.

