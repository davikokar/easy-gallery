package com.davide.seddio.easygallery.data

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.Random
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the gazetteer on the device against the real shipped assets (ADR-0019 Amendment 1 M2, M4)
 * and re-proves the bucketed search against the linear scan there. Needs no media access.
 * Capture the figures with `adb logcat -s GazetteerDeviceTest`.
 */
@RunWith(AndroidJUnit4::class)
class GazetteerDeviceTest {

    // The packaged assets belong to the app under test, not to the test APK.
    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun realAssetsLoadAndLogLoadTimeAndRetainedBytes() {
        val heapBefore = usedHeapBytes()
        val coldStartNanos = System.nanoTime()
        val gazetteer = GazetteerAssets.load(targetContext)
        val coldLoadMillis = millisSince(coldStartNanos)
        val heapAfter = usedHeapBytes()

        val warmStartNanos = System.nanoTime()
        val second = GazetteerAssets.load(targetContext)
        val warmLoadMillis = millisSince(warmStartNanos)

        // heapDeltaBytes is best effort: the platform may collect or grow the heap between the two reads.
        Log.i(
            TAG,
            "M4 load: coldLoadMs=$coldLoadMillis warmLoadMs=$warmLoadMillis places=${gazetteer.placeCount} " +
                "approxRetainedBytes=${gazetteer.approxRetainedBytes} heapDeltaBytes=${heapAfter - heapBefore} " +
                "datasetVersion=${gazetteer.datasetVersion} capKm=${gazetteer.capKm} sdk=${Build.VERSION.SDK_INT}"
        )

        assertTrue("Gazetteer loaded no places", gazetteer.placeCount > 0)
        assertEquals(PLACE_RESOLVER_VERSION, gazetteer.datasetVersion)
        assertEquals(GAZETTEER_CAP_KM, gazetteer.capKm, 0.0)
        assertTrue("approxRetainedBytes must be positive", gazetteer.approxRetainedBytes > 0L)
        assertEquals("A second load must see the same data", gazetteer.placeCount, second.placeCount)
    }

    @Test
    fun resolvesFiftyThousandDeterministicPointsAndLogsRowsPerSecond() {
        val gazetteer = GazetteerAssets.load(targetContext)
        val points = deterministicPoints(gazetteer, POINT_COUNT, THROUGHPUT_SEED)

        // Warm the JIT on a separate set so the timed pass is not dominated by interpreter time.
        val warmup = deterministicPoints(gazetteer, WARMUP_POINT_COUNT, WARMUP_SEED)
        for (i in 0 until warmup.size) gazetteer.nearest(warmup.latitudes[i], warmup.longitudes[i])

        var withPlace = 0
        var jitteredWithPlace = 0
        var jitteredCount = 0
        var farthestJitteredMatchKm = 0.0
        val matches = arrayOfNulls<GazetteerMatch>(points.size)
        val startNanos = System.nanoTime()
        for (i in 0 until points.size) {
            matches[i] = gazetteer.nearest(points.latitudes[i], points.longitudes[i])
        }
        val elapsedNanos = System.nanoTime() - startNanos

        for (i in 0 until points.size) {
            val match = matches[i]
            if (match != null) withPlace++
            if (points.nearARealPlace[i]) {
                jitteredCount++
                if (match != null) {
                    jitteredWithPlace++
                    farthestJitteredMatchKm = maxOf(farthestJitteredMatchKm, match.distanceKm)
                }
            }
        }
        val elapsedMillis = elapsedNanos / 1_000_000L
        val rowsPerSecond = if (elapsedNanos > 0L) points.size * 1_000_000_000L / elapsedNanos else -1L

        Log.i(
            TAG,
            "M2 throughput: points=${points.size} elapsedMs=$elapsedMillis rowsPerSec=$rowsPerSecond " +
                "withPlace=$withPlace outOfCap=${points.size - withPlace} jittered=$jitteredCount " +
                "jitteredWithPlace=$jitteredWithPlace farthestJitteredMatchKm=" +
                String.format(Locale.US, "%.2f", farthestJitteredMatchKm)
        )

        assertEquals("Every point within $JITTER_KM km of a real place must resolve", jitteredCount, jitteredWithPlace)
        assertTrue(
            "A point generated within $JITTER_KM km of a place matched $farthestJitteredMatchKm km away",
            farthestJitteredMatchKm <= JITTER_KM + JITTER_TOLERANCE_KM
        )
    }

    @Test
    fun bucketedSearchEqualsLinearScanOnSeededDevicePoints() {
        val gazetteer = GazetteerAssets.load(targetContext)
        val points = deterministicPoints(gazetteer, EQUIVALENCE_POINT_COUNT, EQUIVALENCE_SEED)

        var mismatches = 0
        var firstMismatch: String? = null
        var withPlace = 0
        val startNanos = System.nanoTime()
        for (i in 0 until points.size) {
            val latitude = points.latitudes[i]
            val longitude = points.longitudes[i]
            val bucketed = gazetteer.nearest(latitude, longitude)
            val linear = gazetteer.nearestLinear(latitude, longitude)
            if (bucketed != null) withPlace++
            if (bucketed != linear) {
                mismatches++
                // Coordinates are synthetic, so naming them is safe and makes the failure reproducible.
                if (firstMismatch == null) {
                    firstMismatch = "lat=$latitude lon=$longitude bucketed=$bucketed linear=$linear"
                }
            }
        }
        Log.i(
            TAG,
            "equivalence: points=${points.size} withPlace=$withPlace mismatches=$mismatches " +
                "elapsedMs=${millisSince(startNanos)}"
        )

        assertTrue("Some points must resolve, or the comparison proves nothing", withPlace > 0)
        assertTrue("Some points must be beyond the cap, or the null path is untested", withPlace < points.size)
        assertEquals("Bucketed search differs from the linear scan; first: $firstMismatch", 0, mismatches)
    }

    @Test
    fun florenceCoordinatesResolveToFlorenceItaly() {
        val gazetteer = GazetteerAssets.load(targetContext)

        // The Duomo, about a kilometre from the GeoNames point for Florence.
        val match = gazetteer.nearest(43.7731, 11.2560)

        Log.i(TAG, "known: florence match=$match")
        assertNotNull("Florence must resolve within the cap", match)
        assertEquals("IT", match!!.countryCode)
        assertEquals("Florence", match.placeName)
        assertTrue("Florence matched ${match.distanceKm} km away", match.distanceKm < 5.0)
    }

    @Test
    fun midPacificCoordinatesHaveNoPlaceWithinTheCap() {
        val gazetteer = GazetteerAssets.load(targetContext)

        val match = gazetteer.nearest(-30.0, -140.0)

        Log.i(TAG, "known: midPacific match=$match")
        assertNull("Open ocean must stay beyond the cap, not be filed under a far city", match)
    }

    private class QueryPoints(count: Int) {
        val latitudes = DoubleArray(count)
        val longitudes = DoubleArray(count)

        // True for points generated within JITTER_KM of a real place.
        val nearARealPlace = BooleanArray(count)
        val size: Int get() = latitudes.size
    }

    // Even indexes jitter around a real place, odd ones are uniform on the sphere; same seed, same points.
    private fun deterministicPoints(gazetteer: Gazetteer, count: Int, seed: Long): QueryPoints {
        val random = Random(seed)
        val points = QueryPoints(count)
        for (i in 0 until count) {
            if (i % 2 == 0) {
                val row = random.nextInt(gazetteer.placeCount)
                val placeLatitude = gazetteer.latitudeOf(row)
                val placeLongitude = gazetteer.longitudeOf(row)
                val distanceKm = random.nextDouble() * JITTER_KM
                val bearing = random.nextDouble() * 2.0 * Math.PI
                val latitude = (placeLatitude + Math.toDegrees(distanceKm * cos(bearing) / EARTH_RADIUS_KM))
                    .coerceIn(-90.0, 90.0)
                val cosLatitude = cos(Math.toRadians(placeLatitude)).coerceAtLeast(MIN_COS_LATITUDE)
                val longitude = placeLongitude +
                    Math.toDegrees(distanceKm * sin(bearing) / (EARTH_RADIUS_KM * cosLatitude))
                points.latitudes[i] = latitude
                points.longitudes[i] = wrapLongitude(longitude)
                points.nearARealPlace[i] = true
            } else {
                // asin of a uniform value in [-1, 1] gives uniform density on the sphere.
                points.latitudes[i] = Math.toDegrees(asin(random.nextDouble() * 2.0 - 1.0))
                points.longitudes[i] = random.nextDouble() * 360.0 - 180.0
            }
        }
        return points
    }

    private fun wrapLongitude(longitude: Double): Double {
        var wrapped = longitude
        while (wrapped > 180.0) wrapped -= 360.0
        while (wrapped < -180.0) wrapped += 360.0
        return wrapped
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        runtime.gc()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun millisSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    private companion object {
        const val TAG = "GazetteerDeviceTest"
        const val POINT_COUNT = 50_000
        const val WARMUP_POINT_COUNT = 2_000
        const val EQUIVALENCE_POINT_COUNT = 3_000
        const val THROUGHPUT_SEED = 20261110L
        const val WARMUP_SEED = 20261111L
        const val EQUIVALENCE_SEED = 20261112L
        const val JITTER_KM = 20.0

        // The jitter is planar and the match is spherical; a kilometre covers the difference at 20 km.
        const val JITTER_TOLERANCE_KM = 1.0
        const val EARTH_RADIUS_KM = GAZETTEER_EARTH_RADIUS_KM

        // Keeps the longitude offset finite for a place within a degree of a pole.
        const val MIN_COS_LATITUDE = 0.05
    }
}
