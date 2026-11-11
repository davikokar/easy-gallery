package com.davide.seddio.easygallery.data

/**
 * Per-volume watermark persisted for media-index delta detection.
 *
 * `dateModifiedWatermarkSeconds` is only used by the API 28-29 fallback path where
 * generation numbers are unavailable.
 */
data class MediaIndexVolumeWatermark(
    val version: String?,
    val generation: Long?,
    val dateModifiedWatermarkSeconds: Long?
)

interface MediaIndexWatermarkStore {
    fun readAll(): Map<String, MediaIndexVolumeWatermark>
    fun read(volumeName: String): MediaIndexVolumeWatermark?
    fun write(volumeName: String, watermark: MediaIndexVolumeWatermark)
    fun remove(volumeName: String)
}

class InMemoryMediaIndexWatermarkStore(
    initial: Map<String, MediaIndexVolumeWatermark> = emptyMap()
) : MediaIndexWatermarkStore {
    private val values = initial.toMutableMap()

    override fun readAll(): Map<String, MediaIndexVolumeWatermark> = values.toMap()

    override fun read(volumeName: String): MediaIndexVolumeWatermark? = values[volumeName]

    override fun write(volumeName: String, watermark: MediaIndexVolumeWatermark) {
        values[volumeName] = watermark
    }

    override fun remove(volumeName: String) {
        values.remove(volumeName)
    }
}

class RoomMediaIndexWatermarkStore(
    private val dao: MediaIndexDao
) : MediaIndexWatermarkStore {

    override fun readAll(): Map<String, MediaIndexVolumeWatermark> {
        return dao.listWatermarks()
            .asSequence()
            .map { entity -> entity.volumeName to entity.toModel() }
            .toMap()
    }

    override fun read(volumeName: String): MediaIndexVolumeWatermark? {
        return dao.getWatermark(volumeName)?.toModel()
    }

    override fun write(volumeName: String, watermark: MediaIndexVolumeWatermark) {
        dao.upsertWatermark(
            MediaIndexWatermarkEntity(
                volumeName = volumeName,
                version = watermark.version,
                generation = watermark.generation,
                dateModifiedWatermarkSeconds = watermark.dateModifiedWatermarkSeconds
            )
        )
    }

    override fun remove(volumeName: String) {
        dao.deleteWatermark(volumeName)
    }
}

private fun MediaIndexWatermarkEntity.toModel(): MediaIndexVolumeWatermark {
    return MediaIndexVolumeWatermark(
        version = version,
        generation = generation,
        dateModifiedWatermarkSeconds = dateModifiedWatermarkSeconds
    )
}
