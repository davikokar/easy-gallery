package com.davide.seddio.easygallery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class MediaIndexDateTakenProjectionEntity(
    val mediaUri: String,
    val capturedDateTakenEpochMillis: Long?
)

data class MediaIndexEvaluationProjectionEntity(
    val mediaUri: String,
    val displayName: String,
    val size: Long,
    val dateModified: Long,
    val capturedDateTakenEpochMillis: Long?,
    val hasCoordinates: Boolean,
    val derivedCountryCode: String?,
    val derivedRegionCode: String?,
    val derivedPlaceName: String?,
    val derivedPlaceResolverVersion: Int?
)

// What grouping reads of a face: the id and the embedding, nothing else.
class MediaIndexFaceEmbeddingEntity(
    val id: Long,
    val embedding: ByteArray
) {
    override fun toString(): String = "MediaIndexFaceEmbeddingEntity(redacted)"
}

// One group for the picker, with its representative face and that face's index row.
class MediaIndexFaceGroupEntity(
    val groupId: Long,
    val imageCount: Int,
    val newestDateTakenEpochMillis: Long?,
    val representativeMediaUri: String,
    val representativeDisplayName: String,
    val representativeSize: Long,
    val representativeDateModified: Long,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val pipelineVersion: Int
) {
    override fun toString(): String = "MediaIndexFaceGroupEntity(redacted)"
}

// One row per face; an image with no face is one row with the face columns null. Never the embedding.
class MediaIndexFaceEvaluationRowEntity(
    val mediaUri: String,
    val displayName: String,
    val size: Long,
    val dateModified: Long,
    val outcome: FaceIndexOutcome,
    val faceId: Long?,
    val boxLeft: Float?,
    val boxTop: Float?,
    val boxRight: Float?,
    val boxBottom: Float?,
    val groupId: Long?,
    val groupingVersion: Int?
) {
    override fun toString(): String = "MediaIndexFaceEvaluationRowEntity(redacted)"
}

@Dao
interface MediaIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<MediaIndexEntity>)

    @Query("SELECT * FROM media_index ORDER BY mediaUri ASC")
    fun observeAll(): Flow<List<MediaIndexEntity>>

    @Query("SELECT mediaUri, capturedDateTakenEpochMillis FROM media_index ORDER BY mediaUri ASC")
    fun observeDateTakenProjection(): Flow<List<MediaIndexDateTakenProjectionEntity>>

    // hasCoordinates uses the same both-columns test as listPendingPlaceResolution.
    @Query(
        "SELECT mediaUri, displayName, size, dateModified, capturedDateTakenEpochMillis, " +
            "(capturedLatitude IS NOT NULL AND capturedLongitude IS NOT NULL) AS hasCoordinates, " +
            "derivedCountryCode, derivedRegionCode, derivedPlaceName, derivedPlaceResolverVersion " +
            "FROM media_index ORDER BY mediaUri ASC"
    )
    fun observeEvaluationProjection(): Flow<List<MediaIndexEvaluationProjectionEntity>>

    @Query("SELECT * FROM media_index WHERE volumeName = :volumeName ORDER BY mediaUri ASC")
    fun observeByVolume(volumeName: String): Flow<List<MediaIndexEntity>>

    // One query over three tables, so one emission is one consistent snapshot of a face or grouping write.
    @Query(
        "SELECT s.mediaUri AS mediaUri, m.displayName AS displayName, m.size AS size, m.dateModified AS dateModified, " +
            "s.outcome AS outcome, f.id AS faceId, f.boxLeft AS boxLeft, f.boxTop AS boxTop, f.boxRight AS boxRight, " +
            "f.boxBottom AS boxBottom, f.groupId AS groupId, f.groupingVersion AS groupingVersion " +
            "FROM media_index_face_state s JOIN media_index m ON m.mediaUri = s.mediaUri " +
            "LEFT JOIN media_index_face f ON f.mediaUri = s.mediaUri AND f.pipelineVersion = :pipelineVersion " +
            "WHERE s.pipelineVersion = :pipelineVersion ORDER BY s.mediaUri ASC, f.id ASC"
    )
    fun observeFaceEvaluationRows(pipelineVersion: Int): Flow<List<MediaIndexFaceEvaluationRowEntity>>

    @Query("SELECT * FROM media_index WHERE mediaUri = :mediaUri LIMIT 1")
    suspend fun getByMediaUri(mediaUri: String): MediaIndexEntity?

    @Query("SELECT * FROM media_index WHERE volumeName = :volumeName ORDER BY mediaUri ASC")
    suspend fun listByVolume(volumeName: String): List<MediaIndexEntity>

    @Query("DELETE FROM media_index WHERE mediaUri = :mediaUri")
    suspend fun deleteByMediaUri(mediaUri: String)

    @Query("DELETE FROM media_index WHERE volumeName = :volumeName")
    suspend fun deleteByVolume(volumeName: String)

    @Query("DELETE FROM media_index")
    suspend fun deleteAll()

    // A null cursor starts from the first row; the cursor is the last URI of the previous page.
    @Query(
        "SELECT * FROM media_index " +
            "WHERE capturedLatitude IS NOT NULL AND capturedLongitude IS NOT NULL " +
            "AND (derivedPlaceResolverVersion IS NULL OR derivedPlaceResolverVersion != :currentVersion) " +
            "AND (:afterMediaUri IS NULL OR mediaUri > :afterMediaUri) " +
            "ORDER BY mediaUri ASC LIMIT :limit"
    )
    suspend fun listPendingPlaceResolution(
        currentVersion: Int,
        afterMediaUri: String?,
        limit: Int
    ): List<MediaIndexEntity>

    // Matches the exact coordinates the answer was computed from; returns the rows written (0 or 1).
    @Query(
        "UPDATE media_index SET derivedCountryCode = :countryCode, derivedRegionCode = :regionCode, " +
            "derivedPlaceName = :placeName, derivedPlaceResolverVersion = :resolverVersion " +
            "WHERE mediaUri = :mediaUri AND capturedLatitude = :latitude AND capturedLongitude = :longitude"
    )
    suspend fun updateResolvedPlace(
        mediaUri: String,
        latitude: Double,
        longitude: Double,
        countryCode: String?,
        regionCode: String?,
        placeName: String?,
        resolverVersion: Int
    ): Int

    @Query("SELECT * FROM media_index_watermark")
    fun listWatermarks(): List<MediaIndexWatermarkEntity>

    @Query("SELECT * FROM media_index_watermark WHERE volumeName = :volumeName LIMIT 1")
    fun getWatermark(volumeName: String): MediaIndexWatermarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertWatermark(watermark: MediaIndexWatermarkEntity)

    @Query("DELETE FROM media_index_watermark WHERE volumeName = :volumeName")
    fun deleteWatermark(volumeName: String)

    // Tier C. The store composes these inside one transaction; none is a rule on its own.
    @Query("SELECT * FROM media_index_face_state WHERE mediaUri = :mediaUri LIMIT 1")
    suspend fun getFaceState(mediaUri: String): MediaIndexFaceStateEntity?

    @Query("SELECT * FROM media_index_face WHERE mediaUri = :mediaUri ORDER BY id ASC")
    suspend fun listFaces(mediaUri: String): List<MediaIndexFaceEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM media_index_face_state) OR EXISTS(SELECT 1 FROM media_index_face) " +
            "OR EXISTS(SELECT 1 FROM media_index_face_attempt)"
    )
    suspend fun hasFaceData(): Boolean

    @Query("SELECT * FROM media_index_face_attempt WHERE mediaUri = :mediaUri LIMIT 1")
    suspend fun getFaceAttempt(mediaUri: String): MediaIndexFaceAttemptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFaceAttempt(attempt: MediaIndexFaceAttemptEntity)

    @Query("DELETE FROM media_index_face_attempt WHERE mediaUri = :mediaUri")
    suspend fun deleteFaceAttempt(mediaUri: String)

    @Query("UPDATE media_index_face_attempt SET mediaUri = :toMediaUri WHERE mediaUri = :fromMediaUri")
    suspend fun rekeyFaceAttempt(fromMediaUri: String, toMediaUri: String)

    @Query("DELETE FROM media_index_face_attempt WHERE mediaUri IN (SELECT mediaUri FROM media_index WHERE volumeName = :volumeName)")
    suspend fun deleteFaceAttemptsByVolume(volumeName: String)

    @Query("DELETE FROM media_index_face_attempt")
    suspend fun deleteAllFaceAttempts(): Int

    // Images by URI path, case-sensitively (instr, not LIKE); no state at [pipelineVersion]. A null cursor starts at the first row.
    @Query(
        "SELECT m.mediaUri AS mediaUri, m.displayName AS displayName, m.size AS size, m.dateModified AS dateModified, " +
            "COALESCE((SELECT a.attempts FROM media_index_face_attempt a " +
            "WHERE a.mediaUri = m.mediaUri AND a.pipelineVersion = :pipelineVersion), 0) AS attempts " +
            "FROM media_index m " +
            "WHERE instr(m.mediaUri, '/images/media/') > 0 " +
            "AND NOT EXISTS (SELECT 1 FROM media_index_face_state s " +
            "WHERE s.mediaUri = m.mediaUri AND s.pipelineVersion = :pipelineVersion) " +
            "AND (:afterMediaUri IS NULL OR m.mediaUri > :afterMediaUri) " +
            "ORDER BY m.mediaUri ASC LIMIT :limit"
    )
    suspend fun listPendingFaceImages(
        pipelineVersion: Int,
        afterMediaUri: String?,
        limit: Int
    ): List<MediaIndexPendingFaceImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFaceState(state: MediaIndexFaceStateEntity)

    @Insert
    suspend fun insertFaces(faces: List<MediaIndexFaceEntity>)

    @Query("DELETE FROM media_index_face_state WHERE mediaUri = :mediaUri")
    suspend fun deleteFaceState(mediaUri: String)

    @Query("DELETE FROM media_index_face WHERE mediaUri = :mediaUri")
    suspend fun deleteFaces(mediaUri: String)

    @Query("UPDATE media_index_face_state SET mediaUri = :toMediaUri WHERE mediaUri = :fromMediaUri")
    suspend fun rekeyFaceState(fromMediaUri: String, toMediaUri: String)

    @Query("UPDATE media_index_face SET mediaUri = :toMediaUri WHERE mediaUri = :fromMediaUri")
    suspend fun rekeyFaces(fromMediaUri: String, toMediaUri: String)

    // Run before the Tier A delete of the same volume: the subquery reads the rows that delete removes.
    @Query("DELETE FROM media_index_face_state WHERE mediaUri IN (SELECT mediaUri FROM media_index WHERE volumeName = :volumeName)")
    suspend fun deleteFaceStatesByVolume(volumeName: String)

    @Query("DELETE FROM media_index_face WHERE mediaUri IN (SELECT mediaUri FROM media_index WHERE volumeName = :volumeName)")
    suspend fun deleteFacesByVolume(volumeName: String)

    // Faces at [pipelineVersion] in id order, after the cursor; a null cursor starts at the first.
    @Query(
        "SELECT id, embedding FROM media_index_face WHERE pipelineVersion = :pipelineVersion " +
            "AND (:afterFaceId IS NULL OR id > :afterFaceId) ORDER BY id ASC LIMIT :limit"
    )
    suspend fun listFacesForGrouping(
        pipelineVersion: Int,
        afterFaceId: Long?,
        limit: Int
    ): List<MediaIndexFaceEmbeddingEntity>

    // A face at the version with no grouping, another grouping, or a group id naming no face at the version.
    @Query(
        "SELECT EXISTS(SELECT 1 FROM media_index_face f WHERE f.pipelineVersion = :pipelineVersion " +
            "AND (f.groupingVersion IS NULL OR f.groupingVersion != :groupingVersion OR f.groupId IS NULL " +
            "OR NOT EXISTS (SELECT 1 FROM media_index_face r WHERE r.id = f.groupId AND r.pipelineVersion = :pipelineVersion)))"
    )
    suspend fun hasFacesPendingGrouping(pipelineVersion: Int, groupingVersion: Int): Boolean

    // Only faces still at [pipelineVersion]; returns the rows written. Callers keep [faceIds] under API 28's 999 variables.
    @Query(
        "UPDATE media_index_face SET groupId = :groupId, groupingVersion = :groupingVersion " +
            "WHERE pipelineVersion = :pipelineVersion AND id IN (:faceIds)"
    )
    suspend fun updateFaceGroup(groupId: Long, groupingVersion: Int, pipelineVersion: Int, faceIds: List<Long>): Int

    // Images are counted among index rows that still exist; the representative must exist at the version too.
    @Query(
        "SELECT g.groupId AS groupId, g.imageCount AS imageCount, g.newestDate AS newestDateTakenEpochMillis, " +
            "rm.mediaUri AS representativeMediaUri, rm.displayName AS representativeDisplayName, " +
            "rm.size AS representativeSize, rm.dateModified AS representativeDateModified, " +
            "r.boxLeft AS boxLeft, r.boxTop AS boxTop, r.boxRight AS boxRight, r.boxBottom AS boxBottom, " +
            "r.pipelineVersion AS pipelineVersion " +
            "FROM (SELECT f.groupId AS groupId, COUNT(DISTINCT f.mediaUri) AS imageCount, " +
            "MAX(m.capturedDateTakenEpochMillis) AS newestDate " +
            "FROM media_index_face f JOIN media_index m ON m.mediaUri = f.mediaUri " +
            "WHERE f.pipelineVersion = :pipelineVersion AND f.groupingVersion = :groupingVersion AND f.groupId IS NOT NULL " +
            "GROUP BY f.groupId HAVING COUNT(DISTINCT f.mediaUri) >= :minimumImages) g " +
            "JOIN media_index_face r ON r.id = g.groupId AND r.pipelineVersion = :pipelineVersion " +
            "JOIN media_index rm ON rm.mediaUri = r.mediaUri " +
            "ORDER BY g.imageCount DESC, (g.newestDate IS NULL) ASC, g.newestDate DESC, g.groupId ASC"
    )
    suspend fun listFaceGroups(
        pipelineVersion: Int,
        groupingVersion: Int,
        minimumImages: Int
    ): List<MediaIndexFaceGroupEntity>

    @Query("DELETE FROM media_index_face_state")
    suspend fun deleteAllFaceStates(): Int

    @Query("DELETE FROM media_index_face")
    suspend fun deleteAllFaces(): Int
}
