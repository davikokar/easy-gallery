package com.davide.seddio.easygallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.data.AndroidMediaLocationAccessChecker
import com.davide.seddio.easygallery.data.LocationState
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaIndexRunOutcome
import com.davide.seddio.easygallery.data.MediaIndexRunStateStore
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.MediaIndexingLivenessStore
import com.davide.seddio.easygallery.data.MediaIndexingWorkStatus
import com.davide.seddio.easygallery.data.MediaIndexingWorkScheduler
import com.davide.seddio.easygallery.data.MediaLocationAccessChecker
import com.davide.seddio.easygallery.data.WorkManagerMediaIndexingLivenessStore
import com.davide.seddio.easygallery.data.canonicalMediaStoreUriForJoin
import com.davide.seddio.easygallery.data.locationStateOf
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
    val lastRunErrorMessage: String?,
    /** Live, indexed items with no coordinates or resolved beyond the cap; pending and unindexed items wait. */
    val indexedWithoutLocationCount: Int = 0,
    /** False when ACCESS_MEDIA_LOCATION is missing, which makes [indexedWithoutLocationCount] meaningless. */
    val locationAccessGranted: Boolean = true
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
        },
    private val locationAccessChecker: MediaLocationAccessChecker =
        AndroidMediaLocationAccessChecker(application)
) : AndroidViewModel(application) {
    private val workStatusFlow: Flow<MediaIndexingWorkStatus> = livenessStore.status

    private val liveCanonicalUris = MutableStateFlow<Set<String>>(emptySet())

    private val locationAccessFlow = MutableStateFlow(locationAccessChecker.isGranted())

    /** What one index row says, keyed by canonical URI; no healing, as the counts have always been. */
    private class IndexedFacts(val dateTakenEpochMillis: Long?, val location: LocationState)

    private val indexedFacts: Flow<Map<String, IndexedFacts>> = mediaIndexStore
        .observeEvaluationProjection()
        .map { rows ->
            buildMap(rows.size) {
                for (row in rows) {
                    put(
                        canonicalMediaStoreUriForJoin(row.identity.mediaUri),
                        IndexedFacts(row.capturedDateTakenEpochMillis, locationStateOf(row))
                    )
                }
            }
        }

    val uiState: StateFlow<MediaIndexingUiState> = combine(
        runStateStore.state,
        workStatusFlow,
        liveCanonicalUris,
        indexedFacts,
        locationAccessFlow
    ) { runState, workStatus, liveUris, facts, locationGranted ->
        var indexedItemsCount = 0
        var indexedWithDateCount = 0
        var indexedWithoutLocationCount = 0
        for (uri in liveUris) {
            val indexed = facts[uri] ?: continue
            indexedItemsCount++
            if (indexed.dateTakenEpochMillis != null) indexedWithDateCount++
            when (indexed.location) {
                LocationState.NoCoordinates, LocationState.ResolvedOutOfCap -> indexedWithoutLocationCount++
                else -> Unit
            }
        }
        val liveNotYetIndexed = (liveUris.size - indexedItemsCount).coerceAtLeast(0)
        val indexedWithoutDateCount = indexedItemsCount - indexedWithDateCount
        MediaIndexingUiState(
            workStatus = workStatus,
            isRunning = workStatus == MediaIndexingWorkStatus.RUNNING,
            selectedRows = runState.selectedRows,
            processedRows = runState.processedRows,
            indexedRows = runState.indexedRows,
            skippedRows = runState.skippedRows,
            deletedRows = runState.deletedRows,
            healedRows = runState.healedRows,
            indexedItemsCount = indexedItemsCount,
            indexedWithDateCount = indexedWithDateCount,
            indexedWithoutDateCount = indexedWithoutDateCount,
            notYetIndexedCount = liveNotYetIndexed,
            lastRunStartedAtMillis = runState.lastRunStartedAtMillis,
            lastRunFinishedAtMillis = runState.lastRunFinishedAtMillis,
            lastSuccessfulRunFinishedAtMillis = runState.lastSuccessfulRunFinishedAtMillis,
            lastRunOutcome = runState.lastRunOutcome,
            lastRunErrorMessage = runState.lastRunErrorMessage,
            indexedWithoutLocationCount = indexedWithoutLocationCount,
            locationAccessGranted = locationGranted
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
            lastRunErrorMessage = null,
            indexedWithoutLocationCount = 0,
            locationAccessGranted = locationAccessFlow.value
        )
    )

    /** The user can change the permission outside the app, so the screen asks again when it resumes. */
    fun refreshLocationAccess() {
        locationAccessFlow.value = locationAccessChecker.isGranted()
    }

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
