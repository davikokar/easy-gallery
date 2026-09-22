package com.davide.seddio.easygallery.ui

import android.net.Uri
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MediaViewerStateTest {

    private fun mediaItem(uri: Uri, type: MediaType = MediaType.IMAGE) = MediaItem(
        uri = uri,
        name = "file.jpg",
        dateAdded = 1000L,
        dateModified = 1000L,
        size = 100L,
        type = type,
        bucketName = "Pictures",
        folderPath = "/storage/emulated/0/Pictures"
    )

    @Test
    fun `record no-ops when no media is selected`() {
        val state = MediaViewerState()
        val uri = mockk<Uri>(relaxed = true)

        state.record(uri, positionMs = 1234L, playWhenReady = true)

        assertNull(state.consume(uri))
    }

    @Test
    fun `consume returns null when no entry exists`() {
        val state = MediaViewerState()
        val uri = mockk<Uri>(relaxed = true)

        assertNull(state.consume(uri))
    }

    @Test
    fun `consume with non-matching uri returns null and preserves stored entry`() {
        val state = MediaViewerState()
        val selectedUri = mockk<Uri>(relaxed = true)
        val storedUri = mockk<Uri>(relaxed = true)
        val otherUri = mockk<Uri>(relaxed = true)
        val selected = mediaItem(selectedUri)

        state.open(selected, listOf(selected))
        state.record(storedUri, positionMs = 555L, playWhenReady = false)

        assertNull(state.consume(otherUri))

        val consumed = state.consume(storedUri)
        assertNotNull(consumed)
        assertSame(storedUri, consumed?.uri)
        assertEquals(555L, consumed?.positionMs)
        assertFalse(consumed?.playWhenReady ?: true)
    }

    @Test
    fun `consume with matching uri returns entry and clears it one-shot`() {
        val state = MediaViewerState()
        val selectedUri = mockk<Uri>(relaxed = true)
        val storedUri = mockk<Uri>(relaxed = true)
        val selected = mediaItem(selectedUri)

        state.open(selected, listOf(selected))
        state.record(storedUri, positionMs = 789L, playWhenReady = true)

        val firstConsume = state.consume(storedUri)
        assertNotNull(firstConsume)
        assertSame(storedUri, firstConsume?.uri)
        assertEquals(789L, firstConsume?.positionMs)
        assertEquals(true, firstConsume?.playWhenReady)

        assertNull(state.consume(storedUri))
    }

    @Test
    fun `open clears any stored entry`() {
        val state = MediaViewerState()
        val firstUri = mockk<Uri>(relaxed = true)
        val secondUri = mockk<Uri>(relaxed = true)
        val storedUri = mockk<Uri>(relaxed = true)
        val first = mediaItem(firstUri)
        val second = mediaItem(secondUri)

        state.open(first, listOf(first, second))
        state.record(storedUri, positionMs = 42L, playWhenReady = true)

        state.open(second, listOf(first, second))

        assertNull(state.consume(storedUri))
    }

    @Test
    fun `close clears any stored entry`() {
        val state = MediaViewerState()
        val selectedUri = mockk<Uri>(relaxed = true)
        val storedUri = mockk<Uri>(relaxed = true)
        val selected = mediaItem(selectedUri)

        state.open(selected, listOf(selected))
        state.record(storedUri, positionMs = 900L, playWhenReady = false)

        state.close()

        assertNull(state.consume(storedUri))
    }

    @Test
    fun `setCurrent clears stored entry when uri differs from current item`() {
        val state = MediaViewerState()
        val firstUri = mockk<Uri>(relaxed = true)
        val secondUri = mockk<Uri>(relaxed = true)
        val first = mediaItem(firstUri)
        val second = mediaItem(secondUri)

        state.open(first, listOf(first, second))
        state.record(firstUri, positionMs = 111L, playWhenReady = true)

        state.setCurrent(second)

        assertNull(state.consume(firstUri))
    }

    @Test
    fun `setCurrent preserves stored entry when uri matches current item`() {
        val state = MediaViewerState()
        val uri = mockk<Uri>(relaxed = true)
        val current = mediaItem(uri)
        val sameUriDifferentItem = mediaItem(uri, type = MediaType.VIDEO)

        state.open(current, listOf(current))
        state.record(uri, positionMs = 222L, playWhenReady = false)

        state.setCurrent(sameUriDifferentItem)

        val consumed = state.consume(uri)
        assertNotNull(consumed)
        assertSame(uri, consumed?.uri)
        assertEquals(222L, consumed?.positionMs)
        assertFalse(consumed?.playWhenReady ?: true)
    }
}
