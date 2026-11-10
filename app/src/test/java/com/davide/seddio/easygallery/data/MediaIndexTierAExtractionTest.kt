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

    @Test
    fun `parseVideoDateTakenEpochMillis treats the platform text for a zero creation time as absent`() {
        assertNull(
            parseVideoDateTakenEpochMillis(
                rawDate = "19040101T000000.000Z",
                fallbackZoneId = ZoneId.of("Europe/Rome")
            )
        )
    }

    @Test
    fun `parseVideoDateTakenEpochMillis treats other renderings of the QuickTime zero date as absent`() {
        val zone = ZoneId.of("America/New_York")

        listOf(
            "19040101T000000Z",
            "19040101T000000.000",
            "19040101T000000",
            "1904-01-01T00:00:00Z",
            "-2082844800000"
        ).forEach { raw ->
            assertNull(raw, parseVideoDateTakenEpochMillis(rawDate = raw, fallbackZoneId = zone))
        }
    }

    @Test
    fun `parseVideoDateTakenEpochMillis still accepts neighbours of the QuickTime zero date`() {
        val zone = ZoneId.of("UTC")

        val oneSecondLater = parseVideoDateTakenEpochMillis("19040101T000001.000Z", zone)
        val nextDay = parseVideoDateTakenEpochMillis("19040102T000000.000Z", zone)

        assertEquals(-2_082_844_800_000L + 1_000L, oneSecondLater)
        assertEquals(-2_082_844_800_000L + 86_400_000L, nextDay)
    }

    @Test
    fun `parseVideoDateTakenEpochMillis accepts a genuine 1950 date`() {
        val parsed = parseVideoDateTakenEpochMillis(
            rawDate = "19500615T120000.000Z",
            fallbackZoneId = ZoneId.of("Europe/Rome")
        )

        val expected = LocalDateTime.of(1950, 6, 15, 12, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(expected, parsed)
    }

    @Test
    fun `parseExifDateTakenEpochMillis treats the all-zero and blank-field forms as absent`() {
        listOf(
            "0000:00:00 00:00:00",
            "    :  :     :  :  ",
            "0000:00:00",
            ""
        ).forEach { raw ->
            assertNull(
                raw,
                parseExifDateTakenEpochMillis(
                    dateTimeOriginal = raw,
                    offsetTimeOriginal = "+00:00",
                    fallbackZoneId = ZoneId.of("UTC")
                )
            )
        }
    }

    @Test
    fun `parseExifDateTakenEpochMillis accepts a genuine 1950 date`() {
        val zone = ZoneId.of("Europe/Rome")

        val parsed = parseExifDateTakenEpochMillis(
            dateTimeOriginal = "1950:06:15 12:00:00",
            offsetTimeOriginal = null,
            fallbackZoneId = zone
        )

        val expected = LocalDateTime.of(1950, 6, 15, 12, 0, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, parsed)
    }
}