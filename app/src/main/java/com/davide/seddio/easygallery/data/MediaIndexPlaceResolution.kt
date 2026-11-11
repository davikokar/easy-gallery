package com.davide.seddio.easygallery.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

// Pure Kotlin, no android.* imports, so it runs on a plain JVM (ADR-0018 Amendment 4).

/** Tag of the one log line a resolve pass emits; filter on it to read M2 and M4 (ADR-0019 Amendment 1). */
const val PLACE_RESOLUTION_LOG_TAG = "MediaIndexPlaceResolve"

/** Rows per read and per write transaction; each commit re-evaluates every rule album. M2 on the device confirms the figure. */
const val DEFAULT_PLACE_RESOLUTION_CHUNK_SIZE = 2_000

/** [skippedRows] are rows that changed or vanished between read and write; they stay pending for the next run. */
data class MediaIndexPlaceResolutionResult(
    val selectedRows: Int,
    val writtenRows: Int,
    val resolvedWithPlaceRows: Int,
    val outOfCapRows: Int,
    val skippedRows: Int,
    val chunks: Int,
    val elapsedMillis: Long,
    /** Null when no row was pending, so the gazetteer was never loaded. */
    val gazetteerLoadMillis: Long?,
    val gazetteerRetainedBytes: Long?
) {
    /** M2: selected rows per second, the gazetteer load excluded; null when the pass was too short to time. */
    val rowsPerSecond: Long?
        get() {
            val resolveMillis = elapsedMillis - (gazetteerLoadMillis ?: 0L)
            return if (resolveMillis > 0L) selectedRows * 1_000L / resolveMillis else null
        }

    fun toLogLine(): String =
        "pass selected=$selectedRows written=$writtenRows withPlace=$resolvedWithPlaceRows " +
            "outOfCap=$outOfCapRows skipped=$skippedRows chunks=$chunks elapsedMs=$elapsedMillis " +
            "rowsPerSec=${rowsPerSecond ?: "n/a"} gazetteerLoadMs=${gazetteerLoadMillis ?: "n/a"} " +
            "gazetteerBytes=${gazetteerRetainedBytes ?: "n/a"}"
}

/** Fills the derived place columns of rows that have coordinates and no current resolver marker (ADR-0018 Amendment 4). */
class MediaIndexPlaceResolver(
    private val store: MediaIndexStore,
    private val gazetteerLoader: () -> Gazetteer,
    private val chunkSize: Int = DEFAULT_PLACE_RESOLUTION_CHUNK_SIZE,
    private val nanoClock: () -> Long = System::nanoTime
) {

    init {
        require(chunkSize > 0) { "chunkSize must be > 0" }
    }

    /** Pages by URI cursor so a skipped row is not re-read forever; a gazetteer load failure propagates before any write. */
    suspend fun resolvePending(): MediaIndexPlaceResolutionResult {
        val startedNanos = nanoClock()
        var gazetteer: Gazetteer? = null
        var loadMillis: Long? = null
        var cursor: String? = null
        var selected = 0
        var written = 0
        var withPlace = 0
        var chunks = 0

        while (true) {
            coroutineContext.ensureActive()
            val page = store.listPendingPlaceResolution(PLACE_RESOLVER_VERSION, cursor, chunkSize)
            if (page.isEmpty()) break

            var loaded = gazetteer
            if (loaded == null) {
                val loadStartedNanos = nanoClock()
                loaded = withContext(Dispatchers.IO) { gazetteerLoader() }
                loadMillis = nanosToMillis(nanoClock() - loadStartedNanos)
                gazetteer = loaded
            }

            val writes = page.map { row -> resolvedWriteFor(loaded, row) }
            written += store.writeResolvedPlaces(writes)
            withPlace += writes.count { it.placeName != null }
            selected += page.size
            chunks += 1
            cursor = page.last().mediaUri

            // A short page means nothing further was pending when it was read.
            if (page.size < chunkSize) break
        }

        return MediaIndexPlaceResolutionResult(
            selectedRows = selected,
            writtenRows = written,
            resolvedWithPlaceRows = withPlace,
            outOfCapRows = selected - withPlace,
            skippedRows = selected - written,
            chunks = chunks,
            elapsedMillis = nanosToMillis(nanoClock() - startedNanos),
            gazetteerLoadMillis = loadMillis,
            gazetteerRetainedBytes = gazetteer?.approxRetainedBytes
        )
    }

    // Beyond the cap, and for coordinates the gazetteer rejects, all three values are null; the marker is still written.
    private fun resolvedWriteFor(gazetteer: Gazetteer, row: MediaIndexEntry): ResolvedPlaceWrite {
        val latitude = checkNotNull(row.capturedLatitude) { "Pending row has no latitude: ${row.mediaUri}" }
        val longitude = checkNotNull(row.capturedLongitude) { "Pending row has no longitude: ${row.mediaUri}" }
        val match = gazetteer.nearest(latitude, longitude)
        return ResolvedPlaceWrite(
            mediaUri = row.mediaUri,
            latitude = latitude,
            longitude = longitude,
            countryCode = match?.countryCode,
            regionCode = match?.regionCode,
            placeName = match?.placeName,
            resolverVersion = PLACE_RESOLVER_VERSION
        )
    }

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000L
}
