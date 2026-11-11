package com.davide.seddio.easygallery.data

enum class MediaStoreDeltaApiBranch {
    API_30_PLUS,
    API_29,
    API_28
}

private const val API_LEVEL_Q = 29
private const val API_LEVEL_R = 30

data class ObservedMediaStoreVolume(
    val volumeName: String,
    val version: String,
    val generation: Long?
)

data class MediaStoreVolumeSnapshot(
    val apiBranch: MediaStoreDeltaApiBranch,
    val observedVolumes: List<ObservedMediaStoreVolume>,
    val presentVolumes: Set<String>,
    val knownButUnavailableVolumes: Set<String>
)

data class MediaIndexDeltaPlan(
    val apiBranch: MediaStoreDeltaApiBranch,
    val volumesToResync: Set<String>,
    val presentVolumes: Set<String>,
    val knownButUnavailableVolumes: Set<String>,
    val nextWatermarks: Map<String, MediaIndexVolumeWatermark>
)

/**
 * Row-level observation used to select incremental candidates from a live MediaStore query.
 */
data class MediaStoreObservedRowDelta(
    val mediaUri: String,
    val volumeName: String,
    val generationAdded: Long?,
    val generationModified: Long?,
    val dateModifiedSeconds: Long
)

data class MediaIndexRowDeltaSelection(
    val rowsToReindex: List<MediaStoreObservedRowDelta>,
    val maxDateModifiedWatermarkByVolume: Map<String, Long>
)

data class MediaIndexDeltaDetection(
    val plan: MediaIndexDeltaPlan,
    val storedWatermarks: Map<String, MediaIndexVolumeWatermark>
)

interface MediaStoreVolumeVersionProvider {
    fun versionForVolume(volumeName: String): String?
}

object UnknownMediaStoreVolumeVersionProvider : MediaStoreVolumeVersionProvider {
    override fun versionForVolume(volumeName: String): String? = null
}

fun resolveMediaStoreDeltaApiBranch(sdkInt: Int): MediaStoreDeltaApiBranch {
    return when {
        sdkInt >= API_LEVEL_R -> MediaStoreDeltaApiBranch.API_30_PLUS
        sdkInt >= API_LEVEL_Q -> MediaStoreDeltaApiBranch.API_29
        else -> MediaStoreDeltaApiBranch.API_28
    }
}

fun planMediaIndexDelta(
    snapshot: MediaStoreVolumeSnapshot,
    storedWatermarks: Map<String, MediaIndexVolumeWatermark>
): MediaIndexDeltaPlan {
    val volumesToResync = linkedSetOf<String>()
    val next = storedWatermarks.toMutableMap()

    snapshot.observedVolumes.forEach { observed ->
        val previous = storedWatermarks[observed.volumeName]
        val watermark = MediaIndexVolumeWatermark(
            version = observed.version,
            generation = observed.generation,
            dateModifiedWatermarkSeconds = previous?.dateModifiedWatermarkSeconds
        )

        if (previous == null || previous.version != observed.version) {
            volumesToResync += observed.volumeName
        }

        next[observed.volumeName] = watermark
    }

    return MediaIndexDeltaPlan(
        apiBranch = snapshot.apiBranch,
        volumesToResync = volumesToResync,
        presentVolumes = snapshot.presentVolumes,
        knownButUnavailableVolumes = snapshot.knownButUnavailableVolumes,
        nextWatermarks = next
    )
}

/**
 * Selects specific rows to (re)index for the current delta pass.
 *
 * - Volumes selected for full resync always contribute all rows.
 * - API 30+ narrows remaining rows by GENERATION_* values.
 * - API 28-29 narrows remaining rows by DATE_MODIFIED watermark.
 */
fun selectRowsForReindex(
    plan: MediaIndexDeltaPlan,
    storedWatermarks: Map<String, MediaIndexVolumeWatermark>,
    observedRows: List<MediaStoreObservedRowDelta>
): MediaIndexRowDeltaSelection {
    val selected = ArrayList<MediaStoreObservedRowDelta>(observedRows.size)
    val maxDateByVolume = linkedMapOf<String, Long>()

    observedRows.forEach { row ->
        val previous = storedWatermarks[row.volumeName]
        maxDateByVolume[row.volumeName] =
            maxOf(maxDateByVolume[row.volumeName] ?: Long.MIN_VALUE, row.dateModifiedSeconds)

        if (row.volumeName in plan.volumesToResync) {
            selected += row
            return@forEach
        }

        val shouldSelect = when (plan.apiBranch) {
            MediaStoreDeltaApiBranch.API_30_PLUS -> {
                val previousGeneration = previous?.generation
                if (previousGeneration == null) {
                    true
                } else {
                    val modified = row.generationModified ?: row.generationAdded
                    modified != null && modified > previousGeneration
                }
            }

            MediaStoreDeltaApiBranch.API_29,
            MediaStoreDeltaApiBranch.API_28 -> {
                shouldResyncByDateModified(
                    dateModifiedSeconds = row.dateModifiedSeconds,
                    dateModifiedWatermarkSeconds = previous?.dateModifiedWatermarkSeconds
                )
            }
        }

        if (shouldSelect) {
            selected += row
        }
    }

    return MediaIndexRowDeltaSelection(
        rowsToReindex = selected,
        maxDateModifiedWatermarkByVolume = maxDateByVolume
    )
}

/**
 * API 28-29 fallback when generation numbers are unavailable.
 *
 * This fallback is known to be imperfect because `DATE_MODIFIED` can move for reasons unrelated to
 * semantic media changes (clock drift, `setLastModified()`), and can therefore over/under-select.
 */
fun shouldResyncByDateModified(
    dateModifiedSeconds: Long,
    dateModifiedWatermarkSeconds: Long?
): Boolean {
    val watermark = dateModifiedWatermarkSeconds ?: return true
    return dateModifiedSeconds > watermark
}

class MediaIndexDeltaDetector(
    private val watermarkStore: MediaIndexWatermarkStore,
    private val snapshotProvider: MediaStoreVolumeSnapshotProvider,
    private val versionProvider: MediaStoreVolumeVersionProvider =
        UnknownMediaStoreVolumeVersionProvider
) {

    fun detect(sdkInt: Int): MediaIndexDeltaDetection {
        val snapshot = snapshotProvider.snapshot(sdkInt)
        val stored = watermarkStore.readAll()
        return MediaIndexDeltaDetection(
            plan = planMediaIndexDelta(snapshot, stored),
            storedWatermarks = stored
        )
    }

    fun persistWatermarks(watermarks: Map<String, MediaIndexVolumeWatermark>) {
        watermarks.forEach { (volumeName, watermark) ->
            watermarkStore.write(volumeName, watermark)
        }
    }

    fun updateDateModifiedWatermark(
        volumeName: String,
        dateModifiedWatermarkSeconds: Long
    ) {
        val current = watermarkStore.read(volumeName)
        if (current == null) {
            val version = versionProvider.versionForVolume(volumeName)
            watermarkStore.write(
                volumeName,
                MediaIndexVolumeWatermark(
                    version = version,
                    generation = null,
                    dateModifiedWatermarkSeconds = dateModifiedWatermarkSeconds
                )
            )
            return
        }

        watermarkStore.write(
            volumeName,
            current.copy(dateModifiedWatermarkSeconds = dateModifiedWatermarkSeconds)
        )
    }
}
