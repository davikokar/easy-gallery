package com.davide.seddio.easygallery.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumRuleSummaryTest {

    private val names = mapOf("IT" to "Italy", "SG" to "Singapore", "FR" to "France")
    private val countryName: (String) -> String = { code -> names[code] ?: code }

    private fun summarize(rule: AlbumRule?) = summarizeAlbumRule(rule, countryName)

    private fun rule(vararg clauses: AlbumRuleClause) = AlbumRule.Evaluatable(clauses.toList())

    private fun time(from: String?, to: String?) =
        AlbumRuleClause.Time(from?.let(LocalDate::parse), to?.let(LocalDate::parse))

    private fun dates(summary: AlbumRuleSummary): List<DatePeriod> =
        (summary as AlbumRuleSummary.Automatic).conditions
            .filterIsInstance<AlbumRuleCondition.Dates>()
            .map { it.period }

    private fun places(summary: AlbumRuleSummary): List<AlbumRuleCondition.Place> =
        (summary as AlbumRuleSummary.Automatic).conditions.filterIsInstance<AlbumRuleCondition.Place>()

    @Test
    fun `no stored rule is a manual album`() {
        assertEquals(AlbumRuleSummary.Manual, summarize(null))
    }

    @Test
    fun `a rule this build cannot read is unreadable and the stored text is not carried`() {
        val raw = "{\"version\":2,\"all\":[{\"type\":\"people\",\"secret\":\"do-not-show\"}]}"
        val parsed = AlbumRuleJson.parse(raw)
        assertTrue(parsed is AlbumRule.Unevaluatable)

        val summary = summarize(parsed)

        assertEquals(AlbumRuleSummary.Unreadable, summary)
        assertFalse(summary.toString().contains("do-not-show"))
        assertFalse(summary.toString().contains("people"))
    }

    @Test
    fun `every reason a rule is unreadable gives the same summary`() {
        val unreadable = listOf(
            "", "null", "not json", "{\"version\":1,\"all\":[]}",
            "{\"version\":1,\"all\":[{\"type\":\"time\",\"from\":\"2025-13-01\"}]}",
            "{\"version\":1,\"all\":[{\"type\":\"region\",\"dataset\":${PLACE_RESOLVER_VERSION + 1},\"country\":\"IT\"}]}"
        )

        unreadable.forEach { raw ->
            assertEquals(raw, AlbumRuleSummary.Unreadable, summarize(AlbumRuleJson.parse(raw)))
        }
    }

    @Test
    fun `a country is summarised by its display name`() {
        val summary = summarize(rule(AlbumRuleClause.Region(country = "IT")))

        assertEquals(
            listOf(AlbumRuleCondition.Place(PlaceScope.COUNTRY, "IT", listOf("Italy"))),
            places(summary)
        )
    }

    @Test
    fun `a region is summarised by its name then its country`() {
        val region = AlbumRuleClause.Region(country = "IT", admin1 = "09", admin1Name = "Lombardy")

        assertEquals(
            listOf(AlbumRuleCondition.Place(PlaceScope.REGION, "IT", listOf("Lombardy", "Italy"))),
            places(summarize(rule(region)))
        )
    }

    @Test
    fun `a single place is summarised by place then region then country`() {
        val place = AlbumRuleClause.Region(country = "IT", admin1 = "09", admin1Name = "Lombardy", place = "Milan")

        assertEquals(
            listOf(AlbumRuleCondition.Place(PlaceScope.PLACE, "IT", listOf("Milan", "Lombardy", "Italy"))),
            places(summarize(rule(place)))
        )
    }

    @Test
    fun `a place with no region and the same name as its country is not repeated`() {
        val singapore = AlbumRuleClause.Region(country = "SG", place = "Singapore")

        assertEquals(
            listOf(AlbumRuleCondition.Place(PlaceScope.PLACE, "SG", listOf("Singapore"))),
            places(summarize(rule(singapore)))
        )
    }

    @Test
    fun `a place with no region keeps its country`() {
        val place = AlbumRuleClause.Region(country = "FR", place = "Lyon")

        assertEquals(listOf("Lyon", "France"), places(summarize(rule(place))).single().names)
    }

    @Test
    fun `the country code stands in when no name is known`() {
        val summary = summarizeAlbumRule(rule(AlbumRuleClause.Region(country = "IT"))) { code -> code }

        assertEquals(listOf("IT"), places(summary).single().names)
    }

    @Test
    fun `the chip label is the first name`() {
        val place = AlbumRuleClause.Region(country = "IT", admin1 = "09", admin1Name = "Lombardy", place = "Milan")
        val region = place.copy(place = null)
        val country = AlbumRuleClause.Region(country = "IT")

        assertEquals("Milan", placeChoiceLabel(place, "Italy"))
        assertEquals("Lombardy", placeChoiceLabel(region, "Italy"))
        assertEquals("Italy", placeChoiceLabel(country, "Italy"))
        assertEquals("IT", placeChoiceLabel(country, null))
    }

    @Test
    fun `a whole year is a year`() {
        val summary = summarize(rule(time("2026-01-01", "2026-12-31")))

        assertEquals(listOf<DatePeriod>(DatePeriod.Year(2026)), dates(summary))
    }

    @Test
    fun `a whole month is a month of a year`() {
        val summary = summarize(rule(time("2025-12-01", "2025-12-31")))

        assertEquals(listOf<DatePeriod>(DatePeriod.Month(2025, 12)), dates(summary))
    }

    @Test
    fun `february ends on the 29th in a leap year and the 28th otherwise`() {
        assertEquals(
            listOf<DatePeriod>(DatePeriod.Month(2024, 2)),
            dates(summarize(rule(time("2024-02-01", "2024-02-29"))))
        )
        assertEquals(
            listOf<DatePeriod>(DatePeriod.Month(2025, 2)),
            dates(summarize(rule(time("2025-02-01", "2025-02-28"))))
        )
        // Only the 28th of a leap february is a range, not a month.
        assertEquals(
            listOf<DatePeriod>(DatePeriod.Between(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 28))),
            dates(summarize(rule(time("2024-02-01", "2024-02-28"))))
        )
    }

    @Test
    fun `a single day is a day`() {
        assertEquals(
            listOf<DatePeriod>(DatePeriod.Day(LocalDate.of(2025, 3, 8))),
            dates(summarize(rule(time("2025-03-08", "2025-03-08"))))
        )
    }

    @Test
    fun `a range with both bounds that is neither a year nor a month stays a range`() {
        val summary = summarize(
            rule(
                time("2025-03-15", "2025-06-10"),
                time("2023-01-01", "2024-12-31"),
                time("2025-01-01", "2025-03-31")
            )
        )

        assertEquals(
            listOf<DatePeriod>(
                DatePeriod.Between(LocalDate.of(2025, 3, 15), LocalDate.of(2025, 6, 10)),
                DatePeriod.Between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31)),
                DatePeriod.Between(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 12, 31))
            ),
            dates(summary)
        )
    }

    @Test
    fun `an open ended range is since or until`() {
        assertEquals(
            listOf<DatePeriod>(DatePeriod.Since(LocalDate.of(2025, 6, 1))),
            dates(summarize(rule(time("2025-06-01", null))))
        )
        assertEquals(
            listOf<DatePeriod>(DatePeriod.Until(LocalDate.of(2020, 12, 31))),
            dates(summarize(rule(time(null, "2020-12-31"))))
        )
    }

    @Test
    fun `an open ended range read from stored text is summarised`() {
        val parsed = AlbumRuleJson.parse("{\"version\":1,\"all\":[{\"type\":\"time\",\"to\":\"2020-12-31\"}]}")

        assertEquals(listOf<DatePeriod>(DatePeriod.Until(LocalDate.of(2020, 12, 31))), dates(summarize(parsed)))
    }

    @Test
    fun `dates are listed newest first whatever order they were stored in`() {
        val summary = summarize(
            rule(
                time("2024-01-01", "2024-01-31"),
                time("2023-01-01", "2023-12-31"),
                time("2025-12-01", "2025-12-31"),
                time("2026-01-01", "2026-12-31")
            )
        )

        assertEquals(
            listOf<DatePeriod>(
                DatePeriod.Year(2026),
                DatePeriod.Month(2025, 12),
                DatePeriod.Month(2024, 1),
                DatePeriod.Year(2023)
            ),
            dates(summary)
        )
    }

    @Test
    fun `a year is listed before the months inside it and an open end before a closed one`() {
        val summary = summarize(
            rule(
                time("2025-03-01", "2025-03-31"),
                time("2025-01-01", "2025-12-31"),
                time("2024-01-01", null),
                time(null, "2025-12-31")
            )
        )

        assertEquals(
            listOf<DatePeriod>(
                DatePeriod.Since(LocalDate.of(2024, 1, 1)),
                DatePeriod.Year(2025),
                DatePeriod.Until(LocalDate.of(2025, 12, 31)),
                DatePeriod.Month(2025, 3)
            ),
            dates(summary)
        )
    }

    @Test
    fun `several conditions are all kept with places first and dates after`() {
        val summary = summarize(
            rule(
                time("2024-02-01", "2024-02-29"),
                AlbumRuleClause.Region(country = "FR"),
                time("2025-01-01", "2025-12-31"),
                AlbumRuleClause.Region(country = "IT", admin1 = "09", admin1Name = "Lombardy")
            )
        )

        assertEquals(
            AlbumRuleSummary.Automatic(
                listOf(
                    AlbumRuleCondition.Place(PlaceScope.COUNTRY, "FR", listOf("France")),
                    AlbumRuleCondition.Place(PlaceScope.REGION, "IT", listOf("Lombardy", "Italy")),
                    AlbumRuleCondition.Dates(DatePeriod.Year(2025)),
                    AlbumRuleCondition.Dates(DatePeriod.Month(2024, 2))
                )
            ),
            summary
        )
    }

    @Test
    fun `a place and a date read from stored text are both summarised`() {
        val parsed = AlbumRuleJson.parse(
            AlbumRuleJson.encode(
                rule(
                    AlbumRuleClause.Region(country = "IT", admin1 = "09", admin1Name = "Lombardy", place = "Milan"),
                    time("2025-12-01", "2025-12-31")
                )
            )
        )

        assertEquals(
            AlbumRuleSummary.Automatic(
                listOf(
                    AlbumRuleCondition.Place(PlaceScope.PLACE, "IT", listOf("Milan", "Lombardy", "Italy")),
                    AlbumRuleCondition.Dates(DatePeriod.Month(2025, 12))
                )
            ),
            summarize(parsed)
        )
    }
}
