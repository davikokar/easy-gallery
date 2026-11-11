package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Random

private val REAL_ASSET_DIR = File("src/main/assets")

// One parse of the real assets serves every test in the class.
private val realGazetteer: Gazetteer by lazy {
    fun open(asset: String) = File(REAL_ASSET_DIR, asset).inputStream()
    open(GazetteerAssets.PLACES).use { places ->
        open(GazetteerAssets.ADMIN1).use { admin1 ->
            open(GazetteerAssets.COUNTRIES).use { countries -> Gazetteer.load(places, admin1, countries) }
        }
    }
}

private val realNames: GazetteerNames by lazy {
    GazetteerAssets.loadNames { File(REAL_ASSET_DIR, it).inputStream() }
}

class AutomaticAlbumFacetsTest {

    // --- what is listed -----------------------------------------------------------------------

    @Test
    fun `countries list only items with place values and count pending items under their old values`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT", "16", "Florence")),
            item(2, JUNE_10, pendingOld("IT", "16", "Florence")),
            item(3, JUNE_10, LocationState.ResolvedOutOfCap),
            item(4, JUNE_10, LocationState.NoCoordinates),
            item(5, JUNE_10, LocationState.PendingWithoutValues),
            unreached(6)
        )

        assertEquals(listOf(CountryFacet("IT", 2)), facets(join).countries(time = null))
    }

    @Test
    fun `countries are filtered by the date choice and an undated item never matches it`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT")),
            item(2, epoch("2020-01-01T12:00:00Z"), resolved("IT")),
            item(3, null, resolved("IT")),
            item(4, JUNE_10, resolved("FR"))
        )

        val inTwentyFive = facets(join).countries(year(2025))

        assertEquals(listOf(CountryFacet("FR", 1), CountryFacet("IT", 1)), inTwentyFive)
        assertEquals(listOf(CountryFacet("FR", 1), CountryFacet("IT", 3)), facets(join).countries(time = null))
    }

    @Test
    fun `a country breaks down into regions with names and a browsable group of places with no region`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT", "16", "Florence")),
            item(2, JUNE_10, resolved("IT", "16", "Pisa")),
            item(3, JUNE_10, resolved("IT", "07", "Rome")),
            item(4, JUNE_10, resolved("IT", null, "Vatican City")),
            item(5, JUNE_10, resolved("IT", null, null)),
            item(6, JUNE_10, resolved("FR", "11", "Paris"))
        )

        val italy = facets(join).country("IT", time = null)

        assertEquals(5, italy.allCount)
        assertEquals(
            listOf(RegionFacet("07", "Lazio", 1), RegionFacet("16", "Tuscany", 2)),
            italy.regions
        )
        // The place with no name counts in the group but cannot be listed.
        assertEquals(PlaceGroup(2, listOf(PlaceFacet("Vatican City", 1))), italy.placesWithoutRegion)
    }

    @Test
    fun `a region the names asset does not know is not listed but still counts under its country`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT", "99", "Nowhere")),
            item(2, JUNE_10, resolved("IT", "16", "Florence"))
        )

        val italy = facets(join).country("IT", time = null)

        assertEquals(2, italy.allCount)
        assertEquals(listOf(RegionFacet("16", "Tuscany", 1)), italy.regions)
    }

    @Test
    fun `values that cannot form a region clause are not listed`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT", "16", "Florence")),
            item(2, JUNE_10, resolved("it", "16", "Florence")),
            item(3, JUNE_10, resolved("ITA", null, "Rome")),
            item(4, JUNE_10, resolved("", null, "Nowhere")),
            item(5, JUNE_10, resolved("IT", "", "Rome")),
            item(6, JUNE_10, resolved("IT", null, "")),
            item(7, JUNE_10, resolved("IT", "16", "")),
            item(8, JUNE_10, resolved("IT", "17", "Pisa"))
        )
        val names = { country: String, code: String ->
            when (code) {
                "" -> "Nameless"
                "17" -> ""
                else -> NAMES["$country.$code"]
            }
        }
        val facets = facets(join, UTC, names)

        val italy = facets.country("IT", time = null)

        assertEquals(listOf(CountryFacet("IT", 5)), facets.countries(time = null))
        assertEquals(5, italy.allCount)
        assertEquals(listOf(RegionFacet("16", "Tuscany", 2)), italy.regions)
        assertEquals(PlaceGroup(1, emptyList()), italy.placesWithoutRegion)
        assertEquals(listOf(PlaceFacet("Florence", 1)), facets.places("IT", "16", time = null))
        for (code in listOf("it", "ITA", "")) {
            assertEquals(CountryBreakdown(0, emptyList(), PlaceGroup(0, emptyList())), facets.country(code, time = null))
        }
    }

    @Test
    fun `a region lists its places with counts`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT", "16", "Pisa")),
            item(2, JUNE_10, resolved("IT", "16", "Florence")),
            item(3, JUNE_10, resolved("IT", "16", "Florence")),
            item(4, JUNE_10, resolved("IT", "16", null)),
            item(5, JUNE_10, resolved("IT", "07", "Rome"))
        )

        assertEquals(
            listOf(PlaceFacet("Florence", 2), PlaceFacet("Pisa", 1)),
            facets(join).places("IT", "16", time = null)
        )
    }

    @Test
    fun `years list only dated items newest first and are filtered by the place choice`() {
        val join = joinOf(
            item(1, epoch("2023-05-01T12:00:00Z"), resolved("IT")),
            item(2, epoch("2025-05-01T12:00:00Z"), resolved("IT")),
            item(3, epoch("2025-06-01T12:00:00Z"), resolved("FR")),
            item(4, null, resolved("IT")),
            item(5, epoch("2024-05-01T12:00:00Z"), LocationState.NoCoordinates),
            unreached(6)
        )

        assertEquals(
            listOf(YearFacet(2025, 2), YearFacet(2024, 1), YearFacet(2023, 1)),
            facets(join).years(region = null)
        )
        assertEquals(
            listOf(YearFacet(2025, 1), YearFacet(2023, 1)),
            facets(join).years(AlbumRuleClause.Region("IT"))
        )
    }

    @Test
    fun `months list a year January first and read the date in the injected zone`() {
        val join = joinOf(
            item(1, epoch("2025-05-31T20:00:00Z"), resolved("IT")), // 06-01 in Tokyo
            item(2, epoch("2025-02-10T12:00:00Z"), resolved("IT")),
            item(3, epoch("2025-02-11T12:00:00Z"), resolved("FR")),
            item(4, epoch("2024-02-11T12:00:00Z"), resolved("IT"))
        )

        assertEquals(
            listOf(MonthFacet(2, 2), MonthFacet(5, 1)),
            facets(join, UTC).months(2025, region = null)
        )
        assertEquals(
            listOf(MonthFacet(2, 2), MonthFacet(6, 1)),
            facets(join, TOKYO).months(2025, region = null)
        )
        assertEquals(
            listOf(MonthFacet(2, 1), MonthFacet(5, 1)),
            facets(join, UTC).months(2025, AlbumRuleClause.Region("IT"))
        )
    }

    @Test
    fun `no choice at all is not a rule and matches nothing`() {
        val join = joinOf(item(1, JUNE_10, resolved("IT")))

        assertEquals(0, facets(join).matchCount(region = null, time = null))
    }

    @Test
    fun `the match count is the items both choices would make members`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT")),
            item(2, JUNE_10, pendingOld("IT")),
            item(3, JUNE_10, resolved("FR")),
            item(4, epoch("2020-01-01T12:00:00Z"), resolved("IT"))
        )

        val facets = facets(join)

        assertEquals(2, facets.matchCount(AlbumRuleClause.Region("IT"), year(2025)))
        assertEquals(3, facets.matchCount(AlbumRuleClause.Region("IT"), time = null))
        assertEquals(3, facets.matchCount(region = null, time = year(2025)))
    }

    // --- coverage -----------------------------------------------------------------------------

    @Test
    fun `coverage counts what indexing reached and found nothing in, never pending or not indexed items`() {
        val join = joinOf(
            item(1, JUNE_10, resolved("IT")),
            item(2, JUNE_10, LocationState.NoCoordinates),
            item(3, null, LocationState.ResolvedOutOfCap),
            item(4, null, pendingOld("IT")),
            item(5, JUNE_10, LocationState.PendingWithoutValues),
            unreached(6)
        )

        assertEquals(MediaIndexCoverage(withoutLocation = 2, withoutDate = 2), join.coverage())
    }

    @Test
    fun `coverage of an empty library is zero`() {
        assertEquals(MediaIndexCoverage(0, 0), joinOf().coverage())
    }

    @Test
    fun `coverage on a built join excludes pending and not indexed items`() {
        val live = (1..6).map { liveItem(it) }
        val rows = listOf(
            row(live[0], JUNE_10, country = "IT", marker = PLACE_RESOLVER_VERSION),
            row(live[1], JUNE_10, hasCoordinates = false),
            row(live[2], null, marker = PLACE_RESOLVER_VERSION), // out of cap, undated
            row(live[3], JUNE_10, country = "IT", marker = null), // pending
            row(live[4], JUNE_10, marker = null) // pending without values
            // live[5] is not indexed
        )

        val coverage = MediaIndexLiveJoin.build(rows, live).coverage()

        assertEquals(MediaIndexCoverage(withoutLocation = 2, withoutDate = 1), coverage)
    }

    // --- facet consistency on real data -------------------------------------------------------

    @Test
    fun `every entry the picker offers counts what the evaluator makes members, on real gazetteer data`() {
        val join = realisticJoin()

        for (zone in listOf(UTC, TOKYO, NEW_YORK)) {
            val facets = facets(join, zone, realNames::admin1Name)
            val timeChoices = listOf(null, year(2024), month(2024, 3), month(2025, 1))
            var offeredEntries = 0

            for (time in timeChoices) {
                val countries = facets.countries(time)
                for (country in countries) {
                    offeredEntries++
                    assertCount(join, zone, country.count, listOfNotNull(AlbumRuleClause.Region(country.countryCode), time))

                    val breakdown = facets.country(country.countryCode, time)
                    assertEquals(country.count, breakdown.allCount)
                    assertEquals(
                        breakdown.allCount,
                        breakdown.regions.sumOf { it.count } + breakdown.placesWithoutRegion.count
                    )
                    for (region in breakdown.regions) {
                        offeredEntries++
                        val clause = AlbumRuleClause.Region(country.countryCode, region.admin1Code, region.admin1Name)
                        assertCount(join, zone, region.count, listOfNotNull(clause, time))

                        for (place in facets.places(country.countryCode, region.admin1Code, time)) {
                            offeredEntries++
                            val placeClause = AlbumRuleClause.Region(
                                country.countryCode, region.admin1Code, region.admin1Name, place.placeName
                            )
                            assertCount(join, zone, place.count, listOfNotNull(placeClause, time))
                        }
                    }
                    for (place in breakdown.placesWithoutRegion.places) {
                        offeredEntries++
                        val clause = AlbumRuleClause.Region(country.countryCode, place = place.placeName)
                        assertCount(join, zone, place.count, listOfNotNull(clause, time))
                    }
                }
            }

            val regionChoices = regionChoicesOffered(facets)
            for (region in regionChoices) {
                for (year in facets.years(region)) {
                    offeredEntries++
                    assertCount(join, zone, year.count, listOfNotNull(region, year(year.year)))
                    val months = facets.months(year.year, region)
                    assertEquals(year.count, months.sumOf { it.count })
                    for (month in months) {
                        offeredEntries++
                        assertCount(join, zone, month.count, listOfNotNull(region, month(year.year, month.month)))
                    }
                }
                for (time in timeChoices) {
                    if (region == null && time == null) continue
                    val clauses = listOfNotNull(region, time)
                    assertCount(join, zone, facets.matchCount(region, time), clauses, requirePositive = false)
                }
            }

            assertTrue("zone $zone offered only $offeredEntries entries", offeredEntries > 150)
        }
    }

    @Test
    fun `the realistic data covers every location state and has a country whose places have no region`() {
        val join = realisticJoin()
        val states = join.items.map { it.location::class }.toSet()
        val singapore = facets(join, UTC, realNames::admin1Name).country("SG", time = null)

        assertEquals(
            setOf(
                LocationState.Resolved::class,
                LocationState.ResolvedOutOfCap::class,
                LocationState.NoCoordinates::class,
                LocationState.PendingWithOldValues::class,
                LocationState.PendingWithoutValues::class,
                LocationState.NotIndexed::class
            ),
            states
        )
        assertTrue(singapore.regions.isEmpty())
        assertTrue(singapore.placesWithoutRegion.places.isNotEmpty())
    }

    // --- fixtures -----------------------------------------------------------------------------

    private fun assertCount(
        join: MediaIndexLiveJoin,
        zone: ZoneId,
        shown: Int,
        clauses: List<AlbumRuleClause>,
        requirePositive: Boolean = true
    ) {
        val members = AlbumRuleEvaluator.evaluate(AlbumRule.Evaluatable(clauses), join, emptyList(), zone).members
        // MockK keeps every recorded toString() call; thousands of evaluations over ~800 mocks fill the test heap.
        clearAllMocks(answers = false)
        assertEquals("entry for $clauses in $zone", members.size, shown)
        if (requirePositive) assertTrue("entry for $clauses lists nothing", shown > 0)
    }

    // A null first so the unfiltered picker is covered, then a country, a region, and both kinds of place.
    private fun regionChoicesOffered(facets: AutomaticAlbumFacets): List<AlbumRuleClause.Region?> {
        val italy = facets.country("IT", time = null)
        val tuscany = italy.regions.first { it.admin1Code == "16" }
        val singapore = facets.country("SG", time = null).placesWithoutRegion.places.first()
        return listOf(
            null,
            AlbumRuleClause.Region("IT"),
            AlbumRuleClause.Region("IT", tuscany.admin1Code, tuscany.admin1Name),
            AlbumRuleClause.Region("IT", tuscany.admin1Code, tuscany.admin1Name, facets.places("IT", "16", null).first().placeName),
            AlbumRuleClause.Region("SG", place = singapore.placeName)
        )
    }

    // Real photo-like rows: coordinates resolved by the real gazetteer, then spread over every state.
    private fun realisticJoin(): MediaIndexLiveJoin {
        val gazetteer = realGazetteer
        val random = Random(42)
        val anchors = listOf(
            43.7696 to 11.2558, 43.7228 to 10.4017, 41.9028 to 12.4964, 45.4642 to 9.1900, // Florence, Pisa, Rome, Milan
            48.8566 to 2.3522, 45.7640 to 4.8357, // Paris, Lyon
            1.3521 to 103.8198, 35.6762 to 139.6503, 35.0116 to 135.7681, // Singapore, Tokyo, Kyoto
            64.1466 to -21.9426, 40.7128 to -74.0060, 37.7749 to -122.4194, // Reykjavik, New York, San Francisco
            51.5074 to -0.1278, -33.8688 to 151.2093, // London, Sydney
            0.5 to -30.0 // mid-Atlantic: beyond the cap
        )
        val firstDay = LocalDate.of(2023, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val spanMillis = 800L * 86_400_000L

        val rows = ArrayList<MediaIndexEvaluationProjection>()
        val live = ArrayList<MediaItem>()
        fun add(dateTaken: Long?, hasCoordinates: Boolean, latitude: Double, longitude: Double, roll: Double) {
            val id = live.size + 1
            val item = liveItem(id)
            live += item
            val match = if (hasCoordinates) gazetteer.nearest(latitude, longitude) else null
            val country = match?.countryCode
            val region = match?.regionCode
            val place = match?.placeName
            val reached = when {
                roll < 0.60 -> row(item, dateTaken, hasCoordinates, country, region, place, PLACE_RESOLVER_VERSION)
                roll < 0.72 -> row(item, dateTaken, hasCoordinates, country, region, place, null)
                roll < 0.78 -> row(item, dateTaken, hasCoordinates, null, null, null, null)
                roll < 0.88 -> row(item, dateTaken, hasCoordinates, country, region, place, PLACE_RESOLVER_VERSION + 1)
                else -> null
            }
            if (reached != null) rows += reached
        }

        repeat(700) {
            val (latitude, longitude) = anchors[random.nextInt(anchors.size)]
            val dateTaken = if (random.nextDouble() < 0.15) null else firstDay + (random.nextDouble() * spanMillis).toLong()
            val hasCoordinates = random.nextDouble() >= 0.08
            add(
                dateTaken,
                hasCoordinates,
                latitude + (random.nextDouble() - 0.5) * 0.3,
                longitude + (random.nextDouble() - 0.5) * 0.3,
                random.nextDouble()
            )
        }
        // Instants on and one millisecond before each local month start, in the two zones the test pins.
        for (zone in listOf(TOKYO, NEW_YORK)) {
            for (year in 2023..2025) for (month in 1..12) {
                val start = YearMonth.of(year, month).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                add(start, true, 43.7696, 11.2558, 0.0)
                add(start - 1, true, 43.7696, 11.2558, 0.0)
            }
        }

        return MediaIndexLiveJoin.build(rows, live)
    }

    private fun facets(
        join: MediaIndexLiveJoin,
        zone: ZoneId = UTC,
        admin1NameOf: (String, String) -> String? = { country, code -> NAMES["$country.$code"] }
    ) = AutomaticAlbumFacets(join, zone, admin1NameOf)

    private fun joinOf(vararg items: JoinedMedia) = MediaIndexLiveJoin(items.toList())

    private fun item(id: Int, dateTaken: Long?, location: LocationState) = JoinedMedia(
        item = liveItem(id),
        date = if (dateTaken == null) DateTakenSortState.IndexedWithoutDate else DateTakenSortState.IndexedWithDate(dateTaken),
        location = location
    )

    private fun unreached(id: Int) = JoinedMedia(liveItem(id), DateTakenSortState.NotIndexed, LocationState.NotIndexed)

    private fun resolved(country: String, region: String? = null, place: String? = null) =
        LocationState.Resolved(PlaceValues(country, region, place))

    private fun pendingOld(country: String, region: String? = null, place: String? = null) =
        LocationState.PendingWithOldValues(PlaceValues(country, region, place))

    private fun year(year: Int) =
        AlbumRuleClause.Time(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    private fun month(year: Int, month: Int) =
        AlbumRuleClause.Time(LocalDate.of(year, month, 1), YearMonth.of(year, month).atEndOfMonth())

    private fun epoch(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun row(
        live: MediaItem,
        dateTaken: Long?,
        hasCoordinates: Boolean = true,
        country: String? = null,
        region: String? = null,
        place: String? = null,
        marker: Int? = null
    ) = MediaIndexEvaluationProjection(
        identity = live.toMediaIdentity(),
        capturedDateTakenEpochMillis = dateTaken,
        hasCoordinates = hasCoordinates,
        derivedCountryCode = country,
        derivedRegionCode = region,
        derivedPlaceName = place,
        derivedPlaceResolverVersion = marker
    )

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val JUNE_10 = Instant.parse("2025-06-10T12:00:00Z").toEpochMilli()
        val NAMES = mapOf("IT.16" to "Tuscany", "IT.07" to "Lazio", "FR.11" to "Ile-de-France")

        fun liveItem(id: Int): MediaItem {
            val uri = mockk<Uri>()
            every { uri.toString() } returns "content://media/external/images/media/$id"
            return MediaItem(
                uri = uri,
                name = "IMG_$id.jpg",
                dateAdded = 1_000L + id,
                dateModified = 2_000L + id,
                size = 10_000L + id,
                type = MediaType.IMAGE,
                bucketName = "Camera",
                folderPath = "/storage/emulated/0/DCIM/Camera"
            )
        }
    }
}
