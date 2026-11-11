package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidMediaStoreVolumeSnapshotProviderBoundaryTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `api 28 snapshot via real gateway observes only primary volume`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val provider = AndroidMediaStoreVolumeSnapshotProvider(context)

        val snapshot = provider.snapshot(28)

        assertEquals(MediaStoreDeltaApiBranch.API_28, snapshot.apiBranch)
        assertEquals(setOf("external_primary"), snapshot.presentVolumes)
        assertTrue(snapshot.observedVolumes.all { it.volumeName == "external_primary" })
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `api 29 snapshot via real gateway keeps observed volumes within present set`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        mockkStatic(android.provider.MediaStore::class) {
            every { android.provider.MediaStore.getExternalVolumeNames(context) } returns linkedSetOf("external_primary", "external")
            every { android.provider.MediaStore.getVersion(context, "external_primary") } returns "v29-primary"
            every { android.provider.MediaStore.getVersion(context, "external") } returns "v29-external"
            val provider = AndroidMediaStoreVolumeSnapshotProvider(context)

            val snapshot = provider.snapshot(29)

            assertEquals(MediaStoreDeltaApiBranch.API_29, snapshot.apiBranch)
            assertTrue(snapshot.observedVolumes.all { it.volumeName in snapshot.presentVolumes })
            assertTrue(snapshot.observedVolumes.all { it.generation == null })
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `api 30 snapshot via real gateway includes generation for observed volumes`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        mockkStatic(android.provider.MediaStore::class) {
            every { android.provider.MediaStore.getExternalVolumeNames(context) } returns linkedSetOf("external_primary", "external")
            every { android.provider.MediaStore.getRecentExternalVolumeNames(context) } returns linkedSetOf("external_primary", "external", "0123-4567")
            every { android.provider.MediaStore.getVersion(context, "external_primary") } returns "v30-primary"
            every { android.provider.MediaStore.getVersion(context, "external") } returns "v30-external"
            every { android.provider.MediaStore.getGeneration(context, "external_primary") } returns 88L
            every { android.provider.MediaStore.getGeneration(context, "external") } returns 99L
            val provider = AndroidMediaStoreVolumeSnapshotProvider(context)

            val snapshot = provider.snapshot(30)

            assertEquals(MediaStoreDeltaApiBranch.API_30_PLUS, snapshot.apiBranch)
            assertTrue(snapshot.observedVolumes.all { it.volumeName in snapshot.presentVolumes })
            assertTrue(snapshot.observedVolumes.all { it.generation != null })
            assertEquals(
                snapshot.knownButUnavailableVolumes,
                snapshot.knownButUnavailableVolumes - snapshot.presentVolumes
            )
        }
    }
}
