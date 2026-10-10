package com.davide.seddio.easygallery.data

private const val LEGACY_EXTERNAL_VOLUME = "external"

data class MediaIndexUriRewrite(
    val fromMediaUri: String,
    val toMediaUri: String,
    val fromVolumeName: String,
    val toVolumeName: String
)

data class MediaIndexReconciliationResult(
    val deletedMediaUris: Set<String>,
    val healedRewrites: List<MediaIndexUriRewrite>
)

private fun reconciledVolumeNameForHealedUri(mediaUri: String, reconciledVolumeName: String): String? {
    val parsed = mediaStoreVolumeNameFromUri(mediaUri) ?: return null
    // The external namespace is a union view, so it does not identify one concrete volume.
    // Keep the currently reconciled volume to preserve listByVolume reachability.
    return if (parsed == LEGACY_EXTERNAL_VOLUME) reconciledVolumeName else parsed
}

class MediaIndexReconciler(
    private val mediaIndexStore: MediaIndexStore
) {

    /**
     * `liveMedia` must be the full unfiltered MediaStore set for the current sync pass.
     * Filtering (folder/search/type) can make still-existing rows look deleted.
     */
    suspend fun reconcileVolume(
        volumeName: String,
        liveMedia: List<MediaItem>,
        presentVolumes: Set<String>
    ): MediaIndexReconciliationResult {
        if (volumeName !in presentVolumes) {
            return MediaIndexReconciliationResult(
                deletedMediaUris = emptySet(),
                healedRewrites = emptyList()
            )
        }

        val indexedRows = mediaIndexStore.listByVolume(volumeName)
        if (indexedRows.isEmpty()) {
            return MediaIndexReconciliationResult(
                deletedMediaUris = emptySet(),
                healedRewrites = emptyList()
            )
        }

        val liveByUri = HashMap<String, MediaItem>(liveMedia.size)
        val liveByHealingKey = HashMap<MediaHealingKey, MediaItem>(liveMedia.size)
        liveMedia.forEach { item ->
            val identity = item.toMediaIdentity()
            liveByUri.putIfAbsent(identity.mediaUri, item)
            liveByHealingKey.putIfAbsent(identity.healingKey, item)
        }

        val deleted = linkedSetOf<String>()
        val rewrites = mutableListOf<MediaIndexUriRewrite>()

        indexedRows.forEach { row ->
            val direct = liveByUri[row.mediaUri]
            if (direct != null) return@forEach

            val healed = liveByHealingKey[row.toMediaIdentity().healingKey]
            if (healed != null) {
                val healedIdentity = healed.toMediaIdentity()
                val healedVolume =
                    reconciledVolumeNameForHealedUri(healedIdentity.mediaUri, volumeName)
                        ?: row.volumeName
                val rewritten = row.copy(
                    mediaUri = healedIdentity.mediaUri,
                    displayName = healedIdentity.healingKey.displayName,
                    size = healedIdentity.healingKey.size,
                    dateModified = healedIdentity.healingKey.dateModified,
                    volumeName = healedVolume
                )

                // Store API only exposes delete-by-uri plus upsert, so rewrite is non-atomic.
                // ADR-0020 treats index rows as a recomputable cache: if a crash lands between
                // these calls, the cost is re-extraction, not user-data loss.
                mediaIndexStore.deleteByMediaUri(row.mediaUri)
                mediaIndexStore.upsert(listOf(rewritten))
                rewrites += MediaIndexUriRewrite(
                    fromMediaUri = row.mediaUri,
                    toMediaUri = rewritten.mediaUri,
                    fromVolumeName = row.volumeName,
                    toVolumeName = rewritten.volumeName
                )
                return@forEach
            }

            mediaIndexStore.deleteByMediaUri(row.mediaUri)
            deleted += row.mediaUri
        }

        return MediaIndexReconciliationResult(
            deletedMediaUris = deleted,
            healedRewrites = rewrites
        )
    }
}
