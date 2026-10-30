package com.victorarana.leo.data.room

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * Stops SQLite from deleting a damaged database file. By default `onCorruption` deletes the file and
 * the platform opens an empty one, silently wiping the reader's history. iOS leaves the file alone and
 * keeps history in memory for that launch; throwing here makes `RoomRoundHistoryRepository` do the same.
 */
internal class KeepDamagedFileFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        val wrapped =
            object : SupportSQLiteOpenHelper.Callback(callback.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = callback.onConfigure(db)

                override fun onCreate(db: SupportSQLiteDatabase) = callback.onCreate(db)

                override fun onOpen(db: SupportSQLiteDatabase) = callback.onOpen(db)

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = callback.onUpgrade(db, oldVersion, newVersion)

                override fun onDowngrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = callback.onDowngrade(db, oldVersion, newVersion)

                override fun onCorruption(db: SupportSQLiteDatabase): Unit =
                    throw SQLiteDatabaseCorruptException("The database file is damaged, leaving it on disk")
            }
        val kept =
            SupportSQLiteOpenHelper.Configuration
                .builder(configuration.context)
                .name(configuration.name)
                .callback(wrapped)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build()
        return delegate.create(kept)
    }
}
