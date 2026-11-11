package com.davide.seddio.easygallery.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos

class GazetteerTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private data class P(
        val lat: Double,
        val lon: Double,
        val name: String,
        val country: String = "XX",
        val admin1: String = "01"
    )

    private fun header(version: Int, rows: Int) =
        "# Test asset\n# dataset-version: $version\n# rows-written: $rows\n# columns: whatever\n"

    private fun placesTsv(
        rows: List<P>,
        version: Int = PLACE_RESOLVER_VERSION,
        declared: Int = rows.size,
        terminate: Boolean = true
    ): String =
        header(version, declared) +
            rows.joinToString("") { "${it.lat}\t${it.lon}\t${it.name}\t${it.country}\t${it.admin1}" + "\n" }.let {
                if (terminate) it else it.removeSuffix("\n")
            }

    private fun admin1Tsv(version: Int = PLACE_RESOLVER_VERSION, declared: Int = 1) =
        header(version, declared) + "XX.01\tRegion One\n"

    private fun countriesTsv(version: Int = PLACE_RESOLVER_VERSION, declared: Int = 1) =
        header(version, declared) + "XX\tTestland\n"

    private fun load(
        places: String,
        admin1: String = admin1Tsv(),
        countries: String = countriesTsv(),
        capKm: Double = GAZETTEER_CAP_KM
    ): Gazetteer = Gazetteer.load(StringReader(places), StringReader(admin1), StringReader(countries), capKm)

    private fun gazetteerOf(rows: List<P>, capKm: Double = GAZETTEER_CAP_KM) = load(placesTsv(rows), capKm = capKm)

    // Largest latitude (to adjacent doubles) at which a place at (placeLat, 0) is still found.
    private fun latitudeAtCap(gazetteer: Gazetteer, placeLat: Double): Double {
        var inside = placeLat
        var outside = placeLat + 3.0
        repeat(300) {
            val mid = (inside + outside) / 2.0
            if (gazetteer.nearest(mid, 0.0) != null) inside = mid else outside = mid
        }
        return inside
    }

    // ---- the cap ----

    @Test
    fun `a point exactly at the cap is within it`() {
        val gazetteer = gazetteerOf(listOf(P(0.0, 0.0, "Origin")))

        val atCap = latitudeAtCap(gazetteer, 0.0)

        val match = gazetteer.nearest(atCap, 0.0)
        assertNotNull(match)
        assertEquals(GAZETTEER_CAP_KM, match!!.distanceKm, 1e-9)
    }

    @Test
    fun `the next representable point beyond the cap is out`() {
        val gazetteer = gazetteerOf(listOf(P(0.0, 0.0, "Origin")))

        val atCap = latitudeAtCap(gazetteer, 0.0)

        assertNull(gazetteer.nearest(Math.nextUp(atCap), 0.0))
        assertNull(gazetteer.nearestLinear(Math.nextUp(atCap), 0.0))
    }

    @Test
    fun `the cap also holds at high latitude along a parallel`() {
        val gazetteer = gazetteerOf(listOf(P(70.0, 10.0, "North")))
        var inside = 10.0
        var outside = 40.0
        repeat(300) {
            val mid = (inside + outside) / 2.0
            if (gazetteer.nearest(70.0, mid) != null) inside = mid else outside = mid
        }

        assertNotNull(gazetteer.nearest(70.0, inside))
        assertNull(gazetteer.nearest(70.0, Math.nextUp(inside)))
        assertEquals(GAZETTEER_CAP_KM, gazetteer.nearest(70.0, inside)!!.distanceKm, 1e-9)
    }

    @Test
    fun `beyond the cap all three values are null`() {
        val gazetteer = gazetteerOf(listOf(P(0.0, 0.0, "Origin")))

        assertNull(gazetteer.nearest(5.0, 5.0))
    }

    @Test
    fun `haversine matches the closed form for one degree on the equator`() {
        val expected = Math.PI * GAZETTEER_EARTH_RADIUS_KM / 180.0

        assertEquals(expected, haversineKm(0.0, 0.0, 0.0, 1.0), 1e-9)
        assertEquals(expected, haversineKm(0.0, 0.0, 1.0, 0.0), 1e-9)
    }

    @Test
    fun `a cap larger than the sphere accepts every place`() {
        val gazetteer = gazetteerOf(listOf(P(-40.0, 170.0, "Far")), capKm = 25_000.0)

        assertEquals("Far", gazetteer.nearest(40.0, -10.0)!!.placeName)
    }

    @Test
    fun `a non-positive or non-finite cap is rejected`() {
        for (cap in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(GazetteerLoadException::class.java) {
                load(placesTsv(listOf(P(0.0, 0.0, "A"))), capKm = cap)
            }
        }
    }

    // ---- the grid ----

    @Test
    fun `places found from the other side of the antimeridian`() {
        val east = gazetteerOf(listOf(P(10.0, 179.99, "East edge")))
        val west = gazetteerOf(listOf(P(10.0, -179.99, "West edge")))

        assertEquals("East edge", east.nearest(10.0, -179.99)!!.placeName)
        assertEquals("West edge", west.nearest(10.0, 179.99)!!.placeName)
        assertEquals("East edge", east.nearest(10.0, -180.0)!!.placeName)
        assertEquals("East edge", east.nearest(10.0, 180.0)!!.placeName)
    }

    @Test
    fun `longitude 180 and -180 are the same place`() {
        val gazetteer = gazetteerOf(listOf(P(0.0, 180.0, "On the line"), P(20.0, -180.0, "Other side")))

        // Distance differs by float noise (sin(pi) is not exactly 0), so compare the place, not the whole match.
        assertEquals("On the line", gazetteer.nearest(0.0, 180.0)!!.placeName)
        assertEquals("On the line", gazetteer.nearest(0.0, -180.0)!!.placeName)
        assertEquals(0.0, gazetteer.nearest(0.0, -180.0)!!.distanceKm, 1e-6)
        assertEquals("On the line", gazetteer.nearest(0.0, 179.5)!!.placeName)
        assertEquals("On the line", gazetteer.nearest(0.0, -179.5)!!.placeName)
        assertEquals("Other side", gazetteer.nearest(20.0, 180.0)!!.placeName)
    }

    @Test
    fun `poles do not crash and find places at either pole from any longitude`() {
        val gazetteer = gazetteerOf(listOf(P(90.0, 0.0, "North Pole"), P(-90.0, 0.0, "South Pole")))

        for (lon in listOf(-180.0, -90.0, 0.0, 77.7, 180.0)) {
            assertEquals("North Pole", gazetteer.nearest(90.0, lon)!!.placeName)
            assertEquals("South Pole", gazetteer.nearest(-90.0, lon)!!.placeName)
        }
        assertNull(gazetteer.nearest(0.0, 0.0))
    }

    @Test
    fun `a place across the pole is found with the full ring`() {
        val gazetteer = gazetteerOf(listOf(P(89.8, 179.0, "Across")))

        val match = gazetteer.nearest(89.8, 0.0)

        assertEquals("Across", match!!.placeName)
        assertTrue(match.distanceKm < 50.0)
        assertEquals(gazetteer.nearestLinear(89.8, 0.0), match)
    }

    @Test
    fun `a place across the south pole is found`() {
        val gazetteer = gazetteerOf(listOf(P(-89.9, -90.0, "Across south")))

        assertEquals("Across south", gazetteer.nearest(-89.9, 90.0)!!.placeName)
    }

    @Test
    fun `empty buckets return null without scanning errors`() {
        val gazetteer = gazetteerOf(listOf(P(45.0, 9.0, "Somewhere")))

        assertNull(gazetteer.nearest(-45.0, -9.0))
        assertNull(gazetteer.nearest(45.0, 12.0))
    }

    @Test
    fun `a place in a neighbouring bucket is found`() {
        val gazetteer = gazetteerOf(listOf(P(44.001, 9.001, "Neighbour")))

        assertEquals("Neighbour", gazetteer.nearest(43.999, 8.999)!!.placeName)
    }

    @Test
    fun `a wide cap reaches several buckets away and widens with latitude`() {
        val gazetteer = gazetteerOf(listOf(P(80.0, 0.0, "High")), capKm = 1_000.0)

        // 1000 km at 80 degrees north spans about 52 degrees of longitude along the parallel.
        assertNotNull(gazetteer.nearest(80.0, 40.0))
        assertNull(gazetteer.nearest(80.0, 60.0))
    }

    // ---- ties and input ----

    @Test
    fun `equal distance goes to the earlier asset row`() {
        val east = P(0.0, 0.4, "East")
        val west = P(0.0, -0.4, "West")

        assertEquals("East", gazetteerOf(listOf(east, west)).nearest(0.0, 0.0)!!.placeName)
        assertEquals("West", gazetteerOf(listOf(west, east)).nearest(0.0, 0.0)!!.placeName)
    }

    @Test
    fun `duplicate coordinates in one bucket go to the earlier row`() {
        val gazetteer = gazetteerOf(listOf(P(10.5, 10.5, "First"), P(10.5, 10.5, "Second")))

        assertEquals("First", gazetteer.nearest(10.5, 10.6)!!.placeName)
    }

    @Test
    fun `NaN infinite and out of range input returns null`() {
        val gazetteer = gazetteerOf(listOf(P(0.0, 0.0, "Origin")))
        val bad = listOf(
            Double.NaN to 0.0, 0.0 to Double.NaN,
            Double.POSITIVE_INFINITY to 0.0, 0.0 to Double.NEGATIVE_INFINITY,
            90.0001 to 0.0, -90.0001 to 0.0, 0.0 to 180.0001, 0.0 to -180.0001
        )

        for ((lat, lon) in bad) {
            assertNull("$lat,$lon", gazetteer.nearest(lat, lon))
            assertNull("$lat,$lon", gazetteer.nearestLinear(lat, lon))
        }
    }

    // ---- what a match carries ----

    @Test
    fun `country region and place all come from the one matched row`() {
        val places = placesTsv(
            listOf(
                P(0.0, 0.0, "Near", country = "AA", admin1 = "10"),
                P(0.0, 0.5, "Farther", country = "BB", admin1 = "20")
            )
        )
        val admin1 = header(PLACE_RESOLVER_VERSION, 2) + "AA.10\tAlpha Region\nBB.20\tBeta Region\n"
        val gazetteer = load(places, admin1 = admin1)

        val match = gazetteer.nearest(0.0, 0.1)!!

        assertEquals("Near", match.placeName)
        assertEquals("AA", match.countryCode)
        assertEquals("10", match.regionCode)
        assertEquals("Alpha Region", match.regionName)
    }

    @Test
    fun `admin1 00 and empty give a null region while place and country stay set`() {
        val gazetteer = gazetteerOf(
            listOf(
                P(0.0, 0.0, "Zero", admin1 = "00"),
                P(10.0, 0.0, "Empty", admin1 = "")
            )
        )

        for ((lat, name) in listOf(0.0 to "Zero", 10.0 to "Empty")) {
            val match = gazetteer.nearest(lat, 0.0)!!
            assertEquals(name, match.placeName)
            assertEquals("XX", match.countryCode)
            assertNull(match.regionCode)
            assertNull(match.regionName)
        }
    }

    @Test
    fun `an admin1 code the asset does not define keeps its code and has no name`() {
        val gazetteer = gazetteerOf(listOf(P(0.0, 0.0, "Odd", admin1 = "99")))

        val match = gazetteer.nearest(0.0, 0.0)!!

        assertEquals("99", match.regionCode)
        assertNull(match.regionName)
    }

    @Test
    fun `country english name is exposed for the display fallback`() {
        val gazetteer = gazetteerOf(listOf(P(0.0, 0.0, "A")))

        assertEquals("Testland", gazetteer.englishCountryName("XX"))
        assertNull(gazetteer.englishCountryName("ZZ"))
    }

    @Test
    fun `non-ASCII place names survive the UTF-8 path`() {
        val places = placesTsv(listOf(P(47.36667, 8.55, "Zürich"), P(35.6895, 139.69171, "東京")))
        val gazetteer = Gazetteer.load(
            ByteArrayInputStream(places.toByteArray(Charsets.UTF_8)),
            ByteArrayInputStream(admin1Tsv().toByteArray(Charsets.UTF_8)),
            ByteArrayInputStream(countriesTsv().toByteArray(Charsets.UTF_8))
        )

        assertEquals("Zürich", gazetteer.nearest(47.36667, 8.55)!!.placeName)
        assertEquals("東京", gazetteer.nearest(35.69, 139.69)!!.placeName)
    }

    @Test
    fun `parsing does not depend on the default locale`() {
        Locale.setDefault(Locale.ITALY)

        val gazetteer = gazetteerOf(listOf(P(45.5, 9.25, "Milano")))

        assertEquals("Milano", gazetteer.nearest(45.5, 9.25)!!.placeName)
    }

    // ---- the loader refuses bad assets ----

    @Test
    fun `an empty stream is rejected`() {
        for (which in 0..2) {
            val e = assertThrows(GazetteerLoadException::class.java) {
                load(
                    places = if (which == 0) "" else placesTsv(listOf(P(0.0, 0.0, "A"))),
                    admin1 = if (which == 1) "" else admin1Tsv(),
                    countries = if (which == 2) "" else countriesTsv()
                )
            }
            assertTrue(e.message, e.message!!.contains("empty"))
        }
    }

    @Test
    fun `a header with no rows is rejected`() {
        val e = assertThrows(GazetteerLoadException::class.java) { load(placesTsv(emptyList(), declared = 0)) }

        assertTrue(e.message, e.message!!.contains("no data rows"))
    }

    @Test
    fun `a truncated file with fewer rows than declared is rejected`() {
        val rows = listOf(P(0.0, 0.0, "A"), P(1.0, 1.0, "B"))

        val e = assertThrows(GazetteerLoadException::class.java) { load(placesTsv(rows, declared = 3)) }

        assertTrue(e.message, e.message!!.contains("truncated"))
    }

    @Test
    fun `a file cut off in the middle of its last line is rejected`() {
        val e = assertThrows(GazetteerLoadException::class.java) {
            load(placesTsv(listOf(P(0.0, 0.0, "A"), P(1.0, 1.0, "B")), terminate = false))
        }

        assertTrue(e.message, e.message!!.contains("truncated"))
    }

    @Test
    fun `more rows than declared are rejected`() {
        val rows = listOf(P(0.0, 0.0, "A"), P(1.0, 1.0, "B"))

        assertThrows(GazetteerLoadException::class.java) { load(placesTsv(rows, declared = 1)) }
    }

    @Test
    fun `a wrong dataset version is rejected in every asset`() {
        val rows = listOf(P(0.0, 0.0, "A"))
        val wrong = PLACE_RESOLVER_VERSION + 1

        val cases = listOf(
            { load(placesTsv(rows, version = wrong)) },
            { load(placesTsv(rows), admin1 = admin1Tsv(version = wrong)) },
            { load(placesTsv(rows), countries = countriesTsv(version = wrong)) }
        )
        for (case in cases) {
            val e = assertThrows(GazetteerLoadException::class.java) { case() }
            assertTrue(e.message, e.message!!.contains("dataset-version"))
        }
    }

    @Test
    fun `a missing version or row count header is rejected`() {
        val body = "0.0\t0.0\tA\tXX\t01\n"

        assertThrows(GazetteerLoadException::class.java) { load("# rows-written: 1\n$body") }
        assertThrows(GazetteerLoadException::class.java) { load("# dataset-version: 1\n$body") }
        assertThrows(GazetteerLoadException::class.java) { load(body) }
    }

    @Test
    fun `a malformed place row is rejected`() {
        val bad = listOf(
            "0.0\t0.0\tA\tXX",
            "0.0\t0.0\tA\tXX\t01\textra",
            "abc\t0.0\tA\tXX\t01",
            "NaN\t0.0\tA\tXX\t01",
            "91.0\t0.0\tA\tXX\t01",
            "0.0\t181.0\tA\tXX\t01",
            "0.0\t0.0\t\tXX\t01",
            "0.0\t0.0\tA\tXXX\t01",
            ""
        )

        for (row in bad) {
            val text = header(PLACE_RESOLVER_VERSION, 1) + row + "\n"
            assertThrows(row, GazetteerLoadException::class.java) { load(text) }
        }
    }

    @Test
    fun `a header line after data is rejected`() {
        val text = header(PLACE_RESOLVER_VERSION, 1) + "0.0\t0.0\tA\tXX\t01\n# late: header\n"

        assertThrows(GazetteerLoadException::class.java) { load(text) }
    }

    @Test
    fun `malformed admin1 and country rows are rejected`() {
        val places = placesTsv(listOf(P(0.0, 0.0, "A")))

        assertThrows(GazetteerLoadException::class.java) {
            load(places, admin1 = header(PLACE_RESOLVER_VERSION, 1) + "nokey\tName\n")
        }
        assertThrows(GazetteerLoadException::class.java) {
            load(places, countries = header(PLACE_RESOLVER_VERSION, 1) + "XXX\tName\n")
        }
    }

    @Test
    fun `invalid UTF-8 bytes fail the load instead of becoming replacement characters`() {
        val good = placesTsv(listOf(P(0.0, 0.0, "AB"))).toByteArray(Charsets.UTF_8)
        val bytes = String(good, Charsets.UTF_8).replace("AB", "A\u0001").toByteArray(Charsets.UTF_8)
        // Turn the placeholder byte after "A" into a lone continuation byte.
        bytes[bytes.indexOf(0x01.toByte())] = 0xC3.toByte()

        assertThrows(IOException::class.java) {
            Gazetteer.load(
                ByteArrayInputStream(bytes),
                ByteArrayInputStream(admin1Tsv().toByteArray()),
                ByteArrayInputStream(countriesTsv().toByteArray())
            )
        }
    }

    // ---- names only ----

    private fun loadNames(admin1: String = admin1Tsv(), countries: String = countriesTsv()): GazetteerNames =
        GazetteerNames.load(StringReader(admin1), StringReader(countries))

    // QZ is in the ISO user-assigned range, so the platform has no name for it.
    private fun countriesWithUnknownToPlatform() =
        header(PLACE_RESOLVER_VERSION, 2) + "QZ\tQuuxland\nIT\tTestitaly\n"

    @Test
    fun `names expose the admin1 name for a country and code pair and nothing for an unknown pair`() {
        val names = loadNames()

        assertEquals("Region One", names.admin1Name("XX", "01"))
        assertNull(names.admin1Name("XX", "02"))
        assertNull(names.admin1Name("YY", "01"))
        assertNull(names.admin1Name("XX", "00"))
    }

    @Test
    fun `names expose the english country name`() {
        val names = loadNames()

        assertEquals("Testland", names.englishCountryName("XX"))
        assertNull(names.englishCountryName("ZZ"))
    }

    @Test
    fun `a country the platform names is shown with the platform name and not the asset one`() {
        val names = loadNames(countries = countriesWithUnknownToPlatform())

        assertEquals("Italy", names.countryDisplayName("IT", Locale.ENGLISH))
    }

    @Test
    fun `a country the platform cannot name falls back to the english asset name`() {
        val names = loadNames(countries = countriesWithUnknownToPlatform())

        assertEquals("Quuxland", names.countryDisplayName("QZ", Locale.ENGLISH))
        assertEquals("Quuxland", names.countryDisplayName("QZ", Locale.JAPANESE))
    }

    @Test
    fun `a country neither the platform nor the asset knows falls back to the bare code`() {
        val names = loadNames(countries = countriesWithUnknownToPlatform())

        assertEquals("QY", names.countryDisplayName("QY", Locale.ENGLISH))
    }

    @Test
    fun `a code that is not a valid region does not throw and falls back`() {
        val names = loadNames(countries = countriesWithUnknownToPlatform())

        assertEquals("not-a-region", names.countryDisplayName("not-a-region", Locale.ENGLISH))
        assertEquals("", names.countryDisplayName("", Locale.ENGLISH))
    }

    @Test
    fun `names loading is rejected for empty, mis-versioned, truncated and unterminated assets`() {
        val wrong = PLACE_RESOLVER_VERSION + 1
        val cases = listOf(
            { loadNames(admin1 = "") },
            { loadNames(countries = "") },
            { loadNames(admin1 = admin1Tsv(version = wrong)) },
            { loadNames(countries = countriesTsv(version = wrong)) },
            { loadNames(admin1 = admin1Tsv(declared = 2)) },
            { loadNames(countries = countriesTsv(declared = 2)) },
            { loadNames(admin1 = admin1Tsv().removeSuffix("\n")) },
            { loadNames(countries = countriesTsv().removeSuffix("\n")) },
            { loadNames(admin1 = admin1Tsv(declared = 0)) },
            { loadNames(admin1 = "# dataset-version: 1\nXX.01\tRegion One\n") },
            { loadNames(countries = header(PLACE_RESOLVER_VERSION, 1) + "XXX\tName\n") },
            { loadNames(admin1 = header(PLACE_RESOLVER_VERSION, 1) + "nokey\tName\n") }
        )

        for ((index, case) in cases.withIndex()) {
            assertThrows("case $index", GazetteerLoadException::class.java) { case() }
        }
    }

    @Test
    fun `names loading fails on invalid UTF-8 bytes`() {
        val bytes = countriesTsv().replace("Testland", "Test\u0001").toByteArray(Charsets.UTF_8)
        bytes[bytes.indexOf(0x01.toByte())] = 0xC3.toByte()

        assertThrows(IOException::class.java) {
            GazetteerNames.load(ByteArrayInputStream(admin1Tsv().toByteArray()), ByteArrayInputStream(bytes))
        }
    }

    // ---- the grid against the definition ----

    @Test
    fun `bucketed search equals a linear scan for 10000 seeded queries at the default cap`() {
        val random = Random(20261110L)
        val gazetteer = gazetteerOf(randomPlaces(random, 8_000))

        val hits = assertEquivalent(gazetteer, random, queries = 10_000)

        assertTrue("only $hits hits; the test would prove little", hits > 3_000)
    }

    @Test
    fun `bucketed search equals a linear scan at a small cap`() {
        val random = Random(7L)
        val gazetteer = gazetteerOf(randomPlaces(random, 8_000), capKm = 5.0)

        assertTrue(assertEquivalent(gazetteer, random, queries = 4_000, spread = 0.1) > 100)
    }

    @Test
    fun `bucketed search equals a linear scan at a wide cap that crosses many buckets`() {
        val random = Random(11L)
        val gazetteer = gazetteerOf(randomPlaces(random, 2_000), capKm = 1_500.0)

        assertTrue(assertEquivalent(gazetteer, random, queries = 3_000) > 1_000)
    }

    @Test
    fun `bucketed search equals a linear scan when the cap exceeds the sphere`() {
        val random = Random(13L)
        val gazetteer = gazetteerOf(randomPlaces(random, 300), capKm = 25_000.0)

        assertEquals(300, assertEquivalent(gazetteer, random, queries = 300))
    }

    @Test
    fun `places and queries on bucket edges agree with a linear scan`() {
        val random = Random(17L)
        val places = (0 until 3_000).map {
            P(
                lat = (random.nextInt(179) - 89).toDouble(),
                lon = (random.nextInt(359) - 179).toDouble(),
                name = "Edge $it"
            )
        }
        val gazetteer = gazetteerOf(places)

        var hits = 0
        repeat(5_000) {
            val place = places[random.nextInt(places.size)]
            val lat = (place.lat + random.nextInt(3) - 1.0 + (random.nextInt(3) - 1) * 1e-9).coerceIn(-90.0, 90.0)
            val lon = wrapLongitude(place.lon + random.nextInt(3) - 1.0 + (random.nextInt(3) - 1) * 1e-9)
            val expected = gazetteer.nearestLinear(lat, lon)
            assertEquals("$lat,$lon", expected, gazetteer.nearest(lat, lon))
            if (expected != null) hits++
        }
        assertTrue(hits > 500)
    }

    // Uniform over the sphere, plus clusters at the poles and the antimeridian where the ring logic is stressed.
    private fun randomPlaces(random: Random, count: Int): List<P> = (0 until count).map { i ->
        val lat: Double
        val lon: Double
        when (i % 4) {
            0 -> {
                lat = Math.toDegrees(asin(2.0 * random.nextDouble() - 1.0))
                lon = random.nextDouble() * 360.0 - 180.0
            }
            1 -> {
                lat = (if (random.nextBoolean()) 1 else -1) * (86.0 + random.nextDouble() * 4.0)
                lon = random.nextDouble() * 360.0 - 180.0
            }
            2 -> {
                lat = random.nextDouble() * 160.0 - 80.0
                lon = wrapLongitude(180.0 + (random.nextDouble() - 0.5) * 3.0)
            }
            else -> {
                lat = random.nextDouble() * 180.0 - 90.0
                lon = random.nextDouble() * 360.0 - 180.0
            }
        }
        P(lat, lon, "Place $i", admin1 = (i % 7).toString())
    }

    private fun wrapLongitude(lon: Double): Double {
        var wrapped = lon
        while (wrapped > 180.0) wrapped -= 360.0
        while (wrapped < -180.0) wrapped += 360.0
        return wrapped
    }

    // Returns the number of queries that found a place, after requiring identical answers for all.
    private fun assertEquivalent(gazetteer: Gazetteer, random: Random, queries: Int, spread: Double = 3.0): Int {
        var hits = 0
        repeat(queries) { i ->
            val lat: Double
            val lon: Double
            when (i % 4) {
                0 -> {
                    lat = random.nextDouble() * 180.0 - 90.0
                    lon = random.nextDouble() * 360.0 - 180.0
                }
                1 -> {
                    val row = random.nextInt(gazetteer.placeCount)
                    lat = (gazetteer.latitudeOf(row) + (random.nextDouble() - 0.5) * spread).coerceIn(-90.0, 90.0)
                    val scale = maxOf(0.05, cos(Math.toRadians(lat)))
                    lon = wrapLongitude(gazetteer.longitudeOf(row) + (random.nextDouble() - 0.5) * spread / scale)
                }
                2 -> {
                    lat = (if (random.nextBoolean()) 1 else -1) * (85.0 + random.nextDouble() * 5.0)
                    lon = random.nextDouble() * 360.0 - 180.0
                }
                else -> {
                    lat = random.nextDouble() * 160.0 - 80.0
                    lon = wrapLongitude(180.0 + (random.nextDouble() - 0.5) * 4.0)
                }
            }
            val expected = gazetteer.nearestLinear(lat, lon)
            val actual = gazetteer.nearest(lat, lon)
            assertEquals("query $i at $lat,$lon", expected, actual)
            if (actual != null) {
                hits++
                assertTrue(abs(actual.distanceKm) <= gazetteer.capKm + 1e-9)
            }
        }
        return hits
    }
}
