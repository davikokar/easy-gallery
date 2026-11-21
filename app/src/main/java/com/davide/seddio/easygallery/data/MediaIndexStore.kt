package com.davide.seddio.easygallery.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    /**
     * Per index row with a face state at [pipelineVersion]: the row's identity, the outcome and the faces at that version
     * with box and group, never an embedding (plan 3.5). One emission is one consistent snapshot, and it re-emits on any
     * change to the index rows, states or faces. Separate from [observeEvaluationProjection] so that a region or time
     * album does not re-evaluate on every face write.
     */
    fun observeFaceEvaluationProjection(pipelineVersion: Int): Flow<FaceEvaluationSnapshot>

    suspend fun listByVolume(volumeName: String): List<MediaIndexEntry>
    suspend fun getByMediaUri(mediaUri: String): MediaIndexEntry?

    /**
     * One transaction. A written URI keeps its Tier C state and faces, stamps and ids included, while the stored row
     * has the same healing triple (ADR-0018 Amendment 6): a re-extraction that only rewrote Tier A columns, a favourite
     * toggle for one, is not a changed file. A different triple, or no stored row, drops them and the image is pending
     * again. Tier A columns are written either way.
     */
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

    /**
     * One transaction. Each write applies only while the index row still has the URI and healing triple it was
     * computed from, replacing that image's state, faces and attempt; returns how many applied. [faceDataEnabled] is
     * read inside the transaction (ADR-0018 Amendment 7 §4): off, nothing is written and 0 is returned. Required, never
     * defaulted, so no writer can skip the backstop.
     */
    suspend fun writeFaceIndexResults(writes: List<FaceIndexWrite>, faceDataEnabled: () -> Boolean): Int

    /**
     * Images the pass has to do, ordered by URI strictly after [afterMediaUri] (null starts at the beginning): index
     * rows of the images collection, picked by the URI's `/images/media/` path, with no state at [pipelineVersion].
     * A video's row never appears; telling a GIF from an image is the caller's, against live media. Page by passing the
     * last URI back.
     */
    suspend fun listPendingFaceImages(pipelineVersion: Int, afterMediaUri: String?, limit: Int): List<PendingFaceImage>

    /**
     * Counts one try of [identity]'s image under [pipelineVersion] and returns its number, in its own transaction:
     * 1 when none was recorded or the recorded one is for another version. Null, and nothing written, when the index
     * row is gone or no longer has [identity], or when [faceDataEnabled] reads off (ADR-0018 Amendment 7 §1, §4).
     */
    suspend fun recordFaceIndexAttempt(identity: MediaIdentity, pipelineVersion: Int, faceDataEnabled: () -> Boolean): Int?

    /** Forgets the recorded tries of one image, for a stop the image is not to blame for. Nothing recorded: nothing happens. */
    suspend fun clearFaceIndexAttempt(mediaUri: String)

    suspend fun getFaceIndexState(mediaUri: String): FaceIndexState?
    suspend fun listFaces(mediaUri: String): List<StoredFace>
    suspend fun hasFaceData(): Boolean

    /**
     * Faces stored at [pipelineVersion] and no other, ascending by face id, strictly after [afterFaceId] (null starts at
     * the beginning); ids and embeddings only. Page by passing the last id back.
     */
    suspend fun listFacesForGrouping(pipelineVersion: Int, afterFaceId: Long?, limit: Int): List<FaceGroupingInput>

    /**
     * Whether grouping has work (plan 3.4, *When grouping is needed*): some face at [pipelineVersion] has no grouping
     * version or one other than [groupingVersion], or has a group id naming no face at [pipelineVersion], which is a
     * representative that was deleted or dropped by a changed healing triple. A face deleted that was not a
     * representative does not count. Faces at another pipeline version are ignored.
     */
    suspend fun isFaceGroupingNeeded(pipelineVersion: Int, groupingVersion: Int): Boolean

    /**
     * One transaction. Each group's faces get its [FaceGroup.groupId] and [groupingVersion], by face id, only while
     * they still exist at [pipelineVersion]: a face deleted since the read is skipped, a face added since keeps its null
     * version. Nothing else is touched. [faceDataEnabled] is read inside the transaction (ADR-0018 Amendment 7 §4):
     * off, nothing is written and 0 is returned. Returns how many faces were written.
     */
    suspend fun writeFaceGroups(
        groups: List<FaceGroup>,
        pipelineVersion: Int,
        groupingVersion: Int,
        faceDataEnabled: () -> Boolean
    ): Int

    /**
     * The groups the People picker offers: faces at [pipelineVersion] and [groupingVersion] whose group's representative
     * face exists at [pipelineVersion], with at least [minimumImages] distinct images among the index rows that still
     * exist. Sorted by distinct images, most first; then the newest capture date among them, an image with none counting
     * as the oldest and a group with no date last; then [FaceGroupSummary.groupId] ascending (decision 46).
     */
    suspend fun listFaceGroups(pipelineVersion: Int, groupingVersion: Int, minimumImages: Int): List<FaceGroupSummary>

    /**
     * One transaction: the row at [fromMediaUri] becomes [healed]. Its Tier C rows are re-keyed and kept, and a row
     * already at the target loses its own to them, only while [healed]'s healing triple equals the stored source
     * row's: a heal is a move, not a change of content. Otherwise neither the source's nor the target's Tier C rows
     * survive, whether the URI is kept or moved (ADR-0018 Amendment 6).
     */
    suspend fun healMediaUri(fromMediaUri: String, healed: MediaIndexEntry)

    /**
     * Deletes every Tier C row, attempts included, and nothing else (ADR-0018 Amendment 5). The rows are deleted and [FaceDataDeletion.rowsDeleted]
     * reported whatever the database's journal mode; [FaceDataDeletion.scrubbed] says whether no deleted bytes remain in
     * its files (Amendment 6). It is false when a reader held the old snapshot of a write-ahead log, or when the journal
     * mode keeps page copies: `PERSIST`, `DELETE` or `TRUNCATE` under `exclusive` locking, or a mode not recognised.
     * Idempotent. The caller repeats it at each start while face data is off, knowing that under `PERSIST` a repeat
     * reports false again. A `secure_delete` that does not read back throws, and nothing is deleted.
     */
    suspend fun deleteAllFaceData(): FaceDataDeletion
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

/**
 * The one rule for whether a written row is still the file its Tier C rows were computed from (ADR-0018
 * Amendment 6): the stored row exists and its healing triple equals the incoming one. The URI is not compared,
 * so a heal that moves a row passes while one that changes the file does not.
 */
private fun keepsTierC(stored: MediaIdentity?, incoming: MediaIdentity): Boolean {
    return stored != null && stored.healingKey == incoming.healingKey
}

// Ids per UPDATE: with the three other variables, well under API 28's limit of 999.
private const val FACE_GROUP_UPDATE_CHUNK = 500

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

    override fun observeFaceEvaluationProjection(pipelineVersion: Int): Flow<FaceEvaluationSnapshot> {
        return dao.observeFaceEvaluationRows(pipelineVersion).map { rows ->
            val images = ArrayList<FaceEvaluationImage>()
            var current: MediaIndexFaceEvaluationRowEntity? = null
            var currentFaces = ArrayList<FaceEvaluationFace>()
            fun close() {
                val head = current ?: return
                images += FaceEvaluationImage(
                    identity = mediaIdentityOf(head.mediaUri, head.displayName, head.size, head.dateModified),
                    outcome = head.outcome,
                    faces = currentFaces
                )
            }
            for (row in rows) {
                if (current?.mediaUri != row.mediaUri) {
                    close()
                    current = row
                    currentFaces = ArrayList()
                }
                if (row.faceId != null) {
                    currentFaces += FaceEvaluationFace(
                        id = row.faceId,
                        box = FaceBox(row.boxLeft!!, row.boxTop!!, row.boxRight!!, row.boxBottom!!),
                        groupId = row.groupId,
                        groupingVersion = row.groupingVersion
                    )
                }
            }
            close()
            FaceEvaluationSnapshot(images)
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
        database.withTransaction {
            // Per URI: one IN list would hit API 28's limit of 999 variables.
            entries.forEach { entry ->
                val stored = dao.getByMediaUri(entry.mediaUri)?.toMediaIdentity()
                if (!keepsTierC(stored, entry.toMediaIdentity())) dropFaceData(entry.mediaUri)
            }
            dao.upsert(entries.map { it.toEntity() })
        }
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
        database.withTransaction {
            dropFaceData(mediaUri)
            dao.deleteByMediaUri(mediaUri)
        }
    }

    override suspend fun deleteByVolume(volumeName: String) {
        database.withTransaction {
            dao.deleteFaceStatesByVolume(volumeName)
            dao.deleteFacesByVolume(volumeName)
            dao.deleteFaceAttemptsByVolume(volumeName)
            dao.deleteByVolume(volumeName)
        }
    }

    override suspend fun deleteAll() {
        database.withTransaction {
            dao.deleteAllFaceStates()
            dao.deleteAllFaces()
            dao.deleteAllFaceAttempts()
            dao.deleteAll()
        }
    }

    override suspend fun writeFaceIndexResults(writes: List<FaceIndexWrite>, faceDataEnabled: () -> Boolean): Int {
        if (writes.isEmpty()) return 0
        return database.withTransaction {
            if (!faceDataEnabled()) return@withTransaction 0
            var applied = 0
            writes.forEach { write ->
                val row = dao.getByMediaUri(write.identity.mediaUri) ?: return@forEach
                if (row.toMediaIdentity() != write.identity) return@forEach

                dropFaceData(row.mediaUri)
                dao.upsertFaceState(MediaIndexFaceStateEntity(row.mediaUri, write.pipelineVersion, write.outcome))
                if (write.faces.isNotEmpty()) {
                    dao.insertFaces(write.faces.map { it.toEntity(row.mediaUri, write.pipelineVersion) })
                }
                applied += 1
            }
            applied
        }
    }

    override suspend fun listPendingFaceImages(
        pipelineVersion: Int,
        afterMediaUri: String?,
        limit: Int
    ): List<PendingFaceImage> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return dao.listPendingFaceImages(pipelineVersion, afterMediaUri, limit).map {
            PendingFaceImage(
                identity = mediaIdentityOf(it.mediaUri, it.displayName, it.size, it.dateModified),
                attempts = it.attempts
            )
        }
    }

    override suspend fun recordFaceIndexAttempt(
        identity: MediaIdentity,
        pipelineVersion: Int,
        faceDataEnabled: () -> Boolean
    ): Int? {
        return database.withTransaction {
            if (!faceDataEnabled()) return@withTransaction null
            val row = dao.getByMediaUri(identity.mediaUri) ?: return@withTransaction null
            if (row.toMediaIdentity() != identity) return@withTransaction null

            val recorded = dao.getFaceAttempt(row.mediaUri)
            val number = if (recorded != null && recorded.pipelineVersion == pipelineVersion) recorded.attempts + 1 else 1
            dao.upsertFaceAttempt(MediaIndexFaceAttemptEntity(row.mediaUri, pipelineVersion, number))
            number
        }
    }

    override suspend fun clearFaceIndexAttempt(mediaUri: String) {
        dao.deleteFaceAttempt(mediaUri)
    }

    override suspend fun getFaceIndexState(mediaUri: String): FaceIndexState? {
        return dao.getFaceState(mediaUri)?.let { FaceIndexState(it.mediaUri, it.pipelineVersion, it.outcome) }
    }

    override suspend fun listFaces(mediaUri: String): List<StoredFace> {
        return dao.listFaces(mediaUri).map { it.toStoredFace() }
    }

    override suspend fun hasFaceData(): Boolean = dao.hasFaceData()

    override suspend fun listFacesForGrouping(pipelineVersion: Int, afterFaceId: Long?, limit: Int): List<FaceGroupingInput> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return dao.listFacesForGrouping(pipelineVersion, afterFaceId, limit)
            .map { FaceGroupingInput(it.id, decodeFaceEmbedding(it.embedding)) }
    }

    override suspend fun isFaceGroupingNeeded(pipelineVersion: Int, groupingVersion: Int): Boolean {
        return dao.hasFacesPendingGrouping(pipelineVersion, groupingVersion)
    }

    override suspend fun writeFaceGroups(
        groups: List<FaceGroup>,
        pipelineVersion: Int,
        groupingVersion: Int,
        faceDataEnabled: () -> Boolean
    ): Int {
        if (groups.isEmpty()) return 0
        return database.withTransaction {
            if (!faceDataEnabled()) return@withTransaction 0
            var written = 0
            groups.forEach { group ->
                // Chunked: one IN list of every member could pass API 28's limit of 999 variables.
                group.memberFaceIds.chunked(FACE_GROUP_UPDATE_CHUNK).forEach { ids ->
                    written += dao.updateFaceGroup(group.groupId, groupingVersion, pipelineVersion, ids)
                }
            }
            written
        }
    }

    override suspend fun listFaceGroups(
        pipelineVersion: Int,
        groupingVersion: Int,
        minimumImages: Int
    ): List<FaceGroupSummary> {
        require(minimumImages > 0) { "minimumImages must be positive, was $minimumImages" }
        return dao.listFaceGroups(pipelineVersion, groupingVersion, minimumImages).map {
            FaceGroupSummary(
                groupId = it.groupId,
                imageCount = it.imageCount,
                newestDateTakenEpochMillis = it.newestDateTakenEpochMillis,
                representative = FaceGroupRepresentative(
                    identity = mediaIdentityOf(
                        it.representativeMediaUri,
                        it.representativeDisplayName,
                        it.representativeSize,
                        it.representativeDateModified
                    ),
                    box = FaceBox(it.boxLeft, it.boxTop, it.boxRight, it.boxBottom),
                    pipelineVersion = it.pipelineVersion
                )
            )
        }
    }

    override suspend fun healMediaUri(fromMediaUri: String, healed: MediaIndexEntry) {
        database.withTransaction {
            val keeps = keepsTierC(dao.getByMediaUri(fromMediaUri)?.toMediaIdentity(), healed.toMediaIdentity())
            if (healed.mediaUri != fromMediaUri) {
                dropFaceData(healed.mediaUri)
                if (keeps) {
                    dao.rekeyFaceState(fromMediaUri, healed.mediaUri)
                    dao.rekeyFaces(fromMediaUri, healed.mediaUri)
                    dao.rekeyFaceAttempt(fromMediaUri, healed.mediaUri)
                } else {
                    dropFaceData(fromMediaUri)
                }
                dao.deleteByMediaUri(fromMediaUri)
            } else if (!keeps) {
                dropFaceData(fromMediaUri)
            }
            dao.upsert(listOf(healed.toEntity()))
        }
    }

    override suspend fun deleteAllFaceData(): FaceDataDeletion {
        val (rowsDeleted, modes) = database.withTransaction {
            // First statement, on the connection that writes: a pooled reader's setting would prove nothing.
            val writable = database.openHelper.writableDatabase
            check(writable.enableSecureDelete()) {
                "secure_delete could not be enabled, so face data was not deleted"
            }
            val deleted = dao.deleteAllFaces() + dao.deleteAllFaceStates() + dao.deleteAllFaceAttempts()
            // The modes of the connection that deleted: what the commit did with the deleted pages depends on them.
            deleted to JournalModes(writable.journalMode(), writable.lockingMode())
        }
        val scrubbed = withContext(Dispatchers.IO) {
            // After the commit: a checkpoint cannot run inside a transaction.
            deletionIsScrubbed(modes.journalMode, modes.lockingMode) {
                database.openHelper.writableDatabase.checkpointTruncate()
            }
        }
        return FaceDataDeletion(rowsDeleted = rowsDeleted, scrubbed = scrubbed)
    }

    private class JournalModes(val journalMode: String, val lockingMode: String)

    private suspend fun dropFaceData(mediaUri: String) {
        dao.deleteFaces(mediaUri)
        dao.deleteFaceState(mediaUri)
        dao.deleteFaceAttempt(mediaUri)
    }

    private fun FaceDetection.toEntity(mediaUri: String, pipelineVersion: Int): MediaIndexFaceEntity {
        return MediaIndexFaceEntity(
            mediaUri = mediaUri,
            pipelineVersion = pipelineVersion,
            boxLeft = box.left,
            boxTop = box.top,
            boxRight = box.right,
            boxBottom = box.bottom,
            embedding = encodeFaceEmbedding(embedding),
            groupId = null,
            groupingVersion = null
        )
    }

    private fun MediaIndexFaceEntity.toStoredFace(): StoredFace {
        return StoredFace(
            id = id,
            mediaUri = mediaUri,
            pipelineVersion = pipelineVersion,
            box = FaceBox(boxLeft, boxTop, boxRight, boxBottom),
            embedding = decodeFaceEmbedding(embedding),
            groupId = groupId,
            groupingVersion = groupingVersion
        )
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

    // Tier C, kept by URI; the models are immutable, so nothing stored can be changed through a reference.
    private val faceStates = HashMap<String, FaceIndexState>()
    private val faces = HashMap<String, List<StoredFace>>()
    private val attempts = HashMap<String, RecordedAttempt>()
    private var nextFaceId = 1L

    // A copy of Tier C and of the identities of the rows it describes, replaced on every change; a new object each
    // time, so the flow re-emits like Room's does.
    private val faceView = MutableStateFlow(FaceView(emptyMap(), emptyMap(), emptyMap()))

    private class RecordedAttempt(val pipelineVersion: Int, val attempts: Int)

    private class FaceView(
        val states: Map<String, FaceIndexState>,
        val faces: Map<String, List<StoredFace>>,
        val identities: Map<String, MediaIdentity>
    )

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

    override fun observeFaceEvaluationProjection(pipelineVersion: Int): Flow<FaceEvaluationSnapshot> {
        return faceView.map { view ->
            val images = view.states.values
                .filter { it.pipelineVersion == pipelineVersion }
                .sortedBy { it.mediaUri }
                .mapNotNull { state ->
                    val identity = view.identities[state.mediaUri] ?: return@mapNotNull null
                    FaceEvaluationImage(
                        identity = identity,
                        outcome = state.outcome,
                        faces = view.faces[state.mediaUri].orEmpty()
                            .filter { it.pipelineVersion == pipelineVersion }
                            .sortedBy { it.id }
                            .map { FaceEvaluationFace(it.id, it.box, it.groupId, it.groupingVersion) }
                    )
                }
            FaceEvaluationSnapshot(images)
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
                if (!keepsTierC(rows[entry.mediaUri]?.toMediaIdentity(), entry.toMediaIdentity())) {
                    dropFaceDataLocked(entry.mediaUri)
                }
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
            dropFaceDataLocked(mediaUri)
            val removed = rows.remove(mediaUri)
            if (removed != null) publishLocked()
        }
    }

    override suspend fun deleteByVolume(volumeName: String) {
        lock.withLock {
            rows.values.filter { it.volumeName == volumeName }.forEach { dropFaceDataLocked(it.mediaUri) }
            val removed = rows.entries.removeIf { it.value.volumeName == volumeName }
            if (removed) publishLocked()
        }
    }

    override suspend fun deleteAll() {
        lock.withLock {
            faceStates.clear()
            faces.clear()
            attempts.clear()
            if (rows.isEmpty()) return@withLock publishFaceViewLocked()
            rows.clear()
            publishLocked()
        }
    }

    override suspend fun writeFaceIndexResults(writes: List<FaceIndexWrite>, faceDataEnabled: () -> Boolean): Int {
        if (writes.isEmpty()) return 0
        return lock.withLock {
            if (!faceDataEnabled()) return@withLock 0
            var applied = 0
            writes.forEach { write ->
                val row = rows[write.identity.mediaUri] ?: return@forEach
                if (row.toMediaIdentity() != write.identity) return@forEach

                dropFaceDataLocked(row.mediaUri)
                faceStates[row.mediaUri] = FaceIndexState(row.mediaUri, write.pipelineVersion, write.outcome)
                if (write.faces.isNotEmpty()) {
                    faces[row.mediaUri] = write.faces.map { detection ->
                        StoredFace(
                            id = nextFaceId++,
                            mediaUri = row.mediaUri,
                            pipelineVersion = write.pipelineVersion,
                            box = detection.box,
                            embedding = detection.embedding,
                            groupId = null,
                            groupingVersion = null
                        )
                    }
                }
                applied += 1
            }
            if (applied > 0) publishFaceViewLocked()
            applied
        }
    }

    override suspend fun getFaceIndexState(mediaUri: String): FaceIndexState? {
        return lock.withLock { faceStates[mediaUri] }
    }

    override suspend fun listPendingFaceImages(
        pipelineVersion: Int,
        afterMediaUri: String?,
        limit: Int
    ): List<PendingFaceImage> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return lock.withLock {
            rows.values
                .asSequence()
                .filter { it.mediaUri.contains("/images/media/") }
                .filter { faceStates[it.mediaUri]?.pipelineVersion != pipelineVersion }
                .filter { afterMediaUri == null || it.mediaUri > afterMediaUri }
                .sortedBy { it.mediaUri }
                .take(limit)
                .map { row ->
                    val recorded = attempts[row.mediaUri]
                    PendingFaceImage(
                        identity = row.toMediaIdentity(),
                        attempts = if (recorded?.pipelineVersion == pipelineVersion) recorded.attempts else 0
                    )
                }
                .toList()
        }
    }

    override suspend fun recordFaceIndexAttempt(
        identity: MediaIdentity,
        pipelineVersion: Int,
        faceDataEnabled: () -> Boolean
    ): Int? {
        return lock.withLock {
            if (!faceDataEnabled()) return@withLock null
            val row = rows[identity.mediaUri] ?: return@withLock null
            if (row.toMediaIdentity() != identity) return@withLock null

            val recorded = attempts[row.mediaUri]
            val number = if (recorded != null && recorded.pipelineVersion == pipelineVersion) recorded.attempts + 1 else 1
            attempts[row.mediaUri] = RecordedAttempt(pipelineVersion, number)
            number
        }
    }

    override suspend fun clearFaceIndexAttempt(mediaUri: String) {
        lock.withLock { attempts.remove(mediaUri) }
    }

    override suspend fun listFaces(mediaUri: String): List<StoredFace> {
        return lock.withLock { faces[mediaUri].orEmpty() }
    }

    override suspend fun hasFaceData(): Boolean {
        return lock.withLock { faceStates.isNotEmpty() || faces.isNotEmpty() || attempts.isNotEmpty() }
    }

    override suspend fun listFacesForGrouping(pipelineVersion: Int, afterFaceId: Long?, limit: Int): List<FaceGroupingInput> {
        require(limit > 0) { "limit must be positive, was $limit" }
        return lock.withLock {
            faces.values
                .asSequence()
                .flatten()
                .filter { it.pipelineVersion == pipelineVersion }
                .filter { afterFaceId == null || it.id > afterFaceId }
                .sortedBy { it.id }
                .take(limit)
                .map { FaceGroupingInput(it.id, it.embedding) }
                .toList()
        }
    }

    override suspend fun isFaceGroupingNeeded(pipelineVersion: Int, groupingVersion: Int): Boolean {
        return lock.withLock {
            val current = faces.values.flatten().filter { it.pipelineVersion == pipelineVersion }
            val currentIds = current.mapTo(HashSet()) { it.id }
            current.any { it.groupingVersion != groupingVersion || it.groupId == null || it.groupId !in currentIds }
        }
    }

    override suspend fun writeFaceGroups(
        groups: List<FaceGroup>,
        pipelineVersion: Int,
        groupingVersion: Int,
        faceDataEnabled: () -> Boolean
    ): Int {
        if (groups.isEmpty()) return 0
        return lock.withLock {
            if (!faceDataEnabled()) return@withLock 0
            val groupOf = HashMap<Long, Long>()
            groups.forEach { group -> group.memberFaceIds.forEach { groupOf[it] = group.groupId } }
            var written = 0
            faces.replaceAll { _, stored ->
                stored.map { face ->
                    val groupId = groupOf[face.id]
                    if (groupId == null || face.pipelineVersion != pipelineVersion) {
                        face
                    } else {
                        written += 1
                        face.copy(groupId = groupId, groupingVersion = groupingVersion)
                    }
                }
            }
            if (written > 0) publishFaceViewLocked()
            written
        }
    }

    override suspend fun listFaceGroups(
        pipelineVersion: Int,
        groupingVersion: Int,
        minimumImages: Int
    ): List<FaceGroupSummary> {
        require(minimumImages > 0) { "minimumImages must be positive, was $minimumImages" }
        return lock.withLock {
            val current = faces.values.flatten().filter { it.pipelineVersion == pipelineVersion }
            val byId = current.associateBy { it.id }
            current
                .filter { it.groupingVersion == groupingVersion && it.groupId != null && rows.containsKey(it.mediaUri) }
                .groupBy { it.groupId!! }
                .mapNotNull { (groupId, members) ->
                    val images = members.map { it.mediaUri }.distinct()
                    val representative = byId[groupId] ?: return@mapNotNull null
                    val representativeRow = rows[representative.mediaUri] ?: return@mapNotNull null
                    if (images.size < minimumImages) return@mapNotNull null
                    FaceGroupSummary(
                        groupId = groupId,
                        imageCount = images.size,
                        newestDateTakenEpochMillis = images.mapNotNull { rows[it]?.capturedDateTakenEpochMillis }.maxOrNull(),
                        representative = FaceGroupRepresentative(
                            identity = representativeRow.toMediaIdentity(),
                            box = representative.box,
                            pipelineVersion = representative.pipelineVersion
                        )
                    )
                }
                .sortedWith(
                    compareByDescending<FaceGroupSummary> { it.imageCount }
                        .thenByDescending { it.newestDateTakenEpochMillis ?: Long.MIN_VALUE }
                        .thenBy { it.groupId }
                )
        }
    }

    override suspend fun healMediaUri(fromMediaUri: String, healed: MediaIndexEntry) {
        lock.withLock {
            val keeps = keepsTierC(rows[fromMediaUri]?.toMediaIdentity(), healed.toMediaIdentity())
            if (healed.mediaUri != fromMediaUri) {
                dropFaceDataLocked(healed.mediaUri)
                if (keeps) {
                    faceStates.remove(fromMediaUri)?.let {
                        faceStates[healed.mediaUri] = it.copy(mediaUri = healed.mediaUri)
                    }
                    faces.remove(fromMediaUri)?.let { moved ->
                        faces[healed.mediaUri] = moved.map { it.copy(mediaUri = healed.mediaUri) }
                    }
                    attempts.remove(fromMediaUri)?.let { attempts[healed.mediaUri] = it }
                } else {
                    dropFaceDataLocked(fromMediaUri)
                }
                rows.remove(fromMediaUri)
            } else if (!keeps) {
                dropFaceDataLocked(fromMediaUri)
            }
            rows[healed.mediaUri] = healed
            publishLocked()
        }
    }

    override suspend fun deleteAllFaceData(): FaceDataDeletion {
        return lock.withLock {
            val rowsDeleted = faces.values.sumOf { it.size } + faceStates.size + attempts.size
            faces.clear()
            faceStates.clear()
            attempts.clear()
            if (rowsDeleted > 0) publishFaceViewLocked()
            // No file, so nothing for a journal to keep.
            FaceDataDeletion(rowsDeleted = rowsDeleted, scrubbed = true)
        }
    }

    private fun dropFaceDataLocked(mediaUri: String) {
        faces.remove(mediaUri)
        faceStates.remove(mediaUri)
        attempts.remove(mediaUri)
    }

    private fun publishLocked() {
        rowsFlow.value = rows.values.sortedBy { it.mediaUri }
        publishFaceViewLocked()
    }

    private fun publishFaceViewLocked() {
        val identities = HashMap<String, MediaIdentity>()
        faceStates.keys.forEach { uri -> rows[uri]?.let { identities[uri] = it.toMediaIdentity() } }
        faceView.value = FaceView(HashMap(faceStates), HashMap(faces), identities)
    }
}
