package com.davide.seddio.easygallery.data

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val size: Long,
    val type: MediaType,
    val bucketName: String,
    val folderPath: String,
    val duration: Long? = null
)

enum class MediaType {
    IMAGE, VIDEO, GIF
}
