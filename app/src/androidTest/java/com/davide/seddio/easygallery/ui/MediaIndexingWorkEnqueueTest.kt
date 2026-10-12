package com.davide.seddio.easygallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.davide.seddio.easygallery.data.AndroidMediaStoreRowDeltaProvider
import com.davide.seddio.easygallery.data.AndroidMediaStoreVolumeSnapshotProvider
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.data.MediaIndexRunOutcome
import com.davide.seddio.easygallery.data.MediaIndexingWorker
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.MediaStoreDeltaApiBranch
import com.davide.seddio.easygallery.data.MediaIndexVolumeWatermark
import com.davide.seddio.easygallery.data.selectRowsForReindex
import com.davide.seddio.easygallery.data.WorkManagerMediaIndexingWorkScheduler
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class MediaIndexingWorkEnqueueTest {

    @Test
    fun indexNowEnqueuesRealWorkManagerRequest() {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        val workManager = WorkManager.getInstance(app)
        val viewModel = MediaIndexingViewModel(application = app)

        workManager.cancelUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK)
        workManager.pruneWork()

        val idsBefore = uniqueWorkIds(workManager)

        viewModel.indexNow()

        val enqueuedId = waitForNewWorkId(workManager, idsBefore)
        assertNotNull(
            "Expected indexNow() to enqueue a new WorkManager request",
            enqueuedId
        )

        val infos = workManager
            .getWorkInfosForUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK)
            .get(10, TimeUnit.SECONDS)

        val enqueuedInfo = infos.first { info -> info.id == enqueuedId }

        assertEquals(NetworkType.NOT_REQUIRED, enqueuedInfo.constraints.requiredNetworkType)
        assertFalse(enqueuedInfo.constraints.requiresBatteryNotLow())
        assertFalse(enqueuedInfo.constraints.requiresCharging())
        assertFalse(enqueuedInfo.constraints.requiresDeviceIdle())

        assertTrue(
            "Expected media index work to carry MediaIndexingWorker tag",
            enqueuedInfo.tags.contains(MediaIndexingWorker::class.java.name)
        )
    }

    @Test
    fun indexNowRunsWorkerToSuccessAndPreservesOrGrowsIndexRows() {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        val workManager = WorkManager.getInstance(app)
        val viewModel = MediaIndexingViewModel(application = app)
        val rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(app)
        val snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(app)

        allowRequiredMediaAppOps(app)
        grantRequiredMediaPermissions(app)
        assertRequiredMediaPermissionsGranted(app)

        runBlocking {
            app.mediaIndexStore.deleteAll()
            app.mediaIndexWatermarkStore.readAll().keys.forEach { volumeName ->
                app.mediaIndexWatermarkStore.remove(volumeName)
            }
        }

        workManager.cancelUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK)
        workManager.pruneWork()

        val visibleMediaCount = runBlocking { MediaStoreDataSource(app).getAllMedia().size }
        assertTrue(
            "MediaStore returned zero items to the app process before indexing. " +
                "This environment cannot prove indexing behavior.",
            visibleMediaCount > 0
        )

        val detection = app.createMediaIndexTierABatchIndexer()
            .let {
                // Read detection through the same concrete components the worker uses.
                val detector = com.davide.seddio.easygallery.data.MediaIndexDeltaDetector(
                    watermarkStore = app.mediaIndexWatermarkStore,
                    snapshotProvider = snapshotProvider
                )
                detector.detect(Build.VERSION.SDK_INT)
            }
        val snapshot = snapshotProvider.snapshot(Build.VERSION.SDK_INT)
        val observedRowsByVolume = snapshot.presentVolumes
            .associateWith { volumeName ->
                rowDeltaProvider.queryObservedRows(volumeName, snapshot.apiBranch).size
            }
        val observedRows = snapshot.presentVolumes.flatMap { volumeName ->
            rowDeltaProvider.queryObservedRows(volumeName, snapshot.apiBranch)
        }
        val selection = selectRowsForReindex(
            plan = detection.plan,
            storedWatermarks = detection.storedWatermarks,
            observedRows = observedRows
        )
        val observedTotal = observedRows.size
        val selectedPreflight = selection.rowsToReindex.size

        assertTrue(
            "Observed-row query returned zero rows. apiBranch=${snapshot.apiBranch}, presentVolumes=${snapshot.presentVolumes}, observedByVolume=$observedRowsByVolume",
            observedTotal > 0
        )
        assertTrue(
            "Selection preflight returned zero rows despite observed rows. " +
                "apiBranch=${snapshot.apiBranch}, observedTotal=$observedTotal, observedByVolume=$observedRowsByVolume, " +
                "discardSummary=${buildDiscardSummary(snapshot.apiBranch, detection.storedWatermarks, observedRows)}",
            selectedPreflight > 0
        )

        val beforeCount = runBlocking { app.mediaIndexStore.observeAll().first().size }
        val idsBefore = uniqueWorkIds(workManager)

        viewModel.indexNow()

        val enqueuedId = waitForNewWorkId(workManager, idsBefore)
        assertNotNull(
            "Expected indexNow() to enqueue a new WorkManager request",
            enqueuedId
        )

        val terminalInfo = waitForTerminalState(workManager, enqueuedId!!)
        assertNotNull("Timed out waiting for media-index work to finish", terminalInfo)
        assertEquals(WorkInfo.State.SUCCEEDED, terminalInfo!!.state)

        val afterCount = runBlocking { app.mediaIndexStore.observeAll().first().size }
        val runState = app.mediaIndexRunStateStore.state.value

        assertEquals(MediaIndexRunOutcome.SUCCESS, runState.lastRunOutcome)
        assertTrue(
            "Expected index row count to stay stable or increase: before=$beforeCount after=$afterCount",
            afterCount >= beforeCount
        )
        assertTrue(
            "Expected worker run to select non-zero rows on a media-bearing device. " +
                "visibleMediaCount=$visibleMediaCount observedTotal=$observedTotal selectedPreflight=$selectedPreflight runSelected=${runState.selectedRows}",
            runState.selectedRows > 0
        )
        assertTrue(
            "Expected worker run to index non-zero rows on a media-bearing device. " +
                "visibleMediaCount=$visibleMediaCount runSelected=${runState.selectedRows} runIndexed=${runState.indexedRows} runSkipped=${runState.skippedRows}",
            runState.indexedRows > 0
        )

        Log.i(
            TAG,
            "indexNow complete: beforeRows=$beforeCount afterRows=$afterCount visibleMedia=$visibleMediaCount apiBranch=${snapshot.apiBranch} presentVolumes=${snapshot.presentVolumes} observedByVolume=$observedRowsByVolume selectedPreflight=$selectedPreflight selected=${runState.selectedRows} indexed=${runState.indexedRows} skipped=${runState.skippedRows} deleted=${runState.deletedRows} healed=${runState.healedRows}"
        )
    }

    @Test
    fun permissionDeniedRunDoesNotAdvanceWatermarksAndLaterGrantedRunIndexes() {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        val workManager = WorkManager.getInstance(app)
        val viewModel = MediaIndexingViewModel(application = app)
        val rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(app)
        val snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(app)

        grantRequiredMediaPermissions(app)
        assertRequiredMediaPermissionsGranted(app)

        runBlocking {
            app.mediaIndexStore.deleteAll()
            app.mediaIndexWatermarkStore.readAll().keys.forEach { volumeName ->
                app.mediaIndexWatermarkStore.remove(volumeName)
            }
        }

        val baselineSnapshot = snapshotProvider.snapshot(Build.VERSION.SDK_INT)
        val baselineRows = baselineSnapshot.presentVolumes.flatMap { volumeName ->
            rowDeltaProvider.queryObservedRows(volumeName, baselineSnapshot.apiBranch)
        }
        val visibleMediaCount = runBlocking { MediaStoreDataSource(app).getAllMedia().size }
        assertTrue(
            "MediaStore returned zero items before denied/granted proof run. This environment cannot prove full indexing behavior.",
            visibleMediaCount > 0
        )
        assertTrue(
            "Observed-row query returned zero rows before denial run. This environment cannot prove watermark behavior.",
            baselineRows.isNotEmpty()
        )

        val baselineGenerationByVolume = baselineRows
            .groupBy { row -> row.volumeName }
            .mapValues { (_, rows) ->
                rows.maxOfOrNull { row -> row.generationModified ?: row.generationAdded ?: Long.MIN_VALUE }
            }
        val baselineDateByVolume = baselineRows
            .groupBy { row -> row.volumeName }
            .mapValues { (_, rows) -> rows.maxOf { row -> row.dateModifiedSeconds } }

        val sentinelWatermarks = baselineSnapshot.presentVolumes.associateWith { volumeName ->
            MediaIndexVolumeWatermark(
                version = "sentinel-$volumeName",
                generation = 0L,
                dateModifiedWatermarkSeconds = 0L
            )
        }

        runBlocking {
            sentinelWatermarks.forEach { (volumeName, watermark) ->
                app.mediaIndexWatermarkStore.write(volumeName, watermark)
            }
        }

        val beforeDeniedRun = runBlocking { app.mediaIndexWatermarkStore.readAll() }

        withDeniedMediaAccess(app) {
            assertRequiredMediaPermissionsDenied(app)

            workManager.cancelUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK)
            workManager.pruneWork()

            val idsBeforeDenied = uniqueWorkIds(workManager)
            viewModel.indexNow()

            val deniedWorkId = waitForNewWorkId(workManager, idsBeforeDenied)
            assertNotNull("Expected denied-permission run to enqueue work", deniedWorkId)

            val deniedTerminalInfo = waitForTerminalState(workManager, deniedWorkId!!)
            assertNotNull("Timed out waiting for denied-permission work to finish", deniedTerminalInfo)
            assertEquals(WorkInfo.State.FAILED, deniedTerminalInfo!!.state)

            val afterDeniedRun = runBlocking { app.mediaIndexWatermarkStore.readAll() }
            assertEquals(
                "Denied run must not mutate media-index watermarks",
                beforeDeniedRun,
                afterDeniedRun
            )
        }

        workManager.cancelUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK)
        workManager.pruneWork()

        val idsBeforeGranted = uniqueWorkIds(workManager)
        viewModel.indexNow()

        val grantedWorkId = waitForNewWorkId(workManager, idsBeforeGranted)
        assertNotNull("Expected granted run to enqueue work", grantedWorkId)

        val grantedTerminalInfo = waitForTerminalState(workManager, grantedWorkId!!)
        assertNotNull("Timed out waiting for granted work to finish", grantedTerminalInfo)
        assertEquals(WorkInfo.State.SUCCEEDED, grantedTerminalInfo!!.state)

        val afterGrantedRun = runBlocking { app.mediaIndexWatermarkStore.readAll() }
        val indexedRowsAfterGrantedRun = runBlocking { app.mediaIndexStore.observeAll().first().size }

        baselineSnapshot.presentVolumes.forEach { volumeName ->
            val watermark = afterGrantedRun[volumeName]
            assertNotNull("Expected watermark for volume $volumeName after granted run", watermark)

            val baselineGeneration = baselineGenerationByVolume[volumeName]
            if (baselineSnapshot.apiBranch == MediaStoreDeltaApiBranch.API_30_PLUS && baselineGeneration != null && baselineGeneration != Long.MIN_VALUE) {
                assertTrue(
                    "Expected generation watermark for $volumeName to advance to at least observed baseline. baseline=$baselineGeneration actual=${watermark!!.generation}",
                    (watermark!!.generation ?: Long.MIN_VALUE) >= baselineGeneration
                )
            }

            if (baselineSnapshot.apiBranch != MediaStoreDeltaApiBranch.API_30_PLUS) {
                val baselineDate = baselineDateByVolume[volumeName]
                if (baselineDate != null) {
                    assertTrue(
                        "Expected date watermark for $volumeName to advance to at least observed baseline. baseline=$baselineDate actual=${watermark!!.dateModifiedWatermarkSeconds}",
                        (watermark!!.dateModifiedWatermarkSeconds ?: Long.MIN_VALUE) >= baselineDate
                    )
                }
            }
        }

        val runState = app.mediaIndexRunStateStore.state.value
        assertEquals(MediaIndexRunOutcome.SUCCESS, runState.lastRunOutcome)
        assertTrue(
            "Expected granted run to select rows after denied run preserved watermarks. selected=${runState.selectedRows}",
            runState.selectedRows > 0
        )
        assertTrue(
            "Expected granted run to index rows after denied run preserved watermarks. indexed=${runState.indexedRows}",
            runState.indexedRows > 0
        )
        assertTrue(
            "Expected granted run to rebuild almost the full visible library after denied run. " +
                "visibleMedia=$visibleMediaCount indexedRowsAfterGrantedRun=$indexedRowsAfterGrantedRun",
            indexedRowsAfterGrantedRun >= (visibleMediaCount - 5)
        )

        Log.i(
            TAG,
            "permissionDenied proof: visibleMedia=$visibleMediaCount baselineObserved=${baselineRows.size} " +
                "grantedSelected=${runState.selectedRows} grantedIndexed=${runState.indexedRows} indexedRowsAfterGrantedRun=$indexedRowsAfterGrantedRun"
        )
    }

    private inline fun withDeniedMediaAccess(
        app: EasyGalleryApp,
        block: () -> Unit
    ) {
        denyRequiredMediaAppOps(app)
        try {
            block()
        } finally {
            // Idempotent restore: always force normal AppOps + runtime grants on every exit path.
            allowRequiredMediaAppOps(app)
            grantRequiredMediaPermissions(app)
            assertRequiredMediaPermissionsGranted(app)
        }
    }

    private fun assertRequiredMediaPermissionsGranted(app: EasyGalleryApp) {
        val packageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = requiredPermissions.filter { permission ->
            packageManager.checkPermission(permission, targetPackage) != PackageManager.PERMISSION_GRANTED
        }

        assertTrue(
            "Missing required media runtime permissions at worker-test start: $missing",
            missing.isEmpty()
        )
    }

    private fun grantRequiredMediaPermissions(app: EasyGalleryApp) {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val permissionsToGrant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        permissionsToGrant.forEach { permission ->
            try {
                uiAutomation.grantRuntimePermission(targetPackage, permission)
            } catch (_: Exception) {
                // Assertion below reports any permission still missing.
            }
        }
    }

    private fun executeShellPermissionCommand(
        uiAutomation: android.app.UiAutomation,
        command: String
    ) {
        uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                while (reader.readLine() != null) {
                    // Drain command output to EOF so the shell command completes.
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun assertRequiredMediaPermissionsDenied(app: EasyGalleryApp) {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val requiredOps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
        } else {
            listOf("READ_EXTERNAL_STORAGE")
        }

        val notDenied = requiredOps.filter { op ->
            readAppOpMode(targetPackage, op) != "ignore"
        }

        assertTrue(
            "Expected media AppOps to be denied before denied-run case, but these ops are not ignore: $notDenied",
            notDenied.isEmpty()
        )
    }

    private fun denyRequiredMediaAppOps(app: EasyGalleryApp) {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val ops = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
        } else {
            listOf("READ_EXTERNAL_STORAGE")
        }

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ops.forEach { op ->
            executeShellPermissionCommand(
                uiAutomation = uiAutomation,
                command = "cmd appops set --uid $targetPackage $op ignore"
            )
        }
    }

    private fun allowRequiredMediaAppOps(app: EasyGalleryApp) {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val ops = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
        } else {
            listOf("READ_EXTERNAL_STORAGE")
        }

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ops.forEach { op ->
            executeShellPermissionCommand(
                uiAutomation = uiAutomation,
                command = "cmd appops set --uid $targetPackage $op allow"
            )
        }
    }

    private fun readAppOpMode(packageName: String, op: String): String {
        val output = executeShellPermissionCommandForOutput(
            uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation,
            command = "cmd appops get --uid $packageName $op"
        )
        val modeRegex = Regex("""\b${Regex.escape(op)}:\s*([a-z_]+)\b""")
        return output
            .lineSequence()
            .mapNotNull { line -> modeRegex.find(line)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            .orEmpty()
    }

    private fun executeShellPermissionCommandForOutput(
        uiAutomation: android.app.UiAutomation,
        command: String
    ): String {
        return uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                reader.readText()
            }
        }
    }

    private fun buildDiscardSummary(
        apiBranch: MediaStoreDeltaApiBranch,
        storedWatermarks: Map<String, com.davide.seddio.easygallery.data.MediaIndexVolumeWatermark>,
        observedRows: List<com.davide.seddio.easygallery.data.MediaStoreObservedRowDelta>
    ): String {
        if (observedRows.isEmpty()) return "no-observed-rows"

        return when (apiBranch) {
            MediaStoreDeltaApiBranch.API_30_PLUS -> {
                var missingPreviousGeneration = 0
                var generationNotAdvanced = 0
                var missingObservedGeneration = 0
                observedRows.forEach { row ->
                    val previousGeneration = storedWatermarks[row.volumeName]?.generation
                    val observedGeneration = row.generationModified ?: row.generationAdded
                    when {
                        previousGeneration == null -> missingPreviousGeneration += 1
                        observedGeneration == null -> missingObservedGeneration += 1
                        observedGeneration <= previousGeneration -> generationNotAdvanced += 1
                    }
                }
                "api30+: missingPreviousGeneration=$missingPreviousGeneration missingObservedGeneration=$missingObservedGeneration generationNotAdvanced=$generationNotAdvanced"
            }

            MediaStoreDeltaApiBranch.API_29,
            MediaStoreDeltaApiBranch.API_28 -> {
                var missingDateWatermark = 0
                var dateNotAdvanced = 0
                observedRows.forEach { row ->
                    val watermark = storedWatermarks[row.volumeName]?.dateModifiedWatermarkSeconds
                    if (watermark == null) {
                        missingDateWatermark += 1
                    } else if (row.dateModifiedSeconds <= watermark) {
                        dateNotAdvanced += 1
                    }
                }
                "api28-29: missingDateWatermark=$missingDateWatermark dateNotAdvanced=$dateNotAdvanced"
            }
        }
    }

    private fun uniqueWorkIds(workManager: WorkManager): Set<UUID> {
        return workManager
            .getWorkInfosForUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK)
            .get(10, TimeUnit.SECONDS)
            .mapTo(LinkedHashSet()) { info -> info.id }
    }

    private fun waitForNewWorkId(
        workManager: WorkManager,
        idsBefore: Set<UUID>
    ): UUID? {
        repeat(20) {
            val infos = workManager
                .getWorkInfosForUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK)
                .get(10, TimeUnit.SECONDS)

            val newest = infos.firstOrNull { info ->
                !idsBefore.contains(info.id) && info.state != WorkInfo.State.CANCELLED
            }
            if (newest != null) {
                return newest.id
            }

            Thread.sleep(250)
        }

        return null
    }

    private fun waitForTerminalState(
        workManager: WorkManager,
        workId: UUID
    ): WorkInfo? {
        repeat(240) {
            val info = workManager
                .getWorkInfoById(workId)
                .get(10, TimeUnit.SECONDS)

            if (info != null && info.state.isFinished) {
                return info
            }

            Thread.sleep(500)
        }

        return null
    }

    private companion object {
        const val TAG = "MediaIndexingWorkTest"
    }
}
