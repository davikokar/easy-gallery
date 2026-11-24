package com.davide.seddio.easygallery.data

import android.Manifest
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.davide.seddio.easygallery.EasyGalleryApp
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real [MediaIndexingWorker] through the production Index now path (WorkManager, the app's singletons, the
 * real index and the real preference stores, the production budgets) until the face pass completes, and checks that
 * the faces are grouped and that a further run groups nothing. Plan 3.4, Task 5.
 *
 * Opt-in: skipped unless the instrumentation argument [OPT_IN_ARGUMENT] is `true`, e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.faceGroupingWorkerMeasurement=true`. It marks the face-data disclosure
 * shown through the real store, which only test sources may do; the dev phone is the one device the app is installed
 * on, so the face data stays on it and is deleted again at the end. Whatever was there before (the setting, the
 * disclosure and capability record) is put back exactly, and a second step then checks it. The worker's own lines
 * (tag [FACE_TAG]) are read back with `logcat -d` after a unique marker; only counts, stop names and times are logged,
 * under [TAG].
 */
@RunWith(AndroidJUnit4::class)
class FaceGroupingWorkerMeasurementDeviceTest {

    @Test
    fun workerGroupsTheRealLibraryAndTheRunIsRestored() {
        assumeTrue(
            "Opt-in: pass the instrumentation argument $OPT_IN_ARGUMENT=true to run this measurement",
            InstrumentationRegistry.getArguments().getString(OPT_IN_ARGUMENT) == "true"
        )
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        val workManager = WorkManager.getInstance(app)
        val before = snapshot(app)
        Log.i(
            TAG,
            "preconditions: settingPresent=${before.settingPresent} settingEnabled=${before.settingEnabled} " +
                "disclosureShown=${before.disclosureShown} capabilityRecorded=${before.capability != null} " +
                "hadFaceData=${before.hadFaceData}"
        )

        // Restoring and checking it are separate steps from the measurement, and none can hide another's failure.
        val bodyFailure = runCatching { measure(app, workManager) }.exceptionOrNull()
        val restoreFailure = runCatching { restore(app, workManager, before) }.exceptionOrNull()
        val verifyFailure = runCatching { assertRestored(app, before) }.exceptionOrNull()

        val failures = listOfNotNull(bodyFailure, restoreFailure, verifyFailure)
        if (failures.isNotEmpty()) {
            val first = failures.first()
            failures.drop(1).forEach { first.addSuppressed(it) }
            throw first
        }
    }

    // ---- the measurement ----

    private fun measure(app: EasyGalleryApp, workManager: WorkManager) {
        val testDeadline = SystemClock.elapsedRealtime() + TOTAL_LIMIT_MILLIS
        grantMediaPermissions()
        val liveItems = runBlocking { MediaStoreDataSource(app).getAllMedia().size }
        Log.i(
            TAG,
            "environment: sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL} liveItems=$liveItems " +
                "thermal=${thermalStatus(app)} charging=${isCharging(app)}"
        )
        if (liveItems == 0) failTest("ENVIRONMENT: MediaStore returned zero live items to the app process.")

        val store = app.mediaIndexStore
        runBlocking {
            if (store.hasFaceData()) {
                val deletion = store.deleteAllFaceData()
                Log.i(TAG, "face data present at the start was deleted: rows=${deletion.rowsDeleted} scrubbed=${deletion.scrubbed}")
            }
        }
        app.faceDataSettingStore.setEnabled(true)
        app.faceDeviceStateStore.markDisclosureShown()

        val runs = ArrayList<WorkerRun>()
        while (true) {
            val run = runWorker(app, workManager, "run${runs.size + 1}", testDeadline)
            runs.add(run)
            val pass = run.pass ?: failTest("Run ${runs.size} logged no face pass line: notRun=${run.notRunReason}")
            if (pass.stop == "COMPLETED") break
            if (pass.stop != "BUDGET") failTest("Run ${runs.size} stopped the face pass for ${pass.stop}")
            if (runs.size >= MAX_RUNS) failTest("The face pass did not complete in $MAX_RUNS runs")
        }
        Log.i(TAG, "pass completed after ${runs.size} run(s)")

        val lastRun = runs.last()
        runBlocking {
            val rows = store.observeAll().first()
            val faces = ArrayList<StoredFace>()
            for (row in rows) faces.addAll(store.listFaces(row.mediaUri))
            val facesForGrouping = store.listFacesForGrouping(FACE_PIPELINE_VERSION, null, MAX_FACES)
            val pendingImages = store.listPendingFaceImages(FACE_PIPELINE_VERSION, null, MAX_FACES).size
            val groupingNeeded = store.isFaceGroupingNeeded(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION)
            val groups = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 1)
            val offered = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2)

            val imagesWithFaces = faces.map { it.mediaUri }.toSet().size
            val distinctImagesByGroup = faces.groupBy { it.groupId }.mapValues { (_, members) -> members.map { it.mediaUri }.toSet().size }
            Log.i(
                TAG,
                "final: indexRows=${rows.size} faces=${faces.size} imagesWithFaces=$imagesWithFaces groups=${groups.size} " +
                    "offered=${offered.size} groupsOfOneFace=${faces.groupBy { it.groupId }.count { it.value.size == 1 }} " +
                    "groupingNeeded=$groupingNeeded stillPendingImages=$pendingImages " +
                    "lastRunGroupingStop=${lastRun.groupingStop} groupingStageMillis=${lastRun.groupingStageMillis}"
            )

            assertTrue("The device's library produced no face: nothing can be grouped", faces.isNotEmpty())
            assertEquals("Every stored face must be listed for grouping", faces.size, facesForGrouping.size)
            assertFalse("A face is still pending grouping after the last run", groupingNeeded)
            assertTrue("Every face must carry a group at the current versions", faces.all { it.groupId != null && it.groupingVersion == FACE_GROUPING_VERSION })
            assertTrue("No group was offered or stored", groups.isNotEmpty())
            assertEquals(
                "Groups with two or more distinct images must be the picker's offered count",
                distinctImagesByGroup.count { it.value >= 2 },
                offered.size
            )
            assertEquals("Every stored group must appear in the picker's query at one image", distinctImagesByGroup.size, groups.size)

            // A run with nothing pending: grouping is not needed and nothing changes.
            val again = runWorker(app, workManager, "noop", testDeadline)
            assertEquals("A run with nothing to group must say so", "NOT_NEEDED", again.groupingStop)
            assertEquals("The face pass of the repeat run indexed nothing", 0, again.pass?.images)
            val facesAfter = store.listFacesForGrouping(FACE_PIPELINE_VERSION, null, MAX_FACES).size
            val groupsAfter = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 1)
            assertEquals("The repeat run must not change the faces", facesForGrouping.size, facesAfter)
            assertEquals("The repeat run must not change the groups", groups, groupsAfter)
            assertFalse(
                "The repeat run left grouping needed",
                store.isFaceGroupingNeeded(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION)
            )
            Log.i(TAG, "noop run: groupingStop=${again.groupingStop} wallMillis=${again.wallMillis} passImages=${again.pass?.images}")
        }
    }

    // ---- one worker run ----

    private class PassLine(val images: Int, val indexed: Int, val failed: Int, val skipped: Int, val stop: String)

    private class WorkerRun(
        val wallMillis: Long,
        val pass: PassLine?,
        val notRunReason: String?,
        val groupingStop: String?,
        val capabilityToPassEndMillis: Long?,
        val groupingStageMillis: Long?
    )

    private fun runWorker(app: EasyGalleryApp, workManager: WorkManager, label: String, testDeadline: Long): WorkerRun {
        val marker = UUID.randomUUID().toString().take(8)
        Log.i(TAG, "marker=$marker label=$label")
        val peakReset = resetPeakRss()
        val rssBefore = statusKb("VmRSS")
        val hwmBefore = statusKb("VmHWM")
        val thermalBefore = thermalStatus(app)
        val charging = isCharging(app)
        val idsBefore = uniqueWorkIds(workManager)

        val startedAt = SystemClock.elapsedRealtime()
        app.mediaIndexingWorkScheduler.enqueueIndexNow()
        val workId = waitForNewWorkId(workManager, idsBefore) ?: failTest("Index now enqueued no work")
        val finished = waitForFinished(app, workManager, workId, testDeadline)
        val wallMillis = SystemClock.elapsedRealtime() - startedAt

        val hwmAfter = statusKb("VmHWM")
        val rssAfter = statusKb("VmRSS")
        val thermalAfter = thermalStatus(app)
        val peakKb = if (peakReset || hwmAfter > hwmBefore) hwmAfter else 0L
        val runState = app.mediaIndexRunStateStore.state.value

        val log = readRunLog(marker)
        Log.i(
            TAG,
            "$label: workState=${finished.state} runOutcome=${runState.lastRunOutcome} wallMillis=$wallMillis " +
                "tierASelected=${runState.selectedRows} tierAIndexed=${runState.indexedRows} tierASkipped=${runState.skippedRows} " +
                "passImages=${log.pass?.images} passIndexed=${log.pass?.indexed} passFailed=${log.pass?.failed} " +
                "passSkipped=${log.pass?.skipped} passStop=${log.pass?.stop} notRun=${log.notRunReason} " +
                "groupingStop=${log.groupingStop} capabilityToPassEndMillis=${log.capabilityToPassEndMillis} " +
                "groupingStageMillis=${log.groupingStageMillis}"
        )
        Log.i(
            TAG,
            "$label memory: peakResetWorked=$peakReset rssBeforeKb=$rssBefore hwmBeforeKb=$hwmBefore hwmAfterKb=$hwmAfter " +
                "rssAfterKb=$rssAfter peakRiseKb=${max(0L, peakKb - rssBefore)} thermalBefore=$thermalBefore " +
                "thermalAfter=$thermalAfter thermalPeak=${finished.thermalPeak} charging=$charging"
        )

        assertEquals("$label: the worker must succeed", WorkInfo.State.SUCCEEDED, finished.state)
        assertEquals("$label: the run state must say success", MediaIndexRunOutcome.SUCCESS, runState.lastRunOutcome)
        return WorkerRun(wallMillis, log.pass, log.notRunReason, log.groupingStop, log.capabilityToPassEndMillis, log.groupingStageMillis)
    }

    private class Finished(val state: WorkInfo.State, val thermalPeak: Int)

    // Polls until the work ends; cancels it and fails at the thermal stop or either time limit.
    private fun waitForFinished(app: EasyGalleryApp, workManager: WorkManager, workId: UUID, testDeadline: Long): Finished {
        val runDeadline = SystemClock.elapsedRealtime() + RUN_LIMIT_MILLIS
        var thermalPeak = thermalStatus(app)
        var lastThermalCheck = SystemClock.elapsedRealtime()
        while (true) {
            val info = workManager.getWorkInfoById(workId).get(10, TimeUnit.SECONDS)
            if (info != null && info.state.isFinished) return Finished(info.state, thermalPeak)

            val now = SystemClock.elapsedRealtime()
            if (now - lastThermalCheck >= THERMAL_CHECK_MILLIS) {
                lastThermalCheck = now
                thermalPeak = max(thermalPeak, thermalStatus(app))
            }
            val reason = when {
                thermalPeak >= THERMAL_STOP -> "thermalStatus=$thermalPeak"
                now > runDeadline -> "a run exceeded ${RUN_LIMIT_MILLIS / 60_000} minutes"
                now > testDeadline -> "the test exceeded ${TOTAL_LIMIT_MILLIS / 60_000} minutes"
                else -> null
            }
            if (reason != null) {
                workManager.cancelWorkById(workId)
                failTest("Stopped: $reason")
            }
            SystemClock.sleep(POLL_MILLIS)
        }
    }

    private fun uniqueWorkIds(workManager: WorkManager): Set<UUID> =
        workManager.getWorkInfosForUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_NOW_WORK)
            .get(10, TimeUnit.SECONDS)
            .mapTo(LinkedHashSet()) { it.id }

    private fun waitForNewWorkId(workManager: WorkManager, idsBefore: Set<UUID>): UUID? {
        repeat(40) {
            val infos = workManager.getWorkInfosForUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_NOW_WORK)
                .get(10, TimeUnit.SECONDS)
            infos.firstOrNull { it.id !in idsBefore && it.state != WorkInfo.State.CANCELLED }?.let { return it.id }
            SystemClock.sleep(250)
        }
        return null
    }

    // ---- the worker's own lines ----

    private class RunLog(
        val pass: PassLine?,
        val notRunReason: String?,
        val groupingStop: String?,
        val capabilityToPassEndMillis: Long?,
        val groupingStageMillis: Long?
    )

    private fun readRunLog(marker: String): RunLog {
        val lines = shell("logcat -d -v threadtime -s $FACE_TAG:I $TAG:I").lines()
        val markerIndex = lines.indexOfLast { it.contains("marker=$marker") }
        if (markerIndex < 0) failTest("The run marker $marker is not in logcat")

        var capabilityAt: Long? = null
        var passAt: Long? = null
        var groupingAt: Long? = null
        var pass: PassLine? = null
        var notRun: String? = null
        var grouping: String? = null
        for (line in lines.drop(markerIndex + 1)) {
            val parsed = LINE.matchEntire(line.trimEnd()) ?: continue
            if (parsed.groupValues[5] != FACE_TAG) continue
            val at = ((parsed.groupValues[1].toLong() * 60 + parsed.groupValues[2].toLong()) * 60 + parsed.groupValues[3].toLong()) * 1000 +
                parsed.groupValues[4].toLong()
            val message = parsed.groupValues[6]
            PASS_FINISHED.matchEntire(message)?.let {
                pass = PassLine(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt(), it.groupValues[5])
                passAt = at
            }
            GROUPING_FINISHED.matchEntire(message)?.let { grouping = it.groupValues[1]; groupingAt = at }
            NOT_RUN.matchEntire(message)?.let { notRun = it.groupValues[1] }
            if (message.startsWith("face capability checked")) capabilityAt = at
        }
        return RunLog(
            pass = pass,
            notRunReason = notRun,
            groupingStop = grouping,
            capabilityToPassEndMillis = elapsed(capabilityAt, passAt),
            groupingStageMillis = elapsed(passAt, groupingAt)
        )
    }

    // Time of day only, so a run across midnight still counts forward.
    private fun elapsed(from: Long?, to: Long?): Long? =
        if (from == null || to == null) null else (to - from + DAY_MILLIS) % DAY_MILLIS

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }

    // ---- what the test changes, and putting it back ----

    private class Snapshot(
        val settingPresent: Boolean,
        val settingEnabled: Boolean,
        val deviceStateFile: Map<String, Any?>,
        val disclosureShown: Boolean,
        val capability: FaceCapabilityState?,
        val hadFaceData: Boolean
    )

    private fun snapshot(app: EasyGalleryApp): Snapshot {
        val settings = app.getSharedPreferences(SharedPreferencesFaceDataSettingStore.PREFS_FILE, Context.MODE_PRIVATE)
        val deviceState = app.getSharedPreferences(FACE_DEVICE_STATE_FILE, Context.MODE_PRIVATE)
        return Snapshot(
            settingPresent = settings.contains(SharedPreferencesFaceDataSettingStore.KEY_FACE_DATA_ENABLED),
            settingEnabled = app.faceDataSettingStore.isEnabled(),
            deviceStateFile = HashMap<String, Any?>(deviceState.all),
            disclosureShown = app.faceDeviceStateStore.isDisclosureShown(),
            capability = app.faceDeviceStateStore.capabilityState(),
            hadFaceData = runBlocking { app.mediaIndexStore.hasFaceData() }
        )
    }

    private fun restore(app: EasyGalleryApp, workManager: WorkManager, before: Snapshot) {
        val errors = ArrayList<Throwable>()
        fun step(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                errors.add(error)
            }
        }

        // A pass writes only while it holds the Tier C lock and skips as BUSY when it is taken, so holding the lock
        // across the flags and the deletion leaves no gap for a periodic run to write face data after them. The flags
        // go back first so that, if the lock cannot be had, a later run still finds the disclosure as it was.
        var lockHeld = false
        step {
            workManager.cancelUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_NOW_WORK)
            val waitUntil = SystemClock.elapsedRealtime() + LOCK_WAIT_MILLIS
            while (SystemClock.elapsedRealtime() < waitUntil) {
                val infos = workManager.getWorkInfosForUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_NOW_WORK)
                    .get(10, TimeUnit.SECONDS)
                if (infos.all { it.state.isFinished }) break
                SystemClock.sleep(250)
            }
            runBlocking { withTimeout(LOCK_WAIT_MILLIS) { app.faceIndexLock.lock() } }
            lockHeld = true
        }
        try {
            step {
                val editor = app.getSharedPreferences(SharedPreferencesFaceDataSettingStore.PREFS_FILE, Context.MODE_PRIVATE).edit()
                if (before.settingPresent) {
                    editor.putBoolean(SharedPreferencesFaceDataSettingStore.KEY_FACE_DATA_ENABLED, before.settingEnabled)
                } else {
                    editor.remove(SharedPreferencesFaceDataSettingStore.KEY_FACE_DATA_ENABLED)
                }
                check(editor.commit()) { "The face-data setting could not be restored" }
            }
            step {
                val editor = app.getSharedPreferences(FACE_DEVICE_STATE_FILE, Context.MODE_PRIVATE).edit().clear()
                before.deviceStateFile.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        else -> error("A face_device_state value of an unexpected type cannot be restored")
                    }
                }
                check(editor.commit()) { "face_device_state could not be restored" }
            }
            step {
                val deletion = runBlocking { app.mediaIndexStore.deleteAllFaceData() }
                Log.i(TAG, "restore: face data deleted rows=${deletion.rowsDeleted} scrubbed=${deletion.scrubbed}")
            }
        } finally {
            if (lockHeld) app.faceIndexLock.unlock()
        }

        val first = errors.firstOrNull() ?: return
        errors.drop(1).forEach { first.addSuppressed(it) }
        throw first
    }

    // Its own step after the restore, so a restore that did nothing is a failure.
    private fun assertRestored(app: EasyGalleryApp, before: Snapshot) {
        assertFalse("Face data is still on the phone after the test", runBlocking { app.mediaIndexStore.hasFaceData() })
        val settings = app.getSharedPreferences(SharedPreferencesFaceDataSettingStore.PREFS_FILE, Context.MODE_PRIVATE)
        assertEquals(
            "The face-data setting's key must be present exactly as before",
            before.settingPresent,
            settings.contains(SharedPreferencesFaceDataSettingStore.KEY_FACE_DATA_ENABLED)
        )
        assertEquals("The face-data setting must read as before", before.settingEnabled, app.faceDataSettingStore.isEnabled())
        assertEquals("The disclosure flag must read as before", before.disclosureShown, app.faceDeviceStateStore.isDisclosureShown())
        assertEquals("The capability record must be as before", before.capability, app.faceDeviceStateStore.capabilityState())
        assertEquals(
            "face_device_state must hold exactly what it held",
            before.deviceStateFile,
            HashMap<String, Any?>(app.getSharedPreferences(FACE_DEVICE_STATE_FILE, Context.MODE_PRIVATE).all)
        )
        Log.i(
            TAG,
            "restored: hasFaceData=false settingPresent=${before.settingPresent} settingEnabled=${before.settingEnabled} " +
                "disclosureShown=${before.disclosureShown} capabilityRecorded=${before.capability != null}"
        )
    }

    // ---- the device ----

    // Writing 5 to clear_refs resets VmHWM to the current RSS, where the kernel and SELinux allow it to an app.
    private fun resetPeakRss(): Boolean {
        try {
            File("/proc/self/clear_refs").writeText("5")
        } catch (_: Exception) {
            return false
        }
        return statusKb("VmHWM") <= statusKb("VmRSS") + RESET_TOLERANCE_KB
    }

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
        const val TAG = "FaceGroupingWorkerTest"
        const val FACE_TAG = "FaceIndexPass"
        const val OPT_IN_ARGUMENT = "faceGroupingWorkerMeasurement"

        const val MAX_RUNS = 6
        const val MAX_FACES = 1_000_000
        const val RUN_LIMIT_MILLIS = 12L * 60L * 1000L
        const val TOTAL_LIMIT_MILLIS = 22L * 60L * 1000L
        const val LOCK_WAIT_MILLIS = 90_000L
        const val POLL_MILLIS = 500L
        const val THERMAL_CHECK_MILLIS = 2_000L
        const val THERMAL_STOP = 3
        const val RESET_TOLERANCE_KB = 8_192L
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        // threadtime: "11-21 10:12:13.123  1234  5678 I Tag: message".
        val LINE = Regex("""^\S+ (\d\d):(\d\d):(\d\d)\.(\d{3})\s+\d+\s+\d+\s+[A-Z]\s+(\S+)\s*:\s?(.*)$""")
        val PASS_FINISHED = Regex("""face pass finished images=(\d+) indexed=(\d+) failed=(\d+) skipped=(\d+) stop=(\w+)""")
        val GROUPING_FINISHED = Regex("""face grouping finished stop=(\w+)""")
        val NOT_RUN = Regex("""face pass not run reason=(\w+)""")

        // One "Name:   123 kB" line of /proc/self/status, or -1.
        fun statusKb(name: String): Long =
            File("/proc/self/status").readLines().firstOrNull { it.startsWith("$name:") }
                ?.filter { it.isDigit() }?.toLongOrNull() ?: -1L

        fun thermalStatus(app: Context): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                app.getSystemService(PowerManager::class.java).currentThermalStatus
            } else {
                -1
            }

        fun isCharging(app: Context): Boolean = app.getSystemService(BatteryManager::class.java).isCharging
    }
}
