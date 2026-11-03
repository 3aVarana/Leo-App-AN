# Phase 7: History, review

Reviewed against baseline `94ab7ca` (uncommitted work on `port/phase-7-history`), `plan.md` (with the user decisions Q1-Q5 of 2026-11-03) and `changes.md`. iOS spec: `Leo/Features/History/*` (4 files), `Root/RootView.swift`, `Root/RootViewModel.swift`, `App/MyApp.swift`, `LeoTests/Features/RoundHistoryViewModelTests.swift`, `RootViewModelTests.swift:136-176`, `LeoTests/Snapshots/{HistoryView,RoundView}SnapshotTests.swift` and their references in `LeoTestsSnapshots/`.

Commands run (read-only on this repo):
- `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug` -> BUILD SUCCESSFUL; 633 tests, 0 failures, 0 errors, 0 skipped (summed from the result XMLs, Roborazzi verify included).
- To check B1 and S1, I ran a probe in a **copy** of the repo in the session scratchpad, not in this tree: a Robolectric test through `RootScreen` with a repository whose `delete` suspends on a gate and then throws, the way Room's IO hop makes it suspend. Then I applied the fixes proposed below to the copy and ran the probe plus `features.history.*` and `RootScreenTests` again (63 tests, all pass). Nothing in this working tree was changed.

## What was checked and holds

- **ViewModel parity** (`RoundHistoryViewModel.kt` vs `.swift`): `hasRounds`, `roundCount` and `correctShare` (0 with no answers) are the same. Setting the same age group does nothing; null shows no rounds. `record` logs a save failure and keeps the list, and reloads only for the current age group. `round(id)` returns null on error and logs it. A load error gives an empty list and is logged. The log tag is `"RoundHistoryViewModel"`. Section kinds follow the same order (this week, then last week, then this month, then a month with `showsYear`), same-kind neighbours merge and empty sections are never made. `YearMonth` removes the zone slip that iOS's `monthName(calendar.timeZone)` guards against. Weeks use local midnights, so a DST week is tested.
- **Async model (D1)**: a reload is a cancel-and-replace job, and a late answer for another age group is dropped (`ensureActive` plus the age-group check). The save runs on `applicationScope` (`SupervisorJob + Main`), so `reload()` from it stays on the main thread, and it survives `store.clear()` (tested). `CancellationException` is rethrown by `attempt`.
- **Tests**: every iOS case is ported with the same data. `hasRoundsFollowsTheAgeGroup`, `ageGroupChangeReloads`, `totals` (7/9), `recordForAnotherAgeGroupDoesNotShow`, `deleteReloads` (including `round(id) == null`), `saveFailureKeepsTheList`, `sectionsGroupByWeekThenMonth` (same dates, January left out) and `lastWeekWinsOverThePreviousMonth` are present. `monthNameFollowsTheCalendarTimeZone` is now `theMonthFollowsTheTimeZone` plus `monthNameDoesNotDependOnTheDefaultTimeZone`, and `monthNameShowsTheYearOnlyWhenAsked` is in `HistoryFormattingTests`. Both `RootViewModelTests` cases are ported. The Android-only cases cover the gates, the save scope, the first weekday and DST.
- **Strings**: all 14 strings plus the plurals match `Localizable.xcstrings` verbatim in en, es and pt-BR, with placeholders in order (checked by script). Adding `many` to the es and pt plurals is correct for Android's CLDR. `history_row_action` is the Q4 change and `navigation_back` the Q2 one; both are listed in `changes.md`.
- **Root**: `onHistory` appears only when `hasRounds`, on Welcome and Results, and follows the current age group (`RootView.swift:99-102`). The configure effect moved above the `NavHost`, so a restored History gets its age group (tested). `MainActivity` wires `onRoundFinished = history::record`, the same as `MyApp.swift:21`.
- **Round page**: kicker, score heading, dots at 14, rows at 24, `Spacer(min 24)`. "Review N missed" is secondary and opens at 0, and there is no button without misses. Review gets "Back to round". A correct row is one node and a missed row is a button labelled "Review" that opens at its own miss.
- **Restored routes**: History, Round (valid, missing or invalid id) and Review (with no misses) each load or pop, and none leaves a blank screen. All are tested.
- **Architecture and rules**: no `androidx.room` or `com.google.mlkit` import under `features/`, no new dependency, no manifest change, no network code. Composables only lay out state (the tap-then-load lives in the destination, not in `HistoryScreen`). Tests use fakes, not mocks. `docs/learning/phase-7.md` is written.
- **Snapshots**: I opened them next to the iOS references: History `three-sections` against iOS `threeSections`, `nine-texts-fontScale2` (dark), and Round `2-missed-fontScale2`. Layout, spacing, kicker capitals, dots and chevron placement match, apart from the declared back bar and UTC times. Nothing is clipped at 200%.

## Declared deviations, judged

| Deviation | Verdict |
|---|---|
| Sections built when state is published, not on every read | **Not acceptable as declared.** The staleness is wider than "History left open across a week boundary". See S2. |
| Delete fired from `LaunchedEffect(state.currentValue)` instead of `confirmValueChange` | Moving off the deprecated `confirmValueChange` is right. But this M3 (1.4.0) has a non-deprecated `onDismiss` parameter for exactly this, and the effect as written re-fires on a restored row. See B1. |
| Hand-written `ic_chevron_right.xml` (weight 400) | **Acceptable for now.** The path `M504-480 320-664l56-56 240 240-240 240-56-56 184-184Z` is a closed, valid chevron (x 320-616, y 240-720 after the translate) and renders correctly in the snapshots, light and dark. It is the published wght400 `chevron_right_24px` path, and the comment is honest about weight and origin. See N1. |
| Swipe background: ground on `magenta600` | **Needs a different pair.** In light mode, ground on `magenta600` is 4.31:1, below AA for 16 sp semibold text. See S3. |

## Findings

### B1 (Blocking). A failed delete restores the row in its swiped state, which deletes it again, in a loop
`app/src/main/java/com/victorarana/leo/features/history/HistoryScreen.kt:193-196`, `features/history/RoundHistoryViewModel.kt:141-148`

iOS reference: `RoundHistoryViewModel.swift:111-119`. A failed delete is logged and the row stays where it was. Plan D1 promises the same end state ("on failure logs and reloads (iOS end state: row still there)").

`rememberSwipeToDismissBoxState()` is a `rememberSaveable`, and a `LazyColumn` keeps the saved state of an item whose key leaves the list. Here is what happens with a store whose `delete` suspends, as Room's does:
1. The swipe settles at `EndToStart`, `onDelete` runs, and the row leaves the list optimistically. Its state is saved as `EndToStart`.
2. The delete fails, and `reload()` brings the row back under the same key.
3. The row is restored at `EndToStart`: drawn swiped away, with the "Delete" background showing. `LaunchedEffect(state.currentValue)` starts with `EndToStart` and calls `onDelete()` again.
4. Each failure repeats from step 1. With a persistent error this never ends, and the row never comes back.

The probe confirmed it: two rounds, swipe the first, fail the delete. Afterwards there was still 1 row, and a second `delete` call was pending.

The existing `HistoryScreenTests.aFailedDeleteBringsTheRowBack` (`:281-290`) passes only because the spy throws synchronously. `viewModelScope` is `Main.immediate`, so delete, failure and reload all happen before a frame and the row never leaves composition. It also checks only the node count (`assertCountEquals(1)`), which is still 1 while the row sits offset off-screen.

Fix: put the swipe back at rest once the delete has been handed off. Use the M3 callback rather than a hand-rolled effect:
```kotlin
val scope = rememberCoroutineScope()
SwipeToDismissBox(
    state = state,
    enableDismissFromStartToEnd = false,
    onDismiss = {
        onDelete()
        scope.launch { state.snapTo(SwipeToDismissBoxValue.Settled) }
    },
    ...
)
```
(or keep the effect and add `state.snapTo(SwipeToDismissBoxValue.Settled)` right after `onDelete()`; I verified that variant in the probe copy: the row comes back at rest, one delete call, and all 21 `HistoryScreenTests` still pass. On success the row is already gone by the next frame, so nothing flashes.)

Also rewrite the test so it exercises the real path. Give the spy a `delete` that suspends on a gate and then throws. Swipe, `waitForIdle`, complete the gate, `waitForIdle`. Then assert 2 rows, the restored row `assertIsDisplayed()` at its normal position, and exactly one delete attempt. Update `changes.md` D4: the reason given for the effect ("`confirmValueChange` is deprecated") stays, but say that `onDismiss` is used.

### S1 (Should fix, parity). Deleting the last round closes History before the store answers, even when the delete then fails
`features/history/RoundHistoryViewModel.kt:143`, `features/history/HistoryScreen.kt:103-105`

iOS reference: `HistoryView.swift:56-61` dismisses on `hasRounds` turning false, which on iOS only happens after a successful synchronous delete. Here the optimistic publish keeps `isLoaded = true` with an empty list, so `onEmpty` pops at once. The probe showed it: with a gated, failing delete, History was gone right after the swipe, and once the delete failed, Home showed the History button again. The reader is thrown out of History for a round that is still there.

Fix: mark the optimistic list as not yet confirmed. Use `publish { copy(summaries = summaries.filterNot { it.id == id }, isLoaded = false) }`, and let the reload that follows set `isLoaded = true`. Then `onEmpty` fires only when the store says the list is empty. I verified this in the probe copy: History stays during the pending delete and after a failure, it still pops after a successful delete of the last round, and all `RoundHistoryViewModelTests`, `HistoryScreenTests` and `RootScreenTests` pass. Add the gated last-row case to `HistoryScreenTests`.

### S2 (Should fix, parity). Sections, zone and first weekday go stale until the next load, not only while History is open
`features/history/RoundHistoryViewModel.kt:168-174`

iOS reference: `RoundHistoryViewModel.swift:68-79,135-137`. `sections` is computed and `now()` is read each time `HistoryView`'s body is evaluated, and a newly pushed `HistoryView` always evaluates it. So on iOS, every opening of History is correct.

Here sections, `zone` and `firstDayOfWeek` are captured at the last `publish` (a load, record, delete or age-group change), and the ViewModel lives in the Activity's store. If a round is finished on Sunday and the app stays in memory, opening History on Monday without finishing a round still shows that round under "This week". The same happens after a time-zone change (the rows' times also use the old `state.zone`) and after a change of the first-day-of-week setting (the Activity is recreated but the ViewModel and its state survive). `changes.md` describes only the narrower "left open across midnight" case.

Fix: rebuild the derived fields whenever History becomes visible. For example, add `fun refreshCalendar() = publish { this }` (publish already re-reads `now`, `zone` and `firstDayOfWeek`) and call it from `HistoryDestination` in a `LifecycleResumeEffect(Unit) { history.refreshCalendar(); onPauseOrDispose {} }`. That also covers returning to the app while History is on screen. Add a VM test: record on Sunday 23:00, move `now` to Monday 01:00, refresh, and the kind becomes `LastWeek`. Then update `changes.md`.

### S3 (Should fix, accessibility). The swipe's "Delete" label fails AA contrast in light mode
`features/history/HistoryScreen.kt:204-216`

Ground `#F3F2F2` on `magenta600` `#D82071` is **4.31:1**. "Delete" is 16 sp semibold, which is not large text, so AA needs 4.5:1. Dark mode is fine at 8.1:1. White would be 4.8:1 in light mode but 2.1:1 on dark `magenta600`, so the implementer was right not to use white.

Fix: use the theme's own error pair, `colors.magenta` with `colors.background` (`Theme.kt:251-252`, `error = magenta`, `onError = background`). That gives 4.61:1 in light and 5.32:1 in dark. iOS uses the system destructive red, which this design can't use, so note the color choice in `changes.md` D4.

### S4 (Should fix). The delete runs on `viewModelScope`, so finishing the Activity can drop it
`features/history/RoundHistoryViewModel.kt:144-147`

iOS deletes synchronously, so a delete always completes. Here the row is removed from the UI at once, but `repository.delete` runs on `viewModelScope`. If the reader swipes the last round (History pops) and then presses back to leave the app straight away, clearing the ViewModel can cancel the delete before Room's IO block starts. The round would then reappear on the next launch. The window is small, but the save already has the fix (phase-6 D6).

Fix: launch the delete on `saveScope`, renamed to something like `storeScope` with its KDoc updated. Keep the `reload()` afterwards, which uses `viewModelScope` and is a no-op once the ViewModel is cleared. Add a test like `recordCompletesAfterTheViewModelIsCleared` for delete.

### N1 (Nit). Chevron weight and provenance
`app/src/main/res/drawable/ic_chevron_right.xml:2-5`

The other icons are weight 500, and this one is 400, a slightly thinner stroke. When a download is possible, swap in `symbols/web/chevron_right/materialsymbolsoutlined/chevron_right_wght500_24px.svg` and use the same comment form as `ic_add.xml`. Until then, reword the comment to "path of the published wght400 `chevron_right_24px.svg`, reproduced, not copied from the file". The path matches that file exactly, so "written by hand" undersells where it comes from.

### N2 (Nit). Tap during a push replaces `openedRound` under the page that is entering
`features/root/HistoryDestinations.kt:96-109,194-204`

While Round A slides in, History stays composed (`KeepUntilTransitionsFinished`) and its rows still take taps. A tap on row B runs `history.open(B)` before the RESUMED check, so `openedRound` becomes B. Round A's `rememberRound` then sees no match, draws the blank `Box` for a frame and loads A again. Fix: check `entry.lifecycle.currentState.isAtLeast(RESUMED)` before `open`, not only before `navigate`, or wrap `onOpen` with `dropUnlessResumed`.

### N3 (Nit, parity). Stacked round rows: number and title on the first baseline
`features/history/RoundScreen.kt:203-206`

iOS (`RoundView.swift:90`) uses `HStack(alignment: .firstTextBaseline, spacing: 8)`. The Kotlin `Row` is top-aligned. In `2-missed-fontScale2` it looks level, because the two styles happen to have close ascents. Use `Modifier.alignByBaseline()` on both texts so it stays right if `answerMarker` changes.

### N4 (Nit, test parity). Snapshot "now" differs from the iOS fixture
`app/src/test/java/com/victorarana/leo/snapshots/HistoryScreenSnapshotTests.kt:44`

iOS uses `date(2026, 3, 20)` with the default 09:12 (`HistoryViewSnapshotTests.swift:16,35`). Android uses 00:00, so the first row reads "12:00 AM" where the same UTC setup would read "9:12 AM". Use `date(2026, 3, 20)`. Sections don't change. Re-record and open the images.

## Verdict

**Changes needed.** 1 Blocking (B1), 4 Should fix (S1-S4), 4 Nits. B1 and S1 are small. Both fixes were checked against the full history and root suites in a scratch copy, together with gated-delete screen tests that reproduce them. After the fixes, re-run `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`, re-record any snapshot touched by S3/N4 and open it, and update `changes.md` (D4 `onDismiss`, swipe colors, the real extent of the stale sections, and the delete scope).

Still open for the user (device needed, as listed in `changes.md`): finish a round, then History; relaunch; swipe, including a full swipe on a real Room store; delete the last round; age-group switch; TalkBack "Delete" action; 200%; landscape; "Don't keep activities" restore of History, Round and Review; the Android 14 first-day-of-week setting; `connectedDebugAndroidTest`.

## Round 2

I checked the "Review fixes (round 1)" section of `changes.md` against the working tree. I diffed every changed file against the scratchpad copy taken in round 1. No source in this tree was edited.

Commands run:
- `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug` -> BUILD SUCCESSFUL. That is 639 tests, 0 failures, 0 errors, 0 skipped (summed from the result XMLs, Roborazzi verify included).
- I refreshed the scratchpad copy from the working tree and ran the round-1 probe again: a repository whose `delete` suspends on a gate and then throws, through `RootScreen`. I added two more cases: a second swipe after a failure, and what History draws while the last delete is pending.

| Finding | Status |
|---|---|
| B1 failed delete re-fires in a loop | **Fixed.** `HistoryScreen.kt:193-206` uses M3's `onDismiss`, and `snapTo(Settled)` runs on the composition scope. Probe: two rows, swipe, failure. Afterwards there are 2 rows, both at x = 0 (at rest), and there was 1 delete call. Swiping the restored row again makes exactly one more call, so the gesture still works after a failure. The new `aFailedDeleteBringsTheRowBackAtRestWithoutTryingAgain` gates and then fails like Room. It asserts the row count, the row is displayed, "Delete" is hidden and `deleteCalls == 1`. Per `changes.md`, the test hangs if the snap is removed, so it guards the bug. |
| S1 last delete closes History early | **Fixed.** `RoundHistoryViewModel.kt:145` publishes `isLoaded = false` and the reload restores it. Probe: during the pending delete History stays; after a failure the row is back and History is still shown. `deletingTheLastRoundClosesHistoryOnceTheStoreHasDeletedIt` covers the success path, and `deletingTheLastRoundIsNotLoadedUntilTheStoreAnswers` covers the VM. |
| S2 stale sections, zone, first weekday | **Fixed.** `refreshCalendar()` is `publish { this }`, so it re-reads `now`, `zone` and `firstDayOfWeek` without a store call (tested via `summariesCalls`). It runs from `LifecycleResumeEffect(history)` in `HistoryDestination` (`HistoryDestinations.kt:95-99`), which covers opening History, popping back from a round, and returning to the app. If nothing changed, the `StateFlow` sees an equal value and emits nothing. |
| S3 swipe contrast | **Fixed.** `colors.magenta` behind `colors.background` text, the theme's error/onError pair (`HistoryScreen.kt:209-214`). |
| S4 delete on `viewModelScope` | **Fixed.** `storeScope` (renamed in `MainActivity`) runs both writes, and `reload()` stays on `viewModelScope`, which is a no-op once the ViewModel is cleared. `deleteCompletesAfterTheViewModelIsCleared` starts the delete, clears the store while it is gated, then completes it. That would fail on `viewModelScope`. |
| N1 chevron comment | **Fixed.** The comment now matches the file's real origin and notes the wght500 swap. |
| N2 tap during push | **Fixed.** The RESUMED check now comes before `open` (`HistoryDestinations.kt:105`). A tap while History itself is still entering is dropped too, which matches `dropUnlessResumed` everywhere else. |
| N3 baseline | **Fixed.** `alignByBaseline()` on the number and the title in the stacked `Row` only (`RoundScreen.kt:204-208`). The side-by-side row keeps `Alignment.Top`, as iOS does. I opened `2-missed-fontScale2`: every number sits on its title's first line, including wrapped titles 3, 5 and 6. Nothing is clipped. |
| N4 snapshot "now" | **Fixed.** 09:12 UTC, the same as iOS. Re-recorded, and `verifyRoborazziDebug` passes as part of `testDebugUnitTest`. |

Regression check on the new code:
- **`LifecycleResumeEffect` and `refreshCalendar`**: they don't touch `summaries`, `isLoaded` or `openedRound`. If a refresh runs during a pending delete, `isLoaded` stays false, so it can't make History pop early. Nothing found.
- **`delete` on `storeScope` with `isLoaded = false`**: a reload started by something else (a `record` finishing) can still bring the row back briefly before the delete's own reload. The row now returns at rest and doesn't re-fire, so this is harmless. Nothing found.
- **`onDismiss`** is driven by `LaunchedEffect(settledValue, onDismiss)` inside M3. The probe shows one call per swipe, so lambda identity doesn't cause re-runs.
- **Spy**: the gate now comes before the failure, and `deleteCalls` is new. Earlier tests that set only `deleteError` still fail at once, and every suite passes.

### N5 (Nit, new). History shows "0 rounds read · 0% answered right" while the last delete is pending
`features/history/HistoryScreen.kt:141-153`

This is a side effect of the S1 fix. The optimistic list is empty but not yet confirmed, so the header draws the plural "other" form with 0 until the store answers and History pops. The probe asserts the text is displayed. On a device this lasts as long as the Room write, usually a frame or two, and iOS never shows it. A possible fix is to keep the header's last confirmed totals while `!isLoaded`, or to draw the header only when `hasRounds`. It's fine to leave as is; if so, note it in `changes.md`.

### N6 (Nit, new). Test helper name
`RoundHistoryViewModelTests.kt:71`

`saveScope()` now builds the `storeScope`. Rename it so it matches.

### Verdict (round 2)

**Approve.** 0 Blocking, 0 Should fix, 2 new Nits (N5, N6), both optional. All round-1 findings are resolved.

Still open for the user (device needed, as listed in `changes.md`): a full swipe on a real Room store, deleting the last round, TalkBack's "Delete" action, 200%, landscape, "Don't keep activities" restores, the Android 14 first-day-of-week setting, a time-zone change with History open and back, and `connectedDebugAndroidTest`.
