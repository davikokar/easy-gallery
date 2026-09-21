package com.davide.seddio.easygallery.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaCoordinates
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaLocationReader
import com.davide.seddio.easygallery.data.buildGeoUri
import com.davide.seddio.easygallery.data.buildGoogleMapsUrl

@Composable
fun rememberMediaLocation(mediaItem: MediaItem): MediaCoordinates? {
    val context = LocalContext.current
    val reader = remember(context) { MediaLocationReader(context) }
    var coordinates by remember(mediaItem.uri) { mutableStateOf<MediaCoordinates?>(null) }

    LaunchedEffect(mediaItem.uri) {
        // Clear stale coordinates immediately when swiping to a new item.
        coordinates = null
        coordinates = reader.readLocation(mediaItem)
    }

    return coordinates
}

fun openMediaLocationInMaps(context: Context, coordinates: MediaCoordinates, label: String) {
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse(buildGeoUri(coordinates, label)))

    try {
        context.startActivity(geoIntent)
    } catch (_: ActivityNotFoundException) {
        val mapsWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse(buildGoogleMapsUrl(coordinates, label)))
        try {
            context.startActivity(mapsWebIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.maps_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }
}
