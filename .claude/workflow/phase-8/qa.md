# Phase 8: Polish, QA

Branch `port/phase-8-polish`, uncommitted tree on top of `e872399`. Run on 2026-11-03 from `clean`. Source was not edited.

## Commands and results

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug assembleRelease` | BUILD SUCCESSFUL (46s, 101 tasks). Debug and release APKs built. |
| `./gradlew :domain:test testDebugUnitTest` (`roborazzi.test.verify=true` in `gradle.properties:21`) | BUILD SUCCESSFUL. `:app` 620 tests, 0 failed, 0 skipped, 0 errors. `:domain` 80 tests, 0 failed (counted from the JUnit XML). `git status` shows no tracked snapshot modified or deleted after verify. |
| `./gradlew detekt ktlintCheck lintDebug` | BUILD SUCCESSFUL. Lint: 0 errors, 0 warnings, 1 hint (the existing `AutoboxingStateCreation`). `lint-results-debug.xml` has no `PluralsCandidate` or `MissingTranslation` entry. |
| `~/Library/Android/sdk/platform-tools/adb devices -l` | Empty list. No device and no running emulator. |
| `~/Library/Android/sdk/emulator/emulator -list-avds` | `Medium_Phone_API_37.0` (system image `android-37.0/google_apis_playstore`). |

## Device state

No device is connected, so 8 to 14 are NOT RUN, and so are `installDebug`, `connectedDebugAndroidTest` and the Gemini Nano availability check.

An AVD exists that the user could start for the debug stub model: `Medium_Phone_API_37.0` (a phone, API 37, Play image). There is no tablet AVD (no Pixel Tablet system image or AVD), so the tablet half of 11 needs one created (`avdmanager`, Pixel Tablet profile) or a physical tablet. An emulator reports Gemini Nano as unavailable, and the stub is the way around that:

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 &
cd /Users/vicchorarana/Desktop/My-Apps/Leo-Android && ./gradlew installDebug
$ADB shell am start -S --ez leo.stubModel true -n com.victorarana.leo/.MainActivity
# optional: --es leo.stubAvailability downloadable|notEligible
```

The remaining steps are the "For QA" block in `changes.md` (font scale 2.0 then reset to 1.0, `cmd uimode night yes`, `settings put global animator_duration_scale 0`, `always_finish_activities 1`, `cmd locale set-app-locales com.victorarana.leo --locales es`, `emulator` locale to fr-FR for the "French device" case, and `adb shell wm size` or the emulator rotation for landscape). The real model and TalkBack with a live round need a physical Gemini Nano device.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | The three Gradle command groups pass. Lint has no `MissingTranslation`/`StringFormat*`/`PluralsCandidate` beyond the documented ignores | PASS | Table above. Remaining `tools:ignore="PluralsCandidate"` are only on the existing `age_group_summary_*`, `onboarding_topics_count`, `quiz_failed_kicker`, `history_row_accessibility` (not the three Q1 strings). |
| 2 | Same key set in the three `strings.xml`, es/pt-BR text equal to the catalog except the listed differences, Q1 applied with tests | PASS | 203 names in each file, symmetric difference empty (both pairs). I compared every iOS catalog string (196 keys) with the Android values after normalizing placeholders. en: 0 differences. es and pt-BR: 9 each, all in the documented list (`", "` is only a quoting artifact of my script; 4 Apple Intelligence strings; "Removes this topic."; "Shows the round"; the VoiceOver caption; "Turn on Apple Intelligence…"). Plurals: `round_review_missed`, `review_count`, `quiz_topics_more` have `one`/`other` in en and `one`/`many`/`other` in es and pt-BR. Tests: `PluralStringsTests` 3/3, plus the `HistoryScreenLocaleTests` and `WelcomeScreenTests` cases, in the 620 green. |
| 3 | `ResolvedLocaleTests` and the `fr-rFR` screen tests pass. No `locales[0]` under `features/` outside `ResolvedLocale.kt` | PASS | `ResolvedLocaleTests` 13/13 (JUnit XML). `grep -rnE 'locales\[0\]\|locales\.get\(0\)\|configuration\.locale' features/` outside `ResolvedLocale.kt`: no match. The only `Locale.getDefault` under `main` is the last-resort fallback at `ResolvedLocale.kt:63`. The Welcome/History/Round `fr-rFR` tests are inside `ResolvedLocaleTests`. |
| 4 | Merged manifest has `android:localeConfig`; the generated `locale_config` lists en-US, es, pt-BR | PASS | Debug (`processDebugManifest` line 44) and release (line 43) manifests: `android:localeConfig="@xml/_generated_res_locale_config"`. `app/build/generated/res/localeConfig/*/xml/_generated_res_locale_config.xml`: `en-US`, `es`, `pt-BR`. `res/resources.properties`: `unqualifiedResLocale=en-US`. |
| 5 | `AccessibilityRulesTests` pass | PASS | 7/7, 0 failed, 0 skipped. |
| 6 | New tablet, landscape, es, pt-BR and high-contrast references exist, were opened, nothing clipped or overlapping, no existing reference changed | PASS (one parity observation) | 48 new untracked PNGs: Tablet 10, Landscape 10, Spanish 14, Portuguese 6, HighContrast 6, LauncherIcon 2, matching the plan's case lists. `git status` shows every `app/src/test/snapshots` change as `??` (new folders) and no ` M` or ` D` on an existing reference; verify passes. Opened: `TabletSnapshotTests/settings-dark-en` (column centered, one 648dp column, all controls inside it), `LandscapeSnapshotTests/onboarding-age-en` (readable column with gutters, Continue bar fixed at the bottom; only two age options fit, the rest scrolls), `SpanishSnapshotTests/three-sections-es` ("4 rondas leídas · 75 % respondido correctamente", "ESTA SEMANA", "20 mar", "FEBRERO", "6 de 6"), `PortugueseSnapshotTests/2-of-3-pt-BR` ("2 de 3 erradas", "Próximo texto errado", "Você"/"Resposta"), `HighContrastSnapshotTests/topics-nine-highContrast-en` (deep `#006786` Done button and kicker; the stepper scrolls under the bottom bar, as designed), `LauncherIconSnapshotTests/masks-dark-en` (night gradient, themed row, same size as the colored row). The reviewer also opened others (review.md). Observation, not a failure: `LandscapeSnapshotTests/reading-fontScale2` shows about one line of passage (review N4); the question to the user is whether a compact-height layout should let the bar scroll. I did not re-open that image. |
| 7 | README sections and links; launcher icon replaced; plan "Known differences" updated; `docs/learning/phase-8.md`; `changes.md` lists each change | PASS | README headings: Why Leo, Features, How it uses Gemini Nano (Writing exercises, Reviewing custom topics, When the model isn't available), Privacy, Requirements, Project structure, Testing, Continuous integration, Status and roadmap, License, Credits; the intro has the four-screenshot table. Every relative link and all 8 image paths resolve (`[ -e ]` loop: no MISSING); the existing reference PNGs are tracked (`git ls-files`). External links not fetched. Icon: the 10 template `.webp` files are deleted (` D`), `drawable/ic_launcher_{foreground,background,monochrome}.xml`, `drawable-night/ic_launcher_background.xml` and `mipmap-anydpi/ic_launcher{,_round}.xml` exist; the manifest points at `@mipmap/ic_launcher{,_round}`. `docs/Leo-Android-Port-Plan.md` has five phase-8 entries (plurals, formatting locale, per-app language, stub model, icon). `docs/learning/phase-8.md` exists (untracked). `changes.md` has an "Android-only deviations" section. |
| (extra) | Stub model absent from the release dex; no `INTERNET` permission | PASS | `strings classes*.dex`: debug `StubLanguageModel` 24, `StubModelAvailabilityProvider` 9, `leo.stubModel` 1, `leo.stubAvailability` 1; release 0, 0, 0, 0. Release holds only the no-op `StubModelHook` (2 hits), as designed. Both merged manifests list only `BIND_SERVICE` (AICore), the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` and `ACCESS_NETWORK_STATE`; no `INTERNET`. The build's `verify{Debug,Release}NoInternetPermission` tasks passed. |
| 8 | TalkBack walk through every screen | NOT RUN | No device. TalkBack and the headings menu need a device or an emulator with TalkBack installed. Semantics are covered only by `AccessibilityRulesTests` (labels, 48dp, headings, roles, custom-action labels). |
| 9 | 200% font and dark mode on every screen | NOT RUN | No device. Covered on the JVM by the snapshot 200% cases and the dark variants; not seen on hardware. |
| 10 | Contrast medium/high (API 34+) switches accent and divider live | NOT RUN | No device. `ThemeSnapshotTests` and the new `HighContrastSnapshotTests` cover the colors only. |
| 11 | Pixel Tablet in both orientations, phone in landscape, state kept on rotation | NOT RUN | No device. Only `Medium_Phone_API_37.0` exists as an AVD (no tablet AVD). Tablet and landscape layouts are covered by snapshots only. |
| 12 | Per-app language es and pt-BR; French device shows consistent English; the S1 `fr-FR` + app `es` case | NOT RUN | No device. The `LocaleManager` path is covered only by the pure selection test and the API-33 shadow test, not by a real per-app override. |
| 13 | Remove animations on | NOT RUN | No device. |
| 14 | Carried-over phase 4-7 lists: splash, "Don't keep activities", live round | NOT RUN | No device. The stub model would let an emulator walk a full round. |

## Overall verdict

**JVM criteria 1-7: PASS.** Build, 620 + 80 unit tests with Roborazzi verify, detekt, ktlint and lint are all green from a clean state. No tracked snapshot changed, the 48 new references are present, the locale config is in the manifest, the stub is out of the release dex, and there is no `INTERNET` permission. No finding.

**Device criteria 8-14: NOT RUN, no device.** The phase is not fully verified until the user runs them. Until then, TalkBack behavior, live contrast switching, real per-app-language region handling, rotation and tablets, and the launcher icon on a real launcher are unobserved. Items the user should look at first: 12 (the S1 case, a French system with Leo set to Spanish), 11 (rotation state and the 200% landscape reading page, review N4), and 8 (the age control announced once).
