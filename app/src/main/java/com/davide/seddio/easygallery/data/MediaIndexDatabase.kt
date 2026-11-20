package com.davide.seddio.easygallery.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "MediaIndexDatabase"

/**
 * Zeroes the bytes of every deleted or replaced row from now on, so Tier C data does not linger in free pages
 * (ADR-0018 Amendment 5). Set rather than assumed: AOSP compiles it in, an OEM's SQLite need not.
 * Returns whether the read-back says it is on. Call it on the write connection: inside a transaction.
 */
internal fun SupportSQLiteDatabase.enableSecureDelete(): Boolean {
    return query("PRAGMA secure_delete = 1").use { it.moveToFirst() && it.getInt(0) == 1 }
}

/**
 * True when the write-ahead log was emptied. Outside a write-ahead log there is nothing to checkpoint and this reads
 * 0 too, so it is evidence of a scrub only for `wal`: [deletionIsScrubbed] decides what any other mode may claim.
 */
internal fun SupportSQLiteDatabase.checkpointTruncate(): Boolean {
    return query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() && it.getInt(0) == 0 }
}

/** The connection's journal mode as SQLite spells it, normally lower case; empty when it answers nothing. */
internal fun SupportSQLiteDatabase.journalMode(): String = pragmaText("PRAGMA journal_mode")

internal fun SupportSQLiteDatabase.lockingMode(): String = pragmaText("PRAGMA locking_mode")

private fun SupportSQLiteDatabase.pragmaText(sql: String): String {
    return query(sql).use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
}

/**
 * Whether a committed deletion left no copy of the deleted pages in the database's files (ADR-0018 Amendment 6),
 * by the journal mode it committed under. [logWasEmptied] is asked only for `wal`. Anything not listed is false.
 */
internal fun deletionIsScrubbed(journalMode: String, lockingMode: String, logWasEmptied: () -> Boolean): Boolean {
    val normalLocking = lockingMode.trim().lowercase() == "normal"
    return when (journalMode.trim().lowercase()) {
        "wal" -> logWasEmptied()
        "delete", "truncate" -> normalLocking
        "memory", "off" -> true
        else -> false
    }
}

@Database(
    entities = [
        MediaIndexEntity::class,
        MediaIndexWatermarkEntity::class,
        MediaIndexFaceStateEntity::class,
        MediaIndexFaceEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(FaceIndexOutcomeConverters::class)
abstract class MediaIndexDatabase : RoomDatabase() {
    abstract fun mediaIndexDao(): MediaIndexDao

    companion object {
        private const val DATABASE_NAME = "media_index.db"

        fun create(context: Context): MediaIndexDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MediaIndexDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(SecureDeleteOnOpen)
                .build()
        }

        // A failed read-back must not stop the index opening; the face-data deletion checks it again and fails then.
        private val SecureDeleteOnOpen = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                val enabled = try {
                    // A transaction pins the pragma to the write connection, not a pooled reader.
                    db.beginTransaction()
                    try {
                        db.enableSecureDelete().also { db.setTransactionSuccessful() }
                    } finally {
                        db.endTransaction()
                    }
                } catch (e: RuntimeException) {
                    false
                }
                if (!enabled) Log.w(TAG, "secure_delete could not be confirmed when the media index opened")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_index_watermark` (
                        `volumeName` TEXT NOT NULL,
                        `version` TEXT,
                        `generation` INTEGER,
                        `dateModifiedWatermarkSeconds` INTEGER,
                        PRIMARY KEY(`volumeName`)
                    )
                    """.trimIndent()
                )
            }
        }

        // Earlier builds stored the QuickTime zero date as a capture date; no re-extraction will ever fix it.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE `media_index` SET `capturedDateTakenEpochMillis` = NULL " +
                        "WHERE `capturedDateTakenEpochMillis` = $QUICKTIME_ZERO_EPOCH_MILLIS"
                )
            }
        }

        // No default: every existing row reads null, which means no resolver has written its derived columns yet.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_index` ADD COLUMN `derivedPlaceResolverVersion` INTEGER")
            }
        }

        // Tier C tables, empty, so every image starts pending; no existing table changes. The SQL is 5.json's createSql.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_index_face_state` (`mediaUri` TEXT NOT NULL, " +
                        "`pipelineVersion` INTEGER NOT NULL, `outcome` TEXT NOT NULL, PRIMARY KEY(`mediaUri`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_index_face` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mediaUri` TEXT NOT NULL, `pipelineVersion` INTEGER NOT NULL, `boxLeft` REAL NOT NULL, " +
                        "`boxTop` REAL NOT NULL, `boxRight` REAL NOT NULL, `boxBottom` REAL NOT NULL, " +
                        "`embedding` BLOB NOT NULL, `groupId` INTEGER, `groupingVersion` INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_face_mediaUri` ON `media_index_face` (`mediaUri`)")
            }
        }
    }
}
