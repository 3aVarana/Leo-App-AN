# Phase 3: On-device model, review

Reviewer did not write this code. Base `bb3cbf7`, working tree on `port/phase-3-on-device-model` (tracked diff plus untracked files). iOS references are relative to `../Leo`.

## What was checked and holds

- **Prompts verbatim.** The five goldens are byte-identical to `LeoTestsSnapshots/ExerciseGeneratorPromptTests/promptSnapshot-_.<group>.txt` (`cmp`). The string literals were also compared by script after joining Swift `\` continuations: `PromptVocabulary` (all audiences, styles and five `promptHint`s), the six `GeneratedExercise` `@Guide`s and the three `TopicReview` `@Guide`s match exactly. The interpolated strings (`wordRange`, the `STORY`/`INFORMATIVE`/`OPEN` passage kinds, the topic-review instructions and prompt) were compared by hand and match. The KSP output `GeneratedExercise_GeneratedProvider` carries the same descriptions and `minItems = maxItems = 3`.
- **Validation order** (`GeneratedExercise.swift:51-88`) is unchanged in `ExerciseValidation.kt:23-55`: clean, dedupe key, passage pipeline, emptyField, then distractors, passageLength, selfReference, standoutAnswer, matchingPunctuation, shuffle, title, explanation.
- **Repair regexes** (`ExerciseRepair.swift:31-178`) are copied exactly. The one rewrite is `(?i:title|t[ií]tulo)` → `[Tt][Ii][Tt][Ll][Ee]|[Tt][IiÍí][Tt][Uu][Ll][Oo]`, which matches the same strings. `\s`, `\p{..}`, `(?m)^` and lookarounds behave the same in ICU (Android) and in NSRegularExpression. Self-reference uses `IGNORE_CASE` (Kotlin adds `UNICODE_CASE`) and `Regex.escape`. The U+2029 paragraph marker is the real character (`ExerciseRepair.kt:68-69`).
- **Attempt loop.** It tries at most 3 topics in order, checks `ensureActive()` before `onAttempt`, uses temperature 0.8 and 1200 max tokens, abandons the stream once `wordCount > acceptedWordCount.last`, treats an empty stream as a failure, and throws `ExerciseGenerationError` when all attempts fail. `CancellationException` is rethrown before the catch-all (`ExerciseGenerator.kt:99-160`). The answers are shuffled by the app with an injectable `Random`.
- **`ContentLanguage`, `TopicValidator.outcome`, refusal handling, availability mapping** are ported faithfully apart from the documented deviations (ICU names, `und`, Q3 reshape).
- **Strings.** `exercise_generation_failed` and `topic_review_generic_rejection` match `Localizable.xcstrings` in en, es and pt-BR.
- **Tests.** Every iOS case is ported, with the same inputs: `ExerciseRepairTests` 23/23, the 19 deferred `ExerciseTests`, `ExerciseGeneratorPromptTests`, `ContentLanguageTests`, `TopicValidatorTests`, availability, the repository test, and the 5 deferred vocabulary tests. The Android-only generator tests cover the cap, `onAttempt` ordering, runaway, cancellation and non-cancellation.
- **Keep rules.** ML Kit discovers providers with `ServiceLoader` (`zzaac`: `ServiceLoader.load(GenerableProvider)`). KSP writes `META-INF/services/...GenerableProvider`, and the parser reads fields by reflection (`zzacu`: `getDeclaredFields`). `-keep class <model> { *; }` plus keeping `GenerableProvider` implementors covers both. This is not yet exercised by R8 because nothing references the classes (noted in changes.md).
- **Privacy and dependencies.** `INTERNET` is still stripped. The only new direct dependencies are the approved ML Kit pair and the Q7 test libraries.
- **Commands run by the reviewer:** `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`: BUILD SUCCESSFUL, 238 tests, 0 failures (JUnit XML). `connectedDebugAndroidTest` was not run (no device).

## Blocking

### B1. D8: `-Xskip-metadata-version-check` turns off the check for the Kotlin stdlib, not only for ML Kit
`app/build.gradle.kts:105-109`

- `debugRuntimeClasspath` resolves `org.jetbrains.kotlin:kotlin-stdlib:2.2.10 -> 2.4.0`. `:app` is now compiled by Kotlin 2.2.10 against a 2.4 stdlib with the version check off. Kotlin reads one minor version ahead (2.2 reads 2.3); 2.4 is two ahead, and JetBrains documents this flag as "may produce incorrect code".
- changes.md says ML Kit's API is "plain Java signatures". It isn't. The code uses ML Kit API that only Kotlin metadata describes: `suspend checkStatus()`, `download(): Flow`, `generateContentStream(): Flow`, the `generateContentRequest { }` DSL, and `KClass` in `GenerableProvider`.
- It compiles and the tests pass, so today's failure mode is more likely a confusing compile error than wrong bytecode. But the flag is module-wide and permanent: it will also hide the next dependency that needs a newer compiler.
- **Fix:** the user decides, before merge.
  - Recommended: upgrade Kotlin to 2.4.x in its own commit, then drop the flag. That also unblocks kotlinx-serialization 1.12; KSP 2.3.x no longer depends on the Kotlin version.
  - Otherwise: the user accepts the flag explicitly, with a `libs.versions.toml` comment linking to this item and a phase-4 task to remove it.

## Should fix

### S1. A cancellation that ML Kit reports as `GenAiException(CANCELLED)` is swallowed
`ExerciseGenerator.kt:138-141`, `:112`; `GenAiModelAvailabilityProvider.kt:73-78`, `:102-106`

- `GenAiException.ErrorCode.CANCELLED = 7` exists in genai-common. If the AICore call surfaces a cancelled collector as that exception instead of `CancellationException`, the catch-all logs "Generation failed". On the third topic, `generate` then throws `ExerciseGenerationError` from a cancelled coroutine. In the provider, a cancelled `download()` would become `Unavailable(DOWNLOAD_FAILED)`.
- iOS: `ExerciseGenerator.swift:157-159` (a cancellation always ends as cancellation).
- **Fix:**
  - Call `currentCoroutineContext().ensureActive()` first inside each catch-all, and once more before `throw ExerciseGenerationError()`.
  - Add a generator test whose stream throws a plain exception after the caller is cancelled.

### S2. The JSON-format suffix sent with every exercise request has no golden test
`MlKitLanguageModel.kt:99-109` (`EXERCISE_FORMAT`, appended at `:34`)

- CLAUDE.md: "Prompt text is covered by golden-file tests. A prompt change updates them on purpose."
- With D3, the text the model actually receives is `prompt + EXERCISE_FORMAT`. The goldens only cover `prompt()`, so an edit to the suffix (or to a `@Guide` constant, which feeds it) goes unnoticed.
- **Fix:**
  - Make the suffix `internal` and move it into a pure function: `exerciseRequestPrompt(prompt)`, in `ExerciseGenerator` or `GeneratedExercise`.
  - Add a golden `app/src/test/resources/golden/ExerciseGeneratorPromptTests/exerciseFormat.txt`, or one golden of the full text for one group. Keep the five iOS goldens untouched.

### S3. The download state can get stuck at `Downloading`
`GenAiModelAvailabilityProvider.kt:81-107`; `ModelAvailabilityProvider.kt:23-26`

- **(a)** `refresh()` only runs on `Completed`. If the ML Kit flow completes without a terminal event, or AICore still reports `DOWNLOADING` right after `Completed`, the state stays `Downloading` and nothing reads it again.
- **(b)** `prepare()` only downloads when the status is `Downloadable`. If AICore is already downloading (app killed mid-download, or another app triggered it), the state is `Downloading(null)` forever, with no progress. Q2(a) asks for progress on the unavailable screen.
- **Fix:**
  - Call `refresh()` after `collect` returns normally (instead of, or as well as, on `Completed`).
  - In `prepare()`, also call `download()` when the status is `Downloading`; ML Kit's `download()` attaches to the running download. Confirm on a device and record it in qa.md.
  - Add tests: a flow that ends without `Completed`, and `prepare()` from `DOWNLOADING`.

### S4. The live-model tests run every case under one time limit
`livemodel/ExerciseGeneratorLiveTests.kt:24,33,44`; `livemodel/PassageLengthLiveTests.kt:32,45`

- iOS `.timeLimit` applies to each parameterized case (`LeoTests/LiveModel/ExerciseGeneratorLiveTests.swift:8,14,19`; `PassageLengthLiveTests.swift:11,22`).
- The ports loop inside one `@Test`:
  - `generatesUsableExercise`: 5 generations in 1 minute.
  - `generatesEverySkill`: 10 generations in 1 minute.
  - `passageLengths`: 30 generations in 2 minutes.
- They will time out on a real device for reasons unrelated to the prompts. The first failing case also hides the rest.
- **Fix:** use JUnit 4's `@RunWith(Parameterized::class)`, one case per row, with the same `LiveModelRule` (per-case timeout, per-case report). That is the closest match to `@Test(arguments:)`.

### S5. Parser robustness for D3
`PartialJson.kt:66`, `:90-109`; `MlKitLanguageModel.kt:43`

- **Malformed `\u` escape.** On a malformed `\uXXXX`, `digits.toInt(HEX)` throws `NumberFormatException` mid-stream. The whole attempt is lost as "Generation failed". Use `toIntOrNull()` and keep the raw characters when it is null.
- **Answer count.** iOS's schema guarantees exactly 3 `incorrectAnswers` (`GeneratedExercise.swift:28`, `.count(3)`). Unconstrained JSON can return 4 or more, which `toExercise` rejects as `distractors`, costing an attempt iOS would never lose.
  - Emulate the schema's `maxItems` at the parse layer with `incorrectAnswers.take(GeneratedExercise.INCORRECT_ANSWER_COUNT)`. Validation stays the iOS one.
- **Chunk accumulation.** The rule in `MlKitLanguageModel.kt:43` (deltas, with a guard for cumulative chunks) is untested and unverified on a device. Extract it as a pure `PartialJson.append(text, chunk)` and unit-test both delta and cumulative streams.
- **Not a code fix:** dialogue with unescaped `"` inside the passage makes the JSON invalid. Phase 9 should measure the share of attempts lost to `complete == null` against typed (non-streamed) generation. This is a user decision between iOS's streaming cut-off and ML Kit's schema guarantee.

### S6. Treating `RESPONSE_GENERATION_ERROR` as a refusal is unverified and silent
`MlKitLanguageModel.kt:68-70`

- iOS maps only `guardrailViolation` and `refusal` to the generic rejection (`TopicValidator.swift:57-68`).
- Error 15 might also cover ordinary inference failures. Those would then show the reader "That topic isn't a good fit" where iOS shows an error.
- **Fix:**
  - Log the code and message when mapping (`Log.w(TAG, "Treated as refusal: ${e.errorCode} ${e.message}")`).
  - Add "gory horror for 6-8 returns a rejection, and logcat shows which code" to the phase's qa.md, for the device run.

## Nit

- **N1.** There are two `Generation.getClient()` clients (`MlKitLanguageModel.kt:26`, `GenAiModelAvailabilityProvider.kt:44`), a process-global `MlKitLanguageModel.shared` (`:93`), and defaults `model = MlKitLanguageModel.shared` in `GenAiExerciseRepository.kt:14` and `GenAiTopicReviewRepository.kt:17`. With manual DI, the `AppContainer` (phase 4) should own one `GenerativeModel` and pass it to all three. Dropping the defaults stops a test from reaching ML Kit by accident.
- **N2.** `client.getAvailableFeatures()` runs on every request (`MlKitLanguageModel.kt:80`). Read it once per instance.
- **N3.** detekt `ForbiddenImport` only covers `**/features/**` (`config/detekt/detekt.yml:16-26`), so acceptance criterion 1 ("`com.google.mlkit` only in `data/genai/`") holds by convention, not by lint. Acceptable; note it in changes.md.
- **N4.** `TopicValidatorLiveTests.kt:35` checks `topic.trim()`. iOS checks whitespace and punctuation (`TopicValidatorLiveTests.swift:19`). Reuse the trim from `TopicValidator.outcome`.
- **N5.** `androidTestImplementation(libs.kotlinx.coroutines.test)` (`app/build.gradle.kts:130`) is unused. Drop it.
- **N6.** `PartialJson.stringValue` compiles its `Regex` on every chunk (`PartialJson.kt:21`). Hoist it for `"passage"`.
- **N7.** `ExerciseRepair.kt:110` uses `trim()` where Swift trims `.whitespaces` only (`ExerciseRepair.swift:95`). It differs only for an answer with a newline right before its final period. Harmless, but `trimEnd(' ', '\t')` would be exact.
- **N8.** `StubExerciseRepository.kt:92` uses `split(" ")`, which keeps empty pieces. Swift's `split(separator:)` drops them, so `"S  F"` throws here. Use `split(" ").filter { it.isNotEmpty() }` or `split(Regex(" +"))`.
- **N9.** For the user's information: ML Kit pulls in `guava:32.0.0-jre`, `kotlinx-coroutines-guava`, `kotlinx-coroutines-reactive` and `reactive-streams` transitively. They are not on the approved list but unavoidable. Re-check APK size after R8 once phase 4 references the classes.

## Verdict

**Changes needed.** B1 needs the user's decision (upgrade Kotlin, or accept the flag explicitly). S1 to S5 are small code and test changes. S6 is a log line plus a QA item. The port itself is faithful: prompts, guides, goldens, regexes, validation order and every iOS test match.

## Round 2

Checked against the working tree after the implementer's "Review follow-up" section in changes.md.

**Commands:**

- `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`: BUILD SUCCESSFUL.
- `./gradlew :domain:test testDebugUnitTest --rerun`: BUILD SUCCESSFUL. `:app` tests re-executed (fresh JUnit XML); 246 tests, 0 failures.

**Goldens:** the five iOS goldens are still byte-identical to `LeoTestsSnapshots/ExerciseGeneratorPromptTests/promptSnapshot-_.<group>.txt` (`cmp`). The new `exerciseFormat.txt` is Android-only and separate.

### Findings from round 1

- **B1, resolved.**
  - Kotlin is 2.4.0 and kotlinx-serialization is 1.12.0. The `-Xskip-metadata-version-check` flag and its `kotlin {}` block are gone from `app/build.gradle.kts`, and no copy of the flag remains in the build files.
  - KSP 2.3.12 and AGP 9.4.1 are unchanged. The `:app` compilation shows no warnings.
  - Regressions checked: phase 2's DataStore JSON and Room tests and the Roborazzi verification pass on 1.12, and detekt and ktlint pass. No regression found.
- **S1, resolved in code.** `ensureActive()` now runs first in the catch-alls (`ExerciseGenerator.kt` attempt; `GenAiModelAvailabilityProvider.kt` refresh and download) and before `throw ExerciseGenerationError()`. The test is weak; see R2-N1.
- **S2, resolved.** `exerciseRequestPrompt` is internal (`GeneratedExercise.kt:62-73`) and used at `MlKitLanguageModel.kt:34`. It is covered by the `exerciseFormat` golden, whose text has the six guides verbatim.
- **S3, resolved.**
  - `download()` now refreshes after the flow ends unless it already failed, and `Completed` no longer triggers it alone.
  - `prepare()` also calls `download()` from `Downloading`.
  - Both behaviors have tests. Whether `download()` attaches to a running download is still a device QA item.
- **S4, resolved.** Both live suites are JUnit4 `Parameterized`, one row per case, each with its own `LiveModelRule` limit (1 and 2 minutes). The rows match iOS: 5 + 10 + 1 = 16 for the generator (changes.md says 20, see R2-N2) and 3 x 5 for passage length. They compile; not run (no device).
- **S5, resolved.**
  - A malformed `\u` escape now keeps its raw characters (`toIntOrNull`).
  - `incorrectAnswers.take(3)` at parse time (`PartialJson.kt:123-127`).
  - The chunk rule is extracted as `PartialJson.append`, with delta and cumulative tests.
- **S6, resolved in code.** The refusal mapping logs its code (`MlKitLanguageModel.kt:68-74`), and the QA items are listed in changes.md. Copy them into `qa.md` when that is written.
- **Nits.** N2, N4, N5, N6, N7 and N8 are done and verified (`ExerciseRepair.kt:110`, `StubExerciseRepository.kt:92`). N1, N3 and N9 are deferred to phase 4 with reasons, which is acceptable.

### New findings

No Blocking or Should fix.

- **R2-N1. The S1 test passes even without the S1 fix.** `ExerciseGeneratorTests.kt:223-243`
  - The test cancels during the first `onAttempt`. The loop's existing `ensureActive()` before the second topic would already turn that into a `CancellationException`, so the test does not exercise either new `ensureActive()`.
  - Fix: cancel in the third `onAttempt` (`if (attempted == 3) cancel()`). Then only the catch-all or post-loop `ensureActive()` stops `ExerciseGenerationError` from escaping.
- **R2-N2. Duplicate test names and a wrong count.** `ExerciseGeneratorLiveTests.kt:64`
  - The row `NINE MAIN_IDEA english` appears twice: once from the per-group rows and once from the every-skill rows. iOS has the same overlap across its two functions.
  - With the name pattern `"{0} {1} {2}"`, the two rows get identical test names. Some reporters (AndroidJUnitRunner, AGP's report) merge or overwrite duplicates.
  - Fix: prefix the pattern with `{index}`.
  - changes.md says 20 rows; there are 16. Correct the count.
- **R2-N3. Misplaced doc comment.** `PartialJson.kt:13-17`: the KDoc meant for `stringValue` now sits on `passageMarker`. Move it back.
- **R2-N4. Signed hex digits are accepted.** `PartialJson.kt:80`: `toIntOrNull(16)` accepts a sign, so `\u-001` decodes as `￿` instead of being kept raw. Harmless; `digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }` before parsing would be exact.
- **R2-N5. A download can still end at `Downloading`.** `GenAiModelAvailabilityProvider.kt`: if AICore still reports `DOWNLOADING` right after the flow ends, the state stays `Downloading(null)` until the next `refresh()`. Phase 4 should call `refresh()` on resume (the interface KDoc already says so); nothing to change here.

### Verdict (round 2)

**Approve.** All round-1 findings are resolved, and the remaining items are nits. Still left for the user: the device run (`connectedDebugAndroidTest`, the live suites, `RegexAndWordCountDeviceTests`) and the QA items listed in changes.md.
