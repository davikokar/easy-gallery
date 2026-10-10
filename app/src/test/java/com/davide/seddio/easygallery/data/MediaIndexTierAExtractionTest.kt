package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class MediaIndexTierAExtractionTest {

    @Test
    fun `parseExifDateTakenEpochMillis interprets zone-less DateTimeOriginal in supplied local zone`() {
        val zone = ZoneId.of("Europe/Rome")
        val raw = "2026:07:03 00:30:00"

        val parsed = parseExifDateTakenEpochMillis(
            dateTimeOriginal = raw,
            offsetTimeOriginal = null,
            fallbackZoneId = zone
        )

        val expected = LocalDateTime.of(2026, 7, 3, 0, 30, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, parsed)
    }

    @Test
    fun `parseExifDateTakenEpochMillis prefers OffsetTimeOriginal when present`() {
        val parsed = parseExifDateTakenEpochMillis(
            dateTimeOriginal = "2026:07:03 00:30:00",
            offsetTimeOriginal = "+00:00",
            fallbackZoneId = ZoneId.of("Europe/Rome")
        )

        val expected = LocalDateTime.of(2026, 7, 3, 0, 30, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(expected, parsed)
    }

    @Test
    fun `parseExifDateTakenEpochMillis returns null for malformed values`() {
        assertNull(
            parseExifDateTakenEpochMillis(
                dateTimeOriginal = "not-a-date",
                offsetTimeOriginal = null,
                fallbackZoneId = ZoneId.of("UTC")
            )
        )
    }

    @Test
    fun `parseVideoDateTakenEpochMillis parses media-metadata UTC timestamp`() {
        val parsed = parseVideoDateTakenEpochMillis(
            rawDate = "20260703T001530.000Z",
            fallbackZoneId = ZoneId.of("Europe/Rome")
        )

        val expected = LocalDateTime.of(2026, 7, 3, 0, 15, 30)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(expected, parsed)
    }

    @Test
    fun `parseVideoDateTakenEpochMillis falls back to local zone when date has no offset`() {
        val zone = ZoneId.of("Europe/Rome")
        val parsed = parseVideoDateTakenEpochMillis(
            rawDate = "20260703T001530",
            fallbackZoneId = zone
        )

        val expected = LocalDateTime.of(2026, 7, 3, 0, 15, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, parsed)
    }
}