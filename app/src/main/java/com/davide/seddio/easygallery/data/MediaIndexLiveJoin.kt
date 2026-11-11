package com.davide.seddio.easygallery.data

/** The place columns of one index row, as stored. */
data class PlaceValues(
    val countryCode: String,
    val regionCode: String?,
    val placeName: String?
)

/** Where a live item stands for a `region` clause (ADR-0017 Amendment 2). */
sealed interface LocationState {

    /** Resolved by the current resolver, with a place. */
    data class Resolved(val values: PlaceValues) : LocationState

    /** Resolved by the current resolver and beyond the cap: no place, and waiting will not give it one. */
    data object ResolvedOutOfCap : LocationState

    /** Indexed, with no coordinates. Waiting will not give it any. */
    data object NoCoordinates : LocationState

    /** Waiting for the resolver (marker null or not current); the values of an earlier pass are kept. */
    data class PendingWithOldValues(val values: PlaceValues) : LocationState

    /** Waiting for the resolver, with no values to match on. */
    data object PendingWithoutValues : LocationState

    /** No index row reached this live item. */
    data object NotIndexed : LocationState
}

/** The values a rule or a picker may match on: current ones, or the old ones a pending row keeps. */
val LocationState.placeValues: PlaceValues?
    get() = when (this) {
        is LocationState.Resolved -> values
        is LocationState.PendingWithOldValues -> values
        LocationState.ResolvedOutOfCap,
        LocationState.NoCoordinates,
        LocationState.PendingWithoutValues,
        LocationState.NotIndexed -> null
    }

/** One live item with what the index says about its date and its place. */
data class JoinedMedia(
    val item: MediaItem,
    val date: DateTakenSortState,
    val location: LocationState
)

/**
 * Library-wide counts of live items the index reached and found nothing usable in (albums.md §8
 * decision 25). Items not indexed, or pending, are in neither: waiting may still change them.
 */
data class MediaIndexCoverage(
    /** Indexed with no coordinates, or resolved beyond the cap. */
    val withoutLocation: Int,
    /** Indexed with no date. */
    val withoutDate: Int
)

/**
 * The index evaluation projection joined to live media, one [JoinedMedia] per live item in live order
 * (ADR-0020). Built once per emission and shared by every album's evaluation and by the picker.
 */
class MediaIndexLiveJoin(val items: List<JoinedMedia>) {

    // The join is shared by albums and the picker, which compute on different threads.
    val liveMedia: List<MediaItem> by lazy { items.map { it.item } }

    // Memoised: every album's list item carries it, and it is a pass over the whole library.
    private val coverage: MediaIndexCoverage by lazy {
        var withoutLocation = 0
        var withoutDate = 0
        for (joined in items) {
            if (joined.date == DateTakenSortState.IndexedWithoutDate) withoutDate++
            when (joined.location) {
                LocationState.NoCoordinates, LocationState.ResolvedOutOfCap -> withoutLocation++
                else -> Unit
            }
        }
        MediaIndexCoverage(withoutLocation = withoutLocation, withoutDate = withoutDate)
    }

    fun coverage(): MediaIndexCoverage = coverage

    companion object {

        /**
         * Both sides go through [canonicalMediaStoreUriForJoin]. A row that only healed onto an item
         * loses to one that matched it directly, and the winning row supplies date and place together.
         */
        fun build(
            projection: List<MediaIndexEvaluationProjection>,
            liveMedia: List<MediaItem>
        ): MediaIndexLiveJoin {
            val lookup = LiveMediaLookup(liveMedia, ::canonicalMediaStoreUriForJoin)
            val direct = HashMap<String, MediaIndexEvaluationProjection>()
            val healed = HashMap<String, MediaIndexEvaluationProjection>()

            for (row in projection) {
                val match = lookup.find(row.identity) ?: continue
                val target = if (match.requiresUriRewrite) healed else direct
                target.putIfAbsent(match.item.uri.toString(), row)
            }

            val items = liveMedia.map { item ->
                val uri = item.uri.toString()
                val row = direct[uri] ?: healed[uri]
                if (row == null) {
                    JoinedMedia(item, DateTakenSortState.NotIndexed, LocationState.NotIndexed)
                } else {
                    JoinedMedia(item, dateStateOf(row), locationStateOf(row))
                }
            }
            return MediaIndexLiveJoin(items)
        }

        private fun dateStateOf(row: MediaIndexEvaluationProjection): DateTakenSortState {
            val dateTaken = row.capturedDateTakenEpochMillis
            return if (dateTaken != null) {
                DateTakenSortState.IndexedWithDate(dateTaken)
            } else {
                DateTakenSortState.IndexedWithoutDate
            }
        }

        private fun locationStateOf(row: MediaIndexEvaluationProjection): LocationState {
            if (!row.hasCoordinates) return LocationState.NoCoordinates

            val country = row.derivedCountryCode
            val values = country?.let { PlaceValues(it, row.derivedRegionCode, row.derivedPlaceName) }
            return if (row.derivedPlaceResolverVersion == PLACE_RESOLVER_VERSION) {
                if (values != null) LocationState.Resolved(values) else LocationState.ResolvedOutOfCap
            } else {
                if (values != null) LocationState.PendingWithOldValues(values) else LocationState.PendingWithoutValues
            }
        }
    }
}
