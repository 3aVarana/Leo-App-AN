# Leo for Android

The Android port of Leo, a reading comprehension app whose exercises are written on the device by a language model. This project is a port: the iOS app is the specification.

## Source of truth

- The iOS app lives at `/Users/vicchorarana/Desktop/My-Apps/Leo` (`../Leo`). It is **read-only** from here: never edit, format, build or commit anything in it.
- Read its `CLAUDE.md`, `README.md` and `docs/` before porting a feature. Behavior, copy, limits, prompts and edge cases come from the Swift code and its tests, not from memory or guesses.
- When iOS behavior is ambiguous or seems wrong, ask the user. Don't "improve" it silently: parity first, Android-specific changes only when the platform requires them, and list each one in the phase's `changes.md`.
- The port runs in phases ([docs/Leo-Android-Port-Plan.md](docs/Leo-Android-Port-Plan.md)), one phase at a time, through the `/port-phase` skill.

## Platform mapping

| iOS (Leo) | Android (this project) |
|---|---|
| SwiftUI | Jetpack Compose, Material 3 components restyled to the Broadsheet design (it must not look like stock Material) |
| `@Observable` ViewModel | `androidx.lifecycle.ViewModel` exposing `StateFlow<UiState>`; views use `collectAsStateWithLifecycle()` |
| Swift concurrency, `MainActor` | Kotlin coroutines, `viewModelScope`, `Dispatchers` injected so tests can replace them |
| `NavigationStack` + `navigationDestination` | Navigation Compose with type-safe `@Serializable` routes |
| `MyApp` composition root | `LeoApplication` + a hand-written `AppContainer` (manual DI, no Hilt) |
| FoundationModels, `@Generable` | ML Kit GenAI Prompt API (Gemini Nano via AICore) + Structured Output: `@Generable` / `@Guide` with the `genai-schema-compiler` KSP processor |
| `SystemLanguageModel.availability` | `generativeModel.checkStatus()`: `UNAVAILABLE` / `DOWNLOADABLE` / `DOWNLOADING` / `AVAILABLE` |
| `UserDefaults` JSON under `readerPreferences` | DataStore, the same JSON (kotlinx.serialization) under the same key name |
| SwiftData round history + schema versions | Room, `exportSchema = true`, a `Migration` plus a migration test for every schema change once a build has shipped |
| `Logger(subsystem: "Leo", category: "<Type>")` | `Log.x("<TypeName>", ...)`; never `println` |
| `Localizable.xcstrings` (en, es, pt-BR) | `res/values/strings.xml`, `values-es/`, `values-pt-rBR/`; plurals in `<plurals>` |
| Dynamic Type | `sp` units and `LocalDensity.fontScale`; layouts must survive 200% font scale |
| VoiceOver | TalkBack: `semantics`, `contentDescription`, `heading()`, merged descendants |
| Reduce Motion | `Settings.Global.ANIMATOR_DURATION_SCALE == 0` disables animations |
| Swift Testing | JUnit 4 + `kotlinx-coroutines-test` |
| swift-snapshot-testing | Roborazzi (JVM, Robolectric), light + dark + 200% font scale |
| SwiftLint / SwiftFormat | detekt + ktlint, warnings as errors; Android Lint with `warningsAsErrors` |
| Source Serif 4 (`Leo/Resources/Fonts`) | Copied to `app/src/main/res/font/` with its OFL license |

## Approved dependencies

Only these, at the versions in `gradle/libs.versions.toml`: Compose BOM, Material 3, activity-compose, core-splashscreen, lifecycle (runtime, viewmodel-compose, runtime-compose), navigation-compose, kotlinx-serialization-json, kotlinx-coroutines (+ test), DataStore, Room (+ KSP compiler), ML Kit `genai-prompt` + `genai-schema-compiler`, KSP, Robolectric, Roborazzi, detekt, ktlint. Anything else is a decision for the user, not a task.

## Architecture

Same layers as iOS, enforced by Gradle modules instead of lint rules:

- `:domain` is a pure Kotlin/JVM module (no Android): the port of `Leo/Domain/`. It cannot import Android, Compose or ML Kit because it doesn't depend on them.
- `:app` holds `data/` (repositories, `genai/`, `room/`, `datastore/`) and `features/` (one package per feature, plus `theme/` and `components/`). detekt's `ForbiddenImport` rejects `com.google.mlkit` and `androidx.room` under `features/`.
- Composables only lay out their ViewModel's state. They never call a repository or ML Kit.
- ViewModels depend on repository interfaces. Tests inject hand-written fakes from `app/src/test/.../support/` (`Stub…`, `…Spy`), mirroring `LeoTests/Support/`. No mocking library.
- Keep the iOS names: `QuizViewModel`, `ExerciseRepair`, `ReaderPreferences`, `RoundSettings`... so a file can be compared with its Swift original side by side.

## Build and test

Run from the repo root:

```bash
./gradlew assembleDebug                     # build
./gradlew :domain:test testDebugUnitTest    # unit tests + Roborazzi verify
./gradlew detekt ktlintCheck lintDebug      # lint, all must pass
./gradlew recordRoborazziDebug              # record snapshots on purpose, then open every new image
./gradlew connectedDebugAndroidTest         # instrumented + live-model tests, needs the device
```

- "Done" means build, unit tests and lint all pass. Report the exact commands run and their result.
- Live-model tests (`@LiveModel` instrumented tests, the port of `LeoTests/LiveModel/`) run on a physical device that supports Gemini Nano; they skip themselves when `checkStatus()` isn't `AVAILABLE`. Emulators don't run Gemini Nano. If no device is connected, say so and leave that step for the user.

## The on-device model

- Port prompts from `Leo/Data/FoundationModels/` word for word first. Gemini Nano is a different model, so prompt tuning is its own phase, measured with the ported evaluation harness, never eyeballed.
- Prompts stay in English; content is written in the reader's language (`ContentLanguage`). Check which languages Gemini Nano supports and fall back to English the way iOS does.
- A fresh request per exercise, streaming so runaway passages can be abandoned early, the app shuffles the answers, and `ExerciseRepair` validates every exercise, exactly as on iOS.
- Prompt text is covered by golden-file tests (the port of `ExerciseGeneratorPromptTests`). A prompt change updates them on purpose.
- Add every `@Generable` class to `app/src/main/keepRules/rules.keep`, or release builds fail to parse.

## Privacy

Same promise as iOS: no accounts, no analytics, no network code, and no `INTERNET` permission in the manifest. Disable cloud backup of user data in `backup_rules.xml` / `data_extraction_rules.xml`.

## Teaching

The user is a senior iOS engineer learning Android with this project. At the end of each phase, write `docs/learning/phase-<n>.md`: at most one page, each concept as "iOS → Android, and why it differs", pointing to the files just written. Explain Android idioms, not general programming. In conversation, keep answers short and concrete.

## Delivery

- One phase per branch and pull request. The user confirms commits, pushes, pull requests and merges.
- Workflow files for each phase go in `.claude/workflow/phase-<n>/` (`plan.md`, `changes.md`, `review.md`, `qa.md`), the same layout as the iOS repo.
