package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

interface MediaStoreVolumeSnapshotProvider {
    fun snapshot(sdkInt: Int = Build.VERSION.SDK_INT): MediaStoreVolumeSnapshot
}

interface MediaStoreVolumeGateway {
    @RequiresApi(Build.VERSION_CODES.Q)
    fun externalVolumeNames(context: Context): Set<String>

    @RequiresApi(Build.VERSION_CODES.R)
    fun recentExternalVolumeNames(context: Context): Set<String>

    fun version(context: Context): String?

    @RequiresApi(Build.VERSION_CODES.Q)
    fun version(context: Context, volumeName: String): String?

    @RequiresApi(Build.VERSION_CODES.R)
    fun generation(context: Context, volumeName: String): Long
}

object AndroidMediaStoreVolumeGateway : MediaStoreVolumeGateway {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun externalVolumeNames(context: Context): Set<String> {
        return MediaStore.getExternalVolumeNames(context)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun recentExternalVolumeNames(context: Context): Set<String> {
        return MediaStore.getRecentExternalVolumeNames(context)
    }

    override fun version(context: Context): String? = MediaStore.getVersion(context)

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun version(context: Context, volumeName: String): String? {
        return MediaStore.getVersion(context, volumeName)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun generation(context: Context, volumeName: String): Long {
        return MediaStore.getGeneration(context, volumeName)
    }
}

class AndroidMediaStoreVolumeSnapshotProvider(
    private val context: Context,
    private val gateway: MediaStoreVolumeGateway = AndroidMediaStoreVolumeGateway
) : MediaStoreVolumeSnapshotProvider {

    override fun snapshot(sdkInt: Int): MediaStoreVolumeSnapshot {
        val branch = resolveMediaStoreDeltaApiBranch(sdkInt)
        return when (branch) {
            MediaStoreDeltaApiBranch.API_30_PLUS -> api30PlusSnapshot(sdkInt)
            MediaStoreDeltaApiBranch.API_29 -> api29Snapshot(sdkInt)
            MediaStoreDeltaApiBranch.API_28 -> api28Snapshot()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun api30PlusSnapshot(sdkInt: Int): MediaStoreVolumeSnapshot {
        require(sdkInt >= Build.VERSION_CODES.R) {
            "API 30+ snapshot requested with sdkInt=$sdkInt"
        }
        val presentVolumes = gateway.externalVolumeNames(context)
        val recentVolumes = gateway.recentExternalVolumeNames(context)

        val observed = presentVolumes.mapNotNull { volumeName ->
            val version = gateway.version(context, volumeName) ?: return@mapNotNull null
            ObservedMediaStoreVolume(
                volumeName = volumeName,
                version = version,
                generation = gateway.generation(context, volumeName)
            )
        }

        return MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS,
            observedVolumes = observed,
            presentVolumes = presentVolumes,
            knownButUnavailableVolumes = recentVolumes - presentVolumes
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun api29Snapshot(sdkInt: Int): MediaStoreVolumeSnapshot {
        require(sdkInt >= Build.VERSION_CODES.Q) {
            "API 29 snapshot requested with sdkInt=$sdkInt"
        }
        val presentVolumes = gateway.externalVolumeNames(context)
        val observed = presentVolumes.mapNotNull { volumeName ->
            val version = gateway.version(context, volumeName) ?: return@mapNotNull null
            ObservedMediaStoreVolume(
                volumeName = volumeName,
                version = version,
                generation = null
            )
        }

        return MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_29,
            observedVolumes = observed,
            presentVolumes = presentVolumes,
            knownButUnavailableVolumes = emptySet()
        )
    }

    private fun api28Snapshot(): MediaStoreVolumeSnapshot {
        // API 28 has no per-volume APIs (names/versions/generation), so we cannot track
        // secondary removable volumes independently. We model only external_primary and let
        // reconciliation keep non-primary rows until those volumes are observed on newer APIs.
        // A device that never leaves API 28 never observes those volumes, so retained rows can
        // accumulate for the life of the install.
        val primaryVersion = gateway.version(context)
        val observed = if (primaryVersion != null) {
            listOf(
                ObservedMediaStoreVolume(
                    volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY,
                    version = primaryVersion,
                    generation = null
                )
            )
        } else {
            emptyList()
        }

        return MediaStoreVolumeSnapshot(
            apiBranch = MediaStoreDeltaApiBranch.API_28,
            observedVolumes = observed,
            presentVolumes = setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            knownButUnavailableVolumes = emptySet()
        )
    }
}
