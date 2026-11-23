package com.davide.seddio.easygallery.data

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 3.7, G5c (items 11, 12 and 15), synthetic: what the grouping pass and the face queries cost at a production-size
 * library, through the real Room store over a throwaway file-backed index ([MediaIndexThrowawayDatabase]). Per size, the
 * seeded faces are random unit-centred embeddings of heavy-tailed identities (the generator of
 * [FaceGroupingMeasurementDeviceTest]) on invented images, two faces to an image, written through the store's own
 * `upsert` and `writeFaceIndexResults`. Then the real [FaceGroupingPass] runs over them, behind a store that times the
 * two calls it makes into the database, the paged read and the one-transaction write, so that the compute phase is
 * what is left. After it: one emission of [MediaIndexStore.observeFaceEvaluationProjection] three times, and the People
 * picker's [MediaIndexStore.listFaceGroups] query. The real library at this size stays out of reach (plan 3.7, L5).
 *
 * Opt-in: skipped unless the instrumentation argument [OPT_IN_ARGUMENT] is `true`, e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.mediaIndexTierCScale=true`; [SIZES_ARGUMENT] (comma-separated, default
 * `10000,20000,37000`) narrows it. Numbers and fixed words only, tag [TAG]: nothing here is face data, and the files
 * are deleted afterwards. It asserts that each set was grouped completely; the figures are judged by the reader, and a
 * run that does not finish is recorded as such and stops the sequence rather than failing the test.
 */
@RunWith(AndroidJUnit4::class)
class MediaIndexTierCScaleDeviceTest {

    @Test
    fun groupingAndTheFaceQueriesAreMeasuredThroughTheRealStoreAtSyntheticScale() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Opt-in: pass the instrumentation argument $OPT_IN_ARGUMENT=true to run this measurement",
            arguments.getString(OPT_IN_ARGUMENT) == "true"
        )
        val sizes = (arguments.getString(SIZES_ARGUMENT) ?: DEFAULT_SIZES).split(',').mapNotNull { it.trim().toIntOrNull() }
        check(sizes.isNotEmpty() && sizes.all { it > 0 }) { "$SIZES_ARGUMENT must list positive sizes" }
        val app = ApplicationProvider.getApplicationContext<Context>()
        val activityManager = app.getSystemService(ActivityManager::class.java)
        Log.i(
            TAG,
            "environment: sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL} maxHeapMb=${Runtime.getRuntime().maxMemory() / MB} " +
                "memoryClassMb=${activityManager.memoryClass} thermal=${thermalStatus(app)} charging=${isCharging(app)}"
        )
        Log.i(
            TAG,
            "window: facePassBudgetMillis=$FACE_PASS_BUDGET_MILLIS groupingStartDeadlineMillis=$FACE_GROUPING_START_DEADLINE_MILLIS " +
                "facePassPageSize=$FACE_PASS_PAGE_SIZE groupingPageSize=$FACE_GROUPING_PAGE_SIZE"
        )

        for (size in sizes.sorted()) {
            val outcome = measure(app, size)
            if (outcome != "ok") {
                Log.w(TAG, "sequence stopped after size=$size reason=$outcome")
                break
            }
        }
    }

    // Returns "ok", or the reason the run stopped before the grouping finished.
    private fun measure(app: Context, size: Int): String = runBlocking {
        MediaIndexThrowawayDatabase.open(app).use { index ->
            val store = index.store
            val embeddings = syntheticEmbeddings(size, SEED + size)

            val seedStart = SystemClock.elapsedRealtime()
            val images = seed(store, embeddings)
            val seedMillis = SystemClock.elapsedRealtime() - seedStart
            Log.i(TAG, "seed size=$size faces=${embeddings.size} images=$images seedMillis=$seedMillis dbBytes=${index.files().sumOf { it.length() }}")

            val settingStore = InMemoryFaceDataSettingStore(initiallyEnabled = true)
            val timing = TimingStore(store)
            val pass = FaceGroupingPass(timing, settingStore, FaceIndexLog { })

            settleMemory()
            val peakReset = resetPeakRss()
            val rssBefore = statusKb("VmRSS")
            val hwmBefore = statusKb("VmHWM")
            val heapBefore = heapUsedKb()
            val thermalBefore = thermalStatus(app)
            val charging = isCharging(app)
            val monitor = ResourceMonitor(app, settingStore, rssBefore, heapBefore)

            var stop: FaceGroupingStop? = null
            var outcome = "ok"
            val startedAt = SystemClock.elapsedRealtime()
            monitor.start()
            try {
                stop = pass.run(System.currentTimeMillis())
                if (stop != FaceGroupingStop.GROUPED) outcome = monitor.abortReason ?: "stop=$stop"
            } catch (_: OutOfMemoryError) {
                outcome = "OutOfMemoryError"
            } finally {
                monitor.stop()
            }
            val totalMillis = SystemClock.elapsedRealtime() - startedAt
            val hwmAfter = statusKb("VmHWM")
            val peakKb = max(monitor.rssPeakKb, if (peakReset || hwmAfter > hwmBefore) hwmAfter else 0L)

            Log.i(
                TAG,
                "grouping size=$size outcome=$outcome stop=${stop ?: "none"} totalMillis=$totalMillis readMillis=${timing.readMillis} " +
                    "readPages=${timing.readPages} writeMillis=${timing.writeMillis} " +
                    "computeMillis=${totalMillis - timing.readMillis - timing.writeMillis} peakResetWorked=$peakReset"
            )
            Log.i(
                TAG,
                "memory size=$size rssBeforeKb=$rssBefore peakRiseKb=${max(0L, peakKb - rssBefore)} heapBeforeKb=$heapBefore " +
                    "heapRiseKb=${max(0L, monitor.heapPeakKb - heapBefore)} maxHeapMb=${Runtime.getRuntime().maxMemory() / MB}"
            )
            Log.i(TAG, "thermal size=$size before=$thermalBefore after=${thermalStatus(app)} peak=${monitor.thermalPeak} charging=$charging")
            if (outcome != "ok") return@use outcome

            assertFalse("Grouping must not be needed after it", store.isFaceGroupingNeeded(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION))
            measureQueries(store, size, embeddings.size)
            "ok"
        }
    }

    private suspend fun measureQueries(store: RoomMediaIndexStore, size: Int, faces: Int) {
        val emissionMillis = ArrayList<Long>()
        var snapshotImages = 0
        var snapshotFaces = 0
        var allGrouped = true
        repeat(EMISSIONS) {
            settleMemory()
            val startedAt = SystemClock.elapsedRealtime()
            val snapshot = store.observeFaceEvaluationProjection(FACE_PIPELINE_VERSION).first()
            emissionMillis.add(SystemClock.elapsedRealtime() - startedAt)
            snapshotImages = snapshot.images.size
            snapshotFaces = snapshot.images.sumOf { it.faces.size }
            allGrouped = snapshot.images.all { image -> image.faces.all { it.groupId != null && it.groupingVersion == FACE_GROUPING_VERSION } }
        }
        Log.i(
            TAG,
            "projection size=$size faces=$faces snapshotImages=$snapshotImages snapshotFaces=$snapshotFaces " +
                "emissionMillis=${emissionMillis.joinToString(",")} heapNowKb=${heapUsedKb()}"
        )
        assertEquals("The projection must carry every seeded face", faces, snapshotFaces)
        assertTrue("Every face must carry a group at the current versions", allGrouped)

        var offered = 0
        val offeredMillis = timed { offered = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2).size }
        var groups = 0
        val groupsMillis = timed { groups = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 1).size }
        Log.i(
            TAG,
            "pickerQuery size=$size groups=$groups groupsMillis=$groupsMillis groupsOfTwoOrMoreImages=$offered offeredMillis=$offeredMillis"
        )
        assertTrue("The picker's query must return the groups", groups > 0)
    }

    private inline fun timed(block: () -> Unit): Long {
        val startedAt = SystemClock.elapsedRealtime()
        block()
        return SystemClock.elapsedRealtime() - startedAt
    }

    // ---- seeding ----

    // Two faces to an image, on invented URIs; the rows are Tier A rows the face tables join to.
    private suspend fun seed(store: RoomMediaIndexStore, embeddings: List<FaceEmbedding>): Int {
        val images = (embeddings.size + FACES_PER_IMAGE - 1) / FACES_PER_IMAGE
        (1..images).chunked(ROW_CHUNK).forEach { numbers -> store.upsert(numbers.map { row(it) }) }
        (1..images).chunked(WRITE_CHUNK).forEach { numbers ->
            val writes = numbers.map { number ->
                val faces = (0 until FACES_PER_IMAGE).mapNotNull { slot ->
                    embeddings.getOrNull((number - 1) * FACES_PER_IMAGE + slot)?.let { FaceDetection(FACE_BOX, it) }
                }
                FaceIndexWrite(row(number).toMediaIdentity(), FACE_PIPELINE_VERSION, FaceIndexOutcome.INDEXED, faces)
            }
            assertEquals("Every seeded image must apply", writes.size, store.writeFaceIndexResults(writes, faceDataEnabled = { true }))
        }
        return images
    }

    private fun row(number: Int): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = "content://media/external_primary/images/media/$number",
            displayName = "synthetic$number.jpg",
            size = 1_000L + number,
            dateModified = 2_000L + number,
            volumeName = "external_primary",
            generationAdded = null,
            generationModified = null,
            capturedDateTakenEpochMillis = BASE_DATE_MILLIS + number * DAY_MILLIS,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )
    }

    // Identities of heavy-tailed size (Pareto, at least two faces), each a random unit centre plus noise, shuffled.
    private fun syntheticEmbeddings(count: Int, seed: Long): List<FaceEmbedding> {
        val random = Random(seed)
        val noise = sqrt(WITHIN_DISTANCE / (1.0 - WITHIN_DISTANCE))
        val vectors = ArrayList<FloatArray>(count)
        while (vectors.size < count) {
            val size = min(count - vectors.size, min(MAX_IDENTITY_SIZE, (2.0 / Math.pow(1.0 - random.nextDouble(), 1.0 / PARETO_ALPHA)).toInt()))
            val centre = DoubleArray(FACE_EMBEDDING_SIZE) { random.nextGaussian() }
            val norm = sqrt(centre.sumOf { it * it })
            repeat(size) {
                vectors.add(FloatArray(FACE_EMBEDDING_SIZE) {
                    (centre[it] / norm + noise * random.nextGaussian() / sqrt(FACE_EMBEDDING_SIZE.toDouble())).toFloat()
                })
            }
        }
        return vectors.map { FaceEmbedding(it) }.shuffled(random)
    }

    // ---- timing the two calls the pass makes into the database ----

    private class TimingStore(private val inner: MediaIndexStore) : MediaIndexStore by inner {
        var readMillis = 0L
        var readPages = 0
        var writeMillis = 0L

        override suspend fun listFacesForGrouping(pipelineVersion: Int, afterFaceId: Long?, limit: Int): List<FaceGroupingInput> {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                return inner.listFacesForGrouping(pipelineVersion, afterFaceId, limit)
            } finally {
                readMillis += SystemClock.elapsedRealtime() - startedAt
                readPages += 1
            }
        }

        override suspend fun writeFaceGroups(
            groups: List<FaceGroup>,
            pipelineVersion: Int,
            groupingVersion: Int,
            faceDataEnabled: () -> Boolean
        ): Int {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                return inner.writeFaceGroups(groups, pipelineVersion, groupingVersion, faceDataEnabled)
            } finally {
                writeMillis += SystemClock.elapsedRealtime() - startedAt
            }
        }
    }

    // Samples the process while a run is going; at a limit it switches the face-data setting off, which the pass reads between phases.
    private class ResourceMonitor(
        private val app: Context,
        private val settingStore: InMemoryFaceDataSettingStore,
        private val rssBeforeKb: Long,
        private val heapBeforeKb: Long
    ) {
        @Volatile var abortReason: String? = null
        @Volatile var rssPeakKb: Long = rssBeforeKb
        @Volatile var heapPeakKb: Long = heapBeforeKb
        @Volatile var thermalPeak: Int = thermalStatus(app)
        @Volatile private var running = true
        private val startedAt = SystemClock.elapsedRealtime()
        private var lastThermalCheck = startedAt
        private val thread = Thread { loop() }.apply { isDaemon = true }

        fun start() = thread.start()

        fun stop() {
            running = false
            thread.join()
        }

        private fun loop() {
            while (running) {
                sample()
                SystemClock.sleep(SAMPLE_INTERVAL_MILLIS)
            }
            sample()
        }

        private fun sample() {
            val now = SystemClock.elapsedRealtime()
            rssPeakKb = max(rssPeakKb, statusKb("VmRSS"))
            heapPeakKb = max(heapPeakKb, heapUsedKb())
            if (now - lastThermalCheck >= THERMAL_CHECK_MILLIS) {
                lastThermalCheck = now
                thermalPeak = max(thermalPeak, thermalStatus(app))
            }
            val reason = when {
                now - startedAt > RUN_TIME_LIMIT_MILLIS -> "timeLimit12Minutes"
                rssPeakKb - rssBeforeKb > MEMORY_RISE_LIMIT_KB -> "rssRiseOver1.5GB"
                heapPeakKb - heapBeforeKb > MEMORY_RISE_LIMIT_KB -> "heapRiseOver1.5GB"
                thermalPeak >= THERMAL_STOP -> "thermalStatus=$thermalPeak"
                else -> null
            }
            if (reason != null && abortReason == null) {
                abortReason = reason
                settingStore.setEnabled(false)
            }
        }
    }

    // ---- the device ----

    private fun settleMemory() {
        repeat(2) {
            System.gc()
            System.runFinalization()
            SystemClock.sleep(SETTLE_MILLIS)
        }
    }

    // Writing 5 to clear_refs resets VmHWM to the current RSS, where the kernel and SELinux allow it to an app.
    private fun resetPeakRss(): Boolean {
        try {
            File("/proc/self/clear_refs").writeText("5")
        } catch (_: Exception) {
            return false
        }
        return statusKb("VmHWM") <= statusKb("VmRSS") + RESET_TOLERANCE_KB
    }

    private companion object {
        const val TAG = "MediaIndexTierCScale"
        const val OPT_IN_ARGUMENT = "mediaIndexTierCScale"
        const val SIZES_ARGUMENT = "mediaIndexTierCScaleSizes"
        const val DEFAULT_SIZES = "10000,20000,37000"

        const val SEED = 20_261_122L
        const val FACES_PER_IMAGE = 2
        const val ROW_CHUNK = 500
        const val WRITE_CHUNK = 250
        const val EMISSIONS = 3
        const val BASE_DATE_MILLIS = 1_600_000_000_000L
        const val DAY_MILLIS = 86_400_000L
        val FACE_BOX = FaceBox(0.2f, 0.2f, 0.5f, 0.6f)

        // 1 - 1 / (1 + noise^2) is the expected cosine distance of two points of one identity in 128 dimensions.
        const val WITHIN_DISTANCE = 0.35
        const val PARETO_ALPHA = 1.6
        const val MAX_IDENTITY_SIZE = 60

        const val RUN_TIME_LIMIT_MILLIS = 12L * 60L * 1000L
        const val MEMORY_RISE_LIMIT_KB = 1_536L * 1024L
        const val THERMAL_STOP = 3
        const val SAMPLE_INTERVAL_MILLIS = 25L
        const val THERMAL_CHECK_MILLIS = 1_000L
        const val SETTLE_MILLIS = 400L
        const val RESET_TOLERANCE_KB = 8_192L
        const val MB = 1024L * 1024L

        // One "Name:   123 kB" line of /proc/self/status, or -1.
        fun statusKb(name: String): Long =
            File("/proc/self/status").readLines().firstOrNull { it.startsWith("$name:") }
                ?.filter { it.isDigit() }?.toLongOrNull() ?: -1L

        fun heapUsedKb(): Long {
            val runtime = Runtime.getRuntime()
            return (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        }

        fun thermalStatus(app: Context): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                app.getSystemService(PowerManager::class.java).currentThermalStatus
            } else {
                -1
            }

        fun isCharging(app: Context): Boolean = app.getSystemService(BatteryManager::class.java).isCharging
    }
}
