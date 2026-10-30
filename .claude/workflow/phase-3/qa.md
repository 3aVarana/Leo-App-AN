# Phase 3 (On-device model): QA

Branch `port/phase-3-on-device-model`, uncommitted tree, Kotlin 2.4.0. No source modified. Overall: **PASS** for everything runnable without a device. Device checks NOT RUN (`adb devices` lists no device or emulator).

## Commands (all with `--rerun-tasks`)

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew assembleRelease` | BUILD SUCCESSFUL (R8) |
| `./gradlew assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `./gradlew :domain:test testDebugUnitTest` | BUILD SUCCESSFUL, 42 tasks. `:domain` 80 tests, `:app` 166 tests (JUnit XML), 0 failures/errors/skipped. Roborazzi verify ran |
| `./gradlew detekt ktlintCheck lintDebug` | BUILD SUCCESSFUL, 55 tasks |

Note: my first batched run of the last two failed in under a second (daemon/config-cache start-up right after the previous builds, no task ran); re-run on its own, both passed.

## Checks
- Goldens: `cmp` of the five `LeoTestsSnapshots/ExerciseGeneratorPromptTests/promptSnapshot-_.<group>.txt` (adult, fifteen, nine, six, twelve) against `app/src/test/resources/golden/ExerciseGeneratorPromptTests/promptSnapshot-<group>.txt`: all five byte-identical. Extra Android-only golden `exerciseFormat.txt`.
- Manifests: 0 `android.permission.INTERNET` in every merged manifest (debug, release, debugUnitTest, debugAndroidTest, `verify{Debug,Release}NoInternetPermission`).
- Dependencies: `git diff` of `app/build.gradle.kts` adds only `mlkit.genai.prompt`, `ksp(mlkit.genai.schema.compiler)`, `androidx.test.runner` 1.7.0 and `androidx.test.ext:junit` 1.3.0 (androidTest). Catalog also moves Kotlin 2.2.10 to 2.4.0 and kotlinx-serialization 1.11.0 to 1.12.0 (approved, review B1); `-Xskip-metadata-version-check` removed.
- `println` in `app/src`: none. `com.google.mlkit` imports only in `data/genai/` (5 files + `rules.keep`).

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | File map files exist; mlkit only in `data/genai/`; ForbiddenImport green | PASS | All mapped files present in `data/{repositories,genai}`, strings in 3 locales, support fakes; mlkit grep above; detekt passed. (ForbiddenImport covers `features/` only, see changes N3) |
| 2 | Five goldens byte-identical; `@Guide` strings equal iOS | PASS | `cmp` all five; the five iOS `description:` strings compared with `GeneratedExercise.kt` constants (title guide too): identical |
| 3 | All ported and Android-only tests pass | PASS | 166 `:app` + 80 `:domain` tests, 0 failures; classes listed in changes.md ran (ExerciseRepair, GeneratedExercise, prompts, ContentLanguage, TopicValidator, mapping, repository, vocabulary, generator, PartialJson, provider) |
| 4 | 3-topic cap, temperature 0.8, 1200 tokens, runaway check, app shuffle, cancellation not swallowed | PASS | `ExerciseGenerator.kt`: `MAX_ATTEMPTS = 3` / `topics.take`, `TEMPERATURE = 0.8f`, `MAX_RESPONSE_TOKENS = 1200`, streaming `collect`; `ExerciseValidation.kt` `shuffled(random)`; `CancellationException` rethrown in generator and provider; unit test for cancelled caller passes. Real model behavior (server-side token cap, chunk shape) NOT RUN |
| 5 | Both classes in `rules.keep`; `assembleRelease` builds | PASS | `rules.keep` keeps `GeneratedExercise`, `TopicReview` and `GenerableProvider` implementations; release build passed. Caveat from changes.md: nothing references genai yet, so R8 does not exercise the rules until phase 4/5 |
| 6 | No INTERNET; only approved dependencies | PASS | See Checks |
| 7 | Stubs in `support/`; dispatchers injected; no `println` | PASS | `FakeLanguageModel`, `GeneratedExerciseFixture`, `StubExerciseRepository` (+ factory spy), `StubTopicReviewRepository`, `StubModelAvailabilityProvider` exist; no `println` |
| 8 | Three commands pass; connected tests run or left to user; changes.md D1-D7 + Q1-Q9; learning doc | PASS (connected: NOT RUN) | Commands above; `changes.md` lists D1-D8, verified API, Q1-Q9; `docs/learning/phase-3.md` exists. `assembleDebugAndroidTest` compiles the live suites and `RegexAndWordCountDeviceTests` |

## Device checks: NOT RUN (no device)
From changes.md; need a physical device with Gemini Nano (live suites skip themselves unless `checkStatus() == AVAILABLE`):
- `./gradlew installDebug` and `./gradlew connectedDebugAndroidTest` (`RegexAndWordCountDeviceTests` also runs on an emulator).
- Whether the device reports Gemini Nano as available: NOT RUN.
- "gory horror movies" for age 6-8 returns a rejection; logcat shows which `GenAiException` code mapped to the refusal.
- Whether `RESPONSE_GENERATION_ERROR` also covers plain inference failures.
- Whether `download()` attaches to an already running download (`prepare()` in `Downloading`).
- Whether stream chunks are deltas or cumulative (`PartialJson.append` handles both).
- AICore accepts `maxOutputTokens = 1200` without a server-side cap.
- Spanish and Portuguese generation quality (phase 9) and share of attempts lost to unparsable JSON.
- Spanish / font scale 2.0 / dark mode screenshots: no screens in this phase.
