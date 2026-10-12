package com.davide.seddio.easygallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaIndexRunOutcome
import com.davide.seddio.easygallery.data.MediaIndexRunStateStore
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.MediaIndexingLivenessStore
import com.davide.seddio.easygallery.data.MediaIndexingWorkStatus
import com.davide.seddio.easygallery.data.MediaIndexingWorkScheduler
import com.davide.seddio.easygallery.data.WorkManagerMediaIndexingLivenessStore
import com.davide.seddio.easygallery.data.canonicalMediaStoreUriForJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MediaIndexingUiState(
    val workStatus: MediaIndexingWorkStatus,
    val isRunning: Boolean,
    val selectedRows: Int,
    val processedRows: Int,
    val indexedRows: Int,
    val skippedRows: Int,
    val deletedRows: Int,
    val healedRows: Int,
    val indexedItemsCount: Int,
    val indexedWithDateCount: Int,
    val indexedWithoutDateCount: Int,
    val notYetIndexedCount: Int,
    val lastRunStartedAtMillis: Long?,
    val lastRunFinishedAtMillis: Long?,
    val lastSuccessfulRunFinishedAtMillis: Long?,
    val lastRunOutcome: MediaIndexRunOutcome?,
    val lastRunErrorMessage: String?
)

class MediaIndexingViewModel @JvmOverloads constructor(
    application: Application,
    private val scheduler: MediaIndexingWorkScheduler =
        (application as EasyGalleryApp).mediaIndexingWorkScheduler,
    private val runStateStore: MediaIndexRunStateStore =
        (application as EasyGalleryApp).mediaIndexRunStateStore,
    mediaIndexStore: MediaIndexStore =
        (application as EasyGalleryApp).mediaIndexStore,
    livenessStore: MediaIndexingLivenessStore =
        (application as EasyGalleryApp).let { app ->
            WorkManagerMediaIndexingLivenessStore(app)
        }
) : AndroidViewModel(application) {
    private val workStatusFlow: Flow<MediaIndexingWorkStatus> = livenessStore.status

    private val liveCanonicalUris = MutableStateFlow<Set<String>>(emptySet())

    private val indexedCanonicalUris: Flow<Set<String>> = mediaIndexStore
        .observeDateTakenProjection()
        .map { projection -> projection.keys }

    private val indexedProjection: Flow<Map<String, Long?>> = mediaIndexStore.observeDateTakenProjection()

    val uiState: StateFlow<MediaIndexingUiState> = combine(
        runStateStore.state,
        workStatusFlow,
        liveCanonicalUris,
        indexedCanonicalUris,
        indexedProjection
    ) { runState, workStatus, liveUris, indexedUris, projection ->
        val liveIndexedUris = indexedUris.intersect(liveUris)
        val liveNotYetIndexed = (liveUris.size - liveIndexedUris.size).coerceAtLeast(0)
        val indexedWithDateCount = liveIndexedUris.count { uri -> projection[uri] != null }
        val indexedWithoutDateCount = liveIndexedUris.size - indexedWithDateCount
        MediaIndexingUiState(
            workStatus = workStatus,
            isRunning = workStatus == MediaIndexingWorkStatus.RUNNING,
            selectedRows = runState.selectedRows,
            processedRows = runState.processedRows,
            indexedRows = runState.indexedRows,
            skippedRows = runState.skippedRows,
            deletedRows = runState.deletedRows,
            healedRows = runState.healedRows,
            indexedItemsCount = liveIndexedUris.size,
            indexedWithDateCount = indexedWithDateCount,
            indexedWithoutDateCount = indexedWithoutDateCount,
            notYetIndexedCount = liveNotYetIndexed,
            lastRunStartedAtMillis = runState.lastRunStartedAtMillis,
            lastRunFinishedAtMillis = runState.lastRunFinishedAtMillis,
            lastSuccessfulRunFinishedAtMillis = runState.lastSuccessfulRunFinishedAtMillis,
            lastRunOutcome = runState.lastRunOutcome,
            lastRunErrorMessage = runState.lastRunErrorMessage
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MediaIndexingUiState(
            workStatus = MediaIndexingWorkStatus.IDLE,
            isRunning = false,
            selectedRows = 0,
            processedRows = 0,
            indexedRows = 0,
            skippedRows = 0,
            deletedRows = 0,
            healedRows = 0,
            indexedItemsCount = 0,
            indexedWithDateCount = 0,
            indexedWithoutDateCount = 0,
            notYetIndexedCount = 0,
            lastRunStartedAtMillis = null,
            lastRunFinishedAtMillis = null,
            lastSuccessfulRunFinishedAtMillis = null,
            lastRunOutcome = null,
            lastRunErrorMessage = null
        )
    )

    fun ensurePeriodicIndexing() {
        scheduler.ensurePeriodicIndexing()
    }

    fun indexNow() {
        scheduler.enqueueIndexNow()
    }

    fun setLiveMedia(media: List<MediaItem>) {
        liveCanonicalUris.value = media
            .mapTo(LinkedHashSet(media.size)) { item ->
                canonicalMediaStoreUriForJoin(item.uri.toString())
            }
    }
}
