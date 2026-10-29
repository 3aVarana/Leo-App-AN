# Phase 0: Foundation, changes

## Files added or changed
- Build: `settings.gradle.kts` (+`:domain`), `build.gradle.kts` (plugins `apply false`), `gradle/libs.versions.toml`, `gradle.properties` (`roborazzi.test.verify=true`), `app/build.gradle.kts`, `domain/build.gradle.kts`, `app/lint.xml`, `.editorconfig`, `config/detekt/detekt.yml`.
- Theme: `app/src/main/java/com/victorarana/leo/features/theme/{Theme,LeoTextStyle,Layout}.kt`; fonts `app/src/main/res/font/source_serif_4_{regular,italic,semibold}.otf`; `app/src/main/assets/licenses/OFL.md`.
- Platform: `AndroidManifest.xml`, `res/xml/{backup_rules,data_extraction_rules}.xml`, `res/values{,-night}/{colors,themes}.xml`, `MainActivity.kt` (empty `LeoTheme` screen).
- Tests: `features/theme/{ThemeTests,LeoTextStyleTests}.kt`, `support/{SnapshotHelpers,SnapshotLayoutTests}.kt`, `snapshots/{ThemeSpecimen,ThemeSnapshotTests}.kt`, `src/test/resources/robolectric.properties`, 14 recorded images in `app/src/test/snapshots/ThemeSnapshotTests/`.
- Deleted: `ui/theme/*`, `ExampleUnitTest`, `ExampleInstrumentedTest` (androidTest dir), template purple colors.

## Dependency versions chosen (`gradle/libs.versions.toml`)
AGP 9.4.1, Kotlin 2.2.10 (unchanged; KSP 2.3.12 declared, not yet applied, so compatibility is unverified), KSP 2.3.12, Compose BOM 2026.02.01 (unchanged), core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.0 (runtime, runtime-compose, viewmodel-compose), navigation-compose 2.10.2, kotlinx-serialization-json 1.12.0 (+ plugin = Kotlin version), coroutines 1.11.0 (core, android, test), DataStore 1.3.0, Room 2.8.5 (+ Room Gradle plugin), ML Kit genai-prompt 1.0.0-beta5, genai-schema-compiler 1.0.0-alpha2 (only pre-releases exist), JUnit 4.13.2, Robolectric 4.17, Roborazzi 1.77.0, detekt 1.23.8, ktlint Gradle plugin 14.2.0. Not yet used by code: navigation, lifecycle-compose, serialization, coroutines, DataStore, Room, ML Kit, KSP (declared only, not yet applied or exercised; added to modules when a phase needs them).
Removed from the template catalog (unapproved): espresso, androidx-test junit.

## Deviations from iOS / the plan
- **Token count:** the plan says "14 asset colors"; iOS has 19 colorsets (+ derived `inkSoft`, `inkMuted`). All 19 are ported, values copied from each `Contents.json`.
- **D1** `LeoTheme` provides `LocalLeoColors` + a `MaterialTheme` mapped onto them; no dynamic color. `object LeoTheme { colors }` plus `fun LeoTheme {}`.
- **D2** `relativeTo` dropped; everything in `sp`; `launchWordmark` uses `dp.toSp()` so it never scales.
- **D3** Overhang renderer not ported. `LineHeightStyle(Center, Trim.None)` + `includeFontPadding = false`. Checked in all 200% snapshots (light and dark): descenders (g, j, p, q, y) are not clipped, including `score` and `display` with tight line heights.
- **D4** High contrast when `UiModeManager.contrast >= 0.5` on API 34+, standard below. Only Accent (light) and Divider (alpha 0.45) change. Updates live: `UiModeManager.addContrastChangeListener` in a `DisposableEffect`. The light/dark/HC choice is the pure `leoColors(darkTheme, highContrast)`, covered by `LeoColorsTests` (every token of light and dark against the colorsets, the four combinations, derived ink tokens) and by a `colors-highContrast` snapshot.
- **D5** `windowBackground` set in `themes.xml` / `values-night/themes.xml`.
- **D6** Snapshots 390x844dp xxhdpi, `GraphicsMode.NATIVE`, 1% threshold, references in `app/src/test/snapshots/<TestClass>/` (folder = `testClass.simpleName`, the helper takes a `KClass`), verify on by default. Robolectric pinned to `sdk=36` (`robolectric.properties`, with a TODO to move to 37): with 37, compose-ui-test's Espresso fails on a removed `InputManager.getInstance()`. Dark mode and font scale go through `DeviceConfigurationOverride`, so `isSystemInDarkTheme()` and `LocalConfiguration` match a device. Tall specimens enlarge the window height via `RuntimeEnvironment.setQualifiers("+h...dp")`; the type specimen is split into headline/body/detail groups. File names end in `-<language>` like iOS (`colors-dark-en.png`). A missing reference fails the verify run (confirmed by deleting `colors.png`).
- **D7** detekt `buildUponDefaultConfig`, `FunctionNaming` ignores `@Composable`, MagicNumber ignores property declarations. Android Lint `warningsAsErrors`, `abortOnError`, `GradleDependency`/`AndroidGradlePluginVersion`/`NewerVersionAvailable` ignored.
- **detekt 1.23.8 on a JDK 21 daemon (round 2):** `gradle/gradle-daemon-jvm.properties` regenerated with `./gradlew updateDaemonJvm --jvm-version=21` (`toolchainVersion=21`; the foojay resolver was already in the template's `settings.gradle.kts`, nothing was added). Gradle now reports "Daemon JVM: Compatible with Java 21", running on Temurin 21.0.13 (`/Library/Java/JavaVirtualMachines/temurin-21.jdk`). detekt is the stable `io.gitlab.arturbosch.detekt` 1.23.8, `config/detekt/detekt.yml` is on the 1.x schema (`imports:` for `ForbiddenImport`, `ignoreAnnotated`), `jvmTarget = "17"` set on detekt tasks. It parses the Kotlin 2.2.10 sources without errors.
- **JVM 17 (Q4):** `jvmTarget`/`release` 17 on both modules, compiled by the JDK 21 daemon with `-release 17`.
- **D8 / Q6 backup:** cloud backup off, device-to-device transfer allowed. `allowBackup="true"` so the two rule files are the single place that says what leaves the device. API 31+: `data_extraction_rules.xml` excludes every domain (including the `device_*` ones) from `<cloud-backup>` and leaves `<device-transfer/>` open. API 29-30: `backup_rules.xml` excludes everything (those versions have no separate transfer rules, so transfer is skipped there). Correction to round 1: the earlier comment claimed `allowBackup="false"` blocks device-to-device transfer on Android 12+; that is wrong (for apps targeting 31+, `false` turns off cloud backup, not transfer). `true` was kept because it matches what the user chose and keeps the rules meaningful, not because `false` would break transfer. INTERNET removed via `tools:node="remove"`; the merger prints a harmless warning "no other declaration present".
- **Privacy check:** a Gradle task `verify<Variant>NoInternetPermission` reads the merged manifest and is wired into `check` (debug and release), instead of a unit test. Confirmed passing.
- **Robolectric on JDK 21:** the `--add-opens` and `--enable-native-access` test JVM args are gone; one `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` remains because Robolectric's FileDescriptor interceptor still fails without it on 21 (verified by removing all args: 10 tests failed).
- MainActivity shows an empty `leoScreenBackground` box. `ThemeSpecimen` is test-only.
- Not done here: `docs/learning/phase-0.md` (the orchestrating skill step), `androidTest` sources (removed template example; instrumented tests start in a later phase).

## ForbiddenImport check
Redone on detekt 1.23.8 with a temporary `app/src/main/java/.../features/tmp/Bad.kt` importing `androidx.room.Dao` and `com.google.mlkit.genai.prompt.Generation`: `./gradlew detekt` failed with `ForbiddenImport` for both ("Features talk to repository interfaces, never to Room directly." and "...never to ML Kit directly."). The file was deleted afterwards (the `features/` folder contains only `theme/`).

## Snapshot review
All 14 images opened (colors, 3 type groups at 100%, 3 at 200%, each light and dark). Source Serif 4 (regular, italic, semibold) renders; no clipped descenders at 200%; the readable-width column is start-aligned (fixed after the first recording centred the swatches).

## Commands run
- `./gradlew assembleDebug` BUILD SUCCESSFUL
- `./gradlew :domain:test testDebugUnitTest` BUILD SUCCESSFUL (`:domain:test` NO-SOURCE; app: 16 tests, 0 failures, snapshots verified)
- `./gradlew detekt ktlintCheck lintDebug` BUILD SUCCESSFUL
- `./gradlew check` BUILD SUCCESSFUL (includes both privacy verify tasks)
- `./gradlew recordRoborazziDebug` BUILD SUCCESSFUL
- Not run: `connectedDebugAndroidTest` (no tests, no device needed this phase).

## Round 2 (review findings)
- Should-fix 1, 2: `leoColors()` + `LeoColorsTests` + live contrast listener + `colors-highContrast` snapshot (see D4).
- Should-fix 3: backup comment and this file corrected (see D8).
- Should-fix 4: done once JDK 21 was installed (see detekt entry).
- Should-fix 5-7: `KClass` folder name, `DeviceConfigurationOverride`, `-en` language suffix (see D6). All 16 images re-recorded and opened (colors and colors-highContrast, 3 type groups at 100% and 200%, light and dark): serif font, no clipped descenders at 200%, high-contrast divider and accent visibly stronger.
- Nits applied: `LeoColors` is a `data class` with `copy` in `highContrast()`; `includeFontPadding`/`@Suppress` removed (default false); Material `Typography` uses Source Serif 4; `device_*` domains excluded from cloud backup; Robolectric TODO; KSP/ML Kit wording. Not applied: `leoScreenBackground` stays `@Composable`; no tablet-width snapshot; the `androidTestImplementation` runner/ext deps (not in the approved list) must be raised with the user before the live-model phase.
- Commands (round 2): `./gradlew assembleDebug` BUILD SUCCESSFUL; `./gradlew :domain:test testDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL (23 app tests, 0 failures; `:domain:test` NO-SOURCE); `./gradlew detekt ktlintCheck lintDebug` BUILD SUCCESSFUL; `./gradlew check` BUILD SUCCESSFUL.
- Commands (JDK 21 pass, all with `--rerun-tasks`): `./gradlew assembleDebug` BUILD SUCCESSFUL; `./gradlew :domain:test testDebugUnitTest` BUILD SUCCESSFUL (23 app tests, 0 failures; `:domain:test` NO-SOURCE); `./gradlew detekt ktlintCheck lintDebug` BUILD SUCCESSFUL.
