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
}
