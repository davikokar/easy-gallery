package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidMediaStoreVersionProviderTest {

    private fun ensureMediaStoreInitialized() {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `currentVersion matches MediaStore global version on API 28`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        ensureMediaStoreInitialized()

        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(context) } returns "v28"

            assertEquals(
                "v28",
                AndroidMediaStoreVersionProvider.currentVersion(context)
            )
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `currentVersion matches MediaStore global version on API 30 plus`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        ensureMediaStoreInitialized()

        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(context) } returns "v30"

            assertEquals(
                "v30",
                AndroidMediaStoreVersionProvider.currentVersion(context)
            )
        }
    }
}
