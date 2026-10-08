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
class MediaIndexStoreTest {

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
    fun `room and in-memory stores produce same final snapshot`() = runTest {
        val inMemory = InMemoryMediaIndexStore()
        val roomBacked = RoomMediaIndexStore(database)

        val inMemorySnapshot = runScenario(inMemory)
        val roomSnapshot = runScenario(roomBacked)

        assertEquals(inMemorySnapshot, roomSnapshot)
    }

    @Test
    fun `observe by volume filters entries`() = runTest {
        val store = RoomMediaIndexStore(database)

        store.observeByVolume("external_primary").test {
            assertTrue(awaitItem().isEmpty())

            store.upsert(
                listOf(
                    sampleEntry(mediaUri = "content://media/external/images/media/10"),
                    sampleEntry(
                        mediaUri = "content://media/external/images/media/11",
                        volumeName = "external"
                    )
                )
            )

            val filtered = awaitItem()
            assertEquals(1, filtered.size)
            assertEquals("content://media/external/images/media/10", filtered.single().mediaUri)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert maps every entry field to entity without transposition`() = runTest {
        val store = RoomMediaIndexStore(database)
        val entry = MediaIndexEntry(
            mediaUri = "content://media/external/images/media/31",
            displayName = "entry-distinct-name.jpg",
            size = 3333L,
            dateModified = 4444L,
            volumeName = "external_custom",
            generationAdded = 111L,
            generationModified = 222L,
            capturedDateTakenEpochMillis = 1_725_123_456_789L,
            capturedLatitude = 43.7001,
            capturedLongitude = 11.1999,
            capturedWidth = 1234,
            capturedHeight = 567,
            derivedCountryCode = "AA",
            derivedRegionCode = "BB-77",
            derivedPlaceName = "Distinct Place"
        )

        store.upsert(listOf(entry))

        val stored = dao.getByMediaUri(entry.mediaUri)
        assertNotNull(stored)
        assertEquals(entry.mediaUri, stored?.mediaUri)
        assertEquals(entry.displayName, stored?.displayName)
        assertEquals(entry.size, stored?.size)
        assertEquals(entry.dateModified, stored?.dateModified)
        assertEquals(entry.volumeName, stored?.volumeName)
        assertEquals(entry.generationAdded, stored?.generationAdded)
        assertEquals(entry.generationModified, stored?.generationModified)
        assertEquals(entry.capturedDateTakenEpochMillis, stored?.capturedDateTakenEpochMillis)
        assertEquals(entry.capturedLatitude, stored?.capturedLatitude)
        assertEquals(entry.capturedLongitude, stored?.capturedLongitude)
        assertEquals(entry.capturedWidth, stored?.capturedWidth)
        assertEquals(entry.capturedHeight, stored?.capturedHeight)
        assertEquals(entry.derivedCountryCode, stored?.derivedCountryCode)
        assertEquals(entry.derivedRegionCode, stored?.derivedRegionCode)
        assertEquals(entry.derivedPlaceName, stored?.derivedPlaceName)
    }

    @Test
    fun `read maps every entity field to store entry without transposition`() = runTest {
        val store = RoomMediaIndexStore(database)
        val entity = MediaIndexEntity(
            mediaUri = "content://media/external/images/media/32",
            displayName = "entity-distinct-name.jpg",
            size = 9876L,
            dateModified = 8765L,
            volumeName = "external_primary",
            generationAdded = 333L,
            generationModified = 444L,
            capturedDateTakenEpochMillis = 1_726_987_654_321L,
            capturedLatitude = 40.5005,
            capturedLongitude = 14.4004,
            capturedWidth = 2468,
            capturedHeight = 135,
            derivedCountryCode = "IT",
            derivedRegionCode = "52",
            derivedPlaceName = "Firenze"
        )

        dao.upsert(listOf(entity))

        val stored = store.getByMediaUri(entity.mediaUri)
        assertNotNull(stored)
        assertEquals(entity.mediaUri, stored?.mediaUri)
        assertEquals(entity.displayName, stored?.displayName)
        assertEquals(entity.size, stored?.size)
        assertEquals(entity.dateModified, stored?.dateModified)
        assertEquals(entity.volumeName, stored?.volumeName)
        assertEquals(entity.generationAdded, stored?.generationAdded)
        assertEquals(entity.generationModified, stored?.generationModified)
        assertEquals(entity.capturedDateTakenEpochMillis, stored?.capturedDateTakenEpochMillis)
        assertEquals(entity.capturedLatitude, stored?.capturedLatitude)
        assertEquals(entity.capturedLongitude, stored?.capturedLongitude)
        assertEquals(entity.capturedWidth, stored?.capturedWidth)
        assertEquals(entity.capturedHeight, stored?.capturedHeight)
        assertEquals(entity.derivedCountryCode, stored?.derivedCountryCode)
        assertEquals(entity.derivedRegionCode, stored?.derivedRegionCode)
        assertEquals(entity.derivedPlaceName, stored?.derivedPlaceName)
    }

    private suspend fun runScenario(store: MediaIndexStore): List<MediaIndexEntry> {
        store.upsert(
            listOf(
                sampleEntry(mediaUri = "content://media/external/images/media/21"),
                sampleEntry(
                    mediaUri = "content://media/external/images/media/22",
                    displayName = "old.jpg",
                    volumeName = "external"
                )
            )
        )

        store.upsert(
            listOf(
                sampleEntry(
                    mediaUri = "content://media/external/images/media/22",
                    displayName = "new.jpg",
                    volumeName = "external",
                    derivedCountryCode = "IT",
                    derivedRegionCode = "52",
                    derivedPlaceName = "Firenze"
                )
            )
        )

        val row = store.getByMediaUri("content://media/external/images/media/22")
        assertNotNull(row)
        assertEquals("new.jpg", row?.displayName)
        assertEquals("IT", row?.derivedCountryCode)

        store.deleteByMediaUri("content://media/external/images/media/21")
        assertNull(store.getByMediaUri("content://media/external/images/media/21"))

        return store.observeAll().first()
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
}
