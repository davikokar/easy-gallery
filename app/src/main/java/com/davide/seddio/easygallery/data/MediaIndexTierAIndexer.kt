package com.davide.seddio.easygallery.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.DateTimeException
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.coroutines.coroutineContext

private const val DEFAULT_INDEX_CHUNK_SIZE = 64

private val EXIF_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

private val VIDEO_DATE_OFFSET_PATTERNS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX", Locale.US),
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.US)
)

private val VIDEO_DATE_LOCAL_PATTERNS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS", Locale.US),
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)
)

data class MediaIndexTierAExtraction(
    val capturedDateTakenEpochMillis: Long?,
    val capturedLatitude: Double?,
    val capturedLongitude: Double?,
    val capturedWidth: Int?,
    val capturedHeight: Int?
) {
    companion object {
        fun empty(): MediaIndexTierAExtraction {
            return MediaIndexTierAExtraction(
                capturedDateTakenEpochMillis = null,
                capturedLatitude = null,
                capturedLongitude = null,
                capturedWidth = null,
                capturedHeight = null
            )
        }
    }
}

data class MediaIndexTierABatchResult(
    val selectedRows: Int,
    val indexedRows: Int,
    val skippedRows: Int,
    val deletedRows: Int,
    val healedRows: Int
)

interface MediaIndexTierAExtractor {
    suspend fun extract(mediaItem: MediaItem): MediaIndexTierAExtraction
}

class AndroidMediaIndexTierAExtractor(
    context: Context,
    private val contentResolver: ContentResolver = context.contentResolver,
    private val retrieverFactory: () -> MediaMetadataRetriever = { MediaMetadataRetriever() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : MediaIndexTierAExtractor {

    override suspend fun extract(mediaItem: MediaItem): MediaIndexTierAExtraction =
        withContext(Dispatchers.IO) {
            when (mediaItem.type) {
                MediaType.IMAGE,
                MediaType.GIF -> readImageTierAMetadata(mediaItem)

                MediaType.VIDEO -> readVideoTierAMetadata(mediaItem)
            }
        }

    private fun readImageTierAMetadata(mediaItem: MediaItem): MediaIndexTierAExtraction {
        val sourceUri = originalMediaUriForExifRead(mediaItem.uri)

        try {
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val width = positiveIntOrNull(exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0))
                val height = positiveIntOrNull(exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0))
                val coordinates = mediaCoordinatesFromExifLatLong(exif.latLong)
                val dateTaken = parseExifDateTakenEpochMillis(
                    dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    offsetTimeOriginal = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                    fallbackZoneId = zoneId
                )

                return MediaIndexTierAExtraction(
                    capturedDateTakenEpochMillis = dateTaken,
                    capturedLatitude = coordinates?.latitude,
                    capturedLongitude = coordinates?.longitude,
                    capturedWidth = width,
                    capturedHeight = height
                )
            }

            return MediaIndexTierAExtraction.empty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Permission denial, missing files, and corrupt headers resolve to absent metadata.
            return MediaIndexTierAExtraction.empty()
        }
    }

    private fun readVideoTierAMetadata(mediaItem: MediaItem): MediaIndexTierAExtraction {
        val retriever = retrieverFactory()

        try {
            contentResolver.openFileDescriptor(mediaItem.uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
            } ?: return MediaIndexTierAExtraction.empty()
            val dateTaken = parseVideoDateTakenEpochMillis(
                rawDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE),
                fallbackZoneId = zoneId
            )
            val coordinates = parseIso6709Location(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            )
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?.let(::positiveIntOrNull)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.let(::positiveIntOrNull)

            return MediaIndexTierAExtraction(
                capturedDateTakenEpochMillis = dateTaken,
                capturedLatitude = coordinates?.latitude,
                capturedLongitude = coordinates?.longitude,
                capturedWidth = width,
                capturedHeight = height
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Permission denial, missing files, and corrupt headers resolve to absent metadata.
            return MediaIndexTierAExtraction.empty()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release failures.
            }
        }
    }
}

/**
 * EXIF DateTimeOriginal does not carry a zone. We interpret zone-less values in the device zone
 * at extraction time so later range filtering compares absolute instants.
 */
fun parseExifDateTakenEpochMillis(
    dateTimeOriginal: String?,
    offsetTimeOriginal: String?,
    fallbackZoneId: ZoneId
): Long? {
    val rawDateTime = dateTimeOriginal?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val localDateTime = try {
        LocalDateTime.parse(rawDateTime, EXIF_DATE_TIME_FORMATTER)
    } catch (_: DateTimeParseException) {
        return null
    }

    val offset = offsetTimeOriginal
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            try {
                ZoneOffset.of(it)
            } catch (_: DateTimeException) {
                null
            }
        }

    return if (offset != null) {
        localDateTime.toInstant(offset).toEpochMilli()
    } else {
        localDateTime.atZone(fallbackZoneId).toInstant().toEpochMilli()
    }
}

fun parseVideoDateTakenEpochMillis(
    rawDate: String?,
    fallbackZoneId: ZoneId
): Long? {
    val raw = rawDate?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    raw.toLongOrNull()?.let { numeric ->
        return if (raw.length <= 10) numeric * 1_000 else numeric
    }

    try {
        return Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        // Continue with non-ISO patterns below.
    }

    VIDEO_DATE_OFFSET_PATTERNS.forEach { formatter ->
        try {
            return OffsetDateTime.parse(raw, formatter).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Try next pattern.
        }
    }

    VIDEO_DATE_LOCAL_PATTERNS.forEach { formatter ->
        try {
            return LocalDateTime.parse(raw, formatter).atZone(fallbackZoneId).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Try next pattern.
        }
    }

    return null
}

class MediaIndexTierABatchIndexer(
    private val mediaRepository: MediaRepository,
    private val mediaIndexStore: MediaIndexStore,
    private val deltaDetector: MediaIndexDeltaDetector,
    private val rowDeltaProvider: MediaStoreRowDeltaProvider,
    private val reconciler: MediaIndexReconciler,
    private val extractor: MediaIndexTierAExtractor,
    private val chunkSize: Int = DEFAULT_INDEX_CHUNK_SIZE
) {

    init {
        require(chunkSize > 0) { "chunkSize must be > 0" }
    }

    suspend fun indexDelta(sdkInt: Int): MediaIndexTierABatchResult {
        val detection = deltaDetector.detect(sdkInt)
        val plan = detection.plan
        val storedWatermarksBeforeDetect = detection.storedWatermarks

        val observedRows = buildList {
            plan.presentVolumes.forEach { volumeName ->
                addAll(rowDeltaProvider.queryObservedRows(volumeName, plan.apiBranch))
            }
        }

        val rowSelection = selectRowsForReindex(
            plan = plan,
            storedWatermarks = storedWatermarksBeforeDetect,
            observedRows = observedRows
        )
        val liveMedia = mediaRepository.getAllMedia()
        val liveByUri = indexLiveMediaByCanonicalUri(liveMedia)
        val existingByUri = buildExistingByUri(plan.presentVolumes)

        var indexedCount = 0
        var missingLiveRows = 0
        var firstMissingLiveUri: String? = null
        val missingGenerationFloorByVolume = linkedMapOf<String, Long>()
        val missingDateFloorByVolume = linkedMapOf<String, Long>()
        val maxIndexedDateByVolume = linkedMapOf<String, Long>()
        rowSelection.rowsToReindex
            .chunked(chunkSize)
            .forEach { chunk ->
                coroutineContext.ensureActive()

                val entries = ArrayList<MediaIndexEntry>(chunk.size)
                chunk.forEach { row ->
                    coroutineContext.ensureActive()

                    if (parseMediaStoreUri(row.mediaUri) == null) {
                        throw IllegalStateException(
                            "Selected row URI is not a MediaStore URI: ${row.mediaUri}"
                        )
                    }

                    val mediaItem = liveByUri[canonicalMediaStoreUriForJoin(row.mediaUri)]
                    if (mediaItem == null) {
                        missingLiveRows += 1
                        if (firstMissingLiveUri == null) {
                            firstMissingLiveUri = row.mediaUri
                        }
                        updateMissingGenerationFloor(
                            missingGenerationFloorByVolume,
                            row.volumeName,
                            row.generationModified ?: row.generationAdded
                        )
                        updateMissingDateFloor(
                            missingDateFloorByVolume,
                            row.volumeName,
                            row.dateModifiedSeconds
                        )
                        return@forEach
                    }
                    val extracted = safeExtract(mediaItem)
                    val existing = existingEntryForObservedUri(row.mediaUri, existingByUri)

                    entries += MediaIndexEntry(
                        mediaUri = row.mediaUri,
                        displayName = mediaItem.name,
                        size = mediaItem.size,
                        dateModified = mediaItem.dateModified,
                        volumeName = row.volumeName,
                        generationAdded = row.generationAdded,
                        generationModified = row.generationModified,
                        capturedDateTakenEpochMillis = extracted.capturedDateTakenEpochMillis,
                        capturedLatitude = extracted.capturedLatitude,
                        capturedLongitude = extracted.capturedLongitude,
                        capturedWidth = extracted.capturedWidth,
                        capturedHeight = extracted.capturedHeight,
                        derivedCountryCode = existing?.derivedCountryCode,
                        derivedRegionCode = existing?.derivedRegionCode,
                        derivedPlaceName = existing?.derivedPlaceName
                    )
                    maxIndexedDateByVolume[row.volumeName] = maxOf(
                        maxIndexedDateByVolume[row.volumeName] ?: Long.MIN_VALUE,
                        row.dateModifiedSeconds
                    )
                }

                if (entries.isNotEmpty()) {
                    mediaIndexStore.upsert(entries)
                    indexedCount += entries.size
                }
            }

        if (
            rowSelection.rowsToReindex.isNotEmpty() &&
                missingLiveRows == rowSelection.rowsToReindex.size
        ) {
            throw IllegalStateException(
                "All selected rows were missing from live media; " +
                    "likely broken row/live join. Example URI: ${firstMissingLiveUri.orEmpty()}"
            )
        }

        var deletedCount = 0
        var healedCount = 0

        plan.presentVolumes.forEach { volumeName ->
            coroutineContext.ensureActive()
            val result = reconciler.reconcileVolume(volumeName, liveMedia, plan.presentVolumes)
            deletedCount += result.deletedMediaUris.size
            healedCount += result.healedRewrites.size
        }

        val clampedWatermarks = clampPersistedWatermarksForSkippedRows(
            plan.nextWatermarks,
            missingGenerationFloorByVolume,
            missingDateFloorByVolume
        )
        deltaDetector.persistWatermarks(clampedWatermarks)

        if (plan.apiBranch != MediaStoreDeltaApiBranch.API_30_PLUS) {
            maxIndexedDateByVolume.forEach { (volumeName, maxDateModified) ->
                deltaDetector.updateDateModifiedWatermark(
                    volumeName,
                    clampValueStrictlyBelowMissingFloor(
                        maxDateModified,
                        missingDateFloorByVolume[volumeName]
                    )
                )
            }
        }

        return MediaIndexTierABatchResult(
            selectedRows = rowSelection.rowsToReindex.size,
            indexedRows = indexedCount,
            skippedRows = missingLiveRows,
            deletedRows = deletedCount,
            healedRows = healedCount
        )
    }

    private suspend fun safeExtract(mediaItem: MediaItem): MediaIndexTierAExtraction {
        return try {
            extractor.extract(mediaItem)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MediaIndexTierAExtraction.empty()
        }
    }

    private suspend fun buildExistingByUri(presentVolumes: Set<String>): Map<String, MediaIndexEntry> {
        if (presentVolumes.isEmpty()) return emptyMap()

        val map = LinkedHashMap<String, MediaIndexEntry>()
        presentVolumes.forEach { volumeName ->
            mediaIndexStore.listByVolume(volumeName).forEach { entry ->
                map.putIfAbsent(canonicalMediaStoreUriForJoin(entry.mediaUri), entry)
            }
        }
        return map
    }
}

private fun positiveIntOrNull(value: Int): Int? {
    return if (value > 0) value else null
}

private fun indexLiveMediaByCanonicalUri(liveMedia: List<MediaItem>): Map<String, MediaItem> {
    val liveByUri = LinkedHashMap<String, MediaItem>(liveMedia.size)
    liveMedia.forEach { item ->
        val canonicalUri = canonicalMediaStoreUriForJoin(item.uri.toString())
        liveByUri.putIfAbsent(canonicalUri, item)
    }
    return liveByUri
}

private fun existingEntryForObservedUri(
    observedUri: String,
    existingByUri: Map<String, MediaIndexEntry>
): MediaIndexEntry? {
    return existingByUri[canonicalMediaStoreUriForJoin(observedUri)]
}

private fun updateMissingGenerationFloor(
    floorsByVolume: MutableMap<String, Long>,
    volumeName: String,
    generation: Long?
) {
    if (generation == null) return
    floorsByVolume[volumeName] = minOf(
        floorsByVolume[volumeName] ?: Long.MAX_VALUE,
        generation
    )
}

private fun updateMissingDateFloor(
    floorsByVolume: MutableMap<String, Long>,
    volumeName: String,
    dateModifiedSeconds: Long
) {
    floorsByVolume[volumeName] = minOf(
        floorsByVolume[volumeName] ?: Long.MAX_VALUE,
        dateModifiedSeconds
    )
}

private fun clampPersistedWatermarksForSkippedRows(
    nextWatermarks: Map<String, MediaIndexVolumeWatermark>,
    missingGenerationFloorByVolume: Map<String, Long>,
    missingDateFloorByVolume: Map<String, Long>
): Map<String, MediaIndexVolumeWatermark> {
    if (missingGenerationFloorByVolume.isEmpty() && missingDateFloorByVolume.isEmpty()) {
        return nextWatermarks
    }

    return nextWatermarks.mapValues { (volumeName, watermark) ->
        val clampedGeneration = clampValueStrictlyBelowMissingFloor(
            watermark.generation,
            missingGenerationFloorByVolume[volumeName]
        )
        val clampedDate = clampValueStrictlyBelowMissingFloor(
            watermark.dateModifiedWatermarkSeconds,
            missingDateFloorByVolume[volumeName]
        )

        if (
            clampedGeneration == watermark.generation &&
                clampedDate == watermark.dateModifiedWatermarkSeconds
        ) {
            watermark
        } else {
            watermark.copy(
                generation = clampedGeneration,
                dateModifiedWatermarkSeconds = clampedDate
            )
        }
    }
}

private fun clampValueStrictlyBelowMissingFloor(
    currentValue: Long,
    missingFloor: Long?
): Long {
    val floor = missingFloor ?: return currentValue
    val maxAllowed = if (floor == Long.MIN_VALUE) Long.MIN_VALUE else floor - 1L
    return minOf(currentValue, maxAllowed)
}

private fun clampValueStrictlyBelowMissingFloor(
    currentValue: Long?,
    missingFloor: Long?
): Long? {
    val floor = missingFloor ?: return currentValue
    val maxAllowed = if (floor == Long.MIN_VALUE) Long.MIN_VALUE else floor - 1L
    return if (currentValue == null) {
        null
    } else {
        minOf(currentValue, maxAllowed)
    }
}