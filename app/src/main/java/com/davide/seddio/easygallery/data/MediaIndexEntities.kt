package com.davide.seddio.easygallery.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
