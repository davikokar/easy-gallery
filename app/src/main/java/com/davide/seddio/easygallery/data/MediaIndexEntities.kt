package com.davide.seddio.easygallery.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(
    tableName = "media_index",
    indices = [
        Index(value = ["volumeName"]),
        Index(value = ["capturedDateTakenEpochMillis"]),
        Index(value = ["capturedLatitude", "capturedLongitude"]),
        Index(value = ["derivedCountryCode", "derivedRegionCode", "derivedPlaceName"])
    ]
)
data class MediaIndexEntity(
    @PrimaryKey
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
    val derivedPlaceResolverVersion: Int?
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

@Entity(tableName = "media_index_watermark")
data class MediaIndexWatermarkEntity(
    @PrimaryKey
    val volumeName: String,
    val version: String?,
    val generation: Long?,
    val dateModifiedWatermarkSeconds: Long?
)

class FaceIndexOutcomeConverters {
    @TypeConverter
    fun fromOutcome(value: FaceIndexOutcome): String = value.name

    @TypeConverter
    fun toOutcome(value: String): FaceIndexOutcome = FaceIndexOutcome.valueOf(value)
}

// Tier C rows (ADR-0018 Amendment 5). No foreign keys: the store's REPLACE upsert would fire a cascade on every
// Tier A write, so the store applies each Tier C rule itself.
@Entity(tableName = "media_index_face_state")
data class MediaIndexFaceStateEntity(
    @PrimaryKey
    val mediaUri: String,
    val pipelineVersion: Int,
    val outcome: FaceIndexOutcome
) {
    override fun toString(): String = "MediaIndexFaceStateEntity(redacted)"
}

@Entity(
    tableName = "media_index_face",
    indices = [Index(value = ["mediaUri"])]
)
class MediaIndexFaceEntity(
    // Auto-generated, so an id is never reused.
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaUri: String,
    val pipelineVersion: Int,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val embedding: ByteArray,
    val groupId: Long?,
    val groupingVersion: Int?
) {
    override fun equals(other: Any?): Boolean {
        return other is MediaIndexFaceEntity &&
            id == other.id &&
            mediaUri == other.mediaUri &&
            pipelineVersion == other.pipelineVersion &&
            boxLeft == other.boxLeft &&
            boxTop == other.boxTop &&
            boxRight == other.boxRight &&
            boxBottom == other.boxBottom &&
            embedding.contentEquals(other.embedding) &&
            groupId == other.groupId &&
            groupingVersion == other.groupingVersion
    }

    override fun hashCode(): Int = 31 * id.hashCode() + embedding.contentHashCode()

    override fun toString(): String = "MediaIndexFaceEntity(redacted)"
}
