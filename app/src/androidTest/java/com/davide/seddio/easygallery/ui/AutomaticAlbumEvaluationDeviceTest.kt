package com.davide.seddio.easygallery.ui

import android.Manifest
import android.app.UiAutomation
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AlbumRuleJson
import com.davide.seddio.easygallery.data.AndroidMediaIndexTierAExtractor
import com.davide.seddio.easygallery.data.AndroidMediaStoreRowDeltaProvider
import com.davide.seddio.easygallery.data.AndroidMediaStoreVolumeSnapshotProvider
import com.davide.seddio.easygallery.data.EasyGalleryDatabase
import com.davide.seddio.easygallery.data.InMemoryMediaStoreVersionStore
import com.davide.seddio.easygallery.data.MediaIndexDatabase
import com.davide.seddio.easygallery.data.MediaIndexDeltaDetector
import com.davide.seddio.easygallery.data.MediaIndexReconciler
import com.davide.seddio.easygallery.data.MediaIndexTierABatchIndexer
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.RoomAlbumStore
import com.davide.seddio.easygallery.data.RoomMediaIndexStore
import com.davide.seddio.easygallery.data.RoomMediaIndexWatermarkStore
import com.davide.seddio.easygallery.data.canonicalMediaStoreUriForJoin
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Stage 2 step 2.5 evaluator against the real index pipeline and real MediaStore.
 *
 * Everything the test writes goes to throwaway in-memory databases; the app's real index
 * database, album database and WorkManager are never touched. Capture counts with
 * `adb logcat -s AutoAlbumEvalDeviceTest`.
 */
@RunWith(AndroidJUnit4::class)
class AutomaticAlbumEvaluationDeviceTest {

    @Test
    fun timeRuleAlbumFillsFromRealIndexBatchWithNoOtherTrigger() {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        val zone = ZoneId.systemDefault()

        try {
            allowRequiredMediaAppOps()
            grantRequiredMediaPermissions()
            assertRequiredMediaPermissionsGranted()

            runBlocking { runScenario(app, zone) }
        } finally {
            // Idempotent restore on every exit path, in the style of MediaIndexingWorkEnqueueTest.
            allowRequiredMediaAppOps()
            grantRequiredMediaPermissions()
            assertRequiredMediaPermissionsGranted()
        }
    }

    private suspend fun runScenario(app: EasyGalleryApp, zone: ZoneId) {
        val liveMedia = MediaStoreDataSource(app).getAllMedia()
        Log.i(TAG, "start: liveItems=${liveMedia.size} zone=$zone sdk=${Build.VERSION.SDK_INT}")
        if (liveMedia.isEmpty()) {
            failTest(
                "ENVIRONMENT: MediaStore returned zero live items to the app process, so no " +
                    "evaluation can be proved. Media permissions were granted and asserted."
            )
        }

        // The rule is fixed before the batch so one album can be observed before and after it.
        val targetDay = chooseTargetDay(app, liveMedia, zone)

        val indexDatabase = Room.inMemoryDatabaseBuilder(app, MediaIndexDatabase::class.java).build()
        val albumDatabase = Room.inMemoryDatabaseBuilder(app, EasyGalleryDatabase::class.java).build()
        var viewModel: AlbumsViewModel? = null
        try {
            val indexStore = RoomMediaIndexStore(indexDatabase)
            val watermarkStore = RoomMediaIndexWatermarkStore(indexDatabase.mediaIndexDao())
            val albumStore = RoomAlbumStore(albumDatabase)

            // A first run needs an empty index and no watermark, or the batch would select nothing.
            assertTrue(
                "Throwaway index must start empty",
                indexStore.observeAll().first().isEmpty()
            )
            assertTrue(
                "Throwaway watermark table must start empty",
                watermarkStore.readAll().isEmpty()
            )

            val indexer = MediaIndexTierABatchIndexer(
                mediaRepository = MediaStoreDataSource(app),
                mediaIndexStore = indexStore,
                deltaDetector = MediaIndexDeltaDetector(
                    watermarkStore = watermarkStore,
                    snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(app)
                ),
                rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(app),
                reconciler = MediaIndexReconciler(indexStore),
                extractor = AndroidMediaIndexTierAExtractor(app, zoneId = zone)
            )

            // An in-memory version store means the version check can never bump the evaluation epoch.
            val vm = AlbumsViewModel(
                application = app,
                albumStore = albumStore,
                mediaStoreVersionStore = InMemoryMediaStoreVersionStore(),
                mediaIndexStore = indexStore,
                zoneProvider = { zone },
                evaluationDispatcher = Dispatchers.Default
            )
            viewModel = vm
            vm.setAllMedia(liveMedia)

            val rule = AlbumRule.Evaluatable(
                listOf(AlbumRuleClause.Time(from = targetDay, to = targetDay))
            )
            val encodedRule = AlbumRuleJson.encode(rule)
            assertEquals(
                "Encoded rule must parse back to the same rule",
                rule,
                AlbumRuleJson.parse(encodedRule)
            )
            val albumId = albumStore.createAlbum(
                name = ALBUM_NAME,
                createdAt = System.currentTimeMillis(),
                rule = encodedRule
            )
            val before = withTimeoutOrNull(AWAIT_MILLIS) {
                vm.albums.first { items -> items.any { it.id == albumId } }
            }?.first { it.id == albumId }
                ?: failTest("Album $albumId never appeared in AlbumsViewModel.albums before the batch")
            assertEquals(
                "Before the index batch the album must be WAITING_ON_INDEX (never COMPLETE)",
                AlbumEvaluationStatus.WAITING_ON_INDEX,
                before.status
            )
            assertEquals("No member can exist before indexing", 0, before.resolvedMemberCount)
            // Selecting an id the ViewModel has not emitted yet makes it clear the selection again.
            vm.selectAlbum(albumId)

            val batch = indexer.indexDelta(Build.VERSION.SDK_INT)
            Log.i(
                TAG,
                "batch: selected=${batch.selectedRows} indexed=${batch.indexedRows} " +
                    "skipped=${batch.skippedRows} deleted=${batch.deletedRows} healed=${batch.healedRows}"
            )
            if (batch.selectedRows == 0 || batch.indexedRows == 0) {
                failTest(
                    "ENVIRONMENT: the batch selected=${batch.selectedRows} indexed=${batch.indexedRows} " +
                        "rows on a device with ${liveMedia.size} live items; nothing can be proved."
                )
            }

            // Independent expectation: index rows and live media only, no evaluator.
            val rows = indexStore.observeAll().first()
            val datedRows = rows.count { it.capturedDateTakenEpochMillis != null }
            val undatedRows = rows.size - datedRows
            val rowByLiveUri = rows.associateBy { canonicalMediaStoreUriForJoin(it.mediaUri) }
            fun indexedDateOf(item: MediaItem): Long? =
                rowByLiveUri[canonicalMediaStoreUriForJoin(item.uri.toString())]
                    ?.capturedDateTakenEpochMillis
            fun dayOf(epochMillis: Long): LocalDate =
                Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

            val liveUris = liveMedia.mapTo(HashSet()) { it.uri.toString() }
            val expectedUris = liveMedia
                .filter { item -> indexedDateOf(item)?.let { dayOf(it) == targetDay } == true }
                .mapTo(HashSet()) { it.uri.toString() }
            val undatedLiveUris = liveMedia
                .filter { item -> indexedDateOf(item) == null }
                .mapTo(HashSet()) { it.uri.toString() }

            Log.i(
                TAG,
                "counts: liveItems=${liveMedia.size} indexedRows=${rows.size} datedRows=$datedRows " +
                    "undatedRows=$undatedRows undatedOrUnindexedLive=${undatedLiveUris.size} " +
                    "targetDay=$targetDay zone=$zone expectedMembers=${expectedUris.size}"
            )
            if (datedRows == 0) {
                failTest(
                    "ENVIRONMENT: none of ${rows.size} indexed rows carries a date-taken, so a " +
                        "time rule cannot match anything."
                )
            }
            if (expectedUris.isEmpty()) {
                failTest(
                    "The pre-scan found a dated item on $targetDay but no live item has an indexed " +
                        "date on that day (datedRows=$datedRows). Index and extractor disagree, or " +
                        "the library changed during the run."
                )
            }

            // From here nothing touches the ViewModel or the album store: the index emission alone drives it.
            val after = withTimeoutOrNull(AWAIT_MILLIS) {
                vm.albums.first { items ->
                    items.any { it.id == albumId && it.status == AlbumEvaluationStatus.COMPLETE }
                }
            }?.first { it.id == albumId }
                ?: failTest(
                    "Album never became COMPLETE after the batch with no other trigger. " +
                        "last=${vm.albums.value.firstOrNull { it.id == albumId }} " +
                        "liveItems=${liveMedia.size} indexedRows=${rows.size}"
                )

            Log.i(
                TAG,
                "after: statusAfter=${after.status} listMemberCount=${after.resolvedMemberCount} " +
                    "selectedAlbumId=${vm.selectedAlbumId.value} albumId=$albumId"
            )
            val members = withTimeoutOrNull(AWAIT_MILLIS) {
                vm.selectedAlbumMembers.first { it.size == expectedUris.size }
            } ?: failTest(
                "Member count never reached the independent expectation. " +
                    "expected=${expectedUris.size} actual=${vm.selectedAlbumMembers.value.size} " +
                    "targetDay=$targetDay"
            )

            val rangeStartMillis = targetDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val rangeEndMillis = targetDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val memberUris = members.map { it.mediaItem.uri.toString() }

            Log.i(
                TAG,
                "result: liveItems=${liveMedia.size} indexedRows=${rows.size} datedRows=$datedRows " +
                    "members=${members.size} expected=${expectedUris.size} statusAfter=${after.status}"
            )

            assertEquals("Members must be unique", memberUris.size, memberUris.toSet().size)
            assertTrue("Album must hold at least one member", members.isNotEmpty())
            assertEquals("Album list count must match the detail members", members.size, after.resolvedMemberCount)
            members.forEach { member ->
                val uri = member.mediaItem.uri.toString()
                assertTrue("Member $uri is not a live item", uri in liveUris)
                assertNull("Member $uri is a rule match, not an addition", member.membershipId)

                val indexedDate = indexedDateOf(member.mediaItem)
                assertNotNull("Member $uri has no indexed date", indexedDate)
                assertTrue(
                    "Member $uri indexed date $indexedDate is outside [$rangeStartMillis, $rangeEndMillis) in $zone",
                    indexedDate!! >= rangeStartMillis && indexedDate < rangeEndMillis
                )
                assertEquals("Member $uri falls on the wrong day in $zone", targetDay, dayOf(indexedDate))
            }
            assertTrue(
                "No member may be an item with a null or missing indexed date",
                memberUris.none { it in undatedLiveUris }
            )
            assertEquals(
                "Members must equal the independently computed set (index rows joined to live media)",
                expectedUris,
                memberUris.toSet()
            )
        } finally {
            // Stop the ViewModel's collectors before the databases they read are closed.
            viewModel?.let { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
            indexDatabase.close()
            albumDatabase.close()
        }
    }

    /** The most common day among the first dated live items, read by the production extractor in [zone]. */
    private suspend fun chooseTargetDay(
        app: EasyGalleryApp,
        liveMedia: List<MediaItem>,
        zone: ZoneId
    ): LocalDate {
        val extractor = AndroidMediaIndexTierAExtractor(app, zoneId = zone)
        val itemsPerDay = HashMap<LocalDate, Int>()
        var datedItems = 0
        for (item in liveMedia) {
            val dateTaken = extractor.extract(item).capturedDateTakenEpochMillis ?: continue
            val day = Instant.ofEpochMilli(dateTaken).atZone(zone).toLocalDate()
            itemsPerDay[day] = (itemsPerDay[day] ?: 0) + 1
            datedItems += 1
            if (datedItems >= PRESCAN_DATED_ITEMS) break
        }
        Log.i(TAG, "prescan: datedItems=$datedItems distinctDays=${itemsPerDay.size}")
        if (itemsPerDay.isEmpty()) {
            failTest(
                "ENVIRONMENT: none of ${liveMedia.size} live items yields an EXIF/container " +
                    "date-taken, so a time rule cannot be exercised on this device."
            )
        }
        return itemsPerDay.entries.maxWith(compareBy({ it.value }, { it.key })).key
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
        } else {
            listOf("READ_EXTERNAL_STORAGE")
        }
    }

    private fun assertRequiredMediaPermissionsGranted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missing = requiredMediaPermissions().filter { permission ->
            context.packageManager.checkPermission(permission, context.packageName) !=
                PackageManager.PERMISSION_GRANTED
        }

        assertTrue("Missing required media runtime permissions: $missing", missing.isEmpty())
    }

    private fun grantRequiredMediaPermissions() {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        requiredMediaPermissions().forEach { permission ->
            try {
                uiAutomation.grantRuntimePermission(targetPackage, permission)
            } catch (_: Exception) {
                // The assertion that follows reports any permission still missing.
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
        const val TAG = "AutoAlbumEvalDeviceTest"
        const val ALBUM_NAME = "AutoAlbumEvalDeviceTest time rule"
        const val AWAIT_MILLIS = 60_000L
        const val PRESCAN_DATED_ITEMS = 50
    }
}
