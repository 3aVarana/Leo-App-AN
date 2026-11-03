# Phase 5 QA (Preferences)

Branch `port/phase-5-preferences`, uncommitted working tree. Date 2026-10-31.

## Commands

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug` | BUILD SUCCESSFUL (44 tasks) |
| `./gradlew --rerun-tasks :domain:test testDebugUnitTest` (includes Roborazzi verify via `finalizeTestRoborazziDebug`) | BUILD SUCCESSFUL. 403 tests (domain + app), 0 failures, 0 errors, 0 skipped. Only warnings: pre-existing `ExperimentalCoroutinesApi` opt-in warnings in phase 2/3 tests. |
| `./gradlew detekt ktlintCheck lintDebug` | BUILD SUCCESSFUL |
| Manifest INTERNET | Only `tools:node="remove"` for `android.permission.INTERNET` (present, strips it). OK |
| ML Kit / Room imports under `features/` | None (grep empty) |
| `adb devices` | No device attached. |

## Device checks: NOT RUN (no device)

Steps for the user, on a physical device with Gemini Nano (check `checkStatus()` is AVAILABLE; this QA cannot say whether a device supports it):
1. `./gradlew installDebug`, `./gradlew connectedDebugAndroidTest`.
2. Fresh install: age picker, Continue disabled until a pick, Continue opens topics, system back returns to ages, Done opens the app, relaunch skips onboarding.
3. Add a real custom topic: spinner while Gemini Nano reviews it, accepted and rejected outcomes, "We couldn't check that topic" with airplane mode/model unavailable.
4. Rotate mid-review (onboarding and Settings): review continues or is dropped as designed, draft survives.
5. Settings (Home "Open settings" link): focus the Add field, confirm the keyboard does not cover it; Cancel and system back discard; Done saves.
6. Haptic: tap a topic chip at the minimum of 3 and feel one light tick per refused tap.
7. TalkBack: headings on kickers/titles, stepper adjustable (swipe up/down changes value), timer row announced as switch, disabled chip reads the "At least 3 topics must stay on." hint.
8. Spanish (`adb shell cmd locale set-app-locales com.victorarana.leo --locales es`), font scale 2.0 (reset to 1.0 after), dark mode, on every Phase 5 screen.

## Snapshot review

Opened: Settings `12-14-fontScale2` light, Settings `12-14` dark, TopicsEditor `9-11-customized-fontScale2` light, TopicsEditor `9-11-reviewing` dark, Onboarding `topics-fifteen` light, Onboarding `round-fontScale2` dark. The rest (30 images in total across the three suites) were not individually opened; they verify against the recorded files in the test run but were not visually compared with iOS.
- No clipping or overflow at 200%. Chips wrap, the age control fits, the timer row wraps its caption, the stepper moves below its caption at large scale.
- Copy correct, including "No limit while TalkBack is on." Dark mode legible, accent/ink contrast fine.
- Stepper is the grey pill with divider as decided (Q4). Spinner is a static arc (D8).
- Observation (not a failure): in Settings `12-14-fontScale2` light the tall image has a large empty area below the editor (image height padding), and in the dark `12-14` snapshot the stepper sits beside the caption instead of below it, per the fontScale 1.3 switch. Neither is a defect.
- Comparison with iOS references in `../Leo/LeoTestsSnapshots/` was visual and coarse (layout order, copy, structure); no pixel diff, and iOS references were not opened side by side image by image.

## Acceptance criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Onboarding flow, Continue gated, back, Done saves, relaunch skips | PARTIAL: JVM PASS, device NOT RUN | `OnboardingScreenTests`, `RootScreenTests`, `RootViewModelTests` pass in the 403; relaunch on a real device not run |
| 2 | Settings edits draft only; Cancel/back discard; Done saves once | PASS (JVM) / device NOT RUN | `SettingsScreenTests`, `RootViewModelTests` settings tests pass |
| 3 | Messages, limits, review outcomes and cancellation | PASS (JVM) / live model NOT RUN | `PreferencesEditorViewModelTests` pass incl. `saveCancelsPendingReview`; real Gemini Nano review not run |
| 4 | All ported VM tests + 6 root tests + Android-only tests | PASS | 403 tests, 0 failures; note `homeStaysComposedWhileSettingsSlidesOver` passes on old code too (per changes.md), so it does not guard R2-S1 |
| 5 | Snapshots light/dark/200%, opened, compared, no clipping | PASS with limited comparison | Verify green; 6 of the images opened (see above), no clipping seen |
| 6 | TalkBack semantics | PARTIAL: unit semantics PASS (stepper tests), TalkBack itself NOT RUN | `TextsPerRoundStepperTests`; device walk-through pending |
| 7 | Strings in 3 locales, no hard-coded UI text outside Placeholders | PASS | lintDebug green (missing translation checks); not exhaustively grepped |
| 8 | No ML Kit/Room in `features/`; build, tests, lint pass | PASS | Commands above |
| 9 | `changes.md` lists D1-D9, Q1-Q7; `docs/learning/phase-5.md` | FAIL | `docs/learning/` contains only phase-0..4; `phase-5.md` is missing. `changes.md` D/Q coverage was not audited line by line. |

## Verdict

Automated verification (build, 403 unit tests with snapshot verify, detekt, ktlint, lint, privacy checks) passes. Not ready to call done: `docs/learning/phase-5.md` is missing (criterion 9), and all device checks (onboarding, live Gemini Nano review, keyboard over field, TalkBack, rotation mid-review, haptic, Spanish/200%/dark by hand) are NOT RUN because no device was connected.

## Follow-up (orchestrator, 2026-10-31)
- Criterion 9 is now PASS. QA ran before the Teach step, so `docs/learning/phase-5.md` didn't exist yet; it has been written since. `changes.md` mentions D1-D9 and Q1-Q7 (checked with grep).
- Nothing else changed. Every device check is still NOT RUN, and the user must run them.
