package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore

class AndroidMediaStoreVolumeVersionProvider(
    private val context: Context
) : MediaStoreVolumeVersionProvider {

    override fun versionForVolume(volumeName: String): String? {
        return if (volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) {
            MediaStore.getVersion(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getVersion(context, volumeName)
        } else {
            null
        }
    }
}
