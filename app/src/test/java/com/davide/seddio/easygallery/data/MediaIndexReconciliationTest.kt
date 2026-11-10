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
    fun `row with the identical external live uri is a direct match and is left alone`() = runTest {
        val store = InMemoryMediaIndexStore()
        val uri = "content://media/external/images/media/51"
        val row = entry(uri, "external", "direct.jpg", size = 10L, dateModified = 20L)
        store.upsert(listOf(row))

        val result = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external",
            liveMedia = listOf(mediaItem(uri, "direct.jpg", size = 10L, dateModified = 20L)),
            presentVolumes = setOf("external")
        )

        assertTrue(result.deletedMediaUris.isEmpty())
        assertTrue(result.healedRewrites.isEmpty())
        assertEquals(listOf(row), store.listByVolume("external"))
    }

    @Test
    fun `per-volume spellings of a live external uri are direct matches`() = runTest {
        listOf(
            "external_primary" to "content://media/external_primary/images/media/52",
            "0123-4567" to "content://media/0123-4567/images/media/52"
        ).forEach { (volume, storedUri) ->
            val store = InMemoryMediaIndexStore()
            val row = entry(storedUri, volume, "spelled.jpg", size = 10L, dateModified = 20L)
            store.upsert(listOf(row))

            val result = MediaIndexReconciler(store).reconcileVolume(
                volumeName = volume,
                liveMedia = listOf(
                    mediaItem(
                        "content://media/external/images/media/52",
                        "spelled.jpg",
                        size = 10L,
                        dateModified = 20L
                    )
                ),
                presentVolumes = setOf(volume)
            )

            assertTrue(volume, result.deletedMediaUris.isEmpty())
            assertTrue(volume, result.healedRewrites.isEmpty())
            assertEquals(volume, listOf(row), store.listByVolume(volume))
        }
    }

    @Test
    fun `per-volume spelling with a changed healing triple is still kept`() = runTest {
        listOf(
            "external_primary" to "content://media/external_primary/video/media/53",
            "0123-4567" to "content://media/0123-4567/video/media/53"
        ).forEach { (volume, storedUri) ->
            val store = InMemoryMediaIndexStore()
            val row = entry(storedUri, volume, "edited.mp4", size = 10L, dateModified = 20L)
            store.upsert(listOf(row))

            val result = MediaIndexReconciler(store).reconcileVolume(
                volumeName = volume,
                liveMedia = listOf(
                    mediaItem(
                        "content://media/external/video/media/53",
                        "edited.mp4",
                        size = 99L,
                        dateModified = 300L
                    )
                ),
                presentVolumes = setOf(volume)
            )

            assertTrue(volume, result.deletedMediaUris.isEmpty())
            assertTrue(volume, result.healedRewrites.isEmpty())
            assertEquals(volume, listOf(row), store.listByVolume(volume))
        }
    }

    @Test
    fun `missing row is deleted while a live row on the same volume is kept`() = runTest {
        val store = InMemoryMediaIndexStore()
        val liveUri = "content://media/external_primary/images/media/54"
        val goneUri = "content://media/external_primary/images/media/55"
        store.upsert(
            listOf(
                entry(liveUri, "external_primary", "live.jpg", size = 1L, dateModified = 2L),
                entry(goneUri, "external_primary", "gone.jpg", size = 3L, dateModified = 4L)
            )
        )

        val result = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external_primary",
            liveMedia = listOf(
                mediaItem("content://media/external/images/media/54", "live.jpg", size = 1L, dateModified = 2L)
            ),
            presentVolumes = setOf("external_primary")
        )

        assertEquals(setOf(goneUri), result.deletedMediaUris)
        assertTrue(result.healedRewrites.isEmpty())
        assertEquals(listOf(liveUri), store.listByVolume("external_primary").map { it.mediaUri })
    }

    @Test
    fun `healing key collision heals onto the first live item`() = runTest {
        val store = InMemoryMediaIndexStore()
        val staleUri = "content://media/external/images/media/56"
        val firstUri = "content://media/external/images/media/156"
        val secondUri = "content://media/external/images/media/256"
        store.upsert(listOf(entry(staleUri, "external", "twin.jpg", size = 7L, dateModified = 8L)))

        val result = MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external",
            liveMedia = listOf(
                mediaItem(firstUri, "twin.jpg", size = 7L, dateModified = 8L),
                mediaItem(secondUri, "twin.jpg", size = 7L, dateModified = 8L)
            ),
            presentVolumes = setOf("external")
        )

        assertTrue(result.deletedMediaUris.isEmpty())
        assertEquals(firstUri, result.healedRewrites.single().toMediaUri)
        assertEquals(listOf(firstUri), store.listByVolume("external").map { it.mediaUri })
    }

    @Test
    fun `heal preserves captured and derived place columns and writes the live uri`() = runTest {
        val store = InMemoryMediaIndexStore()
        val staleUri = "content://media/external/images/media/57"
        val liveUri = "content://media/external/images/media/157"
        val row = entry(staleUri, "external", "place.jpg", size = 5L, dateModified = 6L).copy(
            capturedDateTakenEpochMillis = 1_700_000_000_000L,
            capturedLatitude = 48.85,
            capturedLongitude = 2.35,
            capturedWidth = 4000,
            capturedHeight = 3000,
            derivedCountryCode = "FR",
            derivedRegionCode = "IDF",
            derivedPlaceName = "Paris"
        )
        store.upsert(listOf(row))

        MediaIndexReconciler(store).reconcileVolume(
            volumeName = "external",
            liveMedia = listOf(mediaItem(liveUri, "place.jpg", size = 5L, dateModified = 6L)),
            presentVolumes = setOf("external")
        )

        assertEquals(listOf(row.copy(mediaUri = liveUri)), store.listByVolume("external"))
    }

    @Test
    fun `heal keeps the resolver marker whether a row was resolved or still pending`() = runTest {
        listOf(PLACE_RESOLVER_VERSION, null).forEach { marker ->
            val store = InMemoryMediaIndexStore()
            val staleUri = "content://media/external/images/media/58"
            val liveUri = "content://media/external/images/media/158"
            store.upsert(
                listOf(
                    entry(staleUri, "external", "marked.jpg", size = 5L, dateModified = 6L).copy(
                        capturedLatitude = 48.85,
                        capturedLongitude = 2.35,
                        derivedPlaceResolverVersion = marker
                    )
                )
            )

            MediaIndexReconciler(store).reconcileVolume(
                volumeName = "external",
                liveMedia = listOf(mediaItem(liveUri, "marked.jpg", size = 5L, dateModified = 6L)),
                presentVolumes = setOf("external")
            )

            val healed = store.listByVolume("external").single()
            assertEquals("marker $marker", liveUri, healed.mediaUri)
            assertEquals("marker $marker", marker, healed.derivedPlaceResolverVersion)
        }
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

    private fun entry(
        mediaUri: String,
        volumeName: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): MediaIndexEntry {
        return sampleEntry(mediaUri, volumeName).copy(
            displayName = displayName,
            size = size,
            dateModified = dateModified
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
