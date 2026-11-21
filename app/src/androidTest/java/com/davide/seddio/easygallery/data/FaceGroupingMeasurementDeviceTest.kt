package com.davide.seddio.easygallery.data

import android.Manifest
import android.app.ActivityManager
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the grouping (albums.md §8 decisions 45 and 50) against ADR-0023 §5's budget: on the device's real faces, found by
 * running the existing pass in the process over a throwaway in-memory index (the app's own index, its settings and the
 * disclosure flag are never touched, and nothing persists), and on seeded synthetic sets. Per run: wall time per phase
 * (the component pass, the triangles, the clustering, the representatives), the largest connected component at the cut,
 * the peak RSS and Java heap above the level just before the grouping, thermal status and charging. For the real faces
 * also the groups, and how many of them are offered (two or more distinct images).
 *
 * Opt-in: skipped unless the instrumentation argument [OPT_IN_ARGUMENT] is `true`, e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.faceGroupingMeasurement=true`. [REAL_ARGUMENT] (`false` skips the real
 * faces) and [SIZES_ARGUMENT] (comma-separated, default `2000,5000,10000,20000`) narrow it, so that one size can be run in
 * a fresh process. Aggregates only: no URI, id, box, embedding, crop or group is logged — `adb logcat -s FaceGroupingMeasurement`.
 */
@RunWith(AndroidJUnit4::class)
class FaceGroupingMeasurementDeviceTest {

    @Test
    fun groupingFiguresAreLoggedForTheRealFacesAndSyntheticSets() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Opt-in: pass the instrumentation argument $OPT_IN_ARGUMENT=true to run this measurement",
            arguments.getString(OPT_IN_ARGUMENT) == "true"
        )
        val sizes = (arguments.getString(SIZES_ARGUMENT) ?: DEFAULT_SIZES).split(',').mapNotNull { it.trim().toIntOrNull() }
        val app = ApplicationProvider.getApplicationContext<Context>()

        grantMediaPermissions()
        logEnvironment(app)
        runBlocking {
            if (arguments.getString(REAL_ARGUMENT) != "false") measureRealFaces(app)
            measureSyntheticSets(app, sizes)
        }
    }

    // ---- the real faces ----

    private suspend fun measureRealFaces(app: Context) {
        val mediaRepository = MediaStoreDataSource(app)
        val live = mediaRepository.getAllMedia()
        Log.i(TAG, "real start: sdk=${Build.VERSION.SDK_INT} liveItems=${live.size}")
        if (live.isEmpty()) failTest("ENVIRONMENT: MediaStore returned zero live items to the app process.")

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
            val pass = FaceIndexPass(
                store = store,
                mediaRepository = mediaRepository,
                settingStore = InMemoryFaceDataSettingStore(initiallyEnabled = true),
                pipelineLoader = { OpenCvFacePipeline.load(app) },
                log = FaceIndexLog { },
                budgetMillis = PASS_BUDGET_MILLIS
            )
            val startedAt = SystemClock.elapsedRealtime()
            val passResult = pass.run(System.currentTimeMillis())
            Log.i(
                TAG,
                "real pass: indexedRows=${batch.indexedRows} images=${passResult.indexedImages + passResult.failedImages} " +
                    "stop=${passResult.stop} wallMillis=${SystemClock.elapsedRealtime() - startedAt}"
            )
            assertEquals("The pass must finish for the real faces to be complete", FaceIndexPassStop.COMPLETED, passResult.stop)

            val stored = ArrayList<StoredFace>()
            for (row in store.observeAll().first()) stored.addAll(store.listFaces(row.mediaUri))
            if (stored.isEmpty()) failTest("ENVIRONMENT: the pass found no face on this device, so nothing can be grouped.")
            val inputs = stored.map { FaceGroupingInput(it.id, it.embedding) }

            val run = runGrouping(app, "real", inputs)
            val groups = run.groups ?: failTest("The grouping of the real faces did not finish: ${run.outcome}")
            assertEquals("Every face must be in exactly one group", stored.size, groups.sumOf { it.memberFaceIds.size })
            assertEquals(
                "The split must give the groups and representatives of one triangle over every face",
                groupFacesWithoutSplit(inputs)!!.groups,
                groups
            )

            val imageOf = stored.associate { it.id to it.mediaUri }
            val offered = groups.filter { group -> group.memberFaceIds.map { imageOf.getValue(it) }.toSet().size >= 2 }
            Log.i(
                TAG,
                "real groups: faces=${stored.size} images=${stored.map { it.mediaUri }.toSet().size} groups=${groups.size} " +
                    "groupsWithTwoOrMoreImages=${offered.size} groupsOfOne=${groups.count { it.memberFaceIds.size == 1 }} " +
                    "largestGroup=${groups.maxOf { it.memberFaceIds.size }}"
            )
        } finally {
            database.close()
        }
    }

    // ---- the synthetic sets ----

    private suspend fun measureSyntheticSets(app: Context, sizes: List<Int>) {
        for (size in sizes.sorted()) {
            val faces = syntheticFaces(size, SEED + size)
            val run = runGrouping(app, "synthetic$size", faces)
            if (run.groups != null && size <= UNSPLIT_CHECK_MAX_FACES) {
                val identical = groupFacesWithoutSplit(faces)!!.groups == run.groups
                Log.i(TAG, "identity label=synthetic$size identicalToOneTriangle=$identical")
                assertEquals("The split must give the groups of one triangle over every face", true, identical)
            }
            val reason = stopReason(run)
            if (reason != null) {
                Log.w(TAG, "sequence stopped after size=$size reason=$reason")
                break
            }
        }
    }

    // Identities of heavy-tailed size (Pareto, at least two faces), each a random unit centre plus noise.
    private fun syntheticFaces(count: Int, seed: Long): List<FaceGroupingInput> {
        val random = java.util.Random(seed)
        val noise = sqrt(WITHIN_DISTANCE / (1.0 - WITHIN_DISTANCE))
        val vectors = ArrayList<FloatArray>(count)
        val identityOf = ArrayList<Int>(count)
        var identity = 0
        while (vectors.size < count) {
            val size = min(count - vectors.size, min(MAX_IDENTITY_SIZE, (2.0 / Math.pow(1.0 - random.nextDouble(), 1.0 / PARETO_ALPHA)).toInt()))
            val centre = DoubleArray(FACE_EMBEDDING_SIZE) { random.nextGaussian() }
            val norm = sqrt(centre.sumOf { it * it })
            repeat(size) {
                vectors.add(FloatArray(FACE_EMBEDDING_SIZE) {
                    (centre[it] / norm + noise * random.nextGaussian() / sqrt(FACE_EMBEDDING_SIZE.toDouble())).toFloat()
                })
                identityOf.add(identity)
            }
            identity += 1
        }

        val sizes = identityOf.groupingBy { it }.eachCount().values
        val within = ArrayList<Double>()
        val between = ArrayList<Double>()
        val byIdentity = identityOf.indices.groupBy { identityOf[it] }.values.filter { it.size >= 2 }
        repeat(CALIBRATION_PAIRS) {
            val members = byIdentity[random.nextInt(byIdentity.size)]
            val first = members[random.nextInt(members.size)]
            var second = members[random.nextInt(members.size)]
            while (second == first) second = members[random.nextInt(members.size)]
            within.add(cosineDistance(vectors[first], vectors[second]))
            var left = random.nextInt(count)
            var right = random.nextInt(count)
            while (identityOf[left] == identityOf[right]) {
                left = random.nextInt(count)
                right = random.nextInt(count)
            }
            between.add(cosineDistance(vectors[left], vectors[right]))
        }
        Log.i(
            TAG,
            "synthetic set: faces=$count identities=$identity meanIdentitySize=${format(count.toDouble() / identity)} " +
                "largestIdentity=${sizes.max()} identitiesOfTwo=${sizes.count { it == 2 }} noise=${format(noise)} " +
                "meanWithinDistance=${format(within.average())} meanBetweenDistance=${format(between.average())}"
        )
        return vectors.mapIndexed { index, vector -> FaceGroupingInput(index + 1L, FaceEmbedding(vector)) }.shuffled(random)
    }

    private fun cosineDistance(first: FloatArray, second: FloatArray): Double {
        var dot = 0.0
        var firstSquares = 0.0
        var secondSquares = 0.0
        for (k in first.indices) {
            dot += first[k].toDouble() * second[k]
            firstSquares += first[k].toDouble() * first[k]
            secondSquares += second[k].toDouble() * second[k]
        }
        return 1.0 - dot / (sqrt(firstSquares) * sqrt(secondSquares))
    }

    // Null while the sequence may go on; the BUDGET figures are judged by the reader against ADR-0023 §5.
    private fun stopReason(run: GroupingRun): String? = when {
        run.outcome != "ok" -> run.outcome
        run.thermalAfter >= THERMAL_STOP -> "thermalStatus=${run.thermalAfter}"
        else -> null
    }

    // ---- one run: phases, memory, thermal ----

    private class GroupingRun(val outcome: String, val groups: List<FaceGroup>?, val thermalAfter: Int)

    private class Phases {
        var normaliseMillis = 0L
        var componentsMillis = 0L
        var triangleMillis = 0L
        var clusteringMillis = 0L
        var groupsMillis = 0L
        var componentCount = 0
        var singletonComponents = 0
        var largestComponent = 0
    }

    private suspend fun runGrouping(app: Context, label: String, faces: List<FaceGroupingInput>): GroupingRun {
        settleMemory()
        val peakReset = resetPeakRss()
        val rssBefore = statusKb("VmRSS")
        val hwmBefore = statusKb("VmHWM")
        val heapBefore = heapUsedKb()
        val thermalBefore = thermalStatus(app)
        val charging = isCharging(app)
        val monitor = ResourceMonitor(app, rssBefore, heapBefore)
        val phases = Phases()
        var outcome = "ok"
        var groups: List<FaceGroup>? = null

        val startedAt = SystemClock.elapsedRealtime()
        monitor.start()
        try {
            groups = groupInPhases(faces, phases) { monitor.abortReason == null }
            if (groups == null) outcome = monitor.abortReason ?: "stopped"
        } catch (_: OutOfMemoryError) {
            outcome = "OutOfMemoryError"
        } finally {
            monitor.stop()
        }
        val wallMillis = SystemClock.elapsedRealtime() - startedAt

        val rssAfter = statusKb("VmRSS")
        val hwmAfter = statusKb("VmHWM")
        val heapAfter = heapUsedKb()
        val thermalAfter = thermalStatus(app)
        // After a successful reset VmHWM is exactly this run's peak; without one it is a peak only if it rose.
        val peakKb = max(monitor.rssPeakKb, if (peakReset || hwmAfter > hwmBefore) hwmAfter else 0L)
        val activityManager = app.getSystemService(ActivityManager::class.java)
        val groupingMillis = phases.normaliseMillis + phases.componentsMillis + phases.triangleMillis +
            phases.clusteringMillis + phases.groupsMillis
        val largestTriangleMb = triangleBytes(phases.largestComponent) / MB

        Log.i(
            TAG,
            "run label=$label faces=${faces.size} outcome=$outcome groups=${groups?.size ?: -1} components=${phases.componentCount} " +
                "singletonComponents=${phases.singletonComponents} largestComponent=${phases.largestComponent} " +
                "largestComponentTriangleMb=$largestTriangleMb normaliseMillis=${phases.normaliseMillis} " +
                "componentsMillis=${phases.componentsMillis} triangleMillis=${phases.triangleMillis} " +
                "clusteringMillis=${phases.clusteringMillis} groupsMillis=${phases.groupsMillis} groupingMillis=$groupingMillis " +
                "wallMillis=$wallMillis"
        )
        Log.i(
            TAG,
            "memory label=$label peakResetWorked=$peakReset rssBeforeKb=$rssBefore hwmBeforeKb=$hwmBefore " +
                "rssPeakSampledKb=${monitor.rssPeakKb} hwmAfterKb=$hwmAfter rssAfterKb=$rssAfter " +
                "peakRiseKb=${max(0L, peakKb - rssBefore)} heapBeforeKb=$heapBefore heapPeakSampledKb=${monitor.heapPeakKb} " +
                "heapAfterKb=$heapAfter heapRiseKb=${max(0L, monitor.heapPeakKb - heapBefore)} " +
                "maxHeapMb=${Runtime.getRuntime().maxMemory() / MB} memoryClassMb=${activityManager.memoryClass} " +
                "largeMemoryClassMb=${activityManager.largeMemoryClass}"
        )
        Log.i(
            TAG,
            "thermal label=$label before=$thermalBefore after=$thermalAfter peak=${monitor.thermalPeak} charging=$charging"
        )
        return GroupingRun(outcome, groups, thermalAfter)
    }

    // The phases of groupFaces, one by one so each can be timed: the component pass, then each component's triangle,
    // clustering and representatives, in the order and with the checks groupNormalised has.
    private suspend fun groupInPhases(faces: List<FaceGroupingInput>, phases: Phases, mayContinue: () -> Boolean): List<FaceGroup>? {
        val clock = PhaseClock()
        val normalised = normaliseFaces(faces)
        phases.normaliseMillis = clock.lap()
        val components = connectedComponents(normalised, mayContinue) ?: return null
        phases.componentsMillis = clock.lap()
        phases.componentCount = components.size
        phases.singletonComponents = components.count { it.size == 1 }
        phases.largestComponent = components.maxOfOrNull { it.size } ?: 0

        val groups = ArrayList<FaceGroup>()
        for (ranks in components) {
            if (ranks.size == 1) {
                groups.add(FaceGroup(normalised.faceIds[ranks[0]], listOf(normalised.faceIds[ranks[0]])))
                continue
            }
            if (!mayContinue()) return null
            groups.addAll(groupComponentInPhases(normalised, ranks, phases, clock, mayContinue) ?: return null)
        }
        groups.sortBy { it.groupId }
        return groups
    }

    // Its own function so the component's triangle is garbage as soon as its groups are known, as in groupNormalised.
    private suspend fun groupComponentInPhases(
        faces: NormalisedFaces,
        ranks: IntArray,
        phases: Phases,
        clock: PhaseClock,
        mayContinue: () -> Boolean
    ): List<FaceGroup>? {
        clock.lap()
        val triangle = faceDistanceTriangle(faces, ranks, mayContinue) ?: return null
        phases.triangleMillis += clock.lap()
        val roots = averageLinkageClusters(ranks.size, triangle, FACE_GROUPING_CUT, mayContinue) ?: return null
        phases.clusteringMillis += clock.lap()
        val groups = faceGroupsOf(faces, ranks, roots, mayContinue)
        phases.groupsMillis += clock.lap()
        return groups
    }

    private class PhaseClock {
        private var mark = SystemClock.elapsedRealtime()

        fun lap(): Long {
            val now = SystemClock.elapsedRealtime()
            return (now - mark).also { mark = now }
        }
    }

    // The float32 triangle of a component of this many faces.
    private fun triangleBytes(faces: Int): Long = faces.toLong() * (faces - 1) / 2 * Float.SIZE_BYTES

    // Samples the process while a run is going and asks it to stop when the plan's limits are passed.
    private class ResourceMonitor(private val app: Context, private val rssBeforeKb: Long, private val heapBeforeKb: Long) {
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
                now - startedAt > RUN_TIME_LIMIT_MILLIS -> "timeLimit5Minutes"
                rssPeakKb - rssBeforeKb > MEMORY_RISE_LIMIT_KB -> "rssRiseOver1.5GB"
                heapPeakKb - heapBeforeKb > MEMORY_RISE_LIMIT_KB -> "heapRiseOver1.5GB"
                thermalPeak >= THERMAL_STOP -> "thermalStatus=$thermalPeak"
                else -> null
            }
            if (reason != null && abortReason == null) abortReason = reason
        }
    }

    // ---- the device ----

    private fun logEnvironment(app: Context) {
        val activityManager = app.getSystemService(ActivityManager::class.java)
        Log.i(
            TAG,
            "environment: sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL} maxHeapMb=${Runtime.getRuntime().maxMemory() / MB} " +
                "memoryClassMb=${activityManager.memoryClass} largeMemoryClassMb=${activityManager.largeMemoryClass} " +
                "thermal=${thermalStatus(app)} charging=${isCharging(app)}"
        )
    }

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

    private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)

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
                // The live-item check reports a permission still missing.
            }
        }
    }

    private fun failTest(message: String): Nothing {
        Log.e(TAG, message)
        throw AssertionError(message)
    }

    private companion object {
        const val TAG = "FaceGroupingMeasurement"
        const val OPT_IN_ARGUMENT = "faceGroupingMeasurement"
        const val REAL_ARGUMENT = "faceGroupingMeasurementReal"
        const val SIZES_ARGUMENT = "faceGroupingMeasurementSizes"
        const val DEFAULT_SIZES = "2000,5000,10000,20000"

        // One triangle over every face is 8 MB at 2,000 faces and 50 MB at 5,000; beyond that it is the memory being avoided.
        const val UNSPLIT_CHECK_MAX_FACES = 2_000

        const val PASS_BUDGET_MILLIS = 60L * 60L * 1000L
        const val SEED = 20_261_120L

        // 1 - 1 / (1 + noise^2) is the expected cosine distance of two points of one identity in 128 dimensions.
        const val WITHIN_DISTANCE = 0.35
        const val PARETO_ALPHA = 1.6
        const val MAX_IDENTITY_SIZE = 60
        const val CALIBRATION_PAIRS = 2_000

        const val RUN_TIME_LIMIT_MILLIS = 5L * 60L * 1000L
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
