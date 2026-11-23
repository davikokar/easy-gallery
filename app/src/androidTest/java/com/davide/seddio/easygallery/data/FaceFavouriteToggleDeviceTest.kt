package com.davide.seddio.easygallery.data

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 3.7, G5b (item 4): ADR-0018 Amendment 6 §1 rests on one premise it never observed, that a favourite toggle
 * advances an image's `generationModified` and so sends the row through Tier A again, where `upsert` keeps its Tier C
 * rows because the healing triple is unchanged. This test observes it with the production parts over the phone's real
 * `MediaStore`: a throwaway in-memory index, the production Tier A indexer, the face pass and grouping with the real
 * pipeline over the first images (the app's own index, settings and disclosure flag are never touched), then one
 * image with faces is favourited through [MediaStoreFavouritesDataSource] and the delta is run again.
 *
 * Opt-in: skipped unless the instrumentation argument [OPT_IN_ARGUMENT] is `true`, e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.faceFavouriteToggle=true`. It needs an API 30+ phone on the prompt-free
 * favourite tier and an arm64 process, and fails with `ENVIRONMENT:` otherwise. The image's favourite is put back, and
 * checked, in a step of its own. Faces are compared in the process and never logged; the one result line, tag [TAG],
 * holds booleans and counts of index rows.
 */
@RunWith(AndroidJUnit4::class)
class FaceFavouriteToggleDeviceTest {

    @Test
    fun aFavouriteToggleKeepsTheImagesFaceRowsThroughTheNextIndexRunAndAdvancesItsGeneration() {
        assumeTrue(
            "Opt-in: pass the instrumentation argument $OPT_IN_ARGUMENT=true to run this test",
            InstrumentationRegistry.getArguments().getString(OPT_IN_ARGUMENT) == "true"
        )
        val app = ApplicationProvider.getApplicationContext<Context>()
        grantMediaPermissions()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) failTest("ENVIRONMENT: favourites and generations need API 30+.")
        if (!supportsFacePipelineAbi(Build.SUPPORTED_ABIS.toList())) failTest("ENVIRONMENT: the face pipeline needs an arm64 phone.")
        val favourites = MediaStoreFavouritesDataSource(app)
        if (favourites.currentTier() != FavouriteTier.DIRECT_MARK) {
            failTest("ENVIRONMENT: this test toggles through the prompt-free tier, and the phone is on ${favourites.currentTier()}.")
        }

        runBlocking {
            val repository = MediaStoreDataSource(app)
            if (repository.getAllMedia().isEmpty()) failTest("ENVIRONMENT: MediaStore returned zero live items to the app process.")
            val database = Room.inMemoryDatabaseBuilder(app, MediaIndexDatabase::class.java).build()
            try {
                val store = RoomMediaIndexStore(database)
                val indexer = tierAIndexer(app, repository, store, database)
                val first = indexer.indexDelta(Build.VERSION.SDK_INT)
                Log.i(TAG, "first delta: selectedRows=${first.selectedRows} indexedRows=${first.indexedRows}")

                val settingStore = InMemoryFaceDataSettingStore(initiallyEnabled = true)
                val pass = FaceIndexPass(
                    store = store,
                    mediaRepository = repository,
                    settingStore = settingStore,
                    pipelineLoader = { OpenCvFacePipeline.load(app) },
                    log = FaceIndexLog { },
                    budgetMillis = PASS_BUDGET_MILLIS
                )
                val passResult = pass.run(System.currentTimeMillis())
                val grouping = FaceGroupingPass(store, settingStore, FaceIndexLog { }).run(System.currentTimeMillis())
                Log.i(
                    TAG,
                    "face pass: images=${passResult.indexedImages + passResult.failedImages} stop=${passResult.stop} grouping=$grouping"
                )

                val subject = store.observeAll().first().firstOrNull { row ->
                    row.generationModified != null && store.listFaces(row.mediaUri).any { it.groupId != null }
                } ?: failTest("ENVIRONMENT: none of the ${passResult.indexedImages} images the pass indexed holds a grouped face.")
                toggleAndCompare(app, favourites, store, indexer, subject)
            } finally {
                database.close()
            }
        }
    }

    // The body, the restore and the check of the restore are separate steps, and none can hide another's failure.
    private suspend fun toggleAndCompare(
        app: Context,
        favourites: MediaStoreFavouritesDataSource,
        store: RoomMediaIndexStore,
        indexer: MediaIndexTierABatchIndexer,
        subject: MediaIndexEntry
    ) {
        val uri = Uri.parse(subject.mediaUri)
        val favouriteBefore = readFavourite(app, uri) ?: failTest("ENVIRONMENT: MediaStore does not answer for the chosen image.")
        val mediaStoreGenerationBefore = readGeneration(app, uri)
        val stateBefore = store.getFaceIndexState(subject.mediaUri)
        val facesBefore = store.listFaces(subject.mediaUri)

        val bodyFailure = runCatching {
            assertEquals("The toggle must apply without a prompt", FavouriteWriteResult.Applied, favourites.setFavourite(listOf(uri), !favouriteBefore))
            val mediaStoreGenerationAfter = readGeneration(app, uri)
            val batch = indexer.indexDelta(Build.VERSION.SDK_INT)
            val after = store.getByMediaUri(subject.mediaUri) ?: failTest("The chosen image's index row is gone after the delta.")

            val advancedInMediaStore = mediaStoreGenerationAfter != null && mediaStoreGenerationBefore != null &&
                mediaStoreGenerationAfter > mediaStoreGenerationBefore
            val advancedInIndex = after.generationModified != null && after.generationModified > subject.generationModified!!
            val tripleUnchanged = after.toMediaIdentity() == subject.toMediaIdentity()
            val facesIntact = store.listFaces(subject.mediaUri) == facesBefore
            val stateIntact = store.getFaceIndexState(subject.mediaUri) == stateBefore
            Log.i(
                TAG,
                "result: deltaSelectedRows=${batch.selectedRows} deltaIndexedRows=${batch.indexedRows} " +
                    "generationAdvancedInMediaStore=$advancedInMediaStore generationAdvancedInIndex=$advancedInIndex " +
                    "healingTripleUnchanged=$tripleUnchanged faceRowsIntact=$facesIntact faceStateIntact=$stateIntact"
            )

            assertTrue("The toggle must advance generation_modified in MediaStore", advancedInMediaStore)
            assertTrue("The next delta must have selected the toggled row", batch.selectedRows >= 1)
            assertTrue("The row's generationModified must advance in the index", advancedInIndex)
            assertTrue("A favourite toggle must leave the healing triple alone", tripleUnchanged)
            assertTrue("The image's face rows must be exactly as they were: same ids, boxes, embeddings, groups", facesIntact)
            assertTrue("The image's face state must be as it was", stateIntact)
        }.exceptionOrNull()
        val restoreFailure = runCatching { favourites.setFavourite(listOf(uri), favouriteBefore) }.exceptionOrNull()
        val verifyFailure = runCatching {
            assertEquals("The image's favourite must be as before", favouriteBefore, readFavourite(app, uri))
        }.exceptionOrNull()

        val failures = listOfNotNull(bodyFailure, restoreFailure, verifyFailure)
        if (failures.isNotEmpty()) {
            val first = failures.first()
            failures.drop(1).forEach { first.addSuppressed(it) }
            throw first
        }
    }

    private fun readFavourite(app: Context, uri: Uri): Boolean? =
        app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_FAVORITE), null, null, null)?.use {
            if (it.moveToFirst()) it.getInt(0) == 1 else null
        }

    private fun readGeneration(app: Context, uri: Uri): Long? =
        app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    private fun tierAIndexer(
        app: Context,
        repository: MediaStoreDataSource,
        store: RoomMediaIndexStore,
        database: MediaIndexDatabase
    ): MediaIndexTierABatchIndexer = MediaIndexTierABatchIndexer(
        mediaRepository = repository,
        mediaIndexStore = store,
        deltaDetector = MediaIndexDeltaDetector(
            watermarkStore = RoomMediaIndexWatermarkStore(database.mediaIndexDao()),
            snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(app)
        ),
        rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(app),
        reconciler = MediaIndexReconciler(store),
        extractor = AndroidMediaIndexTierAExtractor(app)
    )

    private fun grantMediaPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetPackage = instrumentation.targetContext.packageName
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Manifest.permission.ACCESS_MEDIA_LOCATION) else emptyList()
        (permissions + location).forEach { permission ->
            try {
                instrumentation.uiAutomation.grantRuntimePermission(targetPackage, permission)
            } catch (_: Exception) {
                // The live-item check reports a permission still missing.
            }
        }
    }

    private fun failTest(message: String): Nothing {
        Log.e(TAG, message)
        throw AssertionError(message)
    }

    private companion object {
        const val TAG = "FaceFavouriteToggleTest"
        const val OPT_IN_ARGUMENT = "faceFavouriteToggle"

        // Enough images for one with a face, far less than the whole library.
        const val PASS_BUDGET_MILLIS = 2L * 60L * 1000L
    }
}
