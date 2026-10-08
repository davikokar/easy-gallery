package com.davide.seddio.easygallery.data

/**
 * Shared media identity used by album membership healing and the media index.
 */
data class MediaHealingKey(
    val displayName: String,
    val size: Long,
    val dateModified: Long
)

data class MediaIdentity(
    val mediaUri: String,
    val healingKey: MediaHealingKey
)

fun mediaIdentityOf(
    mediaUri: String,
    displayName: String,
    size: Long,
    dateModified: Long
): MediaIdentity {
    return MediaIdentity(
        mediaUri = mediaUri,
        healingKey = MediaHealingKey(
            displayName = displayName,
            size = size,
            dateModified = dateModified
        )
    )
}

fun MediaItem.toMediaIdentity(): MediaIdentity {
    return mediaIdentityOf(
        mediaUri = uri.toString(),
        displayName = name,
        size = size,
        dateModified = dateModified
    )
}
