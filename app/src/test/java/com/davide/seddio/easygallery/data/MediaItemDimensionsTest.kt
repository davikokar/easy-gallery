package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemDimensionsTest {

    @Test
    fun `unrotated dimensions are returned as stored`() {
        assertEquals(4000 to 3000, displayedDimensions(4000, 3000, 0))
        assertEquals(4000 to 3000, displayedDimensions(4000, 3000, 180))
    }

    @Test
    fun `a quarter turn swaps width and height`() {
        assertEquals(3000 to 4000, displayedDimensions(4000, 3000, 90))
        assertEquals(3000 to 4000, displayedDimensions(4000, 3000, 270))
    }

    @Test
    fun `a missing orientation counts as zero`() {
        assertEquals(4000 to 3000, displayedDimensions(4000, 3000, null))
    }

    @Test
    fun `orientation is normalised before the swap`() {
        assertEquals(3000 to 4000, displayedDimensions(4000, 3000, 450))
        assertEquals(3000 to 4000, displayedDimensions(4000, 3000, -90))
        assertEquals(4000 to 3000, displayedDimensions(4000, 3000, 360))
    }

    @Test
    fun `an off axis orientation is not a swap`() {
        assertEquals(4000 to 3000, displayedDimensions(4000, 3000, 45))
    }

    @Test
    fun `a missing width or height gives null`() {
        assertNull(displayedDimensions(null, 3000, 0))
        assertNull(displayedDimensions(4000, null, 90))
        assertNull(displayedDimensions(null, null, null))
    }

    @Test
    fun `a width or height that is not positive gives null`() {
        assertNull(displayedDimensions(0, 3000, 0))
        assertNull(displayedDimensions(4000, 0, 90))
        assertNull(displayedDimensions(-1, 3000, 0))
        assertNull(displayedDimensions(4000, -5, 0))
    }

    @Test
    fun `a square stays square under rotation`() {
        assertEquals(2000 to 2000, displayedDimensions(2000, 2000, 90))
    }
}
