package com.davide.seddio.easygallery.data

import android.net.Uri

data class Photo(
    val uri: Uri,
    val name: String,
    val dateAdded: Long
)
