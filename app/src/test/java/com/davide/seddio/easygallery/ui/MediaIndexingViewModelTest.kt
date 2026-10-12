package com.davide.seddio.easygallery.ui

import android.app.Application
import android.net.Uri
import com.davide.seddio.easygallery.data.InMemoryMediaIndexingLivenessStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexRunStateStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaIndexEntry
import com.davide.seddio.easygallery.data.MediaIndexingWorkScheduler
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            livenessStore = InMemoryMediaIndexingLivenessStore(flowOf(false))
        )

        viewModel.setLiveMedia(
            listOf(
                mediaItem("content://media/external/images/1"),
                mediaItem("content://media/external/images/2"),
                mediaItem("content://media/external/images/3")
            )
        )

        viewModel.uiState.test {
            awaitItem()

            indexStore.upsert(
                listOf(
                    entry("content://media/external/images/1", 1_000L),
                    entry("content://media/external/images/2", null)
                )
            )
            val state = awaitItem()

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
            livenessStore = InMemoryMediaIndexingLivenessStore(flowOf(false))
        )

        viewModel.setLiveMedia(listOf(mediaItem("content://media/external/images/1")))

        viewModel.uiState.test {
            awaitItem()

            indexStore.upsert(
                listOf(
                    entry("content://media/external/images/1", 1_000L),
                    entry("content://media/external/images/2", null)
                )
            )
            val state = awaitItem()

            assertEquals(1, state.indexedItemsCount)
            assertEquals(1, state.indexedWithDateCount)
            assertEquals(0, state.indexedWithoutDateCount)
            assertEquals(0, state.notYetIndexedCount)
        }
    }

    private fun entry(mediaUri: String, capturedDateTakenEpochMillis: Long?): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = mediaUri.substringAfterLast('/'),
            size = 1L,
            dateModified = 1L,
            volumeName = "external_primary",
            generationAdded = 1L,
            generationModified = 1L,
            capturedDateTakenEpochMillis = capturedDateTakenEpochMillis,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
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