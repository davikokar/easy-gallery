package com.davide.seddio.easygallery.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ADR-0022's upright frame: a stored box is in the frame of the upright image, so the production decoder must apply the
 * EXIF orientation. A JPEG whose stored top-left corner carries a red mark is written with each orientation, and the mark
 * must land in the corner the upright image has it. ImageDecoder's orientation handling is the platform's, which is why
 * this runs on a device. Nothing here needs OpenCV.
 */
@RunWith(AndroidJUnit4::class)
class FaceDecodeOrientationDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val written = mutableListOf<File>()

    @After
    fun deleteWrittenFiles() {
        written.forEach { it.delete() }
    }

    @Test
    fun normalOrientationKeepsTheMarkAtTopLeft() {
        assertMarkLandsAt(ExifInterface.ORIENTATION_NORMAL, Corner.TOP_LEFT, uprightWidth = STORED_WIDTH, uprightHeight = STORED_HEIGHT)
    }

    @Test
    fun orientation3MovesTheMarkToBottomRight() {
        assertMarkLandsAt(ExifInterface.ORIENTATION_ROTATE_180, Corner.BOTTOM_RIGHT, uprightWidth = STORED_WIDTH, uprightHeight = STORED_HEIGHT)
    }

    @Test
    fun orientation6MovesTheMarkToTopRightAndSwapsTheSides() {
        assertMarkLandsAt(ExifInterface.ORIENTATION_ROTATE_90, Corner.TOP_RIGHT, uprightWidth = STORED_HEIGHT, uprightHeight = STORED_WIDTH)
    }

    @Test
    fun orientation8MovesTheMarkToBottomLeftAndSwapsTheSides() {
        assertMarkLandsAt(ExifInterface.ORIENTATION_ROTATE_270, Corner.BOTTOM_LEFT, uprightWidth = STORED_HEIGHT, uprightHeight = STORED_WIDTH)
    }

    private fun assertMarkLandsAt(orientation: Int, expected: Corner, uprightWidth: Int, uprightHeight: Int) {
        val file = writeMarkedJpeg(orientation)

        val decoded = decodeUprightForFaces(context, Uri.fromFile(file))
        try {
            assertEquals("upright width under orientation $orientation", uprightWidth, decoded.width)
            assertEquals("upright height under orientation $orientation", uprightHeight, decoded.height)
            for (corner in Corner.values()) {
                val pixel = decoded.getPixel(corner.centreX(decoded.width), corner.centreY(decoded.height))
                if (corner == expected) {
                    assertTrue("orientation $orientation: no mark at $corner, pixel=${Integer.toHexString(pixel)}", isRed(pixel))
                } else {
                    assertTrue("orientation $orientation: unexpected mark at $corner, pixel=${Integer.toHexString(pixel)}", isWhite(pixel))
                }
            }
        } finally {
            decoded.recycle()
        }
    }

    // The mark is a solid block in the stored top-left corner, large enough to survive JPEG compression at its centre.
    private fun writeMarkedJpeg(orientation: Int): File {
        val bitmap = Bitmap.createBitmap(STORED_WIDTH, STORED_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(0f, 0f, MARK_SIDE.toFloat(), MARK_SIDE.toFloat(), Paint().apply { color = Color.RED })
        }
        val file = File(context.cacheDir, "face_orientation_$orientation.jpg")
        written.add(file)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "JPEG compression failed" } }
        bitmap.recycle()

        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.saveAttributes()
        return file
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) < 60 && Color.blue(pixel) < 60

    private fun isWhite(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200

    private enum class Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        // The centre of a MARK_SIDE block pushed into this corner.
        fun centreX(width: Int) = if (this == TOP_LEFT || this == BOTTOM_LEFT) MARK_SIDE / 2 else width - MARK_SIDE / 2

        fun centreY(height: Int) = if (this == TOP_LEFT || this == TOP_RIGHT) MARK_SIDE / 2 else height - MARK_SIDE / 2
    }

    private companion object {
        const val STORED_WIDTH = 200
        const val STORED_HEIGHT = 120
        const val MARK_SIDE = 60
    }
}
