package com.davide.seddio.easygallery.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerFilmstripRulesTest {

    @Test
    fun `scale is at its maximum on the centred cell`() {
        assertEquals(FILMSTRIP_MAX_SCALE, filmstripItemScale(0f), 0f)
    }

    @Test
    fun `scale is 1 from one page away and beyond`() {
        assertEquals(1f, filmstripItemScale(1f), 0f)
        assertEquals(1f, filmstripItemScale(1.5f), 0f)
        assertEquals(1f, filmstripItemScale(40f), 0f)
    }

    @Test
    fun `scale is symmetric either side of the centre`() {
        listOf(0.1f, 0.25f, 0.5f, 0.9f, 1f, 3f).forEach { distance ->
            assertEquals(filmstripItemScale(distance), filmstripItemScale(-distance), 0f)
        }
    }

    @Test
    fun `scale falls monotonically from the centre to one page away`() {
        val scales = (0..10).map { filmstripItemScale(it / 10f) }

        scales.zipWithNext().forEach { (nearer, farther) -> assertTrue(nearer >= farther) }
        assertTrue(scales.first() > scales.last())
    }

    @Test
    fun `scale never leaves the range from 1 to the maximum`() {
        listOf(-100f, -2f, -0.5f, 0f, 0.5f, 2f, 100f).forEach { distance ->
            val scale = filmstripItemScale(distance)
            assertTrue("scale $scale at $distance", scale in 1f..FILMSTRIP_MAX_SCALE)
        }
    }

    @Test
    fun `selection fraction is 1 at the centre and 0 one page away`() {
        assertEquals(1f, filmstripSelectionFraction(0f), 0f)
        assertEquals(0.5f, filmstripSelectionFraction(0.5f), 0f)
        assertEquals(0f, filmstripSelectionFraction(-1f), 0f)
    }

    @Test
    fun `side inset is half of the room left around one cell`() {
        assertEquals(158f, filmstripSideInset(stripWidth = 360f, itemWidth = 44f), 0f)
    }

    @Test
    fun `side inset is zero when the strip is exactly one cell wide`() {
        assertEquals(0f, filmstripSideInset(stripWidth = 44f, itemWidth = 44f), 0f)
    }

    @Test
    fun `side inset is clamped at zero when the strip is narrower than a cell`() {
        assertEquals(0f, filmstripSideInset(stripWidth = 20f, itemWidth = 44f), 0f)
        assertEquals(0f, filmstripSideInset(stripWidth = 0f, itemWidth = 44f), 0f)
    }

    @Test
    fun `equal pages need no sync whoever is driving`() {
        assertEquals(FilmstripSync.NONE, filmstripSync(mainPage = 4, stripPage = 4, stripUserDriven = false))
        assertEquals(FilmstripSync.NONE, filmstripSync(mainPage = 4, stripPage = 4, stripUserDriven = true))
    }

    @Test
    fun `the strip follows the main pager when the user is not driving the strip`() {
        assertEquals(FilmstripSync.STRIP_FOLLOWS_MAIN, filmstripSync(mainPage = 5, stripPage = 2, stripUserDriven = false))
        assertEquals(FilmstripSync.STRIP_FOLLOWS_MAIN, filmstripSync(mainPage = 2, stripPage = 5, stripUserDriven = false))
    }

    @Test
    fun `the main pager follows the strip while the user is driving the strip`() {
        assertEquals(FilmstripSync.MAIN_FOLLOWS_STRIP, filmstripSync(mainPage = 5, stripPage = 2, stripUserDriven = true))
        assertEquals(FilmstripSync.MAIN_FOLLOWS_STRIP, filmstripSync(mainPage = 2, stripPage = 5, stripUserDriven = true))
    }

    @Test
    fun `a given state never asks for both directions and the direction depends only on who is driving`() {
        for (main in 0..3) {
            for (strip in 0..3) {
                val idle = filmstripSync(main, strip, stripUserDriven = false)
                val driven = filmstripSync(main, strip, stripUserDriven = true)

                assertTrue(idle != FilmstripSync.MAIN_FOLLOWS_STRIP)
                assertTrue(driven != FilmstripSync.STRIP_FOLLOWS_MAIN)
                if (main == strip) {
                    assertEquals(FilmstripSync.NONE, idle)
                    assertEquals(FilmstripSync.NONE, driven)
                }
            }
        }
    }

    @Test
    fun `the filmstrip is hidden for no items and for one item`() {
        assertEquals(false, isFilmstripShown(0))
        assertEquals(false, isFilmstripShown(1))
    }

    @Test
    fun `the filmstrip is shown from two items`() {
        assertEquals(true, isFilmstripShown(2))
        assertEquals(true, isFilmstripShown(10_000))
    }
}
