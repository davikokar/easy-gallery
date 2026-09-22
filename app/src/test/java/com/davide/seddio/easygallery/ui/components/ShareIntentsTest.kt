package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.data.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareIntentsTest {

    @Test
    fun resolveShareMimeType_allImage_returnsImageWildcard() {
        assertEquals(
            "image/*",
            resolveShareMimeType(listOf(MediaType.IMAGE, MediaType.IMAGE))
        )
    }

    @Test
    fun resolveShareMimeType_allVideo_returnsVideoWildcard() {
        assertEquals(
            "video/*",
            resolveShareMimeType(listOf(MediaType.VIDEO, MediaType.VIDEO))
        )
    }

    @Test
    fun resolveShareMimeType_imageAndGif_returnsImageWildcard() {
        assertEquals(
            "image/*",
            resolveShareMimeType(listOf(MediaType.IMAGE, MediaType.GIF))
        )
    }

    @Test
    fun resolveShareMimeType_imageAndVideo_returnsAnyWildcard() {
        assertEquals(
            "*/*",
            resolveShareMimeType(listOf(MediaType.IMAGE, MediaType.VIDEO))
        )
    }

    @Test
    fun resolveShareMimeType_singleItem_returnsTypeWildcard() {
        assertEquals(
            "video/*",
            resolveShareMimeType(listOf(MediaType.VIDEO))
        )
    }

    @Test
    fun resolveShareMimeType_emptyCollection_returnsAnyWildcard() {
        assertEquals(
            "*/*",
            resolveShareMimeType(emptyList())
        )
    }
}
