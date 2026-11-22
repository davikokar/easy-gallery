package com.davide.seddio.easygallery.ui.components

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Precision
import com.davide.seddio.easygallery.data.FaceBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class PersonFaceRequestTest {

    private val box = FaceBox(0.25f, 0.25f, 0.5f, 0.5f)
    private val uri = "content://media/external/images/media/42"

    private fun request(mediaUri: String = uri, faceBox: FaceBox = box) =
        personFaceRequest(ApplicationProvider.getApplicationContext(), mediaUri, faceBox)

    @Test
    fun `a face is never written to the disk cache`() {
        assertEquals(CachePolicy.DISABLED, request().diskCachePolicy)
    }

    @Test
    fun `the request loads the anchor's image through one crop transformation without hardware bitmaps`() {
        val request = request()

        assertEquals(uri, request.data)
        assertEquals(1, request.transformations.size)
        assertTrue(request.transformations.single() is FaceCropTransformation)
        assertFalse(request.allowHardware)
    }

    @Test
    fun `a source smaller than the decode cap is never upscaled to it`() {
        assertEquals(Precision.INEXACT, request().precision)
    }

    @Test
    fun `two faces of one photo do not share a memory cache entry`() {
        val other = FaceBox(0.5f, 0.25f, 0.75f, 0.5f)

        assertNotEquals(FaceCropTransformation(box).cacheKey, FaceCropTransformation(other).cacheKey)
        assertEquals(FaceCropTransformation(box).cacheKey, FaceCropTransformation(box).cacheKey)
    }
}
