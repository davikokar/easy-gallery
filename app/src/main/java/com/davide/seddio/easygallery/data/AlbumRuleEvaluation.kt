package com.davide.seddio.easygallery.data

import java.time.ZoneId

enum class AlbumEvaluationStatus {
    /** No rule: the members are the manual additions minus exclusions. */
    MANUAL,

    /** Every live item the rule could match has been indexed. */
    COMPLETE,

    /** The live media is not supplied yet, or some live item the rule could match is not indexed. */
    WAITING_ON_INDEX,

    /** The stored rule is not understood by this build; the rule contributes nothing. */
    UNEVALUATABLE
}

data class AlbumRuleEvaluation(
    val members: List<MediaItem>,
    val status: AlbumEvaluationStatus
)

/**
 * Pure album evaluation: `(rule matches ∪ additions) − exclusions` (ADR-0012), with every shown
 * item a live [MediaItem] (ADR-0020). Reads the index and writes nothing back.
 */
object AlbumRuleEvaluator {

    /**
     * [rule] null means a manual album. [join] null means MediaStore has not been read yet:
     * no members, and a rule album is [AlbumEvaluationStatus.WAITING_ON_INDEX] rather than an empty
     * [AlbumEvaluationStatus.COMPLETE]. Manual and unevaluatable albums never depend on the index,
     * so they keep their own status in that case. Orphan and healing write-back stays with
     * [AlbumMembership.resolve].
     */
    fun evaluate(
        rule: AlbumRule?,
        join: MediaIndexLiveJoin?,
        memberships: List<StoredMembership>,
        zone: ZoneId
    ): AlbumRuleEvaluation {
        if (join == null) return evaluateWithoutJoin(rule)
        return evaluate(rule, join, AlbumMembership.resolve(memberships, join.liveMedia), zone)
    }

    /**
     * For a caller that has already resolved the memberships against [join]'s live media and needs
     * that resolution for itself, so the resolve runs once.
     */
    fun evaluate(
        rule: AlbumRule?,
        join: MediaIndexLiveJoin?,
        manual: AlbumMembershipResolution,
        zone: ZoneId
    ): AlbumRuleEvaluation {
        if (join == null) return evaluateWithoutJoin(rule)

        return when (rule) {
            null -> AlbumRuleEvaluation(manual.albumItems, AlbumEvaluationStatus.MANUAL)
            is AlbumRule.Unevaluatable ->
                AlbumRuleEvaluation(manual.albumItems, AlbumEvaluationStatus.UNEVALUATABLE)
            is AlbumRule.Evaluatable -> evaluateRule(rule, join, manual, zone)
        }
    }

    private fun evaluateWithoutJoin(rule: AlbumRule?): AlbumRuleEvaluation {
        val status = when (rule) {
            null -> AlbumEvaluationStatus.MANUAL
            is AlbumRule.Unevaluatable -> AlbumEvaluationStatus.UNEVALUATABLE
            is AlbumRule.Evaluatable -> AlbumEvaluationStatus.WAITING_ON_INDEX
        }
        return AlbumRuleEvaluation(members = emptyList(), status = status)
    }

    // Per item, each clause answers match or no match and settled or not (ADR-0017 Amendment 2).
    private fun evaluateRule(
        rule: AlbumRule.Evaluatable,
        join: MediaIndexLiveJoin,
        manual: AlbumMembershipResolution,
        zone: ZoneId
    ): AlbumRuleEvaluation {
        val tests = rule.clauses.map { it.toTest(zone) }
        val excludedUris = manual.resolvedExclusions.mapTo(HashSet()) { it.resolvedMediaUri }
        val addedUris = manual.resolvedAdditions.mapTo(HashSet()) { it.resolvedMediaUri }

        val members = LinkedHashMap<String, MediaItem>()
        var waitingOnIndex = false
        for (joined in join.items) {
            val uri = joined.item.uri.toString()
            if (uri in excludedUris) continue

            var everyClauseMatches = true
            var someClauseSettledNoMatch = false
            var someClauseUnsettled = false
            for (test in tests) {
                when (test.answer(joined)) {
                    ClauseAnswer.MATCH -> Unit
                    ClauseAnswer.PROVISIONAL_MATCH -> someClauseUnsettled = true
                    ClauseAnswer.SETTLED_NO_MATCH -> {
                        everyClauseMatches = false
                        someClauseSettledNoMatch = true
                    }
                    ClauseAnswer.UNSETTLED_NO_MATCH -> {
                        everyClauseMatches = false
                        someClauseUnsettled = true
                    }
                }
            }

            if (everyClauseMatches) members.putIfAbsent(uri, joined.item)
            // An added item is a member whatever the clauses turn out to say, so it never waits.
            if (someClauseUnsettled && !someClauseSettledNoMatch && uri !in addedUris) waitingOnIndex = true
        }
        for (item in manual.albumItems) {
            members.putIfAbsent(item.uri.toString(), item)
        }

        val status = if (waitingOnIndex) {
            AlbumEvaluationStatus.WAITING_ON_INDEX
        } else {
            AlbumEvaluationStatus.COMPLETE
        }
        return AlbumRuleEvaluation(members = members.values.toList(), status = status)
    }

    private fun AlbumRuleClause.toTest(zone: ZoneId): ClauseTest {
        return when (this) {
            is AlbumRuleClause.Time -> {
                val range = toInstantRange(zone)
                ClauseTest { joined -> timeAnswer(range, joined.date) }
            }
            is AlbumRuleClause.Region -> ClauseTest { joined -> regionAnswer(this, joined.location) }
        }
    }

    private fun timeAnswer(range: InstantRange, date: DateTakenSortState): ClauseAnswer {
        return when (date) {
            is DateTakenSortState.IndexedWithDate ->
                if (range.contains(date.epochMillis)) ClauseAnswer.MATCH else ClauseAnswer.SETTLED_NO_MATCH
            // Indexing will not give it a date.
            DateTakenSortState.IndexedWithoutDate -> ClauseAnswer.SETTLED_NO_MATCH
            DateTakenSortState.NotIndexed -> ClauseAnswer.UNSETTLED_NO_MATCH
        }
    }

    private fun regionAnswer(clause: AlbumRuleClause.Region, location: LocationState): ClauseAnswer {
        return when (location) {
            LocationState.NoCoordinates, LocationState.ResolvedOutOfCap -> ClauseAnswer.SETTLED_NO_MATCH
            is LocationState.Resolved ->
                if (clause.matches(location.values)) ClauseAnswer.MATCH else ClauseAnswer.SETTLED_NO_MATCH
            // Old values match provisionally, but may be wrong, so the item stays unsettled.
            is LocationState.PendingWithOldValues ->
                if (clause.matches(location.values)) {
                    ClauseAnswer.PROVISIONAL_MATCH
                } else {
                    ClauseAnswer.UNSETTLED_NO_MATCH
                }
            LocationState.PendingWithoutValues, LocationState.NotIndexed -> ClauseAnswer.UNSETTLED_NO_MATCH
        }
    }

    private enum class ClauseAnswer { MATCH, PROVISIONAL_MATCH, SETTLED_NO_MATCH, UNSETTLED_NO_MATCH }

    private fun interface ClauseTest {
        fun answer(joined: JoinedMedia): ClauseAnswer
    }
}

/** Every field the clause carries must match; a place without a region pair needs a null region. */
internal fun AlbumRuleClause.Region.matches(values: PlaceValues): Boolean {
    if (values.countryCode != country) return false
    if (admin1 != null && values.regionCode != admin1) return false
    if (place != null) {
        if (values.placeName != place) return false
        if (admin1 == null && values.regionCode != null) return false
    }
    return true
}

internal fun AlbumRuleClause.Time.toInstantRange(zone: ZoneId): InstantRange {
    return InstantRange(
        fromInclusiveMillis = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
        // Next day's start, not +24h: a DST day is 23 or 25 hours long.
        toExclusiveMillis = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    )
}

internal class InstantRange(
    private val fromInclusiveMillis: Long?,
    private val toExclusiveMillis: Long?
) {
    fun contains(epochMillis: Long): Boolean {
        return (fromInclusiveMillis == null || epochMillis >= fromInclusiveMillis) &&
            (toExclusiveMillis == null || epochMillis < toExclusiveMillis)
    }
}
