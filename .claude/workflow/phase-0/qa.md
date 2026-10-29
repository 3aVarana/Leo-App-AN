# Phase 0 (Foundation): QA

Branch `port/phase-0-foundation`, uncommitted tree. No source modified. Daemon JVM 21.

## Commands (all with `--rerun-tasks`, clean state)

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug --rerun-tasks` | BUILD SUCCESSFUL, 39 tasks executed |
| `./gradlew :domain:test testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL. `:domain:test` has no sources. App: 23 tests, 0 failures, 0 errors, 0 skipped (LeoColorsTests 6, LeoTextStyleTests 5, ThemeSnapshotTests 8, SnapshotLayoutTests 1, ThemeTests 3). Roborazzi verify runs (`finalizeTestRoborazziDebug`) |
| `./gradlew detekt ktlintCheck lintDebug --rerun-tasks` | BUILD SUCCESSFUL, 39 tasks |
| `./gradlew check --rerun-tasks` | BUILD SUCCESSFUL, 55 tasks |
| `./gradlew processReleaseManifest --rerun-tasks` | BUILD SUCCESSFUL |

Manifest merge warning (expected): "INTERNET was tagged to remove other declarations but no other declaration present".

## Manifests
Merged debug (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/`, `merged_manifests/debug/processDebugManifest/`) and release (`merged_manifest/release/processReleaseMainManifest/`): 0 occurrences of `android.permission.INTERNET`; only `com.victorarana.leo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx) is declared.

## Device
`adb` is not on PATH; used `~/Library/Android/sdk/platform-tools/adb`. Device: emulator-5554 (sdk_gphone64_arm64, API 37). `./gradlew installDebug` succeeded; app launched via `am start`. Screenshots (`cmd uimode night no/yes`) in the scratchpad (`light.png`, `dark.png`): light shows a flat light grey (#F3F2F2-like) screen, dark a flat near-black (#1C1B1A-like), nothing else drawn, both match the Broadsheet background. Night mode reset to no. Not run: `connectedDebugAndroidTest` (no instrumented tests in phase 0), Spanish/font-scale-by-hand (no screens yet), Gemini Nano (emulator, not applicable; not used this phase).
Pixel values were judged by eye, not sampled.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | `:domain` included; only kotlin("jvm") | PASS | `settings.gradle.kts` has `include(":domain")`. `domain/build.gradle.kts` applies kotlin.jvm plus the ktlint and detekt tooling plugins (lint tools, not libraries; plan literal "only kotlin(jvm)" is loosely met). Deps: junit, coroutines-test only. `app/build.gradle.kts:87` `implementation(project(":domain"))` |
| 2 | All approved deps in catalog, nothing unapproved | PASS | `libs.versions.toml` read: Compose BOM, M3, activity, lifecycle x3, navigation, serialization, coroutines (+test, +android), DataStore, Room (+ktx, compiler), ML Kit prompt + schema compiler, KSP, Robolectric, Roborazzi, detekt, ktlint, kotlin-jvm/compose/serialization, androidx-core-ktx, ui-tooling/test artifacts (Compose BOM family, template-standard). No unapproved libraries |
| 3 | detekt, ktlint, Lint warningsAsErrors, Roborazzi applied; ForbiddenImport scoped | PASS | Plugins in `app/build.gradle.kts`; `warningsAsErrors = true`, `abortOnError = true` (lines 50-51); `config/detekt/detekt.yml:16-24` ForbiddenImport on `**/features/**` for mlkit and room. Throwaway-import check reported in `changes.md` (not repeated by QA) |
| 4 | 3 fonts, OFL, ThemeTests passes | PASS | `res/font/source_serif_4_{regular,italic,semibold}.otf`; `assets/licenses/OFL.md` (SIL OFL 1.1); ThemeTests 3/3 passed |
| 5 | LeoColors tokens/variants; 19 roles + display(); LeoTextStyleTests | PASS | 19 role vals counted in `LeoTextStyle.kt:60-80` plus `display`; spot checks of values (title 30/33/-0.02, launchWordmark not scaling, Background F3F2F2/1C1B1A, high-contrast accent 006786); LeoColorsTests 6/6 and LeoTextStyleTests 5/5 passed. Full per-token hex comparison against the colorsets not done by QA |
| 6 | leoReadableWidth, leoScreenBackground, LocalAnimatesPlaceholders | PASS | `Layout.kt:22`, `Layout.kt:34`, `Theme.kt:132` |
| 7 | ThemeSnapshotTests references recorded, serif rendered, no clipped descenders at 200% | PASS | 16 PNGs in `app/src/test/snapshots/ThemeSnapshotTests/` (colors, highContrast, 3 text groups x light/dark x normal/fontScale2); verify passes. Opened `headlineStyles-fontScale2-en.png`: Source Serif rendered, descenders (j, p, g, y, q) intact. Other images not individually opened by QA |
| 8 | No dynamic color; ui/theme and examples removed; empty LeoTheme screen | PASS | `ui/` dir gone, no dynamicColor use in main sources, example tests deleted (git status), `MainActivity.kt` renders `LeoTheme { Box(fillMaxSize().leoScreenBackground()) }`; device screenshots confirm in light and dark |
| 9 | No INTERNET in merged manifest; cloud backup disabled | PASS | See Manifests. `data_extraction_rules.xml` excludes all domains under `<cloud-backup>`, `<device-transfer />` open; `backup_rules.xml` excludes all; per Q6 `allowBackup="true"` with rule files (documented in manifest comment). Note: pre-API-31 `fullBackupContent` excludes everything, which also blocks transfer there (documented) |
| 10 | assembleDebug, tests, lint pass | PASS | All command rows above succeeded |

## Left for the user
Gemini Nano availability and live-model tests (phase 3+), physical-device check; Spanish and 200% font scale on device (no UI to check yet).
