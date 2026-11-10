package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexDaoTest {

    private lateinit var database: MediaIndexDatabase
    private lateinit var dao: MediaIndexDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaIndexDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.mediaIndexDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert emits row and preserves null derived columns`() = runTest {
        dao.observeAll().test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(listOf(sampleEntity(mediaUri = "content://media/external/images/media/1")))

            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertNull(rows.single().derivedCountryCode)
            assertNull(rows.single().derivedRegionCode)
            assertNull(rows.single().derivedPlaceName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert replaces existing row by media uri`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/2",
                    displayName = "before.jpg",
                    capturedWidth = 800,
                    capturedHeight = 600
                )
            )
        )

        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/2",
                    displayName = "after.jpg",
                    capturedWidth = 1920,
                    capturedHeight = 1080,
                    derivedCountryCode = "IT",
                    derivedRegionCode = "52",
                    derivedPlaceName = "Firenze"
                )
            )
        )

        val stored = dao.getByMediaUri("content://media/external/images/media/2")
        assertNotNull(stored)
        assertEquals("after.jpg", stored?.displayName)
        assertEquals(1920, stored?.capturedWidth)
        assertEquals("IT", stored?.derivedCountryCode)
    }

    @Test
    fun `delete by volume removes only targeted volume`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/11",
                    volumeName = "external_primary"
                ),
                sampleEntity(
                    mediaUri = "content://media/external/images/media/12",
                    volumeName = "external"
                )
            )
        )

        dao.deleteByVolume("external")

        assertNotNull(dao.getByMediaUri("content://media/external/images/media/11"))
        assertNull(dao.getByMediaUri("content://media/external/images/media/12"))
    }

    @Test
    fun `observe by volume emits only matching rows`() = runTest {
        dao.observeByVolume("external_primary").test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(
                listOf(
                    sampleEntity(
                        mediaUri = "content://media/external/images/media/21",
                        volumeName = "external_primary"
                    ),
                    sampleEntity(
                        mediaUri = "content://media/external/images/media/22",
                        volumeName = "external"
                    )
                )
            )

            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("content://media/external/images/media/21", rows.single().mediaUri)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `identity date taken projection returns each column unshuffled`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/41",
                    displayName = "projected.jpg",
                    size = 4_141L,
                    dateModified = 5_252L,
                    capturedDateTakenEpochMillis = 1_727_000_000_123L
                )
            )
        )

        val rows = dao.observeIdentityDateTaken().first()

        assertEquals(
            listOf(
                MediaIndexIdentityDateTakenEntity(
                    mediaUri = "content://media/external/images/media/41",
                    displayName = "projected.jpg",
                    size = 4_141L,
                    dateModified = 5_252L,
                    capturedDateTakenEpochMillis = 1_727_000_000_123L
                )
            ),
            rows
        )
    }

    @Test
    fun `identity date taken projection re-emits on upsert and on delete`() = runTest {
        dao.observeIdentityDateTaken().test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(listOf(sampleEntity(mediaUri = "content://media/external/images/media/51")))
            assertEquals(
                listOf("content://media/external/images/media/51"),
                awaitItem().map { it.mediaUri }
            )

            dao.upsert(
                listOf(
                    sampleEntity(
                        mediaUri = "content://media/external/images/media/51",
                        capturedDateTakenEpochMillis = 1_728_000_000_000L
                    )
                )
            )
            assertEquals(1_728_000_000_000L, awaitItem().single().capturedDateTakenEpochMillis)

            dao.deleteByMediaUri("content://media/external/images/media/51")
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `identity date taken projection returns null date row and does not filter by volume`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/61",
                    capturedDateTakenEpochMillis = null
                ),
                sampleEntity(
                    mediaUri = "content://media/external/images/media/62",
                    volumeName = "unmounted_volume"
                )
            )
        )

        val byUri = dao.observeIdentityDateTaken().first().associateBy { it.mediaUri }

        assertEquals(setOf("content://media/external/images/media/61", "content://media/external/images/media/62"), byUri.keys)
        assertNull(byUri.getValue("content://media/external/images/media/61").capturedDateTakenEpochMillis)
        assertNotNull(byUri.getValue("content://media/external/images/media/62").capturedDateTakenEpochMillis)
        assertNull(byUri["content://media/external/images/media/63"])
    }

    @Test
    fun `pending place query orders by uri and applies marker coordinates and cursor filters`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(mediaUri = "content://media/external/images/media/3", derivedPlaceResolverVersion = 2),
                sampleEntity(mediaUri = "content://media/external/images/media/2", derivedPlaceResolverVersion = 1),
                sampleEntity(mediaUri = "content://media/external/images/media/1"),
                sampleEntity(
                    mediaUri = "content://media/external/images/media/4",
                    capturedLatitude = null,
                    capturedLongitude = null
                ),
                sampleEntity(mediaUri = "content://media/external/images/media/5")
            )
        )

        val all = dao.listPendingPlaceResolution(currentVersion = 2, afterMediaUri = null, limit = 10)
        val afterFirst = dao.listPendingPlaceResolution(
            currentVersion = 2,
            afterMediaUri = "content://media/external/images/media/1",
            limit = 1
        )

        assertEquals(
            listOf(
                "content://media/external/images/media/1",
                "content://media/external/images/media/2",
                "content://media/external/images/media/5"
            ),
            all.map { it.mediaUri }
        )
        assertEquals(listOf("content://media/external/images/media/2"), afterFirst.map { it.mediaUri })
    }

    @Test
    fun `resolved place update matches uri and exact coordinates and reports rows written`() = runTest {
        val uri = "content://media/external/images/media/81"
        dao.upsert(listOf(sampleEntity(mediaUri = uri, derivedCountryCode = "XX")))

        val wrongLatitude = dao.updateResolvedPlace(uri, 43.7697, 11.2558, "IT", "52", "Firenze", 2)
        val wrongLongitude = dao.updateResolvedPlace(uri, 43.7696, 11.2559, "IT", "52", "Firenze", 2)
        val wrongUri = dao.updateResolvedPlace("$uri-other", 43.7696, 11.2558, "IT", "52", "Firenze", 2)
        val untouched = dao.getByMediaUri(uri)
        val matched = dao.updateResolvedPlace(uri, 43.7696, 11.2558, null, null, null, 2)
        val stored = dao.getByMediaUri(uri)

        assertEquals(listOf(0, 0, 0, 1), listOf(wrongLatitude, wrongLongitude, wrongUri, matched))
        assertEquals("XX", untouched?.derivedCountryCode)
        assertNull(untouched?.derivedPlaceResolverVersion)
        assertNull(stored?.derivedCountryCode)
        assertEquals(2, stored?.derivedPlaceResolverVersion)
    }

    @Test
    fun `watermark table upsert list and delete work by volume`() {
        dao.upsertWatermark(
            MediaIndexWatermarkEntity(
                volumeName = "external_primary",
                version = "v3",
                generation = 11L,
                dateModifiedWatermarkSeconds = 900L
            )
        )
        dao.upsertWatermark(
            MediaIndexWatermarkEntity(
                volumeName = "external",
                version = "v4",
                generation = null,
                dateModifiedWatermarkSeconds = 700L
            )
        )

        val all = dao.listWatermarks().associateBy { it.volumeName }
        assertEquals(setOf("external_primary", "external"), all.keys)
        assertEquals("v3", all.getValue("external_primary").version)

        dao.deleteWatermark("external")
        assertNull(dao.getWatermark("external"))
        assertNotNull(dao.getWatermark("external_primary"))
    }

    private fun sampleEntity(
        mediaUri: String,
        displayName: String = "photo.jpg",
        volumeName: String = "external_primary",
        size: Long = 123L,
        dateModified: Long = 456L,
        capturedDateTakenEpochMillis: Long? = 1_725_000_000_000L,
        capturedWidth: Int? = null,
        capturedHeight: Int? = null,
        derivedCountryCode: String? = null,
        derivedRegionCode: String? = null,
        derivedPlaceName: String? = null,
        capturedLatitude: Double? = 43.7696,
        capturedLongitude: Double? = 11.2558,
        derivedPlaceResolverVersion: Int? = null
    ): MediaIndexEntity {
        return MediaIndexEntity(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            volumeName = volumeName,
            generationAdded = 10L,
            generationModified = 11L,
            capturedDateTakenEpochMillis = capturedDateTakenEpochMillis,
            capturedLatitude = capturedLatitude,
            capturedLongitude = capturedLongitude,
            capturedWidth = capturedWidth,
            capturedHeight = capturedHeight,
            derivedCountryCode = derivedCountryCode,
            derivedRegionCode = derivedRegionCode,
            derivedPlaceName = derivedPlaceName,
            derivedPlaceResolverVersion = derivedPlaceResolverVersion
        )
    }
}
