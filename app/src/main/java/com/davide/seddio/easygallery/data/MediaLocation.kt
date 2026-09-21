package com.davide.seddio.easygallery.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class MediaCoordinates(
    val latitude: Double,
    val longitude: Double
)

private val ISO_6709_LOCATION_REGEX = Regex(
    "^([+-]\\d{1,2}\\.\\d+)([+-]\\d{1,3}\\.\\d+)([+-]\\d+(?:\\.\\d+)?)?/?$"
)

fun parseIso6709Location(raw: String?): MediaCoordinates? {
    if (raw.isNullOrBlank()) return null

    val match = ISO_6709_LOCATION_REGEX.matchEntire(raw.trim()) ?: return null
    val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
    val longitude = match.groupValues[2].toDoubleOrNull() ?: return null

    return mediaCoordinatesOrNull(latitude, longitude)
}

fun isValidMediaCoordinates(latitude: Double, longitude: Double): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (latitude !in -90.0..90.0) return false
    if (longitude !in -180.0..180.0) return false
    // Null Island is treated as absent due to common EXIF/encoder defaults.
    if (latitude == 0.0 && longitude == 0.0) return false
    return true
}

fun formatCoordinatesForDisplay(coordinates: MediaCoordinates): String {
    // Coordinates must use Locale.US to keep decimal points in all app locales.
    return String.format(
        Locale.US,
        "%.6f, %.6f",
        coordinates.latitude,
        coordinates.longitude
    )
}

fun buildGeoUri(coordinates: MediaCoordinates, label: String): String {
    val latitude = String.format(Locale.US, "%.6f", coordinates.latitude)
    val longitude = String.format(Locale.US, "%.6f", coordinates.longitude)
    val encodedLabel = URLEncoder.encode(label, StandardCharsets.UTF_8)
    return "geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)"
}

fun buildGoogleMapsUrl(coordinates: MediaCoordinates, label: String): String {
    val latitude = String.format(Locale.US, "%.6f", coordinates.latitude)
    val longitude = String.format(Locale.US, "%.6f", coordinates.longitude)
    val query = URLEncoder.encode("$latitude,$longitude ($label)", StandardCharsets.UTF_8)
    return "https://www.google.com/maps/search/?api=1&query=$query"
}

class MediaLocationReader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun readLocation(mediaItem: MediaItem): MediaCoordinates? = withContext(Dispatchers.IO) {
        when (mediaItem.type) {
            MediaType.VIDEO -> readVideoLocation(mediaItem)
            MediaType.IMAGE,
            MediaType.GIF -> readImageLocation(mediaItem)
        }
    }

    private fun readVideoLocation(mediaItem: MediaItem): MediaCoordinates? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, mediaItem.uri)
            val rawLocation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            return parseIso6709Location(rawLocation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Broad catch is intentional: metadata read failure must never crash UI.
            return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release failures.
            }
        }
    }

    private fun readImageLocation(mediaItem: MediaItem): MediaCoordinates? {
        val sourceUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.setRequireOriginal(mediaItem.uri)
        } else {
            mediaItem.uri
        }

        try {
            appContext.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                val latLong = ExifInterface(inputStream).latLong ?: return null
                if (latLong.size < 2) return null
                return mediaCoordinatesOrNull(latLong[0], latLong[1])
            }
            return null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Broad catch is intentional: metadata read failure must never crash UI.
            return null
        }
    }
}

private fun mediaCoordinatesOrNull(latitude: Double, longitude: Double): MediaCoordinates? {
    if (!isValidMediaCoordinates(latitude, longitude)) return null
    return MediaCoordinates(latitude = latitude, longitude = longitude)
}
