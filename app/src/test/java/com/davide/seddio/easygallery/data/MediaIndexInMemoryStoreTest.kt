package com.davide.seddio.easygallery.data

import android.net.Uri
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexInMemoryStoreTest {

    @Test
    fun `in-memory store supports upsert observe and delete flows`() = runTest {
        val store = InMemoryMediaIndexStore()

        store.observeAll().test {
            assertTrue(awaitItem().isEmpty())

            store.upsert(
                listOf(
                    sampleEntry(mediaUri = "content://media/external/images/media/1"),
                    sampleEntry(
                        mediaUri = "content://media/external/images/media/2",
                        volumeName = "external"
                    )
                )
            )

            val inserted = awaitItem()
            assertEquals(2, inserted.size)

            store.deleteByMediaUri("content://media/external/images/media/1")
            val afterDeleteOne = awaitItem()
            assertEquals(1, afterDeleteOne.size)

            store.deleteByVolume("external")
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entity identity equals live media identity for matching fields`() {
        val mediaUri = "content://media/external/images/media/71"
        val displayName = "identity.jpg"
        val size = 7777L
        val dateModified = 8888L

        val entity = MediaIndexEntity(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            volumeName = "external_primary",
            generationAdded = 1L,
            generationModified = 2L,
            capturedDateTakenEpochMillis = null,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )

        val liveItem = sampleMediaItem(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified
        )

        assertEquals(liveItem.toMediaIdentity(), entity.toMediaIdentity())
    }

    @Test
    fun `store entry identity equals live media identity for matching fields`() {
        val mediaUri = "content://media/external/images/media/72"
        val displayName = "identity-entry.jpg"
        val size = 9999L
        val dateModified = 10_101L

        val entry = MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            volumeName = "external_primary",
            generationAdded = 3L,
            generationModified = 4L,
            capturedDateTakenEpochMillis = null,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )

        val liveItem = sampleMediaItem(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified
        )

        assertEquals(liveItem.toMediaIdentity(), entry.toMediaIdentity())
    }

    private fun sampleEntry(
        mediaUri: String,
        displayName: String = "photo.jpg",
        volumeName: String = "external_primary",
        derivedCountryCode: String? = null,
        derivedRegionCode: String? = null,
        derivedPlaceName: String? = null
    ): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = displayName,
            size = 500L,
            dateModified = 1_000L,
            volumeName = volumeName,
            generationAdded = 5L,
            generationModified = 6L,
            capturedDateTakenEpochMillis = 1_726_000_000_000L,
            capturedLatitude = 43.7696,
            capturedLongitude = 11.2558,
            capturedWidth = 1080,
            capturedHeight = 1920,
            derivedCountryCode = derivedCountryCode,
            derivedRegionCode = derivedRegionCode,
            derivedPlaceName = derivedPlaceName
        )
    }

    private fun sampleMediaItem(
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): MediaItem {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns mediaUri

        return MediaItem(
            uri = uri,
            name = displayName,
            dateAdded = 123L,
            dateModified = dateModified,
            size = size,
            type = MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera",
            duration = null
        )
    }
}
