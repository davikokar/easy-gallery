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
import com.davide.seddio.easygallery.EasyGalleryApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

data class MediaIndexTierAProgress(
    val selectedRows: Int,
    val processedRows: Int,
    val indexedRows: Int,
    val skippedRows: Int
)

class MediaIndexingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as EasyGalleryApp
        val runStateStore = app.mediaIndexRunStateStore
        val startedAt = System.currentTimeMillis()

        runStateStore.markStarted(startedAt)
        setProgress(progressData(MediaIndexTierAProgress(0, 0, 0, 0)))

        return try {
            ensureMediaReadPermission(applicationContext)

            val result = app.createMediaIndexTierABatchIndexer().indexDelta(
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
        } catch (error: Exception) {
            runStateStore.markFailed(System.currentTimeMillis())
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (error.message ?: "unknown")))
        }
    }

    private fun progressData(progress: MediaIndexTierAProgress) = workDataOf(
        KEY_SELECTED_ROWS to progress.selectedRows,
        KEY_PROCESSED_ROWS to progress.processedRows,
        KEY_INDEXED_ROWS to progress.indexedRows,
        KEY_SKIPPED_ROWS to progress.skippedRows
    )

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
            UNIQUE_INDEX_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildPeriodicRequest()
        )
    }

    override fun enqueueIndexNow() {
        workManager.enqueueUniqueWork(
            UNIQUE_INDEX_WORK,
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
        const val UNIQUE_INDEX_WORK = "media-index"
        private const val PERIODIC_REPEAT_HOURS = 24L
    }
}
