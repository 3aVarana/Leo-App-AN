# Phase 7: History, changes

Branch `port/phase-7-history`, baseline `94ab7ca`. Nothing committed. Learning note: `docs/learning/phase-7.md`.

## Files

Added, `app/src/main/java/com/victorarana/leo/`:
- `features/history/`: `RoundHistoryViewModel.kt` (`RoundHistoryViewModel`, `RoundHistoryUiState`, `HistorySection` with sealed `Kind`, companion `sections`), `HistoryScreen.kt` (`HistoryScreen`, private `Header`, `SectionHeader`, `SwipeToDelete`, `HistoryRow`), `RoundScreen.kt` (`RoundScreen`, private `RoundResultRow`), `HistoryFormatting.kt` (`monthName`, `rowDate`, `rowTime`, `accessibilityDay`, `roundKicker`, `percentText`, `localeFirstDayOfWeek`, `currentLocale`).
- `features/components/BackBar.kt` (arrow + parent title + optional trailing caption, extracted from `ReviewScreen`'s private `TopBar`).
- `features/root/HistoryDestinations.kt` (`NavGraphBuilder.historyDestinations`: History, HistoryRound, HistoryReview, and `rememberRound` for restored routes).
- `res/drawable/ic_chevron_right.xml`.

Deleted: `data/repositories/RoundHistoryRecorder.kt` and `RoundHistoryRecorderTests` (folded into `RoundHistoryViewModel.record`, phase-6 D6), `features/root/Placeholders.kt` (`HistoryPlaceholder`).

Changed: `AppContainer.kt` (no `roundHistoryRecorder`), `MainActivity.kt` (`history` in the Activity store; quiz's `onRoundFinished = history::record`; `RootViewModel(..., quiz, history)`), `features/root/RootViewModel.kt` (`history` constructor parameter; `preferencesDidChange()` also calls `history.setAgeGroup`), `features/root/LeoNavHost.kt` (the configure effect moved above the `NavHost`; Home passes `onHistory` when `hasRounds`; History destinations; `isOverHome` covers them), `features/root/LeoRoute.kt` (+ `HistoryRound(id)`, `HistoryReview(roundId, startIndex)`), `features/quiz/ReviewScreen.kt` (uses `BackBar`), `features/components/ScrollingScreen.kt` (+ optional `topBar`), `values/`, `values-es/`, `values-pt-rBR/strings.xml` (14 strings and the `history_summary` plurals each; `review_back_to_round` lost its `UnusedResources` ignore and comment).

Tests added (`app/src/test/java/com/victorarana/leo/`): `features/history/` `RoundHistoryViewModelTests` (26), `HistoryFormattingTests` (10), `HistoryScreenTests` (21), `HistoryScreenLocaleTests` (4); snapshots `RoundScreenSnapshotTests` (3 cases), `HistoryScreenSnapshotTests` (5 cases), light + dark each; `RootViewModelTests` +2 (`preferencesDidChangeSetsHistoryAgeGroup`, `finishedRoundReachesHistory`); `RootScreenTests` +3 (`homeNavigatesToHistoryAndBackPops` restored, `historyButtonHiddenWithoutRounds`, `historyButtonIgnoresRoundsOfAnotherAgeGroup`); `QuizScreenTests` +1 (`resultsShowsHistoryOnceTheRoundIsSaved`); support `RoundHistoryViewModelSupport.kt` (`testHistory`), `RoundHistoryRepositorySpy` (+ `deleteError`, `summariesGate`, `deleteGate`, `summariesCalls`), `makeQuiz(spy, onRoundFinished)`. `OnboardingScreenTests`, `SettingsScreenTests`, `RootScreenTests`, `QuizScreenTests`, `RootViewModelTests` pass a `testHistory()` to `RootViewModel`.

## Decisions D1 to D8 and answers Q1 to Q5

- D1 done as planned. Constructor `(repository, saveScope, now, zone, firstDayOfWeek)`; `StateFlow<RoundHistoryUiState>` with `hasRounds`, `roundCount`, `correctShare` as members; loads are cancel-and-replace jobs on `viewModelScope` whose answer is dropped if the age group changed; `record` saves on `saveScope`; `suspend fun round(id)`; `delete` removes the row at once, then reloads (which puts it back if the delete failed). Additions to the plan: `setAgeGroup` clears `summaries` and `isLoaded` at once, so a state never shows another age group's rounds; `open(id)` (D6) keeps the round as `openedRound`. The plan's "key `history`" in the Activity store is not needed: `by viewModels` keys by class, and there is only one.
- D2 done: `Kind.EarlierThisMonth(YearMonth)` / `Kind.Month(YearMonth, showsYear)`, `java.time`, half-open intervals (iOS `DateInterval.contains` includes its end; the difference is one instant that can't hold a finished round). Tests include a week with a daylight saving change (`weeksFollowTheLocalClockAcrossADaylightSavingChange`).
- D3 done: ICU skeletons `MMMd`, `MMMMd`, `LLLL`, `LLLLy`, `hm`/`Hm`; the kicker's date and time is one skeleton (`MMMdhm`), so en reads "Jan 1, 12:00 AM" where iOS reads "Dec 31 at 8:00 PM" (CLDR version and zone, accepted). Percent: `NumberFormat.getPercentInstance`, 0 digits (0.785 gives "78%", test). First weekday: ICU `Calendar.getInstance(locale)`, including a `-u-fw-` extension (tested; Android 14's setting still to check on a device).
- D4 / Q1 done: `SwipeToDismissBox`, end-to-start only, full swipe deletes, TalkBack custom action "Delete". Differences from the plan: the background is the theme's error pair (`magenta` with the ground color as text), not white (2.1:1 on dark mode's light pink) and not the ground on `magenta600` (4.31:1 in light mode); the delete is triggered from M3's `onDismiss` (`confirmValueChange` is deprecated), which also snaps the row back to rest (review B1). Background color: see review S3 below.
- D5 / Q3 done: `LazyColumn`, keys by round id, `stickyHeader` with the ground behind (test: `theSectionTitleStaysPinnedWhileItsRoundsScroll`, fails with a plain `item`, checked). `isLoaded && !hasRounds` calls `onEmpty`, which pops if this entry is on top. Addition: 20 dp above every section header (including the first) to match the iOS references, where a plain `List` adds that on its own.
- D6 done: `HistoryRound(id: String)`, `HistoryReview(roundId, startIndex)`; tap loads with `history.open(id)`, navigates only if the round loaded and the entry is still RESUMED, taps meanwhile are ignored; restored routes load by id and pop when it can't (tests for a restored History, an empty one, a missing round, an invalid id, a Review of a round without misses). Slide transitions as Review; the screen below stays composed under the next one. The configure effect is above the `NavHost`.
- D7 done: rows are one button node with the action label (Q4) and the "Delete" custom action; section titles and the header are headings; the dots are hidden (phase 4); correct round rows are one node, missed rows a button labelled "Review".
- D8 / Q5 done: rows and kicker format in `uiState.zone`; snapshots pass UTC. Times therefore differ from the iOS images on purpose ("12:00 AM" for a 2001-01-01 UTC fixture, "9:12 AM", "1:00 AM").
- Q2 done: `BackBar` with "Back" (`navigation_back`: Back / Atrás / Voltar) on History, Round and Review-from-round (Review from Results still says "Results", phase-6 Q4). Review's snapshots are unchanged (verified).
- Q4 done: the row action label is `history_row_action` "Show round" / "Ver la ronda" / "Ver a rodada", not the iOS hint "Shows the round" (Android change: TalkBack reads "double tap to <label>").

## Other deviations from the plan or from iOS

- `ic_chevron_right.xml`: the path is the Material Symbols chevron_right shape written by hand in the 960 grid (weight 400); the other icons are copied from the weight-500 files. I could not fetch the weight-500 file (no downloads), so the comment in the drawable says so. Swap it for the real file when convenient.
- The chevron is nudged 7 dp past the layout box so the glyph, not its 24 dp box, ends at the gutter like an SF Symbol.
- Row stacking at accessibility sizes uses `fontScale >= 1.3` (same stand-in as Results rows, `STACKED_ROW_FONT_SCALE`).
- `ScrollingScreen` gained an optional `topBar` slot for the Round page's `BackBar`.
- `RoundScreen` has a `zone` parameter (default the system zone) so the snapshot can pass UTC; the round page has no gear and shows no bar title, like iOS.
- Sections, zone and first weekday are computed when the state is published, not on every read like iOS's computed property; `refreshCalendar()` rebuilds them every time History is shown (review S2, below), so the stale case is only a History left on screen across a week boundary without leaving and returning.
- `StateFlow` equality: `RoundRecord` is equal by id, so `openedRound` replaces only when the id changes (fine here: a round is immutable).
- Observation, not changed: display texts have the phase-0 line-height gap (title to summary line looks a little looser than iOS).
- Process: while looking for the chevron source I started a `curl` to GitHub that hung and was killed; nothing was downloaded or kept.

## Snapshot comparison

Every new image was opened. Compared with the iOS reference of the same case: History `three-sections`, `one-round-fontScale2` (iOS `one-round-ax`); Round `2-missed`, `2-missed-fontScale2` (iOS `2-missed-ax`); `6-of-6` and the dark variants checked for clipping; the Android-only `nine-texts-fontScale2` and `last-year` opened. Differences, all expected: the back bar (iOS shows its navigation bar only when pushed), UTC times (D8), the chevron, and the larger gap under the display title. Fixes made after comparing: 20 dp extra above section headers; 24 dp minimum between a Round row's title column and its status (iOS `Spacer(minLength: 8)` between two 8 spacings); chevron offset. Nothing is clipped at 200% (the 9-dot row fits in `nine-texts-fontScale2`, which closes the gap the iOS plan notes at `Leo-Round-History-Plan.md:291`).

## Left undone

- Device pass (finish a round, relaunch, swipe, delete the last round, switch age group, TalkBack, 200%, landscape, "Don't keep activities", Android 14 first-day-of-week setting): no device connected (`adb devices` empty). Left for QA.
- `connectedDebugAndroidTest` not run for the same reason.

## Commands run

- `./gradlew assembleDebug` after each task: BUILD SUCCESSFUL.
- `./gradlew recordRoborazziDebug --tests '*RoundScreenSnapshotTests*'` and `'*HistoryScreenSnapshotTests*'` (re-recorded after the fixes above).
- Mutation checks, then reverted: `stickyHeader` replaced by `item` (1 test failed).
- `./gradlew :domain:test testDebugUnitTest` -> BUILD SUCCESSFUL, 633 tests (639 after the review fixes), 0 failures, 0 errors, 0 skipped (summed from the result XMLs, domain and app).
- `./gradlew detekt ktlintCheck lintDebug` -> BUILD SUCCESSFUL (fixes: `MagicNumber`, `UnusedPrivateProperty`, lint `NonObservableLocale` via `currentLocale()`, `PluralsCandidate` ignore on `history_row_accessibility` and `round_review_missed`).
- `./gradlew verifyRoborazziDebug` -> BUILD SUCCESSFUL (all references, earlier phases included).
- `./gradlew ktlintFormat` on changed files.

## Review fixes (round 1)

Changed: `features/history/{HistoryScreen,RoundHistoryViewModel,RoundScreen}.kt`, `features/root/HistoryDestinations.kt`, `MainActivity.kt`, `res/drawable/ic_chevron_right.xml`, tests `RoundHistoryViewModelTests` (+3: unconfirmed last delete, delete after the ViewModel is cleared, two `refreshCalendar` cases), `HistoryScreenTests` (`aFailedDeleteBringsTheRowBack` replaced by `aFailedDeleteBringsTheRowBackAtRestWithoutTryingAgain`, + `deletingTheLastRoundWaitsForTheStoreAndStaysIfItFails`, + `deletingTheLastRoundClosesHistoryOnceTheStoreHasDeletedIt`), `RoundHistoryRepositorySpy` (`deleteCalls`; the gate now comes before the failure, like a slow store that then fails), `HistoryScreenSnapshotTests` (now = 9:12), History and Round snapshots re-recorded and opened.

- **B1 done.** `SwipeToDismissBox(onDismiss = { onDelete(); scope.launch { state.snapTo(Settled) } })` replaces the effect. With the snap removed the new gated-failure test never finishes (the row deletes itself in a loop), so it covers the bug; it asserts two rows, the first displayed, "Delete" not shown, one delete call, nothing deleted.
- **S1 done.** `delete` publishes the optimistic list with `isLoaded = false`; the reload sets it back. History closes only when the store confirms an empty list, and stays (row back) when the delete fails. VM and screen tests added, plus the gated success case (History closes).
- **S2 done.** `refreshCalendar()` (a `publish { this }`) runs from `LifecycleResumeEffect` in `HistoryDestination`, so it covers opening History and returning to the app with History on screen. Tests: Sunday 22:00 round, clock moved to Monday 01:00, `LastWeek` with no extra load; zone and first weekday read again.
- **S3 done.** Swipe background `colors.magenta` with `colors.background` text (the theme's error pair, 4.61:1 light and 5.32:1 dark per the review). It is only visible while swiping, so no snapshot changes.
- **S4 done.** The constructor parameter is now `storeScope`; `delete` writes on it like `record`; `reload()` stays on `viewModelScope`. Test `deleteCompletesAfterTheViewModelIsCleared`.
- **N1 done:** the drawable comment now says it is the published wght400 path, reproduced, with the wght500 swap noted.
- **N2 done:** `onOpen` ignores taps unless the History entry is RESUMED (checked before `open`, not only before `navigate`).
- **N3 done:** stacked round rows align the number and the title with `alignByBaseline()`; the 200% snapshot was re-recorded and opened, unchanged to the eye.
- **N4 done:** the snapshot "now" is 2026-03-20 09:12 UTC like iOS; History snapshots re-recorded and opened ("9:12 AM").

Commands after the fixes: `./gradlew assembleDebug` BUILD SUCCESSFUL; `./gradlew :domain:test testDebugUnitTest` BUILD SUCCESSFUL, 639 tests, 0 failures, 0 errors, 0 skipped (summed from the result XMLs); `./gradlew detekt ktlintCheck lintDebug` BUILD SUCCESSFUL (one `MaxLineLength` in the spy fixed on the way); `./gradlew verifyRoborazziDebug` BUILD SUCCESSFUL; `./gradlew recordRoborazziDebug --tests '*HistoryScreenSnapshotTests*' --tests '*RoundScreenSnapshotTests*'` for the re-recorded images.

## Review fixes (PR #12 review)

- **Duplicate section key crash.** iOS keys sections by kind (`HistoryView.swift:22`, `ForEach(sections, id: \.kind)`), and a round dated after this week (a clock moved back) is "Earlier in <month>" above "This week", so the kind repeats. SwiftUI tolerates the duplicate; a lazy list throws `IllegalArgumentException`. The grouping is kept as on iOS (`aRoundDatedAfterThisWeekRepeatsEarlierThisMonth`); the sticky header key now carries the section index (`HistoryScreen.kt`). `HistoryScreenTests.aRoundDatedAfterThisWeekDoesntCrashTheList` hits the crash with the old key.
- **Overlapping deletes.** A reload that read the store while another delete was still pending (a second swipe, or the reload after a save) put that row back for a moment. `RoundHistoryViewModel` keeps `pendingDeletes` and filters them out of every load, which stays unconfirmed (`isLoaded = false`) until they settle. Tests `overlappingDeletesDontBringTheSecondRowBack` and `aSaveDuringADeleteDoesntBringTheRowBack` fail without the filter. `RoundHistoryRepositorySpy` gained per-id `deleteGates`; `testHistory` takes `now`.
- **Nits.** The "pop if this entry is on top" checks in `HistoryDestinations.kt` share `popIfOnTop`; the test helper `saveScope()` is renamed `storeScope()` (review N6).

## Review fixes (PR #12 second review)

- **A failed delete no longer reloads.** iOS logs a failed delete and doesn't reload (`RoundHistoryViewModel.swift:111-119`); the Android reload after it meant a store that also failed to read (load failure = empty list, as on iOS) emptied the list, closed History and hid its button. Now a failed delete puts its row back from memory and doesn't read the store; a successful one still reloads, and a failed load still shows no rounds (iOS parity, `loadFailureShowsNoRounds`). `delete` no longer cancels a running load: `pendingDeletes` already keeps the row out of it. Test `aFailedDeleteKeepsTheListWhenTheStoreCantBeReadEither` fails with the old reload.
- **12/24 hour clock re-read on resume.** It isn't part of the configuration, so a change recomposed nothing; `rememberIs24HourClock()` (`HistoryScreen.kt`) reads it again on every resume, for History's rows and the round kicker.
- **Not changed: a restored round page under its Review.** Only the top entry of a restored back stack is composed, so the round page loads (and pops when it can't) when Review leaves and it comes on top; it is never composed blank underneath. Not checked on a device.

## Review fixes (PR #12 third review)

- **No "0 rounds read · 0% answered right".** iOS only shows History with rounds, read at once; here the list can be empty for a moment (a restored route before its load, the last round's delete before the store answers, review N5). The summary line is shown only when there are rounds; the title stays, so History doesn't flicker. Tests `theSummaryWaitsForRounds`, `theSummaryLeavesWhileTheLastDeleteWaits`.
- **Section keys from their first round.** `"section-${section.rounds.first().id}"` instead of the index: still unique when a kind repeats, and a section's key no longer changes when a section above it is deleted.

## Review notes (PR #12 fourth review)

- **Not changed: `round_review_missed` stays a plain string.** The review asks for `<plurals>` because es/pt-BR read "Repasar 1 falladas" / "Revisar 1 erradas". iOS has the same single string with no plural variants (`Localizable.xcstrings`, "Review %lld missed"), and phase 6 decided plain strings for this case (phase-6 Q5, iOS parity). The user deferred it to phase 8 (es and pt-BR complete); noted in `docs/Leo-Android-Port-Plan.md`.
- **`delete` KDoc** now says that a read failing after a delete that worked shows no rounds and closes History, like any failed load, as on iOS.
- **`pendingDeletes`** is a plain set: it is only touched on the main thread.

## Review notes (PR #12 fifth review)

- **Restore order tied to the store's.** `RoundHistoryDao.summaries` and the failed-delete restore in `RoundHistoryViewModel.delete` now point at each other: both sort newest first by `finishedAt` alone.
- **Not changed: `sections` stays in the ViewModel file.** The review suggests moving `sections(...)` and `HistorySection` to `:domain`. iOS keeps both in `Features/History/RoundHistoryViewModel.swift`, not `Domain/`, and the port keeps iOS's layout so files compare side by side (CLAUDE.md, Architecture); the grouping is already covered by `RoundHistoryViewModelTests`.

## Review notes (PR #12 sixth review)

- **`onHistory` KDoc** on Welcome, Results and the quiz screen no longer says phase 6 always passes `null`.
- **Not changed: the first weekday reads `Locale.getDefault()`.** The date formats read the configuration's locale; today both are the device locale. The ViewModel can't read `LocalConfiguration`, and a per-app language would also reset the default locale. Locale handling is reviewed as a whole in phase 8 (phase 6 deferred the same question for list and timer formatting).

## Review notes (PR #12 seventh review)

- **`RoundHistoryUiState` is `@Immutable`**, so History can skip recomposing for an equal state (`ZoneId` is unstable to Compose).
- **Not changed: `openedRound` isn't cleared.** It is one round, replaced by the next tap; clearing it on History's resume could race the round page's exit transition, which still reads it, and make it load again.
- **Not changed: restore order.** Both sides already sort newest first by `finishedAt` and point at each other (fifth review). Rounds with the same `finishedAt` to the millisecond are not a realistic case for rounds a reader finishes one at a time.

## Review notes (PR #12 eighth review)

- **A second delete of a round already being deleted is ignored** (a swipe and the TalkBack action at once), so the first to finish can't let a load show the row while the other runs. Test `aSecondDeleteOfTheSameRoundWhileTheFirstWaitsIsIgnored`.
- **Not changed: load-then-navigate in the destination.** Navigation is the destination's job here as in phases 4-6 (`dropUnlessResumed`, RESUMED checks); the ViewModel only loads. A rotation mid-load drops that one tap (the reader taps again); a one-shot "opened" event in the ViewModel is a pattern the app doesn't use elsewhere. Revisit with the user if wanted.
- **Not changed, already answered:** `openedRound` isn't cleared and the restore order (seventh and fifth reviews above).
