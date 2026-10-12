package com.davide.seddio.easygallery.data

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class MediaIndexingWorkStatus {
    IDLE,
    WAITING,
    RUNNING
}

interface MediaIndexingLivenessStore {
    val status: Flow<MediaIndexingWorkStatus>
}

class WorkManagerMediaIndexingLivenessStore(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext)
) : MediaIndexingLivenessStore {

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUniqueWork(uniqueWorkName: String): Flow<List<WorkInfo>> = callbackFlow {
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData(
            uniqueWorkName
        )
        val observer = Observer<List<WorkInfo>> { infos ->
            trySend(infos.orEmpty())
        }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }

    private val periodicInfos: Flow<List<WorkInfo>> =
        observeUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_PERIODIC_INDEX_WORK)

    private val indexNowInfos: Flow<List<WorkInfo>> =
        observeUniqueWork(WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_NOW_WORK)

    override val status: Flow<MediaIndexingWorkStatus> = combine(
        periodicInfos,
        indexNowInfos
    ) { periodic, indexNow ->
        val allInfos = periodic + indexNow
        when {
            allInfos.any { info -> info.state == WorkInfo.State.RUNNING } -> {
                MediaIndexingWorkStatus.RUNNING
            }

            allInfos.any { info -> info.state == WorkInfo.State.ENQUEUED } -> {
                MediaIndexingWorkStatus.WAITING
            }

            else -> {
                MediaIndexingWorkStatus.IDLE
            }
        }
    }.distinctUntilChanged()
}

class InMemoryMediaIndexingLivenessStore(
    override val status: Flow<MediaIndexingWorkStatus>
) : MediaIndexingLivenessStore
