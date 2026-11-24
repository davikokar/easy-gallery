package com.davide.seddio.easygallery.data

import java.time.ZoneId

enum class AlbumEvaluationStatus {
    /** No rule: the members are the manual additions minus exclusions. */
    MANUAL,

    /** Every live item the rule could match has been indexed. */
    COMPLETE,

    /** The live media is not supplied yet, or some live item the rule could match is not indexed. */
    WAITING_ON_INDEX,

    /**
     * The rule contributes nothing: this build does not understand it, or a `people` clause cannot be evaluated here.
     * [AlbumRuleEvaluation.unevaluatableCause] says which. The members are the additions minus the exclusions.
     */
    UNEVALUATABLE
}

/** Why an evaluation is [AlbumEvaluationStatus.UNEVALUATABLE] (ADR-0017 Amendment 4). */
enum class AlbumUnevaluatableCause {
    RULE_NOT_UNDERSTOOD,
    PEOPLE_UNAVAILABLE_FACE_DATA_OFF,
    PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE,
    ANCHOR_NOT_FOUND
}

/** [unevaluatableCause] is non-null exactly when [status] is [AlbumEvaluationStatus.UNEVALUATABLE]. */
data class AlbumRuleEvaluation(
    val members: List<MediaItem>,
    val status: AlbumEvaluationStatus,
    val unevaluatableCause: AlbumUnevaluatableCause?
) {
    init {
        require((status == AlbumEvaluationStatus.UNEVALUATABLE) == (unevaluatableCause != null)) {
            "an evaluation has a cause exactly when it is unevaluatable"
        }
    }
}

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
     *
     * [people] is what a `people` clause reads; a rule without one never consults it. Null with a `people` clause is
     * not loaded yet, which waits and is never unevaluatable.
     */
    fun evaluate(
        rule: AlbumRule?,
        join: MediaIndexLiveJoin?,
        memberships: List<StoredMembership>,
        zone: ZoneId,
        people: PeopleEvaluationInput?
    ): AlbumRuleEvaluation {
        if (join == null) return evaluateWithoutJoin(rule, people)
        return evaluate(rule, join, AlbumMembership.resolve(memberships, join.liveMedia), zone, people)
    }

    /**
     * For a caller that has already resolved the memberships against [join]'s live media and needs
     * that resolution for itself, so the resolve runs once.
     */
    fun evaluate(
        rule: AlbumRule?,
        join: MediaIndexLiveJoin?,
        manual: AlbumMembershipResolution,
        zone: ZoneId,
        people: PeopleEvaluationInput?
    ): AlbumRuleEvaluation {
        if (join == null) return evaluateWithoutJoin(rule, people)

        return when (rule) {
            null -> AlbumRuleEvaluation(manual.albumItems, AlbumEvaluationStatus.MANUAL, null)
            is AlbumRule.Unevaluatable -> unevaluatable(manual.albumItems, AlbumUnevaluatableCause.RULE_NOT_UNDERSTOOD)
            is AlbumRule.Evaluatable -> {
                val unavailable = peopleUnavailableCause(rule, people)
                if (unavailable != null) unevaluatable(manual.albumItems, unavailable) else evaluateRule(rule, join, manual, zone, people)
            }
        }
    }

    // Unavailability needs no media, so it is told before the join is.
    private fun evaluateWithoutJoin(rule: AlbumRule?, people: PeopleEvaluationInput?): AlbumRuleEvaluation {
        return when (rule) {
            null -> AlbumRuleEvaluation(emptyList(), AlbumEvaluationStatus.MANUAL, null)
            is AlbumRule.Unevaluatable -> unevaluatable(emptyList(), AlbumUnevaluatableCause.RULE_NOT_UNDERSTOOD)
            is AlbumRule.Evaluatable -> {
                val unavailable = peopleUnavailableCause(rule, people)
                if (unavailable != null) {
                    unevaluatable(emptyList(), unavailable)
                } else {
                    AlbumRuleEvaluation(emptyList(), AlbumEvaluationStatus.WAITING_ON_INDEX, null)
                }
            }
        }
    }

    private fun unevaluatable(members: List<MediaItem>, cause: AlbumUnevaluatableCause): AlbumRuleEvaluation {
        return AlbumRuleEvaluation(members, AlbumEvaluationStatus.UNEVALUATABLE, cause)
    }

    private fun peopleUnavailableCause(rule: AlbumRule.Evaluatable, people: PeopleEvaluationInput?): AlbumUnevaluatableCause? {
        if (people == null || rule.clauses.none { it is AlbumRuleClause.People }) return null
        return when (people.availability) {
            PeopleAvailability.AVAILABLE -> null
            PeopleAvailability.FACE_DATA_OFF -> AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_FACE_DATA_OFF
            PeopleAvailability.DEVICE_INCAPABLE -> AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE
        }
    }

    // Per item, each clause answers match or no match and settled or not (ADR-0017 Amendment 2).
    private fun evaluateRule(
        rule: AlbumRule.Evaluatable,
        join: MediaIndexLiveJoin,
        manual: AlbumMembershipResolution,
        zone: ZoneId,
        people: PeopleEvaluationInput?
    ): AlbumRuleEvaluation {
        val tests = ArrayList<ClauseTest>(rule.clauses.size)
        for (clause in rule.clauses) {
            when (clause) {
                is AlbumRuleClause.Time -> {
                    val range = clause.toInstantRange(zone)
                    tests += ClauseTest { joined -> timeAnswer(range, joined.date) }
                }
                is AlbumRuleClause.Region -> tests += ClauseTest { joined -> regionAnswer(clause, joined.location) }
                is AlbumRuleClause.People -> {
                    // One anchor not found makes the album unevaluatable even while another waits.
                    when (val resolution = resolveAnchor(clause, join, people?.faces)) {
                        AnchorResolution.NotFound ->
                            return unevaluatable(manual.albumItems, AlbumUnevaluatableCause.ANCHOR_NOT_FOUND)
                        AnchorResolution.Waiting -> tests += ClauseTest { ClauseAnswer.UNSETTLED_NO_MATCH }
                        is AnchorResolution.Group -> {
                            // Group resolution implies the snapshot was supplied.
                            val faces = checkNotNull(people).faces
                            tests += ClauseTest { joined ->
                                when (peopleItemAnswer(resolution.groupId, joined, join, faces)) {
                                    PeopleItemAnswer.MATCH -> ClauseAnswer.MATCH
                                    PeopleItemAnswer.SETTLED_NO_MATCH -> ClauseAnswer.SETTLED_NO_MATCH
                                    PeopleItemAnswer.UNSETTLED -> ClauseAnswer.UNSETTLED_NO_MATCH
                                }
                            }
                        }
                    }
                }
            }
        }
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
        return AlbumRuleEvaluation(members = members.values.toList(), status = status, unevaluatableCause = null)
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
