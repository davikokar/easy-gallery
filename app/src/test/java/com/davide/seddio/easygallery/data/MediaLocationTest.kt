package com.davide.seddio.easygallery.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MediaLocationTest {

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
    fun `parseIso6709Location parses latitude and longitude with trailing slash`() {
        val parsed = parseIso6709Location("+37.4220-122.0840/")

        assertNotNull(parsed)
        assertEquals(37.4220, parsed?.latitude ?: 0.0, 0.0)
        assertEquals(-122.0840, parsed?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `parseIso6709Location ignores trailing altitude component`() {
        val parsed = parseIso6709Location("+37.4220-122.0840+009.000/")

        assertNotNull(parsed)
        assertEquals(37.4220, parsed?.latitude ?: 0.0, 0.0)
        assertEquals(-122.0840, parsed?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `parseIso6709Location parses southern and eastern hemisphere coordinates`() {
        val parsed = parseIso6709Location("-33.8688+151.2093/")

        assertNotNull(parsed)
        assertEquals(-33.8688, parsed?.latitude ?: 0.0, 0.0)
        assertEquals(151.2093, parsed?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `parseIso6709Location parses coordinates without trailing slash`() {
        val parsed = parseIso6709Location("+37.4220-122.0840")

        assertNotNull(parsed)
        assertEquals(37.4220, parsed?.latitude ?: 0.0, 0.0)
        assertEquals(-122.0840, parsed?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `parseIso6709Location returns null for null empty and blank input`() {
        assertNull(parseIso6709Location(null))
        assertNull(parseIso6709Location(""))
        assertNull(parseIso6709Location("   \t\n  "))
    }

    @Test
    fun `parseIso6709Location returns null for arbitrary text`() {
        assertNull(parseIso6709Location("not-a-coordinate"))
    }

    @Test
    fun `parseIso6709Location returns null when longitude component is missing`() {
        assertNull(parseIso6709Location("+37.4220/"))
    }

    @Test
    fun `parseIso6709Location returns null for unsupported fixed width DDMM variant`() {
        assertNull(parseIso6709Location("+3726-12205/"))
    }

    @Test
    fun `parseIso6709Location returns null when positive latitude is missing leading plus`() {
        assertNull(parseIso6709Location("37.4220-122.0840/"))
    }

    @Test
    fun `parseIso6709Location returns null when latitude is out of range`() {
        assertNull(parseIso6709Location("+90.0001+10.0000/"))
    }

    @Test
    fun `parseIso6709Location returns null when longitude is out of range`() {
        assertNull(parseIso6709Location("+45.0000+180.0001/"))
    }

    @Test
    fun `parseIso6709Location treats null island as absent`() {
        assertNull(parseIso6709Location("+0.0+0.0/"))
    }

    @Test
    fun `formatCoordinatesForDisplay always uses dot decimal separator and six decimals`() {
        Locale.setDefault(Locale.ITALY)

        val text = formatCoordinatesForDisplay(MediaCoordinates(37.422, -122.084))

        assertEquals("37.422000, -122.084000", text)
        assertFalse(text.contains("37,422000"))
        assertFalse(text.contains("-122,084000"))
    }

    @Test
    fun `buildGeoUri uses dot decimals and URL encodes the label`() {
        Locale.setDefault(Locale.GERMANY)

        val uri = buildGeoUri(MediaCoordinates(37.422, -122.084), "IMG 123 & #1")

        assertTrue(uri.startsWith("geo:37.422000,-122.084000?q=37.422000,-122.084000("))
        assertTrue(uri.endsWith("IMG+123+%26+%231)"))
        assertFalse(uri.contains("37,422000"))
        assertFalse(uri.contains("-122,084000"))
    }

    @Test
    fun `buildGoogleMapsUrl uses dot decimals and stable maps URL shape across locales`() {
        Locale.setDefault(Locale.ITALY)

        val url = buildGoogleMapsUrl(MediaCoordinates(37.422, -122.084), "IMG 123 & #1")

        assertTrue(url.startsWith("https://www.google.com/maps/search/?api=1&query="))
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=37.422000%2C-122.084000+%28IMG+123+%26+%231%29",
            url
        )
        assertFalse(url.contains("37,422000"))
        assertFalse(url.contains("-122,084000"))
    }

    @Test
    fun `isValidMediaCoordinates rejects NaN values`() {
        assertFalse(isValidMediaCoordinates(Double.NaN, 12.0))
        assertFalse(isValidMediaCoordinates(12.0, Double.NaN))
    }

    @Test
    fun `isValidMediaCoordinates rejects infinite values`() {
        assertFalse(isValidMediaCoordinates(Double.POSITIVE_INFINITY, 12.0))
        assertFalse(isValidMediaCoordinates(12.0, Double.NEGATIVE_INFINITY))
    }
}