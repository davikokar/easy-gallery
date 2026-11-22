package com.davide.seddio.easygallery.data

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.FileNotFoundException
import java.io.StringReader

// Drives the real MediaIndexingWorker.doWork through WorkManager's test builder; only its collaborators are fakes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexingWorkerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val runState = InMemoryMediaIndexRunStateStore()
    private val indexStore = InMemoryMediaIndexStore()
    private val repository = FakeMediaRepository().apply { mediaItems = listOf(photo()) }

    // The face seams: the setting is on and the build has no pipeline, which is what the app ships until Task 2.
    private val setting = InMemoryFaceDataSettingStore(initiallyEnabled = true)
    private val deviceState = InMemoryFaceDeviceStateStore()
    private val faceLock = Mutex()
    private val faceLog = mutableListOf<String>()
    private val events = mutableListOf<String>()
    private val pipeline = FakeFacePipeline()
    private var pipelineLoader: () -> FacePipeline = { throw FacePipelineUnavailableException() }
    private var capabilityChecks = 0
    private var passesCreated = 0
    private var deletionFailure: Throwable? = null

    // The grouping seams: how often it was created and asked, what it throws, and the clock its start deadline reads.
    private val lockEvents = mutableListOf<String>()
    private var groupingPassesCreated = 0
    private var groupingChecks = 0
    private var groupingFailure: Throwable? = null
    private var groupingClock: () -> Long = { System.currentTimeMillis() }
    private var faceBudgetMillis = FACE_PASS_BUDGET_MILLIS

    // API 30 checks READ_EXTERNAL_STORAGE; AppOps stay at Robolectric's default of allowed.
    @Before
    fun grantMediaPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @Test
    fun `a place resolve failure marks the run failed returns failure and stamps no row`() = runTest {
        val worker = worker(placeResolver = resolver { throw FileNotFoundException("gazetteer asset missing") })

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            "gazetteer asset missing",
            (result as ListenableWorker.Result.Failure).outputData.getString(MediaIndexingWorker.KEY_ERROR_MESSAGE)
        )
        val state = runState.state.value
        assertEquals(MediaIndexRunOutcome.FAILURE, state.lastRunOutcome)
        assertFalse(state.isRunning)
        assertNotNull(state.lastRunFinishedAtMillis)
        assertNull(state.lastSuccessfulRunFinishedAtMillis)
        // Tier A did index the row before the resolver failed; it must still be pending for the next run.
        assertNull(indexStore.getByMediaUri(PHOTO_URI)!!.derivedPlaceResolverVersion)
    }

    @Test
    fun `a successful run marks the run succeeded and resolves the located row`() = runTest {
        val worker = worker(placeResolver = resolver { syntheticGazetteer() })

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, (result as ListenableWorker.Result.Success).outputData.getInt(MediaIndexingWorker.KEY_INDEXED_ROWS, -1))
        val state = runState.state.value
        assertEquals(MediaIndexRunOutcome.SUCCESS, state.lastRunOutcome)
        assertFalse(state.isRunning)
        assertNotNull(state.lastSuccessfulRunFinishedAtMillis)
        assertEquals(1, state.indexedRows)
        val row = indexStore.getByMediaUri(PHOTO_URI)!!
        assertEquals("Testville", row.derivedPlaceName)
        assertEquals("AA", row.derivedCountryCode)
        assertEquals(PLACE_RESOLVER_VERSION, row.derivedPlaceResolverVersion)
    }

    @Test
    fun `cancelling the worker marks the run cancelled and not failed`() = runTest {
        val resolveStarted = CompletableDeferred<Unit>()
        val hangingStore = object : MediaIndexStore by indexStore {
            override suspend fun listPendingPlaceResolution(
                currentResolverVersion: Int,
                afterMediaUri: String?,
                limit: Int
            ): List<MediaIndexEntry> {
                resolveStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val worker = worker(placeResolver = MediaIndexPlaceResolver(hangingStore, gazetteerLoader = { syntheticGazetteer() }))

        val job = launch { worker.doWork() }
        resolveStarted.await()
        job.cancelAndJoin()

        val state = runState.state.value
        assertEquals(MediaIndexRunOutcome.CANCELLED, state.lastRunOutcome)
        assertFalse(state.isRunning)
        assertNull(state.lastSuccessfulRunFinishedAtMillis)
    }

    @Test
    fun `a missing media permission fails the run before any indexing starts`() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        var indexersCreated = 0
        val worker = worker(
            placeResolver = resolver { syntheticGazetteer() },
            onIndexerCreated = { indexersCreated += 1 }
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            "Required media read permission is missing",
            (result as ListenableWorker.Result.Failure).outputData.getString(MediaIndexingWorker.KEY_ERROR_MESSAGE)
        )
        assertEquals(MediaIndexRunOutcome.FAILURE, runState.state.value.lastRunOutcome)
        assertEquals(0, indexersCreated)
        assertNull(indexStore.getByMediaUri(PHOTO_URI))
    }

    // ---- the face path (plan 3.3, Task 1, criteria 6, 8 and 9) ----

    @Test
    fun `with the setting off the deletion runs first, before Tier A, and no pass or check follows`() = runTest {
        seedSecondPhoto()
        indexStore.writeFaceIndexResults(
            listOf(FaceIndexWrite(indexStore.getByMediaUri(photoUri(2))!!.toMediaIdentity(), FACE_PIPELINE_VERSION, FaceIndexOutcome.INDEXED, emptyList())),
            faceDataEnabled = { true }
        )
        setting.setEnabled(false)
        openAllFaceGates()

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf("deletion", "tierA"), events)
        assertFalse(indexStore.hasFaceData())
        assertEquals(0, capabilityChecks)
        assertEquals(0, passesCreated)
        assertEquals(0, groupingPassesCreated)
        assertEquals(0, groupingChecks)
        assertEquals(
            listOf("face data deletion ran scrubbed=true", "face pass not run reason=SETTING_OFF"),
            faceLog
        )
    }

    @Test
    fun `with the setting off the deletion runs even when there is no face data to delete`() = runTest {
        setting.setEnabled(false)

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf("deletion", "tierA"), events)
    }

    @Test
    fun `with the setting off the deletion runs before the permission check and survives a revoked permission`() = runTest {
        seedSecondPhoto()
        indexStore.writeFaceIndexResults(
            listOf(FaceIndexWrite(indexStore.getByMediaUri(photoUri(2))!!.toMediaIdentity(), FACE_PIPELINE_VERSION, FaceIndexOutcome.INDEXED, emptyList())),
            faceDataEnabled = { true }
        )
        setting.setEnabled(false)
        shadowOf(context).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            "Required media read permission is missing",
            (result as ListenableWorker.Result.Failure).outputData.getString(MediaIndexingWorker.KEY_ERROR_MESSAGE)
        )
        assertEquals(listOf("deletion"), events)
        assertFalse(indexStore.hasFaceData())
    }

    @Test
    fun `with the setting on the deletion never runs and the face data stays`() = runTest {
        seedSecondPhoto()
        indexStore.writeFaceIndexResults(
            listOf(FaceIndexWrite(indexStore.getByMediaUri(photoUri(2))!!.toMediaIdentity(), FACE_PIPELINE_VERSION, FaceIndexOutcome.INDEXED, emptyList())),
            faceDataEnabled = { true }
        )

        worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertEquals(listOf("tierA"), events)
        assertTrue(indexStore.hasFaceData())
        assertTrue(faceLog.none { it.startsWith("face data deletion") })
    }

    @Test
    fun `a deletion that throws neither fails the run nor stops Tier A`() = runTest {
        setting.setEnabled(false)
        deletionFailure = IllegalStateException("secure_delete said $LEAK_MARKER")

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, (result as ListenableWorker.Result.Success).outputData.getInt(MediaIndexingWorker.KEY_INDEXED_ROWS, -1))
        assertEquals(listOf("deletion", "tierA"), events)
        assertEquals(listOf("face data deletion failed", "face pass not run reason=SETTING_OFF"), faceLog)
    }

    @Test
    fun `an incapable device runs no pass`() = runTest {
        deviceState.markDisclosureShown()

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, capabilityChecks)
        assertEquals(0, passesCreated)
        assertEquals(0, groupingPassesCreated)
        assertEquals(0, groupingChecks)
        assertEquals(
            listOf("face capability checked capable=false", "face pass not run reason=INCAPABLE"),
            faceLog
        )
        assertNull(indexStore.getFaceIndexState(PHOTO_URI))
    }

    @Test
    fun `a disclosure that has not been shown runs no pass`() = runTest {
        pipelineLoader = { pipeline }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, passesCreated)
        assertEquals(0, groupingPassesCreated)
        assertEquals(0, groupingChecks)
        assertEquals(
            listOf("face capability checked capable=true", "face pass not run reason=DISCLOSURE_NOT_SHOWN"),
            faceLog
        )
        assertTrue(pipeline.analysed.isEmpty())
        assertNull(indexStore.getFaceIndexState(PHOTO_URI))
    }

    @Test
    fun `a held lock runs no pass and leaves the lock with its holder`() = runTest {
        openAllFaceGates()
        faceLock.lock()

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, passesCreated)
        assertEquals(0, capabilityChecks)
        assertEquals(0, groupingPassesCreated)
        assertEquals(0, groupingChecks)
        assertTrue(faceLock.isLocked)
        assertEquals(listOf("face pass not run reason=BUSY"), faceLog)
        assertTrue(pipeline.analysed.isEmpty())
        faceLock.unlock()
    }

    @Test
    fun `with every gate open the pass indexes the images, frees the lock and leaves the run state to Tier A`() = runTest {
        seedSecondPhoto()
        openAllFaceGates()

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        listOf(photoUri(1), photoUri(2)).forEach {
            assertEquals(FaceIndexOutcome.INDEXED, indexStore.getFaceIndexState(it)!!.outcome)
        }
        assertEquals(listOf("tierA"), events)
        assertFalse(faceLock.isLocked)
        assertEquals(
            listOf(
                "face capability checked capable=true",
                "face pass finished images=2 indexed=2 failed=0 skipped=0 stop=COMPLETED",
                "face grouping finished stop=GROUPED"
            ),
            faceLog
        )
        // Both faces are identical, so grouping put them in one group at the current grouping version.
        val faces = listOf(photoUri(1), photoUri(2)).map { indexStore.listFaces(it).single() }
        assertNotNull(faces[0].groupId)
        assertEquals(faces[0].groupId, faces[1].groupId)
        assertEquals(listOf(FACE_GROUPING_VERSION, FACE_GROUPING_VERSION), faces.map { it.groupingVersion })
        // Tier A selected and indexed one row; the pass indexed two images and none of it is in the run state.
        val state = runState.state.value
        assertEquals(MediaIndexRunOutcome.SUCCESS, state.lastRunOutcome)
        assertEquals(1, state.selectedRows)
        assertEquals(1, state.indexedRows)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(1, output.getInt(MediaIndexingWorker.KEY_INDEXED_ROWS, -1))
        assertEquals(
            setOf(
                MediaIndexingWorker.KEY_SELECTED_ROWS,
                MediaIndexingWorker.KEY_INDEXED_ROWS,
                MediaIndexingWorker.KEY_SKIPPED_ROWS,
                MediaIndexingWorker.KEY_DELETED_ROWS,
                MediaIndexingWorker.KEY_HEALED_ROWS
            ),
            output.keyValueMap.keys
        )
    }

    @Test
    fun `a pass that throws fails the run with the fixed message and leaves Tier A and places persisted`() = runTest {
        openAllFaceGates()
        pipeline.onAnalyse = { uri -> throw IllegalStateException("$LEAK_MARKER $uri") }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            FACE_INDEX_FAILURE_MESSAGE,
            (result as ListenableWorker.Result.Failure).outputData.getString(MediaIndexingWorker.KEY_ERROR_MESSAGE)
        )
        assertEquals(MediaIndexRunOutcome.FAILURE, runState.state.value.lastRunOutcome)
        assertFalse(faceLock.isLocked)
        assertEquals(PLACE_RESOLVER_VERSION, indexStore.getByMediaUri(PHOTO_URI)!!.derivedPlaceResolverVersion)
        assertEquals(
            listOf("face capability checked capable=true", "face pass failed"),
            faceLog
        )
        // A failed pass is not followed by grouping.
        assertEquals(0, groupingPassesCreated)
        assertEquals(0, groupingChecks)
    }

    @Test
    fun `an Error from the pass fails the run with the fixed message, frees the lock and leaves Tier A and places persisted`() = runTest {
        openAllFaceGates()
        pipeline.onAnalyse = { throw OutOfMemoryError(LEAK_MARKER) }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            FACE_INDEX_FAILURE_MESSAGE,
            (result as ListenableWorker.Result.Failure).outputData.getString(MediaIndexingWorker.KEY_ERROR_MESSAGE)
        )
        assertEquals(MediaIndexRunOutcome.FAILURE, runState.state.value.lastRunOutcome)
        assertFalse(faceLock.isLocked)
        assertEquals(PLACE_RESOLVER_VERSION, indexStore.getByMediaUri(PHOTO_URI)!!.derivedPlaceResolverVersion)
        assertEquals(listOf("face capability checked capable=true", "face pass failed"), faceLog)
        assertEquals(0, groupingPassesCreated)
        assertEquals(0, groupingChecks)
    }

    @Test
    fun `a pipeline that loads for the check but not for the pass fails the run with the same fixed message`() = runTest {
        deviceState.markDisclosureShown()
        // The recorded answer is reused, so only the pass asks the loader, and the loader throws.
        deviceState.saveCapabilityResult(CAPABILITY_KEY.asStoredKey(), capable = true)
        pipelineLoader = { throw IllegalStateException(LEAK_MARKER) }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            FACE_INDEX_FAILURE_MESSAGE,
            (result as ListenableWorker.Result.Failure).outputData.getString(MediaIndexingWorker.KEY_ERROR_MESSAGE)
        )
        assertEquals(listOf("face capability checked capable=true", "face pass failed"), faceLog)
        assertFalse(faceLock.isLocked)
    }

    @Test
    fun `nothing the face path emits or returns holds a uri, a name, a box or an embedding value`() = runTest {
        seedSecondPhoto()
        openAllFaceGates()
        pipeline.onAnalyse = { uri ->
            if (uri == photoUri(1)) {
                fakeOutput(faces = listOf(RawFace(311.7125f, 222.3125f, 511.9375f, 433.4375f, FaceEmbedding(floatArrayOf(0.13579f, 0.2468f)))))
            } else {
                throw IllegalStateException("$LEAK_MARKER $uri photo2.jpg")
            }
        }
        ShadowLog.clear()

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        val failure = result as ListenableWorker.Result.Failure
        val returned = failure.outputData.keyValueMap.values.map { it.toString() }
        val androidLog = ShadowLog.getLogs().map { "${it.msg} ${it.throwable?.stackTraceToString().orEmpty()}" }
        val forbidden = listOf(LEAK_MARKER, photoUri(1), photoUri(2), "photo.jpg", "photo2.jpg", "311.7", "222.3", "0.13579", "0.2468", "0.3117")
        FaceLogRules.assertFixedSet(faceLog)
        FaceLogRules.assertNoneOf(faceLog, forbidden)
        FaceLogRules.assertNoneOf(returned, forbidden)
        FaceLogRules.assertNoneOf(androidLog, forbidden)
        assertEquals(listOf(FACE_INDEX_FAILURE_MESSAGE), returned)
    }

    // ---- the grouping pass (plan 3.4, Task 4) ----

    @Test
    fun `grouping runs after the pass inside the same hold of the lock and the lock is freed after`() = runTest {
        openAllFaceGates()
        pipeline.onAnalyse = {
            lockEvents += "analyse locked=${faceLock.isLocked}"
            fakeOutput()
        }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf("capability locked=true", "analyse locked=true", "grouping locked=true"), lockEvents)
        assertEquals(1, groupingPassesCreated)
        assertFalse(faceLock.isLocked)
        assertEquals(
            listOf(
                "face capability checked capable=true",
                "face pass finished images=1 indexed=1 failed=0 skipped=0 stop=COMPLETED",
                "face grouping finished stop=GROUPED"
            ),
            faceLog
        )
        assertNotNull(indexStore.listFaces(PHOTO_URI).single().groupId)
        // Tier A's state alone: one row selected and indexed, and nothing of the face pass or grouping in the output.
        val state = runState.state.value
        assertEquals(MediaIndexRunOutcome.SUCCESS, state.lastRunOutcome)
        assertEquals(1, state.selectedRows)
        assertEquals(1, state.indexedRows)
        assertEquals(0, state.skippedRows)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(
            mapOf(
                MediaIndexingWorker.KEY_SELECTED_ROWS to 1,
                MediaIndexingWorker.KEY_INDEXED_ROWS to 1,
                MediaIndexingWorker.KEY_SKIPPED_ROWS to 0,
                MediaIndexingWorker.KEY_DELETED_ROWS to 0,
                MediaIndexingWorker.KEY_HEALED_ROWS to 0
            ),
            output.keyValueMap
        )
    }

    @Test
    fun `a pass stopped by the setting is not followed by grouping`() = runTest {
        seedIndexedFace()
        openAllFaceGates()
        pipeline.onAnalyse = {
            setting.setEnabled(false)
            fakeOutput()
        }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, groupingPassesCreated)
        assertEquals(0, groupingChecks)
        assertEquals(
            listOf(
                "face capability checked capable=true",
                "face pass finished images=0 indexed=0 failed=0 skipped=0 stop=SETTING_OFF"
            ),
            faceLog
        )
        assertNull(indexStore.listFaces(photoUri(2)).single().groupId)
        assertFalse(faceLock.isLocked)
    }

    @Test
    fun `a pass cut by its budget is still followed by grouping`() = runTest {
        seedIndexedFace()
        openAllFaceGates()
        faceBudgetMillis = 0L

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            listOf(
                "face capability checked capable=true",
                "face pass finished images=0 indexed=0 failed=0 skipped=0 stop=BUDGET",
                "face grouping finished stop=GROUPED"
            ),
            faceLog
        )
        assertNotNull(indexStore.listFaces(photoUri(2)).single().groupId)
        assertFalse(faceLock.isLocked)
    }

    @Test
    fun `a pass that lost media permission is still followed by grouping`() = runTest {
        seedIndexedFace()
        openAllFaceGates()
        pipeline.onAnalyse = { throw SecurityException("refused") }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            listOf(
                "face capability checked capable=true",
                "face pass finished images=0 indexed=0 failed=0 skipped=0 stop=PERMISSION",
                "face grouping finished stop=GROUPED"
            ),
            faceLog
        )
        assertNotNull(indexStore.listFaces(photoUri(2)).single().groupId)
        assertFalse(faceLock.isLocked)
    }

    @Test
    fun `grouping does not start once the worker has run for eight minutes`() = runTest {
        seedIndexedFace()
        openAllFaceGates()
        groupingClock = { runState.state.value.lastRunStartedAtMillis!! + FACE_GROUPING_START_DEADLINE_MILLIS }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        // Past the deadline is not a failure: the run succeeds and the next run groups.
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("face grouping finished stop=PAST_DEADLINE", faceLog.last())
        assertEquals(0, groupingChecks)
        assertNull(indexStore.listFaces(photoUri(2)).single().groupId)
        assertFalse(faceLock.isLocked)
    }

    @Test
    fun `grouping starts one millisecond inside the eight minutes counted from the worker's start`() = runTest {
        seedIndexedFace()
        openAllFaceGates()
        groupingClock = { runState.state.value.lastRunStartedAtMillis!! + FACE_GROUPING_START_DEADLINE_MILLIS - 1L }

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("face grouping finished stop=GROUPED", faceLog.last())
        assertNotNull(indexStore.listFaces(photoUri(2)).single().groupId)
    }

    @Test
    fun `a grouping that throws fails the run with the fixed message and leaves Tier A, places and the pass persisted`() = runTest {
        openAllFaceGates()
        groupingFailure = IllegalStateException("$LEAK_MARKER ${photoUri(1)} photo.jpg")
        ShadowLog.clear()

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertEquals(mapOf(MediaIndexingWorker.KEY_ERROR_MESSAGE to FACE_INDEX_FAILURE_MESSAGE), output.keyValueMap)
        assertEquals(MediaIndexRunOutcome.FAILURE, runState.state.value.lastRunOutcome)
        assertFalse(faceLock.isLocked)
        assertEquals(PLACE_RESOLVER_VERSION, indexStore.getByMediaUri(PHOTO_URI)!!.derivedPlaceResolverVersion)
        assertEquals(FaceIndexOutcome.INDEXED, indexStore.getFaceIndexState(PHOTO_URI)!!.outcome)
        assertEquals(
            listOf(
                "face capability checked capable=true",
                "face pass finished images=1 indexed=1 failed=0 skipped=0 stop=COMPLETED",
                "face pass failed"
            ),
            faceLog
        )
        val androidLog = ShadowLog.getLogs().map { "${it.msg} ${it.throwable?.stackTraceToString().orEmpty()}" }
        FaceLogRules.assertNoneOf(androidLog, listOf(LEAK_MARKER, photoUri(1), "photo.jpg"))
    }

    @Test
    fun `an Error from grouping fails the run with the fixed message, frees the lock and leaves Tier A and places persisted`() = runTest {
        openAllFaceGates()
        groupingFailure = OutOfMemoryError(LEAK_MARKER)

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            FACE_INDEX_FAILURE_MESSAGE,
            (result as ListenableWorker.Result.Failure).outputData.getString(MediaIndexingWorker.KEY_ERROR_MESSAGE)
        )
        assertEquals(MediaIndexRunOutcome.FAILURE, runState.state.value.lastRunOutcome)
        assertFalse(faceLock.isLocked)
        assertEquals(PLACE_RESOLVER_VERSION, indexStore.getByMediaUri(PHOTO_URI)!!.derivedPlaceResolverVersion)
        assertEquals(1, groupingChecks)
        assertEquals(
            listOf(
                "face capability checked capable=true",
                "face pass finished images=1 indexed=1 failed=0 skipped=0 stop=COMPLETED",
                "face pass failed"
            ),
            faceLog
        )
    }

    @Test
    fun `nothing grouping emits or returns holds a uri, a name, a box, an embedding value or a group`() = runTest {
        seedSecondPhoto()
        openAllFaceGates()
        pipeline.onAnalyse = {
            fakeOutput(faces = listOf(RawFace(311.7125f, 222.3125f, 511.9375f, 433.4375f, FaceEmbedding(floatArrayOf(0.13579f, 0.2468f)))))
        }
        ShadowLog.clear()

        val result = worker(placeResolver = resolver { syntheticGazetteer() }).doWork()

        val success = result as ListenableWorker.Result.Success
        val returned = success.outputData.keyValueMap.values.map { it.toString() }
        val androidLog = ShadowLog.getLogs().map { "${it.msg} ${it.throwable?.stackTraceToString().orEmpty()}" }
        val forbidden = listOf(photoUri(1), photoUri(2), "photo.jpg", "photo2.jpg", "311.7", "222.3", "0.13579", "0.2468", "0.3117")
        assertEquals("face grouping finished stop=GROUPED", faceLog.last())
        assertTrue(Regex("^face grouping finished stop=[A-Z_]+$").matches(faceLog.last()))
        FaceLogRules.assertFixedSet(faceLog)
        FaceLogRules.assertNoneOf(faceLog, forbidden)
        FaceLogRules.assertNoneOf(returned, forbidden)
        FaceLogRules.assertNoneOf(androidLog, forbidden)
    }

    // ---- fixtures ----

    private fun worker(
        placeResolver: MediaIndexPlaceResolver,
        onIndexerCreated: () -> Unit = {}
    ): MediaIndexingWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = MediaIndexingWorker(
                appContext,
                workerParameters,
                runStateStore = runState,
                createBatchIndexer = { events += "tierA"; onIndexerCreated(); tierAIndexerWithOneLocatedPhoto() },
                createPlaceResolver = { placeResolver },
                faceDataSettingStore = setting,
                faceDeviceStateStore = deviceState,
                createFaceDataStartupDeletion = { FaceDataStartupDeletion(recordingStore, setting, faceIndexLog) },
                createFaceCapabilityChecker = {
                    capabilityChecks += 1
                    lockEvents += "capability locked=${faceLock.isLocked}"
                    FaceCapabilityChecker(deviceState, CAPABILITY_KEY, listOf(FACE_PIPELINE_ABI), { pipelineLoader() })
                },
                createFaceIndexPass = {
                    passesCreated += 1
                    FaceIndexPass(indexStore, repository, setting, { pipelineLoader() }, faceIndexLog, budgetMillis = faceBudgetMillis)
                },
                createFaceGroupingPass = {
                    groupingPassesCreated += 1
                    FaceGroupingPass(groupingStore, setting, faceIndexLog, clock = { groupingClock() })
                },
                faceIndexLock = faceLock,
                faceIndexLog = faceIndexLog
            )
        }
        return TestListenableWorkerBuilder<MediaIndexingWorker>(context).setWorkerFactory(factory).build()
    }

    private val faceIndexLog = FaceIndexLog { faceLog += it }

    // Records when the deletion happens, so a test can put it in order with Tier A and the permission check.
    private val recordingStore = object : MediaIndexStore by indexStore {
        override suspend fun deleteAllFaceData(): FaceDataDeletion {
            events += "deletion"
            deletionFailure?.let { throw it }
            return indexStore.deleteAllFaceData()
        }
    }

    // Grouping always asks whether it is needed first, so this is where a test sees that it ran, and whether the lock was held.
    private val groupingStore = object : MediaIndexStore by indexStore {
        override suspend fun isFaceGroupingNeeded(pipelineVersion: Int, groupingVersion: Int): Boolean {
            groupingChecks += 1
            lockEvents += "grouping locked=${faceLock.isLocked}"
            groupingFailure?.let { throw it }
            return indexStore.isFaceGroupingNeeded(pipelineVersion, groupingVersion)
        }
    }

    private fun resolver(loader: () -> Gazetteer) = MediaIndexPlaceResolver(indexStore, gazetteerLoader = loader)

    // A device that passes the check, with the disclosure shown: the pass is then only the lock and the setting away.
    private fun openAllFaceGates() {
        pipelineLoader = { pipeline }
        deviceState.markDisclosureShown()
    }

    // The second photo already has one indexed face, identical to the fake pipeline's, that no run has grouped yet.
    private suspend fun seedIndexedFace() {
        seedSecondPhoto()
        val identity = indexStore.getByMediaUri(photoUri(2))!!.toMediaIdentity()
        val face = FaceDetection(FaceBox(0.1f, 0.1f, 0.5f, 0.5f), FaceEmbedding(floatArrayOf(0.5f, -0.5f, 0.25f)))
        indexStore.writeFaceIndexResults(
            listOf(FaceIndexWrite(identity, FACE_PIPELINE_VERSION, FaceIndexOutcome.INDEXED, listOf(face))),
            faceDataEnabled = { true }
        )
    }

    // An index row for the second photo, so the pass has an image that Tier A did not just index.
    private suspend fun seedSecondPhoto() {
        repository.mediaItems = listOf(photo(), photo(2))
        indexStore.upsert(
            listOf(
                MediaIndexEntry(
                    mediaUri = photoUri(2),
                    displayName = "photo2.jpg",
                    size = 300L,
                    dateModified = 200L,
                    volumeName = PRIMARY,
                    generationAdded = 4L,
                    generationModified = 4L,
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
    }

    // One present volume holding one photo whose extraction yields coordinates, so the row exists only after Tier A ran.
    private fun tierAIndexerWithOneLocatedPhoto(): MediaIndexTierABatchIndexer {
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(ObservedMediaStoreVolume(PRIMARY, version = "v1", generation = 10L)),
            presentVolumes = setOf(PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
        val observed = MediaStoreObservedRowDelta(
            mediaUri = PHOTO_URI,
            volumeName = PRIMARY,
            generationAdded = 5L,
            generationModified = 5L,
            dateModifiedSeconds = 5L
        )
        return MediaIndexTierABatchIndexer(
            mediaRepository = repository,
            mediaIndexStore = indexStore,
            deltaDetector = MediaIndexDeltaDetector(
                watermarkStore = InMemoryMediaIndexWatermarkStore(),
                snapshotProvider = object : MediaStoreVolumeSnapshotProvider {
                    override fun snapshot(sdkInt: Int) = snapshot
                }
            ),
            rowDeltaProvider = object : MediaStoreRowDeltaProvider {
                override fun queryObservedRows(volumeName: String, apiBranch: MediaStoreDeltaApiBranch) = listOf(observed)
            },
            reconciler = MediaIndexReconciler(indexStore),
            extractor = object : MediaIndexTierAExtractor {
                override suspend fun extract(mediaItem: MediaItem) = MediaIndexTierAExtraction(
                    capturedDateTakenEpochMillis = 1234L,
                    capturedLatitude = 10.1,
                    capturedLongitude = 20.1,
                    capturedWidth = null,
                    capturedHeight = null
                )
            }
        )
    }

    private fun photoUri(id: Int) = "content://media/external/images/media/$id"

    private fun photo(id: Int = 1) = MediaItem(
        uri = Uri.parse(photoUri(id)),
        name = if (id == 1) "photo.jpg" else "photo$id.jpg",
        dateAdded = 100L,
        dateModified = 200L,
        size = 300L,
        type = MediaType.IMAGE,
        bucketName = "Camera",
        folderPath = "/storage/emulated/0/DCIM/Camera",
        duration = null
    )

    private fun header(rows: Int) = "# Test asset\n# dataset-version: $PLACE_RESOLVER_VERSION\n# rows-written: $rows\n"

    private fun syntheticGazetteer(): Gazetteer = Gazetteer.load(
        StringReader(header(1) + "10.0\t20.0\tTestville\tAA\t01\n"),
        StringReader(header(1) + "AA.01\tRegion One\n"),
        StringReader(header(1) + "AA\tAlphaland\n")
    )

    private companion object {
        const val PRIMARY = "external_primary"
        const val PHOTO_URI = "content://media/external/images/media/1"
        const val LEAK_MARKER = "LEAKCHECK-77"
        val CAPABILITY_KEY = FaceCapabilityKey(appVersionCode = 1L, pipelineVersion = FACE_PIPELINE_VERSION, buildFingerprint = "test/fp")
    }
}
