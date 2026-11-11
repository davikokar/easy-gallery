package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIndexWatermarkInMemoryStoreTest {

    @Test
    fun `in-memory watermark store reads and writes by volume`() {
        val store = InMemoryMediaIndexWatermarkStore()

        val primary = MediaIndexVolumeWatermark(
            version = "v1",
            generation = 10L,
            dateModifiedWatermarkSeconds = null
        )
        val external = MediaIndexVolumeWatermark(
            version = "v2",
            generation = null,
            dateModifiedWatermarkSeconds = 999L
        )

        store.write("external_primary", primary)
        store.write("external", external)

        assertEquals(primary, store.read("external_primary"))
        assertEquals(external, store.read("external"))

        store.remove("external")
        assertNull(store.read("external"))
        assertEquals(primary, store.read("external_primary"))
    }
}
