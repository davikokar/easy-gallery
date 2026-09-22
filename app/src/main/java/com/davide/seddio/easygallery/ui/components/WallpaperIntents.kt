package com.davide.seddio.easygallery.ui.components

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaType

fun supportsWallpaper(type: MediaType): Boolean = type == MediaType.IMAGE

fun setImageAsWallpaper(context: Context, uri: Uri) {
    val wallpaperManager = WallpaperManager.getInstance(context)
    if (!wallpaperManager.isWallpaperSupported || !wallpaperManager.isSetWallpaperAllowed) {
        Toast.makeText(context, R.string.wallpaper_app_not_found, Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val cropIntent = wallpaperManager.getCropAndSetWallpaperIntent(uri).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(cropIntent)
        return
    } catch (_: IllegalArgumentException) {
        // Fall back to a generic attach-data chooser when crop-and-set cannot be built.
    } catch (_: ActivityNotFoundException) {
        // Fall back to a generic attach-data chooser when no crop-and-set handler is visible.
    }

    val mimeType = context.contentResolver.getType(uri) ?: "image/*"
    val attachDataIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(uri, mimeType)
        putExtra("mimeType", mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(
        attachDataIntent,
        context.getString(R.string.menu_use_as_wallpaper)
    )

    try {
        context.startActivity(chooserIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.wallpaper_app_not_found, Toast.LENGTH_SHORT).show()
    }
}
