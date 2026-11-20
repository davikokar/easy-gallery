package com.davide.seddio.easygallery.data

import android.app.ActivityManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteTransactionListener
import android.os.Build
import android.os.CancellationSignal
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.io.IOException

/**
 * ADR-0018 Amendment 5: no deleted face data may survive in the file. Each case drives the production `create`
 * over a real file and searches every `media_index.db*` file for a byte marker carried by every embedding.
 * It fails with `secure_delete` off, and without the checkpoint.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MediaIndexFaceDataScrubTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private var database: MediaIndexDatabase? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        dbFile = context.getDatabasePath(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `face data is in no file after the deletion on a write-ahead log database`() = runBlocking {
        createFileWithoutAutoVacuum()
        val (db, store) = openProductionDatabase()
        assertEquals("wal", db.pragma("PRAGMA journal_mode"))
        assertEquals("1", db.pragma("PRAGMA secure_delete"))
        assertEquals("0", db.pragma("PRAGMA auto_vacuum"))
        val expectedRows = populateWithDeletionsAndAHeal(store)
        assertTrue("control: the marker is in the files before the deletion", markerCounts().values.sum() > 0)
        assertTrue(
            "control: the log also holds copies of the embeddings that earlier deletions removed",
            markerCounts().values.sum() > LIVE_FACES * MARKERS_PER_EMBEDDING
        )

        val deletion = store.deleteAllFaceData()

        assertEquals(FaceDataDeletion(rowsDeleted = expectedRows, scrubbed = true), deletion)
        assertEquals(emptyMap<String, Int>(), markerCounts().filterValues { it > 0 })
        assertFalse(store.hasFaceData())
        db.close()
        database = null
        assertEquals(emptyMap<String, Int>(), markerCounts().filterValues { it > 0 })
    }

    @Test
    fun `face data is in no file after the deletion when production opens a low ram device without wal and the journal is forced to truncate`() = runBlocking {
        shadowOf(context.getSystemService(ActivityManager::class.java)).setIsLowRamDevice(true)
        createFileWithoutAutoVacuum()
        val (db, store) = openProductionDatabase()
        // Proves production's own open took the non-WAL branch; the TRUNCATE below is forced, so it proves nothing about production.
        assertTrue("production must not open a low ram device in wal", db.pragma("PRAGMA journal_mode") != "wal")
        db.useTruncateJournal()
        assertEquals("truncate", db.pragma("PRAGMA journal_mode"))
        assertEquals("0", db.pragma("PRAGMA auto_vacuum"))
        assertEquals("1", db.pragma("PRAGMA secure_delete"))
        val expectedRows = populateWithDeletionsAndAHeal(store)
        assertTrue("control: the marker is in the files before the deletion", markerCounts().values.sum() > 0)

        val deletion = store.deleteAllFaceData()

        assertEquals(FaceDataDeletion(rowsDeleted = expectedRows, scrubbed = true), deletion)
        assertEquals(emptyMap<String, Int>(), markerCounts().filterValues { it > 0 })
        db.close()
        database = null
        assertEquals(emptyMap<String, Int>(), markerCounts().filterValues { it > 0 })
    }

    @Test
    fun `a rollback journal that keeps its page copies deletes the rows but does not report a scrub`() = runBlocking {
        shadowOf(context.getSystemService(ActivityManager::class.java)).setIsLowRamDevice(true)
        createFileWithoutAutoVacuum()
        val (db, store) = openProductionDatabase()
        assertTrue("production must not open a low ram device in wal", db.pragma("PRAGMA journal_mode") != "wal")
        db.usePersistJournal()
        assertEquals("persist", db.pragma("PRAGMA journal_mode"))
        assertEquals("0", db.pragma("PRAGMA auto_vacuum"))
        assertEquals("1", db.pragma("PRAGMA secure_delete"))
        val expectedRows = populateWithDeletionsAndAHeal(store)
        assertTrue("control: the marker is in the files before the deletion", markerCounts().values.sum() > 0)

        val deletion = store.deleteAllFaceData()

        assertEquals(FaceDataDeletion(rowsDeleted = expectedRows, scrubbed = false), deletion)
        assertFalse(store.hasFaceData())
        assertTrue(
            "control: the journal keeps the deleted pages, which is why the deletion is not reported scrubbed",
            markerCounts().getValue("$DATABASE_NAME-journal") > 0
        )
    }

    @Test
    fun `a reader holding the old snapshot makes the deletion incomplete and a repeat after it leaves finishes it`() =
        runBlocking {
            createFileWithoutAutoVacuum()
            val (db, store) = openProductionDatabase()
            assertEquals("wal", db.pragma("PRAGMA journal_mode"))
            populateWithDeletionsAndAHeal(store)
            val reader = SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
            )
            reader.beginReadSnapshot()
            try {
                val blocked = store.deleteAllFaceData()

                assertFalse("a reader on the old snapshot must stop the log being emptied", blocked.scrubbed)
                assertFalse(store.hasFaceData())
                assertTrue("the log still holds the old pages", markerCounts().values.sum() > 0)
            } finally {
                reader.endTransaction()
                reader.close()
            }

            val repeated = store.deleteAllFaceData()

            assertEquals(FaceDataDeletion(rowsDeleted = 0, scrubbed = true), repeated)
            assertEquals(emptyMap<String, Int>(), markerCounts().filterValues { it > 0 })
        }

    // Auto-vacuum NONE is the worst case: a deleted page stays in the file as a free page.
    // The platform's SQLite defaults to FULL and has already written the header when openOrCreateDatabase returns,
    // so the setting only takes effect through a VACUUM.
    private fun createFileWithoutAutoVacuum() {
        dbFile.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        raw.execSQL("PRAGMA auto_vacuum = 0")
        raw.execSQL("VACUUM")
        raw.close()
    }

    // beginTransactionNonExclusive takes the write lock, which would block the deletion's own transaction.
    // A deferred transaction and one read hold only a read snapshot, which is what a concurrent reader holds.
    // API 35's beginTransactionReadOnly is out: Robolectric's cached SDK jar is 30.
    private fun SQLiteDatabase.beginReadSnapshot() {
        val session = SQLiteDatabase::class.java.getDeclaredMethod("getThreadSession").apply { isAccessible = true }.invoke(this)
        session.javaClass
            .getMethod("beginTransaction", Int::class.java, SQLiteTransactionListener::class.java, Int::class.java, CancellationSignal::class.java)
            .invoke(session, TRANSACTION_MODE_DEFERRED, null, 0, null)
        rawQuery("SELECT COUNT(*) FROM media_index_face", null).use { it.moveToFirst() }
    }

    // Robolectric's SQLite reports journal_mode memory for a non-WAL file, where a device uses TRUNCATE.
    // Set it on the connection the store writes through, so the -journal file exists as it does on a device.
    private fun MediaIndexDatabase.useTruncateJournal() {
        openHelper.writableDatabase.query("PRAGMA journal_mode = TRUNCATE").use { it.moveToFirst() }
    }

    // PERSIST zeroes the journal's header at commit instead of deleting or truncating it, so the page copies stay.
    // SQLite refuses a mode change inside a transaction, so this runs outside one, on the connection the store writes through.
    private fun MediaIndexDatabase.usePersistJournal() {
        openHelper.writableDatabase.query("PRAGMA journal_mode = PERSIST").use { it.moveToFirst() }
    }

    private fun openProductionDatabase(): Pair<MediaIndexDatabase, RoomMediaIndexStore> {
        val db = MediaIndexDatabase.create(context)
        database = db
        return db to RoomMediaIndexStore(db)
    }

    // The primary connection answers inside a transaction; a pooled reader may not carry the pragma.
    private suspend fun MediaIndexDatabase.pragma(sql: String): String {
        return withTransaction {
            openHelper.writableDatabase.query(sql).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        }
    }

    // 40 images with two faces each; 10 are then re-extracted and one healed, so earlier deletions are covered.
    // Returns the Tier C rows that remain: 30 states and 60 faces for the 30 untouched images, and the healed one's.
    private suspend fun populateWithDeletionsAndAHeal(store: RoomMediaIndexStore): Int {
        val rows = (1..IMAGE_COUNT).map { row(it) }
        store.upsert(rows)
        store.writeFaceIndexResults(rows.map { faceWrite(it) })

        store.upsert(rows.take(REEXTRACTED_COUNT).map { it.copy(size = it.size + 1) })
        val healed = rows[REEXTRACTED_COUNT]
        store.healMediaUri(healed.mediaUri, healed.copy(mediaUri = "content://media/0123-4567/images/media/9999", volumeName = "0123-4567"))

        val untouched = IMAGE_COUNT - REEXTRACTED_COUNT
        return untouched * (1 + FACES_PER_IMAGE)
    }

    private fun faceWrite(row: MediaIndexEntry): FaceIndexWrite {
        val detections = (0 until FACES_PER_IMAGE).map {
            FaceDetection(FaceBox(0.1f * (it + 1), 0.1f, 0.1f * (it + 1) + 0.2f, 0.5f), FaceEmbedding(markerEmbeddingValues()))
        }
        return FaceIndexWrite(row.toMediaIdentity(), pipelineVersion = 1, outcome = FaceIndexOutcome.INDEXED, faces = detections)
    }

    private fun row(number: Int): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = "content://media/external_primary/images/media/$number",
            displayName = "photo$number.jpg",
            size = 1_000L + number,
            dateModified = 2_000L + number,
            volumeName = "external_primary",
            generationAdded = null,
            generationModified = null,
            capturedDateTakenEpochMillis = null,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )
    }

    // 128 floats, the recogniser's size: a fixed four-float pattern repeated, none of them NaN or infinite.
    private fun markerEmbeddingValues(): FloatArray {
        return FloatArray(EMBEDDING_LENGTH) { Float.fromBits(MARKER_BITS[it % MARKER_BITS.size]) }
    }

    private fun markerBytes(): ByteArray {
        return encodeFaceEmbedding(FaceEmbedding(FloatArray(MARKER_BITS.size) { Float.fromBits(MARKER_BITS[it]) }))
    }

    private fun markerCounts(): Map<String, Int> {
        val marker = markerBytes()
        val files = dbFile.parentFile?.listFiles { file -> file.name.startsWith(DATABASE_NAME) }.orEmpty()
        return files.associate { file -> file.name to countOccurrences(readFile(file), marker) }
    }

    // On Windows the open -shm has locked regions and cannot be read. It is the log's index of page numbers and
    // hashes, never page content, so an unreadable one is empty here; any other unreadable file is a failure.
    private fun readFile(file: File): ByteArray {
        return try {
            file.readBytes()
        } catch (e: IOException) {
            if (!file.name.endsWith("-shm")) throw e
            ByteArray(0)
        }
    }

    private fun countOccurrences(haystack: ByteArray, needle: ByteArray): Int {
        var count = 0
        var index = 0
        while (index <= haystack.size - needle.size) {
            var matches = true
            for (offset in needle.indices) {
                if (haystack[index + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                count += 1
                index += needle.size
            } else {
                index += 1
            }
        }
        return count
    }

    private companion object {
        const val DATABASE_NAME = "media_index.db"
        const val TRANSACTION_MODE_DEFERRED = 0
        const val IMAGE_COUNT = 40
        const val REEXTRACTED_COUNT = 10
        const val FACES_PER_IMAGE = 2
        const val EMBEDDING_LENGTH = 128
        const val LIVE_FACES = (IMAGE_COUNT - REEXTRACTED_COUNT) * FACES_PER_IMAGE
        const val MARKERS_PER_EMBEDDING = EMBEDDING_LENGTH / 4
        val MARKER_BITS = intArrayOf(0x3F5A9C31, 0x3E71D2A7, 0x3DB4E80F, 0x3F0C6D93)
    }
}
