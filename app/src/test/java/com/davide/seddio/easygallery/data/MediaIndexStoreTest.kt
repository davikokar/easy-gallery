package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun `room and in-memory stores emit same identity date taken rows`() = runTest {
        val inMemory = InMemoryMediaIndexStore()
        val roomBacked = RoomMediaIndexStore(database)
        val entries = listOf(
            sampleEntry(mediaUri = "content://media/external/images/media/82", displayName = "b.jpg"),
            sampleEntry(
                mediaUri = "content://media/external/images/media/81",
                displayName = "a.jpg",
                capturedDateTakenEpochMillis = null
            )
        )
        inMemory.upsert(entries)
        roomBacked.upsert(entries)

        val inMemoryRows = inMemory.observeIdentityDateTaken().first()
        val roomRows = roomBacked.observeIdentityDateTaken().first()

        assertEquals(inMemoryRows, roomRows)
        assertEquals(
            listOf("content://media/external/images/media/81", "content://media/external/images/media/82"),
            roomRows.map { it.identity.mediaUri }
        )
        assertEquals(entries[1].toMediaIdentity(), roomRows.first().identity)
        assertNull(roomRows.first().capturedDateTakenEpochMillis)
    }

    @Test
    fun `room identity date taken re-emits when only an upsert lands`() = runTest {
        val store = RoomMediaIndexStore(database)

        store.observeIdentityDateTaken().test {
            assertTrue(awaitItem().isEmpty())

            store.upsert(listOf(sampleEntry(mediaUri = "content://media/external/images/media/91")))

            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
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
            derivedPlaceName = "Distinct Place",
            derivedPlaceResolverVersion = 9
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
        assertEquals(entry.derivedPlaceResolverVersion, stored?.derivedPlaceResolverVersion)
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
            derivedPlaceName = "Firenze",
            derivedPlaceResolverVersion = 7
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
        assertEquals(entity.derivedPlaceResolverVersion, stored?.derivedPlaceResolverVersion)
    }

    @Test
    fun `pending selection returns located rows whose marker is null or not current`() = runTest {
        forEachStore { store ->
            store.upsert(
                listOf(
                    sampleEntry(uri(1), derivedPlaceResolverVersion = null),
                    sampleEntry(uri(2), derivedPlaceResolverVersion = 1),
                    sampleEntry(uri(3), derivedPlaceResolverVersion = CURRENT),
                    sampleEntry(uri(4), capturedLatitude = null, capturedLongitude = null),
                    sampleEntry(uri(5), capturedLongitude = null),
                    sampleEntry(uri(6), capturedLatitude = null, derivedPlaceResolverVersion = 1)
                )
            )

            val pending = store.listPendingPlaceResolution(CURRENT, afterMediaUri = null, limit = 100)

            assertEquals(listOf(uri(1), uri(2)), pending.map { it.mediaUri })
        }
    }

    @Test
    fun `pending selection pages by cursor and terminates when nothing is written`() = runTest {
        forEachStore { store ->
            store.upsert((1..5).map { sampleEntry(uri(it)) })

            val pages = mutableListOf<List<String>>()
            var cursor: String? = null
            while (true) {
                val page = store.listPendingPlaceResolution(CURRENT, cursor, limit = 2)
                if (page.isEmpty()) break
                pages += page.map { it.mediaUri }
                cursor = page.last().mediaUri
            }

            assertEquals(listOf(listOf(uri(1), uri(2)), listOf(uri(3), uri(4)), listOf(uri(5))), pages)
            assertEquals(5, store.listPendingPlaceResolution(CURRENT, null, limit = 100).size)
        }
    }

    @Test
    fun `pending selection rejects a non-positive limit`() = runTest {
        forEachStore { store ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.listPendingPlaceResolution(CURRENT, null, limit = 0) }
            }
        }
    }

    @Test
    fun `conditional write sets the three derived columns and the marker and leaves the rest`() = runTest {
        forEachStore { store ->
            val before = sampleEntry(uri(1))
            val other = sampleEntry(uri(2))
            store.upsert(listOf(before, other))

            val written = store.writeResolvedPlaces(
                listOf(resolved(uri(1), countryCode = "IT", regionCode = "52", placeName = "Firenze"))
            )

            assertEquals(1, written)
            assertEquals(
                before.copy(
                    derivedCountryCode = "IT",
                    derivedRegionCode = "52",
                    derivedPlaceName = "Firenze",
                    derivedPlaceResolverVersion = CURRENT
                ),
                store.getByMediaUri(uri(1))
            )
            assertEquals(other, store.getByMediaUri(uri(2)))
            assertEquals(listOf(uri(2)), store.listPendingPlaceResolution(CURRENT, null, 100).map { it.mediaUri })
        }
    }

    @Test
    fun `conditional write of a result beyond the cap stamps the marker with null codes`() = runTest {
        forEachStore { store ->
            store.upsert(listOf(sampleEntry(uri(1), derivedCountryCode = "XX", derivedPlaceName = "Stale")))

            val written = store.writeResolvedPlaces(listOf(resolved(uri(1))))

            assertEquals(1, written)
            val row = store.getByMediaUri(uri(1))!!
            assertNull(row.derivedCountryCode)
            assertNull(row.derivedRegionCode)
            assertNull(row.derivedPlaceName)
            assertEquals(CURRENT, row.derivedPlaceResolverVersion)
            assertTrue(store.listPendingPlaceResolution(CURRENT, null, 100).isEmpty())
        }
    }

    @Test
    fun `conditional write is skipped when the coordinates changed since they were read`() = runTest {
        forEachStore { store ->
            store.upsert(listOf(sampleEntry(uri(1)), sampleEntry(uri(2))))
            // Tier A re-extracts both rows after the resolver read them: one latitude, one longitude differs.
            val reExtracted = listOf(
                sampleEntry(uri(1), capturedLatitude = 44.0, derivedCountryCode = "IT"),
                sampleEntry(uri(2), capturedLongitude = 12.0, derivedCountryCode = "IT")
            )
            store.upsert(reExtracted)

            val written = store.writeResolvedPlaces(listOf(resolved(uri(1)), resolved(uri(2))))

            assertEquals(0, written)
            assertEquals(reExtracted[0], store.getByMediaUri(uri(1)))
            assertEquals(reExtracted[1], store.getByMediaUri(uri(2)))
            assertEquals(2, store.listPendingPlaceResolution(CURRENT, null, 100).size)
        }
    }

    @Test
    fun `conditional write skips a deleted row and still writes the others in the same chunk`() = runTest {
        forEachStore { store ->
            store.upsert(listOf(sampleEntry(uri(1)), sampleEntry(uri(2))))
            store.deleteByMediaUri(uri(1))

            val written = store.writeResolvedPlaces(listOf(resolved(uri(1)), resolved(uri(2))))

            assertEquals(1, written)
            assertNull(store.getByMediaUri(uri(1)))
            assertEquals(CURRENT, store.getByMediaUri(uri(2))?.derivedPlaceResolverVersion)
        }
    }

    @Test
    fun `conditional write of an empty chunk writes nothing`() = runTest {
        forEachStore { store ->
            assertEquals(0, store.writeResolvedPlaces(emptyList()))
        }
    }

    @Test
    fun `a re-extracting upsert resets the marker to the entry's and keeps the carried derived values`() = runTest {
        forEachStore { store ->
            store.upsert(listOf(sampleEntry(uri(1))))
            store.writeResolvedPlaces(listOf(resolved(uri(1), countryCode = "IT", regionCode = "52", placeName = "Firenze")))
            val resolvedRow = store.getByMediaUri(uri(1))!!

            // What Tier A writes: the old derived values, the marker left at its default of null.
            store.upsert(
                listOf(
                    MediaIndexEntry(
                        mediaUri = resolvedRow.mediaUri,
                        displayName = resolvedRow.displayName,
                        size = resolvedRow.size,
                        dateModified = resolvedRow.dateModified,
                        volumeName = resolvedRow.volumeName,
                        generationAdded = resolvedRow.generationAdded,
                        generationModified = resolvedRow.generationModified,
                        capturedDateTakenEpochMillis = resolvedRow.capturedDateTakenEpochMillis,
                        capturedLatitude = resolvedRow.capturedLatitude,
                        capturedLongitude = resolvedRow.capturedLongitude,
                        capturedWidth = resolvedRow.capturedWidth,
                        capturedHeight = resolvedRow.capturedHeight,
                        derivedCountryCode = resolvedRow.derivedCountryCode,
                        derivedRegionCode = resolvedRow.derivedRegionCode,
                        derivedPlaceName = resolvedRow.derivedPlaceName
                    )
                )
            )

            val row = store.getByMediaUri(uri(1))!!
            assertNull(row.derivedPlaceResolverVersion)
            assertEquals("Firenze", row.derivedPlaceName)
            assertEquals(listOf(uri(1)), store.listPendingPlaceResolution(CURRENT, null, 100).map { it.mediaUri })
        }
    }

    @Test
    fun `a heal through delete and upsert keeps the marker`() = runTest {
        forEachStore { store ->
            store.upsert(listOf(sampleEntry(uri(1))))
            store.writeResolvedPlaces(listOf(resolved(uri(1), countryCode = "IT", regionCode = "52", placeName = "Firenze")))
            val resolvedRow = store.getByMediaUri(uri(1))!!

            // The reconciler's rewrite: copy the row under the healed URI and healing triple.
            val healed = resolvedRow.copy(mediaUri = uri(2), displayName = "healed.jpg", size = 501L, dateModified = 1_001L)
            store.deleteByMediaUri(resolvedRow.mediaUri)
            store.upsert(listOf(healed))

            assertNull(store.getByMediaUri(uri(1)))
            assertEquals(healed, store.getByMediaUri(uri(2)))
            assertEquals(CURRENT, store.getByMediaUri(uri(2))?.derivedPlaceResolverVersion)
            assertTrue(store.listPendingPlaceResolution(CURRENT, null, 100).isEmpty())
        }
    }

    @Test
    fun `a skipped conditional write does not re-emit observers`() = runTest {
        val store = RoomMediaIndexStore(database)
        store.upsert(listOf(sampleEntry(uri(1), capturedLatitude = 44.0)))

        store.observeAll().test {
            assertEquals(1, awaitItem().size)

            assertEquals(0, store.writeResolvedPlaces(listOf(resolved(uri(1)))))
            expectNoEvents()

            assertEquals(1, store.writeResolvedPlaces(listOf(resolved(uri(1), latitude = 44.0))))
            assertEquals(CURRENT, awaitItem().single().derivedPlaceResolverVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun forEachStore(check: suspend (MediaIndexStore) -> Unit) {
        listOf("in-memory" to InMemoryMediaIndexStore(), "room" to RoomMediaIndexStore(database)).forEach { (name, store) ->
            try {
                check(store)
            } catch (e: AssertionError) {
                throw AssertionError("[$name store] ${e.message}", e)
            }
        }
    }

    private fun uri(n: Int) = "content://media/external/images/media/$n"

    // Defaults match sampleEntry's coordinates, so a write built with no arguments is computed from the stored ones.
    private fun resolved(
        mediaUri: String,
        latitude: Double = 43.7696,
        longitude: Double = 11.2558,
        countryCode: String? = null,
        regionCode: String? = null,
        placeName: String? = null
    ) = ResolvedPlaceWrite(mediaUri, latitude, longitude, countryCode, regionCode, placeName, CURRENT)

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

    private companion object {
        const val CURRENT = 2
    }
}
