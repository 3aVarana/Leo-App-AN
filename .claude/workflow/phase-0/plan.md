# Phase 0: Foundation, plan

iOS spec: `Leo/Features/Theme/Theme.swift`, `Leo/Assets.xcassets/Colors/*.colorset`, `Leo/Resources/Fonts/`, `.swiftlint.yml`, test `LeoTests/Features/ThemeTests.swift`, snapshot support `LeoTests/Support/SnapshotHelpers.swift`, `SnapshotLayoutTests.swift`.
Already here: the Android Studio template (`app/` only, `ui/theme/{Color,Type,Theme}.kt` with Material You purple, `MainActivity` "Hello Android", AGP 9.4.1, Kotlin 2.2.10, compileSdk/targetSdk 37, minSdk 29, Java 11). Nothing to reuse except the Gradle skeleton.

## File map

| iOS | Android (module, path) |
|---|---|
| `Theme.swift:10-38` colors + `Assets.xcassets/Colors/` (19 sets) | `:app` `features/theme/Theme.kt`: `LeoColors` (immutable class, one `Color` per token), `LightLeoColors`, `DarkLeoColors`, high-contrast variants, `LocalLeoColors`, `LeoTheme {}` |
| `Theme.swift:42-60` `LeoWeight`, `Font.leo` | `:app` `features/theme/LeoTextStyle.kt`: `enum class LeoWeight(@FontRes)`, `SourceSerif4 = FontFamily(...)` |
| `Theme.swift:64-114` `LeoTextStyle` + statics | same file: `data class LeoTextStyle(size, weight, lineHeight: Float?, tracking, scalesWithFontSize)` + `companion` (`display(size)`, `score`, `title` ... `kicker`) + `@Composable fun LeoTextStyle.toTextStyle(): TextStyle` |
| `Theme.swift:116-193` modifier, overhang renderer | `toTextStyle()` above; no renderer (see D3) |
| `Theme.swift:197-210` layout | `:app` `features/theme/Layout.kt`: `Modifier.leoReadableWidth()`, `Modifier.leoScreenBackground()` |
| `Theme.swift:212-215` `animatesPlaceholders` | `Theme.kt`: `val LocalAnimatesPlaceholders = staticCompositionLocalOf { true }` |
| `Resources/Fonts/*.otf` + `OFL.md` | `app/src/main/res/font/source_serif_4_regular.otf`, `source_serif_4_italic.otf`, `source_serif_4_semibold.otf` (res names must be lowercase); `OFL.md` location per Q9 |
| `.swiftlint.yml:22-36` layer rules | `:domain` as `kotlin("jvm")` module (no Android deps); `config/detekt/detekt.yml` `ForbiddenImport` with `includes: ['**/features/**']` for `com.google.mlkit.*`, `androidx.room.*` |
| `.swiftlint.yml:8-20` | `.editorconfig` (ktlint_official, `max_line_length = 120`, `ktlint_function_naming_ignore_when_annotated_with = Composable`) |
| `SnapshotHelpers.swift` | `app/src/test/java/com/victorarana/leo/support/SnapshotHelpers.kt`: `fun ComposeContentTestRule.assertViewSnapshot(name, fontScale = 1f, content)` |
| `ThemeTests.swift` | `app/src/test/java/com/victorarana/leo/features/theme/ThemeTests.kt` |
| `SnapshotLayoutTests.swift` | `app/src/test/java/com/victorarana/leo/support/SnapshotLayoutTests.kt` |
| (none) | `app/src/test/java/com/victorarana/leo/snapshots/ThemeSnapshotTests.kt` + test-only `ThemeSpecimen` composable |

Delete: `ui/theme/{Color,Type,Theme}.kt`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`, template purple colors in `res/values/colors.xml`, `Greeting` in `MainActivity.kt`.

## Behavior to preserve

- Tokens: 14 asset colors (`Theme.swift:11-37`), light and dark each, exact sRGB hex/alpha from each `Contents.json` (e.g. Background `#F3F2F2` / `#1C1B1A`; Divider is translucent: `#201E1D` @ 0.16 light, `#EFEDED` @ 0.18 dark).
- Derived: `inkSoft = ink.copy(alpha = 0.78f)` (`Theme.swift:15`), `inkMuted = ink.copy(alpha = 0.7f)` (`:17`).
- High contrast: only `Accent` (`#006786` light, dark unchanged `#38A6CF`) and `Divider` (alpha 0.45 both modes) have variants (`Accent.colorset/Contents.json:33-72`, `Divider.colorset/Contents.json:33-72`).
- Fonts: Regular, Italic (`SourceSerif4-It`), Semibold only (`Theme.swift:46-52`). No bold.
- 19 text roles, exact size/weight/lineHeight/tracking (`Theme.swift:90-113`), e.g. `title` 30 semibold, lh 33, -0.02em; `kicker` 12, tracking 0.08em; `verdict`, `wordmark`, `caption`, `note`, `answerMarker`, `kicker` keep the font's own line height (`lineHeight = null`, `:69`).
- `display(size)`: lineHeight `size * (size >= 48 ? 1.04 : 1.08)`, tracking `size >= 48 ? -0.025 : -0.02` (`Theme.swift:80-88`).
- Tracking is in em (`Theme.swift:70`, applied as `tracking * size` points at `:131`) → `letterSpacing = tracking.em`.
- Line heights scale with text size like the font (`Theme.swift:141-144`) → `sp`.
- `launchWordmark` 48 semibold never scales (`Theme.swift:100-102`).
- Readable width: 24 horizontal padding, max width 648, centered column, content start-aligned (`Theme.swift:200-204`).
- Screen background fills behind system bars (`Theme.swift:207-209`).
- Placeholders animate by default, off in snapshots (`Theme.swift:214`, `SnapshotHelpers.swift:48`).
- Snapshots: light + dark (`-dark` suffix), language appended to name (`SnapshotHelpers.swift:46,66`), 1% tolerance (`:62`), references in a repo-level folder per test file (`:141-146`), test file names unique (`SnapshotLayoutTests.swift:8-16`).
- Layer rules (`.swiftlint.yml:25-36`); lint fails the build (`--strict`, iOS `CLAUDE.md` "Lint and format").

## Tests to port

| iOS | Kotlin |
|---|---|
| `ThemeTests.fontIsRegistered` (3 args) | `ThemeTests.fontIsBundled` over `LeoWeight.entries`: `ResourcesCompat.getFont(context, weight.fontRes)` non-null (Robolectric) |
| `SnapshotLayoutTests.testFileNamesAreUnique` | `SnapshotLayoutTests.testFileNamesAreUnique` (walks `app/src/test/java`) |
| Android-only | `LeoTextStyleTests`: `display(48)` → lh 49.92, tracking -0.025; `display(40)` → lh 43.2, tracking -0.02; `launchWordmark.scalesWithFontSize == false` |
| Android-only | `ThemeSnapshotTests`: `ThemeSpecimen` (every color swatch + every text role) in light, dark, and fontScale 2.0 (light + dark) |
| Android-only | Privacy check: merged debug manifest has no `android.permission.INTERNET` (unit test reading the merged manifest, or a Gradle verification task wired into `check`) |

## Android decisions

- **D1 Material mapping.** Drop dynamic color (template `Theme.kt:40`). `LeoTheme` provides `LocalLeoColors` and a `MaterialTheme` whose `ColorScheme` maps background/surface/ink/accent/divider so stray Material defaults stay on-brand; components read `LeoTheme.colors` directly. Recommend.
- **D2 Font scaling.** iOS scales each role by its `relativeTo` style; Android has one `fontScale` (nonlinear on API 34+). Use `sp` everywhere, drop `relativeTo` (list in `changes.md`). Fixed `launchWordmark`: `with(LocalDensity.current) { 48.dp.toSp() }`.
- **D3 Overhang renderer** (`Theme.swift:146-181`) works around iOS 26 clipping. Compose doesn't clip glyphs to line boxes; use `LineHeightStyle(Alignment.Center, Trim.None)` + `PlatformTextStyle(includeFontPadding = false)` and check descenders in the 200% snapshot. Don't port the renderer.
- **D4 High contrast.** Android has `UiModeManager.getContrast()` (API 34+, 0 / 0.5 / 1.0). Recommend high-contrast tokens when `contrast >= 0.5` on API 34+, standard below. See Q5.
- **D5 Window background.** `themes.xml` + `values-night/themes.xml` set `android:windowBackground` to Background light/dark so there's no white flash before Compose draws.
- **D6 Snapshot device.** Robolectric `@Config(qualifiers = "w390dp-h844dp-xxhdpi")` mirrors iOS `iPhone13Pro` 390x844 @3x (`SnapshotHelpers.swift:44`), `@GraphicsMode(NATIVE)`, Robolectric `sdk` pinned to the newest it supports (may be below 37), `isIncludeAndroidResources = true`. References in `app/src/test/snapshots/<TestClass>/` (`roborazzi { outputDir }`), `roborazzi.test.verify=true` in `gradle.properties` so `testDebugUnitTest` verifies, `changeThreshold = 0.01`. A missing reference must fail the verify run (confirm Roborazzi's behavior; iOS CI uses `SNAPSHOT_TESTING_RECORD=never`).
- **D7 Lint strictness.** detekt `buildUponDefaultConfig`, `FunctionNaming.ignoreAnnotated: [Composable]`, `MagicNumber` ignoring property declarations (theme constants). Android Lint `warningsAsErrors = true`, `abortOnError = true`; disable `GradleDependency`/`AndroidGradlePluginVersion`/`NewerVersionAvailable` in `app/lint.xml` so a new upstream release doesn't break the build.
- **D8 Privacy.** Manifest: `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` (ML Kit in phase 3 may merge it in). `allowBackup="false"`; `data_extraction_rules.xml` excludes all domains from `cloud-backup`; `backup_rules.xml` excludes all.

## Open questions for the user

1. minSdk: keep template 29, or raise (Gemini Nano devices run Android 14+; 34 would make D4 check-free)? Recommend 29 so older phones still reach the unavailable screen.
2. Confirm `applicationId`/namespace `com.victorarana.leo` (same as iOS bundle id) and packages `com.victorarana.leo.features.theme`, `com.victorarana.leo.domain`.
3. Versions for approved deps not yet in `libs.versions.toml`: navigation-compose, lifecycle viewmodel-compose/runtime-compose, kotlinx-serialization (+ plugin), coroutines, DataStore, Room, ML Kit `genai-prompt`/`genai-schema-compiler`, KSP, Robolectric, Roborazzi, detekt, ktlint Gradle plugin, `kotlin-jvm` plugin. OK for the implementer to pick the latest stable that works with AGP 9.4.1, listed in `changes.md` for review? Bump Kotlin from 2.2.10 if KSP/detekt need it?
4. JVM target: raise from 11 to 17 for `:app` and `:domain`?
5. High contrast: map Android's "medium" (0.5) contrast to iOS's high-contrast colors, or only 1.0?
6. Backup: also exclude device-to-device transfer, or allow it (iOS device migration carries app data)?
7. Snapshot device 390x844dp @xxhdpi (iOS parity) vs a Pixel size (e.g. 411x914dp)?
8. Line length 120 (SwiftLint's default warning, an error under `--strict`) OK? The `@Guide` description exemption (`.swiftlint.yml:17-20`) is handled in phase 3 with a file-level suppression.
9. Where should `OFL.md` live: `app/src/main/assets/licenses/OFL.md` (shipped in the APK, as iOS ships it in the bundle) or repo-only `licenses/OFL.md`?
10. App icon: keep the template launcher icon until phase 4/8, or port the iOS icon now?

## Decisions

Approved by the user on 2026-10-29, with these answers:

- Q1: keep minSdk 29. The high-contrast check is gated on API 34+.
- Q2: `applicationId`/namespace `com.victorarana.leo`, packages as proposed.
- Q3: implementer picks the latest stable versions that work with AGP 9.4.1; bump Kotlin only if KSP/detekt require it. List every chosen version in `changes.md`.
- Q4: JVM target 17 for `:app` and `:domain`.
- Q5: D4 as recommended: high-contrast Accent/Divider when `UiModeManager.getContrast() >= 0.5` (API 34+).
- Q6: allow device-to-device transfer; exclude cloud backup only. `data_extraction_rules.xml` excludes everything under `<cloud-backup>` and leaves `<device-transfer>` open. Pick `allowBackup`/`backup_rules.xml` so pre-API-31 cloud backup is off while D2D still works on 31+, and record the choice in `changes.md`.
- Q7: snapshots at 390x844dp xxhdpi (iOS iPhone 13 Pro parity).
- Q8: max line length 120.
- Q9: `OFL.md` in `app/src/main/assets/licenses/OFL.md` (shipped, like the iOS bundle).
- Q10: keep the template launcher icon for now; port the iOS icon in a later phase.
- D1–D8 approved as written (D8 adjusted by Q6).
- After review round 1 (user, 2026-10-29): use stable detekt 1.23.8 with the Gradle daemon pinned to JDK 21 instead of detekt 2.0.0-alpha.6 on JDK 25. Fix all seven should-fix items in `review.md` before QA.

## Acceptance criteria

1. `settings.gradle.kts` includes `:domain`; `domain/build.gradle.kts` applies only `kotlin("jvm")`, has no Android/Compose/ML Kit deps; `:app` has `implementation(project(":domain"))`.
2. Every approved dependency and plugin from `CLAUDE.md` is in `gradle/libs.versions.toml`; nothing unapproved is added.
3. detekt, ktlint, Android Lint (`warningsAsErrors`) and Roborazzi are applied; `ForbiddenImport` rejects `com.google.mlkit`/`androidx.room` under `features/` (checked once with a throwaway import, reported in `changes.md`).
4. Three Source Serif 4 fonts in `res/font/`, OFL license copied; `ThemeTests` passes.
5. `LeoColors` has all 14 tokens + `inkSoft`/`inkMuted`, hex values matching the colorsets, light/dark/high-contrast; `LeoTextStyle` has all 19 named roles plus `display()`; `LeoTextStyleTests` passes.
6. `Modifier.leoReadableWidth()`, `Modifier.leoScreenBackground()`, `LocalAnimatesPlaceholders` exist.
7. `ThemeSnapshotTests` references recorded (light, dark, 200% light/dark), each image opened: serif font rendered, no clipped descenders at 200%.
8. No dynamic color; `ui/theme/` and template examples removed; `MainActivity` shows an empty `LeoTheme` screen on the Broadsheet background.
9. Merged debug manifest has no `INTERNET` permission; cloud backup disabled.
10. `./gradlew assembleDebug`, `./gradlew :domain:test testDebugUnitTest`, `./gradlew detekt ktlintCheck lintDebug` all pass (`:domain:test` NO-SOURCE is acceptable this phase).

## Tasks

1. Version catalog: add all approved libraries/plugins (Q3); root `build.gradle.kts` declares plugins `apply false`. Build passes.
2. Add `:domain` (kotlin-jvm, JUnit 4 + coroutines-test test deps, toolchain per Q4); wire `:app` → `:domain`. Build passes.
3. `.editorconfig`, ktlint plugin on both modules; reformat template code. `ktlintCheck` passes.
4. detekt + `config/detekt/detekt.yml` (D7, ForbiddenImport scoped); Android Lint config + `app/lint.xml`. `detekt lintDebug` pass.
5. Privacy (D8): manifest permission removal, backup/data-extraction rules, privacy check. Build + lint pass.
6. Fonts + license; `LeoWeight`, `SourceSerif4`; port `ThemeTests` (Robolectric deps). Tests pass.
7. `Theme.kt` colors (all variants) + `LeoTheme` + `LocalAnimatesPlaceholders` (D1, D4, D5); delete `ui/theme/`, update `MainActivity`. Build passes.
8. `LeoTextStyle.kt` + `toTextStyle()` (D2, D3) + `LeoTextStyleTests`; `Layout.kt`. Tests pass.
9. Roborazzi setup (D6), `support/SnapshotHelpers.kt`, `SnapshotLayoutTests`, `ThemeSpecimen` + `ThemeSnapshotTests`; `recordRoborazziDebug`, open every image, then the verify run passes.
10. Run the three commands in criterion 10; write `changes.md` listing D1-D8 deviations and chosen versions.
