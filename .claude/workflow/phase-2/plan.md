# Phase 2: Storage, plan

iOS spec: `Leo/Data/Repositories/PreferencesRepository.swift`, `Leo/Data/Repositories/RoundHistoryRepository.swift`, `Leo/Data/SwiftData/RoundHistorySchema.swift`. Tests: `LeoTests/Data/{UserDefaultsPreferencesRepositoryTests,SwiftDataRoundHistoryRepositoryTests,RoundHistorySchemaTests}.swift`; fakes `LeoTests/Support/{PreferencesRepositorySpy,RoundHistoryRepositorySpy,TestDefaults}.swift`. Callers (for API shape only): `Features/Root/RootViewModel.swift:29,41`, `App/MyApp.swift:13,16`.

Reuse from phases 0-1: `:domain` `ReaderPreferences` + `ReaderPreferencesSerializer` + `LeoJson` (`ReaderPreferencesSerializer.kt:17`, ignores unknown keys, coerces nulls), `UuidSerializer` (lowercase out, any case in), `RoundRecord`, `RoundAnswer`, `RoundSummary`, `Exercise` (`require` on `correctIndex`), `AgeGroup/TopicKind/ComprehensionSkill.fromRawValue`; test fixtures `readerPreferencesFixture`, `roundRecordFixture`, `exerciseFixture` (`domain/src/testFixtures/.../Fixtures.kt`). Catalog already has `datastore-preferences`, `room-runtime`, `room-ktx`, `room-compiler`, `coroutines-*`, plugins `ksp` and `room`; none applied to `:app` yet. Backup rules already exclude `file` and `database` from cloud backup (`app/src/main/res/xml/data_extraction_rules.xml`), so DataStore and Room files are covered.

## File map

All in `:app`, `app/src/main/java/com/victorarana/leo/data/`.

| iOS | Kotlin |
|---|---|
| `PreferencesRepository.swift:5-9` (protocol) | `data/repositories/PreferencesRepository.kt`: `interface PreferencesRepository { suspend fun load(): ReaderPreferences?; suspend fun save(preferences: ReaderPreferences) }` (D1) |
| `PreferencesRepository.swift:12-38` | `data/datastore/DataStorePreferencesRepository.kt`: `class DataStorePreferencesRepository(dataStore: DataStore<Preferences>)`, `companion { const val KEY = "readerPreferences"; fun create(context, ioDispatcher): DataStorePreferencesRepository }` (D2) |
| `RoundHistoryRepository.swift:7-14` (protocol) | `data/repositories/RoundHistoryRepository.kt`: `suspend fun save(round: RoundRecord)`, `suspend fun summaries(ageGroup: AgeGroup): List<RoundSummary>`, `suspend fun round(id: UUID): RoundRecord?`, `suspend fun delete(id: UUID)` |
| `RoundHistoryRepository.swift:17-99` | `data/room/RoomRoundHistoryRepository.kt` (`live(context, ioDispatcher)`, `inMemory(context, ioDispatcher)`), `data/room/RoundHistoryDao.kt` |
| `RoundHistoryRepository.swift:101-168` (mapping) | `data/room/RoundHistoryMapping.kt`: `RoundRecord.toStored()`, `StoredRoundWithAnswers.toRecordOrNull()`, `StoredAnswer.toAnswerOrNull()` (internal) |
| `RoundHistorySchema.swift:6-14, 94-102` | `data/room/RoundHistoryDatabase.kt`: `@Database(entities = [StoredRound::class, StoredAnswer::class], version = 1, exportSchema = true)`, `companion const val VERSION = 1`, `const val FILE_NAME = "RoundHistory.db"`; `data/room/RoundHistoryConverters.kt` (UUID, Instant, `List<String>`, `List<Boolean>`) |
| `RoundHistorySchema.swift:19-37` | `data/room/StoredRound.kt`: `@Entity(tableName = "StoredRound")`, `@PrimaryKey id: UUID`, `finishedAt: Instant`, `ageGroup: String` (indexed), `results: List<Boolean>`; `StoredRoundWithAnswers` (`@Embedded` + `@Relation`) |
| `RoundHistorySchema.swift:41-91` | `data/room/StoredAnswer.kt`: `@Entity(tableName = "StoredAnswer")`, autogen `rowId`, `roundId: UUID` (`ForeignKey(onDelete = CASCADE)`, indexed), then the 13 iOS fields with the same names (`index`, `exerciseID`, ... `explanation`) |
| Schema file | `app/schemas/com.victorarana.leo.data.room.RoundHistoryDatabase/1.json` (generated, committed) |
| `LeoTests/Support/*Spy.swift` | `app/src/test/java/com/victorarana/leo/support/PreferencesRepositorySpy.kt`, `RoundHistoryRepositorySpy.kt` (`errorToThrow: Throwable?`) |

## Behavior to preserve

- Key `"readerPreferences"`, never changed (`PreferencesRepository.swift:13-14`; test `usesReaderPreferencesKey`). Value is the `ReaderPreferences` JSON from `LeoJson` (shape fixed by phase 1's `savedFormat` golden).
- `load()`: nothing stored → `null` (`:21`); undecodable → log `"Couldn't read the saved preferences: …"` and `null` (`:22-27`), stored data left untouched. Version-1 JSON `{"ageGroup":"12-14"}` → defaults (`UserDefaultsPreferencesRepositoryTests.swift:32-35`).
- `save()` never throws to the caller; failure logs `"Couldn't save the preferences: …"` and keeps old data (`:30-37`). Log tag `"PreferencesRepository"` (`:18`).
- History log tag `"RoundHistoryRepository"` (`RoundHistoryRepository.swift:18`); `save`/`delete`/`summaries`/`round` propagate errors, the caller logs (`:5-6`).
- `summaries(ageGroup)`: only that group, newest `finishedAt` first (`:72-81`); reads `results`, never the answers table (`:70-71`, `RoundHistorySchema.swift:25-27`).
- `save` writes `results = answers.map { isCorrect }` alongside the answers (`RoundHistoryRepository.swift:109`). `id` unique (`RoundHistorySchema.swift:21`): saving the same id again replaces it (D5).
- `round(id)`: unknown → `null` (`:85`); answers sorted by `index` (`:140`, schema `:43-44`); unknown `ageGroup`, `topicKind` or `skill` raw value, or `correctIndex`/`selectedOption` outside `options.indices` → whole round `null`, logged `"Round $id has unreadable data, skipping it"` (`:86-88`, `:136-154`). Check indices before building `Exercise` (its `require` would throw).
- Restored exercise keeps its stored `exerciseID`, topic prompt/name/kind, texts as saved; nothing re-localized (`RoundHistorySchema.swift:39-40`, `:155-166`).
- `delete(id)`: cascades to answers (`RoundHistorySchema.swift:28`); unknown id does nothing, no throw (`:93-98`).
- Store file local only, no sync (`:30-32`). Open failure → in-memory store for that launch, logged `"Couldn't open the round history store, keeping history in memory this launch: …"` (`:40-45`, D4).

## Tests to port

| iOS | Kotlin (`app/src/test/java/com/victorarana/leo/data/...`) | Notes |
|---|---|---|
| `UserDefaultsPreferencesRepositoryTests` (5) | `datastore/DataStorePreferencesRepositoryTests` | Plain JUnit + `runTest`; DataStore via `PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("t.preferences_pb") })` with `TemporaryFolder` (the `TestDefaults` role). `usesReaderPreferencesKey` reads `stringPreferencesKey("readerPreferences")` directly |
| Android-only | same class: `corruptFileLoadsNil`, `nullWhenReadFails` | Garbage bytes in the `.preferences_pb` file → `null` via the corruption handler (D2) |
| `SwiftDataRoundHistoryRepositoryTests` (7) | `room/RoomRoundHistoryRepositoryTests` | Robolectric; `Room.inMemoryDatabaseBuilder` + `StandardTestDispatcher`. `deleteRemovesTheRoundAndItsAnswers` counts rows with a DAO `answerCount()`. `unreadableRoundIsSkipped` inserts entities through the DAO. `roundsSurviveANewContainer` uses a file in `TemporaryFolder`, closes and reopens. Dates: fixture `2001-01-01T00:00:00Z` + 0 / 1000 / 500 s |
| Android-only | same class: `savingTheSameIdTwiceKeepsOneRound`, `finishedAtKeepsMillisecondPrecision`, `fallsBackToInMemoryWhenTheStoreCannotOpen` | D3, D4, D5 |
| `RoundHistorySchemaTests` (3) | `room/RoundHistorySchemaTests` | `versionIsFrozen`: `RoundHistoryDatabase.VERSION == 1` and `app/schemas/.../1.json` exists. Attribute tests via `PRAGMA table_info`: `StoredRound` = {id, finishedAt, ageGroup, results}; `StoredAnswer` = the 13 iOS names + {rowId, roundId} (iOS `answers`/`round` relationship → `roundId`) |
| Support | `support/PreferencesRepositorySpy`, `support/RoundHistoryRepositorySpy` | Direct ports; used from phase 4 on. No test of their own (iOS has none) |

## Android decisions

- **D1 Suspend APIs.** iOS calls both repositories synchronously on the main actor (`RootViewModel.swift:29`). Android forbids disk I/O on the main thread (Room throws; DataStore is async). Options: (a) `suspend` functions, same names and stateless shape; (b) `Flow<ReaderPreferences?>`; (c) `runBlocking` first read. Recommend (a): parity of shape, and the launch intro (phase 4) covers the first load.
- **D2 Preferences DataStore with a string key.** Options: (a) Preferences DataStore, `stringPreferencesKey("readerPreferences")` holding the JSON text; (b) typed `DataStore<ReaderPreferences>` with a `Serializer`. Recommend (a): it is literally "same JSON under the same key", and a bad JSON value stays on disk and loads `null` exactly as iOS. File `files/datastore/leo.preferences_pb` (`preferencesDataStoreFile("leo")`), created once per process (`create` documented as "call once, from `AppContainer`"). `ReplaceFileCorruptionHandler { emptyPreferences() }` plus log for a damaged file; `IOException`/`SerializationException`/`IllegalArgumentException` caught in `load`, `IOException` in `save`. Scope `CoroutineScope(ioDispatcher + SupervisorJob())`, dispatcher injected.
- **D3 Columns.** UUIDs as `TEXT` lowercase (phase-1 Q3); `finishedAt` as epoch milliseconds `INTEGER` (Q1); `options` and `results` as JSON `TEXT` via `LeoJson` + `ListSerializer` (SwiftData stores arrays as a blob too). `index` is an SQL keyword: hand-written queries quote it (`ORDER BY \`index\``). Raw values stay `String` columns so an unknown value is detected on read, as on iOS.
- **D4 Store open + fallback.** Room opens lazily, on first query, so `live()` can't fail synchronously like `RoundHistoryRepository.swift:40-45`. Recommend: `RoomRoundHistoryRepository` holds a lazily opened database; the first call opens it on `ioDispatcher` (`openHelper.writableDatabase`), and on `SQLiteException` logs the iOS message and switches to an in-memory database. Opener injected as a lambda so the test can make it throw. `setQueryCoroutineContext(ioDispatcher)`. No `fallbackToDestructiveMigration`.
- **D5 Same id saved twice.** `@Attribute(.unique)` upserts. Room: one `@Transaction` that deletes by id (cascade) then inserts round + answers; not `REPLACE`, whose implicit delete doesn't reliably run FK actions.
- **D6 Schema versions.** SwiftData `RoundHistorySchemaV1` (1.0.0) + empty `RoundHistoryMigrationPlan` ↔ Room `version = 1`, `exportSchema = true`, schema JSON committed via the `androidx.room` Gradle plugin (`room { schemaDirectory("$projectDir/schemas") }`). **No migration and no migration test now:** no Android build has shipped. The rule from the first released build: bump `version`, add a `Migration(n, n+1)` (or `@AutoMigration`), commit `n+1.json`, test it. iOS V1 data never reaches Android, so there is nothing to import. A real migration test wants `androidx.room:room-testing` (`MigrationTestHelper`), not approved (Q2).
- **D7 Gradle.** `:app`: plugins `ksp`, `room`; `implementation(libs.androidx.datastore.preferences, libs.androidx.room.runtime, libs.androidx.room.ktx, libs.kotlinx.coroutines.android)`, `ksp(libs.androidx.room.compiler)`, `testImplementation(testFixtures(project(":domain")), libs.kotlinx.coroutines.test)`. Risk: KSP 2.3.12 with AGP 9 built-in Kotlin 2.2.10; if it fails, stop and report rather than change versions.

## Open questions for the user

1. `finishedAt` precision: store epoch **milliseconds** (Instant's nanoseconds are dropped; a restored round can differ from the in-memory one by under 1 ms), or epoch nanoseconds in one `Long`? Recommend milliseconds: History groups by week/month and sorts; nothing compares sub-ms. Test asserts truncation explicitly.
2. Approve `androidx.room:room-testing` now (catalog entry, unused until the first migration), or ask again when the first post-release schema change happens? Recommend ask again then; phase 2 has no migration.
3. D4: keep iOS's in-memory fallback when the database can't be opened (recommended, parity), or let the error reach the caller (simpler; History would show its error state)?
4. Device-to-device transfer currently carries both files to a new phone (`data_extraction_rules.xml:15`), like an iOS device migration. Keep? Recommend keep.

## Acceptance criteria

1. Every file in the file map exists; `features/` imports neither `androidx.room` nor `androidx.datastore` implementations (detekt `ForbiddenImport` still green).
2. `DataStorePreferencesRepository.KEY == "readerPreferences"`; saved value is the phase-1 JSON; missing, corrupt JSON and corrupt file each load `null` without throwing.
3. `RoomRoundHistoryRepository` restores every field of `roundRecordFixture`; summaries are per group and newest first; delete cascades; unknown delete is a no-op; an unreadable round returns `null`; data survives reopening the file.
4. `RoundHistoryDatabase` is version 1 with `exportSchema = true`; `app/schemas/.../1.json` is committed; table/column names match the iOS attribute names (`RoundHistorySchemaTests`).
5. No migration, no `fallbackToDestructiveMigration`; D6's rule is written in `RoundHistoryDatabase` KDoc and `changes.md`.
6. Dispatchers are constructor/factory parameters; no `Dispatchers.IO` hard-coded outside `create`/`live` defaults; no `println`.
7. `PreferencesRepositorySpy` and `RoundHistoryRepositorySpy` exist in `app/src/test/.../support/`.
8. No `INTERNET` permission; only approved dependencies added.
9. `./gradlew assembleDebug`, `./gradlew :domain:test testDebugUnitTest`, `./gradlew detekt ktlintCheck lintDebug` all pass; `changes.md` lists D1-D7, answers to Q1-Q4, commands and results; `docs/learning/phase-2.md` written.

## Tasks

1. Gradle (D7): plugins, deps, `room { schemaDirectory }`, `testFixtures(project(":domain"))`. `assembleDebug` passes.
2. `PreferencesRepository.kt`, `DataStorePreferencesRepository.kt`, `PreferencesRepositorySpy.kt`; `DataStorePreferencesRepositoryTests` (5 ported + 2). Tests pass.
3. `RoundHistoryRepository.kt`, `RoundHistoryRepositorySpy.kt`. Build passes.
4. `RoundHistoryConverters.kt`, `StoredRound.kt`, `StoredAnswer.kt`, `RoundHistoryDao.kt`, `RoundHistoryDatabase.kt`; build generates and commit `schemas/.../1.json`; `RoundHistorySchemaTests`. Tests pass.
5. `RoundHistoryMapping.kt`, `RoomRoundHistoryRepository.kt` (lazy open + fallback, D4/D5); `RoomRoundHistoryRepositoryTests` (7 ported + 3). Tests pass.
6. Run the three commands; fix detekt/ktlint/lint; write `changes.md` and `docs/learning/phase-2.md` (UserDefaults → DataStore, SwiftData → Room entities/DAO/exported schema, sync → suspend + injected dispatchers, `@Attribute(.unique)` → transaction).

## Decisions

Approved by the user on 2026-10-30.

- Plan approved as written: D1–D7 as recommended.
- Q1: `finishedAt` stored as epoch **milliseconds**.
- Q2: `androidx.room:room-testing` not added now; ask again at the first post-release schema change.
- Q3: keep the iOS **in-memory fallback** when the history store can't be opened.
- Q4: keep device-to-device transfer of the preferences and history files.
- Review B1 (2026-10-30): a damaged history database keeps its file and falls back to memory for that launch, as on iOS (override `onCorruption` to throw instead of deleting). Non-blocking N1–N7 fixed in the same round. N8 (learning note) is written by the orchestrator.
