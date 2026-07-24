package com.davide.seddio.easygallery.data

import android.net.Uri

data class Folder(
    val name: String,
    val imageCount: Int,
    val thumbnailUri: Uri,
    val isPinned: Boolean = false,
    val path: String = "",
    val size: Long = 0L,
    val dateModified: Long = 0L,
    val dateTaken: Long = 0L
)
