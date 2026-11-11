package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidMediaStoreVolumeVersionProviderTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `api 28 returns global version for primary volume`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(context) } returns "v28-primary"
            val provider = AndroidMediaStoreVolumeVersionProvider(context)

            assertEquals(
                "v28-primary",
                provider.versionForVolume(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            )
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `api 28 returns null for non-primary volume instead of calling missing overload`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val provider = AndroidMediaStoreVolumeVersionProvider(context)

        assertNull(provider.versionForVolume("external"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `api 29 uses per-volume overload for non-primary volumes`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(context, "external") } returns "v29-external"
            val provider = AndroidMediaStoreVolumeVersionProvider(context)

            assertEquals(
                "v29-external",
                provider.versionForVolume("external")
            )
        }
    }
}
