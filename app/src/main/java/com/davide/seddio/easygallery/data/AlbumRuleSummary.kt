package com.davide.seddio.easygallery.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * What an album's stored rule says, as data for an Album info dialog. It holds no app string, so the
 * UI layer resolves every label: month names, "All of ...", and the notice for [Unreadable].
 */
sealed interface AlbumRuleSummary {

    /** No stored rule: the album holds only what the user added. */
    data object Manual : AlbumRuleSummary

    /**
     * The rule is one this build cannot read. Deliberately carries neither the stored text nor the
     * reason: the text must never be shown, and the UI says what it says for any unreadable rule.
     */
    data object Unreadable : AlbumRuleSummary

    /**
     * A rule is a conjunction, so a member must satisfy **every** one of [conditions]; there is no
     * "or". Places come first, in stored order; dates follow, newest period first.
     */
    data class Automatic(val conditions: List<AlbumRuleCondition>) : AlbumRuleSummary
}

sealed interface AlbumRuleCondition {

    /**
     * [names] run from the most specific to the broadest and are plain names, not app strings:
     * place, region and country for a [PlaceScope.PLACE]; region and country for a [PlaceScope.REGION];
     * the country alone for a [PlaceScope.COUNTRY]. An adjacent repeat (Singapore, Singapore) is dropped.
     */
    data class Place(
        val scope: PlaceScope,
        val countryCode: String,
        val names: List<String>
    ) : AlbumRuleCondition

    data class Dates(val period: DatePeriod) : AlbumRuleCondition
}

enum class PlaceScope { COUNTRY, REGION, PLACE }

/** One `time` clause, in the form a person would say it. Bounds are inclusive calendar dates. */
sealed interface DatePeriod {
    /** January 1 to December 31 of [year]. */
    data class Year(val year: Int) : DatePeriod

    /** The first to the last day of the month, so February of a leap year ends on the 29th. [month] is 1-12. */
    data class Month(val year: Int, val month: Int) : DatePeriod

    data class Day(val date: LocalDate) : DatePeriod

    /** Any other range with both bounds. */
    data class Between(val from: LocalDate, val to: LocalDate) : DatePeriod

    /** Open towards the future: [from] and everything after it. */
    data class Since(val from: LocalDate) : DatePeriod

    /** Open towards the past: everything up to and including [to]. */
    data class Until(val to: LocalDate) : DatePeriod
}

/**
 * Summarises [rule], where null is a manual album. Covers every rule shape [AlbumRuleJson] can read,
 * including combinations the creation screen never writes. [countryName] turns a country code into
 * the name to show, normally `GazetteerNames.countryDisplayName` in the app's locale.
 */
fun summarizeAlbumRule(rule: AlbumRule?, countryName: (countryCode: String) -> String): AlbumRuleSummary {
    return when (rule) {
        null -> AlbumRuleSummary.Manual
        is AlbumRule.Unevaluatable -> AlbumRuleSummary.Unreadable
        is AlbumRule.Evaluatable -> {
            val places = rule.clauses.filterIsInstance<AlbumRuleClause.Region>().map { region ->
                AlbumRuleCondition.Place(
                    scope = region.scope(),
                    countryCode = region.country,
                    names = placeNames(region, countryName(region.country))
                )
            }
            // End date then start date, both newest first: a year ties with its December, and December sorts first.
            val dates = rule.clauses.filterIsInstance<AlbumRuleClause.Time>()
                .sortedWith(
                    compareByDescending<AlbumRuleClause.Time> { it.to ?: LocalDate.MAX }
                        .thenByDescending { it.from ?: LocalDate.MIN }
                )
                .map { AlbumRuleCondition.Dates(it.toPeriod()) }
            AlbumRuleSummary.Automatic(places + dates)
        }
    }
}

/**
 * A region clause's names, most specific first, ending at the country. [countryName] is null when the
 * country's name is not known, in which case the code stands in. The New automatic album screen's
 * choice chips show the first of these.
 */
fun placeNames(region: AlbumRuleClause.Region, countryName: String?): List<String> {
    val names = listOfNotNull(region.place, region.admin1Name, countryName ?: region.country)
    return names.filterIndexed { index, name -> index == 0 || name != names[index - 1] }
}

/** The most specific name a region clause carries. */
fun placeChoiceLabel(region: AlbumRuleClause.Region, countryName: String?): String =
    placeNames(region, countryName).first()

private fun AlbumRuleClause.Region.scope(): PlaceScope = when {
    place != null -> PlaceScope.PLACE
    admin1 != null -> PlaceScope.REGION
    else -> PlaceScope.COUNTRY
}

private fun AlbumRuleClause.Time.toPeriod(): DatePeriod {
    val start = from
    val end = to
    if (start == null) return DatePeriod.Until(requireNotNull(end))
    if (end == null) return DatePeriod.Since(start)

    return when {
        start == end -> DatePeriod.Day(start)
        start.monthValue == 1 && start.dayOfMonth == 1 && end == LocalDate.of(start.year, 12, 31) ->
            DatePeriod.Year(start.year)
        start.dayOfMonth == 1 && end == YearMonth.from(start).atEndOfMonth() ->
            DatePeriod.Month(start.year, start.monthValue)
        else -> DatePeriod.Between(start, end)
    }
}
