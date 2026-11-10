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
import java.io.FileNotFoundException
import java.io.StringReader

// Drives the real MediaIndexingWorker.doWork through WorkManager's test builder; only its collaborators are fakes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexingWorkerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val runState = InMemoryMediaIndexRunStateStore()
    private val indexStore = InMemoryMediaIndexStore()

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
                createBatchIndexer = { onIndexerCreated(); tierAIndexerWithOneLocatedPhoto() },
                createPlaceResolver = { placeResolver }
            )
        }
        return TestListenableWorkerBuilder<MediaIndexingWorker>(context).setWorkerFactory(factory).build()
    }

    private fun resolver(loader: () -> Gazetteer) = MediaIndexPlaceResolver(indexStore, gazetteerLoader = loader)

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
            mediaRepository = FakeMediaRepository().apply { mediaItems = listOf(photo()) },
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

    private fun photo() = MediaItem(
        uri = Uri.parse(PHOTO_URI),
        name = "photo.jpg",
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
    }
}
