package com.davide.seddio.easygallery.data

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface MediaIndexingLivenessStore {
    val isRunning: Flow<Boolean>
}

class WorkManagerMediaIndexingLivenessStore(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext)
) : MediaIndexingLivenessStore {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val isRunning: Flow<Boolean> = callbackFlow {
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData(
            WorkManagerMediaIndexingWorkScheduler.UNIQUE_INDEX_WORK
        )
        val observer = Observer<List<WorkInfo>> { infos ->
            val running = infos.orEmpty().any { info ->
                info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
            }
            trySend(running)
        }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }.distinctUntilChanged()
}

class InMemoryMediaIndexingLivenessStore(
    override val isRunning: Flow<Boolean>
) : MediaIndexingLivenessStore
