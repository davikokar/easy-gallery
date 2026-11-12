package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.AlbumNaming
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AutomaticAlbumFacets
import com.davide.seddio.easygallery.data.CountryBreakdown
import com.davide.seddio.easygallery.data.GazetteerNames
import com.davide.seddio.easygallery.data.MediaIndexCoverage
import com.davide.seddio.easygallery.data.MediaIndexLiveJoin
import com.davide.seddio.easygallery.data.MediaIndexingWorkStatus
import com.davide.seddio.easygallery.data.MediaLocationAccessChecker
import com.davide.seddio.easygallery.data.MonthFacet
import com.davide.seddio.easygallery.data.PlaceFacet
import com.davide.seddio.easygallery.data.YearFacet
import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The time choice the picker writes: a whole year or a whole month (ADR-0016 Amendment 2). */
sealed interface TimeSelection {
    val year: Int
    fun toClause(): AlbumRuleClause.Time

    data class Year(override val year: Int) : TimeSelection {
        override fun toClause() = AlbumRuleClause.Time(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    }

    data class Month(override val year: Int, val month: Int) : TimeSelection {
        override fun toClause(): AlbumRuleClause.Time {
            val yearMonth = YearMonth.of(year, month)
            return AlbumRuleClause.Time(yearMonth.atDay(1), yearMonth.atEndOfMonth())
        }
    }
}

/**
 * The draft: what the user has chosen, and which country, region and year the lists are open on.
 * The browsed values only say which lists to compute; they are not part of the rule. [region] is a
 * country, a region, or a place, exactly as the `region` clause can say it.
 */
data class AutomaticAlbumSelection(
    val region: AlbumRuleClause.Region? = null,
    val time: TimeSelection? = null,
    val browsedCountry: String? = null,
    val browsedAdmin1: String? = null,
    val browsedYear: Int? = null
) {
    /** Null until at least one clause is chosen: "no rule" is a manual album, never an empty rule. */
    fun toRule(): AlbumRule.Evaluatable? {
        val clauses = listOfNotNull(region, time?.toClause())
        return if (clauses.isEmpty()) null else AlbumRule.Evaluatable(clauses)
    }
}

data class CountryEntry(val countryCode: String, val displayName: String, val count: Int)

/** Everything the picker lists, counted over one join and cross-filtered by the other dimension's choice. */
data class AutomaticAlbumPickerContent(
    /** Countries with places, by display name in the app's locale. */
    val countries: List<CountryEntry> = emptyList(),
    /** For [AutomaticAlbumSelection.browsedCountry]. */
    val browsedCountry: CountryBreakdown? = null,
    /** For the browsed country and [AutomaticAlbumSelection.browsedAdmin1]. */
    val browsedRegionPlaces: List<PlaceFacet> = emptyList(),
    val years: List<YearFacet> = emptyList(),
    /** For [AutomaticAlbumSelection.browsedYear]. */
    val browsedYearMonths: List<MonthFacet> = emptyList(),
    /** Items the combined choice would make members; what the album holds once created. */
    val matchCount: Int = 0
) {
    companion object {
        fun build(
            join: MediaIndexLiveJoin,
            zone: ZoneId,
            names: GazetteerNames,
            locale: Locale,
            selection: AutomaticAlbumSelection
        ): AutomaticAlbumPickerContent {
            val facets = AutomaticAlbumFacets(join, zone, names::admin1Name)
            val timeClause = selection.time?.toClause()
            val region = selection.region
            val collator = Collator.getInstance(locale)

            val countries = facets.countries(timeClause)
                .map { CountryEntry(it.countryCode, names.countryDisplayName(it.countryCode, locale), it.count) }
                .sortedWith(compareBy(collator) { it.displayName })
            val browsedCountry = selection.browsedCountry
            val browsedAdmin1 = selection.browsedAdmin1
            val browsedYear = selection.browsedYear

            return AutomaticAlbumPickerContent(
                countries = countries,
                browsedCountry = browsedCountry?.let { facets.country(it, timeClause) },
                browsedRegionPlaces = if (browsedCountry != null && browsedAdmin1 != null) {
                    facets.places(browsedCountry, browsedAdmin1, timeClause)
                } else {
                    emptyList()
                },
                years = facets.years(region),
                browsedYearMonths = browsedYear?.let { facets.months(it, region) }.orEmpty(),
                matchCount = facets.matchCount(region, timeClause)
            )
        }
    }
}

enum class AutomaticAlbumPickerStatus {
    /** The names asset is being read; nothing can be listed yet. */
    LOADING_NAMES,

    /** The names asset could not be read; an empty list here would be a lie, so none is offered. */
    NAMES_FAILED,

    /** MediaStore has not been read yet. */
    WAITING_FOR_MEDIA,

    READY
}

data class AutomaticAlbumPickerUi(
    val status: AutomaticAlbumPickerStatus,
    val name: String,
    val selection: AutomaticAlbumSelection,
    val content: AutomaticAlbumPickerContent,
    /** Library-wide counts for the coverage notices; null until the live media is known. */
    val coverage: MediaIndexCoverage?,
    /** False on API 29+ without `ACCESS_MEDIA_LOCATION`: the location notice says so instead of counting. */
    val locationAccessGranted: Boolean,
    /** What to pass to `AlbumsViewModel.createAutomaticAlbum`; null while nothing is chosen. */
    val rule: AlbumRule.Evaluatable?,
    val canCreate: Boolean
)

/** What the picker's Index band shows; Settings is the one surface that measures progress. */
sealed interface AutomaticAlbumIndexBand {
    /** Nothing to say: no media waits to be indexed and no pass is running. */
    data object Hidden : AutomaticAlbumIndexBand

    /** A pass is running, so Index now must offer no action: it would replace the pass. */
    data object Running : AutomaticAlbumIndexBand

    /** [count] live media are not indexed yet; [nothingIndexed] is true when no media at all is indexed. */
    data class NotIndexed(val count: Int, val nothingIndexed: Boolean) : AutomaticAlbumIndexBand
}

/** WAITING is the normal idle state between periodic runs, so only RUNNING is "busy". */
fun automaticAlbumIndexBand(state: MediaIndexingUiState): AutomaticAlbumIndexBand = when {
    state.workStatus == MediaIndexingWorkStatus.RUNNING -> AutomaticAlbumIndexBand.Running
    state.notYetIndexedCount > 0 -> AutomaticAlbumIndexBand.NotIndexed(
        count = state.notYetIndexedCount,
        nothingIndexed = state.indexedItemsCount == 0
    )
    else -> AutomaticAlbumIndexBand.Hidden
}

/** While true an empty list may still fill in, so it must not claim that no places or dates were found. */
fun AutomaticAlbumIndexBand.emptyListMayStillFill(): Boolean = when (this) {
    AutomaticAlbumIndexBand.Running -> true
    is AutomaticAlbumIndexBand.NotIndexed -> nothingIndexed
    AutomaticAlbumIndexBand.Hidden -> false
}

/** Shared by the coverage notices and the Status area header, so the header never shows over nothing. */
fun locationCoverageNoticeVisible(coverage: MediaIndexCoverage?, locationAccessGranted: Boolean): Boolean =
    !locationAccessGranted || (coverage != null && coverage.withoutLocation > 0)

fun dateCoverageNoticeVisible(coverage: MediaIndexCoverage?): Boolean =
    coverage != null && coverage.withoutDate > 0

/** Whether the Place picker's Status area has anything to show, and so whether it has a header. */
fun placeStatusAreaHasContent(
    band: AutomaticAlbumIndexBand,
    coverage: MediaIndexCoverage?,
    locationAccessGranted: Boolean
): Boolean = band != AutomaticAlbumIndexBand.Hidden || locationCoverageNoticeVisible(coverage, locationAccessGranted)

/** Whether the Date picker's Status area has anything to show, and so whether it has a header. */
fun dateStatusAreaHasContent(band: AutomaticAlbumIndexBand, coverage: MediaIndexCoverage?): Boolean =
    band != AutomaticAlbumIndexBand.Hidden || dateCoverageNoticeVisible(coverage)

/**
 * The New automatic album screen's draft and what it lists. Owned by `AlbumsViewModel`, which hands
 * it the one join its album evaluation uses, so the picker and an album created from it agree by
 * construction. Facets are computed only while [uiState] is collected, so a library with no
 * automatic albums pays nothing until the picker opens.
 */
class AutomaticAlbumPickerState(
    private val scope: CoroutineScope,
    liveJoin: Flow<MediaIndexLiveJoin?>,
    existingAlbumNames: Flow<List<String>>,
    // The zone, the locale and the permission are read at each computation, never cached.
    private val zoneProvider: () -> ZoneId,
    private val localeProvider: () -> Locale,
    private val locationAccess: MediaLocationAccessChecker,
    private val namesLoader: () -> GazetteerNames,
    private val workDispatcher: CoroutineDispatcher
) {

    private sealed interface NamesState {
        data object NotLoaded : NamesState
        data object Loading : NamesState
        data class Ready(val names: GazetteerNames) : NamesState
        data object Failed : NamesState
    }

    private data class Snapshot(
        val status: AutomaticAlbumPickerStatus,
        val selection: AutomaticAlbumSelection,
        val content: AutomaticAlbumPickerContent,
        val coverage: MediaIndexCoverage?,
        val locationAccessGranted: Boolean
    )

    private val _name = MutableStateFlow("")
    private val _selection = MutableStateFlow(AutomaticAlbumSelection())
    private val _names = MutableStateFlow<NamesState>(NamesState.NotLoaded)

    // Bumped on open and refresh so the locale and the permission are read afresh after a recreate or a grant.
    private val _openCount = MutableStateFlow(0)

    /** False when no draft was opened, as in a process restored onto this screen. */
    val hasDraftName: Boolean get() = _name.value.isNotEmpty()

    private val snapshots: Flow<Snapshot> = combine(liveJoin, _names, _selection, _openCount) { join, names, selection, _ ->
        buildSnapshot(join, names, selection)
    }.flowOn(workDispatcher)

    val uiState: StateFlow<AutomaticAlbumPickerUi> = combine(snapshots, _name, existingAlbumNames) { snapshot, name, existing ->
        val rule = snapshot.selection.toRule()
        AutomaticAlbumPickerUi(
            status = snapshot.status,
            name = name,
            selection = snapshot.selection,
            content = snapshot.content,
            coverage = snapshot.coverage,
            locationAccessGranted = snapshot.locationAccessGranted,
            rule = rule,
            canCreate = rule != null && AlbumNaming.isAvailable(name.trim(), existing)
        )
    }.stateIn(
        scope,
        // Resetting on stop means a later open never shows the last draft's lists for a frame.
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
        AutomaticAlbumPickerUi(
            status = AutomaticAlbumPickerStatus.LOADING_NAMES,
            name = "",
            selection = AutomaticAlbumSelection(),
            content = AutomaticAlbumPickerContent(),
            coverage = null,
            locationAccessGranted = true,
            rule = null,
            canCreate = false
        )
    )

    /** Starts a fresh draft named [suggestedName] and reads the names asset once, the first time. */
    fun open(suggestedName: String) {
        _name.value = suggestedName
        _selection.value = AutomaticAlbumSelection()
        _openCount.update { it + 1 }
        loadNamesIfNeeded()
    }

    /** Recomputes the lists with the current locale and permission, keeping the draft. */
    fun refresh() {
        _openCount.update { it + 1 }
    }

    /** Drops the draft. The names stay loaded: they are the same for every draft. */
    fun close() {
        _name.value = ""
        _selection.value = AutomaticAlbumSelection()
    }

    /** After [AutomaticAlbumPickerStatus.NAMES_FAILED]; a no-op while loading or loaded. */
    fun retryLoadingNames() = loadNamesIfNeeded()

    fun setName(name: String) {
        _name.value = name
    }

    fun selectRegion(region: AlbumRuleClause.Region?) {
        _selection.update { it.copy(region = region) }
    }

    fun selectTime(time: TimeSelection?) {
        _selection.update { it.copy(time = time) }
    }

    /** Opens the lists of [countryCode], or closes them with null; either way the region list closes. */
    fun browseCountry(countryCode: String?) {
        _selection.update { it.copy(browsedCountry = countryCode, browsedAdmin1 = null) }
    }

    fun browseRegion(admin1Code: String?) {
        _selection.update { it.copy(browsedAdmin1 = admin1Code) }
    }

    fun browseYear(year: Int?) {
        _selection.update { it.copy(browsedYear = year) }
    }

    private fun loadNamesIfNeeded() {
        val current = _names.value
        if (current is NamesState.Loading || current is NamesState.Ready) return

        _names.value = NamesState.Loading
        scope.launch {
            _names.value = try {
                NamesState.Ready(withContext(workDispatcher) { namesLoader() })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                NamesState.Failed
            }
        }
    }

    private fun buildSnapshot(
        join: MediaIndexLiveJoin?,
        namesState: NamesState,
        selection: AutomaticAlbumSelection
    ): Snapshot {
        val locationAccessGranted = locationAccess.isGranted()
        fun waiting(status: AutomaticAlbumPickerStatus) =
            Snapshot(status, selection, AutomaticAlbumPickerContent(), null, locationAccessGranted)

        if (namesState is NamesState.Failed) return waiting(AutomaticAlbumPickerStatus.NAMES_FAILED)
        if (namesState !is NamesState.Ready) return waiting(AutomaticAlbumPickerStatus.LOADING_NAMES)
        if (join == null) return waiting(AutomaticAlbumPickerStatus.WAITING_FOR_MEDIA)

        return Snapshot(
            status = AutomaticAlbumPickerStatus.READY,
            selection = selection,
            content = AutomaticAlbumPickerContent.build(
                join = join,
                zone = zoneProvider(),
                names = namesState.names,
                locale = localeProvider(),
                selection = selection
            ),
            coverage = join.coverage(),
            locationAccessGranted = locationAccessGranted
        )
    }
}
