# Phase 2 (Storage): QA

Branch `port/phase-2-storage`, uncommitted tree. No source modified. Overall: **PASS** (device checks NOT RUN).

## Commands (all with `--rerun-tasks`)

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug --rerun-tasks` | BUILD SUCCESSFUL, 44 tasks executed |
| `./gradlew :domain:test testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL, 42 tasks, no hang (run under a 560 s alarm, finished in 11 s). `:domain` 80 tests, 0 failures/errors/skipped. `:app` 55 tests, 0 failures/errors/skipped: DataStorePreferencesRepositoryTests 7, RoomRoundHistoryRepositoryTests 13, RoundHistorySchemaTests 3, DomainLabelsTests 9, ThemeSnapshotTests 8, LeoColorsTests 6, LeoTextStyleTests 5, ThemeTests 3, SnapshotLayoutTests 1. Roborazzi verify ran with them |
| `./gradlew detekt ktlintCheck lintDebug --rerun-tasks` | BUILD SUCCESSFUL, 53 tasks |
| `./gradlew processReleaseManifest processDebugManifest --rerun-tasks` | BUILD SUCCESSFUL |

Counts come from the JUnit XML in `domain/build/test-results/test/` and `app/build/test-results/testDebugUnitTest/`.

## Manifests
Every merged manifest under `app/build/intermediates/merged_manifest{,s}/` (debug, release, debugUnitTest, verify*NoInternetPermission): 0 occurrences of `android.permission.INTERNET`.

## Device
`adb devices` (SDK platform-tools) lists no device or emulator. NOT RUN: `installDebug`, `connectedDebugAndroidTest` (no instrumented tests exist yet), Gemini Nano availability (unused this phase), Spanish / font scale 2.0 / dark mode screenshots (no screens this phase).

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | File map files exist; `features/` imports no room/datastore; detekt green | PASS | All 11 mapped files plus `1.json` and both spies exist under `data/{repositories,datastore,room}` (extra: `KeepDamagedFileFactory.kt`, for review B1). `grep androidx.room\|androidx.datastore\|com.google.mlkit` in `features/`: none. detekt passed |
| 2 | `KEY == "readerPreferences"`; phase-1 JSON; missing, corrupt JSON, corrupt file load `null` | PASS | `DataStorePreferencesRepository.kt:61` `const val KEY`. `DataStorePreferencesRepositoryTests` 7/7 pass (5 ported + `corruptFileLoadsNil`, `nullWhenReadFails`) |
| 3 | Room repo restores every field; per-group newest-first; cascade delete; unknown delete no-op; unreadable round `null`; survives reopen | PASS | `RoomRoundHistoryRepositoryTests` 13/13: `saveThenRoundRestoresEveryField`, `summariesAreNewestFirstAndPerAgeGroup`, `summaryResultsMatchAnswers`, `deleteRemovesTheRoundAndItsAnswers`, `deletingAnUnknownRoundDoesNothing`, `unreadableRoundIsSkipped`, `roundsSurviveANewDatabase`, plus D3/D4/D5 and B1 tests |
| 4 | Version 1, `exportSchema = true`, `1.json` committed, names match iOS | PASS | `RoundHistoryDatabase.kt:18-19`; `app/schemas/com.victorarana.leo.data.room.RoundHistoryDatabase/1.json` present (untracked, to be committed); `RoundHistorySchemaTests` 3/3 |
| 5 | No migration, no `fallbackToDestructiveMigration`; D6 rule in KDoc and changes.md | PASS | grep finds `fallbackToDestructiveMigration` only in the KDoc prohibition (`RoundHistoryDatabase.kt:14`); the D6 rule is in that KDoc (lines 10-14); changes.md covers D1-D7 and Q1-Q4 (line 31) |
| 6 | Dispatchers injected; no hard-coded `Dispatchers.IO` outside create/live defaults; no `println` | PASS | `Dispatchers.IO` appears only as default parameters (`RoomRoundHistoryRepository.kt:115,128`, `DataStorePreferencesRepository.kt:73`); no `println` in `data/` or test support |
| 7 | Both spies exist in `support/` | PASS | `PreferencesRepositorySpy.kt`, `RoundHistoryRepositorySpy.kt` |
| 8 | No INTERNET; only approved dependencies | PASS | See Manifests. `app/build.gradle.kts` diff adds ksp, room plugin, datastore-preferences, room-runtime/ktx/compiler, coroutines-android/test, domain testFixtures; all approved. Also `buildConfig = true` (a build feature flag, not a dependency; used by the release/debug schema-error behavior, check changes.md) |
| 9 | Three commands pass; changes.md D1-D7, Q1-Q4, commands; learning doc | PASS | All commands above succeeded; changes.md and `docs/learning/phase-2.md` exist |

## Left for the user
Install and launch on a device (none connected), live-model checks (phase 3+), committing the untracked `app/schemas/` and `data/` files, and a real-device smoke test of the DataStore and Room files when the first screen uses them (phase 4+).
