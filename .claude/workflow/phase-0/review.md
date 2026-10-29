# Phase 0: Foundation, review

Branch `port/phase-0-foundation`, uncommitted and untracked changes diffed against `3fb722e` (`baseline.sha`).
Spec: `plan.md` (Decisions section) and the iOS originals it cites.

## What I verified

- **Colors:** I dumped all 19 `Leo/Assets.xcassets/Colors/*.colorset/Contents.json` files and compared them with `Theme.kt:92-136`. Every light and dark hex matches, Divider alpha is 0.16 / 0.18, and all are sRGB. High contrast: only Accent (`#006786` light, dark,high `#38A6CF`) and Divider (0.45 in both modes) have `contrast: high` entries, which matches `LeoColors.highContrast()` (`Theme.kt:61-82`). `inkSoft` 0.78 and `inkMuted` 0.7 match `Theme.swift:15,17`.
- **Text roles:** all 19 statics and `display()` match `Theme.swift:80-113` on size, weight, lineHeight (`nil` → `null` for verdict, wordmark, launchWordmark, caption, note, answerMarker, kicker) and tracking. Tracking is in em, line heights are in sp, and `launchWordmark` uses `dp.toSp()`.
- **Fonts and license:** the three `.otf` files and `OFL.md` are byte-identical to `../Leo/Leo/Resources/Fonts/` (checked with `cmp`), and all four are packaged in `app-debug.apk`.
- **Layout:** `leoReadableWidth()` gives the same result as the SwiftUI chain (24 gutters inside a 648 cap, centered column, start-aligned content).
- **Snapshots:** I opened `colors-dark`, `headlineStyles` and `headlineStyles-fontScale2`. Serif glyphs render and descenders aren't clipped. At 200%, `launchWordmark` stays at 48 and the other roles scale non-linearly. `Density(density, fontScale)` applies the API 34+ `FontScaleConverter`, so that is realistic.
- **Privacy:** the merged debug and release manifests have no `INTERNET` permission, and `verify<Variant>NoInternetPermission` is wired into `check`.
- **Commands** (JAVA_HOME = Android Studio JBR, daemon JVM 25 from `gradle-daemon-jvm.properties`):
  - `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug check`: BUILD SUCCESSFUL.
  - `./gradlew :app:testDebugUnitTest --rerun detekt --rerun-tasks`: BUILD SUCCESSFUL. 16 tests, 0 failures (LeoTextStyleTests 5, ThemeSnapshotTests 7, SnapshotLayoutTests 1, ThemeTests 3).

## Blocking

None.

## Should fix

1. **Color tokens and the high-contrast path have no effective test** (`Theme.kt:184-190`, `SnapshotHelpers.kt:54`).
   - D4 is untested. The snapshot helper hard-codes `highContrast = false`, and the light/dark/HC selection is inline in `LeoTheme`.
   - The `colors` snapshot also doesn't protect the hex values. One swatch is 84x84 px, which is about 0.24% of a 1170x2532 image. That is below the 1% `changeThreshold`, so a wrong hex on one token would still pass verify.
   - Fix:
     - Extract `internal fun leoColors(darkTheme: Boolean, highContrast: Boolean): LeoColors` and call it from `LeoTheme`.
     - Add a plain JUnit `LeoColorsTests` that asserts every token's ARGB for light and dark, as a table copied from the colorsets.
     - In the same test, cover the four `(dark, highContrast)` combinations: accent `0xFF006786` only for light+HC, divider alpha 0.45 for both HC variants, every other token unchanged.
     - Optionally add a `colors-highContrast` snapshot through a `highContrast` parameter on `assertViewSnapshot`.

2. **High contrast doesn't update live** (`Theme.kt:158-170`; iOS reads the trait, which updates live).
   - `remember(context, configuration)` only recomputes on a configuration change. A change to `UiModeManager.contrast` is not delivered as one; that is why API 34 added `UiModeManager.ContrastChangeListener`. A user who changes contrast while Leo is open keeps the old colors until the next config change or process restart.
   - Fix: on API 34+, keep the value in `mutableStateOf` and register `addContrastChangeListener(context.mainExecutor, listener)` in a `DisposableEffect`. Unregister it in `onDispose`.

3. **The backup rationale in the manifest comment and `changes.md` is wrong** (`AndroidManifest.xml:10-15`, `changes.md` "D8 / Q6").
   - The comment says `allowBackup="false"` "also blocks Android 12+ device-to-device transfer". The Android 12 behavior changes say the opposite: for apps targeting 31+, `allowBackup="false"` disables cloud backup but **not** D2D transfer.
   - The current configuration still meets Q6 (cloud off everywhere, D2D open on 31+). But the recorded reason for the choice is false, and it would end up in the user's learning notes.
   - Fix, either option:
     - Keep `true` and correct the comment and `changes.md`.
     - Or switch to `allowBackup="false"`, which is simpler and doesn't depend on the exclude lists being complete, and keep `data_extraction_rules.xml` for D2D.
   - Separately (Suggestion), D2D can also work on API 29-30. In `backup_rules.xml`, replace the excludes with `<include domain="..." path="." requireFlags="deviceToDeviceTransfer"/>` per domain. That only takes effect if `allowBackup` stays `true`.

4. **detekt 2.0.0-alpha.6 conflicts with Q3 ("latest stable")** (`libs.versions.toml:20`).
   - The reason is real: the daemon JVM is pinned to 25 by the template's `gradle/gradle-daemon-jvm.properties`, and 1.23.8 crashes on JDK 25. Still, an alpha lint tool gating the build is a dependency decision for the user, not the implementer (CLAUDE.md, "Approved dependencies").
   - Fix: get the user's explicit approval in `plan.md` Decisions. The alternative is `toolchainVersion=21` in `gradle-daemon-jvm.properties` plus detekt 1.23.8, but confirm 1.23.8 accepts Kotlin 2.2.10 sources before proposing it.
   - Also record that `--add-opens` in `app/build.gradle.kts:43-50` exists only because of JDK 25.

5. **The snapshot folder name is a free string, so the ported uniqueness test doesn't protect it** (`SnapshotHelpers.kt:39`, `ThemeSnapshotTests.kt:53`; iOS derives it from `#filePath`, `SnapshotHelpers.swift:108`).
   - `SnapshotLayoutTests` checks that file names are unique, but the folder comes from the `testClass` string each test passes in. Two classes can pass the same string and overwrite each other's images.
   - Fix: take `testClass: KClass<*>` (call sites pass `this::class`) and use `simpleName`. You could also read it from a `TestName` / `Description` rule.

6. **The snapshot helper switches dark mode and font scale only inside Compose** (`SnapshotHelpers.kt:49-54`; iOS sets system traits, `overrideUserInterfaceStyle` and `preferredContentSizeCategory`, `SnapshotHelpers.swift:50-53`).
   - `LeoTheme(darkTheme = dark)` and a hand-built `LocalDensity` never change `LocalConfiguration`.
   - As a result, `LeoTheme`'s real default (`isSystemInDarkTheme()`) isn't exercised. Any future composable that reads `isSystemInDarkTheme()`, `values-night` resources or `LocalConfiguration.fontScale` will render light or 100% in the "dark" and "200%" images.
   - Fix: wrap the content in `DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(isDark) then DeviceConfigurationOverride.FontScale(fontScale))` from `ui-test`, and call `LeoTheme { }` with its defaults. That keeps the non-linear scaling.
   - This is cheap now and expensive once phase 4 has dozens of references.

7. **The language suffix is missing from snapshot names** (`SnapshotHelpers.kt:64-65`; iOS `SnapshotHelpers.swift:66`, `"\(name)\(suffix)-\(TestEnvironment.appLanguage)"`). `plan.md` lists it under "Behavior to preserve", and `changes.md` defers it. Adding it later means renaming every reference.
   - Fix: append `-${Locale.getDefault().language}` now, or `-en` until the localized phases. Re-record and rename the 14 images.

## Nits

1. `LeoTextStyle.kt:92,109`: `includeFontPadding` has defaulted to `false` since Compose 1.5. Drop the `platformStyle` argument and the `@Suppress("DEPRECATION")`.
2. `Theme.kt:30-90`: `LeoColors` could be a `data class` with `highContrast()` written as `copy(accent = ..., divider = ...)`. That is 20 lines instead of 20 named arguments, and Kotlin readers will expect it.
3. `Theme.kt:192`: D1 maps only the `ColorScheme`. Stray Material text (a `Button` label, a `TextField` placeholder) will still be Roboto. Consider `typography = Typography().copy(...)` with `fontFamily = SourceSerif4` on the defaults, for the same reason D1 maps colors.
4. `data_extraction_rules.xml:4-9`: the `<cloud-backup>` excludes leave out the `device_root`, `device_file`, `device_database` and `device_sharedpref` domains. Nothing lives there today; add them so the rule stays exhaustive.
5. `changes.md` says "KSP 2.3.12 works with" Kotlin 2.2.10, but KSP isn't applied to any module yet, so this is unverified until Room or ML Kit arrive. Reword it as "declared, not yet exercised". The same goes for `com.google.mlkit.*` in `ForbiddenImport`, which the file already admits.
6. `app/build.gradle.kts:22`: `testInstrumentationRunner` stays, but every `androidTestImplementation` dependency was removed. That's fine, but the live-model phase must add `androidx.test` runner/ext deps, and those aren't in CLAUDE.md's approved list. Flag it to the user ahead of that phase.
7. `Layout.kt:33`: `@Composable` modifier factory. It works and lint passes, but it can't be used in a non-composable modifier chain. Acceptable as a screen-root helper.
8. There's no snapshot of `leoReadableWidth()` at a width above 648dp (tablet or landscape), so the cap and centering are untested. iOS doesn't test it either; optional.
9. `robolectric.properties`: `sdk=36` vs `targetSdk 37` is reasonable and documented. Add a TODO to move to 37 once Robolectric and compose-ui-test support it.

## Parity of tests

- `ThemeTests.fontIsRegistered` (3 arguments) → `ThemeTests.fontIsBundled` (parameterized over `LeoWeight.entries`): ported.
- `SnapshotLayoutTests.testFileNamesAreUnique` → ported, walking `src/test/java` (see Should fix 5 for the gap).
- The Android-only tests from the plan are present: `LeoTextStyleTests` (plus null-lineHeight and em-tracking cases), `ThemeSnapshotTests` (light, dark, 200% light and dark), and the privacy check as a `check` task.

## Verdict

**Changes needed.** Nothing blocks, and the build, tests and lint pass. The token values and type table match iOS exactly. Should fix 1-2 and 5-7 are small and much cheaper now than after phase 4 builds on the helper. Should fix 3-4 are documentation and user-decision items.

---

## Round 2

I re-read the working tree against `3fb722e`, including untracked files, and checked each fix in the code, not in `changes.md`.

### Verified

- **Commands** (JAVA_HOME = Android Studio JBR; the daemon now runs on JDK 21 per `gradle-daemon-jvm.properties`):
  - `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug check --rerun-tasks`: BUILD SUCCESSFUL.
  - 23 app tests, 0 failures: LeoColorsTests 6, ThemeTests 3, ThemeSnapshotTests 8, LeoTextStyleTests 5, SnapshotLayoutTests 1.
- **detekt 1.23.8 enforcement.** I copied the repo into the scratchpad (repo untouched) and added two files under `features/tmp/`. One imported `androidx.room.Dao` and `com.google.mlkit.genai.prompt.Generation`; the other had an empty `catch (e: Exception)`. `./gradlew :app:detekt` reported `ForbiddenImport` for both imports, plus `EmptyCatchBlock`, `TooGenericExceptionCaught` and `SwallowedException`. It ended with "Analysis failed with 6 weighted issues", BUILD FAILED. So the 1.x `imports:` key and rule-level `includes` work, and every finding fails the build: the default `maxIssues: 0` acts as warnings-as-errors.
- **Host-locale independence.** In the same copy, `JAVA_TOOL_OPTIONS="-Duser.language=es -Duser.country=ES" ./gradlew :app:testDebugUnitTest` passed with all 23 tests. Robolectric resets `Locale.getDefault()` from its own qualifiers, so the `-en` suffix doesn't depend on the developer's machine locale.

### Round 1 findings, rechecked

| # | Finding | Status | Evidence |
|---|---|---|---|
| S1 | Tokens and high contrast untested | **Fixed** | `Theme.kt:143-152` `leoColors()` is pure and used by `LeoTheme` (`:193`). `LeoColorsTests` holds an independent hex table for all 19 tokens in light and dark. I checked it line by line against my dump of the 19 `Contents.json` files and every value matches, including divider alpha `0x29` (0.16), `0x2E` (0.18) and `0x73` (0.45). The test also covers the four `(dark, highContrast)` combinations, with "only accent/divider change" asserted by comparing the full table, and checks that the key set equals the 19 tokens. New `colors-highContrast` snapshots opened: the divider and accent are visibly stronger. |
| S2 | Contrast not live | **Fixed** | `Theme.kt:169-181`. The listener is registered in a `DisposableEffect(uiModeManager)` and removed in `onDispose` with the same instance. State is a `mutableFloatStateOf` seeded from `uiModeManager.contrast` and keyed on the manager, which is keyed on `context`, so a new context re-registers. The callback runs on `context.mainExecutor`. The lambda captures only the state, so nothing leaks after dispose. The API 34 guard is `@RequiresApi` plus an SDK check. |
| S3 | Backup rationale wrong | **Fixed** | `AndroidManifest.xml:10-19` and `changes.md` D8 now state correctly that on 31+ `allowBackup="false"` disables cloud backup but not D2D. Keeping `true` (so the rule files are the single source) is a valid choice and meets Q6. `device_*` domains were added to `<cloud-backup>`. API 29-30 still skips D2D, which is stated and was accepted; the `requireFlags` option stays a Suggestion. |
| S4 | detekt alpha | **Fixed** | detekt is now stable `io.gitlab.arturbosch.detekt` 1.23.8 on a JDK 21 daemon, per the user's decision. Enforcement was verified above. The JDK-25-only `--add-opens` flags are gone; one `--add-exports` remains, and its comment explains why. |
| S5 | Folder name was a free string | **Fixed** | `SnapshotHelpers.kt:47,54` take `KClass<*>` and use `simpleName`; call sites pass `this::class`. |
| S6 | Dark mode and font scale only inside Compose | **Fixed** | `SnapshotHelpers.kt:60-64`: `DeviceConfigurationOverride(DarkMode(dark) then FontScale(fontScale))`, and `LeoTheme` uses its `isSystemInDarkTheme()` default. The 200% images still show non-linear scaling and an unscaled `launchWordmark`. |
| S7 | Language suffix | **Fixed** | Names follow `<name>[-dark]-<lang>.png`, which matches iOS `"\(name)\(suffix)-\(language)"`. `app/src/test/snapshots/ThemeSnapshotTests/` holds exactly 16 `-en` images (8 tests x light/dark), with no leftovers from the old naming in the tree or in `git status`. |
| N1 | `includeFontPadding` / `@Suppress` | Fixed | `LeoTextStyle.kt` now sets only `lineHeightStyle`. |
| N2 | `data class` + `copy` | Fixed | `Theme.kt:37,66-70`. |
| N3 | Material typography | Fixed | `Theme.kt:204-223`. |
| N4 | `device_*` domains | Fixed | `data_extraction_rules.xml`. |
| N5 | KSP / ML Kit wording | Fixed | `changes.md` now says "declared, not yet applied". The ML Kit `ForbiddenImport` was re-run on 1.23.8; I reproduced it above. |
| N6 | androidTest deps for live-model phase | Open, deferred | Recorded in `changes.md` for the user. Fine. |
| N7, N8 | `@Composable` modifier, tablet snapshot | Not applied | Acceptable, as said in round 1. |
| N9 | Robolectric TODO | Fixed | `robolectric.properties:1-2`. |

### New findings

**Blocking:** none.

**Should fix:** none.

**Nits and decisions for the user:**

1. **Decision for the user: the foojay toolchain resolver plugin** (`settings.gradle.kts:14-16`, `org.gradle.toolchains.foojay-resolver-convention` 1.0.0).
   - It came with the Android Studio template (it is present at `3fb722e`, so it wasn't added in this phase). It is not on CLAUDE.md's approved list.
   - It lets Gradle download a JDK from foojay.io when the pinned daemon JVM (21) isn't installed. The `toolchainUrl.*` entries in `gradle/gradle-daemon-jvm.properties` point there.
   - This is build-time network access only. The app's no-network promise is unaffected.
   - Options:
     - Keep it and add it to the approved list.
     - Remove it. Every contributor and CI then has to install JDK 21 themselves, and `updateDaemonJvm` can't write download URLs.
2. **Wrong iOS reference in a comment** (`SnapshotHelpers.kt:32`): it says "like the iOS `perceptualPrecision` of 0.99". iOS deliberately uses `precision: 0.99` and *not* `perceptualPrecision` (`SnapshotHelpers.swift:55-62`). Roborazzi's `changeThreshold` (share of differing pixels) is the equivalent of iOS `precision`, so only the comment is wrong. I missed this in round 1.
3. **A tiny race in the contrast listener** (`Theme.kt:174-177`): `contrast` is read in `remember` and the listener is registered later in `DisposableEffect`, so a change in that gap (one frame) would be missed. To close it, re-read `uiModeManager.contrast` inside the effect right after registering. Optional.

### Verdict (round 2)

**Approve.** All seven Should-fix items and the applied nits are fixed in the code and verified independently: the tests run, the detekt rules fire on a throwaway copy, the snapshots were re-opened, and the color table matches the iOS colorsets. The foojay resolver is a decision for the user, not a code defect.
