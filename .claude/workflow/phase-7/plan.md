# Phase 7: History, plan

Baseline `94ab7ca` (phases 0-6 merged). iOS spec: `Leo/Features/History/*` (4 files), `Root/RootView.swift:6,29-31,56,79,99-102`, `Root/RootViewModel.swift:10,47-51`, `App/MyApp.swift:12-24`, `docs/Leo-Round-History-Plan.md` (H1-H8, 1.2, 5.2-5.5); tests `LeoTests/Features/RoundHistoryViewModelTests.swift`, `RootViewModelTests.swift:136-176`, `LeoTests/Snapshots/{HistoryView,RoundView}SnapshotTests.swift`, references in `LeoTestsSnapshots/{HistoryView,RoundView}SnapshotTests/`. Reused (do not duplicate): `:domain` `RoundRecord`, `RoundSummary`, `RoundAnswer`, fixtures `roundRecordFixture(ageGroup, finishedAt, results)`, `roundAnswers`; `RoundHistoryRepository` (suspend) + `RoomRoundHistoryRepository` (IO dispatcher inside), `support/RoundHistoryRepositorySpy`; components `RoundResultDots`, `HistoryButton`, `Kicker`, `ReviewLabel`, `LeoButton`/`LeoButtonKind`, `ScrollingScreen`/`LeoBottomBar`, `LeoIcon`, `displayNameRes`; `preferences/Headline`; `quiz/ReviewScreen(misses, startIndex, onBack, backTitle, parentTitle)`; `STACKED_ROW_FONT_SCALE`; `AppContainer.history`, `.applicationScope`; `WelcomeScreen`/`ResultScreen`/`QuizScreen` `onHistory: (() -> Unit)?`; `assertViewSnapshot`; `support/WaitUntil.kt`, `MainDispatcherRule`.

## File map (`app/src/main/java/com/victorarana/leo/`, package `features.history` unless noted)

| iOS | Kotlin |
|---|---|
| `RoundHistoryViewModel.swift` (`HistorySection`, `RoundHistoryViewModel`) | `RoundHistoryViewModel.kt`: `RoundHistoryViewModel : ViewModel`, `RoundHistoryUiState`, `HistorySection` + sealed `HistorySection.Kind`, companion `internal fun sections(summaries, now, zone, firstDayOfWeek)` (D1-D3) |
| `HistoryView.swift` (`HistoryView`, `HistoryRow`) | `HistoryScreen.kt` (`HistoryScreen`, private `HistoryRow`, private `SwipeToDelete`) (D4, D5) |
| `RoundView.swift` (`RoundView`, `RoundResultRow`) | `RoundScreen.kt` (`RoundScreen(round, onReview, onBack)`, private `RoundResultRow`) |
| `monthName` + the views' `Date.FormatStyle`s | `HistoryFormatting.kt`: `monthName(YearMonth, showingYear, locale)`, `rowDate`, `rowTime`, `accessibilityDay`, `roundKicker` (ICU skeletons, D3) |
| `RoundRecord+PreviewFixture.swift` | not ported: no `@Preview`s in this repo; tests use `roundRecordFixture` |
| `RootView.swift:29-31,99-102` | `features/root/LeoNavHost.kt`: `LeoRoute.History`, `.HistoryRound(id)`, `.HistoryReview(roundId, startIndex)` destinations; `onHistory` only when `hasRounds`; configure effect moved above `NavHost` (D6) |
| `RootViewModel.swift:10,50` | `features/root/RootViewModel.kt`: ctor `history: RoundHistoryViewModel`; `preferencesDidChange()` sets `history.setAgeGroup(...)` |
| `MyApp.swift:12-22` | `MainActivity.kt`: `history` in the Activity store (key "history"), `quiz.onRoundFinished = { history.record(it) }`; `AppContainer.kt` drops `roundHistoryRecorder` |
| (Android only) | `features/components/BackBar.kt`: `ReviewScreen`'s private `TopBar` extracted (arrow + parent title, optional trailing text), used by Review, History and Round (Q2) |
| SF Symbol `chevron.right` | `res/drawable/ic_chevron_right.xml` (Material Symbols, provenance as phase 4) |
| (delete) | `data/repositories/RoundHistoryRecorder.kt` + its tests (folded into `record`, phase-6 D6); `features/root/Placeholders.kt` (`HistoryPlaceholder`) |

## Behavior to preserve

ViewModel (`RoundHistoryViewModel.swift`)
- `ageGroup` null before onboarding; setting a different value reloads, the same value does nothing (`:24-29`); null -> no summaries (`:122-125`).
- `hasRounds = summaries non-empty` (`:49-51`); `roundCount` (`:53-55`); `correctShare` = correct / all answers across rounds, 0 with none (`:58-63`).
- `record`: save; on error log and stop (list unchanged); reload only when `round.ageGroup == ageGroup` (`:89-99`). `round(id)` null on error, logged (`:102-109`). `delete`: on error log, no reload; else reload (`:111-119`). Load error -> empty list, logged (`:128-131`). Log tag `"RoundHistoryViewModel"`.
- Sections (`:68-79,135-152`): consecutive summaries (newest first) with the same kind share a section; empty sections never appear. Kind: in this week's interval -> ThisWeek; else in the week before -> LastWeek; else in this month -> EarlierThisMonth(month); else Month(month of date, showsYear = its year != today's year). Weeks follow the locale's first weekday; LastWeek wins over EarlierThisMonth and over an older month (`RoundHistoryViewModelTests.swift:143-155`).
- `monthName`: wide month, plus year only when asked, in the calendar's zone (`:83-86`).
History screen (`HistoryView.swift`)
- Header: "Your rounds" display 40, heading; summary bodyLarge inkSoft, spacing 8, top 20, bottom 28, readable width (`:64-76`); summary "%d rounds read · %@ answered right", plural one/other, percent with 0 decimals (`:78-81`, xcstrings 100).
- Section title: `Kicker`, heading (List section header), top 20 except the first, bottom 8 (`:83-90`); "This week", "Last week", "Earlier in %@" (month name), else month name verbatim (`:92-103`).
- Row (`:108-164`): one button, vertical padding 12; date (day + abbreviated month, 17 semibold ink) over time (hour:minute, bodySmall inkSoft), spacing 2; Spacer min 8; score "%d of %d" (17 semibold) over `RoundResultDots`, trailing, spacing 6; `chevron.right` small inkSoft; spacing 12. At accessibility sizes stacked: date/time, then score/dots, spacing 10, no chevron. TalkBack: one node "%@, %@, %d of %d right" (day + wide month, time), hint "Shows the round".
- Swipe to delete, no confirmation, "Delete" (`:34-40`, H3). Deleting the last round closes History (`:56-61`). Tap loads the round first; a round that can't be loaded isn't opened (`:26`, plan 5.3 update).
- No gear: the age group can't change while History or a round is shown (`:6-8`).
Round screen (`RoundView.swift`)
- Kicker "%@ · readers %@" (day + abbreviated month + time, `ageGroup.displayName`), top 28 (`:13-14,45-48`); score "%d of %d" `.score` heading (`:15-17`); dots top 14 (`:18-19`); rows top 24, `Spacer(min 24)` (`:20-26`).
- Bottom bar "Review %d missed", secondary, opens Review at 0; absent with no misses (`:28-37`). Review gets `backTitle` "Back to round" (`:40-42`).
- Rows (`:81-142`): vertical padding 10; number `answerMarker` min width 16; title 17/24 over "topic · skill" caption inkSoft (spacing 2), layout priority; Spacer min 8; status "Correct" 14 semibold accent700 or `ReviewLabel`; no dotted leader. Stacked at accessibility sizes: number + title on the first baseline (spacing 8), subtitle, status (spacing 6).
- TalkBack: correct row one node "Text %d, %@, %@, correct"; missed row a button "Text %d, %@, %@, missed", hint "Review", opens Review at that miss (`:51-72`).
Root
- History button shown only when `hasRounds`, on Welcome and Results, variant from the current preferences' age group (`RootView.swift:56,79-80,99-102`); Results after an age-group change keeps the old round but the button follows the new group (plan 1.2).
- `preferencesDidChange` sets `history.ageGroup` (`RootViewModel.swift:50`); finished rounds reach history through `onRoundFinished` (`MyApp.swift:21`).

## Android decisions

- **D1 Ownership and async.** (a, recommended) `RoundHistoryViewModel : ViewModel` in the Activity store (like quiz, phase-6 D1), passed to `RootViewModel`. Ctor `(repository, saveScope: CoroutineScope, now: () -> Instant = Instant::now, zone: () -> ZoneId = ZoneId::systemDefault, firstDayOfWeek: () -> DayOfWeek = ::localeFirstDayOfWeek)`; providers stand for `.autoupdatingCurrent`. `StateFlow<RoundHistoryUiState(ageGroup, summaries, isLoaded, sections, zone)>` with `hasRounds`, `roundCount`, `correctShare` as members. Reload = cancel-and-replace `Job` on `viewModelScope`, result dropped if `ageGroup` changed meanwhile. `record` launches `save` on `saveScope` (`AppContainer.applicationScope`), so a save started at "See results" survives the Activity finishing (phase-6 D6), then reloads if the age group matches. `suspend fun round(id): RoundRecord?`. `delete(id)` removes the row from `summaries` at once (the swiped row must not bounce back), then deletes; on failure logs and reloads (iOS end state: row still there).
- **D2 Section kinds.** iOS keys months by a `Date` (month start) and needs `monthName` to use the calendar's zone. Recommended: `Kind.EarlierThisMonth(month: YearMonth)` / `Kind.Month(month: YearMonth, showsYear)`, computed with `java.time` in `zone`: week start = `today.with(previousOrSame(firstDayOfWeek))` at start of day, intervals via `ZonedDateTime.plusWeeks` (DST-safe). `YearMonth` removes the zone bug by construction; the zone test is kept against the boundary.
- **D3 Formatting.** ICU `android.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale)` with the zone set, mirroring Apple's skeleton-based `Date.FormatStyle`: row date "MMMd", accessibility day "MMMMd", month "LLLL" / "LLLLy", kicker "MMMd" + time; time uses `android.text.format.DateFormat.is24HourFormat` -> "Hm"/"hm" (iOS follows the device 12/24h setting). Percent: `NumberFormat.getPercentInstance(locale)`, max 0 fraction digits (HALF_EVEN, as Foundation). en may print "Dec 31, 8:00 PM" where iOS prints "Dec 31 at 8:00 PM": CLDR version difference, accepted and noted. First weekday: `android.icu.util.Calendar.getInstance(locale).firstDayOfWeek` (honors Android 14's "First day of week" regional setting; check on device).
- **D4 Swipe to delete.** iOS `.swipeActions` (partial swipe reveals "Delete", full swipe deletes). (a, recommended) Material 3 `SwipeToDismissBox`, end-to-start only, background magenta600 with "Delete" in white at the trailing edge, deletes on dismiss; plus a TalkBack custom action "Delete" on the row (VoiceOver exposes swipe actions the same way). (b) a hand-built `anchoredDraggable` reveal with a tappable button: closer to iOS, more code. Q1.
- **D5 List.** `LazyColumn` with `key = id`, header item, then per section a header (`stickyHeader` with the ground behind it, Q3) and rows inside `leoReadableWidth()`; edge to edge like `ScrollingScreen`. `isLoaded && !hasRounds` pops History (`LaunchedEffect`), so a restored route with nothing to show also pops instead of drawing an empty list.
- **D6 Navigation.** `LeoRoute.HistoryRound(id: String)` (UUID as String) and `LeoRoute.HistoryReview(roundId: String, startIndex: Int)`. Tap: `rememberCoroutineScope` launches `history.open(id)` (= `round(id)`, kept as `openedRound` in state), then navigates only if non-null and the entry is still RESUMED; taps while a load runs are ignored. Round and its Review read `openedRound`; if it doesn't match the route id (process death) they load it by id and pop when null. Push/pop slide like Review, none under reduced motion; History, HistoryRound, HistoryReview join `isOverHome()`. `LaunchedEffect(preferences) { root.preferencesDidChange() }` moves from the Home destination to the top of `LeoNavHost`, so a restored History still gets its age group (Home isn't composed under it).
- **D7 TalkBack.** Rows `clearAndSetSemantics { contentDescription; role = Button; onClick(label = …) }` like `ResultRowItem`; the hint as action label (Q4). Section titles and the header `heading()`. Dots already hidden.
- **D8 Snapshot time zone.** Rows on iOS use the device zone while sections use the test calendar (UTC); the references show the simulator's zone (e.g. "5:12 AM", "Dec 31 at 8:00 PM"). Here rows and kicker format in `uiState.zone`, so snapshot tests pass `ZoneOffset.UTC` and get "9:12 AM" / "Jan 1": deterministic, times differ from the iOS images on purpose.

## Strings (en/es/pt-BR from `Leo/Localizable.xcstrings`, line given)
`history_title` 3153, `history_summary` `<plurals>` one/other 100 (`%1$d`, `%2$s`), `history_this_week` 2641, `history_last_week` 1393, `history_earlier_in` 1025, `history_delete` 913, `history_row_accessibility` 52, `history_row_hint` 2081 (Q4), `round_kicker` 36, `round_topic_skill` 20, `round_review_missed` 1921 (plain string, phase-6 Q5 precedent, `PluralsCandidate` ignore), `round_row_correct` 2353, `round_row_missed` 2369. Reuse `result_score`, `result_correct`, `review_label`, `history_button_label`, `review_back_to_round` (drop its `UnusedResources` ignore and comment). Possibly `navigation_back` (Q2).

## Tests to port (`app/src/test/java/com/victorarana/leo/`)
- `features/history/RoundHistoryViewModelTests` (Robolectric for `Log`, `StandardTestDispatcher` Main, UTC + Monday + fixed `now`, `advanceUntilIdle` after each call): `hasRoundsFollowsTheAgeGroup`, `ageGroupChangeReloads`, `totals` (7/9), `recordForAnotherAgeGroupDoesNotShow`, `deleteReloads`, `saveFailureKeepsTheList`, `sectionsGroupByWeekThenMonth`, `lastWeekWinsOverThePreviousMonth`, `monthNameFollowsTheCalendarTimeZone` (Feb 1 00:00 in UTC-5 is still February), `monthNameShowsTheYearOnlyWhenAsked`. Android-only: `savesTheRoundOnce`, `aFailedSaveIsLoggedNotThrown` (from `RoundHistoryRecorderTests`), `recordCompletesAfterTheViewModelIsCleared`, `lateReloadForAPreviousAgeGroupIsDropped`, `loadFailureShowsNoRounds`, `deleteFailureRestoresTheRound`, `deleteRemovesTheRowBeforeTheStoreAnswers`, `sectionsFollowTheFirstDayOfWeek` (Sunday vs Monday), `roundReturnsNullOnError`, `settingTheSameAgeGroupDoesNotReload`.
- `features/history/HistoryFormattingTests`: month names en/es/pt-BR, year only when asked, percent rounding (0.785 -> "78%"), 24h vs 12h time.
- `features/root/RootViewModelTests`: `preferencesDidChangeSetsHistoryAgeGroup`, `finishedRoundReachesHistory` (quiz -> `history.record` -> `roundCount == 1`); `makeRoot` gains `history`.
- `features/history/HistoryScreenTests` (Android-only): tap row -> Round; missed row -> Review page -> "Back to round" returns to Round; swipe end-to-start deletes (`performTouchInput { swipeLeft() }`); TalkBack "Delete" action deletes; deleting the last round pops to Home and hides the button; a round deleted before it loads isn't opened.
- `features/root/RootScreenTests`: restore `homeNavigatesToHistoryAndBackPops`; `historyButtonHiddenWithoutRounds`. `QuizScreenTests`: Results shows History after the round is saved.
- Snapshots (`snapshots/`, light + dark, `-ax` -> `-fontScale2`, compare with iOS images): `HistoryScreenSnapshotTests` three-sections (h1700), one-round, one-round-fontScale2 (h1100); `RoundScreenSnapshotTests` 2-missed (h1700), 6-of-6 (h1500), 2-missed-fontScale2 (h2700). Android-only: `HistoryScreenSnapshotTests` nine-texts-fontScale2 (the 9-dot row at 200%, a gap the iOS plan notes, `Leo-Round-History-Plan.md:291`).

## Open questions for the user
1. Swipe to delete: `SwipeToDismissBox` full swipe deletes at once, with a TalkBack "Delete" action (recommended), or a hand-built partial swipe that reveals a "Delete" button like iOS?
2. Back bars on History and Round: iOS shows the system back button; Welcome and History set empty titles, so iOS reads "Back". Recommend arrow + "Back" (new `navigation_back`: "Back"/"Atrás"/"Voltar") on History, Round and Review-from-round, from a shared `BackBar` extracted from `ReviewScreen`; or arrow alone with content description "Back".
3. Section headers: iOS's plain `List` pins section headers while scrolling (please confirm on the simulator). Use `stickyHeader` with the ground behind (recommended if iOS pins), or plain scrolling headers?
4. Row action label: TalkBack reads "Double-tap to <label>"; iOS's hint "Shows the round" would read "Double-tap to Shows the round". Use a new label "Show round" ("Ver la ronda"/"Ver a rodada", recommended), or reuse the iOS string verbatim?
5. Snapshot times in UTC (D8, recommended: deterministic, clock times differ from iOS references), or pin Robolectric's default zone to the simulator's to match the iOS images?

## Acceptance criteria
1. Finishing a round saves it (also when the Activity finishes right after); the History button then appears on Results and Welcome; hidden while the current age group has no rounds.
2. History shows header, summary (singular for one round), and sections This week / Last week / Earlier in <month> / <month> [year], empty ones left out, weeks starting on the locale's first day.
3. Rows match "Behavior to preserve" at 1x and stacked at 200%; tapping opens the round; a missed row and "Review N missed" open Review, whose last button is "Back to round".
4. Swipe (and the TalkBack action) deletes without confirmation; deleting the last round returns to the previous screen with the button hidden.
5. Changing the age group in Settings switches list and totals; switching back restores them.
6. A restored History/Round/Review route after process death shows the right content or pops; never a blank screen (device check with "Don't keep activities", closes phase-4 N10).
7. All ported and Android-only tests pass; snapshot suites recorded, every image opened and compared with iOS, nothing clipped at 200%.
8. Strings in `values/`, `values-es/`, `values-pt-rBR/`; `HistoryPlaceholder` and `RoundHistoryRecorder` deleted; no Room/ML Kit import under `features/`; `./gradlew assembleDebug`, `./gradlew :domain:test testDebugUnitTest`, `./gradlew detekt ktlintCheck lintDebug` pass.
9. `changes.md` lists D1-D8 and the answers to Q1-Q5; `docs/learning/phase-7.md` written.

## Tasks
1. Strings (3 locales), `ic_chevron_right`, `HistoryFormatting.kt` + `HistoryFormattingTests`. Build.
2. `RoundHistoryViewModel` (+ state, sections) and its tests; delete `RoundHistoryRecorder` (+ tests), wire `MainActivity`/`AppContainer`; `RootViewModel(history)` + 2 tests. Tests.
3. `BackBar` extracted from `ReviewScreen` (re-run `ReviewScreenSnapshotTests` unchanged unless Q2 changes the label). Tests.
4. `RoundScreen` + `RoundScreenSnapshotTests`. Record, open, verify.
5. `HistoryScreen` (list, sections, rows, swipe, TalkBack action, pop on empty) + `HistoryScreenSnapshotTests`. Record, open, verify.
6. `LeoRoute` + `LeoNavHost` (History, HistoryRound, HistoryReview, `onHistory` from `hasRounds`, configure effect hoisted), delete `Placeholders.kt`; `HistoryScreenTests`, `RootScreenTests`, `QuizScreenTests` updates. Tests.
7. Full build, unit tests, lint; device pass if connected (finish round -> History, relaunch, swipe, delete last, age-group switch, TalkBack, 200%, landscape, "Don't keep activities"); `changes.md`; `docs/learning/phase-7.md` (`SwipeToDismissBox` vs `swipeActions`, `LazyColumn` sections/`stickyHeader` vs `List`, `java.time` weeks vs `Calendar`, loading before navigating with type-safe routes, application-scope saves).

## Decisions (user, 2026-11-03)
- Q1 Swipe to delete: (a) Material 3 `SwipeToDismissBox`, end-to-start only, full swipe deletes, plus a TalkBack "Delete" custom action.
- Q2 Back bars: arrow + "Back" (new `navigation_back`: "Back"/"Atrás"/"Voltar") on History, Round and Review-from-round, from a shared `BackBar` extracted from `ReviewScreen`.
- Q3 Section headers: pinned. iOS uses `.listStyle(.plain)` (`HistoryView.swift:48`), whose section headers stick while scrolling; use `stickyHeader` with the ground behind.
- Q4 Row action label: new label "Show round" / "Ver la ronda" / "Ver a rodada" (Android change, listed in `changes.md`).
- Q5 Snapshot time zone: UTC (D8); clock times differ from the iOS reference images on purpose.
