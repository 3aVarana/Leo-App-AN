# Phase 8: Polish, plan

Baseline `e872399` (phases 0-7 merged). iOS spec: `Leo/Localizable.xcstrings` (196 keys, en/es/pt-BR), the accessibility rules in iOS `CLAUDE.md:85-88`, the localization rules in iOS `CLAUDE.md:79-83`, `README.md`, `Features/Theme/Theme.swift:197-204`, `LeoTests/Support/SnapshotHelpers.swift`, `LeoTests/Support/TestEnvironment.swift:8-10`, `Leo/AppIcon.icon/`. iOS has no test of the catalog and no iPad, landscape, contrast or non-English snapshots. Reuse, don't duplicate: `features/theme/{Theme.kt (leoColors, rememberHighContrast), Accessibility.kt (rememberReduceMotion, rememberTalkBackOn, CappedFontScale, scaled), Layout.kt (leoReadableWidth)}`, `components/ScrollingScreen.kt` (`LeoBottomBar`), `preferences/OnboardingScreen.kt` (`Headline`, `SectionKicker`), `support/SnapshotHelpers.kt` (`assertViewSnapshot`, `SNAPSHOT_DEVICE_QUALIFIERS`), `SnapshotLayoutTests`.

## Audit results

**Strings: no missing key or translation.** All 196 iOS keys have an Android string in `values/`, `values-es/` and `values-pt-rBR/strings.xml`, and the es/pt-BR text matches iOS word for word. Every `%@`/`%lld` maps to `%n$s`/`%n$d` with the same order. Lint already fails on `MissingTranslation`, `ExtraTranslation` and `StringFormat*` (`app/build.gradle.kts:53-55`, `warningsAsErrors`). Intentional differences, already recorded, stay as they are:
- Platform names: VoiceOver becomes TalkBack (`xcstrings:2561` → `settings_reading_timer_caption`), Apple Intelligence becomes Gemini Nano (`xcstrings:607,623,639,2625`).
- `xcstrings:2785` "Turn on Apple Intelligence in Settings…" has no Android counterpart. Android adds `unavailable_device_not_eligible_detail`, `unavailable_download_*` and `unavailable_preparing_title` instead (phase 4).
- iOS accessibility hints become Android action labels: "Removes this topic." (`xcstrings:1841`) → `custom_topic_chip_remove_action`, and "Shows the round" (`:2081`) → `history_row_action` (phase 7 Q4).
- `settings_suggested_count` is `<plurals>` on Android but a plain string on iOS (`xcstrings:2257`). The user approved this in phase 5.
- Android-only strings: `navigation_back`, the `quiz_spelled_out_counts` array, `app_name`.

What is left: the count-plural decision (Q1) and the formatting-locale follow-ups (Q2, Q3).

**Accessibility semantics are already in place on every screen.** Each iOS `.isHeader` has a heading on Android:
- `UnavailableView:22` → `UnavailableScreen.kt:115`
- `WelcomeView:21,47` → `WelcomeScreen.kt:72,88`
- `LoadingView:29` → `LoadingScreen.kt:58`
- `ExerciseView:91,105,141` → `ExerciseScreen.kt:249,282,311`
- `GenerationFailedView:30` → `GenerationFailedScreen.kt:87`
- `ResultView:49` → `ResultScreen.kt:105`
- `ReviewView:69,97` → `ReviewScreen.kt:127,158`
- `OnboardingView:38,80,90,120` → `OnboardingScreen.kt:115,161,171,203`
- `SettingsView:20,29,35,51,78` → `SettingsScreen.kt:72,93,102,105,114`
- `TopicsEditor:21` → `TopicsEditor.kt:91`
- `HistoryView:68` → `HistoryScreen.kt:160`
- `RoundView:17` → `RoundScreen.kt:81`

The rest matches too:
- **Icon-only controls have labels:** `GearButton.kt:45`, `HistoryButton.kt:73`, `TopicChip.kt:96`. The stepper is one adjustable node (`TextsPerRoundStepper.kt:79-90`).
- **Merged and combined rows:** `ResultScreen.kt:147,160`, `RoundScreen.kt:117,132`, `HistoryScreen.kt:283-291`, `AnswerRow.kt:57`, `ProgressDots.kt:82`.
- **Reduce Motion is respected:** `LaunchIntroOverlay.kt:103`, `SkeletonLines.kt:43`, `UnavailableScreen.kt:204`, `ExerciseScreen.kt:347`, `ReviewScreen.kt:66`, `LeoNavHost.kt:46`, `TopicsEditor.kt:209`. The crossfades in `RootScreen.kt:81`, `QuizScreen.kt:44` and `ExerciseScreen.kt:141` are left to the system's animator scale of 0 (checked on a device).
- **No hard-coded colors under `features/`** outside `theme/` (grep).
- **Every snapshot suite except LaunchIntro has a 200% case.**

No semantics gap was found by reading. What is still unverified is behavior: nothing has been heard with TalkBack in phases 4-7, and the touch-target and label rules have no automated test.

**High contrast.** iOS: only `Accent`, `Divider` and `AccentColor` have Increase Contrast variants (`Assets.xcassets/Colors/{Accent,Divider}.colorset`). Android has had this since phase 0:
- `Theme.kt:65-77`: accent `#006786` in light mode, divider alpha 0.45.
- `Theme.kt:154-181`: `UiModeManager.contrast >= 0.5`, API 34+ only (phase-0 Q1/Q5). Below API 34 there is no public API.
- Gap: only `ThemeSnapshotTests` covers it, no screen does.

**Tablets and landscape.** iOS `leoReadableWidth` (24 pt gutters, max 648, `Theme.swift:200-204`) is ported in `Layout.kt:15-27` and used by `ScrollingScreen.kt:70,235`, `ExerciseScreen.kt:127`, `ReviewScreen.kt:112`, `SettingsScreen.kt:68,90` and `HistoryScreen.kt:157,191,300,308`, the same places as iOS. The manifest has no orientation lock, and the safe-drawing insets include the cutout.
- Gap: no snapshot is taken at any size other than 390x844 portrait.
- Bug: `SnapshotHelpers.kt:57` always applies `+h844dp` (`heightDp` defaults to 844), which would break a landscape or tablet qualifier.

**Phase 8 follow-ups from earlier phases.**
- Phase-6 S3 and phase-7 changes:110: the formatting locale (Q2).
- Phase-7 PR #12: plurals (Q1).
- Phase-0 Q10: the launcher icon is still the template (`mipmap-*/ic_launcher*`) (Q5).
- Device checklists never run: phase-4 changes:119/133 (splash, rotation, download states), phase-5 qa:53, phase-6 qa:58-67, and phase-7 qa:57-64, including "Don't keep activities" (phase-4 N10).

## File map

| iOS | Android (`app/`) |
|---|---|
| `Localizable.xcstrings` "Review %lld missed" (`:1921`), "%lld of %lld missed" (`:84`), "Drawn from %@ and %lld more…" (`:977`) | `res/values{,-es,-pt-rBR}/strings.xml`: `round_review_missed`, `review_count`, `quiz_topics_more` become `<plurals>` if Q1 is yes. Call sites: `RoundScreen.kt:73`, `ReviewScreen.kt:77`, `WelcomeScreen.kt:140` (`pluralStringResource`) |
| `Locale.current` behind every formatter (`WelcomeView.swift:70-73,84`, `HistoryView.swift:79,160-161`, `RoundView.swift:46`, `RoundHistoryViewModel.swift:85`, `ExerciseView.swift:227`) | New `features/theme/ResolvedLocale.kt`: pure `resolvedLocale(resourceTag, deviceLocale)` and `@Composable rememberResolvedLocale()`; string `resource_locale` ("en" / "es" / "pt-BR"). Replaces `locales[0]` at `WelcomeScreen.kt:115,128`, `ReadingTimer.kt:129`, `ResultScreen.kt:245`, `ProgressDots.kt:192`, `UnavailableScreen.kt:145`, `HistoryFormatting.kt:92` (Q2) |
| Per-app language: Settings › Leo › Language (system) | `app/build.gradle.kts` `androidResources { generateLocaleConfig = true; localeFilters += ["en","es","pt-rBR"] }`, plus `src/main/res/resources.properties` (`unqualifiedResLocale=en-US`) (Q3) |
| `SnapshotHelpers.swift` | `support/SnapshotHelpers.kt`: `heightDp: Int? = null` (no `+h` override unless asked); names use `pt-BR` like iOS `preferredLocalizations` (en/es names don't change); `highContrast` adds `-highContrast` |
| (none on iOS) | New snapshot suites in `app/src/test/.../snapshots/`: `TabletSnapshotTests` (`w1280dp-h800dp-land-xhdpi`), `LandscapeSnapshotTests` (`w844dp-h390dp-land-xxhdpi`), `SpanishSnapshotTests` (`+es`), `PortugueseSnapshotTests` (`+pt-rBR`), `HighContrastSnapshotTests` |
| `AppIcon.icon` (`Pages.svg`, `Spark.svg`, gradient `#38A6CF`→`#006786`, dark `#0F4D64`→`#072634`) | `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`, `mipmap-anydpi/ic_launcher{,_round}.xml`; delete the template webps (Q5) |
| `README.md` | `README.md` (see Behavior) |
| (iOS sim has the model) | Debug-only stub model in `app/src/debug/` for emulator UI checks, if Q4 is yes |

## Behavior to preserve

- es and pt-BR copy is word for word `Localizable.xcstrings`. The only plural on iOS is "%lld rounds read · %@ answered right" (`xcstrings:100-157`), already `history_summary`.
- `xcstrings:174` `", "` (comment "Separates topic names in a list") stays a quoted `", "`.
- iOS formatters follow `Locale.current`: the app's resolved localization for the language, the device region for the rest (e.g. `en_FR` on a French phone). So a French device gets English words and English "and", plus the French first weekday and date order. Today Android mixes English strings with a French `ListFormatter` and French month names.
- First weekday: `HistoryFormatting.kt:105` (`Locale.getDefault()`) stays region-driven, as on iOS.
- Readable width: 24 gutters, max 648 including them, start-aligned (`Theme.swift:198-204`).
- Spoken values are unchanged: the timer reads minutes and seconds in full words (`ExerciseView.swift:227`), the stepper value is the number (`TextsPerRoundStepper.swift:32`).
- README follows the iOS sections (`README.md:1-139`), adapted to Android:
  - Intro and screenshot table: Android references `OnboardingSnapshotTests/topics-nine`, `ExerciseScreenSnapshotTests/wrong`, `ResultScreenSnapshotTests/3-of-6`, `ReviewScreenSnapshotTests/2-of-3`, each light/dark with `<picture>`.
  - Why Leo: verbatim.
  - Features: TalkBack, font size up to 200%, dark theme, contrast on Android 14+, phones and tablets in both orientations.
  - How it uses Gemini Nano: ML Kit Prompt API, Structured Output, the same repair rules, streaming, a fresh request each time, `ContentLanguage`.
  - When the model isn't available: the allowlist, the download with progress, no emulators.
  - Privacy: no `INTERNET` permission, DataStore + Room, no cloud backup, device-to-device transfer on.
  - Requirements: a Gemini Nano device, minSdk 29, JDK 21 daemon.
  - Project structure: `:domain` and `:app` `data/` + `features/`.
  - Testing: the four Gradle commands plus Roborazzi, and `@LiveModel` instrumented tests.
  - CI: `claude-review.yml` only.
  - Status (port phases), License and Credits: copied, plus the Material Symbols Apache notice.

## Tests to port

None from iOS: it has no localization or layout tests. Android-only:
- `features/theme/ResolvedLocaleTests`: en/es/pt-BR resources × devices `fr-FR`, `es-MX`, `pt-PT`, `[fr, es]`, `en-GB`. Language comes from the resources, region from the device. Robolectric `@Config(qualifiers="fr-rFR")` shows `WelcomeScreen` and `HistoryScreen` in English words with English "and" and English month names.
- Plurals (if Q1): `WelcomeScreenTests.spanish` changes ("y 1 tema más de los tuyos"); new es/pt-BR cases in `HistoryScreenLocaleTests` ("Repasar 1 fallada", "Revisar 1 errada") and `ReviewScreen` ("1 de 1 fallada"). English is unchanged.
- `features/AccessibilityRulesTests`, per screen with fixtures:
  - every node with a click action has text or `contentDescription` (`onAllNodes(hasClickAction())`);
  - icon-only and ghost controls are at least 48dp (`assertTouchHeightIsAtLeast`/`Width`): `GearButton`, `HistoryButton`, `BackBar`, stepper ±, chips, age segments;
  - one heading per screen title.
- Snapshot suites (light + dark, opened and compared):
  - **Tablet:** Welcome, Exercise `reading` and `wrong`, Settings, History.
  - **Landscape phone:** Welcome, Exercise `reading` at 1x and 200%, Onboarding age, Settings.
  - **es:** Welcome, Exercise `wrong`, Results, Settings 1x and 200%, History, Round.
  - **pt-BR:** Onboarding topics, Unavailable `device-not-eligible`, Review.
  - **High contrast:** Exercise `correct`, Onboarding topics, Settings.
- Existing references must not change (`verifyRoborazziDebug`).

## Android decisions

1. **Locale** (Q2): use the resource-resolved language rather than `locales[0]`. A `resource_locale` string avoids reimplementing Android's resource matching.
2. **Tablets:** no separate tablet layout, same as iOS (a centered readable column). Settings stays a full-screen route on tablets (Q6).
3. **Contrast below API 34** has no public API. The system "High contrast text" is drawn by the system, and whether it reaches Compose text is a device observation, not code.
4. **"Remove animations"** stays the Reduce Motion stand-in (phase 0). Compose's own transitions already honor the animator scale.

## Open questions for the user

1. **Plurals for `round_review_missed`, `review_count`, `quiz_topics_more`?** Recommend yes for all three, Android only, as with `settings_suggested_count` in phase 5. `round_review_missed` and `quiz_topics_more` key on the count; `review_count` keys on the total (`%2$d`, "1 de 1 fallada"). en is unchanged; es/pt-BR get `one`/`many`/`other`. Record it in `changes.md`, and offer the same catalog fix to iOS separately.
2. **Formatting locale:** language from the resources, region from the device, as iOS `Locale.current` does? Recommend yes. It fixes phase-6 S3 and phase-7 changes:110.
3. **Per-app language** (Android 13+ "App languages" plus `localeFilters`, so library strings and TalkBack role words follow the app's three languages)? Recommend yes. It needs no dependency; API 29-32 keep the system language (AppCompat is not approved).
4. **Debug-only stub model for emulators?** Gemini Nano doesn't run on emulators and few tablets support it, so without a stub "tablets checked" can only come from snapshots. Recommend yes: `app/src/debug/`, enabled by an `am start --ez leo.stubModel true` extra, absent from release (check with `apkanalyzer`).
5. **Port the iOS app icon now** (phase-0 Q10)? Recommend yes: a flat adaptive icon plus a monochrome layer. Liquid Glass lighting can't be reproduced.
6. **Settings on tablets:** iOS shows a form sheet on iPad (`RootView.swift:24` `.sheet`). Keep Android's full-screen slide-up (recommended), or use a Navigation `dialog` above 600dp width?
7. **Automated Accessibility Test Framework checks** (`ui-test-junit4-accessibility`, not approved) or hand-written rule tests? Recommend hand-written: no new dependency.

## Acceptance criteria

JVM (QA can run these without a device):
1. `./gradlew assembleDebug`, `:domain:test testDebugUnitTest` (Roborazzi verify on) and `detekt ktlintCheck lintDebug` pass. Lint shows no `MissingTranslation`/`StringFormat*`/`PluralsCandidate` except the documented ignores.
2. The three `strings.xml` have the same key set, matching es/pt-BR text against the catalog except the listed intentional differences, and the Q1 outcome applied, with tests.
3. `ResolvedLocaleTests` and the `fr-rFR` screen tests pass, and no `locales[0]` is left under `features/` outside `ResolvedLocale.kt` (grep).
4. The merged manifest has `android:localeConfig` and the generated `locale_config.xml` lists en-US, es, pt-BR (Q3).
5. `AccessibilityRulesTests` pass.
6. The new tablet, landscape, es, pt-BR and high-contrast references exist, each was opened, nothing is clipped or overlapping, and no existing reference changed.
7. `README.md` has every section listed above, and its image links resolve to committed PNGs. The launcher icon is replaced (Q5). The plurals entry in `docs/Leo-Android-Port-Plan.md` "Known differences" records the decision. `docs/learning/phase-8.md` is written, and `changes.md` lists each Android-only change.

Device or emulator (QA runs these, or leaves them for the user with exact steps):

8. TalkBack walk through every screen. Headings via the headings menu; the gear, History, Back and stepper are announced; option values; the "Review", "Show round" and "Delete" actions; Loading re-announces; the timer is hidden.
9. 200% font (`adb shell settings put system font_scale 2.0`) and dark mode on every screen.
10. Contrast set to medium or high (API 34+) switches accent and divider live.
11. Pixel Tablet emulator in both orientations, and a phone in landscape: the column is centered, the bottom bars and `TopOffset` are usable, and rotation keeps the state.
12. Per-app language set to es and pt-BR; the device in French shows consistent English.
13. Remove animations is on.
14. The carried-over phase 4-7 lists: splash, "Don't keep activities", live round.

## Tasks (each ends with the three Gradle commands green)

1. Apply Q1 in the three locales and the three call sites; update and add the locale tests.
2. Add `ResolvedLocale.kt`, `resource_locale` and `ResolvedLocaleTests`; switch the 8 call sites; add the `fr-rFR` screen tests.
3. Per-app language config (Q3); check the merged manifest.
4. `SnapshotHelpers.kt`: optional height and the `pt-BR` tag; verify that no reference changed.
5. Add the five snapshot suites and record them. Open every image and fix what clips, re-recording on purpose.
6. `AccessibilityRulesTests`; fix any control they catch.
7. Debug stub model (Q4) and the release check.
8. Launcher icon (Q5): vectors from the SVGs, light and dark backgrounds, monochrome layer.
9. `README.md`.
10. Port plan "Known differences", `changes.md`, `docs/learning/phase-8.md`.
11. Run the device/emulator checklist (8-14), or report it NOT RUN with `adb devices` output.

## Decisions

Answered by the user on 2026-11-03; every recommendation accepted.

1. **Plurals:** yes, all three (`round_review_missed`, `review_count` keyed on the total, `quiz_topics_more`). Android-only deviation, recorded in `changes.md` and in the port plan's "Known differences".
2. **Formatting locale:** language from the resolved resources, region from the device, as iOS `Locale.current`.
3. **Per-app language:** yes, `generateLocaleConfig` + `localeFilters` en/es/pt-BR.
4. **Debug stub model:** yes, `app/src/debug/` only, enabled by an `am start` extra, verified absent from release.
5. **App icon:** port now as a flat adaptive icon with a monochrome layer.
6. **Settings on tablets:** keep the full-screen slide-up route.
7. **Accessibility checks:** hand-written `AccessibilityRulesTests`, no new dependency.
