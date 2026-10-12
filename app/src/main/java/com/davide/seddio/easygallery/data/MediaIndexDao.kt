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

@Dao
interface MediaIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<MediaIndexEntity>)

    @Query("SELECT * FROM media_index ORDER BY mediaUri ASC")
    fun observeAll(): Flow<List<MediaIndexEntity>>

    @Query("SELECT mediaUri, capturedDateTakenEpochMillis FROM media_index ORDER BY mediaUri ASC")
    fun observeDateTakenProjection(): Flow<List<MediaIndexDateTakenProjectionEntity>>

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

    @Query("SELECT * FROM media_index_watermark")
    fun listWatermarks(): List<MediaIndexWatermarkEntity>

    @Query("SELECT * FROM media_index_watermark WHERE volumeName = :volumeName LIMIT 1")
    fun getWatermark(volumeName: String): MediaIndexWatermarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertWatermark(watermark: MediaIndexWatermarkEntity)

    @Query("DELETE FROM media_index_watermark WHERE volumeName = :volumeName")
    fun deleteWatermark(volumeName: String)
}
