package com.davide.seddio.easygallery.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MediaIndexStore {
    fun observeAll(): Flow<List<MediaIndexEntry>>
    fun observeByVolume(volumeName: String): Flow<List<MediaIndexEntry>>
    fun observeDateTakenProjection(): Flow<Map<String, Long?>>
    fun observeDateTakenCounts(): Flow<DateTakenCounts>

    /**
     * Every row, unfiltered and with stored URIs, ordered by URI: what a rule evaluation reads from the index
     * (ADR-0017 Amendment 2). Live-media filtering is the caller's join (ADR-0020). Re-emits on any index change.
     */
    fun observeEvaluationProjection(): Flow<List<MediaIndexEvaluationProjection>>

    suspend fun listByVolume(volumeName: String): List<MediaIndexEntry>
    suspend fun getByMediaUri(mediaUri: String): MediaIndexEntry?
    suspend fun upsert(entries: List<MediaIndexEntry>)

    /**
     * Rows with coordinates whose resolver marker is null or not [currentResolverVersion], ordered by URI,
     * strictly after [afterMediaUri] (null starts at the beginning). Page by passing the last URI back,
     * never by repeating until empty: a skipped write leaves its row pending.
     */
    suspend fun listPendingPlaceResolution(
        currentResolverVersion: Int,
        afterMediaUri: String?,
        limit: Int
    ): List<MediaIndexEntry>

    /** One transaction; returns how many writes matched a row still holding the coordinates they were computed from. */
    suspend fun writeResolvedPlaces(writes: List<ResolvedPlaceWrite>): Int

    suspend fun deleteByMediaUri(mediaUri: String)
    suspend fun deleteByVolume(volumeName: String)
    suspend fun deleteAll()
}

data class DateTakenCounts(
    val withDate: Int,
    val withoutDate: Int
)

/**
 * One index row as a rule evaluation sees it. The derived place values are kept while the row is pending, so
 * they are only current when [derivedPlaceResolverVersion] matches the resolver's version; a null marker is
 * pending, and a current marker with null values is a coordinate resolved beyond the cap, which is not the
 * same as [hasCoordinates] being false.
 */
data class MediaIndexEvaluationProjection(
    val identity: MediaIdentity,
    val capturedDateTakenEpochMillis: Long?,
    val hasCoordinates: Boolean,
    val derivedCountryCode: String?,
    val derivedRegionCode: String?,
    val derivedPlaceName: String?,
    val derivedPlaceResolverVersion: Int?
)

/** A resolver's answer for one row; [latitude] and [longitude] are the stored coordinates it was computed from. */
data class ResolvedPlaceWrite(
    val mediaUri: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String?,
    val regionCode: String?,
    val placeName: String?,
    val resolverVersion: Int
)

data class MediaIndexEntry(
    val mediaUri: String,
    val displayName: String,
    val size: Long,
    val dateModified: Long,
    val volumeName: String,
    val generationAdded: Long?,
    val generationModified: Long?,
    val capturedDateTakenEpochMillis: Long?,
    val capturedLatitude: Double?,
    val capturedLongitude: Double?,
    val capturedWidth: Int?,
    val capturedHeight: Int?,
    val derivedCountryCode: String?,
    val derivedRegionCode: String?,
    val derivedPlaceName: String?,
    // Null forces re-resolution, so it is the safe value for any writer that does not know better.
    val derivedPlaceResolverVersion: Int? = null
) {
    fun toMediaIdentity(): MediaIdentity {
        return mediaIdentityOf(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified
        )
    }
}

class RoomMediaIndexStore(
    private val database: MediaIndexDatabase,
    private val dao: MediaIndexDao = database.mediaIndexDao()
) : MediaIndexStore {

    override fun observeAll(): Flow<List<MediaIndexEntry>> {
        return dao.observeAll().map { rows -> rows.map { it.toStoreEntry() } }
    }

    override fun observeByVolume(volumeName: String): Flow<List<MediaIndexEntry>> {
        return dao.observeByVolume(volumeName).map { rows -> rows.map { it.toStoreEntry() } }
    }

    override fun observeDateTakenProjection(): Flow<Map<String, Long?>> {
        return dao.observeDateTakenProjection().map { rows ->
            buildMap(rows.size) {
                rows.forEach { row ->
                    put(canonicalMediaStoreUriForJoin(row.mediaUri), row.capturedDateTakenEpochMillis)
                }
            }
        }
    }

    override fun observeDateTakenCounts(): Flow<DateTakenCounts> {
        return observeDateTakenProjection().map { projection ->
            DateTakenCounts(
                withDate = projection.count { it.value != null },
                withoutDate = projection.count { it.value == null }
            )
        }
    }

    override fun observeEvaluationProjection(): Flow<List<MediaIndexEvaluationProjection>> {
        return dao.observeEvaluationProjection().map { rows ->
            rows.map { row ->
                MediaIndexEvaluationProjection(
                    identity = mediaIdentityOf(
                        mediaUri = row.mediaUri,
                        displayName = row.displayName,
                        size = row.size,
                        dateModified = row.dateModified
                    ),
                    capturedDateTakenEpochMillis = row.capturedDateTakenEpochMillis,
                    hasCoordinates = row.hasCoordinates,
                    derivedCountryCode = row.derivedCountryCode,
                    derivedRegionCode = row.derivedRegionCode,
                    derivedPlaceName = row.derivedPlaceName,
                    derivedPlaceResolverVersion = row.derivedPlaceResolverVersion
                )
            }
        }
    }

    override suspend fun getByMediaUri(mediaUri: String): MediaIndexEntry? {
        return dao.getByMediaUri(mediaUri)?.toStoreEntry()
    }

    override suspend fun listByVolume(volumeName: String): List<MediaIndexEntry> {
        return dao.listByVolume(volumeName).map { it.toStoreEntry() }
    }

    override suspend fun upsert(entries: List<MediaIndexEntry>) {
        if (entries.isEmpty()) return
        dao.upsert(entries.map { it.toEntity() })
    }

    override suspend fun listPendingPlaceResolution(
        currentResolverVersion: Int,
        afterMediaUri: String?,
        limit: Int
    ): List<MediaIndexEntry> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return dao.listPendingPlaceResolution(currentResolverVersion, afterMediaUri, limit)
            .map { it.toStoreEntry() }
    }

    override suspend fun writeResolvedPlaces(writes: List<ResolvedPlaceWrite>): Int {
        if (writes.isEmpty()) return 0
        return database.withTransaction {
            writes.sumOf { write ->
                dao.updateResolvedPlace(
                    mediaUri = write.mediaUri,
                    latitude = write.latitude,
                    longitude = write.longitude,
                    countryCode = write.countryCode,
                    regionCode = write.regionCode,
                    placeName = write.placeName,
                    resolverVersion = write.resolverVersion
                )
            }
        }
    }

    override suspend fun deleteByMediaUri(mediaUri: String) {
        dao.deleteByMediaUri(mediaUri)
    }

    override suspend fun deleteByVolume(volumeName: String) {
        dao.deleteByVolume(volumeName)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun MediaIndexEntity.toStoreEntry(): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            volumeName = volumeName,
            generationAdded = generationAdded,
            generationModified = generationModified,
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

    private fun MediaIndexEntry.toEntity(): MediaIndexEntity {
        return MediaIndexEntity(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            volumeName = volumeName,
            generationAdded = generationAdded,
            generationModified = generationModified,
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

class InMemoryMediaIndexStore : MediaIndexStore {
    private val lock = Mutex()
    private val rows = linkedMapOf<String, MediaIndexEntry>()
    private val rowsFlow = MutableStateFlow<List<MediaIndexEntry>>(emptyList())

    override fun observeAll(): Flow<List<MediaIndexEntry>> = rowsFlow

    override fun observeByVolume(volumeName: String): Flow<List<MediaIndexEntry>> {
        return rowsFlow.map { current ->
            current.filter { it.volumeName == volumeName }
        }
    }

    override fun observeDateTakenProjection(): Flow<Map<String, Long?>> {
        return rowsFlow.map { current ->
            buildMap(current.size) {
                current.forEach { entry ->
                    put(canonicalMediaStoreUriForJoin(entry.mediaUri), entry.capturedDateTakenEpochMillis)
                }
            }
        }
    }

    override fun observeDateTakenCounts(): Flow<DateTakenCounts> {
        return observeDateTakenProjection().map { projection ->
            DateTakenCounts(
                withDate = projection.count { it.value != null },
                withoutDate = projection.count { it.value == null }
            )
        }
    }

    override fun observeEvaluationProjection(): Flow<List<MediaIndexEvaluationProjection>> {
        return rowsFlow.map { current ->
            current.map { entry ->
                MediaIndexEvaluationProjection(
                    identity = entry.toMediaIdentity(),
                    capturedDateTakenEpochMillis = entry.capturedDateTakenEpochMillis,
                    hasCoordinates = entry.capturedLatitude != null && entry.capturedLongitude != null,
                    derivedCountryCode = entry.derivedCountryCode,
                    derivedRegionCode = entry.derivedRegionCode,
                    derivedPlaceName = entry.derivedPlaceName,
                    derivedPlaceResolverVersion = entry.derivedPlaceResolverVersion
                )
            }
        }
    }

    override suspend fun getByMediaUri(mediaUri: String): MediaIndexEntry? {
        return lock.withLock { rows[mediaUri] }
    }

    override suspend fun listByVolume(volumeName: String): List<MediaIndexEntry> {
        return lock.withLock {
            rows.values
                .asSequence()
                .filter { it.volumeName == volumeName }
                .sortedBy { it.mediaUri }
                .toList()
        }
    }

    override suspend fun upsert(entries: List<MediaIndexEntry>) {
        if (entries.isEmpty()) return

        lock.withLock {
            entries.forEach { entry ->
                rows[entry.mediaUri] = entry
            }
            publishLocked()
        }
    }

    override suspend fun listPendingPlaceResolution(
        currentResolverVersion: Int,
        afterMediaUri: String?,
        limit: Int
    ): List<MediaIndexEntry> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return lock.withLock {
            rows.values
                .asSequence()
                .filter { it.capturedLatitude != null && it.capturedLongitude != null }
                .filter { it.derivedPlaceResolverVersion != currentResolverVersion }
                .filter { afterMediaUri == null || it.mediaUri > afterMediaUri }
                .sortedBy { it.mediaUri }
                .take(limit)
                .toList()
        }
    }

    override suspend fun writeResolvedPlaces(writes: List<ResolvedPlaceWrite>): Int {
        if (writes.isEmpty()) return 0
        return lock.withLock {
            var written = 0
            writes.forEach { write ->
                val row = rows[write.mediaUri] ?: return@forEach
                if (row.capturedLatitude != write.latitude || row.capturedLongitude != write.longitude) {
                    return@forEach
                }
                rows[write.mediaUri] = row.copy(
                    derivedCountryCode = write.countryCode,
                    derivedRegionCode = write.regionCode,
                    derivedPlaceName = write.placeName,
                    derivedPlaceResolverVersion = write.resolverVersion
                )
                written += 1
            }
            if (written > 0) publishLocked()
            written
        }
    }

    override suspend fun deleteByMediaUri(mediaUri: String) {
        lock.withLock {
            val removed = rows.remove(mediaUri)
            if (removed != null) publishLocked()
        }
    }

    override suspend fun deleteByVolume(volumeName: String) {
        lock.withLock {
            val removed = rows.entries.removeIf { it.value.volumeName == volumeName }
            if (removed) publishLocked()
        }
    }

    override suspend fun deleteAll() {
        lock.withLock {
            if (rows.isEmpty()) return@withLock
            rows.clear()
            publishLocked()
        }
    }

    private fun publishLocked() {
        rowsFlow.value = rows.values.sortedBy { it.mediaUri }
    }
}
