package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import java.io.FileNotFoundException
import java.io.StringReader

// Every scenario runs against the in-memory store and the real Room store, so the conditional write is exercised in SQL.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexPlaceResolutionTest {

    private lateinit var database: MediaIndexDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaIndexDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun eachStore(block: suspend TestScope.(String, MediaIndexStore) -> Unit) = runTest {
        listOf<Pair<String, MediaIndexStore>>(
            "in-memory" to InMemoryMediaIndexStore(),
            "room" to RoomMediaIndexStore(database)
        ).forEach { (name, store) ->
            block(name, store)
        }
    }

    // ---- what a pass writes ----

    @Test
    fun `rows within the cap get country region place and the current marker`() = eachStore { name, store ->
        store.upsert(
            listOf(
                row(1, NEAR_ALPHA.first, NEAR_ALPHA.second),
                row(2, NEAR_BETA.first, NEAR_BETA.second)
            )
        )

        val result = resolver(store).resolvePending()

        val alpha = store.getByMediaUri(uri(1))!!
        assertEquals(name, "AA", alpha.derivedCountryCode)
        assertEquals(name, "01", alpha.derivedRegionCode)
        assertEquals(name, "Alpha", alpha.derivedPlaceName)
        assertEquals(name, PLACE_RESOLVER_VERSION, alpha.derivedPlaceResolverVersion)
        val beta = store.getByMediaUri(uri(2))!!
        assertEquals(name, "BB", beta.derivedCountryCode)
        assertNull(name, beta.derivedRegionCode)
        assertEquals(name, "Beta", beta.derivedPlaceName)
        assertEquals(name, PLACE_RESOLVER_VERSION, beta.derivedPlaceResolverVersion)
        assertEquals(name, 2, result.writtenRows)
        assertEquals(name, 2, result.resolvedWithPlaceRows)
    }

    @Test
    fun `out-of-cap rows are stamped with all derived values null and never reselected`() = eachStore { name, store ->
        store.upsert(
            listOf(row(1, FAR_FROM_ANYTHING.first, FAR_FROM_ANYTHING.second, country = "ZZ", region = "99", place = "Stale"))
        )
        var loads = 0
        val resolver = resolver(store) { loads += 1; syntheticGazetteer() }

        val first = resolver.resolvePending()
        val second = resolver.resolvePending()

        val stamped = store.getByMediaUri(uri(1))!!
        assertNull(name, stamped.derivedCountryCode)
        assertNull(name, stamped.derivedRegionCode)
        assertNull(name, stamped.derivedPlaceName)
        assertEquals(name, PLACE_RESOLVER_VERSION, stamped.derivedPlaceResolverVersion)
        assertEquals(name, 1, first.outOfCapRows)
        assertEquals(name, 0, first.resolvedWithPlaceRows)
        assertEquals(name, 0, second.selectedRows)
        assertEquals(name, 1, loads)
        assertTrue(name, store.listPendingPlaceResolution(PLACE_RESOLVER_VERSION, null, 10).isEmpty())
    }

    @Test
    fun `coordinates the gazetteer rejects are stamped as no place and not reselected`() = eachStore { name, store ->
        store.upsert(listOf(row(1, 200.0, 11.0)))

        val result = resolver(store).resolvePending()

        assertEquals(name, 1, result.outOfCapRows)
        assertEquals(name, PLACE_RESOLVER_VERSION, store.getByMediaUri(uri(1))!!.derivedPlaceResolverVersion)
        assertTrue(name, store.listPendingPlaceResolution(PLACE_RESOLVER_VERSION, null, 10).isEmpty())
    }

    @Test
    fun `rows without coordinates are never selected or stamped and nothing is loaded for them`() = eachStore { name, store ->
        store.upsert(listOf(row(1), row(2, lat = 10.0), row(3, lon = 20.0)))
        var loads = 0

        val result = resolver(store) { loads += 1; syntheticGazetteer() }.resolvePending()

        assertEquals(name, 0, result.selectedRows)
        assertNull(name, result.gazetteerLoadMillis)
        assertEquals(name, 0, loads)
        (1..3).forEach { assertNull(name, store.getByMediaUri(uri(it))!!.derivedPlaceResolverVersion) }
    }

    @Test
    fun `a version bump reselects rows stamped with any other version`() = eachStore { name, store ->
        store.upsert(
            listOf(
                row(1, NEAR_ALPHA.first, NEAR_ALPHA.second, marker = PLACE_RESOLVER_VERSION - 1, country = "ZZ", place = "Old"),
                row(2, NEAR_ALPHA.first, NEAR_ALPHA.second, marker = PLACE_RESOLVER_VERSION + 1, country = "ZZ", place = "Newer"),
                row(3, NEAR_ALPHA.first, NEAR_ALPHA.second, marker = PLACE_RESOLVER_VERSION, country = "ZZ", place = "Current")
            )
        )

        val result = resolver(store).resolvePending()

        assertEquals(name, 2, result.selectedRows)
        assertEquals(name, "Alpha", store.getByMediaUri(uri(1))!!.derivedPlaceName)
        assertEquals(name, "Alpha", store.getByMediaUri(uri(2))!!.derivedPlaceName)
        assertEquals(name, PLACE_RESOLVER_VERSION, store.getByMediaUri(uri(2))!!.derivedPlaceResolverVersion)
        assertEquals(name, "Current", store.getByMediaUri(uri(3))!!.derivedPlaceName)
    }

    // ---- paging ----

    @Test
    fun `paging covers more rows than one page with no duplicates or gaps`() = eachStore { name, store ->
        // Indexes 4, 8 and 12 have no coordinates; 10 sorts before 2 as a string.
        val rows = (1..14).map { index ->
            if (index % 4 == 0) row(index) else row(index, NEAR_ALPHA.first, NEAR_ALPHA.second)
        }
        store.upsert(rows)
        val located = rows.filter { it.capturedLatitude != null }.map { it.mediaUri }
        val recording = InterceptingStore(store)

        val result = resolver(recording, chunkSize = 4).resolvePending()

        assertEquals(name, located.size, result.selectedRows)
        assertEquals(name, located.size, result.writtenRows)
        assertEquals(name, 3, result.chunks)
        assertEquals(name, located.sorted(), recording.writtenUris)
        assertEquals(name, recording.writtenUris.size, recording.writtenUris.distinct().size)
        located.forEach { assertEquals(name, "Alpha", store.getByMediaUri(it)!!.derivedPlaceName) }
    }

    @Test
    fun `a row exactly filling the last page ends the pass on an empty page`() = eachStore { name, store ->
        store.upsert((1..8).map { row(it, NEAR_ALPHA.first, NEAR_ALPHA.second) })
        val recording = InterceptingStore(store)

        val result = resolver(recording, chunkSize = 4).resolvePending()

        assertEquals(name, 8, result.writtenRows)
        assertEquals(name, 2, result.chunks)
        assertEquals(name, 3, recording.listCalls)
    }

    @Test
    fun `a row whose write keeps being skipped does not trap the pass`() = eachStore { name, store ->
        store.upsert(listOf(row(1, NEAR_ALPHA.first, NEAR_ALPHA.second), row(2, NEAR_ALPHA.first, NEAR_ALPHA.second)))
        lateinit var recording: InterceptingStore
        recording = InterceptingStore(store, afterList = { page ->
            check(recording.listCalls < 20) { "$name: the pass is not paging by cursor" }
            // Tier A keeps re-extracting row 2 with fresh coordinates between every read and write.
            if (page.any { it.mediaUri == uri(2) }) {
                val current = store.getByMediaUri(uri(2))!!
                store.upsert(listOf(current.copy(capturedLongitude = 30.0 + recording.listCalls * 0.001, derivedPlaceResolverVersion = null)))
            }
        })

        val result = resolver(recording, chunkSize = 1).resolvePending()

        assertEquals(name, 2, result.selectedRows)
        assertEquals(name, 1, result.writtenRows)
        assertEquals(name, 1, result.skippedRows)
        assertEquals(name, 3, recording.listCalls)
        assertNull(name, store.getByMediaUri(uri(2))!!.derivedPlaceResolverVersion)
    }

    // ---- the conditional write ----

    @Test
    fun `a row whose coordinates changed between selection and write is skipped and stays pending`() = eachStore { name, store ->
        store.upsert(
            listOf(
                row(1, NEAR_ALPHA.first, NEAR_ALPHA.second),
                row(2, NEAR_ALPHA.first, NEAR_ALPHA.second, country = "ZZ", place = "Old label")
            )
        )
        var reExtracted = false
        val recording = InterceptingStore(store, afterList = {
            if (!reExtracted) {
                reExtracted = true
                val current = store.getByMediaUri(uri(2))!!
                store.upsert(
                    listOf(current.copy(capturedLatitude = NEAR_BETA.first, capturedLongitude = NEAR_BETA.second, derivedPlaceResolverVersion = null))
                )
            }
        })

        val result = resolver(recording).resolvePending()

        assertEquals(name, 2, result.selectedRows)
        assertEquals(name, 1, result.writtenRows)
        assertEquals(name, 1, result.skippedRows)
        assertEquals(name, "Alpha", store.getByMediaUri(uri(1))!!.derivedPlaceName)
        val skipped = store.getByMediaUri(uri(2))!!
        assertEquals(name, NEAR_BETA.first, skipped.capturedLatitude)
        assertEquals(name, "Old label", skipped.derivedPlaceName)
        assertNull(name, skipped.derivedPlaceResolverVersion)

        val next = resolver(store).resolvePending()

        assertEquals(name, 1, next.writtenRows)
        val resolved = store.getByMediaUri(uri(2))!!
        assertEquals(name, "Beta", resolved.derivedPlaceName)
        assertEquals(name, PLACE_RESOLVER_VERSION, resolved.derivedPlaceResolverVersion)
    }

    @Test
    fun `a row deleted between selection and write is skipped and not recreated`() = eachStore { name, store ->
        store.upsert(listOf(row(1, NEAR_ALPHA.first, NEAR_ALPHA.second), row(2, NEAR_ALPHA.first, NEAR_ALPHA.second)))
        val recording = InterceptingStore(store, afterList = { store.deleteByMediaUri(uri(2)) })

        val result = resolver(recording).resolvePending()

        assertEquals(name, 1, result.writtenRows)
        assertEquals(name, 1, result.skippedRows)
        assertNull(name, store.getByMediaUri(uri(2)))
    }

    // ---- cancellation and failure ----

    @Test
    fun `cancellation between chunks leaves later rows pending and rethrows`() = eachStore { name, store ->
        store.upsert((1..5).map { row(it, NEAR_ALPHA.first, NEAR_ALPHA.second) })
        lateinit var job: Job
        val recording = InterceptingStore(store, afterWrite = { writeCalls -> if (writeCalls == 1) job.cancel() })
        var thrown: CancellationException? = null

        job = launch {
            try {
                resolver(recording, chunkSize = 2).resolvePending()
            } catch (cancelled: CancellationException) {
                thrown = cancelled
                throw cancelled
            }
        }
        job.join()

        assertNotNull(name, thrown)
        assertEquals(name, 1, recording.writeCalls)
        assertEquals(name, PLACE_RESOLVER_VERSION, store.getByMediaUri(uri(1))!!.derivedPlaceResolverVersion)
        assertEquals(name, PLACE_RESOLVER_VERSION, store.getByMediaUri(uri(2))!!.derivedPlaceResolverVersion)
        (3..5).forEach { assertNull(name, store.getByMediaUri(uri(it))!!.derivedPlaceResolverVersion) }
        assertEquals(name, listOf(uri(3), uri(4), uri(5)), store.listPendingPlaceResolution(PLACE_RESOLVER_VERSION, null, 10).map { it.mediaUri })
    }

    @Test
    fun `a gazetteer that fails to load stamps nothing and surfaces the failure`() = eachStore { name, store ->
        val brokenLoaders = mapOf<String, () -> Gazetteer>(
            "empty places" to { load(places = "") },
            "no data rows" to { load(places = header(0)) },
            "truncated places" to { load(places = header(3) + "10.0\t20.0\tAlpha\tAA\t01\n") },
            "wrong version" to { load(places = header(1, version = PLACE_RESOLVER_VERSION + 1) + "10.0\t20.0\tAlpha\tAA\t01\n") },
            "missing asset" to { throw FileNotFoundException("gazetteer/places.tsv") }
        )

        brokenLoaders.forEach { (label, loader) ->
            store.upsert(listOf(row(1, NEAR_ALPHA.first, NEAR_ALPHA.second, country = "ZZ", place = "Old label")))

            val error = runCatching { resolver(store, loader = loader).resolvePending() }.exceptionOrNull()

            assertNotNull("$name/$label: no failure surfaced", error)
            val untouched = store.getByMediaUri(uri(1))!!
            assertNull("$name/$label", untouched.derivedPlaceResolverVersion)
            assertEquals("$name/$label", "Old label", untouched.derivedPlaceName)
            assertEquals("$name/$label", 1, store.listPendingPlaceResolution(PLACE_RESOLVER_VERSION, null, 10).size)
        }
    }

    // ---- reporting ----

    @Test
    fun `the gazetteer is loaded once per pass however many pages there are`() = eachStore { name, store ->
        store.upsert((1..5).map { row(it, NEAR_ALPHA.first, NEAR_ALPHA.second) })
        var loads = 0

        val result = resolver(store, chunkSize = 2) { loads += 1; syntheticGazetteer() }.resolvePending()

        assertEquals(name, 3, result.chunks)
        assertEquals(name, 1, loads)
    }

    @Test
    fun `result counts and the log line report the pass`() = eachStore { name, store ->
        store.upsert(
            listOf(
                row(1, NEAR_ALPHA.first, NEAR_ALPHA.second),
                row(2, NEAR_BETA.first, NEAR_BETA.second),
                row(3, FAR_FROM_ANYTHING.first, FAR_FROM_ANYTHING.second)
            )
        )
        val gazetteer = syntheticGazetteer()
        var nowNanos = 0L
        val tickMillis = 5L
        val resolver = MediaIndexPlaceResolver(
            store = store,
            gazetteerLoader = { gazetteer },
            nanoClock = { nowNanos += tickMillis * 1_000_000L; nowNanos }
        )

        val result = resolver.resolvePending()

        // Clock reads: start 5, load start 10, load end 15, finish 20 ms.
        assertEquals(
            MediaIndexPlaceResolutionResult(
                selectedRows = 3, writtenRows = 3, resolvedWithPlaceRows = 2, outOfCapRows = 1, skippedRows = 0,
                chunks = 1, elapsedMillis = 15L, gazetteerLoadMillis = 5L, gazetteerRetainedBytes = gazetteer.approxRetainedBytes
            ),
            result
        )
        assertEquals(name, 300L, result.rowsPerSecond)
        assertEquals(
            name,
            "pass selected=3 written=3 withPlace=2 outOfCap=1 skipped=0 chunks=1 elapsedMs=15 " +
                "rowsPerSec=300 gazetteerLoadMs=5 gazetteerBytes=${gazetteer.approxRetainedBytes}",
            result.toLogLine()
        )
    }

    @Test
    fun `an empty pass reports no load and no rate`() = eachStore { name, store ->
        val result = resolver(store).resolvePending()

        assertEquals(name, 0, result.selectedRows)
        assertNull(name, result.gazetteerRetainedBytes)
        assertTrue(name, result.toLogLine().contains("gazetteerLoadMs=n/a gazetteerBytes=n/a"))
    }

    // ---- the real asset, through the real store ----

    @Test
    fun `a Florence coordinate resolves end to end through the packaged asset and the Room store`() = runTest {
        val store = RoomMediaIndexStore(database)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected = GazetteerAssets.load(context).nearest(43.7696, 11.2558)!!
        store.upsert(
            listOf(
                row(1, 43.7696, 11.2558),
                row(2, -30.0, -140.0),
                row(3)
            )
        )

        val result = MediaIndexPlaceResolver(store, gazetteerLoader = { GazetteerAssets.load(context) }).resolvePending()

        assertEquals(2, result.selectedRows)
        assertEquals(2, result.writtenRows)
        val florence = store.getByMediaUri(uri(1))!!
        assertEquals("IT", florence.derivedCountryCode)
        assertEquals("Florence", florence.derivedPlaceName)
        assertNotNull(florence.derivedRegionCode)
        assertEquals(expected.regionCode, florence.derivedRegionCode)
        assertEquals(PLACE_RESOLVER_VERSION, florence.derivedPlaceResolverVersion)
        val ocean = store.getByMediaUri(uri(2))!!
        assertNull(ocean.derivedCountryCode)
        assertNull(ocean.derivedRegionCode)
        assertNull(ocean.derivedPlaceName)
        assertEquals(PLACE_RESOLVER_VERSION, ocean.derivedPlaceResolverVersion)
        assertNull(store.getByMediaUri(uri(3))!!.derivedPlaceResolverVersion)
        assertTrue(store.listPendingPlaceResolution(PLACE_RESOLVER_VERSION, null, 10).isEmpty())
    }

    // ---- fixtures ----

    // Delegates everything, and lets a test act after a read or a write, or record what was written.
    private class InterceptingStore(
        private val delegate: MediaIndexStore,
        private val afterList: suspend (List<MediaIndexEntry>) -> Unit = {},
        private val afterWrite: suspend (Int) -> Unit = {}
    ) : MediaIndexStore by delegate {
        var listCalls = 0
            private set
        var writeCalls = 0
            private set
        val writtenUris = mutableListOf<String>()

        override suspend fun listPendingPlaceResolution(
            currentResolverVersion: Int,
            afterMediaUri: String?,
            limit: Int
        ): List<MediaIndexEntry> {
            val page = delegate.listPendingPlaceResolution(currentResolverVersion, afterMediaUri, limit)
            listCalls += 1
            afterList(page)
            return page
        }

        override suspend fun writeResolvedPlaces(writes: List<ResolvedPlaceWrite>): Int {
            val written = delegate.writeResolvedPlaces(writes)
            writeCalls += 1
            writtenUris += writes.map { it.mediaUri }
            afterWrite(writeCalls)
            return written
        }
    }

    private fun resolver(
        store: MediaIndexStore,
        chunkSize: Int = DEFAULT_PLACE_RESOLUTION_CHUNK_SIZE,
        loader: () -> Gazetteer = { syntheticGazetteer() }
    ) = MediaIndexPlaceResolver(store, gazetteerLoader = loader, chunkSize = chunkSize)

    private fun uri(index: Int) = "content://media/external/images/media/$index"

    private fun row(
        index: Int,
        lat: Double? = null,
        lon: Double? = null,
        marker: Int? = null,
        country: String? = null,
        region: String? = null,
        place: String? = null
    ) = MediaIndexEntry(
        mediaUri = uri(index),
        displayName = "photo$index.jpg",
        size = 1L,
        dateModified = 1L,
        volumeName = "external_primary",
        generationAdded = null,
        generationModified = null,
        capturedDateTakenEpochMillis = null,
        capturedLatitude = lat,
        capturedLongitude = lon,
        capturedWidth = null,
        capturedHeight = null,
        derivedCountryCode = country,
        derivedRegionCode = region,
        derivedPlaceName = place,
        derivedPlaceResolverVersion = marker
    )

    private fun header(rows: Int, version: Int = PLACE_RESOLVER_VERSION) =
        "# Test asset\n# dataset-version: $version\n# rows-written: $rows\n"

    private fun load(places: String): Gazetteer = Gazetteer.load(
        StringReader(places),
        StringReader(header(1) + "AA.01\tRegion One\n"),
        StringReader(header(2) + "AA\tAlphaland\nBB\tBetaland\n")
    )

    // Alpha has a region; Beta's admin1 is "00", so its region is null.
    private fun syntheticGazetteer(): Gazetteer =
        load(header(2) + "10.0\t20.0\tAlpha\tAA\t01\n-30.0\t100.0\tBeta\tBB\t00\n")

    private companion object {
        val NEAR_ALPHA = 10.1 to 20.1
        val NEAR_BETA = -30.2 to 100.2
        val FAR_FROM_ANYTHING = 60.0 to -60.0
    }
}
