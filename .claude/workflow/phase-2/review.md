# Phase 2: Storage, review

Reviewed: working tree on `port/phase-2-storage` against `a784dc6` (`baseline.sha`), with `plan.md` (Decisions of 2026-10-30) and `changes.md`. iOS originals: `Leo/Data/Repositories/PreferencesRepository.swift`, `RoundHistoryRepository.swift`, `Leo/Data/SwiftData/RoundHistorySchema.swift`, `LeoTests/Data/*`, `LeoTests/Support/{PreferencesRepositorySpy,RoundHistoryRepositorySpy,TestDefaults}.swift`.

Checks run by the reviewer:

- `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`: exit 0.
- Test reports: `DataStorePreferencesRepositoryTests` 7/7, `RoomRoundHistoryRepositoryTests` 10/10, `RoundHistorySchemaTests` 3/3, no skips.

## Summary

The port is faithful. Both protocols map 1:1 to suspend interfaces (D1); the key, log tags and log messages match the iOS ones exactly; `load`/`save` error handling matches (`null` on missing/undecodable, the bad value is left on disk, `save` never throws); the mapping (`RoundHistoryMapping.kt`) mirrors `RoundHistoryRepository.swift:103-168` line for line, including sorting by `index`, rejecting unknown raw values, and checking `correctIndex`/`selectedOption` before calling `Exercise`'s `require`. Room is set up correctly: the FK cascade is real (Room turns on `PRAGMA foreign_keys` when entities declare FKs, and `deleteRemovesTheRoundAndItsAnswers` and `savingTheSameIdTwiceKeepsOneRound` prove it), `replaceRound` is a single `@Transaction` (D5), `round()` with `@Relation` is `@Transaction`, `summaries` selects only `StoredRound` columns, `exportSchema = true` with the schema committed, and there is no destructive fallback. All 15 iOS tests are ported with the same cases (`deletingAnUnknownRoundDoesNothing` is slightly stronger); the spies match their Swift originals. The dispatcher is injected everywhere; `Dispatchers.IO` appears only as the default in `create`/`live`/`inMemory`. No new dependencies, no `INTERNET`, backup exclusions cover `files/datastore/` and `databases/`.

One finding blocks: the in-memory fallback doesn't cover a damaged store the way the KDoc and Q3 say it does, and that difference from iOS isn't documented.

## Blocking

### B1. A damaged history store is deleted by the platform, not kept with an in-memory fallback, and that isn't documented

`app/src/main/java/com/victorarana/leo/data/room/RoomRoundHistoryRepository.kt:21-23` (KDoc: "if that fails (full disk, damaged store), switches to an in-memory database"), `:39-47`.
iOS: `RoundHistoryRepository.swift:30-45`: a store that can't be opened, including a damaged one, is left on disk and history goes to memory for that launch.

On Android, Room opens through `FrameworkSQLiteOpenHelper`, which installs a `DatabaseErrorHandler` that calls `SupportSQLiteOpenHelper.Callback.onCorruption`. The default implementation (androidx.sqlite 2.6.2, `SupportSQLiteOpenHelper.android.kt:188-230`) **deletes the database file(s)**, and the framework then opens a fresh, empty file. So for the "damaged store" case, `openHelper.writableDatabase` usually succeeds and the `catch (SQLiteException)` never runs: the reader's history is wiped silently and the app keeps writing to a new file on disk. The in-memory fallback only covers the non-corruption cases (can't open, read-only or full disk, and via `IllegalStateException` a missing migration or downgrade).

This is a platform-driven behavior change, and Q3 was approved on the assumption that the iOS behavior carries over. CLAUDE.md requires every Android-specific change to be listed in `changes.md`, and the KDoc currently says the opposite of what happens.

Fix (pick with the user):
- (a) Accept the platform behavior: correct the KDoc (corruption: SQLite deletes the file and starts empty; other open failures: in-memory for this launch), and add an entry to `changes.md` under "Deviations" explaining the difference from iOS and why. This is the smallest change, and arguably better for the reader than iOS (history works again from the next round on, instead of staying in memory on every launch).
- (b) Keep the damaged file: pass `openHelperFactory` with a `SupportSQLiteOpenHelper.Factory` that wraps `FrameworkSQLiteOpenHelperFactory` and replaces the callback's `onCorruption` with one that throws (or records) instead of deleting, so the existing catch falls back to memory. This is more code and harder to test under Robolectric; only worth it if the user wants strict parity.

In either case, ask the user before choosing; don't decide silently.

## Non-blocking

### N1. The production DataStore factory, including its corruption handler, is never tested
`app/src/test/java/com/victorarana/leo/data/datastore/DataStorePreferencesRepositoryTests.kt:39-44` builds its own `PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, ...)`, a copy of `DataStorePreferencesRepository.kt:77-85`. `corruptFileLoadsNull` therefore tests the test's handler. It would pass with no handler at all, because `CorruptionException` is an `IOException` and `load` catches it. What the production handler actually buys is that **`save` works again** after the file is damaged, and that isn't asserted.
Fix: extract `internal fun dataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences>` in the companion, use it from `create` and from the test, and extend `corruptFileLoadsNull` to `save` and then `load` the saved value back.

### N2. A failed `open()` leaks the half-built database
`RoomRoundHistoryRepository.kt:41-47`: when `open()` returns a `RoundHistoryDatabase` but `openHelper.writableDatabase` throws, the instance is dropped without `close()`. Fix: keep the reference and `runCatching { it.close() }` before `fallBack(error)`.

### N3. Catching `IllegalStateException` also hides schema mistakes during development
`RoomRoundHistoryRepository.kt:44-46`. Room also reports "Room cannot verify the data integrity" (an entity changed without a version bump) as `IllegalStateException`. With this catch, a debug build silently runs on an in-memory store and only logs. That is acceptable for release, but it's worth one line in the KDoc and `changes.md` (the deviation note mentions only the migration case), or rethrowing when `BuildConfig.DEBUG` (if `buildConfig` is enabled).

### N4. Schema drift without a version bump isn't caught by a test
`app/src/test/java/com/victorarana/leo/data/room/RoundHistorySchemaTests.kt:34-38` checks only that `1.json` exists. If someone changes an entity and doesn't bump `version`, KSP silently rewrites `1.json`, and only the column-name tests might catch it (a type or nullability change wouldn't fail anything). Fix: in `versionIsFrozen`, open the in-memory DB, read `SELECT identity_hash FROM room_master_table`, and assert it equals `identityHash` in the committed `1.json`. The test's working directory (the `app/` module dir, set by Gradle) is also an implicit assumption; add a short comment.

### N5. Test-only members live in production code without a marker
`RoundHistoryDao.kt:43-45` (`answerCount`), `RoomRoundHistoryRepository.kt:74-80` (`daoForTesting`, `closeForTesting`). Fix: annotate them with `@VisibleForTesting` (androidx.annotation is already on the classpath through core-ktx). Alternatively, `answerCount` could be a raw `PRAGMA`/`SELECT` through `openHelper` in the test.

### N6. `features/` isn't guarded against DataStore imports
Acceptance criterion 1 says `features/` imports neither Room nor DataStore, but `config/detekt` `ForbiddenImport` lists only `com.google.mlkit.*` and `androidx.room.*`. Fix: add `androidx.datastore.*` with the same reason.

### N7. Small idiom nits
- `DataStorePreferencesRepository.kt:40-47`: `SerializationException` extends `IllegalArgumentException`, so the `SerializationException` branch is redundant. Collapse it to `IOException` + `IllegalArgumentException` (same for `save`), or keep it for readability with a comment.
- `RoundHistoryMapping.kt:24`: fully qualified `java.util.UUID`. Import it.
- `RoundHistoryConverters.kt:19,31,37`: a malformed UUID or JSON in a row throws from `round()`/`summaries()` instead of making the round "unreadable" like an unknown raw value does. That can't happen on iOS (typed columns), and can't happen here unless the file is hand-edited, so no change is needed; mentioned for completeness.
- `roundsSurviveANewDatabase` never closes the second repository. Harmless under Robolectric.

### N8. `docs/learning/phase-2.md` is missing
Acceptance criterion 9 and CLAUDE.md "Teaching" require it. `changes.md` says it was skipped "per the request". Make sure it's written before the phase is closed.

## Checklist

| Area | Result |
|---|---|
| Parity: key, JSON, log tags/messages, null-on-failure, save never throws | OK |
| Parity: summaries per group, newest first, from `results` only | OK |
| Parity: mapping, unreadable round → `null` + log, sorted by `index` | OK |
| Parity: delete cascade, unknown delete no-op, same id replaces (D5) | OK |
| Parity: open failure → in-memory | Partial, see B1 |
| iOS tests ported (5 + 7 + 3), same cases; spies | OK |
| Room: transaction, FK cascade, `exportSchema`, schema committed, no destructive fallback | OK |
| Dispatchers injected; `setQueryCoroutineContext`; DataStore scope `ioDispatcher + SupervisorJob` | OK |
| Architecture: interfaces in `data/repositories`, impls in `datastore/`, `room/`; fakes not mocks | OK |
| Dependencies approved only; no `INTERNET`; backup exclusions | OK |
| Build, unit tests, detekt, ktlint, Android Lint | Pass |

## Verdict

**Changes needed.** Only B1 blocks: at minimum, correct the KDoc and document the corruption behavior in `changes.md` after confirming with the user (option a), or implement option b. The non-blocking items are recommended, with N1 and N8 first.

---

## Round 2

Re-reviewed the working tree after the "Review follow-up" section of `changes.md`. The user chose B1 option (b) and asked for N1–N7; N8 (the learning note) is the coordinator's.

Checks run by the reviewer:

- `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`: exit 0.
- Data suites: `DataStorePreferencesRepositoryTests` 7/7, `RoomRoundHistoryRepositoryTests` 11/11, `RoundHistorySchemaTests` 3/3.
- Two experiments on a throwaway copy of the repo in the scratchpad, not on the working tree:
  1. With the `openHelperFactory(KeepDamagedFileFactory(...))` line removed, `aDamagedFileIsKeptAndHistoryFallsBackToMemory` **fails**: the file now starts with `S`, so SQLite deleted it and wrote a fresh `SQLite format 3` file. The test really guards the fix.
  2. After adding a column to `StoredRound` without bumping `version`, `versionIsFrozen` **passes**: the build rewrote `1.json` (hash `62731baa…` → `71f7f136…`) before the test read it. Only `storedRoundAttributes` failed, because a column *name* changed. See R2-N1.

### Fix verification

| Item | Result |
|---|---|
| B1 (option b) | Resolved. `KeepDamagedFileFactory.kt` copies every `Configuration` field (`context`, `name`, `useNoBackupDirectory`, `allowDataLossOnRecovery`) and forwards every callback except `onCorruption`, which throws `SQLiteDatabaseCorruptException`. The open fails, `FrameworkSQLiteOpenHelper` retries once (a 500 ms sleep, on `ioDispatcher`) and rethrows, because `allowDataLossOnRecovery` is false, and the existing `SQLiteException` catch falls back to memory with the iOS log message. Corruption found later, during a query, now surfaces as a thrown `SQLiteException` from that call instead of a silent delete. That matches iOS's "errors propagate, the caller logs". The KDoc is corrected; the test is meaningful (experiment 1). |
| N1 | Resolved. `dataStore(scope, produceFile)` is shared by `create` and the tests; `corruptFileLoadsNull` now proves that `save` works after corruption. |
| N2 | Resolved. A half-opened database is closed before the fallback (`runCatching`) and before the debug rethrow. |
| N3 | Resolved as specified: `buildConfig = true`; debug rethrows, release falls back. See R2-N2. |
| N4 | **Not achieved.** See R2-N1. |
| N5 | Done, with a placement nit (R2-N3). |
| N6 | Resolved. `androidx.datastore.*` is in `ForbiddenImport`. |
| N7 | Resolved. A single `IllegalArgumentException` catch in `load` (`SerializationException` is kept in `save`, with a comment); `UUID` imported. |

### Blocking

None.

### Non-blocking

**R2-N1. The `identityHash` check can't fail in a normal build**
`app/src/test/java/com/victorarana/leo/data/room/RoundHistorySchemaTests.kt` (`versionIsFrozen`). The Room Gradle plugin rewrites `app/schemas/.../1.json` on every compile, so the "committed" hash it reads from the working tree is always the hash of the current entities (experiment 2). Fix: pin the hash as a golden literal, e.g. `assertEquals("62731baa043d77f865065d1634186891", identityHash())`, with a comment: "Changing an entity changes this hash: bump `version`, add a migration, then update the literal on purpose." Keep the `1.json` existence check. This matches how phase 1 pins the preferences JSON.

**R2-N2. The debug rethrow is quieter than the KDoc says, and the release branch is untested**
`RoomRoundHistoryRepository.kt:29-30, 55-61`. The rethrown `IllegalStateException` reaches the repository's caller, which (by the iOS contract) logs it and shows nothing. So "fails loudly" means one error log per call, not a crash. Unit tests always run the debug variant, so the release fallback path (`fallBack` on `IllegalStateException`) has no test. Fix: reword the KDoc ("…is rethrown, so every history call fails and logs instead of silently using memory"). Optionally, pass `rethrowSchemaErrors: Boolean = BuildConfig.DEBUG` to the internal constructor, so a test can cover both branches without depending on the variant.

**R2-N3. KDoc placed after `@VisibleForTesting`**
`RoomRoundHistoryRepository.kt:94-99`, `RoundHistoryDao.kt:44-46`. A KDoc block must come before the annotations, or it isn't attached to the declaration (Dokka and the IDE drop it). Fix: put the `/** … */` line first.

**R2-N4. A local variable shadows the property**
`RoomRoundHistoryRepository.kt:47`: `var database` in `openDatabase()` shadows the `database` property that `dao()` assigns. Fix: rename it to `opened`.

**R2-N5 (carried). `docs/learning/phase-2.md`**
Still missing. The coordinator owns it; acceptance criterion 9 needs it before the phase closes.

### Verdict (Round 2)

**Approve.** B1 is fixed for real, and the regression test fails without the fix. N1–N3 and N5–N7 are done. The remaining items are non-blocking. R2-N1 is worth doing before merging, because N4's goal (catching schema drift without a version bump) isn't met yet. It's a one-line change.
