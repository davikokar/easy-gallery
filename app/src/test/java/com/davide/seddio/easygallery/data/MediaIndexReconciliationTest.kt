package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexReconciliationTest {

    @Test
    fun `api 28 changed primary does not resync an unobservable second volume and its rows survive`() = runTest {
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_28,
            observedVolumes = listOf(
                ObservedMediaStoreVolume("external_primary", "v9", null)
            ),
            presentVolumes = setOf("external_primary"),
            knownButUnavailableVolumes = emptySet()
        )
        val storedWatermarks = mapOf(
            "external_primary" to MediaIndexVolumeWatermark("v8", null, 100L),
            "0123-4567" to MediaIndexVolumeWatermark("v4", null, 50L)
        )

        val plan = planMediaIndexDelta(snapshot, storedWatermarks)
        assertEquals(setOf("external_primary"), plan.volumesToResync)

        val store = InMemoryMediaIndexStore()
        store.upsert(MediaIndexDeltaTestFixtures.twoVolumeIndexRows())

        val reconciler = MediaIndexReconciler(store)
        reconciler.reconcileVolume(
            volumeName = "external_primary",
            liveMedia = emptyList(),
            presentVolumes = plan.presentVolumes
        )

        val remaining =
            store.listByVolume("external_primary").map { it.mediaUri } +
                store.listByVolume("0123-4567").map { it.mediaUri }
        assertEquals(listOf("content://media/0123-4567/images/media/2"), remaining)
    }

    @Test
    fun `api 29 changed primary resyncs primary rows and keeps secondary rows`() = runTest {
        val snapshot = MediaIndexDeltaTestFixtures.twoVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_29,
            primaryVersion = "v9",
            secondaryVersion = "v4"
        )
        val storedWatermarks = mapOf(
            MediaIndexDeltaTestFixtures.PRIMARY_VOLUME to MediaIndexVolumeWatermark("v8", null, 100L),
            MediaIndexDeltaTestFixtures.SECONDARY_VOLUME to MediaIndexVolumeWatermark("v4", null, 50L)
        )

        val plan = planMediaIndexDelta(snapshot, storedWatermarks)
        assertEquals(setOf(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME), plan.volumesToResync)

        val store = InMemoryMediaIndexStore()
        store.upsert(MediaIndexDeltaTestFixtures.twoVolumeIndexRows())

        val reconciler = MediaIndexReconciler(store)
        reconciler.reconcileVolume(
            volumeName = MediaIndexDeltaTestFixtures.PRIMARY_VOLUME,
            liveMedia = emptyList(),
            presentVolumes = plan.presentVolumes
        )

        assertTrue(store.listByVolume(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME).isEmpty())
        assertEquals(
            listOf("content://media/0123-4567/images/media/2"),
            store.listByVolume(MediaIndexDeltaTestFixtures.SECONDARY_VOLUME).map { it.mediaUri }
        )
    }

    @Test
    fun `api 30 plus changed primary resyncs primary rows and keeps secondary rows`() = runTest {
        val snapshot = MediaIndexDeltaTestFixtures.twoVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            primaryVersion = "v9",
            secondaryVersion = "v4",
            primaryGeneration = 22L,
            secondaryGeneration = 7L
        )
        val storedWatermarks = mapOf(
            MediaIndexDeltaTestFixtures.PRIMARY_VOLUME to MediaIndexVolumeWatermark("v8", 21L, null),
            MediaIndexDeltaTestFixtures.SECONDARY_VOLUME to MediaIndexVolumeWatermark("v4", 7L, null)
        )

        val plan = planMediaIndexDelta(snapshot, storedWatermarks)
        assertEquals(setOf(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME), plan.volumesToResync)

        val store = InMemoryMediaIndexStore()
        store.upsert(MediaIndexDeltaTestFixtures.twoVolumeIndexRows())

        val reconciler = MediaIndexReconciler(store)
        reconciler.reconcileVolume(
            volumeName = MediaIndexDeltaTestFixtures.PRIMARY_VOLUME,
            liveMedia = emptyList(),
            presentVolumes = plan.presentVolumes
        )

        assertTrue(store.listByVolume(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME).isEmpty())
        assertEquals(
            listOf("content://media/0123-4567/images/media/2"),
            store.listByVolume(MediaIndexDeltaTestFixtures.SECONDARY_VOLUME).map { it.mediaUri }
        )
    }

    @Test
    fun `reconciliation deletes missing rows for a present volume`() = runTest {
        val store = InMemoryMediaIndexStore()
        val missingUri = "content://media/external_primary/images/media/11"
        store.upsert(listOf(sampleEntry(missingUri, "external_primary")))

        val result = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external_primary",
            liveMedia = emptyList(),
            presentVolumes = setOf("external_primary")
        )

        assertEquals(setOf(missingUri), result.deletedMediaUris)
        assertTrue(store.listByVolume("external_primary").isEmpty())
    }

    @Test
    fun `reconciliation heals moved media by shared identity using in-memory join`() = runTest {
        val store = InMemoryMediaIndexStore()
        val originalUri = "content://media/external_primary/images/media/21"
        val movedUri = "content://media/0123-4567/images/media/99"

        store.upsert(
            listOf(
                MediaIndexEntry(
                    mediaUri = originalUri,
                    displayName = "same.jpg",
                    size = 321L,
                    dateModified = 777L,
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
            )
        )

        val live = listOf(mediaItem(movedUri, "same.jpg", size = 321L, dateModified = 777L))
        val result = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external_primary",
            liveMedia = live,
            presentVolumes = setOf("external_primary", "0123-4567")
        )

        assertTrue(result.deletedMediaUris.isEmpty())
        assertEquals(1, result.healedRewrites.size)
        assertEquals(originalUri, result.healedRewrites.single().fromMediaUri)
        assertEquals(movedUri, result.healedRewrites.single().toMediaUri)

        val rows = store.listByVolume("0123-4567")
        assertEquals(1, rows.size)
        assertEquals(movedUri, rows.single().mediaUri)
        assertEquals("0123-4567", rows.single().volumeName)
    }

    @Test
    fun `api 28 heal keeps row on external_primary and row remains reconcilable`() = runTest {
        val store = InMemoryMediaIndexStore()
        val staleUri = "content://media/external_primary/images/media/31"
        val healedLegacyUri = "content://media/external/images/media/331"

        store.upsert(
            listOf(
                MediaIndexEntry(
                    mediaUri = staleUri,
                    displayName = "legacy.jpg",
                    size = 987L,
                    dateModified = 654L,
                    volumeName = "external_primary",
                    generationAdded = null,
                    generationModified = null,
                    capturedDateTakenEpochMillis = null,
                    capturedLatitude = null,
                    capturedLongitude = null,
                    capturedWidth = null,
                    capturedHeight = null,
                    derivedCountryCode = null,
                    derivedRegionCode = null,
                    derivedPlaceName = null
                )
            )
        )

        val firstPass = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external_primary",
            liveMedia = listOf(mediaItem(healedLegacyUri, "legacy.jpg", size = 987L, dateModified = 654L)),
            presentVolumes = setOf("external_primary")
        )

        assertTrue(firstPass.deletedMediaUris.isEmpty())
        assertEquals(1, firstPass.healedRewrites.size)
        assertEquals("external_primary", firstPass.healedRewrites.single().toVolumeName)
        assertEquals(
            healedLegacyUri,
            store.listByVolume("external_primary").single().mediaUri
        )

        val secondPass = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external_primary",
            liveMedia = emptyList(),
            presentVolumes = setOf("external_primary")
        )

        assertEquals(setOf(healedLegacyUri), secondPass.deletedMediaUris)
        assertTrue(store.listByVolume("external_primary").isEmpty())
    }

    @Test
    fun `healed external uri keeps secondary volume name instead of orphaning to external`() = runTest {
        val store = InMemoryMediaIndexStore()
        val secondaryVolume = "0123-4567"
        val staleUri = "content://media/0123-4567/images/media/41"
        val healedLegacyUri = "content://media/external/images/media/401"

        store.upsert(
            listOf(
                MediaIndexEntry(
                    mediaUri = staleUri,
                    displayName = "secondary.jpg",
                    size = 999L,
                    dateModified = 555L,
                    volumeName = secondaryVolume,
                    generationAdded = null,
                    generationModified = null,
                    capturedDateTakenEpochMillis = null,
                    capturedLatitude = null,
                    capturedLongitude = null,
                    capturedWidth = null,
                    capturedHeight = null,
                    derivedCountryCode = null,
                    derivedRegionCode = null,
                    derivedPlaceName = null
                )
            )
        )

        MediaIndexReconciler(store).reconcileVolume(
            volumeName = secondaryVolume,
            liveMedia = listOf(mediaItem(healedLegacyUri, "secondary.jpg", size = 999L, dateModified = 555L)),
            presentVolumes = setOf(secondaryVolume)
        )

        val rows = store.listByVolume(secondaryVolume)
        assertEquals(1, rows.size)
        assertEquals(healedLegacyUri, rows.single().mediaUri)
        assertEquals(secondaryVolume, rows.single().volumeName)
    }

    @Test
    fun `reconciliation skips deletion when target volume is not present`() = runTest {
        val store = InMemoryMediaIndexStore()
        val uri = "content://media/external/images/media/41"
        store.upsert(listOf(sampleEntry(uri, "external")))

        val result = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external",
            liveMedia = emptyList(),
            presentVolumes = setOf("external_primary")
        )

        assertTrue(result.deletedMediaUris.isEmpty())
        assertTrue(result.healedRewrites.isEmpty())
        assertEquals(uri, store.listByVolume("external").single().mediaUri)
    }

    @Test
    fun `mediaStoreVolumeNameFromUri parses standard media uris without Uri parse`() {
        assertEquals(
            "external_primary",
            mediaStoreVolumeNameFromUri("content://media/external_primary/images/media/123")
        )
        assertEquals(
            "external",
            mediaStoreVolumeNameFromUri("content://media/external/video/media/456")
        )
        assertNull(mediaStoreVolumeNameFromUri("not-a-media-uri"))
    }

    private fun sampleEntry(mediaUri: String, volumeName: String): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = "photo.jpg",
            size = 100L,
            dateModified = 200L,
            volumeName = volumeName,
            generationAdded = null,
            generationModified = null,
            capturedDateTakenEpochMillis = null,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )
    }

    private fun mediaItem(
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
            dateAdded = 0L,
            dateModified = dateModified,
            size = size,
            type = MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
    }
}
