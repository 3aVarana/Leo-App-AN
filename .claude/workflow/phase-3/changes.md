# Phase 3: On-device model, changes

No stop condition of Q1 was hit (see "Verified ML Kit API"). Nothing committed. `connectedDebugAndroidTest` was NOT run: `adb devices` lists no device, so the live-model suites and `RegexAndWordCountDeviceTests` are left for the user.

## Files

Added, `app/src/main/java/com/victorarana/leo/data/`:
- `repositories/ExerciseRepository.kt` (+ `ExerciseGenerationError`), `TopicReviewRepository.kt`, `ModelAvailabilityProvider.kt` (D4, adds `prepare()` for Q2a).
- `genai/`: `ContentLanguage`, `PromptVocabulary`, `ExerciseRepair` (+ `ExerciseRejection`, `ExerciseRejectedException`), `GeneratedExercise` (`@Generable`), `ExerciseValidation` (`GeneratedExercise.toExercise`), `ExerciseGenerator`, `LanguageModel` (D2, with `GenerationOptions`, `ExerciseSnapshot`, `ModelRefusedException`), `MlKitLanguageModel`, `PartialJson`, `TopicReview` (`@Generable`), `TopicValidator`, `GenAiExerciseRepository`, `GenAiTopicReviewRepository`, `ModelAvailabilityMapping`, `GenAiModelAvailabilityProvider` (+ `ModelStatusSource`, `MlKitModelStatusSource`, `ModelDownloadEvent`).

Added tests: `app/src/test/.../data/genai/` `ContentLanguageTests`, `PromptVocabularyTests`, `ExerciseRepairTests`, `GeneratedExerciseTests` (the 19 deferred `ExerciseTests`), `ExerciseGeneratorPromptTests` (+ 5 goldens in `app/src/test/resources/golden/ExerciseGeneratorPromptTests/`, copied byte for byte from the iOS files and matched exactly), `ExerciseGeneratorTests` (Android-only), `PartialJsonTests` (Android-only), `TopicValidatorTests`, `GenAiExerciseRepositoryTests` (incl. the deferred `generationErrorDescription`, in 3 locales), `ModelAvailabilityMappingTests`, `GenAiModelAvailabilityProviderTests`. Support: `FakeLanguageModel`, `GeneratedExerciseFixture`, `StubExerciseRepository` (+ `ExerciseRepositoryFactorySpy`), `StubTopicReviewRepository`, `StubModelAvailabilityProvider`.
The deferred `AgeGroupTests` 2 and `DefaultTopicsTests` 3 are in `PromptVocabularyTests` (+ `GenAiExerciseRepositoryTests` for `generationErrorDescription`).

Added instrumented, `app/src/androidTest/java/com/victorarana/leo/`: `livemodel/{LiveModelRule (with @LiveModel),ExerciseGeneratorLiveTests,TopicValidatorLiveTests,PassageLengthLiveTests}`, `data/genai/RegexAndWordCountDeviceTests`.

Changed: `gradle/libs.versions.toml` (`androidx.test:runner` 1.7.0, `androidx.test.ext:junit` 1.3.0, Q7), `app/build.gradle.kts` (deps), `app/src/main/keepRules/rules.keep`, three `strings.xml` (`exercise_generation_failed`, `topic_review_generic_rejection`, copied from `Localizable.xcstrings`), `domain/.../ModelAvailability.kt` (Q3).

## Verified ML Kit API (genai-prompt 1.0.0-beta5, genai-common 1.0.0-beta5, genai-schema(-compiler) 1.0.0-alpha2, read with javap from the resolved jars)

- `Generation.getClient(): GenerativeModel` (also `getClient(GenerationConfig)`; no `Context` needed).
- `GenerativeModel`: `suspend checkStatus(): Int` (`FeatureStatus.UNAVAILABLE/DOWNLOADABLE/DOWNLOADING/AVAILABLE`), `download(): Flow<DownloadStatus>` (`DownloadStarted(bytesToDownload)`, `DownloadProgress(totalBytesDownloaded)`, `DownloadCompleted`, `DownloadFailed(e)`), `generateContent(GenerateContentRequest)`, `generateContentStream(GenerateContentRequest): Flow<GenerateContentResponse>` (text in `candidates[0].text`), `generateContent(GenerateTypedContentRequest<T>): GenerateTypedContentResponse<T>` (`candidates[0].response: T?`, `finishReason`), `getAvailableFeatures()` (`Feature.SYSTEM_PROMPT/STRUCTURED_OUTPUT/...`), `getTokenLimit()`, `warmup()`. `isSystemPromptAvailable()` is deprecated in favor of `getAvailableFeatures()`.
- Request: `generateContentRequest(SystemInstruction, TextPart) { temperature; maxOutputTokens; topK; seed; candidateCount; ... }`. Client-side checks: temperature 0..1, `maxOutputTokens >= 0` with **no upper bound**, so 1200 and 0.8 are accepted (Q1 condition 1 not hit on the client; whether AICore caps output server-side could not be checked without a device). `GenerateContentRequest` has a system instruction, so D3's "merge into one prompt" fallback is only used when `SYSTEM_PROMPT` is not an available feature.
- Structured output: `generateTypedContentRequest(request, Klass::class)`; **no streaming variant** (typed output is `generateContent` only). Streaming gives text chunks only.
- `@Generable(description)` and `@Guide(description, minItems, maxItems, minimum, maximum, enumValues)`: `minItems = 3, maxItems = 3` is the `.count(3)` equivalent (Q1 condition 2 not hit). The `genai-schema-compiler` KSP processor runs under the project's KSP 2.3.12 and generates `GeneratedExercise_GeneratedProvider` with the six descriptions and `minItems = 3` (Q1 condition 3 not hit). Annotations must be on constructor parameters (`@param:Guide`).
- `GenAiException.errorCode` constants are in `GenAiException.ErrorCode` (`RESPONSE_GENERATION_ERROR` 15, `STRUCTURED_OUTPUT_RESPONSE_ERROR` -105, `NOT_AVAILABLE` 8, `BUSY` 9, ...). There is no guardrail/refusal code as such; `RESPONSE_GENERATION_ERROR` is treated as the refusal (A, unverified on a device). Other errors are rethrown.
- ML Kit's docs list no languages for the Prompt API (only a third-party page says English and Korean were validated). The en/es/pt allowlist is kept as decided in Q4 and **could not be narrowed from documentation**; phase 9 should measure Spanish and Portuguese on a device.
- Docs: input under about 4000 tokens, "avoid more than 4K output tokens", AICore enforces a per-app inference quota and rejects background use (`BACKGROUND_USE_BLOCKED`).

## Deviations

From the plan:
- **D8 Kotlin 2.4 (resolved, review B1).** ML Kit is compiled with Kotlin 2.4. The first version of this phase worked around it with `-Xskip-metadata-version-check`; per your decision the project is now on Kotlin 2.4.0 and the flag is gone (see "Review follow-up").
- **D3 streaming.** The plan's fallback applied: the Prompt API has no streaming structured output, so exercises are streamed as plain text. `MlKitLanguageModel` appends a JSON-format instruction (the six `@Guide` texts, shared constants in `GeneratedExercise`) to the prompt and `PartialJson` reads the partial `passage` for the runaway check and the finished object at the end. Consequence: for exercises the output is **not constrained by the schema** (iOS and the topic review are); a malformed or truncated JSON counts as a failed attempt and the next topic is tried. `GeneratedExercise` is still `@Generable` (schema generated, kept by R8) and is the type used everywhere, but no typed request is made for it. The prompts and goldens are unchanged. Stream chunks are treated as deltas, with a guard for cumulative chunks; this is unverified on a device.
- **D2** `LanguageModel` is public (not internal) because public repository constructors take it; it has `streamExercise` and `reviewTopic` only (no `respondExercise`, nothing needs it).
- **D4** `ModelAvailabilityProvider` also has `prepare()` (refresh, then download if `Downloadable`: Q2a). Until the first `refresh()` the provider reports `Downloading(null)` (indeterminate), so the first frame doesn't flash an error. `GenAiModelAvailabilityProvider` takes a `ModelStatusSource` (fake in tests).
- **D5** `TopicValidator.review` and `GenAiTopicReviewRepository` take the localized generic rejection as a parameter (`() -> String`), as the data layer has no `Context`; the container will pass `context.getString(R.string.topic_review_generic_rejection)` in phase 4.
- **D1** The leading-title `(?i:title|t[ií]tulo)` is written as explicit character classes instead of `(?iu:...)`: ICU's regex (Android) doesn't document the `u` embedded flag, and the classes behave the same on both engines. `RegexAndWordCountDeviceTests` repeats the Unicode cases on a device.
- **D7** `Trace.beginAsyncSection/endAsyncSection` carry names only: Android's API has no arguments, so the outcome strings of the signposts ("accepted", "rejected: ...") and the "First snapshot" event are in the logs only (the log lines are the iOS ones).
- **D6** `ContentLanguage` holds an ICU `ULocale`, so its unit tests (and everything using a `ContentLanguage`) run under Robolectric, because the JVM has no `android.icu`. `ContentLanguage.current(preferred, supportedLanguages)` takes the locale as a parameter for tests; default is `LocaleList.getDefault()[0]`.
- **Tests**: `GeneratedExerciseTests`, `ExerciseRepairTests` and the generator tests are Robolectric classes for the same reason.

From iOS behavior:
- `ContentLanguage.name` for `und`: iOS says "Unknown language"; ICU erases `und` to an empty language, so Android says "English" (test `undeterminedLanguage` documents it). Not reachable from a device language.
- Chinese Traditional is "Chinese (Traditional)" (ICU) instead of Apple's "Chinese, Traditional" (Q6); only reachable if Chinese were allowlisted.
- Topic text in error logs only when `BuildConfig.DEBUG`, `<private>` otherwise (Q9).
- Self-reference patterns use `RegexOption.IGNORE_CASE`; `trim()` is Kotlin's whitespace, `isPunctuation()` is the Unicode `P*` categories (Swift `CharacterSet.punctuationCharacters`).

## Answers applied (Q1-Q9)

Q1 verified as above, no stop. Q2a `prepare()`. Q3 domain `ModelAvailability` reshaped. Q4 en/es/pt kept (could not verify). Q5 locale phrase verbatim. Q6 ICU name. Q7 the two test libs in the catalog. Q8 `Trace`. Q9 debug-only topic logging.

## Commands

- `./gradlew assembleDebug` after task 1: failed (metadata, D8), passed after the flag.
- `./gradlew ktlintFormat assembleDebug assembleRelease assembleDebugAndroidTest :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --console=plain`: BUILD SUCCESSFUL. `:domain` 80 tests, `:app` 158 tests (JUnit XML counts), 0 failures. First runs of detekt reported MagicNumber, ReturnCount, UseCheckOrError, MaxLineLength: all fixed. Kotlin warnings about `@Guide` targets fixed with `@param:`.
- `assembleRelease` (R8) passes. Note the release mapping contains none of the `genai` classes because nothing in the app uses them yet, so the keep rules are not exercised until phase 4/5 wires the repositories; re-check then. Merged release manifest has no `INTERNET` (the `verify*NoInternetPermission` transform ran as part of the assemble tasks).
- `adb devices`: no device, so `connectedDebugAndroidTest` not run. The three live suites skip themselves unless `checkStatus() == AVAILABLE`; `RegexAndWordCountDeviceTests` needs only an emulator.

## Review follow-up (review.md)

**B1, Kotlin upgrade (separate commit: only `gradle/libs.versions.toml` and `app/build.gradle.kts`).** `kotlin` 2.2.10 -> 2.4.0 (Kotlin Gradle plugin, Compose compiler plugin and serialization plugin share this version; KSP 2.3.12 unchanged, it is independent of the Kotlin version; AGP 9.4.1 unchanged). `kotlinxSerialization` 1.11.0 -> 1.12.0, undoing phase-1 deviation 1 (and its comment). Removed `-Xskip-metadata-version-check` and its `kotlin {}` block from `app/build.gradle.kts`. No source changes were needed; no new libraries. The `libs.versions.toml` hunk for the two test libraries and the ML Kit lines belongs to the phase commit, not the upgrade commit.

Fixed:
- **S1** `ensureActive()` first in the catch-alls of `ExerciseGenerator.attempt`, `GenAiModelAvailabilityProvider.refresh/download`, and before `throw ExerciseGenerationError()`. Test `cancelledCallerIsNotReportedAsAFailureWhenTheModelThrowsAnOrdinaryException`.
- **S2** The suffix is now `exerciseRequestPrompt(prompt)` (internal, in `GeneratedExercise.kt`). New golden `golden/ExerciseGeneratorPromptTests/exerciseFormat.txt` (ours; the five iOS goldens are untouched) and test `exerciseFormatSnapshot`.
- **S3** `download()` calls `refresh()` after the flow ends (unless it already failed), not only on `Completed`; `prepare()` also calls `download()` when the status is `Downloading` (attaching to a running download is unconfirmed on a device, see QA). Tests `downloadFlowThatEndsWithoutCompletedStillRefreshes`, `prepareAttachesToADownloadAlreadyInProgress`.
- **S4** `ExerciseGeneratorLiveTests` (16 rows, named with `{index}` because one case appears twice) and `PassageLengthLiveTests` (3 topics x 5 skills) are JUnit4 `Parameterized`, one row per case with its own `LiveModelRule` limit (1 and 2 minutes). Compiled by `assembleDebugAndroidTest`; not run (no device).
- **S5** `toIntOrNull` for `\u` escapes (a malformed one keeps its characters), `incorrectAnswers` cut to 3 at parse time, chunk accumulation extracted to `PartialJson.append` with delta and cumulative tests.
- **S6** Refusal mapping logs `Log.w "Treated as refusal: error code ..., message"`; other `GenAiException`s from the review are logged with their code. **Device QA items (for qa.md):** "gory horror movies" for 6-8 returns a rejection and logcat shows which code mapped; whether `RESPONSE_GENERATION_ERROR` also covers plain inference failures; whether `download()` attaches to an in-progress download; whether stream chunks are deltas or cumulative.

Nits: done N2 (system-prompt feature read once per instance), N4 (live test trims whitespace and punctuation like iOS), N5 (unused androidTest coroutines-test dropped), N6 (passage regex hoisted), N7 (`trimEnd/trimStart` of spaces and tabs only), N8 (`scripted` ignores empty pieces). Skipped: N1 (one shared `GenerativeModel` and no `shared` defaults belong to the phase 4 `AppContainer`), N3 (noted: `com.google.mlkit` only in `data/genai/` holds by convention, since detekt `ForbiddenImport` covers only `features/`), N9 (information; re-check APK size after R8 in phase 4). Not a code fix, for the user: phase 9 should measure the share of attempts lost to `complete == null` (unescaped quotes in dialogue) against typed, non-streamed generation.

Commands: `./gradlew ktlintFormat assembleDebug assembleRelease assembleDebugAndroidTest :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --console=plain` -> BUILD SUCCESSFUL on Kotlin 2.4.0 (0 test failures).
