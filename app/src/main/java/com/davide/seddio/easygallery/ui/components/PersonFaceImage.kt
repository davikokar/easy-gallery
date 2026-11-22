package com.davide.seddio.easygallery.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import coil3.transform.Transformation
import com.davide.seddio.easygallery.data.FaceBox

/** The longest side the anchor's image is decoded at; a face is cropped out of it, so more would only cost memory. */
internal const val PERSON_FACE_DECODE_PX = 1536

/**
 * Crops the decoded image to [box]'s square (see [faceCropRect]) at display time. The memory cache holds the result, and
 * [cacheKey] tells two faces of one photo apart; nothing is written to disk (ADR-0022 §1, ADR-0024 §6).
 */
internal class FaceCropTransformation(private val box: FaceBox) : Transformation() {
    override val cacheKey: String =
        "face-crop:${box.left.toRawBits()}:${box.top.toRawBits()}:${box.right.toRawBits()}:${box.bottom.toRawBits()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val rect = faceCropRect(box, input.width, input.height)
        return Bitmap.createBitmap(input, rect.left, rect.top, rect.size, rect.size)
    }
}

/**
 * The request for one person's face: the anchor's image, decoded upright and capped, then cropped. Never to the disk
 * cache (ADR-0024 §6), and not hardware-backed, which a transformation cannot read.
 */
internal fun personFaceRequest(context: PlatformContext, mediaUri: String, box: FaceBox): ImageRequest =
    ImageRequest.Builder(context)
        .data(mediaUri)
        .size(PERSON_FACE_DECODE_PX)
        .scale(Scale.FIT)
        // EXACT would upscale a source smaller than the cap, for memory and no sharpness.
        .precision(Precision.INEXACT)
        .allowHardware(false)
        .diskCachePolicy(CachePolicy.DISABLED)
        .transformations(FaceCropTransformation(box))
        .build()

/**
 * A person's face as a circle. While loading, and when the photo is gone or cannot be decoded, a neutral circle with a
 * person icon stands in: the tile stays pickable, because the anchor is data and not pixels. The request is tied to
 * the composition, so it is cancelled when the tile leaves it.
 */
@Composable
fun PersonFaceImage(mediaUri: String, box: FaceBox, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(mediaUri, box) { personFaceRequest(context, mediaUri, box) }
    var loaded by remember(request) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.6f)
            )
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { loaded = it is AsyncImagePainter.State.Success },
            modifier = Modifier.fillMaxSize()
        )
    }
}
