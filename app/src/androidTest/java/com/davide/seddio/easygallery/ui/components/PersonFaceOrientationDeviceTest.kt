package com.davide.seddio.easygallery.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import coil3.toBitmap
import com.davide.seddio.easygallery.data.FaceBox
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A box is in the upright frame (ADR-0022), so the tile's crop must be taken from the image as the viewer shows it. A
 * 200x120 JPEG with a red block in its stored top-left corner and EXIF orientation 6 is upright 120x200 with the block at
 * top-right; the box names that corner, and the production image loader must have rotated the pixels before the crop.
 */
@RunWith(AndroidJUnit4::class)
class PersonFaceOrientationDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "person_face_orientation.jpg")

    @After
    fun deleteFile() {
        file.delete()
    }

    @Test
    fun theCropOfAnOrientation6PhotoIsTakenFromTheUprightImage() {
        val bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(0f, 0f, 60f, 60f, Paint().apply { color = Color.RED })
        }
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        // Upright x 60..120, y 0..60 is the mark; this box centres on it and crops an 86 px square from (34, 0).
        val request = personFaceRequest(context, Uri.fromFile(file).toString(), FaceBox(0.55f, 0.05f, 0.95f, 0.25f))
        val result = runBlocking { SingletonImageLoader.get(context).execute(request) }

        val crop = (result as SuccessResult).image.toBitmap()
        assertEquals(86, crop.width)
        assertTrue("The mark is inside the crop", isRed(crop.getPixel(56, 30)))
        assertTrue("The crop's bottom-left is the white of the upright image", isWhite(crop.getPixel(10, 75)))
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) < 60 && Color.blue(pixel) < 60

    private fun isWhite(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200
}
