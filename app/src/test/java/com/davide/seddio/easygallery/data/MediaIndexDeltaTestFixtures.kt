package com.davide.seddio.easygallery.data

object MediaIndexDeltaTestFixtures {
    const val PRIMARY_VOLUME = "external_primary"
    const val SECONDARY_VOLUME = "external"

    fun twoVolumeSnapshot(
        apiBranch: MediaStoreDeltaApiBranch,
        primaryVersion: String,
        secondaryVersion: String,
        primaryGeneration: Long? = null,
        secondaryGeneration: Long? = null,
        knownButUnavailableVolumes: Set<String> = emptySet()
    ): MediaStoreVolumeSnapshot {
        return MediaStoreVolumeSnapshot(
            apiBranch = apiBranch,
            observedVolumes = listOf(
                ObservedMediaStoreVolume(PRIMARY_VOLUME, primaryVersion, primaryGeneration),
                ObservedMediaStoreVolume(SECONDARY_VOLUME, secondaryVersion, secondaryGeneration)
            ),
            presentVolumes = setOf(PRIMARY_VOLUME, SECONDARY_VOLUME),
            knownButUnavailableVolumes = knownButUnavailableVolumes
        )
    }

    fun twoVolumeIndexRows(): List<MediaIndexEntry> {
        return listOf(
            MediaIndexEntry(
                mediaUri = "content://media/external_primary/images/media/1",
                displayName = "primary.jpg",
                size = 100L,
                dateModified = 200L,
                volumeName = PRIMARY_VOLUME,
                generationAdded = null,
                generationModified = null,
                capturedDateTakenEpochMillis = null,
                capturedLatitude = null,
                capturedLongitude = null,
                capturedWidth = null,
                capturedHeight = null,
                derivedCountryCode = null,
                derivedRegionCode = null,
                derivedPlaceName = null
            ),
            MediaIndexEntry(
                mediaUri = "content://media/external/images/media/2",
                displayName = "secondary.jpg",
                size = 101L,
                dateModified = 201L,
                volumeName = SECONDARY_VOLUME,
                generationAdded = null,
                generationModified = null,
                capturedDateTakenEpochMillis = null,
                capturedLatitude = null,
                capturedLongitude = null,
                capturedWidth = null,
                capturedHeight = null,
                derivedCountryCode = null,
                derivedRegionCode = null,
                derivedPlaceName = null
            )
        )
    }
}
