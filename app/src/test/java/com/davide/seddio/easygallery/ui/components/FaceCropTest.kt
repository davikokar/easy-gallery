package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.data.FaceBox
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Fractions are binary (multiples of 1/16), so every pixel value below is exact in Float and Double.
class FaceCropTest {

    private fun crop(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int) =
        faceCropRect(FaceBox(left, top, right, bottom), width, height)

    @Test
    fun `the square is 1_8 times the box with the box centred in it`() {
        val rect = crop(0.375f, 0.375f, 0.625f, 0.625f, 1000, 1000)

        assertEquals(FaceCropRect(left = 275, top = 275, size = 450), rect)
        // 250 px of face in a 450 px square: 100 px of margin on each side, 40% of the face.
        assertEquals(1.8, rect.size / 250.0, 1e-9)
        assertEquals(100, 375 - rect.left)
        assertEquals(100, rect.right - 625)
    }

    @Test
    fun `a tall box sets the side from its height`() {
        // 100 px wide and 250 px tall in a 1600 by 1000 image.
        val rect = crop(0.25f, 0.25f, 0.3125f, 0.5f, 1600, 1000)

        assertEquals(FaceCropRect(left = 225, top = 150, size = 450), rect)
    }

    @Test
    fun `a wide box sets the side from its width`() {
        // 400 px wide and 100 px tall in a 1600 by 1600 image.
        val rect = crop(0.25f, 0.4375f, 0.5f, 0.5f, 1600, 1600)

        assertEquals(FaceCropRect(left = 240, top = 390, size = 720), rect)
    }

    @Test
    fun `a portrait image crops in its own pixels`() {
        // 150 by 125 px in a 600 by 1000 image: the fractions of each axis are taken against that axis.
        val rect = crop(0.375f, 0.4375f, 0.625f, 0.5625f, 600, 1000)

        assertEquals(FaceCropRect(left = 165, top = 365, size = 270), rect)
    }

    @Test
    fun `a box at the left edge is shifted right and keeps its size`() {
        assertEquals(FaceCropRect(0, 275, 450), crop(0f, 0.375f, 0.25f, 0.625f, 1000, 1000))
    }

    @Test
    fun `a box at the right edge is shifted left and keeps its size`() {
        assertEquals(FaceCropRect(550, 275, 450), crop(0.75f, 0.375f, 1f, 0.625f, 1000, 1000))
    }

    @Test
    fun `a box at the top edge is shifted down and keeps its size`() {
        assertEquals(FaceCropRect(275, 0, 450), crop(0.375f, 0f, 0.625f, 0.25f, 1000, 1000))
    }

    @Test
    fun `a box at the bottom edge is shifted up and keeps its size`() {
        assertEquals(FaceCropRect(275, 550, 450), crop(0.375f, 0.75f, 0.625f, 1f, 1000, 1000))
    }

    @Test
    fun `a box in each corner is shifted on both axes`() {
        assertEquals(FaceCropRect(0, 0, 450), crop(0f, 0f, 0.25f, 0.25f, 1000, 1000))
        assertEquals(FaceCropRect(550, 0, 450), crop(0.75f, 0f, 1f, 0.25f, 1000, 1000))
        assertEquals(FaceCropRect(0, 550, 450), crop(0f, 0.75f, 0.25f, 1f, 1000, 1000))
        assertEquals(FaceCropRect(550, 550, 450), crop(0.75f, 0.75f, 1f, 1f, 1000, 1000))
    }

    @Test
    fun `a box on an edge of a non-square image is shifted by that image's own size`() {
        // 2000 by 1000: 125 px wide, 250 px tall, against the right edge.
        assertEquals(FaceCropRect(1550, 150, 450), crop(0.9375f, 0.25f, 1f, 0.5f, 2000, 1000))
    }

    @Test
    fun `a box larger than the short side gets the largest square that fits, centred on the box`() {
        // The whole frame of a landscape image.
        assertEquals(FaceCropRect(100, 0, 800), crop(0f, 0f, 1f, 1f, 1000, 800))
        // The whole frame of a portrait image.
        assertEquals(FaceCropRect(0, 100, 800), crop(0f, 0f, 1f, 1f, 800, 1000))
        // A box whose margin does not fit a 400 px short side: centred on the box in the long dimension.
        assertEquals(FaceCropRect(300, 0, 400), crop(0.4375f, 0.125f, 0.5625f, 0.875f, 1000, 400))
    }

    @Test
    fun `a box larger than the short side is still shifted inside the long dimension`() {
        assertEquals(FaceCropRect(600, 0, 400), crop(0.75f, 0.125f, 1f, 0.875f, 1000, 400))
        assertEquals(FaceCropRect(0, 0, 400), crop(0f, 0.125f, 0.25f, 0.875f, 1000, 400))
    }

    @Test
    fun `a square as large as the image is the whole image`() {
        assertEquals(FaceCropRect(0, 0, 1000), crop(0f, 0f, 1f, 1f, 1000, 1000))
    }

    @Test
    fun `a one pixel image gives that pixel`() {
        assertEquals(FaceCropRect(0, 0, 1), crop(0f, 0f, 1f, 1f, 1, 1))
    }

    @Test
    fun `a tiny image is cropped to its short side`() {
        assertEquals(FaceCropRect(0, 0, 2), crop(0.25f, 0.25f, 0.75f, 0.75f, 2, 2))
        assertEquals(FaceCropRect(1, 0, 3), crop(0.5f, 0.25f, 1f, 0.75f, 4, 3))
    }

    @Test
    fun `a box so small the side rounds to nothing still gives one pixel`() {
        // 0.25 px of box in a 4 by 4 image: 0.45 px of side before rounding.
        assertEquals(FaceCropRect(2, 2, 1), crop(0.5f, 0.5f, 0.5625f, 0.5625f, 4, 4))
    }

    @Test
    fun `an image with no pixels is refused`() {
        val box = FaceBox(0f, 0f, 1f, 1f)

        assertThrows(IllegalArgumentException::class.java) { faceCropRect(box, 0, 100) }
        assertThrows(IllegalArgumentException::class.java) { faceCropRect(box, 100, 0) }
    }

    @Test
    fun `any box in any image gives a whole square inside the image`() {
        val random = Random(7)
        repeat(2_000) {
            val width = 1 + random.nextInt(4_000)
            val height = 1 + random.nextInt(4_000)
            val left = random.nextFloat() * 0.95f
            val top = random.nextFloat() * 0.95f
            val right = (left + 0.001f + random.nextFloat() * (1f - left - 0.001f)).coerceAtMost(1f)
            val bottom = (top + 0.001f + random.nextFloat() * (1f - top - 0.001f)).coerceAtMost(1f)
            val box = FaceBox(left, top, right, bottom)

            val rect = faceCropRect(box, width, height)

            assertTrue(rect.size >= 1)
            assertTrue(rect.left >= 0 && rect.top >= 0)
            assertTrue(rect.right <= width && rect.bottom <= height)
            assertTrue(rect.size <= minOf(width, height))
        }
    }
}
