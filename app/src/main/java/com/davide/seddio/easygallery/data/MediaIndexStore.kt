package com.davide.seddio.easygallery.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface MediaIndexStore {
    fun observeAll(): Flow<List<MediaIndexEntry>>
    fun observeByVolume(volumeName: String): Flow<List<MediaIndexEntry>>

    suspend fun getByMediaUri(mediaUri: String): MediaIndexEntry?
    suspend fun upsert(entries: List<MediaIndexEntry>)
    suspend fun deleteByMediaUri(mediaUri: String)
    suspend fun deleteByVolume(volumeName: String)
    suspend fun deleteAll()
}

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
    val derivedPlaceName: String?
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

    override suspend fun getByMediaUri(mediaUri: String): MediaIndexEntry? {
        return dao.getByMediaUri(mediaUri)?.toStoreEntry()
    }

    override suspend fun upsert(entries: List<MediaIndexEntry>) {
        if (entries.isEmpty()) return
        dao.upsert(entries.map { it.toEntity() })
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
            derivedPlaceName = derivedPlaceName
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
            derivedPlaceName = derivedPlaceName
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

    override suspend fun getByMediaUri(mediaUri: String): MediaIndexEntry? {
        return lock.withLock { rows[mediaUri] }
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
