package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.data.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperIntentsTest {

    @Test
    fun supportsWallpaper_image_returnsTrue() {
        assertTrue(supportsWallpaper(MediaType.IMAGE))
    }

    @Test
    fun supportsWallpaper_video_returnsFalse() {
        assertFalse(supportsWallpaper(MediaType.VIDEO))
    }

    @Test
    fun supportsWallpaper_gif_returnsFalse() {
        assertFalse(supportsWallpaper(MediaType.GIF))
    }

    @Test
    fun supportsWallpaper_isExhaustiveAcrossAllMediaTypes() {
        val expected = mapOf(
            MediaType.IMAGE to true,
            MediaType.VIDEO to false,
            MediaType.GIF to false
        )

        assertEquals(
            "A new MediaType was added; update wallpaper support policy explicitly.",
            expected.keys,
            MediaType.entries.toSet()
        )

        for (type in MediaType.entries) {
            assertEquals(
                "Unexpected wallpaper support policy for MediaType $type",
                expected.getValue(type),
                supportsWallpaper(type)
            )
        }
    }
}
