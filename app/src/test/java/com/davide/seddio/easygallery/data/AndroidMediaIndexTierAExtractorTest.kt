package com.davide.seddio.easygallery.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class AndroidMediaIndexTierAExtractorTest {

    @Test
    fun `extract image reads tier a metadata and coordinates from one exif read`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val imageFile = createJpegWithExif()
        every { resolver.openInputStream(any()) } answers { imageFile.inputStream() }

        val extractor = AndroidMediaIndexTierAExtractor(
            context = mockk(relaxed = true),
            contentResolver = resolver
        )

        val extracted = extractor.extract(mediaItem("content://media/external/images/media/1", MediaType.IMAGE))

        val expectedDate = LocalDateTime.of(2026, 7, 3, 10, 11, 12)
            .toInstant(ZoneOffset.ofHours(2))
            .toEpochMilli()
        assertEquals(expectedDate, extracted.capturedDateTakenEpochMillis)
        assertNotNull(extracted.capturedLatitude)
        assertNotNull(extracted.capturedLongitude)
        assertEquals(43.7696, extracted.capturedLatitude!!, 0.000001)
        assertEquals(11.2558, extracted.capturedLongitude!!, 0.000001)
        assertEquals(1, extracted.capturedWidth)
        assertEquals(1, extracted.capturedHeight)
    }

    @Test
    fun `extract image returns absent metadata on permission denial`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.openInputStream(any()) } throws SecurityException("denied")

        val extractor = AndroidMediaIndexTierAExtractor(
            context = mockk(relaxed = true),
            contentResolver = resolver
        )

        val extracted = extractor.extract(mediaItem("content://media/external/images/media/2", MediaType.IMAGE))

        assertEmptyExtraction(extracted)
    }

    @Test
    fun `extract image returns absent metadata when file is missing`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.openInputStream(any()) } returns null

        val extractor = AndroidMediaIndexTierAExtractor(
            context = mockk(relaxed = true),
            contentResolver = resolver
        )

        val extracted = extractor.extract(mediaItem("content://media/external/images/media/3", MediaType.IMAGE))

        assertEmptyExtraction(extracted)
    }

    @Test
    fun `extract image returns absent metadata on corrupt exif header`() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.openInputStream(any()) } throws IllegalStateException("corrupt")

        val extractor = AndroidMediaIndexTierAExtractor(
            context = mockk(relaxed = true),
            contentResolver = resolver
        )

        val extracted = extractor.extract(mediaItem("content://media/external/images/media/4", MediaType.IMAGE))

        assertEmptyExtraction(extracted)
    }

    private fun createJpegWithExif(): File {
        val file = File.createTempFile("tier-a-exif", ".jpg")
        file.outputStream().use { output ->
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.JPEG, 100, output)
        }

        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:07:03 10:11:12")
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+02:00")
        exif.setLatLong(43.7696, 11.2558)
        exif.saveAttributes()

        return file
    }

    private fun mediaItem(uri: String, type: MediaType): MediaItem {
        return MediaItem(
            uri = Uri.parse(uri),
            name = "item",
            dateAdded = 1L,
            dateModified = 2L,
            size = 3L,
            type = type,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera",
            duration = null
        )
    }

    private fun assertEmptyExtraction(extraction: MediaIndexTierAExtraction) {
        assertNull(extraction.capturedDateTakenEpochMillis)
        assertNull(extraction.capturedLatitude)
        assertNull(extraction.capturedLongitude)
        assertNull(extraction.capturedWidth)
        assertNull(extraction.capturedHeight)
    }
}
