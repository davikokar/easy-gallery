package com.davide.seddio.easygallery.ui.components

import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 30f
private const val DOUBLE_TAP_SCALE = 3f

/**
 * Calculates a new offset by damping the pan based on the current scale.
 * This ensures 1:1 finger tracking even at high zoom levels.
 */
fun calculateNewOffset(currentOffset: Offset, pan: Offset, scale: Float): Offset {
    return if (scale > 1f) {
        currentOffset + (pan / scale)
    } else {
        Offset.Zero
    }
}

@Composable
fun ZoomableImage(
    uri: Uri,
    contentDescription: String?,
    rotationZ: Float = 0f,
    onTap: () -> Unit,
    onScaleChanged: (Float) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    // Reset zoom when image changes
    LaunchedEffect(uri) {
        scale = 1f
        offset = Offset.Zero
        onScaleChanged(1f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > MIN_SCALE) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                        onScaleChanged(scale)
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        // If we are zooming or already zoomed, we handle it locally
                        if (scale > MIN_SCALE || zoom != 1f) {
                            val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            if (newScale != scale) {
                                scale = newScale
                                onScaleChanged(scale)
                            }
                            
                            if (scale > MIN_SCALE) {
                                offset = calculateNewOffset(offset, pan, scale)
                                // Only consume if we are actually zooming or panning to allow tap gestures to reach other listeners
                                if (zoom != 1f || pan != Offset.Zero) {
                                    event.changes.forEach { it.consume() }
                                }
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val isRotated = (rotationZ / 90f).toInt() % 2 != 0
        
        val aspectRatio = if (isRotated && imageSize != IntSize.Zero) {
            val imgWidth = imageSize.width.toFloat()
            val imgHeight = imageSize.height.toFloat()
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            val baseScale = minOf(containerWidth / imgWidth, containerHeight / imgHeight)
            val baseWidth = imgWidth * baseScale
            val baseHeight = imgHeight * baseScale

            minOf(containerWidth / baseHeight, containerHeight / baseWidth)
        } else {
            1f
        }

        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val finalScale = scale * aspectRatio
                    scaleX = finalScale
                    scaleY = finalScale
                    translationX = offset.x * scale
                    translationY = offset.y * scale
                    this.rotationZ = rotationZ
                },
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                imageSize = state.painter.intrinsicSize.let { 
                    IntSize(it.width.toInt(), it.height.toInt()) 
                }
            }
        )
    }
}
