package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.data.FaceBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The crop is this many times the larger side of the face box, so the face keeps a margin of 40% (plan 3.6). */
internal const val FACE_CROP_SIDE_FACTOR = 1.8

/** A square of whole pixels inside an image: [left] and [top] are its top-left corner, [size] its side. */
data class FaceCropRect(val left: Int, val top: Int, val size: Int) {
    val right: Int get() = left + size
    val bottom: Int get() = top + size
}

/**
 * The square of the decoded image to show for [box], whose edges are fractions of the upright frame (ADR-0022 §1); the
 * image is [imageWidth] by [imageHeight] pixels in that frame. Centred on the box, [FACE_CROP_SIDE_FACTOR] times its
 * larger side in pixels, shifted to stay inside the image and never shrunk for it; an image shorter than that square
 * gets the largest square that fits, centred on the box in the other dimension as far as the image allows.
 * Computed at display time and never stored.
 */
fun faceCropRect(box: FaceBox, imageWidth: Int, imageHeight: Int): FaceCropRect {
    require(imageWidth > 0 && imageHeight > 0) { "an image has a positive size" }

    val boxWidth = (box.right.toDouble() - box.left.toDouble()) * imageWidth
    val boxHeight = (box.bottom.toDouble() - box.top.toDouble()) * imageHeight
    val centreX = (box.left.toDouble() + box.right.toDouble()) / 2.0 * imageWidth
    val centreY = (box.top.toDouble() + box.bottom.toDouble()) / 2.0 * imageHeight

    val wantedSide = FACE_CROP_SIDE_FACTOR * max(boxWidth, boxHeight)
    val side = min(wantedSide.roundToInt(), min(imageWidth, imageHeight)).coerceAtLeast(1)

    val left = (centreX - side / 2.0).roundToInt().coerceIn(0, imageWidth - side)
    val top = (centreY - side / 2.0).roundToInt().coerceIn(0, imageHeight - side)
    return FaceCropRect(left = left, top = top, size = side)
}
