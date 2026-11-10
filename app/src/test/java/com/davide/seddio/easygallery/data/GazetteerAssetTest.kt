package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Random
import kotlin.math.cos

// The real asset files, read from the module directory; Gradle runs unit tests with it as the working directory.
private val ASSET_DIR = File("src/main/assets")

private fun loadFromFiles(): Gazetteer {
    fun open(asset: String): File = File(ASSET_DIR, asset).also { assertTrue("missing ${it.absolutePath}", it.exists()) }
    return open(GazetteerAssets.PLACES).inputStream().use { places ->
        open(GazetteerAssets.ADMIN1).inputStream().use { admin1 ->
            open(GazetteerAssets.COUNTRIES).inputStream().use { countries ->
                Gazetteer.load(places, admin1, countries)
            }
        }
    }
}

// The only Robolectric test: proves the assets are packaged and readable through the app's AssetManager.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class GazetteerAssetPackagingTest {

    @Test
    fun `the packaged assets load through the AssetManager and equal the files on disk`() {
        val packaged = GazetteerAssets.load(ApplicationProvider.getApplicationContext())
        val fromFiles = loadFromFiles()

        assertEquals(fromFiles.placeCount, packaged.placeCount)
        assertEquals(fromFiles.datasetVersion, packaged.datasetVersion)
        assertEquals(fromFiles.nearest(43.7696, 11.2558), packaged.nearest(43.7696, 11.2558))
    }
}

// Immutable once loaded, so one parse serves every test in the class.
private val sharedGazetteer: Gazetteer by lazy { loadFromFiles() }

class GazetteerAssetTest {

    private val gazetteer: Gazetteer get() = sharedGazetteer

    @Test
    fun `the centre of Florence resolves to Italy Tuscany and Florence`() {
        val match = gazetteer.nearest(43.7696, 11.2558)

        assertNotNull(match)
        assertEquals("IT", match!!.countryCode)
        assertTrue(match.regionName, match.regionName!!.contains("Tuscany"))
        assertEquals("Florence", match.placeName)
    }

    @Test
    fun `Zurich keeps its umlaut`() {
        val match = gazetteer.nearest(47.3769, 8.5417)

        assertEquals("Z\u00fcrich", match!!.placeName)
        assertEquals("CH", match.countryCode)
    }

    @Test
    fun `the open South Pacific is beyond the cap`() {
        assertNull(gazetteer.nearest(-30.0, -140.0))
    }

    @Test
    fun `near the North Pole is beyond the cap`() {
        assertNull(gazetteer.nearest(89.9, 0.0))
    }

    @Test
    fun `places just across the antimeridian are found from both sides`() {
        // Labasa (Fiji) at 179.36 E and Funafuti (Tuvalu) at 179.19 E lie within the cap of the line.
        val cases = listOf(Triple(-16.43, "Labasa", "FJ"), Triple(-8.52, "Funafuti", "TV"))

        for ((lat, place, country) in cases) {
            val east = gazetteer.nearest(lat, 179.999)
            val west = gazetteer.nearest(lat, -179.999)

            assertEquals(place, east!!.placeName)
            assertEquals(country, east.countryCode)
            assertEquals(place, west!!.placeName)
            assertEquals(gazetteer.nearestLinear(lat, 179.999)!!.placeName, east.placeName)
            assertEquals(gazetteer.nearestLinear(lat, -179.999)!!.placeName, west.placeName)
        }
    }

    @Test
    fun `Chukotka either side of the antimeridian agrees with the linear scan`() {
        for (lat in listOf(64.0, 65.0, 66.5, 68.0)) {
            assertEquals(gazetteer.nearestLinear(lat, 179.999), gazetteer.nearest(lat, 179.999))
            assertEquals(gazetteer.nearestLinear(lat, -179.999), gazetteer.nearest(lat, -179.999))
        }
    }

    @Test
    fun `bucketed search equals a linear scan for 20000 seeded points on the real data`() {
        val random = Random(20261110L)
        var hits = 0
        var bucketedNanos = 0L

        repeat(20_000) { i ->
            val lat: Double
            val lon: Double
            when (i % 4) {
                0 -> {
                    lat = random.nextDouble() * 180.0 - 90.0
                    lon = random.nextDouble() * 360.0 - 180.0
                }
                1 -> {
                    val row = random.nextInt(gazetteer.placeCount)
                    lat = (gazetteer.latitudeOf(row) + (random.nextDouble() - 0.5) * 3.0).coerceIn(-90.0, 90.0)
                    val scale = maxOf(0.05, cos(Math.toRadians(lat)))
                    lon = wrap(gazetteer.longitudeOf(row) + (random.nextDouble() - 0.5) * 3.0 / scale)
                }
                2 -> {
                    lat = (if (random.nextBoolean()) 1 else -1) * (80.0 + random.nextDouble() * 10.0)
                    lon = random.nextDouble() * 360.0 - 180.0
                }
                else -> {
                    lat = random.nextDouble() * 170.0 - 85.0
                    lon = wrap(180.0 + (random.nextDouble() - 0.5) * 6.0)
                }
            }

            val started = System.nanoTime()
            val actual = gazetteer.nearest(lat, lon)
            bucketedNanos += System.nanoTime() - started
            assertEquals("query $i at $lat,$lon", gazetteer.nearestLinear(lat, lon), actual)
            if (actual != null) hits++
        }

        println("GAZETTEER bucketed lookups: 20000 queries, ${bucketedNanos / 1_000_000} ms total, $hits hits")
        assertTrue("only $hits hits", hits > 5_000)
    }

    @Test
    fun `header versions equal the resolver version and row counts equal rows written`() {
        for (asset in listOf(GazetteerAssets.PLACES, GazetteerAssets.ADMIN1, GazetteerAssets.COUNTRIES)) {
            val lines = File(ASSET_DIR, asset).readLines(Charsets.UTF_8)
            val headers = lines.takeWhile { it.startsWith("#") }
            val version = headers.single { it.startsWith("# dataset-version:") }.substringAfter(':').trim().toInt()
            val written = headers.single { it.startsWith("# rows-written:") }.substringAfter(':').trim().toInt()

            assertEquals(asset, PLACE_RESOLVER_VERSION, version)
            assertEquals(asset, written, lines.size - headers.size)
            if (asset == GazetteerAssets.PLACES) assertEquals(written, gazetteer.placeCount)
        }
        assertEquals(PLACE_RESOLVER_VERSION, gazetteer.datasetVersion)
    }

    @Test
    fun `every admin1 key resolves or is blank or 00 and every country resolves`() {
        var withRegion = 0
        for (row in 0 until gazetteer.placeCount) {
            val place = gazetteer.placeNameOf(row)
            if (gazetteer.regionCodeOf(row) != null) {
                withRegion++
                assertNotNull("$place ${gazetteer.countryCodeOf(row)}.${gazetteer.regionCodeOf(row)}", gazetteer.regionNameOf(row))
            }
            assertNotNull("$place ${gazetteer.countryCodeOf(row)}", gazetteer.englishCountryName(gazetteer.countryCodeOf(row)))
        }
        assertTrue(withRegion > 30_000)
    }

    private fun wrap(lon: Double): Double {
        var wrapped = lon
        while (wrapped > 180.0) wrapped -= 360.0
        while (wrapped < -180.0) wrapped += 360.0
        return wrapped
    }
}
