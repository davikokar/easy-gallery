package com.davide.seddio.easygallery.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexDatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 adds watermark table and preserves media index rows`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media-index-migration-test.db"
        context.deleteDatabase(databaseName)

        val dbFile = context.getDatabasePath(databaseName)
        dbFile.parentFile?.mkdirs()

        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_index` (
                `mediaUri` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `dateModified` INTEGER NOT NULL,
                `volumeName` TEXT NOT NULL,
                `generationAdded` INTEGER,
                `generationModified` INTEGER,
                `capturedDateTakenEpochMillis` INTEGER,
                `capturedLatitude` REAL,
                `capturedLongitude` REAL,
                `capturedWidth` INTEGER,
                `capturedHeight` INTEGER,
                `derivedCountryCode` TEXT,
                `derivedRegionCode` TEXT,
                `derivedPlaceName` TEXT,
                PRIMARY KEY(`mediaUri`)
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_volumeName` ON `media_index` (`volumeName`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_capturedDateTakenEpochMillis` ON `media_index` (`capturedDateTakenEpochMillis`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_capturedLatitude_capturedLongitude` ON `media_index` (`capturedLatitude`, `capturedLongitude`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_derivedCountryCode_derivedRegionCode_derivedPlaceName` ON `media_index` (`derivedCountryCode`, `derivedRegionCode`, `derivedPlaceName`)")
        sqlite.execSQL(
            """
            INSERT INTO `media_index`(
                `mediaUri`, `displayName`, `size`, `dateModified`, `volumeName`,
                `generationAdded`, `generationModified`, `capturedDateTakenEpochMillis`,
                `capturedLatitude`, `capturedLongitude`, `capturedWidth`, `capturedHeight`,
                `derivedCountryCode`, `derivedRegionCode`, `derivedPlaceName`
            ) VALUES(
                'content://media/external_primary/images/media/10', 'before.jpg', 12, 34, 'external_primary',
                1, 2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
            )
            """.trimIndent()
        )
        sqlite.version = 1
        sqlite.close()

        val roomDb = Room.databaseBuilder(context, MediaIndexDatabase::class.java, databaseName)
            .addMigrations(MediaIndexDatabase.MIGRATION_1_2)
            .build()

        runBlocking {
            val row = roomDb.mediaIndexDao().getByMediaUri("content://media/external_primary/images/media/10")
            assertNotNull(row)
            assertEquals("before.jpg", row?.displayName)
        }

        val cursor = roomDb.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='media_index_watermark'"
        )
        cursor.use {
            assertEquals(true, it.moveToFirst())
        }

        roomDb.close()
        context.deleteDatabase(databaseName)
    }
}
