# Phase 7: History, QA

Branch `port/phase-7-history`, baseline `94ab7ca`, uncommitted work. Run on 2026-11-03 from the repo root with `--rerun-tasks` (nothing reused). No source modified.

## Commands and results

| Command | Result |
|---|---|
| `./gradlew clean assembleDebug --rerun-tasks` | BUILD SUCCESSFUL, 44 tasks executed |
| `./gradlew --rerun-tasks :domain:test testDebugUnitTest` (`roborazzi.test.verify=true` in `gradle.properties`, so snapshots are compared) | BUILD SUCCESSFUL, 42 tasks executed |
| `./gradlew --rerun-tasks detekt ktlintCheck lintDebug` | BUILD SUCCESSFUL, 55 tasks executed, no detekt / ktlint / lint output |

Test counts, summed from the result XMLs: `:domain` 10 suites, 80 tests; `:app` 59 suites, 559 tests; 0 failures, 0 errors, 0 skipped. Total 639 (the figure in changes.md after the review fixes; phase 6 had 559 total, so 80 added).

Phase-7 suites (tests): RoundHistoryViewModelTests 30, HistoryFormattingTests 10, HistoryScreenTests 23, HistoryScreenLocaleTests 4, HistoryScreenSnapshotTests 5 (cases, light + dark each), RoundScreenSnapshotTests 3, RootViewModelTests 33, RootScreenTests 10, QuizScreenTests 11, ReviewScreenSnapshotTests 5 (unchanged references pass after the `BackBar` extraction).

Warnings in the test run: the same five Kotlin `w:` lines about `ExperimentalCoroutinesApi` opt-in in `GenAiModelAvailabilityProviderTests.kt` (82, 192, 195, 239) and `TopicValidatorTests.kt:122` as in phase 6 (earlier-phase files, not touched here, not build failures). No new warnings.

## Static checks (no device needed)

| Check | Result |
|---|---|
| No `com.google.mlkit` / `androidx.room` import under `features/` | PASS: `grep -rnE` finds nothing |
| No INTERNET permission | PASS: the source manifest only has `<uses-permission INTERNET tools:node="remove">`; all merged manifests under `app/build/intermediates/merged_manifest*` have 0 matches (including `verifyDebugNoInternetPermission`) |
| Strings in three locales | PASS: `values/`, `values-es/`, `values-pt-rBR/` each have 201 `<string>`/`<plurals>` entries; every phase-7 key (`history_title`, `history_summary` plurals, `history_this_week`, `history_last_week`, `history_earlier_in`, `history_delete`, `history_row_accessibility`, `round_kicker`, `round_topic_skill`, `round_review_missed`, `round_row_correct`, `round_row_missed`, `navigation_back`) is in all three. The plan's `history_row_hint` was replaced by `history_row_action` ("Show round" / "Ver la ronda" / "Ver a rodada", Q4), present in all three |
| `HistoryPlaceholder` and `RoundHistoryRecorder` deleted | PASS in code: `Placeholders.kt` and `RoundHistoryRecorder.kt` (+ its tests) are gone; no match in `app/src`. The only match in `docs/` is the historical text of `docs/learning/phase-6.md:13`, which describes phase 6 as written (not a defect) |
| `docs/learning/phase-7.md` | PASS: exists, 17 lines |
| `changes.md` lists D1 to D8 and Q1 to Q5 | PASS: "Decisions D1 to D8 and answers Q1 to Q5" section |
| iOS repo untouched | PASS: `git -C ../Leo status --short` is empty |

## Snapshot images opened

Files exist for all cases: `app/src/test/snapshots/HistoryScreenSnapshotTests/` (three-sections, one-round, one-round-fontScale2, nine-texts-fontScale2, last-year; light + dark each = 10 PNGs) and `RoundScreenSnapshotTests/` (2-missed, 2-missed-fontScale2, 6-of-6; light + dark = 6 PNGs).

Opened: History `three-sections-dark`, `one-round-fontScale2` (light), `nine-texts-fontScale2-dark`; Round `2-missed-fontScale2` (light). Nothing clipped or overlapping: Back bar fits, the display title and summary wrap, pinned-style section headers sit on the ground, rows keep date/time, count and dots inside the frame, the 9-dot row fits at 200% (stacked: date, time, "6 of 9" over dots), chevrons are present at 1x and the stacked 200% rows drop to a vertical layout without one, the Round page wraps long titles with hanging indents, "Review" chips and the bottom "Review 2 missed" button are inside the frame, dark colors correct. Observations, not defects: the stacked 200% row puts the "6 of 9" count over the dots with a small left inset relative to the date (it is centered over the dots' width); the line gap under the display title is a little looser than iOS (already noted in changes.md as the phase-0 gap); times are UTC on purpose (D8). I did not compare against iOS reference images in this run (changes.md says the author did; the reviewer approved round 2).

## Device

`~/Library/Android/sdk/platform-tools/adb devices` lists no device or emulator. Gemini Nano availability is unknown. `installDebug`, `connectedDebugAndroidTest` and every manual device check are NOT RUN.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Finishing a round saves it (also when the Activity finishes right after); History button then appears on Results and Welcome; hidden while the age group has no rounds | PARTIAL | JVM: RoundHistoryViewModelTests (`savesTheRoundOnce`, `recordCompletesAfterTheViewModelIsCleared`, `aFailedSaveIsLoggedNotThrown`, `recordForAnotherAgeGroupDoesNotShow`), RootViewModelTests `finishedRoundReachesHistory`, QuizScreenTests `resultsShowsHistoryOnceTheRoundIsSaved`, RootScreenTests `historyButtonHiddenWithoutRounds`, `historyButtonIgnoresRoundsOfAnotherAgeGroup`. Not run: a real round saved to Room on a device, then relaunch |
| 2 | History shows header, summary (singular for one), sections This week / Last week / Earlier in month / month [year], empty ones left out, weeks from the locale's first day | PASS (JVM) | RoundHistoryViewModelTests `sectionsGroupByWeekThenMonth`, `lastWeekWinsOverThePreviousMonth`, `sectionsFollowTheFirstDayOfWeek`, DST week test; HistoryFormattingTests 10 (month names en/es/pt-BR, year only when asked); HistoryScreenLocaleTests 4; snapshots three-sections, one-round, last-year. Not run: Android 14 "First day of week" regional setting on a device (D3) |
| 3 | Rows match iOS at 1x and stacked at 200%; tap opens the round; a missed row and "Review N missed" open Review whose last button is "Back to round" | PASS (JVM) / images | HistoryScreenTests 23 (tap row -> Round, missed row -> Review page -> "Back to round" returns to Round); snapshots one-round(-fontScale2), nine-texts-fontScale2, Round 2-missed(-fontScale2), 6-of-6 opened, no clipping. iOS image comparison not repeated here |
| 4 | Swipe (and the TalkBack action) deletes without confirmation; deleting the last round returns to the previous screen with the button hidden | PARTIAL | HistoryScreenTests (swipe end-to-start deletes via `swipeLeft()`, custom "Delete" action, deleting the last round pops to Home and hides the button); ViewModel `deleteRemovesTheRowBeforeTheStoreAnswers`, `deleteFailureRestoresTheRound`. Not run: a real finger swipe and TalkBack on a device |
| 5 | Changing the age group in Settings switches list and totals; switching back restores them | PASS (JVM) | RoundHistoryViewModelTests `hasRoundsFollowsTheAgeGroup`, `ageGroupChangeReloads`, `lateReloadForAPreviousAgeGroupIsDropped`, `settingTheSameAgeGroupDoesNotReload`; RootViewModelTests `preferencesDidChangeSetsHistoryAgeGroup`. Not run: through the real Settings screen on a device |
| 6 | A restored History/Round/Review route after process death shows the right content or pops, never blank (device check with "Don't keep activities", closes phase-4 N10) | PARTIAL | JVM (HistoryScreenTests): restored History, empty History pops, missing round pops, invalid id pops, Review of a round without misses pops; configure effect hoisted above the `NavHost`. NOT RUN: the real "Don't keep activities" check on a device, so N10 is not closed by evidence from a device |
| 7 | All ported and Android-only tests pass; snapshot suites recorded, every image opened and compared with iOS, nothing clipped at 200% | PASS (with a caveat) | 639 tests, 0 failures with Roborazzi verify. 16 new PNGs exist; I opened four (list above), nothing clipped. I did not re-compare with iOS; the author and reviewer did |
| 8 | Strings in three locales; `HistoryPlaceholder` and `RoundHistoryRecorder` deleted; no Room/ML Kit under `features/`; the three Gradle commands pass | PASS | See Commands and Static checks |
| 9 | `changes.md` lists D1 to D8 and Q1 to Q5; `docs/learning/phase-7.md` written | PASS | See Static checks |

## Left for the user (all NOT RUN, no device)

1. `./gradlew installDebug` and `./gradlew connectedDebugAndroidTest`; whether the device reports Gemini Nano AVAILABLE.
2. Finish a real round, see History appear on Results, force-stop and relaunch, History still has it.
3. Swipe delete with a finger; TalkBack "Delete" and "Show round" actions; delete the last round (back to the previous screen, button hidden).
4. Settings age-group switch and back (list and totals follow).
5. "Don't keep activities" with History, Round and Review open: each restores or pops, never blank (closes phase-4 N10).
6. Font scale 2.0 (`adb shell settings put system font_scale 2.0`, reset to 1.0), Spanish (`adb shell cmd locale set-app-locales com.victorarana.leo --locales es`), dark mode, landscape on the real screens.
7. Android 14 "First day of week" setting changing the section boundaries.
8. Remove animations: no slide between History, Round and Review.

## Verdict

**PASS on everything that can run without a device; the phase is not fully verified.** Build, 639 unit and snapshot tests, detekt, ktlint and lint are green from scratch; the static checks and opened images are clean. No FAIL rows. Criteria 1, 4 and 6 are PARTIAL (JVM coverage only) until the device checklist is done; criterion 6's "Don't keep activities" device check is the one that closes phase-4 N10 and has not been run.
