package com.davide.seddio.easygallery.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import android.util.Log
import com.davide.seddio.easygallery.EasyGalleryApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

data class MediaIndexTierAProgress(
    val selectedRows: Int,
    val processedRows: Int,
    val indexedRows: Int,
    val skippedRows: Int
)

/** Raised for any face-path failure, so that nothing the path threw, its message included, reaches the output data. */
private class FaceIndexPassFailure : Exception(FACE_INDEX_FAILURE_MESSAGE)

object AndroidFaceIndexLog : FaceIndexLog {
    private const val TAG = "FaceIndexPass"

    override fun line(text: String) {
        Log.i(TAG, text)
    }
}

// The seams default to the app's own wiring; WorkManager's reflective factory only sees the two-argument constructor.
class MediaIndexingWorker @JvmOverloads constructor(
    appContext: Context,
    params: WorkerParameters,
    private val runStateStore: MediaIndexRunStateStore = (appContext as EasyGalleryApp).mediaIndexRunStateStore,
    private val createBatchIndexer: () -> MediaIndexTierABatchIndexer =
        { (appContext as EasyGalleryApp).createMediaIndexTierABatchIndexer() },
    private val createPlaceResolver: () -> MediaIndexPlaceResolver =
        { (appContext as EasyGalleryApp).createMediaIndexPlaceResolver() },
    private val faceDataSettingStore: FaceDataSettingStore = (appContext as EasyGalleryApp).faceDataSettingStore,
    private val faceDeviceStateStore: FaceDeviceStateStore = (appContext as EasyGalleryApp).faceDeviceStateStore,
    private val createFaceDataStartupDeletion: () -> FaceDataStartupDeletion =
        { (appContext as EasyGalleryApp).createFaceDataStartupDeletion() },
    private val createFaceCapabilityChecker: () -> FaceCapabilityChecker =
        { (appContext as EasyGalleryApp).createFaceCapabilityChecker() },
    private val createFaceIndexPass: () -> FaceIndexPass =
        { (appContext as EasyGalleryApp).createFaceIndexPass() },
    private val faceIndexLock: Mutex = (appContext as EasyGalleryApp).faceIndexLock,
    private val faceIndexLog: FaceIndexLog = (appContext as EasyGalleryApp).faceIndexLog
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val startedAt = System.currentTimeMillis()

        runStateStore.markStarted(startedAt)
        setProgress(progressData(MediaIndexTierAProgress(0, 0, 0, 0)))

        return try {
            // First, and before the permission check: a deletion reads no media and must not wait on one (ADR-0024 §3).
            createFaceDataStartupDeletion().run()

            ensureMediaReadPermission(applicationContext)

            val result = createBatchIndexer().indexDelta(
                sdkInt = Build.VERSION.SDK_INT,
                onProgress = { progress ->
                    runStateStore.updateProgress(
                        selectedRows = progress.selectedRows,
                        processedRows = progress.processedRows,
                        indexedRows = progress.indexedRows,
                        skippedRows = progress.skippedRows
                    )
                    setProgressAsync(progressData(progress))
                }
            )

            // Every run, even when Tier A selected nothing, so rows indexed before the resolver existed get resolved.
            resolvePendingPlaces()

            // After Tier A and places, so a face failure cannot stop them; the run state still reports Tier A alone.
            runFaceIndexPass(startedAt)

            runStateStore.markSucceeded(System.currentTimeMillis(), result)
            Result.success(
                workDataOf(
                    KEY_SELECTED_ROWS to result.selectedRows,
                    KEY_INDEXED_ROWS to result.indexedRows,
                    KEY_SKIPPED_ROWS to result.skippedRows,
                    KEY_DELETED_ROWS to result.deletedRows,
                    KEY_HEALED_ROWS to result.healedRows
                )
            )
        } catch (cancelled: CancellationException) {
            runStateStore.markCancelled(System.currentTimeMillis())
            throw cancelled
        } catch (_: FaceIndexPassFailure) {
            runStateStore.markFailed(System.currentTimeMillis())
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to FACE_INDEX_FAILURE_MESSAGE))
        } catch (error: Exception) {
            runStateStore.markFailed(System.currentTimeMillis())
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (error.message ?: "unknown")))
        }
    }

    // A failure propagates to doWork's catch, which marks the run failed; Tier A's watermarks are already persisted and untouched.
    private suspend fun resolvePendingPlaces() {
        try {
            val placeResult = createPlaceResolver().resolvePending()
            Log.i(PLACE_RESOLUTION_LOG_TAG, placeResult.toLogLine())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(PLACE_RESOLUTION_LOG_TAG, "Place resolution failed: ${error.message}", error)
            throw error
        }
    }

    private fun progressData(progress: MediaIndexTierAProgress) = workDataOf(
        KEY_SELECTED_ROWS to progress.selectedRows,
        KEY_PROCESSED_ROWS to progress.processedRows,
        KEY_INDEXED_ROWS to progress.indexedRows,
        KEY_SKIPPED_ROWS to progress.skippedRows
    )

    // Anything the face path throws, an Error included, becomes FaceIndexPassFailure so no message or throwable escapes.
    private suspend fun runFaceIndexPass(workStartedAtMillis: Long) {
        if (!faceDataSettingStore.isEnabled()) {
            faceIndexLog.line(FaceIndexLogLines.passNotRun(FaceIndexNotRunReason.SETTING_OFF))
            return
        }
        // The lock comes before the capability check: the periodic job and Index now can overlap, and two checks at once
        // would load the models twice and count one run's start as the other's unfinished one.
        if (!faceIndexLock.tryLock()) {
            faceIndexLog.line(FaceIndexLogLines.passNotRun(FaceIndexNotRunReason.BUSY))
            return
        }
        try {
            val capable = createFaceCapabilityChecker().isCapable()
            faceIndexLog.line(FaceIndexLogLines.capabilityChecked(capable))
            if (!capable) {
                faceIndexLog.line(FaceIndexLogLines.passNotRun(FaceIndexNotRunReason.INCAPABLE))
                return
            }
            if (!faceDeviceStateStore.isDisclosureShown()) {
                faceIndexLog.line(FaceIndexLogLines.passNotRun(FaceIndexNotRunReason.DISCLOSURE_NOT_SHOWN))
                return
            }
            createFaceIndexPass().run(workStartedAtMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            faceIndexLog.line(FaceIndexLogLines.PASS_FAILED)
            throw FaceIndexPassFailure()
        } finally {
            faceIndexLock.unlock()
        }
    }

    private fun ensureMediaReadPermission(context: Context) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = requiredPermissions.all { permission ->
            hasMediaReadAccess(context, permission)
        }

        if (!allGranted) {
            throw IllegalStateException("Required media read permission is missing")
        }
    }

    private fun hasMediaReadAccess(context: Context, permission: String): Boolean {
        val runtimeGranted =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!runtimeGranted) return false

        val op = when (permission) {
            Manifest.permission.READ_MEDIA_IMAGES -> "android:read_media_images"
            Manifest.permission.READ_MEDIA_VIDEO -> "android:read_media_video"
            Manifest.permission.READ_EXTERNAL_STORAGE -> AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE
            else -> return runtimeGranted
        }

        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return runtimeGranted
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(op, Process.myUid(), context.packageName)
        }

        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
    }

    companion object {
        const val KEY_SELECTED_ROWS = "selected_rows"
        const val KEY_PROCESSED_ROWS = "processed_rows"
        const val KEY_INDEXED_ROWS = "indexed_rows"
        const val KEY_SKIPPED_ROWS = "skipped_rows"
        const val KEY_DELETED_ROWS = "deleted_rows"
        const val KEY_HEALED_ROWS = "healed_rows"
        const val KEY_ERROR_MESSAGE = "error_message"
    }
}

interface MediaIndexingWorkScheduler {
    fun ensurePeriodicIndexing()

    fun enqueueIndexNow()
}

class WorkManagerMediaIndexingWorkScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }
) : MediaIndexingWorkScheduler {

    override fun ensurePeriodicIndexing() {
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_INDEX_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildPeriodicRequest()
        )
    }

    override fun enqueueIndexNow() {
        workManager.enqueueUniqueWork(
            UNIQUE_INDEX_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            buildIndexNowRequest()
        )
    }

    internal fun buildPeriodicRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<MediaIndexingWorker>(
            PERIODIC_REPEAT_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(periodicConstraints())
            .build()
    }

    internal fun buildIndexNowRequest(): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<MediaIndexingWorker>()

        if (sdkIntProvider() >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }

        return builder.build()
    }

    private fun periodicConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    companion object {
        const val UNIQUE_PERIODIC_INDEX_WORK = "media-index-periodic"
        const val UNIQUE_INDEX_NOW_WORK = "media-index-now"
        private const val PERIODIC_REPEAT_HOURS = 24L
    }
}
