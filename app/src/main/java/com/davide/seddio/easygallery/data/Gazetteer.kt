package com.davide.seddio.easygallery.data

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.CodingErrorAction
import java.util.IllformedLocaleException
import java.util.Locale
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Pure Kotlin, no android.* imports, so it runs on a plain JVM (ADR-0019 Amendment 1).

/** Stamped on every resolved index row; the loader requires the asset header to carry the same value. */
const val PLACE_RESOLVER_VERSION = 1

/** Global distance cap for nearest-place lookups; provisional until measurement M5 (ADR-0019 §5). */
const val GAZETTEER_CAP_KM = 100.0

/** Mean Earth radius, the sphere haversine uses (ADR-0019 §5). */
const val GAZETTEER_EARTH_RADIUS_KM = 6371.0088

/** Raised for an asset that is missing, empty, truncated, mis-versioned or malformed. */
class GazetteerLoadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The three answers for one coordinate; always all read off the single matched place row. */
data class GazetteerMatch(
    val placeName: String,
    val countryCode: String,
    /** The admin1 code, or null when the place has none (`00` or empty in the asset). */
    val regionCode: String?,
    /** English admin1 name; null when [regionCode] is null or the asset does not define the key. */
    val regionName: String?,
    val distanceKm: Double
)

/** Great-circle distance on a sphere of [GAZETTEER_EARTH_RADIUS_KM]. */
fun haversineKm(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double {
    val latA = Math.toRadians(latitudeA)
    val latB = Math.toRadians(latitudeB)
    val sinHalfLat = sin((latB - latA) / 2.0)
    val sinHalfLon = sin((Math.toRadians(longitudeB) - Math.toRadians(longitudeA)) / 2.0)
    val a = sinHalfLat * sinHalfLat + cos(latA) * cos(latB) * sinHalfLon * sinHalfLon
    return 2.0 * GAZETTEER_EARTH_RADIUS_KM * asin(sqrt(min(1.0, a)))
}

/** Nearest-place table in primitive arrays with a 1-degree bucket grid (CSR: `cellStart` spans into `cellRows`). */
class Gazetteer private constructor(
    private val latitudes: DoubleArray,
    private val longitudes: DoubleArray,
    private val nameOffsets: IntArray,
    private val nameBytes: ByteArray,
    private val countryIndex: ShortArray,
    private val regionIndex: ShortArray,
    private val countryCodes: Array<String>,
    private val regionCodes: Array<String>,
    private val regionNames: Array<String?>,
    private val countryNamesByCode: Map<String, String>,
    private val cellStart: IntArray,
    private val cellRows: IntArray,
    val capKm: Double,
    val datasetVersion: Int
) {
    val placeCount: Int get() = latitudes.size

    /** Bytes held by the big arrays; excludes the small interned string tables. */
    val approxRetainedBytes: Long
        get() = latitudes.size * 8L + longitudes.size * 8L + nameOffsets.size * 4L + nameBytes.size +
            countryIndex.size * 2L + regionIndex.size * 2L + cellStart.size * 4L + cellRows.size * 4L

    // Angular cap; at or beyond pi every point of the sphere is within it.
    private val capRadians = capKm / GAZETTEER_EARTH_RADIUS_KM
    private val capCoversSphere = capRadians >= Math.PI
    private val sinHalfCap = if (capCoversSphere) 1.0 else sin(capRadians / 2.0)

    // Haversine term at the cap, so no asin is needed per candidate.
    private val capTerm = if (capCoversSphere) Double.POSITIVE_INFINITY else sinHalfCap * sinHalfCap

    // One degree of latitude derived from R, never a rounded constant (ADR-0019 §6).
    private val latitudeRing =
        min(ceil(capKm / (Math.PI * GAZETTEER_EARTH_RADIUS_KM / 180.0)), LATITUDE_ROWS.toDouble()).toInt()

    /** Nearest place within [capKm] using the bucket grid, or null (also for NaN or out-of-range input). */
    fun nearest(latitude: Double, longitude: Double): GazetteerMatch? {
        if (!isValidCoordinate(latitude, longitude)) return null
        val search = Search(latitude, longitude)

        val centreRow = latitudeRowOf(latitude)
        val firstRow = max(0, centreRow - latitudeRing)
        val lastRow = min(LATITUDE_ROWS - 1, centreRow + latitudeRing)
        val southEdge = firstRow - 90.0
        val northEdge = lastRow + 1 - 90.0

        var firstColumn = 0
        var lastColumn = LONGITUDE_COLUMNS - 1
        if (!capCoversSphere && southEdge > -90.0 && northEdge < 90.0) {
            // Exact spherical bound 2*asin(sin(d/2)/cos(phi)) at the band's poleward edge.
            val poleward = max(-southEdge, northEdge)
            val ratio = sinHalfCap / cos(Math.toRadians(poleward))
            if (ratio < 1.0) {
                val halfSpan = Math.toDegrees(2.0 * asin(ratio)) + LONGITUDE_PAD_DEGREES
                val low = floor(longitude - halfSpan).toInt()
                val high = floor(longitude + halfSpan).toInt()
                if (high - low + 1 < LONGITUDE_COLUMNS) {
                    firstColumn = low
                    lastColumn = high
                }
            }
        }

        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                val cell = row * LONGITUDE_COLUMNS + Math.floorMod(column + 180, LONGITUDE_COLUMNS)
                for (k in cellStart[cell] until cellStart[cell + 1]) search.offer(cellRows[k])
            }
        }
        return search.result()
    }

    /** Linear scan over every row: the definition [nearest] is proven against. Test oracle only. */
    internal fun nearestLinear(latitude: Double, longitude: Double): GazetteerMatch? {
        if (!isValidCoordinate(latitude, longitude)) return null
        val search = Search(latitude, longitude)
        for (row in latitudes.indices) search.offer(row)
        return search.result()
    }

    /** English country name from `countries.tsv`, for the display-time fallback in step 2.7. */
    fun englishCountryName(countryCode: String): String? = countryNamesByCode[countryCode]

    internal fun latitudeOf(row: Int): Double = latitudes[row]
    internal fun longitudeOf(row: Int): Double = longitudes[row]
    internal fun placeNameOf(row: Int): String =
        String(nameBytes, nameOffsets[row], nameOffsets[row + 1] - nameOffsets[row], Charsets.UTF_8)
    internal fun countryCodeOf(row: Int): String = countryCodes[countryIndex[row].toInt() and 0xFFFF]
    internal fun regionCodeOf(row: Int): String? =
        regionIndex[row].toInt().and(0xFFFF).let { if (it == 0) null else regionCodes[it] }
    internal fun regionNameOf(row: Int): String? =
        regionIndex[row].toInt().and(0xFFFF).let { if (it == 0) null else regionNames[it] }

    private fun matchOf(row: Int, distanceKm: Double): GazetteerMatch = GazetteerMatch(
        placeName = placeNameOf(row),
        countryCode = countryCodeOf(row),
        regionCode = regionCodeOf(row),
        regionName = regionNameOf(row),
        distanceKm = distanceKm
    )

    // Both search paths feed rows through here, so they cannot differ in arithmetic or tie-breaking.
    private inner class Search(private val latitude: Double, private val longitude: Double) {
        private val latRadians = Math.toRadians(latitude)
        private val lonRadians = Math.toRadians(longitude)
        private val cosLat = cos(latRadians)
        private var bestTerm = capTerm
        private var bestRow = -1

        fun offer(row: Int) {
            val term = haversineTerm(row)
            // Equal distance goes to the earlier asset row; the cap itself is inclusive.
            if (term < bestTerm || (term == bestTerm && (bestRow == -1 || row < bestRow))) {
                bestTerm = term
                bestRow = row
            }
        }

        fun result(): GazetteerMatch? {
            if (bestRow == -1) return null
            return matchOf(
                bestRow,
                haversineKm(latitude, longitude, latitudes[bestRow], longitudes[bestRow])
            )
        }

        // The latitude term alone is a lower bound on the full term, so returning early with it
        // can never change which row wins.
        private fun haversineTerm(row: Int): Double {
            val placeLat = Math.toRadians(latitudes[row])
            val sinHalfLat = sin((placeLat - latRadians) / 2.0)
            val latTerm = sinHalfLat * sinHalfLat
            if (latTerm > bestTerm) return latTerm
            val sinHalfLon = sin((Math.toRadians(longitudes[row]) - lonRadians) / 2.0)
            return latTerm + cosLat * cos(placeLat) * sinHalfLon * sinHalfLon
        }
    }

    companion object {
        private const val LATITUDE_ROWS = 180
        private const val LONGITUDE_COLUMNS = 360
        private const val LONGITUDE_PAD_DEGREES = 1e-9
        private const val MAX_INTERNED = 0xFFFF

        private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
            latitude in -90.0..90.0 && longitude in -180.0..180.0

        // 90 degrees falls in the last row.
        private fun latitudeRowOf(latitude: Double): Int =
            min(LATITUDE_ROWS - 1, floor(latitude).toInt() + 90)

        // 180 and -180 are one column.
        private fun longitudeColumnOf(longitude: Double): Int =
            Math.floorMod(floor(longitude).toInt() + 180, LONGITUDE_COLUMNS)

        fun load(
            places: InputStream,
            admin1: InputStream,
            countries: InputStream,
            capKm: Double = GAZETTEER_CAP_KM
        ): Gazetteer = load(strictUtf8(places), strictUtf8(admin1), strictUtf8(countries), capKm)

        /** Parses the three assets and builds the grid; throws [GazetteerLoadException] for a bad asset. Does not close the readers. */
        fun load(
            places: Reader,
            admin1: Reader,
            countries: Reader,
            capKm: Double = GAZETTEER_CAP_KM
        ): Gazetteer {
            if (!capKm.isFinite() || capKm <= 0.0) {
                throw GazetteerLoadException("Distance cap must be positive and finite, was $capKm")
            }

            val admin1Names = readAdmin1Names(admin1)
            val countryNames = readCountryNames(countries)

            val latitudes = DoubleBuilder()
            val longitudes = DoubleBuilder()
            val nameOffsets = IntBuilder()
            val nameBlob = ByteArrayOutputStream(1 shl 19)
            val countryIndexes = ShortBuilder()
            val regionIndexes = ShortBuilder()
            val countryTable = InternTable()
            val regionTable = InternTable()
            regionTable.indexOf("") // slot 0 means "no region"
            val regionCodes = ArrayList<String>().apply { add("") }
            val regionNames = ArrayList<String?>().apply { add(null) }

            val header = readTable("places.tsv", places) { line, lineNumber ->
                val fields = line.split('\t')
                if (fields.size != 5) throw malformed("places.tsv", lineNumber, line)
                val latitude = fields[0].toDoubleOrNull()
                val longitude = fields[1].toDoubleOrNull()
                val name = fields[2]
                val countryCode = fields[3]
                val admin1Code = fields[4]
                if (latitude == null || longitude == null || !isValidCoordinate(latitude, longitude) ||
                    name.isBlank() || countryCode.length != 2
                ) {
                    throw malformed("places.tsv", lineNumber, line)
                }

                latitudes.add(latitude)
                longitudes.add(longitude)
                nameOffsets.add(nameBlob.size())
                nameBlob.write(name.toByteArray(Charsets.UTF_8))
                countryIndexes.add(countryTable.indexOf(countryCode))

                if (admin1Code.isEmpty() || admin1Code == "00") {
                    regionIndexes.add(0)
                } else {
                    val key = "$countryCode.$admin1Code"
                    val before = regionTable.size
                    val index = regionTable.indexOf(key)
                    if (regionTable.size > before) {
                        regionCodes.add(admin1Code)
                        regionNames.add(admin1Names[key])
                    }
                    regionIndexes.add(index)
                }
                if (countryTable.size > MAX_INTERNED || regionTable.size > MAX_INTERNED) {
                    throw GazetteerLoadException("places.tsv has too many distinct country or region codes")
                }
            }
            nameOffsets.add(nameBlob.size())

            val latitudeArray = latitudes.toArray()
            val longitudeArray = longitudes.toArray()
            val grid = buildGrid(latitudeArray, longitudeArray)
            return Gazetteer(
                latitudes = latitudeArray,
                longitudes = longitudeArray,
                nameOffsets = nameOffsets.toArray(),
                nameBytes = nameBlob.toByteArray(),
                countryIndex = countryIndexes.toArray(),
                regionIndex = regionIndexes.toArray(),
                countryCodes = countryTable.toArray(),
                regionCodes = regionCodes.toTypedArray(),
                regionNames = regionNames.toTypedArray(),
                countryNamesByCode = countryNames,
                cellStart = grid.first,
                cellRows = grid.second,
                capKm = capKm,
                datasetVersion = header.version
            )
        }

        // Counting sort by cell keeps rows ascending within a cell.
        private fun buildGrid(latitudes: DoubleArray, longitudes: DoubleArray): Pair<IntArray, IntArray> {
            val cellCount = LATITUDE_ROWS * LONGITUDE_COLUMNS
            val cellOf = IntArray(latitudes.size) {
                latitudeRowOf(latitudes[it]) * LONGITUDE_COLUMNS + longitudeColumnOf(longitudes[it])
            }
            val cellStart = IntArray(cellCount + 1)
            for (cell in cellOf) cellStart[cell + 1]++
            for (i in 0 until cellCount) cellStart[i + 1] += cellStart[i]
            val fill = cellStart.copyOf(cellCount)
            val cellRows = IntArray(latitudes.size)
            for (row in cellOf.indices) cellRows[fill[cellOf[row]]++] = row
            return cellStart to cellRows
        }

        /** `admin1.tsv` as a `CC.code` to English name map; shared with [GazetteerNames]. */
        internal fun readAdmin1Names(source: Reader): Map<String, String> {
            val names = HashMap<String, String>()
            readTable("admin1.tsv", source) { line, lineNumber ->
                val fields = line.split('\t')
                val key = fields.firstOrNull().orEmpty()
                if (fields.size != 2 || key.length < 4 || key[2] != '.' || fields[1].isEmpty()) {
                    throw malformed("admin1.tsv", lineNumber, line)
                }
                names[key] = fields[1]
            }
            return names
        }

        /** `countries.tsv` as an ISO-2 to English name map; shared with [GazetteerNames]. */
        internal fun readCountryNames(source: Reader): Map<String, String> {
            val names = HashMap<String, String>()
            readTable("countries.tsv", source) { line, lineNumber ->
                val fields = line.split('\t')
                if (fields.size != 2 || fields[0].length != 2 || fields[1].isEmpty()) {
                    throw malformed("countries.tsv", lineNumber, line)
                }
                names[fields[0]] = fields[1]
            }
            return names
        }

        private class TableHeader(val version: Int, val rowsWritten: Int)

        // Reads `# key: value` header lines, then data rows, and checks the declared version and row count.
        private fun readTable(name: String, source: Reader, onRow: (String, Int) -> Unit): TableHeader {
            val tracked = EndsWithNewlineReader(source)
            val reader = BufferedReader(tracked)
            val headerValues = HashMap<String, String>()
            var rows = 0
            var lineNumber = 0
            var inHeader = true
            while (true) {
                val line = reader.readLine() ?: break
                lineNumber++
                if (inHeader && line.startsWith("#")) {
                    val body = line.removePrefix("#").trim()
                    val colon = body.indexOf(':')
                    if (colon > 0) headerValues.putIfAbsent(body.substring(0, colon).trim(), body.substring(colon + 1).trim())
                    continue
                }
                inHeader = false
                if (line.isEmpty() || line.startsWith("#")) throw malformed(name, lineNumber, line)
                onRow(line, lineNumber)
                rows++
            }

            if (lineNumber == 0) throw GazetteerLoadException("$name is empty")
            if (!tracked.endedWithNewline) throw GazetteerLoadException("$name is truncated: last line has no terminator")
            val version = headerValues["dataset-version"]?.toIntOrNull()
                ?: throw GazetteerLoadException("$name has no valid '# dataset-version:' header")
            val declared = headerValues["rows-written"]?.toIntOrNull()
                ?: throw GazetteerLoadException("$name has no valid '# rows-written:' header")
            if (version != PLACE_RESOLVER_VERSION) {
                throw GazetteerLoadException("$name is dataset-version $version, resolver expects $PLACE_RESOLVER_VERSION")
            }
            if (rows == 0) throw GazetteerLoadException("$name has no data rows")
            if (rows < declared) throw GazetteerLoadException("$name is truncated: $rows rows, header declares $declared")
            if (rows > declared) throw GazetteerLoadException("$name has $rows rows, header declares only $declared")
            return TableHeader(version, declared)
        }

        private fun malformed(name: String, lineNumber: Int, line: String) =
            GazetteerLoadException("$name line $lineNumber is malformed: '${line.take(80)}'")

        // Invalid bytes must fail the load rather than become U+FFFD inside a place name.
        internal fun strictUtf8(stream: InputStream): Reader = InputStreamReader(
            stream,
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        )
    }
}

/**
 * Region and country names only, from `admin1.tsv` and `countries.tsv`. Never reads `places.tsv`,
 * which costs about half a second on a device; the album picker needs names, not coordinates.
 */
class GazetteerNames private constructor(
    private val admin1NamesByKey: Map<String, String>,
    private val countryNamesByCode: Map<String, String>
) {
    /** The GeoNames `name` form of the admin1 region for [countryCode] and [admin1Code], or null if the asset has none. */
    fun admin1Name(countryCode: String, admin1Code: String): String? = admin1NamesByKey["$countryCode.$admin1Code"]

    /** English country name from `countries.tsv`. */
    fun englishCountryName(countryCode: String): String? = countryNamesByCode[countryCode]

    /**
     * Country name for [locale] from the platform; falls back to the English name, then to [countryCode].
     * The platform returns the code itself, or nothing, for a region it has no name for.
     */
    fun countryDisplayName(countryCode: String, locale: Locale): String {
        val platformName = try {
            Locale.Builder().setRegion(countryCode).build().getDisplayCountry(locale)
        } catch (e: IllformedLocaleException) {
            ""
        }
        if (platformName.isNotBlank() && !platformName.equals(countryCode, ignoreCase = true)) return platformName
        return countryNamesByCode[countryCode] ?: countryCode
    }

    companion object {
        fun load(admin1: InputStream, countries: InputStream): GazetteerNames =
            load(Gazetteer.strictUtf8(admin1), Gazetteer.strictUtf8(countries))

        /** Same validation as [Gazetteer.load] for these two assets; throws [GazetteerLoadException]. Does not close the readers. */
        fun load(admin1: Reader, countries: Reader): GazetteerNames =
            GazetteerNames(Gazetteer.readAdmin1Names(admin1), Gazetteer.readCountryNames(countries))
    }
}

// A cut-off download usually ends mid-line; the generator terminates every line.
private class EndsWithNewlineReader(private val inner: Reader) : Reader() {
    var endedWithNewline = true
        private set

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        val count = inner.read(cbuf, off, len)
        if (count > 0) endedWithNewline = cbuf[off + count - 1] == '\n'
        return count
    }

    override fun close() = inner.close()
}

private class InternTable {
    private val indexes = HashMap<String, Int>()
    private val values = ArrayList<String>()
    val size: Int get() = values.size

    fun indexOf(value: String): Int = indexes.getOrPut(value) {
        values.add(value)
        values.size - 1
    }

    fun toArray(): Array<String> = values.toTypedArray()
}

private class DoubleBuilder {
    private var data = DoubleArray(1 shl 15)
    private var size = 0
    fun add(value: Double) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }
    fun toArray(): DoubleArray = data.copyOf(size)
}

private class IntBuilder {
    private var data = IntArray(1 shl 15)
    private var size = 0
    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }
    fun toArray(): IntArray = data.copyOf(size)
}

private class ShortBuilder {
    private var data = ShortArray(1 shl 15)
    private var size = 0
    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value.toShort()
    }
    fun toArray(): ShortArray = data.copyOf(size)
}
