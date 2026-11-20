package com.davide.seddio.easygallery.data

import android.Manifest
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures ADR-0023 verify item 6 on the real pipeline: images processed, wall time, mean time per image, peak RSS,
 * thermal status before and after, whether the phone was charging, and that a second budgeted run takes only what the
 * first did not.
 *
 * Opt-in: skipped unless the instrumentation argument [OPT_IN_ARGUMENT] is `true`, e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.faceIndexMeasurement=true`. [BUDGET_ARGUMENT] sets the short budget in
 * milliseconds. It runs the pass in the app's process over the device's media, against a throwaway in-memory index, so
 * the app's real index and settings are untouched and nothing persists. Only aggregates are logged, never a URI, a box or
 * an embedding: `adb logcat -s FaceIndexPassMeasurement`. Turn the screen off first for the figure the plan asks for.
 */
@RunWith(AndroidJUnit4::class)
class FaceIndexPassMeasurementDeviceTest {

    @Test
    fun passFiguresAreLoggedAndTheSecondBudgetedRunTakesOnlyWhatTheFirstLeft() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Opt-in: pass the instrumentation argument $OPT_IN_ARGUMENT=true to run this measurement",
            arguments.getString(OPT_IN_ARGUMENT) == "true"
        )
        val shortBudgetMillis = arguments.getString(BUDGET_ARGUMENT)?.toLongOrNull() ?: DEFAULT_SHORT_BUDGET_MILLIS
        val app = ApplicationProvider.getApplicationContext<Context>()

        grantMediaPermissions()
        runBlocking { measure(app, shortBudgetMillis) }
    }

    private suspend fun measure(app: Context, shortBudgetMillis: Long) {
        val mediaRepository = MediaStoreDataSource(app)
        val live = mediaRepository.getAllMedia()
        Log.i(TAG, "start: sdk=${Build.VERSION.SDK_INT} liveItems=${live.size}")
        if (live.isEmpty()) {
            failTest("ENVIRONMENT: MediaStore returned zero live items to the app process; media permissions were granted.")
        }

        val database = Room.inMemoryDatabaseBuilder(app, MediaIndexDatabase::class.java).build()
        try {
            val store = RoomMediaIndexStore(database)
            val indexer = MediaIndexTierABatchIndexer(
                mediaRepository = mediaRepository,
                mediaIndexStore = store,
                deltaDetector = MediaIndexDeltaDetector(
                    watermarkStore = RoomMediaIndexWatermarkStore(database.mediaIndexDao()),
                    snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(app)
                ),
                rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(app),
                reconciler = MediaIndexReconciler(store),
                extractor = AndroidMediaIndexTierAExtractor(app)
            )
            val batch = indexer.indexDelta(Build.VERSION.SDK_INT)
            val pendingAtStart = countPending(store)
            Log.i(TAG, "index: indexedRows=${batch.indexedRows} pendingImages=$pendingAtStart")
            if (pendingAtStart == 0) {
                failTest("ENVIRONMENT: no indexed image is pending for the face pass, so nothing can be measured.")
            }

            val settings = InMemoryFaceDataSettingStore(initiallyEnabled = true)
            val log = FaceIndexLog { Log.i(TAG, it) }
            fun pass(budgetMillis: Long) = FaceIndexPass(
                store = store,
                mediaRepository = mediaRepository,
                settingStore = settings,
                pipelineLoader = { OpenCvFacePipeline.load(app) },
                log = log,
                budgetMillis = budgetMillis
            )

            val thermalBefore = thermalStatus(app)
            val charging = isCharging(app)

            // Two short-budget runs: the second must take only images the first did not.
            val first = pass(shortBudgetMillis).run(System.currentTimeMillis())
            val pendingAfterFirst = countPending(store)
            val second = pass(shortBudgetMillis).run(System.currentTimeMillis())
            val pendingAfterSecond = countPending(store)
            Log.i(
                TAG,
                "budgetRuns: budgetMillis=$shortBudgetMillis pendingStart=$pendingAtStart " +
                    "firstProcessed=${processed(first)} pendingAfterFirst=$pendingAfterFirst " +
                    "secondProcessed=${processed(second)} pendingAfterSecond=$pendingAfterSecond"
            )
            if (processed(first) == 0 || first.stop != FaceIndexPassStop.BUDGET) {
                failTest(
                    "ENVIRONMENT: the first run processed ${processed(first)} images and stopped on ${first.stop}; " +
                        "a budget of $shortBudgetMillis ms must end it part-way for the resume check to mean anything."
                )
            }
            assertEquals("The first run must leave pending exactly what it did not process", pendingAtStart - processed(first), pendingAfterFirst)
            assertEquals("The second run must take only what the first left", pendingAfterFirst - processed(second), pendingAfterSecond)
            assertTrue("The second run processed nothing", processed(second) > 0)

            // The remainder, unbudgeted for practical purposes: the throughput figures come from this run.
            val startedAt = SystemClock.elapsedRealtime()
            val full = pass(FULL_RUN_BUDGET_MILLIS).run(System.currentTimeMillis())
            val wallMillis = SystemClock.elapsedRealtime() - startedAt
            val thermalAfter = thermalStatus(app)
            val pendingAfterFull = countPending(store)

            val states = store.observeAll().first().mapNotNull { store.getFaceIndexState(it.mediaUri) }
            val failedStates = states.count { it.outcome == FaceIndexOutcome.FAILED }
            var faces = 0
            for (row in store.observeAll().first()) faces += store.listFaces(row.mediaUri).size

            Log.i(
                TAG,
                "fullRun: images=${processed(full)} indexed=${full.indexedImages} failed=${full.failedImages} " +
                    "skipped=${full.skippedImages} stop=${full.stop} wallMillis=$wallMillis " +
                    "meanMillisPerImage=${mean(wallMillis, processed(full))} pendingAfter=$pendingAfterFull"
            )
            Log.i(
                TAG,
                "totals: imagesWithState=${states.size} failedStates=$failedStates facesStored=$faces " +
                    "peakRssKb=${statusKb("VmHWM")} currentRssKb=${statusKb("VmRSS")} " +
                    "thermalBefore=$thermalBefore thermalAfter=$thermalAfter charging=$charging"
            )

            assertEquals("The remainder must complete within its budget", FaceIndexPassStop.COMPLETED, full.stop)
            assertEquals("The full run must take every image the budgeted runs left", pendingAfterSecond - processed(full), pendingAfterFull)
        } finally {
            database.close()
        }
    }

    private fun processed(result: FaceIndexPassResult) = result.indexedImages + result.failedImages

    private suspend fun countPending(store: MediaIndexStore): Int {
        var count = 0
        var cursor: String? = null
        while (true) {
            val page = store.listPendingFaceImages(FACE_PIPELINE_VERSION, cursor, COUNT_PAGE_SIZE)
            if (page.isEmpty()) return count
            count += page.size
            cursor = page.last().identity.mediaUri
        }
    }

    private fun mean(wallMillis: Long, images: Int): String =
        if (images == 0) "n/a" else String.format(Locale.US, "%.1f", wallMillis.toDouble() / images)

    private fun thermalStatus(app: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            app.getSystemService(PowerManager::class.java).currentThermalStatus.toString()
        } else {
            "n/a"
        }

    private fun isCharging(app: Context): Boolean = app.getSystemService(BatteryManager::class.java).isCharging

    // One "Name:   123 kB" line of /proc/self/status, or -1.
    private fun statusKb(name: String): Long =
        File("/proc/self/status").readLines().firstOrNull { it.startsWith("$name:") }
            ?.filter { it.isDigit() }?.toLongOrNull() ?: -1L

    private fun grantMediaPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetPackage = instrumentation.targetContext.packageName
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.forEach { permission ->
            try {
                instrumentation.uiAutomation.grantRuntimePermission(targetPackage, permission)
            } catch (_: Exception) {
                // The live-item check in the scenario reports a permission still missing.
            }
        }
    }

    private fun failTest(message: String): Nothing {
        Log.e(TAG, message)
        throw AssertionError(message)
    }

    private companion object {
        const val TAG = "FaceIndexPassMeasurement"
        const val OPT_IN_ARGUMENT = "faceIndexMeasurement"
        const val BUDGET_ARGUMENT = "faceIndexMeasurementBudgetMillis"
        const val DEFAULT_SHORT_BUDGET_MILLIS = 8_000L
        const val FULL_RUN_BUDGET_MILLIS = 60L * 60L * 1000L
        const val COUNT_PAGE_SIZE = 200
    }
}
