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
     * [rule] null means a manual album. [liveMedia] null means MediaStore has not been read yet:
     * no members, and a rule album is [AlbumEvaluationStatus.WAITING_ON_INDEX] rather than an empty
     * [AlbumEvaluationStatus.COMPLETE]. Manual and unevaluatable albums never depend on the index,
     * so they keep their own status in that case. Orphan and healing write-back stays with
     * [AlbumMembership.resolve].
     */
    fun evaluate(
        rule: AlbumRule?,
        indexEntries: List<MediaIndexIdentityDateTaken>,
        liveMedia: List<MediaItem>?,
        memberships: List<StoredMembership>,
        zone: ZoneId
    ): AlbumRuleEvaluation {
        if (liveMedia == null) {
            val status = when (rule) {
                null -> AlbumEvaluationStatus.MANUAL
                is AlbumRule.Unevaluatable -> AlbumEvaluationStatus.UNEVALUATABLE
                is AlbumRule.Evaluatable -> AlbumEvaluationStatus.WAITING_ON_INDEX
            }
            return AlbumRuleEvaluation(members = emptyList(), status = status)
        }

        val manual = AlbumMembership.resolve(memberships, liveMedia)
        return when (rule) {
            null -> AlbumRuleEvaluation(manual.albumItems, AlbumEvaluationStatus.MANUAL)
            is AlbumRule.Unevaluatable ->
                AlbumRuleEvaluation(manual.albumItems, AlbumEvaluationStatus.UNEVALUATABLE)
            is AlbumRule.Evaluatable -> evaluateRule(rule, indexEntries, liveMedia, manual, zone)
        }
    }

    private fun evaluateRule(
        rule: AlbumRule.Evaluatable,
        indexEntries: List<MediaIndexIdentityDateTaken>,
        liveMedia: List<MediaItem>,
        manual: AlbumMembershipResolution,
        zone: ZoneId
    ): AlbumRuleEvaluation {
        val ranges = rule.clauses.map { it.toInstantRange(zone) }
        val stateByLiveUri = dateTakenStateByLiveUri(indexEntries, liveMedia)
        val excludedUris = manual.resolvedExclusions.mapTo(HashSet()) { it.resolvedMediaUri }
        val addedUris = manual.resolvedAdditions.mapTo(HashSet()) { it.resolvedMediaUri }

        val members = LinkedHashMap<String, MediaItem>()
        var waitingOnIndex = false
        for (item in liveMedia) {
            val uri = item.uri.toString()
            if (uri in excludedUris) continue

            when (val state = stateByLiveUri[uri] ?: DateTakenSortState.NotIndexed) {
                is DateTakenSortState.IndexedWithDate ->
                    if (ranges.all { it.contains(state.epochMillis) }) members.putIfAbsent(uri, item)
                // Indexed without a date: never matches, and indexing will not change that.
                DateTakenSortState.IndexedWithoutDate -> Unit
                // An added item is a member whatever its date turns out to be.
                DateTakenSortState.NotIndexed -> if (uri !in addedUris) waitingOnIndex = true
            }
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

    /**
     * Keyed by the live item's own URI. Both sides of the join go through
     * [canonicalMediaStoreUriForJoin]; a row that only healed onto an item loses to a row that
     * matched it directly.
     */
    private fun dateTakenStateByLiveUri(
        indexEntries: List<MediaIndexIdentityDateTaken>,
        liveMedia: List<MediaItem>
    ): Map<String, DateTakenSortState> {
        val lookup = LiveMediaLookup(liveMedia, ::canonicalMediaStoreUriForJoin)
        val direct = HashMap<String, DateTakenSortState>()
        val healed = HashMap<String, DateTakenSortState>()

        for (entry in indexEntries) {
            val match = lookup.find(entry.identity) ?: continue
            val dateTaken = entry.capturedDateTakenEpochMillis
            val state = if (dateTaken != null) {
                DateTakenSortState.IndexedWithDate(dateTaken)
            } else {
                DateTakenSortState.IndexedWithoutDate
            }
            val target = if (match.requiresUriRewrite) healed else direct
            target.putIfAbsent(match.item.uri.toString(), state)
        }

        return healed + direct
    }

    private fun AlbumRuleClause.toInstantRange(zone: ZoneId): InstantRange {
        return when (this) {
            is AlbumRuleClause.Time -> InstantRange(
                fromInclusiveMillis = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                // Next day's start, not +24h: a DST day is 23 or 25 hours long.
                toExclusiveMillis = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            )
        }
    }

    private class InstantRange(
        private val fromInclusiveMillis: Long?,
        private val toExclusiveMillis: Long?
    ) {
        fun contains(epochMillis: Long): Boolean {
            return (fromInclusiveMillis == null || epochMillis >= fromInclusiveMillis) &&
                (toExclusiveMillis == null || epochMillis < toExclusiveMillis)
        }
    }
}
