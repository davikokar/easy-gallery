package com.davide.seddio.easygallery.data

import android.net.Uri
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `evaluation projection distinguishes a null date from an absent row`() = runTest {
        val store = InMemoryMediaIndexStore()
        val withDate = sampleEntry(mediaUri = "content://media/external/images/media/3")
        val withoutDate = sampleEntry(
            mediaUri = "content://media/external/images/media/4",
            capturedDateTakenEpochMillis = null
        )
        store.upsert(listOf(withoutDate, withDate))

        val rows = store.observeEvaluationProjection().first().associateBy { it.identity.mediaUri }

        assertEquals(setOf(withDate.mediaUri, withoutDate.mediaUri), rows.keys)
        assertEquals(withDate.capturedDateTakenEpochMillis, rows.getValue(withDate.mediaUri).capturedDateTakenEpochMillis)
        assertNull(rows.getValue(withoutDate.mediaUri).capturedDateTakenEpochMillis)
        assertNull(rows["content://media/external/images/media/5"])
    }

    @Test
    fun `a chunk of conditional writes publishes one snapshot with every matching row written`() = runTest {
        val store = InMemoryMediaIndexStore()
        val first = sampleEntry(mediaUri = "content://media/external/images/media/8")
        val second = sampleEntry(mediaUri = "content://media/external/images/media/9")
        store.upsert(listOf(first, second))

        store.observeAll().test {
            assertEquals(2, awaitItem().size)

            val written = store.writeResolvedPlaces(
                listOf(first, second).map {
                    ResolvedPlaceWrite(it.mediaUri, 43.7696, 11.2558, "IT", "52", "Firenze", 3)
                }
            )

            assertEquals(2, written)
            assertEquals(listOf(3, 3), awaitItem().map { it.derivedPlaceResolverVersion })
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `evaluation projection carries identity date coordinates flag place values and marker`() = runTest {
        val store = InMemoryMediaIndexStore()
        val entry = sampleEntry(
            mediaUri = "content://media/external/images/media/101",
            displayName = "evaluated.jpg",
            capturedDateTakenEpochMillis = 1_729_000_000_321L,
            derivedCountryCode = "AA",
            derivedRegionCode = "BB-77",
            derivedPlaceName = "Distinct Place",
            derivedPlaceResolverVersion = 9
        )
        store.upsert(listOf(entry))

        assertEquals(
            listOf(
                MediaIndexEvaluationProjection(
                    identity = entry.toMediaIdentity(),
                    capturedDateTakenEpochMillis = 1_729_000_000_321L,
                    hasCoordinates = true,
                    derivedCountryCode = "AA",
                    derivedRegionCode = "BB-77",
                    derivedPlaceName = "Distinct Place",
                    derivedPlaceResolverVersion = 9
                )
            ),
            store.observeEvaluationProjection().first()
        )
    }

    @Test
    fun `evaluation projection keeps a pending row's old values and separates out-of-cap from no coordinates`() = runTest {
        val store = InMemoryMediaIndexStore()
        store.upsert(
            listOf(
                sampleEntry(
                    mediaUri = "content://media/external/images/media/111",
                    derivedCountryCode = "IT",
                    derivedPlaceName = "Firenze"
                ),
                sampleEntry(mediaUri = "content://media/external/images/media/112", derivedPlaceResolverVersion = 2),
                sampleEntry(
                    mediaUri = "content://media/external/images/media/113",
                    capturedLatitude = null,
                    capturedLongitude = null
                )
            )
        )

        val byUri = store.observeEvaluationProjection().first().associateBy { it.identity.mediaUri }
        val pending = byUri.getValue("content://media/external/images/media/111")
        val outOfCap = byUri.getValue("content://media/external/images/media/112")
        val noCoordinates = byUri.getValue("content://media/external/images/media/113")

        assertNull(pending.derivedPlaceResolverVersion)
        assertEquals("IT", pending.derivedCountryCode)
        assertEquals("Firenze", pending.derivedPlaceName)
        assertTrue(outOfCap.hasCoordinates)
        assertEquals(2, outOfCap.derivedPlaceResolverVersion)
        assertNull(outOfCap.derivedCountryCode)
        assertTrue(!noCoordinates.hasCoordinates)
        assertNull(noCoordinates.derivedPlaceResolverVersion)
    }

    @Test
    fun `evaluation projection re-emits on upsert and on delete`() = runTest {
        val store = InMemoryMediaIndexStore()
        val uri = "content://media/external/images/media/121"

        store.observeEvaluationProjection().test {
            assertTrue(awaitItem().isEmpty())

            store.upsert(listOf(sampleEntry(mediaUri = uri)))
            assertEquals(1, awaitItem().size)

            // A genuinely different row: StateFlow conflates an equal snapshot.
            store.upsert(listOf(sampleEntry(mediaUri = uri, derivedPlaceResolverVersion = 2)))
            assertEquals(2, awaitItem().single().derivedPlaceResolverVersion)

            store.deleteByMediaUri(uri)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an entry built without a marker carries null so it reads as pending`() {
        assertNull(sampleEntry(mediaUri = "content://media/external/images/media/10").derivedPlaceResolverVersion)
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
            derivedPlaceName = null,
            derivedPlaceResolverVersion = null
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
        capturedDateTakenEpochMillis: Long? = 1_726_000_000_000L,
        derivedCountryCode: String? = null,
        derivedRegionCode: String? = null,
        derivedPlaceName: String? = null,
        capturedLatitude: Double? = 43.7696,
        capturedLongitude: Double? = 11.2558,
        derivedPlaceResolverVersion: Int? = null
    ): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = displayName,
            size = 500L,
            dateModified = 1_000L,
            volumeName = volumeName,
            generationAdded = 5L,
            generationModified = 6L,
            capturedDateTakenEpochMillis = capturedDateTakenEpochMillis,
            capturedLatitude = capturedLatitude,
            capturedLongitude = capturedLongitude,
            capturedWidth = 1080,
            capturedHeight = 1920,
            derivedCountryCode = derivedCountryCode,
            derivedRegionCode = derivedRegionCode,
            derivedPlaceName = derivedPlaceName,
            derivedPlaceResolverVersion = derivedPlaceResolverVersion
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
