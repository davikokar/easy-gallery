package com.davide.seddio.easygallery.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType

fun resolveShareMimeType(types: Collection<MediaType>): String {
    if (types.isEmpty()) return "*/*"
    if (types.all { it == MediaType.IMAGE || it == MediaType.GIF }) return "image/*"
    if (types.all { it == MediaType.VIDEO }) return "video/*"
    return "*/*"
}

fun shareMedia(context: Context, mediaItems: List<MediaItem>) {
    if (mediaItems.isEmpty()) return

    val uris = mediaItems.map { it.uri }
    val mimeType = resolveShareMimeType(mediaItems.map { it.type })

    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }

    shareIntent.clipData = buildShareClipData(context, uris)
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    val chooserIntent = Intent.createChooser(
        shareIntent,
        context.getString(R.string.share_media_title)
    )
    context.startActivity(chooserIntent)
}

private fun buildShareClipData(context: Context, uris: List<Uri>): ClipData {
    val clipData = ClipData.newUri(context.contentResolver, "shared-media", uris.first())
    for (uri in uris.drop(1)) {
        clipData.addItem(ClipData.Item(uri))
    }
    return clipData
}
