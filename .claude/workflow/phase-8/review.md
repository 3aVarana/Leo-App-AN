# Phase 8: Polish, review (round 1)

Reviewed: the uncommitted working tree on `port/phase-8-polish` against `e872399` (`baseline.sha`), untracked files included. Spec: `plan.md` (Decisions 1-7) and the iOS app at `../Leo`.

## Evidence I ran myself

| Check | Result |
|---|---|
| `./gradlew assembleDebug assembleRelease :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --continue` | BUILD SUCCESSFUL. `:app` debug unit tests: 615, 0 failed, 0 skipped (from `test-results/testDebugUnitTest`). `StubLanguageModelTests` ran. ktlint covers `debug`, `release`, `testDebug`. |
| Merged manifests (debug, release) | `android:localeConfig="@xml/_generated_res_locale_config"`. No `INTERNET` permission (only the existing ML Kit `ACCESS_NETWORK_STATE` and AICore bind). |
| Generated `_generated_res_locale_config.xml` | `defaultLocale="en-US"`, locales `en-US`, `es`, `pt-BR`. |
| `aapt2 dump configurations` on the release APK | Locale configs left: `es`, `pt`, `pt-rBR` (no `en-r*`, `es-rUS`, `b+es+419`). |
| `strings` over `classes*.dex` | Debug: `StubLanguageModel` 23, `StubModelAvailabilityProvider` 8, `leo.stubModel` 1, `leo.stubAvailability` 1. Release: all 0. (Release still has 2 hits for `StubModelHook`, the no-op twin. That's expected.) |
| `git status app/src/test/snapshots` | Only new folders. No existing reference was modified or deleted. No old reference used the `-pt` suffix, so the `pt-BR` rename touches nothing. |
| Images opened | `LandscapeSnapshotTests/reading-fontScale2-en`, `welcome-en`; `TabletSnapshotTests/history-en`, `wrong-dark-en`; `SpanishSnapshotTests/12-14-fontScale2-es`; `PortugueseSnapshotTests/device-not-eligible-dark-pt-BR`; `HighContrastSnapshotTests/correct-highContrast-en`; `LauncherIconSnapshotTests/masks-en`, `masks-dark-en`. Nothing clipped or overlapping except as noted under N4. On the tablet, the column is 648dp including gutters and centered. |
| README | Every relative link resolves: the 8 PNGs, the `data/genai/*.kt` files, `docs/Leo-Android-Port-Plan.md`, both `assets/licenses/*.md`. These claims check out: the INTERNET build check (`app/build.gradle.kts:76-92`), `minSdk` 29, JDK 21 (`gradle-daemon-jvm.properties`), `claude-review.yml` as the only workflow, `@Generable` on `GeneratedExercise`, `SwipeToDismissBox`, the detekt `ForbiddenImport` rule, and the iOS repo URL (`3aVarana/Leo-App`). |

## Verified, no finding

- **Plurals (Decision 1).**
  - en `one` and `other` are byte-identical to the old strings, so English output is unchanged.
  - es and pt-BR have `one`/`many`/`other`, which is what CLDR requires (`many` covers 1,000,000 and up; lint's `MissingQuantity` passes).
  - `review_count` is selected on `misses.size`, the total (`ReviewScreen.kt:78`), and `HistoryScreenLocaleTests.portugueseReviewCountAgreesWithTheTotal` ("1 de 2 erradas") would fail if it were keyed on the position.
  - pt `one` also covers 0 (CLDR `i = 0..1`). No call site can reach 0: the Round button is only built when `misses` isn't empty, Review always has at least one miss, and `more` is at least 1.
  - The pt-BR `quiz_topics_more` reads the same in every form, and that's correct.
- **`resolvedLocale` (pure function).**
  - It takes the language from `resource_locale` and the region plus the unicode extensions (Android 14 `fw`/`hc`/`ca`/`nu`/`mu`) from the device. That matches iOS `Locale.current` (app localization + Region).
  - A device script is correctly not copied.
  - On pt-PT, Android 7+ matching falls back to `values-pt-rBR`, and the result is `pt-PT`, with the region kept. That's correct.
  - On a `[fr-FR, es-ES]` device, the resources resolve to es and the region is FR, giving `es-FR`, as on iOS.
  - The plural rules come from the configuration's `locales[0]`, which `ResourcesImpl` moves to the best-matching locale, so es/pt plural selection is right on multi-locale devices.
- **Changing the `localeFirstDayOfWeek()` default to `deviceLocale()`** is fine. Without a per-app language it equals `Locale.getDefault()`. With one, see S1: it isn't worse than before, but it doesn't achieve what `changes.md` says.
- **`Kicker` uppercasing in the resolved locale** matches iOS `textCase` under `Locale.current` (an en_TR phone gets "I", not "İ").
- **Debug stub model.**
  - It lives in `src/debug` only. The `src/release` twin has the same members and returns null.
  - There's no new dependency. In release it can't be turned on by an intent extra: the release `enableIfRequested` is `= Unit`, and the stub classes aren't in the dex.
  - `enableIfRequested` runs before the lazy `viewModels` (and so before `AppContainer.languageModel`/`availability`) are first touched. Process-death restore re-delivers the original intent.
- **Snapshot helper.** `heightDp: Int? = null` is equivalent for every existing caller, since `SNAPSHOT_DEVICE_QUALIFIERS` already has `h844dp`. Verify passes.
- **The `AccessibilityRulesTests` label, 48dp and heading rules do test something.**
  - The 48dp check includes the stepper's ± buttons: `clearAndSetSemantics` hides them from the merged tree but not from the unmerged tree, as `TextsPerRoundStepperTests` already relies on.
  - The guard test proves the controls are found.

## Findings

### Blocking

None.

### Should-fix

**S1. `deviceLocale()` very likely returns the per-app language, not the device locale, on Android 13+ once "App languages" is set.**
- Where: `app/src/main/java/com/victorarana/leo/features/theme/ResolvedLocale.kt:36-39`, which `HistoryFormatting.kt:100` also uses. The claim is also made in `changes.md` task 2 and in "Android-only deviations" (first weekday).
- iOS reference: `Locale.current`, the app language plus the system Region (plan, Behavior to preserve).
- What's wrong:
  - A per-app locale is applied as an override of the app process's configuration (`WindowProcessController.applyAppSpecificConfig`). `ResourcesManager.applyConfigurationToResources` pushes that same configuration into `Resources.updateSystemConfiguration`, so in-process `Resources.getSystem().configuration.locales` starts with the app's locale (e.g. `es` or `es-ES`).
  - That's why API 33 added `LocaleManager.getSystemLocales()` ("the current system locales, ignoring app-specific overrides").
  - Result: a French phone with Leo set to Spanish gets `es`/`es-ES` formatting and a Spain/Spanish first weekday instead of `es-FR`. That is the exact case Decisions 2 and 3 together were meant to cover. Robolectric can't show this, because qualifiers set a single system locale.
- Fix:
  - On API 33+, read `context.getSystemService(LocaleManager::class.java).systemLocales[0]`, and fall back to `Resources.getSystem().configuration.locales[0]` below 33 (no per-app language exists there).
  - That means `deviceLocale` needs a `Context`: use `LocalContext.current` in `rememberResolvedLocale`.
  - For `RoundHistoryViewModel`, pass `firstDayOfWeek` from the `MainActivity` factory using the application context, instead of a default that reaches for a static.
  - Add a JVM test of the selection: the API-33 path is chosen and the region comes from the system list.
  - Add a QA step to checklist 12:
    ```
    adb shell cmd locale set-app-locales com.victorarana.leo --locales es
    ```
    with the system set to `fr-FR`. Expect History month headers in Spanish in French date order, and Monday as the first weekday. Run `get-app-locales` to confirm.
  - Correct `changes.md` either way.

**S2. `AccessibilityRulesTests.theseControlsCanBeActivatedByTalkBack` can never fail.**
- Where: `app/src/test/java/com/victorarana/leo/features/AccessibilityRulesTests.kt:265-271`.
- What's wrong: `clickables()` selects nodes with `hasClickAction()`, which *is* `keyIsDefined(SemanticsActions.OnClick)`. Filtering those for `OnClick == null` is always empty. `changes.md` task 6 counts it as one of the six rules.
- Fix: replace it with a rule that can fail. Two options:
  - (a) every node with a `Role` of `Button`, `RadioButton`, `Switch`, `Checkbox`, `DropdownList` or `Tab` has `OnClick`;
  - (b) every `CustomActions` entry has a non-empty label. That covers the History row "Delete" and the chip's "Remove", which none of the current rules look at. History is in the screen list, so the row's custom action is reachable.
  
  Or delete it, and update `changes.md` to say five rules.

**S3. The duplicate age-segment nodes aren't a TalkBack bug, but fix them now anyway: it's one line.**
- Where: `app/src/main/java/com/victorarana/leo/features/components/AgeSegmentedControl.kt:65-67`, and the `isPlaced` filter at `AccessibilityRulesTests.kt:192-194,227-229`.
- Why it isn't a TalkBack bug:
  - The `Fit` subcomposition is measured and never placed. Compose's accessibility delegate drops nodes whose `LayoutNode` isn't placed, so TalkBack won't announce the options twice.
  - The placed `Segments` is its own subtree under its own `selectableGroup()`, so its collection info still counts 5.
- Why fix it anyway:
  - The duplicates leak into every test finder (Settings has 10 radio nodes).
  - They forced the `isPlaced` filter into both rules, and that filter would also hide a real unplaced-node problem elsewhere.
- Fix:
  ```kotlin
  subcompose(AgeControlSlot.Fit) { Box(Modifier.clearAndSetSemantics {}) { Segments(selection, onSelect) } }
  ```
  The `Box` measures to the same intrinsic width. Then drop the `isPlaced` filters, or keep them with a comment that nothing depends on them, and still run checklist item 8 on a device.

### Nit

**N1. The launcher icon snapshot's dark image and KDoc are misleading.**
- Where: `app/src/test/java/com/victorarana/leo/snapshots/LauncherIconSnapshotTests.kt:33-36,49-58`.
- Dark image: `masks-dark-en.png` shows the light gradient. The `AndroidView` resolves `@mipmap/ic_launcher` against a context that `DeviceConfigurationOverride` doesn't change, so `drawable-night/ic_launcher_background.xml` (`#0F4D64`→`#072634`) is never shown.
- KDoc: "the launcher, not the app, picks the background" isn't accurate either. The launcher resolves the app's `-night` resource against the system's night mode.
- Fix: in the dark pass, load the drawable from `context.createConfigurationContext(Configuration(context.resources.configuration).apply { uiMode = UI_MODE_NIGHT_YES or (uiMode and UI_MODE_NIGHT_MASK.inv()) })`, so the dark reference shows the night icon. Otherwise drop the dark reference.
- Themed row: it draws the raw monochrome layer, without `AdaptiveIconDrawable`'s 1.5x layer scaling, so it looks about 2/3 the size of a real themed icon. Wrap it in `AdaptiveIconDrawable(ColorDrawable(ground), monochrome)` and tint, or say so in the KDoc.

**N2. The `localeFilters` comment overstates what the filter does.**
- Where: `app/build.gradle.kts:46-47`.
- What's wrong: TalkBack speaks role names ("Button", "Radio button") from its own resources, in the system language. What the filter actually trims is library strings such as Compose/Material state descriptions and labels.
- Side effect: the APK no longer carries the libraries' `es-rUS`/`b+es+419` translations, so Latin-American readers get Spain-Spanish library strings. That's acceptable, because the app's own strings are a single `values-es`.
- Fix: reword the comment and add one line about the Latin-American Spanish effect to `changes.md`.

**N3. `ResolvedLocaleTests` names cases it doesn't test.**
- Where: `app/src/test/java/com/victorarana/leo/features/theme/ResolvedLocaleTests.kt:52-57`.
- What's wrong: `theFirstLocaleOfTheListGivesTheRegion` only exercises `list[0]`, not the actual `[fr, es]` resource resolution. Robolectric qualifiers can't express a list, so say that in the KDoc.
- Fix: also add a `@Config(qualifiers = "pt-rPT")` case asserting `rememberResolvedLocale()` is `pt-PT` and a pt-BR string is shown. That pins the pt-PT → `values-pt-rBR` fallback the formatting depends on.

**N4. In landscape at 200% font, the reading page shows about one line of passage.**
- Where: `LandscapeSnapshotTests/reading-fontScale2-*.png`.
- This is parity: iOS pins the timer and button with `.safeAreaInset(edge: .bottom)` (`Leo/Features/Quiz/ExerciseView.swift:51`). Don't change it silently.
- Fix: put the question to the user in the phase report: whether a compact-height layout should let the bar scroll with the passage, which would be an Android-only change. Keep it in QA item 11.

**N5. `changes.md` says the learning note is left to the user.**
- Where: `.claude/workflow/phase-8/changes.md` task 10, "docs/learning/phase-8.md is NOT written (the user does it)".
- What's wrong: per `.claude/skills/port-phase/SKILL.md:16` and `CLAUDE.md` → Teaching, the agent writes it at step 7, after review and QA. Not writing it yet is fine; the stated reason is wrong.
- Fix: reword to "written after QA (skill step 7)".

## Verdict

**Changes needed.** Nothing blocks. Fix S1, the per-app-language region that contradicts Decision 2 and `changes.md`, then S2 and S3, then re-run `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`. The nits can go in the same pass.

## Round 2

Re-checked only the fixes for the round-1 findings, plus anything they could have broken.

### Evidence

| Check | Result |
|---|---|
| `./gradlew assembleDebug assembleRelease :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --continue` | BUILD SUCCESSFUL. `:app` debug unit tests: 619, 0 failed, 0 skipped (was 615). |
| `git status app/src/test/snapshots` | No tracked reference is modified or deleted. The Settings and Onboarding references survived the `AgeSegmentedControl` change, and verify passes. The phase-8 folders are untracked, so git can't diff them against round 1. I re-opened the launcher icon images; Roborazzi verify passes for the rest. |
| `javap -p` on the debug `MainActivity.class` | `private static final java.time.DayOfWeek history_delegate$lambda$0$0$0$0(com.victorarana.leo.MainActivity)`. The new `firstDayOfWeek` lambda captures the Activity (see B1). |

### Round-1 findings

- **S1: correct in substance, but the injection leaks the Activity (B1).**
  - `deviceLocale(context)` uses `LocaleManager.systemLocales` on API 33+ and `Resources.getSystem()` below, and the selection is a pure function.
  - Tests cover it: `ResolvedLocaleTests.theSystemListWinsOverTheProcessesOnApi33` tests the selection, and `theApi33PathAnswersWithTheSystemList` runs the API-33 path.
  - `rememberResolvedLocale()` is keyed on the configuration and the context.
  - `localeFirstDayOfWeek` has no default any more, and `RoundHistoryViewModel` takes `firstDayOfWeek` from its caller. `changes.md` is corrected and has the QA step.
- **S2: fixed.**
  - `everyNodeWithAnInteractiveRoleCanBeActivated` and `everyCustomActionHasALabel` can fail. The custom-action rule has a `found > 0` guard, and `changes.md` says both were inverted once to see them fail.
  - The vacuous test is gone.
- **S3: fixed.**
  - The `Fit` slot is wrapped in `Box(Modifier.clearAndSetSemantics {})` (`AgeSegmentedControl.kt:66-67`), and the `isPlaced` filters are gone.
  - The guard pins "6 to 8" to exactly one node on Settings. The references are unchanged.
- **N1: fixed.**
  - `masks-dark-en.png` shows the `drawable-night` gradient.
  - The themed row is an `AdaptiveIconDrawable` and is now the same size as the full-color icon.
  - The KDoc is accurate.
- **N2: fixed.** The comment at `app/build.gradle.kts:37-40` is accurate, and the Latin-American Spanish side effect is in `changes.md`.
- **N3: fixed.** `portugalGetsBrazilianStringsAndItsOwnRegion` (`pt-rPT`) pins the pt-BR fallback and the `pt-PT` region. The list test's KDoc says what it doesn't cover.
- **N4:** left for the user, as agreed.
- **N5: fixed.** `changes.md` task 10.

### New findings

**B1 (Blocking). `RoundHistoryViewModel` holds the first `MainActivity` for the rest of the ViewModel's life.**
- Where: `app/src/main/java/com/victorarana/leo/MainActivity.kt:30`
  ```kotlin
  firstDayOfWeek = { localeFirstDayOfWeek(deviceLocale(application)) }
  ```
- Why it leaks:
  - Inside `initializer { }` the receiver is `CreationExtras`, so `application` resolves to `this@MainActivity.getApplication()`, read each time the lambda runs. The lambda therefore keeps a reference to the Activity (bytecode above).
  - The ViewModel outlives the Activity on rotation. After a configuration change, the destroyed Activity and its whole view tree stay reachable from the `ViewModelStore` until the user finishes the Activity.
  - The surrounding `(application as LeoApplication).container` line is fine: it runs once, inside the factory, and nothing stores the Activity.
- Fix: capture the application before building the lambda.
  ```kotlin
  val app = application
  RoundHistoryViewModel(..., firstDayOfWeek = { localeFirstDayOfWeek(deviceLocale(app)) })
  ```
  You could equally read it with `this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]`, or put a `firstDayOfWeek` provider on `AppContainer`, built from its `appContext`.
- After the fix, `javap -p` should show the lambda taking an `android.app.Application` (or `LeoApplication`), not a `MainActivity`.

**N6 (Nit). `pickDeviceLocale` assumes the list isn't empty.**
- Where: `app/src/main/java/com/victorarana/leo/features/theme/ResolvedLocale.kt:57`.
- What's wrong: `LocaleList.get(0)` returns null on an empty list, and the platform type then fails with an NPE at the non-null return. `LocaleManager` shouldn't return an empty list, but the fallback costs nothing.
- Fix:
  ```kotlin
  (systemLocales?.takeUnless { it.isEmpty } ?: processLocales)[0] ?: Locale.getDefault()
  ```

### Round 2 verdict

**Blocking findings: B1.** Fix the captured Activity, check with `javap` as above, and re-run `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`. Everything else from round 1 is resolved. Once B1 is fixed this is approved; it doesn't need another full round.

## Round 2 follow-up

B1 fixed (`MainActivity.kt:25,32`, `val app = application`). `javap -p` shows `history_delegate$lambda$0$0$0$0(android.app.Application)`. N6 fixed with a test. The implementer reports the three Gradle command groups green (620 app + 80 domain tests). Per the round-2 verdict, this approves the phase; no further review round.
