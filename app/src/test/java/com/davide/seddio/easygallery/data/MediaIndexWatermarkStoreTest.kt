package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexWatermarkStoreTest {

    @Test
    fun `room watermark store persists per-volume rows`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaIndexDatabase::class.java
        ).allowMainThreadQueries().build()

        val store = RoomMediaIndexWatermarkStore(database.mediaIndexDao())
        val primary = MediaIndexVolumeWatermark(version = "v3", generation = 11L, dateModifiedWatermarkSeconds = 700L)
        val external = MediaIndexVolumeWatermark(version = "v4", generation = null, dateModifiedWatermarkSeconds = 123L)

        store.write("external_primary", primary)
        store.write("external", external)

        assertEquals(primary, store.read("external_primary"))
        assertEquals(external, store.read("external"))

        val all = store.readAll()
        assertEquals(setOf("external_primary", "external"), all.keys)

        database.close()
    }

    @Test
    fun `room watermark store removes a single volume row`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaIndexDatabase::class.java
        ).allowMainThreadQueries().build()

        val store = RoomMediaIndexWatermarkStore(database.mediaIndexDao())
        val watermark = MediaIndexVolumeWatermark(
            version = "opaque|version|token",
            generation = 17L,
            dateModifiedWatermarkSeconds = 222L
        )
        val secondary = MediaIndexVolumeWatermark(
            version = "v-secondary",
            generation = 2L,
            dateModifiedWatermarkSeconds = 333L
        )

        store.write("external_primary", watermark)
        store.write("external", secondary)
        store.remove("external_primary")

        assertNull(store.read("external_primary"))
        assertEquals(secondary, store.read("external"))

        database.close()
    }
}
