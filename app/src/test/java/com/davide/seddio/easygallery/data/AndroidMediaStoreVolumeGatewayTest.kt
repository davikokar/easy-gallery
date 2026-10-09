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
class AndroidMediaStoreVolumeGatewayTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `global version delegates to MediaStore on API 28`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(context) } returns "v28"

            assertEquals(
                "v28",
                AndroidMediaStoreVolumeGateway.version(context)
            )
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `api 29 per-volume delegates to MediaStore`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY
        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(context, volumeName) } returns "v29-primary"
            every { MediaStore.getExternalVolumeNames(context) } returns linkedSetOf("external_primary", "external")

            assertEquals(
                "v29-primary",
                AndroidMediaStoreVolumeGateway.version(context, volumeName)
            )
            assertEquals(
                linkedSetOf("external_primary", "external"),
                AndroidMediaStoreVolumeGateway.externalVolumeNames(context)
            )
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `api 30 generation and recent volumes delegate to MediaStore`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY
        mockkStatic(MediaStore::class) {
            every { MediaStore.getGeneration(context, volumeName) } returns 77L
            every { MediaStore.getRecentExternalVolumeNames(context) } returns linkedSetOf("external_primary", "0123-4567")

            assertEquals(
                77L,
                AndroidMediaStoreVolumeGateway.generation(context, volumeName)
            )
            assertEquals(
                linkedSetOf("external_primary", "0123-4567"),
                AndroidMediaStoreVolumeGateway.recentExternalVolumeNames(context)
            )
        }
    }
}
