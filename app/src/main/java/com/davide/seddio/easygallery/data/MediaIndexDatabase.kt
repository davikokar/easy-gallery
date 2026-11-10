package com.davide.seddio.easygallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaIndexEntity::class, MediaIndexWatermarkEntity::class],
    version = 4,
    exportSchema = true
)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
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
    }
}
