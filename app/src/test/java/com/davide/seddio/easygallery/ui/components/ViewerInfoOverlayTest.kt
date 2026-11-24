package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.data.ResolvedDateTaken
import com.davide.seddio.easygallery.ui.theme.BrandBlueLight
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow

class ViewerInfoOverlayTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val instant = Instant.parse("2024-01-05T15:04:00Z").toEpochMilli()
    private lateinit var previousDefault: Locale

    @Before
    fun saveDefaultLocale() {
        previousDefault = Locale.getDefault()
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(previousDefault)
    }

    @Test
    fun `date time uses the medium date and short time of the given locale`() {
        val us = formatMediaDateTime(instant, Locale.US, utc)
        val germany = formatMediaDateTime(instant, Locale.GERMANY, utc)

        assertTrue(us, us.contains("Jan 5, 2024") && us.contains("3:04"))
        assertTrue(germany, germany.contains("05.01.2024") && germany.contains("15:04"))
    }

    @Test
    fun `date time follows the passed locale and not the process default`() {
        Locale.setDefault(Locale.GERMANY)

        val us = formatMediaDateTime(instant, Locale.US, utc)

        assertTrue(us, us.contains("Jan"))
        assertNotEquals(formatMediaDateTime(instant, Locale.GERMANY, utc), us)
    }

    @Test
    fun `date time is shown in the given time zone`() {
        val tokyo = formatMediaDateTime(instant, Locale.US, TimeZone.getTimeZone("Asia/Tokyo"))

        assertTrue(tokyo, tokyo.contains("Jan 6, 2024") && tokyo.contains("12:04"))
    }

    @Test
    fun `a positive capture date is displayable and keeps its kind`() {
        val resolved = ResolvedDateTaken(1_000L, isCaptureDate = true)

        assertEquals(resolved, displayableDateTaken(resolved))
    }

    @Test
    fun `a positive fallback date is displayable and stays a fallback`() {
        val resolved = ResolvedDateTaken(1_000L, isCaptureDate = false)

        assertEquals(resolved, displayableDateTaken(resolved))
    }

    @Test
    fun `zero and negative instants are not displayable whichever kind they are`() {
        assertNull(displayableDateTaken(ResolvedDateTaken(0L, isCaptureDate = true)))
        assertNull(displayableDateTaken(ResolvedDateTaken(-1L, isCaptureDate = true)))
        assertNull(displayableDateTaken(ResolvedDateTaken(0L, isCaptureDate = false)))
        assertNull(displayableDateTaken(ResolvedDateTaken(-1L, isCaptureDate = false)))
    }

    @Test
    fun `scrim keeps text legible even over a pure white image`() {
        val background = 1f - VIEWER_INFO_SCRIM_ALPHA

        val whiteText = contrast(1f, 1f, 1f, background)
        val link = contrast(BrandBlueLight.red, BrandBlueLight.green, BrandBlueLight.blue, background)

        assertTrue("white text $whiteText", whiteText >= 4.5)
        assertTrue("link $link", link >= 4.5)
    }

    @Test
    fun `scrim is translucent so the image still shows through`() {
        assertTrue(VIEWER_INFO_SCRIM_ALPHA > 0f)
        assertFalse(VIEWER_INFO_SCRIM_ALPHA >= 1f)
    }

    // WCAG contrast of a colour over a grey of the given sRGB channel value (0..1).
    private fun contrast(r: Float, g: Float, b: Float, greyBackground: Float): Double {
        val foreground = luminance(r, g, b)
        val background = luminance(greyBackground, greyBackground, greyBackground)
        return (maxOf(foreground, background) + 0.05) / (minOf(foreground, background) + 0.05)
    }

    private fun luminance(r: Float, g: Float, b: Float): Double =
        0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
