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

data class LiveMediaMatch(
    val item: MediaItem,
    val requiresUriRewrite: Boolean
)

/**
 * The one lookup of a stored identity against live media (ADR-0010, ADR-0020): direct URI first,
 * then the healing key. On a healing-key collision the first live item wins.
 *
 * [uriKey] is applied to both sides of the direct match. Membership compares raw URIs
 * (ADR-0010 Amendment 1); the index join passes [canonicalMediaStoreUriForJoin].
 */
class LiveMediaLookup(
    liveMedia: List<MediaItem>,
    private val uriKey: (String) -> String = { it }
) {
    private val byUri = HashMap<String, MediaItem>(liveMedia.size)
    private val byHealingKey = HashMap<MediaHealingKey, MediaItem>(liveMedia.size)

    init {
        for (item in liveMedia) {
            val identity = item.toMediaIdentity()
            byUri.putIfAbsent(uriKey(identity.mediaUri), item)
            byHealingKey.putIfAbsent(identity.healingKey, item)
        }
    }

    fun find(identity: MediaIdentity): LiveMediaMatch? {
        val direct = byUri[uriKey(identity.mediaUri)]
        if (direct != null) return LiveMediaMatch(item = direct, requiresUriRewrite = false)

        val healed = byHealingKey[identity.healingKey] ?: return null
        return LiveMediaMatch(item = healed, requiresUriRewrite = true)
    }
}
