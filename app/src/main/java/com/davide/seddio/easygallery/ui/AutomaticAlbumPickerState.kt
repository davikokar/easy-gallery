package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.AlbumNaming
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AutomaticAlbumFacets
import com.davide.seddio.easygallery.data.CountryBreakdown
import com.davide.seddio.easygallery.data.FaceCapabilityState
import com.davide.seddio.easygallery.data.FaceEvaluationSnapshot
import com.davide.seddio.easygallery.data.FaceGroupSummary
import com.davide.seddio.easygallery.data.GazetteerNames
import com.davide.seddio.easygallery.data.MediaIndexLiveJoin
import com.davide.seddio.easygallery.data.MediaIndexingWorkStatus
import com.davide.seddio.easygallery.data.MonthFacet
import com.davide.seddio.easygallery.data.PlaceFacet
import com.davide.seddio.easygallery.data.YearFacet
import com.davide.seddio.easygallery.data.peopleMemberUris
import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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

/** The most people one album can name (plan 3.6, D3, 2026-11-21). */
const val MAX_PEOPLE_PER_ALBUM = 8

/** A person is offered only when their face appears in at least this many photos (albums.md decision 40). */
const val PEOPLE_PICKER_MINIMUM_IMAGES = 2

const val AUTOMATIC_ALBUM_TAB_PLACE = 0
const val AUTOMATIC_ALBUM_TAB_DATE = 1
const val AUTOMATIC_ALBUM_TAB_PEOPLE = 2

/** A restored People tab is Place while People is not offered (plan 3.6, D1). */
fun coerceAutomaticAlbumTab(tab: Int, peopleOffered: Boolean): Int =
    if (tab == AUTOMATIC_ALBUM_TAB_PEOPLE && !peopleOffered) AUTOMATIC_ALBUM_TAB_PLACE else tab

/**
 * Whether the picker offers the People tab (ADR-0024 Amendment 1 §5): face data on and the record for [currentKey] says
 * capable. An unknown capability is not offered here, though evaluation treats it as available, on purpose.
 */
fun isPeoplePickerOffered(faceDataEnabled: Boolean, capability: FaceCapabilityState?, currentKey: String): Boolean =
    faceDataEnabled && capability != null && capability.key == currentKey && capability.capable == true

/**
 * The draft: what the user has chosen, and which country, region and year the lists are open on.
 * The browsed values only say which lists to compute; they are not part of the rule. [region] is a
 * country, a region, or a place, exactly as the `region` clause can say it. [people] are the chosen
 * groups in the order chosen, each kept whole so its clause is the face the user saw (decision 41).
 */
data class AutomaticAlbumSelection(
    val region: AlbumRuleClause.Region? = null,
    val time: TimeSelection? = null,
    val people: List<FaceGroupSummary> = emptyList(),
    val browsedCountry: String? = null,
    val browsedAdmin1: String? = null,
    val browsedYear: Int? = null
) {
    /** One clause per person, anchored at the representative as it was listed. */
    fun peopleClauses(): List<AlbumRuleClause.People> =
        people.map { AlbumRuleClause.People.anchoredAt(it.representative) }

    /** Null until at least one clause is chosen: "no rule" is a manual album, never an empty rule. */
    fun toRule(): AlbumRule.Evaluatable? {
        val clauses = listOfNotNull(region, time?.toClause()) + peopleClauses()
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
            selection: AutomaticAlbumSelection,
            // Read only when a person is chosen; null is not loaded yet, which matches nothing.
            faces: FaceEvaluationSnapshot? = null
        ): AutomaticAlbumPickerContent {
            val peopleUris = if (selection.people.isEmpty()) {
                null
            } else {
                peopleMemberUris(join, zone, selection.peopleClauses(), faces)
            }
            val facets = AutomaticAlbumFacets(join, zone, names::admin1Name, peopleUris)
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

/** The People tab's own state; the screen-wide [AutomaticAlbumPickerStatus] does not gate it. */
enum class PeopleListStatus {
    /** Not revealed yet, or dropped. The tab starts a load with `revealPeople()`. */
    NOT_LOADED,
    LOADING,
    READY,

    /** The query returned nothing: pass not run, running, or nobody in two photos. */
    EMPTY,

    /** The one-shot query threw. */
    FAILED
}

/** What the People tab shows. [offered] false is the screen with no People tab, and everything else empty. */
data class PeoplePickerUi(
    val offered: Boolean = false,
    val status: PeopleListStatus = PeopleListStatus.NOT_LOADED,
    /** In the query's order, never re-sorted or filtered. */
    val groups: List<FaceGroupSummary> = emptyList(),
    /** In the order chosen; each is also in [groups] while the list is frozen. */
    val chosen: List<FaceGroupSummary> = emptyList()
) {
    /** The People chip's count. */
    val chosenCount: Int get() = chosen.size
    val atCap: Boolean get() = chosen.size >= MAX_PEOPLE_PER_ALBUM

    fun isChosen(person: FaceGroupSummary): Boolean = chosen.any { it.groupId == person.groupId }

    /** An unchosen tile at the cap; a chosen one stays enabled so it can always be removed. */
    fun isDisabled(person: FaceGroupSummary): Boolean = atCap && !isChosen(person)
}

data class AutomaticAlbumPickerUi(
    val status: AutomaticAlbumPickerStatus,
    val name: String,
    val selection: AutomaticAlbumSelection,
    val content: AutomaticAlbumPickerContent,
    /** What to pass to `AlbumsViewModel.createAutomaticAlbum`; null while nothing is chosen. */
    val rule: AlbumRule.Evaluatable?,
    val canCreate: Boolean,
    val people: PeoplePickerUi = PeoplePickerUi()
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

/**
 * The New automatic album screen's draft and what it lists. Owned by `AlbumsViewModel`, which hands
 * it the one join its album evaluation uses, so the picker and an album created from it agree by
 * construction. Facets are computed only while [uiState] is collected, so a library with no
 * automatic albums pays nothing until the picker opens.
 *
 * The People tab's state lives here too (plan 3.6): the groups load once per visit at the first reveal and again when
 * indexing leaves RUNNING, never while a person is chosen, and the face projection is read only while one is. Its
 * methods, like the draft's, are called from one thread, the main one.
 */
class AutomaticAlbumPickerState(
    private val scope: CoroutineScope,
    liveJoin: Flow<MediaIndexLiveJoin?>,
    existingAlbumNames: Flow<List<String>>,
    // The zone and the locale are read at each computation, never cached.
    private val zoneProvider: () -> ZoneId,
    private val localeProvider: () -> Locale,
    private val namesLoader: () -> GazetteerNames,
    private val workDispatcher: CoroutineDispatcher,
    // See isPeoplePickerOffered; each emission is a fresh read of the stores.
    peopleOffered: Flow<Boolean>,
    // Subscribed to only while a person is chosen, so nothing else pays for face writes.
    private val faceSnapshots: Flow<FaceEvaluationSnapshot>,
    // One query, the People picker's (`MediaIndexStore.listFaceGroups`), in the order it returns.
    private val peopleLoader: suspend () -> List<FaceGroupSummary>
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
        val content: AutomaticAlbumPickerContent
    )

    private data class PeopleList(
        val status: PeopleListStatus = PeopleListStatus.NOT_LOADED,
        val groups: List<FaceGroupSummary> = emptyList()
    )

    // What the draft is and whether People is offered, as one value, so a tile and the chip never see different drafts.
    private data class Draft(val selection: AutomaticAlbumSelection, val offered: Boolean)

    private val _name = MutableStateFlow("")
    private val _selection = MutableStateFlow(AutomaticAlbumSelection())
    private val _names = MutableStateFlow<NamesState>(NamesState.NotLoaded)
    private val _people = MutableStateFlow(PeopleList())
    private var peopleLoadJob: Job? = null
    private var indexingRunning: Boolean? = null

    // A reload was skipped or dropped because a person was chosen; the list is read again once nobody is.
    private var reloadSkipped = false

    // Eager and cheap: it must be able to drop the draft's people the moment the tab stops being offered.
    private val offered: StateFlow<Boolean> = peopleOffered.stateIn(scope, SharingStarted.Eagerly, false)

    // Bumped on open and refresh so the locale is read afresh after a recreate.
    private val _openCount = MutableStateFlow(0)

    /** False when no draft was opened, as in a process restored onto this screen. */
    val hasDraftName: Boolean get() = _name.value.isNotEmpty()

    // The draft as everything downstream reads it: while People is not offered nobody is chosen, even for the one
    // emission before the drop below has cleared the draft itself.
    private val draft: Flow<Draft> = combine(_selection, offered) { selection, isOffered ->
        val effective = if (isOffered || selection.people.isEmpty()) selection else selection.copy(people = emptyList())
        Draft(effective, isOffered)
    }

    private val effectiveSelection: Flow<AutomaticAlbumSelection> = draft.map { it.selection }.distinctUntilChanged()

    // Nobody chosen is no subscription at all, which is what keeps 3.5's "only a people album subscribes" true.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val chosenPeopleFaces: Flow<FaceEvaluationSnapshot?> = effectiveSelection
        .map { it.people.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { anyChosen -> if (anyChosen) faceSnapshots else flowOf(null) }

    // A chosen person whose face projection has not arrived yet yields no snapshot, so the previous counts stay until
    // the real ones: building one would match nothing and publish a count of 0 and empty lists.
    private val snapshots: Flow<Snapshot> =
        combine(liveJoin, _names, effectiveSelection, _openCount, chosenPeopleFaces) { join, names, selection, _, faces ->
            if (selection.people.isNotEmpty() && faces == null) null else buildSnapshot(join, names, selection, faces)
        }.filterNotNull().flowOn(workDispatcher)

    val uiState: StateFlow<AutomaticAlbumPickerUi> =
        combine(snapshots, _name, existingAlbumNames, _people, draft) { snapshot, name, existing, people, current ->
            // The chosen people are read from the draft, never the snapshot: that one is computed off-thread, and a
            // tile, the cap and the chip must agree with what togglePerson will accept.
            val selection = snapshot.selection.copy(people = current.selection.people)
            val rule = selection.toRule()
            AutomaticAlbumPickerUi(
                status = snapshot.status,
                name = name,
                selection = selection,
                content = snapshot.content,
                rule = rule,
                canCreate = rule != null && AlbumNaming.isAvailable(name.trim(), existing),
                people = if (current.offered) {
                    PeoplePickerUi(true, people.status, people.groups, current.selection.people)
                } else {
                    PeoplePickerUi()
                }
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
                rule = null,
                canCreate = false
            )
        )

    // Last, so that everything it reads is initialised when an immediate dispatcher runs it inside the constructor.
    init {
        scope.launch {
            offered.collect { isOffered -> if (!isOffered) dropPeople() }
        }
    }

    /** Starts a fresh draft named [suggestedName] and reads the names asset once, the first time. */
    fun open(suggestedName: String) {
        _name.value = suggestedName
        _selection.value = AutomaticAlbumSelection()
        resetPeopleList()
        _openCount.update { it + 1 }
        loadNamesIfNeeded()
    }

    /** Recomputes the lists with the current locale, keeping the draft. */
    fun refresh() {
        _openCount.update { it + 1 }
    }

    /** Drops the draft. The names stay loaded: they are the same for every draft. */
    fun close() {
        _name.value = ""
        _selection.value = AutomaticAlbumSelection()
        resetPeopleList()
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

    /** The People tab was shown: the first time in a visit this loads the groups, and a later reveal changes nothing. */
    fun revealPeople() {
        if (!offered.value || _people.value.status != PeopleListStatus.NOT_LOADED) return
        loadPeople()
    }

    /** After [PeopleListStatus.FAILED]; a no-op in any other state. */
    fun retryLoadingPeople() {
        if (!offered.value || _people.value.status != PeopleListStatus.FAILED) return
        loadPeople()
    }

    /**
     * The screen reports whether an indexing pass is running. Leaving RUNNING reloads a list that was revealed, unless a
     * person is chosen: a regroup can change group ids, and the anchors come from the list the user saw (decision 41).
     * A reload skipped for that reason is made when the last chosen person is removed.
     */
    fun onIndexingRunningChanged(running: Boolean) {
        val wasRunning = indexingRunning == true
        indexingRunning = running
        if (!wasRunning || running) return
        if (!offered.value || _people.value.status == PeopleListStatus.NOT_LOADED) return
        if (_selection.value.people.isNotEmpty()) {
            reloadSkipped = true
            return
        }
        loadPeople()
    }

    /**
     * Chooses [person], or removes them when already chosen, and says whether the draft changed. False, with nothing
     * changed, at [MAX_PEOPLE_PER_ALBUM] chosen, for a summary that is not exactly the listed one (a reload can give a
     * group id another face, and the anchor is the tile the user tapped), and while People is not offered.
     */
    fun togglePerson(person: FaceGroupSummary): Boolean {
        if (!offered.value) return false
        while (true) {
            val current = _selection.value
            val chosen = current.people
            val next = if (chosen.any { it.groupId == person.groupId }) {
                chosen.filterNot { it.groupId == person.groupId }
            } else {
                if (chosen.size >= MAX_PEOPLE_PER_ALBUM) return false
                val listed = _people.value.groups.firstOrNull { it.groupId == person.groupId }?.takeIf { it == person }
                    ?: return false
                chosen + listed
            }
            if (_selection.compareAndSet(current, current.copy(people = next))) {
                if (next.isEmpty()) reloadSkippedPeople()
                return true
            }
        }
    }

    /** The People chip: removes every chosen person and keeps the list. */
    fun clearPeople() {
        _selection.update { it.copy(people = emptyList()) }
        reloadSkippedPeople()
    }

    private fun reloadSkippedPeople() {
        if (!reloadSkipped) return
        reloadSkipped = false
        if (!offered.value || _people.value.status == PeopleListStatus.NOT_LOADED) return
        loadPeople()
    }

    // The tab stopped being offered: the draft's people and the loaded groups go with it.
    private fun dropPeople() {
        resetPeopleList()
        _selection.update { it.copy(people = emptyList()) }
    }

    private fun resetPeopleList() {
        peopleLoadJob?.cancel()
        peopleLoadJob = null
        reloadSkipped = false
        _people.value = PeopleList()
    }

    // A reload keeps showing the list it replaces; only a first load or a retry shows Loading.
    private fun loadPeople() {
        reloadSkipped = false
        val status = _people.value.status
        if (status == PeopleListStatus.NOT_LOADED || status == PeopleListStatus.FAILED) {
            _people.value = PeopleList(PeopleListStatus.LOADING)
        }
        peopleLoadJob?.cancel()
        peopleLoadJob = scope.launch {
            val groups = try {
                withContext(workDispatcher) { peopleLoader() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                null
            }
            // The list a person was chosen from is frozen, whatever this load found.
            if (_selection.value.people.isNotEmpty()) {
                reloadSkipped = true
                return@launch
            }
            _people.value = when {
                groups == null -> PeopleList(PeopleListStatus.FAILED)
                groups.isEmpty() -> PeopleList(PeopleListStatus.EMPTY)
                else -> PeopleList(PeopleListStatus.READY, groups)
            }
        }
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
        selection: AutomaticAlbumSelection,
        faces: FaceEvaluationSnapshot?
    ): Snapshot {
        fun waiting(status: AutomaticAlbumPickerStatus) =
            Snapshot(status, selection, AutomaticAlbumPickerContent())

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
                selection = selection,
                faces = faces
            )
        )
    }
}
