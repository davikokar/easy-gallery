package com.davide.seddio.easygallery.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MediaDurationTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setup() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun formatMediaDuration_zeroDuration_returnsZeroMinutesAndSeconds() {
        assertEquals("0:00", formatMediaDuration(0L))
    }

    @Test
    fun formatMediaDuration_subMinute_returnsSecondsOnly() {
        assertEquals("0:59", formatMediaDuration(59_999L))
    }

    @Test
    fun formatMediaDuration_exactlyOneMinute_returnsOneMinute() {
        assertEquals("1:00", formatMediaDuration(60_000L))
    }

    @Test
    fun formatMediaDuration_subHour_returnsMinutesAndSeconds() {
        assertEquals("59:59", formatMediaDuration(3_599_999L))
    }

    @Test
    fun formatMediaDuration_exactlyOneHour_returnsHoursMinutesAndSeconds() {
        assertEquals("1:00:00", formatMediaDuration(3_600_000L))
    }

    @Test
    fun formatMediaDuration_overOneHour_returnsHoursMinutesAndSeconds() {
        assertEquals("2:03:04", formatMediaDuration(7_384_000L))
    }

    @Test
    fun formatMediaDuration_negativeOrUnset_returnsZeroMinutesAndSeconds() {
        assertEquals("0:00", formatMediaDuration(-1L))
        assertEquals("0:00", formatMediaDuration(Long.MIN_VALUE))
    }

    @Test
    fun formatMediaDuration_isStableAcrossDifferentDefaultLocales() {
        Locale.setDefault(Locale("ar", "EG"))
        val arabic = formatMediaDuration(3_661_000L)

        Locale.setDefault(Locale.ITALY)
        val italian = formatMediaDuration(3_661_000L)

        assertEquals("1:01:01", arabic)
        assertEquals("1:01:01", italian)
        assertFalse(arabic.any { it.isLetter() })
        assertFalse(italian.contains(','))
    }

    @Test
    fun playbackProgressFraction_unknownZeroOrNegativeTotalDuration_returnsZero() {
        assertEquals(0f, playbackProgressFraction(1_000L, 0L), 0f)
        assertEquals(0f, playbackProgressFraction(1_000L, -1L), 0f)
        assertEquals(0f, playbackProgressFraction(-1_000L, 0L), 0f)
    }

    @Test
    fun playbackProgressFraction_startPosition_returnsZero() {
        assertEquals(0f, playbackProgressFraction(0L, 1_000L), 0f)
    }

    @Test
    fun playbackProgressFraction_midpoint_returnsHalf() {
        assertEquals(0.5f, playbackProgressFraction(500L, 1_000L), 0f)
    }

    @Test
    fun playbackProgressFraction_exactEnd_returnsOne() {
        assertEquals(1f, playbackProgressFraction(1_000L, 1_000L), 0f)
    }

    @Test
    fun playbackProgressFraction_positionBeyondDuration_clampsToOne() {
        assertEquals(1f, playbackProgressFraction(1_500L, 1_000L), 0f)
    }
}
