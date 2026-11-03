# Phase 6: Quiz, QA

Branch `port/phase-6-quiz`, base `b1fde47`, uncommitted work. Run on 2026-11-03 from the repo root, nothing reused (`--rerun-tasks` or `clean`).

## Commands and results

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug` | BUILD SUCCESSFUL, 44 tasks executed |
| `./gradlew --rerun-tasks assembleDebug` | BUILD SUCCESSFUL, 42 tasks executed, no warnings |
| `./gradlew --rerun-tasks :domain:test testDebugUnitTest` (`roborazzi.test.verify=true`, so snapshots are compared) | BUILD SUCCESSFUL, 42 tasks executed |
| `./gradlew --rerun-tasks detekt ktlintCheck lintDebug` | BUILD SUCCESSFUL, 55 tasks executed, no lint/detekt/ktlint output |

Test counts, summed from the result XMLs: `:domain` 10 suites, 80 tests, 0 failures, 0 errors, 0 skipped. `:app` 54 suites, 479 tests, 0 failures, 0 errors, 0 skipped. Total 559, the same as review round 2.

Phase-6 suites (tests, all passing): QuizPlanTests 5, QuizViewModelTests 33, QuizViewModelGenerationTests 14, QuizViewModelRoundTests 19, QuizViewModelReadingTests 6, QuizViewModelRoundSizeTests 5, QuizViewModelOptionStateTests 4, QuizScreenTests 10, WelcomeScreenTests 7, ReadingTimerTests 9, RoundHistoryRecorderTests 2, RootViewModelTests 31, RootScreenTests 7; snapshots: Welcome 8, Loading 4, GenerationFailed 4, Exercise 11, Result 6, Review 5.

Warnings in the test run (all in files this phase did not touch, from earlier phases): five Kotlin `w:` lines "This declaration needs opt-in ... ExperimentalCoroutinesApi" in `app/src/test/.../data/genai/GenAiModelAvailabilityProviderTests.kt` (lines 82, 192, 195, 239) and `TopicValidatorTests.kt:122`; and the manifest merger notice "uses-permission#android.permission.INTERNET was tagged at AndroidManifest.xml:6 to remove other declarations but no other declaration present" (expected: the `tools:node="remove"` line). These do not fail the build; I did not check why they are not errors.

## Static checks (no device)

| Check | Result |
|---|---|
| No `com.google.mlkit` / `androidx.room` import under `features/` | PASS: `grep -rnE` found nothing |
| No INTERNET permission | PASS: the manifest only has `<uses-permission INTERNET tools:node="remove">`; the merged debug manifest has 0 matches for INTERNET |
| String keys in three locales | PASS: `values/`, `values-es/`, `values-pt-rBR/` each have 188 keys; `diff` of the sorted key sets is empty for es and pt-BR. 59 lines were added to `values/strings.xml` (strings plus the `quiz_spelled_out_counts` array) |
| `HomePlaceholder` deleted | PASS: no match in `app/src` or `docs` |
| `docs/learning/phase-6.md` | PASS: exists, 19 lines |
| `changes.md` mentions D1 to D10, Q1 to Q5, B2 known issue | PASS: "Decisions D1 to D10" lists D1 to D10 (D9 recorded as changed, with reason), "Answers Q1 to Q5", and a B2 entry "documented, not changed (user decision of 2026-11-03: keep iOS behavior)" |
| iOS repo untouched | PASS: `git -C ../Leo status --short` is empty |

## Snapshot images opened

At 200% font scale, light and dark, one or more per suite: Welcome `nine-texts-fontScale2` (light), `six-fontScale2` (dark); Loading `2-of-6-fontScale2` (light, dark); GenerationFailed `text-2-fontScale2` (light, dark); Exercise `reading-fontScale2` and `unanswered-fontScale2` (light, dark); Result `5-of-6-fontScale2` (light, dark); Review `1-of-1-fontScale2` (light, dark). Nothing clipped or overlapping: headlines wrap, the progress dots, bottom bars and buttons stay inside the frame, stacked Result rows are aligned, the Review top bar (arrow + "Results" left, "1 of 1 missed" right) fits, dark colors are correct. Observations, not defects: Welcome and Result snapshots show the History ("Past rounds") control because the snapshots pass `onHistory = {}` on purpose (changes.md Q3; the real `QuizScreen` hides it); in the Review 200% images the "Answer" option text wraps to five narrow lines because the label column takes a fixed share. I did not compare against iOS reference images in this run (the reviewer did in round 2).

## Device

`adb devices` (`~/Library/Android/sdk/platform-tools/adb`, not on PATH) lists no device or emulator. Gemini Nano availability is therefore unknown. Every device check is NOT RUN.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | A prepared round starts generating before Start; Welcome names its topics; Start shows Loading or the first ready exercise | PARTIAL | JVM: QuizViewModelTests / GenerationTests (configure starts generation, Start shows Loading or first ready exercise), WelcomeScreenTests 7 and Welcome snapshots (two topics, three-plus "and N more", none) pass. Not run: a live Gemini Nano round |
| 2 | Passage first, question after "Answer the question" or timer end; timer full per exercise, pauses in background, survives rotation, absent when off or with TalkBack on | PARTIAL | JVM: ReadingTimerTests 9 (Compose clock, lifecycle pause, `rememberSaveable` restore), QuizViewModelReadingTests 6, Exercise snapshots `reading`, `reading-untimed`, `reading-6-8`. Not run: real rotation, real background, TalkBack on |
| 3 | Option states, verdict, explanation, shake (not under Remove animations), haptic, "Next text"/"See results" | PARTIAL | JVM: QuizViewModelOptionStateTests 4, QuizViewModelRoundTests, Exercise snapshots (unanswered, correct, wrong, wrong-no-explanation, last-answered, last-missed). Not run: shake, haptic and Remove animations on a device (the shake is not asserted by a JVM test) |
| 4 | Failed exercise replaced with the same skill, at most 3 times per round; then Failed with both retries; earlier answers kept | PASS (JVM) | QuizPlanTests 5, QuizViewModelGenerationTests 14 (including the Q1 tests `cancellationThrownByTheRepositoryCountsAsAFailure` and the no-replacements variant), GenerationFailed snapshots 4. Failure paths need no device |
| 5 | Finishing saves exactly one `RoundRecord` (double tap included); Results/Review show the finished round after Settings changes; "Practice again" starts a new round of the current size | PARTIAL | JVM: RoundHistoryRecorderTests 2, QuizViewModelRoundTests 19 (`historySurvivesNewSettings`), QuizScreenTests 10, Result and Review snapshots. Not run: end-to-end against real Room on a device, Review restore after process death (changes.md says a mid-round restore is lost by design) |
| 6 | Ported Quiz/Plan/Root tests and Android-only tests pass; snapshot suites recorded, opened, compared with iOS, nothing clipped at 200% | PASS | 559 tests, 0 failures with Roborazzi verify; six new suites plus re-recorded LaunchIntro; images opened as listed above, no clipping. iOS comparison was done by the reviewer (round 2); I confirmed only the 200% images |
| 7 | TalkBack: headings, Loading announcement, option values, row actions, timer node, question focus | NOT RUN | No device. Semantics are set in code (`liveRegion = Polite` on Loading, `stateDescription` for option value, `onClick(label = "Review")`, timer node, `FocusRequester` on the question heading) but never heard. Left for the user |
| 8 | Strings in three locales; `HomePlaceholder` deleted; no ML Kit/Room under `features/`; build, tests, lint pass | PASS | See Commands and Static checks above |
| 9 | `changes.md` lists D1 to D10 and Q1 to Q5; `docs/learning/phase-6.md` written | PASS | See Static checks |

Decisions spot-check from code and docs only: Q1 (cancel counts as failure) covered by tests above; Q2 (pause/resume) covered by `pauseKeepsTheQueue...`, `resumeContinuesGeneration`, `QuizScreenTests.leavingTheAppPausesGenerationAndReturningResumesIt`, `recreatingTheActivityMidGenerationDoesNotCancelIt`; Q3 History hidden in `QuizScreen` (null `onHistory`); Q4 arrow + "Results" visible in the Review snapshots; Q5 plain strings (`tools:ignore="PluralsCandidate"`) in all three locales; B2 documented.

## Left for the user (all NOT RUN, no device)

1. Live Gemini Nano round (and whether the device reports Gemini Nano AVAILABLE).
2. `BACKGROUND_USE_BLOCKED` with pause/resume (Q2): background the app mid-generation, return, and check generation continues without a counted failure.
3. Rotation on a real phone: timer value and in-flight generation survive (B1 end to end).
4. TalkBack: headings, Loading announcement, option values, "Review" row action, timer node, question focus; also timer absent with TalkBack on.
5. Haptics (Confirm/Reject) on answer.
6. Remove animations on: no shake, no slide, instant scroll.
7. Review restore after process death.
8. System back from Home.
9. `./gradlew installDebug` and `./gradlew connectedDebugAndroidTest`.
10. Optional visual pass in Spanish, font scale 2.0 and dark mode on the real screens.

## Verdict

**PASS on everything that can run without a device; the phase is not fully verified.** Build, 559 unit and snapshot tests, detekt, ktlint and lint are green from scratch; the static checks and 200% images are clean. No FAIL rows. Criteria 1, 2, 3 and 5 are PARTIAL and criterion 7 is NOT RUN until the device checklist above is done.
