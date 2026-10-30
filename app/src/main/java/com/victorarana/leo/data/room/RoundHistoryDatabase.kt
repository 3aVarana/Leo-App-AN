package com.victorarana.leo.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The on-disk schema for saved rounds, version [VERSION]. The port of `RoundHistorySchemaV1`.
 *
 * `exportSchema` writes each version to `app/schemas/`, and those files are committed. No build has
 * shipped yet, so there is no migration and no migration test. From the first released build, every
 * schema change must: bump `version`, add a `Migration(n, n + 1)` (or an `@AutoMigration`) to the
 * database builders, commit the new `n+1.json`, and test the migration. Never
 * `fallbackToDestructiveMigration`: a reader's history is theirs.
 */
@Database(
    entities = [StoredRound::class, StoredAnswer::class],
    version = RoundHistoryDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(RoundHistoryConverters::class)
abstract class RoundHistoryDatabase : RoomDatabase() {
    abstract fun dao(): RoundHistoryDao

    companion object {
        const val VERSION = 1

        /** The store in `databases/`, local only (excluded from cloud backup). */
        const val FILE_NAME = "RoundHistory.db"
    }
}
