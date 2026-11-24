package com.davide.seddio.easygallery.data

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
    val type: MediaType,
    val bucketName: String,
    val folderPath: String,
    val duration: Long? = null,
    // Rotation-corrected size; null when MediaStore has no usable width and height.
    val displayWidth: Int? = null,
    val displayHeight: Int? = null,
    // MediaStore DATE_TAKEN in ms, API 29+ only; null when absent, null column or not positive.
    val mediaStoreDateTakenMillis: Long? = null
)

enum class MediaType {
    IMAGE, VIDEO, GIF
}

/**
 * Stored MediaStore width and height are before rotation, so a 90 or 270 degree orientation swaps
 * them. Null when either is missing or not positive; a missing orientation counts as 0.
 */
fun displayedDimensions(width: Int?, height: Int?, orientationDegrees: Int?): Pair<Int, Int>? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val normalized = ((orientationDegrees ?: 0) % 360 + 360) % 360
    return if (normalized == 90 || normalized == 270) height to width else width to height
}
