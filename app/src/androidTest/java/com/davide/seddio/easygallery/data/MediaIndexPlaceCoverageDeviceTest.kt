package com.davide.seddio.easygallery.data

import android.Manifest
import android.app.UiAutomation
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.EasyGalleryApp
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures ADR-0019 Amendment 1 M3 (coverage) and the device half of M5 (the cap) on the real
 * library, and checks the 2.6 invariant that no row with coordinates is left pending.
 *
 * Everything the test writes goes to a throwaway in-memory index database; the app's real index,
 * its watermarks and WorkManager are never touched. Only counts, distances and URIs are logged,
 * never a coordinate. Capture the figures with `adb logcat -s MediaIndexPlaceCoverageDeviceTest`.
 */
@RunWith(AndroidJUnit4::class)
class MediaIndexPlaceCoverageDeviceTest {

    @Test
    fun everyLocatedRowIsResolvedAndCoverageAndCapFiguresAreLogged() {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()

        try {
            allowRequiredMediaAppOps()
            grantRequiredPermissions()
            assertRequiredMediaPermissionsGranted()

            runBlocking { runScenario(app) }
        } finally {
            // Idempotent restore on every exit path, in the style of MediaIndexingWorkEnqueueTest. The location
            // grant is only ever added, never revoked: revoking a runtime permission can kill this process.
            allowRequiredMediaAppOps()
            grantRequiredPermissions()
            assertRequiredMediaPermissionsGranted()
        }
    }

    private suspend fun runScenario(app: EasyGalleryApp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            failTest(
                "ENVIRONMENT: ACCESS_MEDIA_LOCATION is not granted on API ${Build.VERSION.SDK_INT}, " +
                    "so the platform redacts GPS from every image and no coordinate can be indexed."
            )
        }

        val liveMedia = MediaStoreDataSource(app).getAllMedia()
        Log.i(TAG, "start: sdk=${Build.VERSION.SDK_INT} liveItems=${liveMedia.size}")
        if (liveMedia.isEmpty()) {
            failTest(
                "ENVIRONMENT: MediaStore returned zero live items to the app process, so coverage " +
                    "cannot be measured. Media permissions were granted and asserted."
            )
        }

        val indexDatabase = Room.inMemoryDatabaseBuilder(app, MediaIndexDatabase::class.java).build()
        try {
            val indexStore = RoomMediaIndexStore(indexDatabase)
            val watermarkStore = RoomMediaIndexWatermarkStore(indexDatabase.mediaIndexDao())

            // A first run needs an empty index and no watermark, or the batch would select nothing.
            assertTrue("Throwaway index must start empty", indexStore.observeAll().first().isEmpty())
            assertTrue("Throwaway watermark table must start empty", watermarkStore.readAll().isEmpty())

            val indexer = MediaIndexTierABatchIndexer(
                mediaRepository = MediaStoreDataSource(app),
                mediaIndexStore = indexStore,
                deltaDetector = MediaIndexDeltaDetector(
                    watermarkStore = watermarkStore,
                    snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(app)
                ),
                rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(app),
                reconciler = MediaIndexReconciler(indexStore),
                extractor = AndroidMediaIndexTierAExtractor(app)
            )

            val batch = indexer.indexDelta(Build.VERSION.SDK_INT)
            Log.i(
                TAG,
                "batch: selected=${batch.selectedRows} indexed=${batch.indexedRows} skipped=${batch.skippedRows} " +
                    "deleted=${batch.deletedRows} healed=${batch.healedRows}"
            )
            if (batch.selectedRows == 0 || batch.indexedRows == 0) {
                failTest(
                    "ENVIRONMENT: the batch selected=${batch.selectedRows} indexed=${batch.indexedRows} rows " +
                        "on a device with ${liveMedia.size} live items; nothing can be measured."
                )
            }

            val indexedRows = indexStore.observeAll().first()
            val locatedBefore = indexedRows.filter { it.hasCoordinates() }
            Log.i(
                TAG,
                "M3 coverage: liveItems=${liveMedia.size} indexedRows=${indexedRows.size} " +
                    "rowsWithCoordinates=${locatedBefore.size} " +
                    "shareOfLive=${percent(locatedBefore.size, liveMedia.size)} " +
                    "shareOfIndexed=${percent(locatedBefore.size, indexedRows.size)}"
            )
            if (locatedBefore.isEmpty()) {
                failTest(
                    "ENVIRONMENT: none of ${indexedRows.size} indexed rows has coordinates. The device's " +
                        "photos carry no GPS, or location access is withheld, so neither the resolver nor " +
                        "the cap can be exercised."
                )
            }

            val pendingBefore = pendingRows(indexStore, indexedRows.size)
            assertEquals(
                "On a fresh index every row with coordinates must be pending before the resolver runs",
                locatedBefore.size,
                pendingBefore.size
            )

            val resolver = MediaIndexPlaceResolver(
                store = indexStore,
                gazetteerLoader = { GazetteerAssets.load(app) }
            )
            val pass = resolver.resolvePending()
            Log.i(TAG, "resolvePass: ${pass.toLogLine()}")

            val rowsAfter = indexStore.observeAll().first()
            val locatedAfter = rowsAfter.filter { it.hasCoordinates() }
            val pendingAfter = pendingRows(indexStore, rowsAfter.size)
            // Two independent readings of the stored columns, so a half-written row fails the sum below.
            val withPlace = locatedAfter.count { it.derivedPlaceName != null }
            val outOfCap = locatedAfter.count {
                it.derivedPlaceName == null && it.derivedCountryCode == null && it.derivedRegionCode == null
            }
            val staleMarkers = locatedAfter.count { it.derivedPlaceResolverVersion != PLACE_RESOLVER_VERSION }

            Log.i(
                TAG,
                "resolved: rowsWithCoordinates=${locatedAfter.size} withPlace=$withPlace outOfCap=$outOfCap " +
                    "pendingBefore=${pendingBefore.size} pendingAfter=${pendingAfter.size} " +
                    "staleMarkers=$staleMarkers resolverVersion=$PLACE_RESOLVER_VERSION"
            )

            val widestGazetteer = GazetteerAssets.load(app, capKm = WIDE_CAP_KM)
            val nearestKm = locatedAfter.map { row ->
                // A cap past half the circumference reaches every point, so this is never null.
                checkNotNull(widestGazetteer.nearest(row.capturedLatitude!!, row.capturedLongitude!!)) {
                    "Wide-cap lookup returned null for ${row.mediaUri}"
                }.distanceKm
            }
            val sorted = nearestKm.sorted()
            Log.i(
                TAG,
                "M5 nearestKm: rows=${sorted.size} median=${format(percentile(sorted, 0.50))} " +
                    "p95=${format(percentile(sorted, 0.95))} p99=${format(percentile(sorted, 0.99))} " +
                    "max=${format(sorted.last())}"
            )
            Log.i(
                TAG,
                "M5 capSweep: rowsWithCoordinates=${sorted.size} " +
                    CAP_SWEEP_KM.joinToString(" ") { cap ->
                        "outOfCap${cap.toInt()}=${sorted.count { it > cap }}"
                    }
            )
            // Counts and URIs only; a person checks these by hand for whether the photo was taken somewhere remote.
            locatedAfter.zip(nearestKm)
                .filter { (_, distanceKm) -> distanceKm > GAZETTEER_CAP_KM }
                .take(MAX_OUT_OF_CAP_ROWS_LOGGED)
                .forEach { (row, distanceKm) ->
                    Log.i(TAG, "M5 outOfCapRow: uri=${row.mediaUri} nearestKm=${format(distanceKm)}")
                }

            assertEquals(
                "The resolve pass must have selected exactly the rows with coordinates",
                locatedBefore.size,
                pass.selectedRows
            )
            assertEquals("Nothing else writes this throwaway index, so no write may be skipped", 0, pass.skippedRows)
            assertTrue(
                "After the resolver, rows with coordinates are still pending at version " +
                    "$PLACE_RESOLVER_VERSION: ${pendingAfter.size}",
                pendingAfter.isEmpty()
            )
            assertEquals(
                "Every row with coordinates must carry the current resolver marker",
                0,
                staleMarkers
            )
            assertEquals(
                "withPlace + outOfCap must equal the rows with coordinates",
                locatedAfter.size,
                withPlace + outOfCap
            )
            assertEquals("The pass and the stored rows disagree on rows with a place", pass.resolvedWithPlaceRows, withPlace)
            assertEquals("The pass and the stored rows disagree on rows out of cap", pass.outOfCapRows, outOfCap)
            assertEquals(
                "Stored out-of-cap rows must equal the rows whose nearest place is farther than the cap",
                sorted.count { it > GAZETTEER_CAP_KM },
                outOfCap
            )
        } finally {
            indexDatabase.close()
        }
    }

    private fun MediaIndexEntry.hasCoordinates(): Boolean = capturedLatitude != null && capturedLongitude != null

    private suspend fun pendingRows(store: MediaIndexStore, rowCount: Int): List<MediaIndexEntry> =
        store.listPendingPlaceResolution(PLACE_RESOLVER_VERSION, null, rowCount.coerceAtLeast(1))

    private fun percent(part: Int, whole: Int): String =
        if (whole == 0) "n/a" else String.format(Locale.US, "%.1f%%", part * 100.0 / whole)

    private fun format(value: Double): String = String.format(Locale.US, "%.1f", value)

    // Nearest-rank percentile of an ascending, non-empty list.
    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun failTest(message: String): Nothing {
        Log.e(TAG, message)
        throw AssertionError(message)
    }

    private fun requiredMediaPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun requiredMediaAppOps(): List<String> {
        val ops = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableListOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
        } else {
            mutableListOf("READ_EXTERNAL_STORAGE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ops += "ACCESS_MEDIA_LOCATION"
        return ops
    }

    private fun hasPermission(permission: String): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.packageManager.checkPermission(permission, context.packageName) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun assertRequiredMediaPermissionsGranted() {
        val missing = requiredMediaPermissions().filterNot { hasPermission(it) }
        assertTrue("Missing required media runtime permissions: $missing", missing.isEmpty())
    }

    // ACCESS_MEDIA_LOCATION is checked by the scenario itself, so a withheld grant reads as an environment finding.
    private fun grantRequiredPermissions() {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val permissions = requiredMediaPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) permissions += Manifest.permission.ACCESS_MEDIA_LOCATION
        permissions.forEach { permission ->
            try {
                uiAutomation.grantRuntimePermission(targetPackage, permission)
            } catch (_: Exception) {
                // The checks that follow report any permission still missing.
            }
        }
    }

    private fun allowRequiredMediaAppOps() {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        requiredMediaAppOps().forEach { op ->
            executeShellCommand(uiAutomation, "cmd appops set --uid $targetPackage $op allow")
        }
    }

    private fun executeShellCommand(uiAutomation: UiAutomation, command: String) {
        uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                // Drain to EOF so the shell command has completed before the test continues.
                while (reader.readLine() != null) {
                    // Output is irrelevant.
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private companion object {
        const val TAG = "MediaIndexPlaceCoverageDeviceTest"

        // Past half the Earth's circumference (about 20,015 km), so every valid point has a nearest place.
        const val WIDE_CAP_KM = 25_000.0
        val CAP_SWEEP_KM = listOf(25.0, 50.0, 100.0, 150.0)
        const val MAX_OUT_OF_CAP_ROWS_LOGGED = 25
    }
}
