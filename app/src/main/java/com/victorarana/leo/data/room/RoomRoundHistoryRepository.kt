package com.victorarana.leo.data.room

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.victorarana.leo.BuildConfig
import com.victorarana.leo.data.repositories.RoundHistoryRepository
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Keeps finished rounds in a local Room database.
 *
 * Room opens a database on first use, not at construction, so the first call opens it (on
 * [ioDispatcher]) and, if that fails (full disk, damaged store), switches to an in-memory
 * database from [openInMemory]: the app still runs, history just isn't kept that launch.
 * A damaged file is left on disk, like iOS: [KeepDamagedFileFactory] stops SQLite from deleting it.
 * The fallback only covers failures at open: damage SQLite finds later makes each later call throw
 * for the rest of the launch, and the callers log it.
 *
 * With [rethrowSchemaErrors] (debug builds by default) an `IllegalStateException` from Room (an entity
 * changed without a version bump, or a missing migration) is rethrown instead of falling back, so every
 * history call fails and is logged by its caller instead of silently using memory.
 */
class RoomRoundHistoryRepository internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val open: () -> RoundHistoryDatabase,
    private val openInMemory: () -> RoundHistoryDatabase,
    private val rethrowSchemaErrors: Boolean = BuildConfig.DEBUG,
) : RoundHistoryRepository {
    private val openLock = Mutex()
    private var database: RoundHistoryDatabase? = null
    private var dao: RoundHistoryDao? = null

    private suspend fun dao(): RoundHistoryDao =
        openLock.withLock {
            dao ?: withContext(ioDispatcher) { openDatabase() }.also { database = it }.dao().also { dao = it }
        }

    private fun openDatabase(): RoundHistoryDatabase {
        var opened: RoundHistoryDatabase? = null
        return try {
            open().also {
                opened = it
                it.openHelper.writableDatabase
            }
        } catch (error: SQLiteException) {
            fallBack(opened, error)
        } catch (error: IllegalStateException) {
            // Room reports a version it can't migrate (a store from a newer build) this way.
            if (rethrowSchemaErrors) {
                opened?.close()
                throw error
            }
            fallBack(opened, error)
        }
    }

    private fun fallBack(
        failed: RoundHistoryDatabase?,
        error: Exception,
    ): RoundHistoryDatabase {
        runCatching { failed?.close() }
        Log.e(TAG, "Couldn't open the round history store, keeping history in memory this launch: $error")
        return openInMemory()
    }

    override suspend fun save(round: RoundRecord) {
        val stored = round.toStored()
        dao().replaceRound(stored.round, stored.answers)
    }

    /**
     * Doesn't read the answers: the score and dots come from `results`, saved alongside them. A row that
     * can't be decoded is skipped, so it doesn't hide the rest of the history.
     */
    override suspend fun summaries(ageGroup: AgeGroup): List<RoundSummary> =
        dao().summaries(ageGroup.rawValue).mapNotNull { stored ->
            stored.toSummaryOrNull().also { if (it == null) logUnreadable(stored.id) }
        }

    override suspend fun round(id: UUID): RoundRecord? {
        val dao = dao()
        val record =
            try {
                // The type converters run inside the query and throw on text that doesn't decode.
                val stored = dao.round(id) ?: return null
                stored.toRecordOrNull()
            } catch (_: IllegalArgumentException) {
                null
            }
        if (record == null) logUnreadable(id)
        return record
    }

    private fun logUnreadable(id: Any) {
        Log.e(TAG, "Round $id has unreadable data, skipping it")
    }

    override suspend fun delete(id: UUID) {
        dao().deleteRound(id)
    }

    /** For tests: the DAO the repository is using, opening the database if needed. */
    @VisibleForTesting
    internal suspend fun daoForTesting(): RoundHistoryDao = dao()

    /** For tests: closes the database so its file can be reopened. */
    @VisibleForTesting
    internal fun closeForTesting() {
        database?.close()
    }

    companion object {
        private const val TAG = "RoundHistoryRepository"

        /**
         * The on-disk store, [RoundHistoryDatabase.FILE_NAME] in the app's databases folder. Call once
         * per process, from `AppContainer`.
         */
        fun live(
            context: Context,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): RoomRoundHistoryRepository {
            val appContext = context.applicationContext
            return RoomRoundHistoryRepository(
                ioDispatcher = ioDispatcher,
                open = { onDiskDatabase(appContext, RoundHistoryDatabase.FILE_NAME, ioDispatcher) },
                openInMemory = { inMemoryDatabase(appContext, ioDispatcher) },
            )
        }

        /** For previews. */
        fun inMemory(
            context: Context,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): RoomRoundHistoryRepository {
            val appContext = context.applicationContext
            val database = { inMemoryDatabase(appContext, ioDispatcher) }
            return RoomRoundHistoryRepository(ioDispatcher, open = database, openInMemory = database)
        }

        /** [name] is a file name in `databases/`, or an absolute path. */
        internal fun onDiskDatabase(
            context: Context,
            name: String,
            ioDispatcher: CoroutineDispatcher,
        ): RoundHistoryDatabase =
            Room
                .databaseBuilder(context, RoundHistoryDatabase::class.java, name)
                .openHelperFactory(KeepDamagedFileFactory(FrameworkSQLiteOpenHelperFactory()))
                .setQueryCoroutineContext(ioDispatcher)
                .build()

        private fun inMemoryDatabase(
            context: Context,
            ioDispatcher: CoroutineDispatcher,
        ): RoundHistoryDatabase =
            Room
                .inMemoryDatabaseBuilder(context, RoundHistoryDatabase::class.java)
                .setQueryCoroutineContext(ioDispatcher)
                .build()
    }
}
