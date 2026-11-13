package com.davide.seddio.easygallery.ui

import android.app.Application
import android.net.Uri
import com.davide.seddio.easygallery.data.InMemoryMediaIndexingLivenessStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexRunStateStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaIndexEntry
import com.davide.seddio.easygallery.data.MediaIndexingWorkStatus
import com.davide.seddio.easygallery.data.MediaIndexingWorkScheduler
import com.davide.seddio.easygallery.data.MediaLocationAccessChecker
import com.davide.seddio.easygallery.data.PLACE_RESOLVER_VERSION
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaIndexingViewModelTest {

    @Test
    fun `indexed rows without dates are counted separately from rows with dates`() = runTest {
        val indexStore = InMemoryMediaIndexStore()
        val viewModel = MediaIndexingViewModel(
            application = mockk<Application>(relaxed = true),
            scheduler = mockk<MediaIndexingWorkScheduler>(relaxed = true),
            runStateStore = InMemoryMediaIndexRunStateStore(),
            mediaIndexStore = indexStore,
            livenessStore = InMemoryMediaIndexingLivenessStore(flowOf(MediaIndexingWorkStatus.IDLE))
        )

        viewModel.setLiveMedia(
            listOf(
                mediaItem("content://media/external/images/1"),
                mediaItem("content://media/external/images/2"),
                mediaItem("content://media/external/images/3")
            )
        )

        viewModel.uiState.test {
            awaitState { it.notYetIndexedCount == 3 }

            indexStore.upsert(
                listOf(
                    entry("content://media/external/images/1", 1_000L),
                    entry("content://media/external/images/2", null)
                )
            )
            val state = awaitState { it.indexedWithDateCount == 1 }

            assertEquals(2, state.indexedItemsCount)
            assertEquals(1, state.indexedWithDateCount)
            assertEquals(1, state.indexedWithoutDateCount)
            assertEquals(1, state.notYetIndexedCount)
        }
    }

    @Test
    fun `indexed counts ignore rows not present in live media`() = runTest {
        val indexStore = InMemoryMediaIndexStore()
        val viewModel = MediaIndexingViewModel(
            application = mockk<Application>(relaxed = true),
            scheduler = mockk<MediaIndexingWorkScheduler>(relaxed = true),
            runStateStore = InMemoryMediaIndexRunStateStore(),
            mediaIndexStore = indexStore,
            livenessStore = InMemoryMediaIndexingLivenessStore(flowOf(MediaIndexingWorkStatus.IDLE))
        )

        viewModel.setLiveMedia(listOf(mediaItem("content://media/external/images/1")))

        viewModel.uiState.test {
            awaitState { it.notYetIndexedCount == 1 }

            indexStore.upsert(
                listOf(
                    entry("content://media/external/images/1", 1_000L),
                    entry("content://media/external/images/2", null)
                )
            )
            val state = awaitState { it.indexedWithDateCount == 1 }

            assertEquals(1, state.indexedItemsCount)
            assertEquals(1, state.indexedWithDateCount)
            assertEquals(0, state.indexedWithoutDateCount)
            assertEquals(0, state.notYetIndexedCount)
        }
    }

    @Test
    fun `ui state maps work status to running flag`() = runTest {
        val statusCases = listOf(
            MediaIndexingWorkStatus.IDLE to false,
            MediaIndexingWorkStatus.WAITING to false,
            MediaIndexingWorkStatus.RUNNING to true
        )

        statusCases.forEach { (status, expectedRunning) ->
            val viewModel = MediaIndexingViewModel(
                application = mockk<Application>(relaxed = true),
                scheduler = mockk<MediaIndexingWorkScheduler>(relaxed = true),
                runStateStore = InMemoryMediaIndexRunStateStore(),
                mediaIndexStore = InMemoryMediaIndexStore(),
                livenessStore = InMemoryMediaIndexingLivenessStore(flowOf(status))
            )

            val state = if (status == MediaIndexingWorkStatus.IDLE) {
                viewModel.uiState.value
            } else {
                viewModel.uiState.drop(1).first()
            }
            assertEquals(status, state.workStatus)
            assertEquals(expectedRunning, state.isRunning)
        }
    }

    @Test
    fun `rows without coordinates and rows resolved beyond the cap are counted as without location`() = runTest {
        val indexStore = InMemoryMediaIndexStore()
        val viewModel = createViewModel(indexStore)
        viewModel.setLiveMedia((1..4).map { mediaItem("content://media/external/images/$it") })

        viewModel.uiState.test {
            awaitState { it.notYetIndexedCount == 4 }

            indexStore.upsert(
                listOf(
                    entry("content://media/external/images/1", 1_000L),
                    entry(
                        "content://media/external/images/2", 1_000L,
                        latitude = 48.0, longitude = 2.0, marker = PLACE_RESOLVER_VERSION
                    ),
                    entry(
                        "content://media/external/images/3", 1_000L,
                        latitude = 70.0, longitude = 170.0, marker = PLACE_RESOLVER_VERSION
                    ),
                    entry(
                        "content://media/external/images/4", 1_000L,
                        latitude = 48.0, longitude = 2.0, country = "FR", marker = PLACE_RESOLVER_VERSION
                    )
                )
            )
            val state = awaitState { it.indexedItemsCount == 4 }

            // 1 has no coordinates and 2 and 3 were resolved to no place; 4 resolved to a place.
            assertEquals(3, state.indexedWithoutLocationCount)
        }
    }

    @Test
    fun `pending not yet indexed and not live rows are not counted as without location`() = runTest {
        val indexStore = InMemoryMediaIndexStore()
        val viewModel = createViewModel(indexStore)
        viewModel.setLiveMedia((1..3).map { mediaItem("content://media/external/images/$it") })

        viewModel.uiState.test {
            awaitState { it.notYetIndexedCount == 3 }

            indexStore.upsert(
                listOf(
                    // Pending: coordinates, no marker and no values.
                    entry("content://media/external/images/1", 1_000L, latitude = 48.0, longitude = 2.0, marker = null),
                    // Pending under an earlier resolver, keeping its old values.
                    entry(
                        "content://media/external/images/2", 1_000L,
                        latitude = 48.0, longitude = 2.0, country = "FR", marker = PLACE_RESOLVER_VERSION - 1
                    ),
                    // Not live: the item is gone from the device.
                    entry("content://media/external/images/9", 1_000L)
                    // Item 3 is live and has no row.
                )
            )
            val state = awaitState { it.indexedItemsCount == 2 }

            assertEquals(1, state.notYetIndexedCount)
            assertEquals(0, state.indexedWithoutLocationCount)
        }
    }

    @Test
    fun `location access flows into the state and is read again on refresh`() = runTest {
        var granted = false
        val viewModel = createViewModel(InMemoryMediaIndexStore(), MediaLocationAccessChecker { granted })

        viewModel.uiState.test {
            assertFalse(awaitState { !it.locationAccessGranted }.locationAccessGranted)

            granted = true
            viewModel.refreshLocationAccess()

            assertTrue(awaitState { it.locationAccessGranted }.locationAccessGranted)
        }
    }

    // uiState starts with a placeholder and the combine can emit intermediate states; skip to the one the test means.
    private suspend fun ReceiveTurbine<MediaIndexingUiState>.awaitState(
        predicate: (MediaIndexingUiState) -> Boolean
    ): MediaIndexingUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private fun createViewModel(
        indexStore: InMemoryMediaIndexStore,
        locationAccess: MediaLocationAccessChecker = MediaLocationAccessChecker { true }
    ) = MediaIndexingViewModel(
        application = mockk<Application>(relaxed = true),
        scheduler = mockk<MediaIndexingWorkScheduler>(relaxed = true),
        runStateStore = InMemoryMediaIndexRunStateStore(),
        mediaIndexStore = indexStore,
        livenessStore = InMemoryMediaIndexingLivenessStore(flowOf(MediaIndexingWorkStatus.IDLE)),
        locationAccessChecker = locationAccess
    )

    private fun entry(
        mediaUri: String,
        capturedDateTakenEpochMillis: Long?,
        latitude: Double? = null,
        longitude: Double? = null,
        country: String? = null,
        marker: Int? = null
    ): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = mediaUri.substringAfterLast('/'),
            size = 1L,
            dateModified = 1L,
            volumeName = "external_primary",
            generationAdded = 1L,
            generationModified = 1L,
            capturedDateTakenEpochMillis = capturedDateTakenEpochMillis,
            capturedLatitude = latitude,
            capturedLongitude = longitude,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = country,
            derivedRegionCode = null,
            derivedPlaceName = null,
            derivedPlaceResolverVersion = marker
        )
    }

    private fun mediaItem(uriValue: String): MediaItem {
        val uri = mockk<Uri>()
        every { uri.toString() } returns uriValue

        return MediaItem(
            uri = uri,
            name = uriValue.substringAfterLast('/'),
            dateAdded = 1L,
            dateModified = 1L,
            size = 1L,
            type = com.davide.seddio.easygallery.data.MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera",
            duration = null
        )
    }
}