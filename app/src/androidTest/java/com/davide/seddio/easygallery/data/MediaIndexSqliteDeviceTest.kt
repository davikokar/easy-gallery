package com.davide.seddio.easygallery.data

import android.app.ActivityManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteTransactionListener
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.EasyGalleryApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 3.7, G2 (items 1 to 3): what ADR-0018 Amendments 5 and 6 rely on, read from this phone's real SQLite rather than
 * Robolectric's. The same cases as `MediaIndexFaceDataScrubTest`, over a throwaway file-backed index opened with the
 * production callbacks ([MediaIndexThrowawayDatabase]) and filled with synthetic embeddings that all carry one byte
 * marker; each searches every `media_index.db*` file for it. The app's own index is touched by one method only, and
 * only to read pragmas. The one fixed line it logs, tag [TAG], holds pragma values and a few flags, never a row.
 *
 * `PERSIST` or `exclusive` locking in any reading fails a test with a message starting `REVISIT`: ADR-0018
 * Amendment 6's trigger for revisiting it, which goes to the maintainer and stops step 3.7.
 */
@RunWith(AndroidJUnit4::class)
class MediaIndexSqliteDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var throwaway: MediaIndexThrowawayDatabase? = null

    @After
    fun tearDown() {
        throwaway?.close()
        throwaway = null
    }

    @Test
    fun theModesAreRecordedAfterARealDeletionAndItLeavesNoMarkerInAnyFile() {
        runBlocking {
            val index = openThrowaway()
            populateWithDeletionsAndAHeal(index.store)
            assertTrue("control: the marker is in the files before the deletion", markerCounts(index).values.sum() > 0)

            val deletion = index.store.deleteAllFaceData()
            val modes = index.database.readModes()
            logModes("phone", modes, deletion.scrubbed)

            assertEquals("secure_delete must read back as on on the connection that writes", "1", modes.secureDelete)
            assertModesAreNotTheRevisitTrigger(modes)
            assertTrue("A deletion under the phone's own modes must report scrubbed: $modes", deletion.scrubbed)
            assertEquals(emptyMap<String, Int>(), markerCounts(index).filterValues { it > 0 })
            assertFalse(index.store.hasFaceData())
            index.database.close()
            assertEquals(emptyMap<String, Int>(), markerCounts(index).filterValues { it > 0 })
        }
    }

    @Test
    fun aRollbackJournalOnTruncateLeavesNoPageCopiesAfterTheDeletion() {
        runBlocking {
            val index = openThrowaway(prepare = MediaIndexThrowawayDatabase::createWithoutAutoVacuum)
            val db = index.database
            logModes("opened", db.readModes(), scrubbed = null)
            db.useRollbackJournal("TRUNCATE")
            val modes = db.readModes()
            logModes("truncateBeforeDeletion", modes, scrubbed = null)
            assertEquals("The journal must be forced to truncate", "truncate", modes.journalMode)
            assertEquals("The worst case is auto_vacuum none", "0", modes.autoVacuum)
            assertEquals("secure_delete must be on before the data is written, as production's open leaves it", "1", modes.secureDelete)
            populateWithDeletionsAndAHeal(index.store)
            assertTrue("control: the marker is in the files before the deletion", markerCounts(index).values.sum() > 0)

            val deletion = index.store.deleteAllFaceData()

            logModes("truncateAfterDeletion", db.readModes(), deletion.scrubbed)
            assertTrue("A deletion under truncate and normal locking must report scrubbed", deletion.scrubbed)
            assertEquals(emptyMap<String, Int>(), markerCounts(index).filterValues { it > 0 })
            db.close()
            assertEquals(emptyMap<String, Int>(), markerCounts(index).filterValues { it > 0 })
        }
    }

    // The control for the test above: it proves a marker in a rollback journal would be seen, and not reported scrubbed.
    @Test
    fun aRollbackJournalThatKeepsItsPageCopiesDeletesTheRowsButIsNotReportedScrubbed() {
        runBlocking {
            val index = openThrowaway(prepare = MediaIndexThrowawayDatabase::createWithoutAutoVacuum)
            val db = index.database
            db.useRollbackJournal("PERSIST")
            assertEquals("The journal must be forced to persist", "persist", db.readModes().journalMode)
            populateWithDeletionsAndAHeal(index.store)
            logModes("persistBeforeDeletion", db.readModes(), scrubbed = null)
            assertTrue("control: the marker is in the files before the deletion", markerCounts(index).values.sum() > 0)

            val deletion = index.store.deleteAllFaceData()

            logModes("persistAfterDeletion", db.readModes(), deletion.scrubbed)
            assertFalse("A deletion under persist must not report scrubbed", deletion.scrubbed)
            assertFalse(index.store.hasFaceData())
            assertTrue(
                "control: the journal keeps the deleted pages, which is why the deletion is not reported scrubbed: " +
                    "${markerCounts(index)} sizes=${index.files().associate { it.name to it.length() }}",
                (markerCounts(index)["${MediaIndexThrowawayDatabase.DATABASE_NAME}-journal"] ?: 0) > 0
            )
        }
    }

    @Test
    fun aCheckpointBlockedByARealConcurrentReaderIsIncompleteAndARepeatFinishesIt() {
        runBlocking {
            val index = openThrowaway(prepare = MediaIndexThrowawayDatabase::createWithoutAutoVacuum)
            val modes = index.database.readModes()
            logModes("blocked", modes, scrubbed = null)
            assertEquals("ENVIRONMENT: this case needs the index in a write-ahead log, as the phone opens it", "wal", modes.journalMode)
            populateWithDeletionsAndAHeal(index.store)
            val reader = SQLiteDatabase.openDatabase(
                index.databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
            )
            reader.beginReadSnapshot()
            try {
                val blocked = index.store.deleteAllFaceData()

                assertFalse("a reader on the old snapshot must stop the log being emptied", blocked.scrubbed)
                assertFalse(index.store.hasFaceData())
                assertTrue("the log or the file still holds the old pages", markerCounts(index).values.sum() > 0)
            } finally {
                reader.endTransaction()
                reader.close()
            }

            val repeated = index.store.deleteAllFaceData()

            assertEquals(FaceDataDeletion(rowsDeleted = 0, scrubbed = true), repeated)
            assertEquals(emptyMap<String, Int>(), markerCounts(index).filterValues { it > 0 })
        }
    }

    // Reads the app's own index and writes nothing: the modes it opens under, and whether it holds any Tier C row.
    @Test
    fun theProductionIndexModesAreRecorded() {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        runBlocking {
            val modes = app.mediaIndexDatabase.readModes()
            val hasFaceData = app.mediaIndexStore.hasFaceData()
            logModes("production", modes, scrubbed = null, hasFaceData = hasFaceData)

            assertEquals("secure_delete must read back as on on the connection that writes", "1", modes.secureDelete)
            assertModesAreNotTheRevisitTrigger(modes)
            if (InstrumentationRegistry.getArguments().getString(EXPECT_NO_FACE_DATA_ARGUMENT) == "true") {
                assertFalse("The app's own index still holds a Tier C row", hasFaceData)
            }
        }
    }

    // ---- the pragmas ----

    private data class Modes(val journalMode: String, val lockingMode: String, val autoVacuum: String, val secureDelete: String)

    // The primary connection answers inside a transaction; a pooled reader may not carry the pragma.
    private suspend fun MediaIndexDatabase.readModes(): Modes = withTransaction {
        val db = openHelper.writableDatabase
        Modes(
            journalMode = db.pragmaText("PRAGMA journal_mode"),
            lockingMode = db.pragmaText("PRAGMA locking_mode"),
            autoVacuum = db.pragmaText("PRAGMA auto_vacuum"),
            secureDelete = db.pragmaText("PRAGMA secure_delete")
        )
    }

    private fun SupportSQLiteDatabase.pragmaText(sql: String): String =
        query(sql).use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }

    // SQLite refuses a journal-mode change inside a transaction, so this runs outside one, on the connection the store writes through.
    private fun SupportSQLiteDatabase.setJournalMode(mode: String) {
        query("PRAGMA journal_mode = $mode").use { it.moveToFirst() }
    }

    private suspend fun MediaIndexDatabase.useRollbackJournal(mode: String) {
        leaveWriteAheadLog()
        openHelper.writableDatabase.setJournalMode(mode)
        // Leaving the log reopens the connection and drops the pragma production's onOpen set; a low-RAM open keeps it.
        withTransaction { check(openHelper.writableDatabase.enableSecureDelete()) { "secure_delete could not be enabled" } }
        // A small page cache makes SQLite spill, which is what makes it write a journal: on a phone whose file system offers
        // atomic batch writes (the SM-G990B's f2fs), a transaction that fits in the cache never touches the journal file.
        openHelper.writableDatabase.query("PRAGMA cache_size = $SMALL_CACHE_PAGES").use { it.moveToFirst() }
    }

    // The framework refuses the switch while any connection of the pool is in use, and Room's own work may still be draining.
    private fun MediaIndexDatabase.leaveWriteAheadLog() {
        var attempt = 0
        while (true) {
            try {
                openHelper.writableDatabase.disableWriteAheadLogging()
                return
            } catch (busy: IllegalStateException) {
                if (++attempt >= WAL_SWITCH_ATTEMPTS) throw busy
                SystemClock.sleep(WAL_SWITCH_RETRY_MILLIS)
            }
        }
    }

    private fun assertModesAreNotTheRevisitTrigger(modes: Modes) {
        assertTrue("REVISIT: journal_mode persist keeps page copies (ADR-0018 Amendment 6): $modes", modes.journalMode.lowercase() != "persist")
        assertTrue("REVISIT: exclusive locking may keep a rollback journal (ADR-0018 Amendment 6): $modes", modes.lockingMode.lowercase() != "exclusive")
    }

    private fun logModes(label: String, modes: Modes, scrubbed: Boolean?, hasFaceData: Boolean? = null) {
        val lowRam = context.getSystemService(ActivityManager::class.java).isLowRamDevice
        Log.i(
            TAG,
            "modes label=$label journal_mode=${modes.journalMode} locking_mode=${modes.lockingMode} " +
                "auto_vacuum=${modes.autoVacuum} secure_delete=${modes.secureDelete} lowRamDevice=$lowRam " +
                "sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL} scrubbed=${scrubbed ?: "na"} hasFaceData=${hasFaceData ?: "na"}"
        )
    }

    // ---- a real concurrent reader ----

    // beginTransactionNonExclusive takes the write lock, which would block the deletion's own transaction. A deferred
    // read-only transaction and one read hold only a read snapshot, which is what a concurrent reader holds.
    private fun SQLiteDatabase.beginReadSnapshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            beginTransactionReadOnly()
        } else {
            val session = SQLiteDatabase::class.java.getDeclaredMethod("getThreadSession").apply { isAccessible = true }.invoke(this)
            session.javaClass
                .getMethod("beginTransaction", Int::class.java, SQLiteTransactionListener::class.java, Int::class.java, CancellationSignal::class.java)
                .invoke(session, TRANSACTION_MODE_DEFERRED, null, 0, null)
        }
        rawQuery("SELECT COUNT(*) FROM media_index_face", null).use { it.moveToFirst() }
    }

    // ---- the synthetic index ----

    private fun openThrowaway(prepare: (java.io.File) -> Unit = {}): MediaIndexThrowawayDatabase =
        MediaIndexThrowawayDatabase.open(context, prepare).also { throwaway = it }

    private fun markerCounts(index: MediaIndexThrowawayDatabase): Map<String, Int> =
        MediaIndexThrowawayDatabase.markerCounts(index.files(), markerBytes())

    // 40 images with two faces each; 10 are then re-extracted and one healed, so earlier deletions are covered.
    private suspend fun populateWithDeletionsAndAHeal(store: RoomMediaIndexStore) {
        val rows = (1..IMAGE_COUNT).map { row(it) }
        store.upsert(rows)
        store.writeFaceIndexResults(rows.map { faceWrite(it) }, faceDataEnabled = { true })

        store.upsert(rows.take(REEXTRACTED_COUNT).map { it.copy(size = it.size + 1) })
        val healed = rows[REEXTRACTED_COUNT]
        store.healMediaUri(healed.mediaUri, healed.copy(mediaUri = "content://media/0123-4567/images/media/9999", volumeName = "0123-4567"))
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
        return FloatArray(FACE_EMBEDDING_SIZE) { Float.fromBits(MARKER_BITS[it % MARKER_BITS.size]) }
    }

    private fun markerBytes(): ByteArray {
        return encodeFaceEmbedding(FaceEmbedding(FloatArray(MARKER_BITS.size) { Float.fromBits(MARKER_BITS[it]) }))
    }

    private companion object {
        const val TAG = "MediaIndexSqliteDeviceTest"
        const val EXPECT_NO_FACE_DATA_ARGUMENT = "mediaIndexExpectNoFaceData"
        const val TRANSACTION_MODE_DEFERRED = 0
        const val WAL_SWITCH_ATTEMPTS = 40
        const val SMALL_CACHE_PAGES = 10
        const val WAL_SWITCH_RETRY_MILLIS = 50L
        const val IMAGE_COUNT = 40
        const val REEXTRACTED_COUNT = 10
        const val FACES_PER_IMAGE = 2
        val MARKER_BITS = intArrayOf(0x3F5A9C31, 0x3E71D2A7, 0x3DB4E80F, 0x3F0C6D93)
    }
}
