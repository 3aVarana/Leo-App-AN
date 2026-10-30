# Phase 4 (App shell): QA

Branch `port/phase-4-app-shell`, uncommitted work on baseline in `baseline.sha`. All Gradle runs used `--rerun-tasks` (no task cache). Source not modified.

## Commands and results

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew --rerun-tasks assembleDebug assembleRelease assembleDebugAndroidTest` | BUILD SUCCESSFUL, 130 tasks executed |
| 2 | `./gradlew --rerun-tasks :domain:test testDebugUnitTest` | BUILD SUCCESSFUL, 42 tasks executed. `:domain` 80 tests, `:app` 234 tests, 0 failures, 0 errors, 0 skipped. `roborazzi.test.verify=true` in `gradle.properties`, so snapshots were compared, not recorded |
| 3 | `./gradlew --rerun-tasks detekt ktlintCheck lintDebug` | BUILD SUCCESSFUL, 55 tasks executed |

Targeted counts (from `app/build/test-results`): `LaunchIntroTests` 14/14, `RootViewModelTests` 14/14, `RootScreenTests` 6/6.

Other checks:
- Merged manifests (`app/build/intermediates/merged_manifests/{debug,release}/.../AndroidManifest.xml`): 0 occurrences of `INTERNET`.
- Release R8 (`app/build/outputs/mapping/release/configuration.txt`) carries `-keep class ...GeneratedExercise { *; }` and `-keep class ...TopicReview { *; }` from `app/src/main/keepRules/rules.keep`. Caveat: `mapping.txt` and `seeds.txt` have no entry for either class (0 grep hits). I read that as an unrenamed kept class that is never referenced from code, so R8 left nothing to map. I did not inspect the APK dex, so "kept in the shipped APK" is not directly proven. Whether R8 dropped them as unused was not checked.
- No `com.google.mlkit`, `androidx.room` or `androidx.datastore` import under `features/`.
- `Generation.getClient()` appears once in `src/main`: `AppContainer.kt:36` (lazy).
- `strings.xml` has 112 entries in each of en, es and pt-BR.

## Device

`adb` is not on PATH. It exists at `~/Library/Android/sdk/platform-tools/adb`, and `adb devices` there lists no device. Gemini Nano availability is unknown. Every device-only check is NOT RUN, and the user must run them:
- `./gradlew installDebug` and `./gradlew connectedDebugAndroidTest` (live-model suites skip themselves unless `checkStatus()` is `AVAILABLE`).
- Cold launch on API 29-30, 31+ and 34+ (splash and its hand-off to the launch intro).
- TalkBack focus after the intro, and rotation (the intro must not replay).
- Download states (`Downloadable`, `Downloading`, `DOWNLOAD_FAILED`) and the screen swap to the app.
- Spanish (`adb shell cmd locale set-app-locales com.victorarana.leo --locales es`), font scale 2.0 (reset to 1.0 afterwards) and dark mode on screen.

## Acceptance criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | The three assemble tasks pass; no `INTERNET`; release keeps `GeneratedExercise` and `TopicReview` | PASS (with caveat) | Command 1 green. 0 `INTERNET` in both merged manifests. Keep rules are in the R8 configuration. The classes are absent from `mapping.txt` and `seeds.txt`, so there is no direct proof they survive in the APK (see above). |
| 2 | `LeoApplication` and `AppContainer` build every repository with one `GenerativeModel`; no ML Kit, Room or DataStore import under `features/` | PASS | One `Generation.getClient()` in `AppContainer.kt:36`. No forbidden import under `features/`. detekt green. I did not read the whole container to confirm every repository uses that model. |
| 3 | Intro plays once per process, fades on every phase-4 screen, blocks taps, honors reduce motion; 14 `LaunchIntroTests` pass | PASS on JVM, NOT RUN on device | `LaunchIntroTests` 14/14. `LaunchIntroSnapshotTests` (launch, reduced-motion, rest; light and dark) verify green. `hasPlayed` guard in `LaunchIntroOverlay.kt:91`. `RootScreenTests` 6/6. Rotation (no replay) and tap blocking on a real device are NOT RUN. |
| 4 | Unavailable screen renders 6 states with approved copy in en/es/pt-BR; progress updates live; swaps to the app on `Available` | PASS on JVM, NOT RUN on device | `UnavailableScreenSnapshotTests` has 16 images covering all 6 states (light, dark, fontScale2), all en only. es and pt-BR copy is present in `strings.xml` (`unavailable_*`) but rendered only through string resources, with no es/pt snapshot. `RootScreenTests.downloadingModelShowsProgressCopy` and `screenSwapsToTheAppWhenTheModelBecomesAvailable` pass. Real downloads are NOT RUN. |
| 5 | Nav graph `Home` and `History`; back pops; gates outside NavHost; placeholders named and documented | PASS | `LeoNavHost.kt` uses `HomePlaceholder` and the History route. `RootScreenTests.homeNavigatesToHistoryAndBackPops` passes. The gates are tested (`availableWithoutPreferencesShowsOnboarding` etc.). Documentation of the placeholders is in `changes.md`, which I did not audit line by line. |
| 6 | Every component in the file map exists, matches the cited sizes and colors, has TalkBack semantics | PARTIAL: PASS for existence and snapshots, NOT RUN for TalkBack | All map files exist in `features/components/`, with `ic_*` drawables and `splash_icon.xml`. `ComponentsSnapshotTests` (12 images) and `ProgressDotsTests` pass. Sizes and semantics were not checked against the Swift source by me (the review covered that). TalkBack on device NOT RUN. |
| 7 | Snapshots recorded (light, dark, 200%) for the gallery, narrow dots, age control, unavailable states and intro frames; each opened and checked | PASS for recording, NOT RUN for image review | Files exist in `app/src/test/snapshots/`: Components 12, LaunchIntro 6, Unavailable 16, and they verify green. I did not open the images. |
| 8 | `:domain:test testDebugUnitTest` and `detekt ktlintCheck lintDebug` pass; new strings in all three `strings.xml` | PASS | Commands 2 and 3 green. 112 strings in each of the three files. |
| 9 | `changes.md` lists every Android-specific change; `docs/learning/phase-4.md` written (one page) | PARTIAL | `changes.md` exists (146 lines); I did not audit it against D1-D7 and Q1-Q8. `docs/learning/phase-4.md` already exists in the tree, so the pending note was apparently written. I did not check its length. |

## Outstanding

- All device checks above (user).
- Dex-level confirmation of the keep rules is not done. It can be done with `apkanalyzer dex packages` on `app/build/outputs/apk/release/app-release-unsigned.apk`.
- The snapshot images were not opened in this run.

## Follow-up checks (orchestrator, after QA)

- Criterion 1 caveat closed: `strings` on the dex in `app/build/outputs/apk/release/app-release-unsigned.apk` shows `Lcom/victorarana/leo/data/genai/GeneratedExercise;` and `Lcom/victorarana/leo/data/genai/TopicReview;` with their original names. PASS.
- Criterion 9: `changes.md` has an entry for each of D1-D7 and Q1-Q8; `docs/learning/phase-4.md` is written (one page). PASS.
