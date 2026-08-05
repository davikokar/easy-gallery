package com.davide.seddio.easygallery.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomableImageMathTest {

    @Test
    fun calculateNewOffset_atScaleOne_returnsZeroOffset() {
        val currentOffset = Offset(10f, 10f)
        val pan = Offset(5f, 5f)
        val scale = 1f
        
        val newOffset = calculateNewOffset(currentOffset, pan, scale)
        
        assertEquals(Offset.Zero, newOffset)
    }

    @Test
    fun calculateNewOffset_atScaleTwo_dampensPanByHalf() {
        val currentOffset = Offset(10f, 10f)
        val pan = Offset(10f, 10f)
        val scale = 2f
        
        // (10, 10) + (10 / 2, 10 / 2) = (15, 15)
        val newOffset = calculateNewOffset(currentOffset, pan, scale)
        
        assertEquals(Offset(15f, 15f), newOffset)
    }

    @Test
    fun calculateNewOffset_atHighScale_dampensPanSignificantly() {
        val currentOffset = Offset(100f, 100f)
        val pan = Offset(100f, 100f)
        val scale = 10f
        
        // (100, 100) + (100 / 10, 100 / 10) = (110, 110)
        val newOffset = calculateNewOffset(currentOffset, pan, scale)
        
        assertEquals(Offset(110f, 110f), newOffset)
    }

    @Test
    fun calculateNewOffset_atMaxScale_dampensPanEvenMore() {
        val currentOffset = Offset(0f, 0f)
        val pan = Offset(30f, 30f)
        val scale = 30f
        
        // (0, 0) + (30 / 30, 30 / 30) = (1, 1)
        val newOffset = calculateNewOffset(currentOffset, pan, scale)
        
        assertEquals(Offset(1f, 1f), newOffset)
    }
}
