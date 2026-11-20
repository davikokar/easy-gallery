package com.davide.seddio.easygallery.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

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
            .addMigrations(
                MediaIndexDatabase.MIGRATION_1_2,
                MediaIndexDatabase.MIGRATION_2_3,
                MediaIndexDatabase.MIGRATION_3_4,
                MediaIndexDatabase.MIGRATION_4_5
            )
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

    @Test
    fun `migration 2 to 3 nulls only the quicktime zero date and touches nothing else`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media_index.db"
        context.deleteDatabase(databaseName)
        val seeded = createSeededDatabase(context, databaseName, version = 2)

        val roomDb = Room.databaseBuilder(context, MediaIndexDatabase::class.java, databaseName)
            .addMigrations(
                MediaIndexDatabase.MIGRATION_2_3,
                MediaIndexDatabase.MIGRATION_3_4,
                MediaIndexDatabase.MIGRATION_4_5
            )
            .build()
        val db = roomDb.openHelper.writableDatabase

        assertEquals(5, db.version)
        assertEquals(seeded.otherColumns, db.query(OTHER_COLUMNS_SQL).use(::snapshot))
        assertEquals(seeded.watermarks, db.query(WATERMARK_SQL).use(::snapshot))
        assertEquals(
            listOf(null, REAL_1950_DATE, null, ONE_SECOND_AFTER_SENTINEL),
            db.query(DATES_SQL).use(::dates)
        )

        roomDb.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `a migrated 2 to 3 database reopens with the production builder`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media_index.db"
        context.deleteDatabase(databaseName)
        val seeded = createSeededDatabase(context, databaseName, version = 2)

        val migrated = MediaIndexDatabase.create(context)
        migrated.openHelper.writableDatabase
        migrated.close()

        val reopened = MediaIndexDatabase.create(context)
        val db = reopened.openHelper.writableDatabase
        assertEquals(5, db.version)
        assertEquals(seeded.otherColumns, db.query(OTHER_COLUMNS_SQL).use(::snapshot))
        assertEquals(seeded.watermarks, db.query(WATERMARK_SQL).use(::snapshot))
        runBlocking {
            val sentinelRow = reopened.mediaIndexDao().getByMediaUri("content://media/external/video/media/1")
            assertNotNull(sentinelRow)
            assertNull(sentinelRow?.capturedDateTakenEpochMillis)
        }

        reopened.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `migration 3 to 4 adds a null resolver marker and preserves every existing column`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media_index.db"
        context.deleteDatabase(databaseName)
        val seeded = createSeededDatabase(context, databaseName, version = 3)

        val roomDb = Room.databaseBuilder(context, MediaIndexDatabase::class.java, databaseName)
            .addMigrations(MediaIndexDatabase.MIGRATION_3_4, MediaIndexDatabase.MIGRATION_4_5)
            .build()
        val db = roomDb.openHelper.writableDatabase

        assertEquals(5, db.version)
        assertEquals(seeded.allColumns, db.query(ALL_COLUMNS_SQL).use(::snapshot))
        assertEquals(seeded.watermarks, db.query(WATERMARK_SQL).use(::snapshot))
        assertEquals(listOf<Long?>(null, null, null, null), db.query(MARKER_SQL).use(::dates))
        // The sentinel survives: 3 to 4 touches no data.
        assertEquals(
            listOf(QUICKTIME_ZERO_EPOCH_MILLIS, REAL_1950_DATE, null, ONE_SECOND_AFTER_SENTINEL),
            db.query(DATES_SQL).use(::dates)
        )

        roomDb.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `a migrated 3 to 4 database reopens with the production builder and takes marker writes`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media_index.db"
        context.deleteDatabase(databaseName)
        createSeededDatabase(context, databaseName, version = 3)

        val migrated = MediaIndexDatabase.create(context)
        migrated.openHelper.writableDatabase
        migrated.close()

        val reopened = MediaIndexDatabase.create(context)
        runBlocking {
            val uri = "content://media/external/video/media/2"
            val before = reopened.mediaIndexDao().getByMediaUri(uri)
            assertNull(before?.derivedPlaceResolverVersion)
            assertEquals(1, reopened.mediaIndexDao().updateResolvedPlace(uri, 45.5, 9.25, "IT", "25", "Milan", 1))
            assertEquals(1, reopened.mediaIndexDao().getByMediaUri(uri)?.derivedPlaceResolverVersion)
        }

        reopened.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `the full chain from version 1 to 5 opens with the production builder`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media_index.db"
        context.deleteDatabase(databaseName)
        val seeded = createSeededDatabase(context, databaseName, version = 1)

        val migrated = MediaIndexDatabase.create(context)
        val db = migrated.openHelper.writableDatabase

        assertEquals(5, db.version)
        assertEquals(seeded.otherColumns, db.query(OTHER_COLUMNS_SQL).use(::snapshot))
        assertEquals(
            listOf(null, REAL_1950_DATE, null, ONE_SECOND_AFTER_SENTINEL),
            db.query(DATES_SQL).use(::dates)
        )
        assertEquals(listOf<Long?>(null, null, null, null), db.query(MARKER_SQL).use(::dates))
        assertEquals(emptyList<List<String?>>(), db.query(WATERMARK_SQL).use(::snapshot))
        assertEquals(emptyList<List<String?>>(), db.query(FACE_ROWS_SQL).use(::snapshot))
        assertEquals(emptyList<List<String?>>(), db.query(FACE_STATE_ROWS_SQL).use(::snapshot))
        runBlocking {
            assertEquals(4, migrated.mediaIndexDao().observeAll().first().size)
        }

        migrated.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `migration 4 to 5 adds empty tier c tables and changes no existing table`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media_index.db"
        context.deleteDatabase(databaseName)
        val seeded = createSeededDatabase(context, databaseName, version = 4)

        val roomDb = Room.databaseBuilder(context, MediaIndexDatabase::class.java, databaseName)
            .addMigrations(MediaIndexDatabase.MIGRATION_4_5)
            .build()
        val db = roomDb.openHelper.writableDatabase

        assertEquals(5, db.version)
        assertEquals(seeded.allColumns, db.query(ALL_COLUMNS_SQL).use(::snapshot))
        assertEquals(seeded.markers, db.query(MARKER_SQL).use(::dates))
        assertEquals(seeded.watermarks, db.query(WATERMARK_SQL).use(::snapshot))
        assertEquals(seeded.existingSchema, db.query(EXISTING_SCHEMA_SQL).use(::snapshot))
        assertEquals(emptyList<List<String?>>(), db.query(FACE_ROWS_SQL).use(::snapshot))
        assertEquals(emptyList<List<String?>>(), db.query(FACE_STATE_ROWS_SQL).use(::snapshot))
        assertEquals(
            listOf("index_media_index_face_mediaUri"),
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'media_index_face' ORDER BY name")
                .use { cursor -> snapshot(cursor).map { it.single() } }
        )

        roomDb.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `a migrated 4 to 5 database reopens with the production builder and takes a face write`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val databaseName = "media_index.db"
        context.deleteDatabase(databaseName)
        createSeededDatabase(context, databaseName, version = 4)

        val migrated = MediaIndexDatabase.create(context)
        migrated.openHelper.writableDatabase
        migrated.close()

        val reopened = MediaIndexDatabase.create(context)
        val store = RoomMediaIndexStore(reopened)
        runBlocking {
            val uri = "content://media/external/video/media/2"
            val identity = mediaIdentityOf(uri, displayName = "clip2.mp4", size = 101L, dateModified = 201L)
            val detection = FaceDetection(FaceBox(0.1f, 0.2f, 0.3f, 0.4f), FaceEmbedding(floatArrayOf(0.5f, -0.25f)))

            val applied = store.writeFaceIndexResults(
                listOf(FaceIndexWrite(identity, pipelineVersion = 1, outcome = FaceIndexOutcome.INDEXED, faces = listOf(detection)))
            )

            assertEquals(1, applied)
            assertEquals(FaceIndexState(uri, 1, FaceIndexOutcome.INDEXED), store.getFaceIndexState(uri))
            assertEquals(listOf(detection), store.listFaces(uri).map { FaceDetection(it.box, it.embedding) })
            assertEquals(4, reopened.mediaIndexDao().observeAll().first().size)
        }

        reopened.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `the shared sentinel is the quicktime zero date`() {
        assertEquals(-2_082_844_800_000L, QUICKTIME_ZERO_EPOCH_MILLIS)
    }

    private class Seeded(
        val otherColumns: List<List<String?>>,
        val allColumns: List<List<String?>>,
        val watermarks: List<List<String?>>,
        val markers: List<Long?>,
        val existingSchema: List<List<String?>>
    )

    // Builds a file at the given version (1 to 4) with rows at the sentinel, a real 1950 date, null, and sentinel + 1 second.
    // From version 4 the rows carry a resolver marker: 3, null, 5, null.
    private fun createSeededDatabase(context: Context, databaseName: String, version: Int): Seeded {
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
                ${if (version >= 4) "`derivedPlaceResolverVersion` INTEGER," else ""}
                PRIMARY KEY(`mediaUri`)
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_volumeName` ON `media_index` (`volumeName`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_capturedDateTakenEpochMillis` ON `media_index` (`capturedDateTakenEpochMillis`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_capturedLatitude_capturedLongitude` ON `media_index` (`capturedLatitude`, `capturedLongitude`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_derivedCountryCode_derivedRegionCode_derivedPlaceName` ON `media_index` (`derivedCountryCode`, `derivedRegionCode`, `derivedPlaceName`)")
        if (version >= 2) {
            sqlite.execSQL(
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
        val dates = listOf(QUICKTIME_ZERO_EPOCH_MILLIS, REAL_1950_DATE, null, ONE_SECOND_AFTER_SENTINEL)
        dates.forEachIndexed { i, date ->
            sqlite.execSQL(
                """
                INSERT INTO `media_index` VALUES(
                    'content://media/external/video/media/${i + 1}', 'clip${i + 1}.mp4', ${100 + i}, ${200 + i}, 'external_primary',
                    ${10 + i}, ${20 + i}, ${date ?: "NULL"},
                    45.5, 9.25, 1920, 1080, 'IT', '25', 'Milan'${if (version >= 4) ", ${if (i % 2 == 0) 3 + i else "NULL"}" else ""}
                )
                """.trimIndent()
            )
        }
        if (version >= 2) {
            sqlite.execSQL("INSERT INTO `media_index_watermark` VALUES('external_primary', '1234', 77, 1700000000)")
        }
        val seeded = Seeded(
            otherColumns = sqlite.rawQuery(OTHER_COLUMNS_SQL, null).use(::snapshot),
            allColumns = sqlite.rawQuery(ALL_COLUMNS_SQL, null).use(::snapshot),
            watermarks = if (version >= 2) sqlite.rawQuery(WATERMARK_SQL, null).use(::snapshot) else emptyList(),
            markers = if (version >= 4) sqlite.rawQuery(MARKER_SQL, null).use(::dates) else emptyList(),
            existingSchema = sqlite.rawQuery(EXISTING_SCHEMA_SQL, null).use(::snapshot)
        )
        sqlite.version = version
        sqlite.close()
        return seeded
    }

    private fun snapshot(cursor: Cursor): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        while (cursor.moveToNext()) {
            rows += (0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }
        }
        return rows
    }

    private fun dates(cursor: Cursor): List<Long?> {
        val values = mutableListOf<Long?>()
        while (cursor.moveToNext()) values += if (cursor.isNull(0)) null else cursor.getLong(0)
        return values
    }

    private companion object {
        const val OTHER_COLUMNS_SQL =
            "SELECT `mediaUri`, `displayName`, `size`, `dateModified`, `volumeName`, `generationAdded`, " +
                "`generationModified`, `capturedLatitude`, `capturedLongitude`, `capturedWidth`, `capturedHeight`, " +
                "`derivedCountryCode`, `derivedRegionCode`, `derivedPlaceName` FROM `media_index` ORDER BY `mediaUri`"
        const val WATERMARK_SQL = "SELECT * FROM `media_index_watermark` ORDER BY `volumeName`"
        const val DATES_SQL = "SELECT `capturedDateTakenEpochMillis` FROM `media_index` ORDER BY `mediaUri`"
        const val MARKER_SQL = "SELECT `derivedPlaceResolverVersion` FROM `media_index` ORDER BY `mediaUri`"
        const val FACE_ROWS_SQL = "SELECT * FROM `media_index_face`"
        const val FACE_STATE_ROWS_SQL = "SELECT * FROM `media_index_face_state`"
        const val EXISTING_SCHEMA_SQL =
            "SELECT `name`, `sql` FROM `sqlite_master` WHERE `tbl_name` IN ('media_index', 'media_index_watermark') ORDER BY `name`"
        const val ALL_COLUMNS_SQL =
            "SELECT `mediaUri`, `displayName`, `size`, `dateModified`, `volumeName`, `generationAdded`, " +
                "`generationModified`, `capturedDateTakenEpochMillis`, `capturedLatitude`, `capturedLongitude`, " +
                "`capturedWidth`, `capturedHeight`, `derivedCountryCode`, `derivedRegionCode`, `derivedPlaceName` " +
                "FROM `media_index` ORDER BY `mediaUri`"

        val REAL_1950_DATE: Long =
            LocalDate.of(1950, 6, 15).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val ONE_SECOND_AFTER_SENTINEL: Long = QUICKTIME_ZERO_EPOCH_MILLIS + 1_000
    }
}
