# Phase 1 (Domain): QA

Branch `port/phase-1-domain`, uncommitted tree. No source modified.

## Commands (all with `--rerun-tasks`)

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug --rerun-tasks` | BUILD SUCCESSFUL, 41 tasks executed |
| `./gradlew :domain:test testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL, 38 tasks. `:domain` 80 tests, 0 failures/errors/skipped (ReaderPreferencesTests 23, ReaderPreferencesTopicEditingTests 18, DefaultTopicsTests 10, AgeGroupTests 7, ReaderPreferencesSerializationTests 7, WordCountTests 5, ExerciseTests 3, RoundRecordTests 3, TextMatchingTests 3, RoundSummaryTests 1). `:app` 32 tests, 0 failures/errors/skipped (DomainLabelsTests 9, ThemeSnapshotTests 8, LeoColorsTests 6, LeoTextStyleTests 5, ThemeTests 3, SnapshotLayoutTests 1). Roborazzi verify ran with the unit tests; no snapshots changed |
| `./gradlew detekt ktlintCheck lintDebug --rerun-tasks` | BUILD SUCCESSFUL, 48 tasks |
| `./gradlew processReleaseManifest --rerun-tasks` | BUILD SUCCESSFUL |

Counts come from the JUnit XML in `domain/build/test-results/test/` and `app/build/test-results/testDebugUnitTest/`.

## Manifests
`app/src/main/AndroidManifest.xml` strips INTERNET (`tools:node="remove"`). Merged debug and release manifests (`merged_manifest/{debug,release}/*` and `merged_manifests/{debug,release}/*`, including the `verify*NoInternetPermission` copies): 0 occurrences of `android.permission.INTERNET`.

## Device
`adb devices` lists no device or emulator. NOT RUN: install and launch of the debug app, Gemini Nano availability (not used this phase), Spanish/font-scale/dark checks by hand (no screens this phase). `connectedDebugAndroidTest`: NOT RUN, no instrumented tests exist in phase 1.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | 13 iOS files have Kotlin counterparts; `:domain` depends only on stdlib, serialization-json, test deps | PASS | All 13 mapped files exist in `domain/src/main/kotlin/com/victorarana/leo/domain/` (plus `WordCount.kt`, `TextMatching.kt`, `ReaderPreferencesSerializer.kt`). `domain/build.gradle.kts` deps: `kotlinx.serialization.json` (impl and testFixtures), junit, coroutines-test; plugins `java-test-fixtures`, kotlin-serialization. Note: serialization pinned to 1.11.0 (catalog 1.12.0 incompatible with Kotlin 2.2.10), documented in changes.md deviation 1 |
| 2 | 85 default topics match `Topic.swift` in id, name, prompt, kind; `savedIds` golden equals iOS | PASS | Script extracted (id, name, prompt, kind) tuples from `Topic.swift` and `Topic.kt`: 85 vs 85, lists identical in order. `diff` of `domain/src/test/resources/golden/savedIds.1.txt` against `../Leo/LeoTestsSnapshots/DefaultTopicsTests/savedIds.1.txt`: identical. `DefaultTopicsTests.savedIds` and `eightyFiveTopicsInAllGroups` pass |
| 3 | `savedFormat` equals iOS JSON; v1 decodes with defaults; clamping cases pass | PASS | `savedFormat.1.json` golden is byte-identical to the iOS file; `ReaderPreferencesTests` (23) and `ReaderPreferencesSerializationTests` (7) pass. Uppercase/lowercase UUID handled per Q3 (ids lowercased on both sides, `decodesUpperCaseUuidsWrittenByIos`). Clamping tests were not individually re-read by QA beyond their passing |
| 4 | Every listed test passes; every deferred test is named in changes.md with its target phase | PASS | 80 domain + 9 `DomainLabelsTests` pass. changes.md "Deferred tests (24, to phase 3)" names 19 `ExerciseTests`, 2 `AgeGroupTests`, 3 `DefaultTopicsTests` (spot-checked 12 names by grep, all present; the section header states phase 3). Note: the plan says "19 of 20 in ExerciseTests"; changes.md lists 19 names, matching |
| 5 | `readingTime` and `acceptedWordCount` cases pass exactly | PASS | `AgeGroupTests.readingTime` (cases 6-8 incl. 90s, 80s, 120s, 160s, 30s minimum) and `acceptedWordCount` (30..120, 48..180, 60..225, 72..270, 96..345) present and passing |
| 6 | No Android, `java.awt` or `println` in `:domain` | PASS | `grep -E "import android\|java\.awt\|println\|androidx" domain/src` returns nothing |
| 7 | assembleDebug, tests, lint pass | PASS | All command rows above succeeded |

Additional: INTERNET absent from merged manifests (PASS, see Manifests). Deferred-test and Q2 items: `:app` strings in en/es/pt-BR and `DomainLabelsTests` (9) pass; lint passes with MissingTranslation as error.

## Left for the user
Install and launch on a device/emulator (none connected), Gemini Nano availability and live-model tests (phase 3+), phase 3 instrumented check for Android ICU `BreakIterator` word counts (changes.md deviation 9), and checking that a per-app language change reaches `ResourceTopicNames` (phase 5).
