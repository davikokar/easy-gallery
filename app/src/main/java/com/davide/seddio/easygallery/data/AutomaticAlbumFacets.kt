package com.davide.seddio.easygallery.data

import java.time.Instant
import java.time.ZoneId

data class CountryFacet(val countryCode: String, val count: Int)

data class RegionFacet(val admin1Code: String, val admin1Name: String, val count: Int)

data class PlaceFacet(val placeName: String, val count: Int)

/**
 * Places in one country. [placesWithoutRegion] is browsable, but its [PlaceGroup.count] is not a
 * selection: a `region` clause has no form for "every place here with no region".
 */
data class CountryBreakdown(
    /** "All of {country}": the count for a clause naming the country alone. */
    val allCount: Int,
    val regions: List<RegionFacet>,
    val placesWithoutRegion: PlaceGroup
)

data class PlaceGroup(val count: Int, val places: List<PlaceFacet>)

data class YearFacet(val year: Int, val count: Int)

data class MonthFacet(val month: Int, val count: Int)

/**
 * What the picker offers, counted over the same [MediaIndexLiveJoin] the evaluator reads (ADR-0016
 * Amendment 2). Every count is what [AlbumRuleEvaluator] would make members for the clause the entry
 * writes, combined with the other dimension's current choice and with no manual additions or exclusions.
 * A pending row counts under its old values, as the evaluator matches it; undated, unlocated and
 * out-of-cap items are never listed. Lists are ordered by key; display order is the caller's.
 *
 * [admin1NameOf] gives the GeoNames `name` form a region clause carries. A region it has no name
 * for cannot be written into a rule, so it is not listed; its items still count under the country.
 * Likewise a country that is not two upper-case letters, and an empty region code, name or place, are
 * not listed: a row like that must never be able to fail a clause's constructor in the screen.
 */
class AutomaticAlbumFacets(
    private val join: MediaIndexLiveJoin,
    private val zone: ZoneId,
    private val admin1NameOf: (countryCode: String, admin1Code: String) -> String?
) {

    /** Countries with places, counting only items in [time] when it is given. */
    fun countries(time: AlbumRuleClause.Time?): List<CountryFacet> {
        val range = time?.toInstantRange(zone)
        val counts = HashMap<String, Int>()
        for (joined in join.items) {
            val values = joined.location.placeValues ?: continue
            if (isClauseCountry(values.countryCode) && joined.inDate(range)) counts.merge(values.countryCode, 1, Int::plus)
        }
        return counts.map { CountryFacet(it.key, it.value) }.sortedBy { it.countryCode }
    }

    fun country(countryCode: String, time: AlbumRuleClause.Time?): CountryBreakdown {
        if (!isClauseCountry(countryCode)) return CountryBreakdown(0, emptyList(), PlaceGroup(0, emptyList()))

        val range = time?.toInstantRange(zone)
        var all = 0
        var withoutRegion = 0
        val regionCounts = HashMap<String, Int>()
        val placeCounts = HashMap<String, Int>()
        for (joined in join.items) {
            val values = joined.location.placeValues ?: continue
            if (values.countryCode != countryCode || !joined.inDate(range)) continue

            all++
            if (values.regionCode != null) {
                regionCounts.merge(values.regionCode, 1, Int::plus)
            } else {
                withoutRegion++
                values.placeName?.takeIf { it.isNotEmpty() }?.let { placeCounts.merge(it, 1, Int::plus) }
            }
        }

        val regions = regionCounts.mapNotNull { (code, count) ->
            admin1NameOf(countryCode, code)?.takeIf { code.isNotEmpty() && it.isNotEmpty() }
                ?.let { RegionFacet(code, it, count) }
        }.sortedWith(compareBy({ it.admin1Name }, { it.admin1Code }))
        return CountryBreakdown(
            allCount = all,
            regions = regions,
            placesWithoutRegion = PlaceGroup(withoutRegion, placeCounts.toPlaceFacets())
        )
    }

    /** Places in one region; the region's own count is its [RegionFacet]. */
    fun places(countryCode: String, admin1Code: String, time: AlbumRuleClause.Time?): List<PlaceFacet> {
        val range = time?.toInstantRange(zone)
        val counts = HashMap<String, Int>()
        for (joined in join.items) {
            val values = joined.location.placeValues ?: continue
            val place = values.placeName?.takeIf { it.isNotEmpty() } ?: continue
            if (values.countryCode == countryCode && values.regionCode == admin1Code && joined.inDate(range)) {
                counts.merge(place, 1, Int::plus)
            }
        }
        return counts.toPlaceFacets()
    }

    /** Years with dated items, newest first, counting only items in [region] when it is given. */
    fun years(region: AlbumRuleClause.Region?): List<YearFacet> {
        val counts = HashMap<Int, Int>()
        for (joined in join.items) {
            val localDate = joined.localDate() ?: continue
            if (joined.inPlace(region) && localDate.year in WRITABLE_YEARS) counts.merge(localDate.year, 1, Int::plus)
        }
        return counts.map { YearFacet(it.key, it.value) }.sortedByDescending { it.year }
    }

    /** Months of [year] with dated items, January first, counting only items in [region] when it is given. */
    fun months(year: Int, region: AlbumRuleClause.Region?): List<MonthFacet> {
        val counts = HashMap<Int, Int>()
        for (joined in join.items) {
            val localDate = joined.localDate() ?: continue
            if (localDate.year == year && joined.inPlace(region)) counts.merge(localDate.monthValue, 1, Int::plus)
        }
        return counts.map { MonthFacet(it.key, it.value) }.sortedBy { it.month }
    }

    /** Items the combined choice would make members. No choice at all is not a rule and counts 0. */
    fun matchCount(region: AlbumRuleClause.Region?, time: AlbumRuleClause.Time?): Int {
        if (region == null && time == null) return 0
        val range = time?.toInstantRange(zone)
        return join.items.count { it.inPlace(region) && it.inDate(range) }
    }

    private fun Map<String, Int>.toPlaceFacets(): List<PlaceFacet> {
        return map { PlaceFacet(it.key, it.value) }.sortedBy { it.placeName }
    }

    private fun JoinedMedia.inDate(range: InstantRange?): Boolean {
        if (range == null) return true
        val state = date
        return state is DateTakenSortState.IndexedWithDate && range.contains(state.epochMillis)
    }

    private fun JoinedMedia.inPlace(region: AlbumRuleClause.Region?): Boolean {
        if (region == null) return true
        val values = location.placeValues ?: return false
        return region.matches(values)
    }

    // The calendar date in the device zone, which is how a time clause reads its range.
    private fun JoinedMedia.localDate() = (date as? DateTakenSortState.IndexedWithDate)?.let {
        Instant.ofEpochMilli(it.epochMillis).atZone(zone).toLocalDate()
    }

    private companion object {
        // A time clause can only be written with a four-digit year.
        val WRITABLE_YEARS = 0..9999

        // What a region clause accepts; a row that fails it is never listed, so no clause can fail to build.
        fun isClauseCountry(code: String) = code.length == 2 && code.all { it in 'A'..'Z' }
    }
}
