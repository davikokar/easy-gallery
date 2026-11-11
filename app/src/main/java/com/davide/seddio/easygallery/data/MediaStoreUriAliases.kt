package com.davide.seddio.easygallery.data

private val MEDIASTORE_URI_REGEX =
    Regex("^content://media/([^/]+)/(images|video)/media/(.+)$")

private const val LEGACY_EXTERNAL_VOLUME = "external"

data class MediaStoreUriParts(
    val volumeName: String,
    val mediaKindSegment: String,
    val mediaIdSegment: String
)

fun parseMediaStoreUri(mediaUri: String): MediaStoreUriParts? {
    val match = MEDIASTORE_URI_REGEX.matchEntire(mediaUri) ?: return null
    return MediaStoreUriParts(
        volumeName = match.groupValues[1],
        mediaKindSegment = match.groupValues[2],
        mediaIdSegment = match.groupValues[3]
    )
}

fun mediaStoreVolumeNameFromUri(mediaUri: String): String? {
    return parseMediaStoreUri(mediaUri)?.volumeName
}

/**
 * Canonical join key for MediaStore URIs across volumes.
 *
 * MediaStoreDataSource always emits EXTERNAL_CONTENT_URI rows (volume "external"), while
 * per-volume delta queries can emit content://media/<volume>/... rows. We normalize all of them
 * to the external namespace so row selection and live media joins use one URI spelling.
 */
fun canonicalMediaStoreUriForJoin(mediaUri: String): String {
    val parsed = parseMediaStoreUri(mediaUri) ?: return mediaUri
    if (parsed.volumeName == LEGACY_EXTERNAL_VOLUME) return mediaUri

    return "content://media/$LEGACY_EXTERNAL_VOLUME/${parsed.mediaKindSegment}/media/${parsed.mediaIdSegment}"
}
