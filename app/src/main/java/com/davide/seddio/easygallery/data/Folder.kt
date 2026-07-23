package com.davide.seddio.easygallery.data

import android.net.Uri

data class Folder(
    val name: String,
    val imageCount: Int,
    val thumbnailUri: Uri
)
