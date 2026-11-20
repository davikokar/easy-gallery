package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pass and the start-up deletion, on the in-memory store and a fake pipeline (plan 3.3, Task 1, criteria 5, 6, 8). */
class FaceIndexPassTest {

    private val store = InMemoryMediaIndexStore()
    private val setting = InMemoryFaceDataSettingStore(initiallyEnabled = true)
    private val repository = FakeMediaRepository()
    private val log = mutableListOf<String>()
    private var now = 0L
    private var loads = 0
    private val pipeline = FakeFacePipeline()

    // ---- criterion 5: the pass ----

    @Test
    fun `an image is written once as indexed with its faces as clamped fractions and no attempt left`() = runTest {
        val uri = seedImage(11)
        pipeline.onAnalyse = {
            fakeOutput(
                width = 1000,
                height = 800,
                faces = listOf(RawFace(-20f, 100f, 180f, 300f, FaceEmbedding(floatArrayOf(1f, 2f, 3f))))
            )
        }

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(1, 0, 0, FaceIndexPassStop.COMPLETED), result)
        assertEquals(FaceIndexState(uri, FACE_PIPELINE_VERSION, FaceIndexOutcome.INDEXED), store.getFaceIndexState(uri))
        val face = store.listFaces(uri).single()
        assertEquals(FaceBox(0f, 0.125f, 0.18f, 0.375f), face.box)
        assertEquals(FaceEmbedding(floatArrayOf(1f, 2f, 3f)), face.embedding)
        assertEquals(emptyMap<String, Int>(), pendingAttempts())
        assertEquals(listOf(uri), pipeline.analysed)
    }

    @Test
    fun `a video and a gif never get a state`() = runTest {
        val video = seedImage(11, type = MediaType.VIDEO, collection = "video")
        val gif = seedImage(12, type = MediaType.GIF)
        // The gif's row is in the images collection, so only the live type keeps it out.
        val image = seedImage(13)

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(1, 0, 1, FaceIndexPassStop.COMPLETED), result)
        assertNull(store.getFaceIndexState(video))
        assertNull(store.getFaceIndexState(gif))
        assertNotNull(store.getFaceIndexState(image))
        assertEquals(listOf(image), pipeline.analysed)
        assertEquals(emptyMap<String, Int>(), pendingAttempts().filterKeys { it != gif })
    }

    @Test
    fun `a candidate that is gone from live media or whose live triple differs is skipped and never decoded`() = runTest {
        val gone = seedImage(11, live = false)
        val changedSize = seedImage(12, liveSize = 999L)
        val changedName = seedImage(13, liveName = "renamed.jpg")
        val changedDate = seedImage(14, liveDateModified = 999L)
        val current = seedImage(15)

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(1, 0, 4, FaceIndexPassStop.COMPLETED), result)
        listOf(gone, changedSize, changedName, changedDate).forEach { assertNull(store.getFaceIndexState(it)) }
        assertEquals(listOf(current), pipeline.analysed)
        // Skipped rows stay pending for reconciliation or Tier A to move first, with no attempt recorded.
        assertEquals(listOf(gone, changedSize, changedName, changedDate).associateWith { 0 }, pendingAttempts())
    }

    @Test
    fun `a decode failure and an inference failure are each written failed at once with no face`() = runTest {
        val undecodable = seedImage(11)
        val refused = seedImage(12)
        pipeline.onAnalyse = { uri ->
            throw FacePipelineException(if (uri == undecodable) FacePipelineFailure.DECODE else FacePipelineFailure.INFERENCE)
        }

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(0, 2, 0, FaceIndexPassStop.COMPLETED), result)
        listOf(undecodable, refused).forEach {
            assertEquals(FaceIndexState(it, FACE_PIPELINE_VERSION, FaceIndexOutcome.FAILED), store.getFaceIndexState(it))
            assertTrue(store.listFaces(it).isEmpty())
        }
        assertEquals(emptyMap<String, Int>(), pendingAttempts())
        assertEquals(listOf(undecodable, refused), pipeline.analysed)
    }

    @Test
    fun `an output reporting an empty frame or a zero sized image is also written failed`() = runTest {
        val uri = seedImage(11)
        pipeline.onAnalyse = { FacePipelineOutput(imageWidth = 0, imageHeight = 0, faces = emptyList()) }

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(0, 1, 0, FaceIndexPassStop.COMPLETED), result)
        assertEquals(FaceIndexOutcome.FAILED, store.getFaceIndexState(uri)!!.outcome)
    }

    @Test
    fun `an image tried the bound of times is written failed without decoding`() = runTest {
        val uri = seedImage(11)
        repeat(FACE_ATTEMPT_BOUND) { recordAttempt(uri) }

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(0, 1, 0, FaceIndexPassStop.COMPLETED), result)
        assertEquals(FaceIndexOutcome.FAILED, store.getFaceIndexState(uri)!!.outcome)
        assertTrue(pipeline.analysed.isEmpty())
        assertEquals(emptyMap<String, Int>(), pendingAttempts())
    }

    @Test
    fun `an image tried one time fewer than the bound is still decoded and a crash leaves it counted`() = runTest {
        val uri = seedImage(11)
        repeat(FACE_ATTEMPT_BOUND - 1) { recordAttempt(uri) }
        pipeline.onAnalyse = { throw IllegalStateException("native crash stand-in") }

        val failure = runCatching { pass().run(now) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(uri), pipeline.analysed)
        assertEquals(mapOf(uri to FACE_ATTEMPT_BOUND), pendingAttempts())
        assertNull(store.getFaceIndexState(uri))
    }

    @Test
    fun `an unopenable file skips the image and clears its attempt`() = runTest {
        val missing = seedImage(11)
        val unreadable = seedImage(12)
        val fine = seedImage(13)
        pipeline.onAnalyse = { uri ->
            when (uri) {
                missing -> throw FileNotFoundException()
                unreadable -> throw IOException()
                else -> fakeOutput()
            }
        }

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(1, 0, 2, FaceIndexPassStop.COMPLETED), result)
        assertNull(store.getFaceIndexState(missing))
        assertNull(store.getFaceIndexState(unreadable))
        assertNotNull(store.getFaceIndexState(fine))
        assertEquals(mapOf(missing to 0, unreadable to 0), pendingAttempts())
    }

    @Test
    fun `a refused read stops the pass and clears the attempt of its image`() = runTest {
        val first = seedImage(11)
        val refused = seedImage(12)
        val untouched = seedImage(13)
        pipeline.onAnalyse = { uri -> if (uri == refused) throw SecurityException() else fakeOutput() }

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(1, 0, 0, FaceIndexPassStop.PERMISSION), result)
        assertNotNull(store.getFaceIndexState(first))
        assertNull(store.getFaceIndexState(refused))
        assertNull(store.getFaceIndexState(untouched))
        assertEquals(mapOf(refused to 0, untouched to 0), pendingAttempts())
        assertEquals(listOf(first, refused), pipeline.analysed)
    }

    @Test
    fun `a refused listing of live media stops the pass before any image`() = runTest {
        val uri = seedImage(11)
        val refusing = object : MediaRepository by repository {
            override suspend fun getAllMedia(): List<MediaItem> = throw SecurityException()
        }

        val result = pass(media = refusing).run(now)

        assertEquals(FaceIndexPassResult(0, 0, 0, FaceIndexPassStop.PERMISSION), result)
        assertEquals(mapOf(uri to 0), pendingAttempts())
        assertEquals(0, loads)
    }

    @Test
    fun `any other failure propagates and the pipeline is still closed`() = runTest {
        seedImage(11)
        pipeline.onAnalyse = { throw IllegalStateException("not a file problem") }

        val failure = runCatching { pass().run(now) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(1, pipeline.closeCount)
        assertTrue("no summary line for a run that threw", log.isEmpty())
    }

    @Test
    fun `a build with no pipeline propagates and leaves no attempt behind`() = runTest {
        val uri = seedImage(11)

        val failure = runCatching {
            pass(pipelineLoader = { throw FacePipelineUnavailableException() }).run(now)
        }.exceptionOrNull()

        assertTrue(failure is FacePipelineUnavailableException)
        assertEquals(mapOf(uri to 0), pendingAttempts())
    }

    @Test
    fun `a cancellation during the decode leaves no attempt and no write for its image`() = runTest {
        val uri = seedImage(11)
        val untouched = seedImage(12)
        lateinit var running: Job
        pipeline.onAnalyse = {
            running.cancel()
            fakeOutput()
        }

        running = launch { pass().run(now) }
        running.join()

        assertTrue(running.isCancelled)
        assertNull(store.getFaceIndexState(uri))
        assertTrue(store.listFaces(uri).isEmpty())
        assertFalse(store.hasFaceData())
        assertEquals(mapOf(uri to 0, untouched to 0), pendingAttempts())
        assertEquals(listOf(uri), pipeline.analysed)
        assertEquals(1, pipeline.closeCount)
    }

    @Test
    fun `the setting turning off during an image stops the pass and writes nothing for that image`() = runTest {
        val first = seedImage(11)
        val inFlight = seedImage(12)
        val untouched = seedImage(13)
        pipeline.onAnalyse = { uri ->
            if (uri == inFlight) setting.setEnabled(false)
            fakeOutput()
        }

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(1, 0, 0, FaceIndexPassStop.SETTING_OFF), result)
        assertNotNull(store.getFaceIndexState(first))
        assertNull(store.getFaceIndexState(inFlight))
        assertNull(store.getFaceIndexState(untouched))
        assertTrue(store.listFaces(inFlight).isEmpty())
        assertEquals(mapOf(inFlight to 0, untouched to 0), pendingAttempts())
        assertEquals(listOf(first, inFlight), pipeline.analysed)
    }

    @Test
    fun `the setting off before the first image stops without loading the pipeline or recording an attempt`() = runTest {
        val uri = seedImage(11)
        setting.setEnabled(false)

        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(0, 0, 0, FaceIndexPassStop.SETTING_OFF), result)
        assertEquals(0, loads)
        assertEquals(mapOf(uri to 0), pendingAttempts())
        assertFalse(store.hasFaceData())
    }

    @Test
    fun `the budget stops the pass and the next run resumes with the images it left`() = runTest {
        val uris = (11..15).map { seedImage(it) }
        pipeline.onAnalyse = {
            now += 60_000L
            fakeOutput()
        }
        val budget = 150_000L

        val first = pass(budgetMillis = budget).run(workStartedAtMillis = now)
        val analysedByFirst = pipeline.analysed.toList()
        val second = pass(budgetMillis = budget).run(workStartedAtMillis = now)

        assertEquals(FaceIndexPassResult(3, 0, 0, FaceIndexPassStop.BUDGET), first)
        assertEquals(uris.take(3), analysedByFirst)
        assertEquals(FaceIndexPassResult(2, 0, 0, FaceIndexPassStop.COMPLETED), second)
        assertEquals("the second run decodes only what the first left", uris, pipeline.analysed)
        uris.forEach { assertNotNull(store.getFaceIndexState(it)) }
    }

    @Test
    fun `the budget counts from the start of the worker not from the start of the pass`() = runTest {
        val uri = seedImage(11)
        now = 8 * 60 * 1000L

        val result = pass().run(workStartedAtMillis = 0L)

        assertEquals(FaceIndexPassResult(0, 0, 0, FaceIndexPassStop.BUDGET), result)
        assertTrue(pipeline.analysed.isEmpty())
        assertEquals(mapOf(uri to 0), pendingAttempts())
    }

    @Test
    fun `more images than a page are all processed once and in order`() = runTest {
        val uris = (11..15).map { seedImage(it) }

        val result = pass(pageSize = 2).run(now)

        assertEquals(FaceIndexPassResult(5, 0, 0, FaceIndexPassStop.COMPLETED), result)
        assertEquals(uris, pipeline.analysed)
    }

    @Test
    fun `a stale row in the middle of a page does not stop the pages after it`() = runTest {
        seedImage(11, live = false)
        seedImage(12, live = false)
        val last = seedImage(13)

        val result = pass(pageSize = 2).run(now)

        assertEquals(FaceIndexPassResult(1, 0, 2, FaceIndexPassStop.COMPLETED), result)
        assertEquals(listOf(last), pipeline.analysed)
    }

    @Test
    fun `a run with nothing pending never loads the pipeline and still reports`() = runTest {
        val result = pass().run(now)

        assertEquals(FaceIndexPassResult(0, 0, 0, FaceIndexPassStop.COMPLETED), result)
        assertEquals(0, loads)
        assertEquals(listOf("face pass finished images=0 indexed=0 failed=0 skipped=0 stop=COMPLETED"), log)
    }

    @Test
    fun `the pipeline is loaded once for a whole run and closed at its end`() = runTest {
        (11..13).forEach { seedImage(it) }

        pass().run(now)

        assertEquals(1, loads)
        assertEquals(1, pipeline.closeCount)
    }

    @Test
    fun `a pipeline that fails to close does not fail a finished pass`() = runTest {
        seedImage(11)
        pipeline.closeFailure = IllegalStateException("close")

        val result = pass().run(now)

        assertEquals(1, result.indexedImages)
    }

    @Test
    fun `a face narrower or shorter than 40 pixels is dropped and one of exactly 40 is kept`() = runTest {
        val uri = seedImage(11)
        pipeline.onAnalyse = {
            fakeOutput(
                width = 1000,
                height = 800,
                faces = listOf(
                    fakeFace(0f, 0f, 39.9f, 100f),
                    fakeFace(100f, 100f, 200f, 139.9f),
                    fakeFace(300f, 300f, 340f, 340f),
                    fakeFace(500f, 100f, 600f, 200f)
                )
            )
        }

        pass().run(now)

        val boxes = store.listFaces(uri).map { it.box }
        assertEquals(listOf(FaceBox(0.3f, 0.375f, 0.34f, 0.425f), FaceBox(0.5f, 0.125f, 0.6f, 0.25f)), boxes)
    }

    @Test
    fun `an image whose faces are all dropped is still done with no face`() = runTest {
        val uri = seedImage(11)
        pipeline.onAnalyse = { fakeOutput(faces = listOf(fakeFace(0f, 0f, 10f, 10f))) }

        val result = pass().run(now)

        assertEquals(1, result.indexedImages)
        assertEquals(FaceIndexOutcome.INDEXED, store.getFaceIndexState(uri)!!.outcome)
        assertTrue(store.listFaces(uri).isEmpty())
    }

    @Test
    fun `the box geometry clamps to the image and drops what is empty or not a number`() {
        val width = 1000
        val height = 500

        fun box(left: Float, top: Float, right: Float, bottom: Float) =
            faceDetectionOf(fakeFace(left, top, right, bottom), width, height)?.box

        assertEquals(FaceBox(0f, 0f, 1f, 1f), box(-100f, -100f, 1100f, 600f))
        assertEquals(FaceBox(0.9f, 0.5f, 1f, 1f), box(900f, 250f, 1100f, 600f))
        assertNull("wholly outside once clamped", box(1000f, 0f, 1200f, 300f))
        assertNull("wholly outside above", box(100f, -300f, 300f, -50f))
        assertNull(box(Float.NaN, 0f, 100f, 100f))
        assertNull(box(0f, 0f, Float.NaN, 100f))
        assertNull(faceDetectionOf(fakeFace(0f, 0f, 100f, 100f), 0, 500))
        assertNull(faceDetectionOf(fakeFace(0f, 0f, 100f, 100f), 1000, 0))
    }

    @Test
    fun `the size cut-off is judged on the detected box before it is clamped`() {
        // 30 px of the face lie inside the image, but the detector saw 100: it is kept (decision 46).
        val kept = faceDetectionOf(fakeFace(-70f, 0f, 30f, 100f), 1000, 500)
        // 100 px wide as clamped, but the detector saw 39.9 px.
        val dropped = faceDetectionOf(fakeFace(0f, 0f, 39.9f, 100f), 1000, 500)

        assertNotNull(kept)
        assertNull(dropped)
    }

    // ---- criterion 6: the logger rule (ADR-0024 §6) ----

    @Test
    fun `no line the pass or the deletion emits holds a uri, a name, a box or an embedding value`() = runTest {
        val indexed = seedImage(48151623, name = LEAK_NAME)
        val undecodable = seedImage(48151624, name = LEAK_NAME)
        val unopenable = seedImage(48151625, name = LEAK_NAME)
        val refused = seedImage(48151626, name = LEAK_NAME)
        pipeline.onAnalyse = { uri ->
            when (uri) {
                indexed -> fakeOutput(faces = listOf(RawFace(311.7125f, 222.3125f, 511.9375f, 433.4375f, LEAK_EMBEDDING)))
                undecodable -> throw FacePipelineException(FacePipelineFailure.DECODE, "cannot decode $uri $LEAK_NAME")
                unopenable -> throw IOException("cannot open $uri")
                else -> throw SecurityException("refused $uri $LEAK_NAME")
            }
        }
        val emitted = mutableListOf<String>()
        val recording = FaceIndexLog { emitted += it }

        pass(logTo = recording).run(now)
        // A second run that throws something the pass does not handle: the caller sees it, the log does not.
        seedImage(48151627, name = LEAK_NAME)
        pipeline.onAnalyse = { uri -> throw IllegalStateException("boom $uri $LEAK_NAME") }
        val surfaced = runCatching { pass(logTo = recording).run(now) }.exceptionOrNull()
        assertTrue(surfaced is IllegalStateException)
        pipeline.onAnalyse = { fakeOutput() }
        pass(logTo = recording).run(now)
        setting.setEnabled(false)
        val failingStore = object : MediaIndexStore by store {
            override suspend fun deleteAllFaceData(): FaceDataDeletion =
                throw IllegalStateException("scrub failed for $indexed $LEAK_NAME")
        }
        FaceDataStartupDeletion(failingStore, setting, recording).run()
        FaceDataStartupDeletion(store, setting, recording).run()

        // Two finished passes, the failed deletion and the one that ran; the run that threw says nothing.
        assertEquals(4, emitted.size)
        FaceLogRules.assertFixedSet(emitted)
        FaceLogRules.assertNoneOf(emitted, listOf(indexed, undecodable, unopenable, refused, LEAK_NAME) + LEAK_VALUES)
    }

    @Test
    fun `the data classes the pass handles print nothing of what they hold`() {
        val face = RawFace(311.7125f, 222.3125f, 511.9375f, 433.4375f, LEAK_EMBEDDING)

        val printed = listOf(
            face.toString(),
            FacePipelineOutput(1000, 800, listOf(face)).toString(),
            FaceSelfTestResult(true, floatArrayOf(0.13579f)).toString(),
            PendingFaceImage(mediaIdentityOf(LEAK_URI, LEAK_NAME, 1L, 2L), 1).toString()
        )

        FaceLogRules.assertNoneOf(printed, listOf(LEAK_URI, LEAK_NAME) + LEAK_VALUES)
    }

    // ---- criterion 8: the start-up deletion ----

    @Test
    fun `the deletion runs while the setting reads off whatever hasFaceData says`() = runTest {
        setting.setEnabled(false)
        val counting = CountingDeletionStore(store)
        assertFalse(counting.hasFaceData())

        val result = FaceDataStartupDeletion(counting, setting, { log += it }).run()

        assertEquals(1, counting.deletions)
        assertEquals(FaceDataDeletion(rowsDeleted = 0, scrubbed = true), result)
        assertEquals(listOf("face data deletion ran scrubbed=true"), log)
    }

    @Test
    fun `the deletion removes the face data that is there`() = runTest {
        val uri = seedImage(11)
        pass().run(now)
        assertTrue(store.hasFaceData())
        setting.setEnabled(false)

        val result = FaceDataStartupDeletion(store, setting, { log += it }).run()

        assertEquals(2, result!!.rowsDeleted)
        assertFalse(store.hasFaceData())
        assertNull(store.getFaceIndexState(uri))
    }

    @Test
    fun `the deletion does nothing while the setting reads on`() = runTest {
        seedImage(11)
        pass().run(now)
        log.clear()
        val counting = CountingDeletionStore(store)

        val result = FaceDataStartupDeletion(counting, setting, { log += it }).run()

        assertNull(result)
        assertEquals(0, counting.deletions)
        assertTrue(store.hasFaceData())
        assertTrue(log.isEmpty())
    }

    @Test
    fun `not scrubbed is a result, is reported once and is not retried`() = runTest {
        setting.setEnabled(false)
        val counting = CountingDeletionStore(store, scrubbed = false)

        val result = FaceDataStartupDeletion(counting, setting, { log += it }).run()

        assertEquals(FaceDataDeletion(rowsDeleted = 0, scrubbed = false), result)
        assertEquals(1, counting.deletions)
        assertEquals(listOf("face data deletion ran scrubbed=false"), log)
    }

    @Test
    fun `a thrown error neither propagates nor is retried and its message is not logged`() = runTest {
        setting.setEnabled(false)
        val counting = CountingDeletionStore(store, failure = IllegalStateException("secure_delete said $LEAK_URI"))

        val result = FaceDataStartupDeletion(counting, setting, { log += it }).run()

        assertNull(result)
        assertEquals(1, counting.deletions)
        assertEquals(listOf("face data deletion failed"), log)
    }

    @Test
    fun `a cancellation is not swallowed by the deletion`() = runTest {
        setting.setEnabled(false)
        val counting = CountingDeletionStore(store, failure = CancellationException("cancelled"))

        val failure = runCatching { FaceDataStartupDeletion(counting, setting, { log += it }).run() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `the setting store is read at the moment of the run and not before`() = runTest {
        val counting = CountingDeletionStore(store)
        val deletion = FaceDataStartupDeletion(counting, setting, { log += it })

        assertNull(deletion.run())
        setting.setEnabled(false)
        assertEquals(FaceDataDeletion(0, true), deletion.run())
        assertEquals(1, counting.deletions)
    }

    // ---- fixtures ----

    private fun pass(
        media: MediaRepository = repository,
        pipelineLoader: () -> FacePipeline = { loads += 1; pipeline },
        budgetMillis: Long = FACE_PASS_BUDGET_MILLIS,
        pageSize: Int = FACE_PASS_PAGE_SIZE,
        logTo: FaceIndexLog = FaceIndexLog { log += it }
    ) = FaceIndexPass(
        store = store,
        mediaRepository = media,
        settingStore = setting,
        pipelineLoader = pipelineLoader,
        log = logTo,
        clock = { now },
        budgetMillis = budgetMillis,
        pageSize = pageSize
    )

    // The index row and the live item share one identity unless a live* argument says the file changed.
    private suspend fun seedImage(
        id: Int,
        type: MediaType = MediaType.IMAGE,
        collection: String = "images",
        name: String = "photo$id.jpg",
        live: Boolean = true,
        liveName: String = name,
        liveSize: Long = 10L,
        liveDateModified: Long = 20L
    ): String {
        val uri = "content://media/external/$collection/media/$id"
        store.upsert(
            listOf(
                MediaIndexEntry(
                    mediaUri = uri,
                    displayName = name,
                    size = 10L,
                    dateModified = 20L,
                    volumeName = "external_primary",
                    generationAdded = 1L,
                    generationModified = 2L,
                    capturedDateTakenEpochMillis = null,
                    capturedLatitude = null,
                    capturedLongitude = null,
                    capturedWidth = null,
                    capturedHeight = null,
                    derivedCountryCode = null,
                    derivedRegionCode = null,
                    derivedPlaceName = null
                )
            )
        )
        if (live) {
            val liveUri = mockk<Uri>()
            every { liveUri.toString() } returns uri
            repository.mediaItems += MediaItem(
                uri = liveUri,
                name = liveName,
                dateAdded = 1L,
                dateModified = liveDateModified,
                size = liveSize,
                type = type,
                bucketName = "Camera",
                folderPath = "/storage/emulated/0/DCIM/Camera"
            )
        }
        return uri
    }

    private suspend fun recordAttempt(uri: String) {
        val row = store.getByMediaUri(uri)!!
        store.recordFaceIndexAttempt(row.toMediaIdentity(), FACE_PIPELINE_VERSION) { true }
    }

    // Attempts are only observable through the pending list: a done image drops out of it, with its attempt cleared.
    private suspend fun pendingAttempts(): Map<String, Int> =
        store.listPendingFaceImages(FACE_PIPELINE_VERSION, null, 1_000).associate { it.identity.mediaUri to it.attempts }

    private class CountingDeletionStore(
        private val inner: InMemoryMediaIndexStore,
        private val scrubbed: Boolean = true,
        private val failure: Throwable? = null
    ) : MediaIndexStore by inner {
        var deletions = 0

        override suspend fun deleteAllFaceData(): FaceDataDeletion {
            deletions += 1
            failure?.let { throw it }
            val real = inner.deleteAllFaceData()
            return real.copy(scrubbed = scrubbed)
        }
    }

    private companion object {
        const val LEAK_NAME = "LEAKCHECK-Maria_Rossi-4242.jpg"
        const val LEAK_URI = "content://media/external/images/media/48151623"
        val LEAK_EMBEDDING = FaceEmbedding(floatArrayOf(0.13579f, 0.2468f, 0.97531f))

        // The rendered forms of the box (pixels and fractions) and of the embedding values.
        val LEAK_VALUES = listOf("311.7", "222.3", "511.9", "433.4", "0.3117", "0.2223", "0.13579", "0.2468", "0.97531")
    }
}

/** What a face-path log line may be, written independently of the production helpers that build them. */
internal object FaceLogRules {
    private val FIXED_SET = listOf(
        Regex("^face pass finished images=\\d+ indexed=\\d+ failed=\\d+ skipped=\\d+ stop=(COMPLETED|SETTING_OFF|BUDGET|PERMISSION)$"),
        Regex("^face pass not run reason=(SETTING_OFF|INCAPABLE|DISCLOSURE_NOT_SHOWN|BUSY)$"),
        Regex("^face capability checked capable=(true|false)$"),
        Regex("^face data deletion ran scrubbed=(true|false)$"),
        Regex("^face pass failed$"),
        Regex("^face data deletion failed$")
    )

    fun assertFixedSet(lines: List<String>) {
        lines.forEach { line ->
            assertTrue("not in the fixed set of face log lines", FIXED_SET.any { it.matches(line) })
        }
    }

    // The message names the position of the match, never the forbidden text itself.
    fun assertNoneOf(lines: List<String>, forbidden: List<String>) {
        lines.forEachIndexed { index, line ->
            forbidden.forEachIndexed { which, text ->
                assertFalse("line $index holds forbidden fixture #$which", line.contains(text))
            }
        }
    }
}

/** A pipeline that records what it was asked and lets each test say what happens. */
internal class FakeFacePipeline : FacePipeline {
    var onAnalyse: (String) -> FacePipelineOutput = { fakeOutput() }
    var onSelfTest: () -> FaceSelfTestResult = { validSelfTest() }
    var closeFailure: Throwable? = null
    val analysed = mutableListOf<String>()
    var closeCount = 0

    override fun analyse(mediaUri: String): FacePipelineOutput {
        analysed += mediaUri
        return onAnalyse(mediaUri)
    }

    override fun selfTest(): FaceSelfTestResult = onSelfTest()

    override fun close() {
        closeCount += 1
        closeFailure?.let { throw it }
    }
}

internal fun fakeFace(left: Float, top: Float, right: Float, bottom: Float) =
    RawFace(left, top, right, bottom, FaceEmbedding(floatArrayOf(0.5f, -0.5f, 0.25f)))

internal fun fakeOutput(
    width: Int = 1000,
    height: Int = 800,
    faces: List<RawFace> = listOf(fakeFace(100f, 100f, 300f, 300f))
) = FacePipelineOutput(width, height, faces)

internal fun validSelfTest() = FaceSelfTestResult(detectionOutputValid = true, embedding = floatArrayOf(0.5f, -0.5f, 0.25f))
