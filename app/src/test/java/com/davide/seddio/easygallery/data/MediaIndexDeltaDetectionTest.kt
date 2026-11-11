package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexDeltaDetectionTest {

    @Test
    fun `resolveMediaStoreDeltaApiBranch selects API 28 below Q`() {
        assertEquals(MediaStoreDeltaApiBranch.API_28, resolveMediaStoreDeltaApiBranch(28))
    }

    @Test
    fun `resolveMediaStoreDeltaApiBranch selects API 29 for Q`() {
        assertEquals(MediaStoreDeltaApiBranch.API_29, resolveMediaStoreDeltaApiBranch(29))
    }

    @Test
    fun `resolveMediaStoreDeltaApiBranch selects API 30 plus for R and above`() {
        assertEquals(MediaStoreDeltaApiBranch.API_30_PLUS, resolveMediaStoreDeltaApiBranch(30))
        assertEquals(MediaStoreDeltaApiBranch.API_30_PLUS, resolveMediaStoreDeltaApiBranch(34))
    }

    @Test
    fun `api 30 plus plan resyncs only changed volume`() {
        val snapshot = MediaIndexDeltaTestFixtures.twoVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            primaryVersion = "v2",
            secondaryVersion = "v1",
            primaryGeneration = 20L,
            secondaryGeneration = 8L,
            knownButUnavailableVolumes = setOf("0123-4567")
        )
        val stored = mapOf(
            MediaIndexDeltaTestFixtures.PRIMARY_VOLUME to MediaIndexVolumeWatermark("v1", 10L, null),
            MediaIndexDeltaTestFixtures.SECONDARY_VOLUME to MediaIndexVolumeWatermark("v1", 8L, null)
        )

        val plan = planMediaIndexDelta(snapshot, stored)

        assertEquals(setOf(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME), plan.volumesToResync)
        assertEquals("v2", plan.nextWatermarks.getValue(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME).version)
        assertEquals(20L, plan.nextWatermarks.getValue(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME).generation)
        assertEquals(
            setOf(MediaIndexDeltaTestFixtures.SECONDARY_VOLUME),
            plan.presentVolumes - plan.volumesToResync
        )
    }

    @Test
    fun `api 29 plan still scopes resync by volume`() {
        val snapshot = MediaIndexDeltaTestFixtures.twoVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_29,
            primaryVersion = "v3",
            secondaryVersion = "v7"
        )
        val stored = mapOf(
            MediaIndexDeltaTestFixtures.PRIMARY_VOLUME to MediaIndexVolumeWatermark("v2", null, null),
            MediaIndexDeltaTestFixtures.SECONDARY_VOLUME to MediaIndexVolumeWatermark("v7", null, null)
        )

        val plan = planMediaIndexDelta(snapshot, stored)

        assertEquals(setOf(MediaIndexDeltaTestFixtures.PRIMARY_VOLUME), plan.volumesToResync)
        assertEquals(
            "v7",
            plan.nextWatermarks.getValue(MediaIndexDeltaTestFixtures.SECONDARY_VOLUME).version
        )
    }

    @Test
    fun `selectRowsForReindex uses generation columns on API 30 plus`() {
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(
                ObservedMediaStoreVolume("external_primary", "v2", 20L)
            ),
            presentVolumes = setOf("external_primary"),
            knownButUnavailableVolumes = emptySet()
        )
        val stored = mapOf(
            "external_primary" to MediaIndexVolumeWatermark("v2", 10L, null)
        )

        val plan = planMediaIndexDelta(snapshot, stored)
        val rows = listOf(
            MediaStoreObservedRowDelta("uri-1", "external_primary", 9L, 9L, 100L),
            MediaStoreObservedRowDelta("uri-2", "external_primary", 11L, 11L, 101L),
            MediaStoreObservedRowDelta("uri-3", "external_primary", 7L, 12L, 102L)
        )

        val selection = selectRowsForReindex(plan, stored, rows)

        assertEquals(listOf("uri-2", "uri-3"), selection.rowsToReindex.map { it.mediaUri })
        assertEquals(102L, selection.maxDateModifiedWatermarkByVolume["external_primary"])
    }

    @Test
    fun `selectRowsForReindex uses date modified watermark on API 29`() {
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_29,
            observedVolumes = listOf(
                ObservedMediaStoreVolume("external_primary", "v1", null)
            ),
            presentVolumes = setOf("external_primary"),
            knownButUnavailableVolumes = emptySet()
        )
        val stored = mapOf(
            "external_primary" to MediaIndexVolumeWatermark("v1", null, 200L)
        )

        val plan = planMediaIndexDelta(snapshot, stored)
        val rows = listOf(
            MediaStoreObservedRowDelta("uri-1", "external_primary", null, null, 200L),
            MediaStoreObservedRowDelta("uri-2", "external_primary", null, null, 201L)
        )

        val selection = selectRowsForReindex(plan, stored, rows)

        assertEquals(listOf("uri-2"), selection.rowsToReindex.map { it.mediaUri })
    }

    @Test
    fun `selectRowsForReindex uses date modified watermark on API 28`() {
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_28,
            observedVolumes = listOf(
                ObservedMediaStoreVolume("external_primary", "v1", null)
            ),
            presentVolumes = setOf("external_primary"),
            knownButUnavailableVolumes = emptySet()
        )
        val stored = mapOf(
            "external_primary" to MediaIndexVolumeWatermark("v1", null, 300L)
        )

        val plan = planMediaIndexDelta(snapshot, stored)
        val rows = listOf(
            MediaStoreObservedRowDelta("uri-1", "external_primary", null, null, 300L),
            MediaStoreObservedRowDelta("uri-2", "external_primary", null, null, 301L)
        )

        val selection = selectRowsForReindex(plan, stored, rows)

        assertEquals(listOf("uri-2"), selection.rowsToReindex.map { it.mediaUri })
    }

    @Test
    fun `selectRowsForReindex includes whole volume when plan requires full resync`() {
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_29,
            observedVolumes = listOf(
                ObservedMediaStoreVolume("external_primary", "v2", null)
            ),
            presentVolumes = setOf("external_primary"),
            knownButUnavailableVolumes = emptySet()
        )
        val stored = mapOf(
            "external_primary" to MediaIndexVolumeWatermark("v1", null, 200L)
        )

        val plan = planMediaIndexDelta(snapshot, stored)
        val rows = listOf(
            MediaStoreObservedRowDelta("uri-1", "external_primary", null, null, 100L),
            MediaStoreObservedRowDelta("uri-2", "external_primary", null, null, 200L)
        )

        val selection = selectRowsForReindex(plan, stored, rows)

        assertEquals(listOf("uri-1", "uri-2"), selection.rowsToReindex.map { it.mediaUri })
    }

    @Test
    fun `detect returns plan and stored watermarks from the same pre-pass read`() {
        val snapshot = MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = listOf(ObservedMediaStoreVolume("external_primary", "v2", 9L)),
            presentVolumes = setOf("external_primary"),
            knownButUnavailableVolumes = emptySet()
        )
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf("external_primary" to MediaIndexVolumeWatermark("v1", 2L, null))
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(snapshot)
        )

        val detection = detector.detect(sdkInt = 30)

        assertEquals(setOf("external_primary"), detection.plan.volumesToResync)
        assertEquals(
            mapOf("external_primary" to MediaIndexVolumeWatermark("v1", 2L, null)),
            detection.storedWatermarks
        )

        detector.persistWatermarks(detection.plan.nextWatermarks)
        assertEquals(MediaIndexVolumeWatermark("v2", 9L, null), store.read("external_primary"))
    }

    @Test
    fun `updateDateModifiedWatermark creates new volume watermark with provider version`() {
        val store = InMemoryMediaIndexWatermarkStore()
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(emptySnapshot()),
            versionProvider = FixedVersionProvider(mapOf("external_primary" to "v5"))
        )

        detector.updateDateModifiedWatermark("external_primary", 777L)

        assertEquals(
            MediaIndexVolumeWatermark("v5", generation = null, dateModifiedWatermarkSeconds = 777L),
            store.read("external_primary")
        )
    }

    @Test
    fun `updateDateModifiedWatermark updates existing volume watermark preserving version and generation`() {
        val store = InMemoryMediaIndexWatermarkStore(
            mapOf("external_primary" to MediaIndexVolumeWatermark("v4", generation = 21L, dateModifiedWatermarkSeconds = 100L))
        )
        val detector = MediaIndexDeltaDetector(
            watermarkStore = store,
            snapshotProvider = FixedSnapshotProvider(emptySnapshot())
        )

        detector.updateDateModifiedWatermark("external_primary", 888L)

        assertEquals(
            MediaIndexVolumeWatermark("v4", generation = 21L, dateModifiedWatermarkSeconds = 888L),
            store.read("external_primary")
        )
    }

    @Test
    fun `date modified fallback is conservative by watermark`() {
        assertTrue(shouldResyncByDateModified(200L, null))
        assertTrue(shouldResyncByDateModified(201L, 200L))
        assertFalse(shouldResyncByDateModified(200L, 200L))
        assertFalse(shouldResyncByDateModified(199L, 200L))
    }

    private class FixedSnapshotProvider(
        private val snapshot: MediaStoreVolumeSnapshot
    ) : MediaStoreVolumeSnapshotProvider {
        override fun snapshot(sdkInt: Int): MediaStoreVolumeSnapshot = snapshot
    }

    private class FixedVersionProvider(
        private val versionsByVolume: Map<String, String?>
    ) : MediaStoreVolumeVersionProvider {
        override fun versionForVolume(volumeName: String): String? = versionsByVolume[volumeName]
    }

    private fun emptySnapshot(): MediaStoreVolumeSnapshot {
        return MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_28,
            observedVolumes = emptyList(),
            presentVolumes = emptySet(),
            knownButUnavailableVolumes = emptySet()
        )
    }
}
