package com.davide.seddio.easygallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.LocaleHelper
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.ALBUM_DISPLAY_PREFERENCES_FILE
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.AlbumMembership
import com.davide.seddio.easygallery.data.AlbumNaming
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AlbumRuleEvaluator
import com.davide.seddio.easygallery.data.AlbumRuleJson
import com.davide.seddio.easygallery.data.AlbumStore
import com.davide.seddio.easygallery.data.AndroidMediaLocationAccessChecker
import com.davide.seddio.easygallery.data.AndroidMediaStoreVersionProvider
import com.davide.seddio.easygallery.data.FolderViewOverrides
import com.davide.seddio.easygallery.data.FolderViewPreferencesStore
import com.davide.seddio.easygallery.data.GazetteerAssets
import com.davide.seddio.easygallery.data.GazetteerNames
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.InMemoryMediaIndexStore
import com.davide.seddio.easygallery.data.MediaIndexCoverage
import com.davide.seddio.easygallery.data.MediaIndexEvaluationProjection
import com.davide.seddio.easygallery.data.MediaIndexLiveJoin
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaLocationAccessChecker
import com.davide.seddio.easygallery.data.MediaStoreVersionProvider
import com.davide.seddio.easygallery.data.MediaStoreVersionStore
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.MembershipOrphanStateChange
import com.davide.seddio.easygallery.data.MembershipUriRewrite
import com.davide.seddio.easygallery.data.OverridablePreference
import com.davide.seddio.easygallery.data.PreferenceApplyTarget
import com.davide.seddio.easygallery.data.RoomAlbumStore
import com.davide.seddio.easygallery.data.SharedPreferencesFolderViewPreferencesStore
import com.davide.seddio.easygallery.data.SharedPreferencesMediaStoreVersionStore
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.StoredAlbum
import com.davide.seddio.easygallery.data.StoredMembership
import com.davide.seddio.easygallery.data.StoredMembershipKind
import com.davide.seddio.easygallery.data.ViewPreferences
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.data.albumOverrideKey
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AlbumListItem(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val cover: MediaItem?,
    val isCoverFallback: Boolean,
    val resolvedMemberCount: Int,
    val storedMemberCount: Int,
    /** Stored additions that did not resolve to a live item; rule matches never count. */
    val hiddenMemberCount: Int,
    val storedMembershipCount: Int,
    val status: AlbumEvaluationStatus,
    /** The rule has a `time` clause; false for a manual or unevaluatable album. */
    val hasTimeClause: Boolean = false,
    /** The rule has a `region` clause; false for a manual or unevaluatable album. */
    val hasRegionClause: Boolean = false,
    /**
     * Library-wide counts from the join (albums.md §8 decision 25). Null for a manual or
     * unevaluatable album, and until the live media is known; the notice for a clause kind the
     * album does not have is the caller's to leave out.
     */
    val coverage: MediaIndexCoverage? = null,
    /** False on API 29+ without `ACCESS_MEDIA_LOCATION`; only read for an album with a region clause. */
    val locationAccessGranted: Boolean = true
)

/** [membershipId] is null for a rule match that has no addition row. */
data class AlbumDetailMember(
    val membershipId: Long?,
    val mediaItem: MediaItem
)

class AlbumsViewModel @JvmOverloads constructor(
    application: Application,
    private val albumStore: AlbumStore = RoomAlbumStore((application as EasyGalleryApp).database),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val albumNameFormatter: (Locale, String, Int) -> String = { locale, format, ordinal ->
        String.format(locale, format, ordinal)
    },
    private val mediaStoreVersionStore: MediaStoreVersionStore =
        SharedPreferencesMediaStoreVersionStore(application),
    private val mediaStoreVersionProvider: MediaStoreVersionProvider =
        AndroidMediaStoreVersionProvider,
    private val mediaIndexStore: MediaIndexStore =
        (application as? EasyGalleryApp)?.mediaIndexStore ?: InMemoryMediaIndexStore(),
    // Read at each evaluation, not cached: the user can change the device zone while the app lives.
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val evaluationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // The seam that lets a test count how often the join is built.
    private val joinFactory: (List<MediaIndexEvaluationProjection>, List<MediaItem>) -> MediaIndexLiveJoin =
        { projection, liveMedia -> MediaIndexLiveJoin.build(projection, liveMedia) },
    private val locationAccessChecker: MediaLocationAccessChecker = AndroidMediaLocationAccessChecker(application),
    private val gazetteerNamesLoader: () -> GazetteerNames = { GazetteerAssets.loadNames(application) },
    // Read when the picker computes, never cached: a language change recreates the Activity.
    private val localeProvider: () -> Locale = {
        LocaleHelper.wrap(application).resources.configuration.locales[0]
    },
    // Its own file: clear(group) is file-wide, so albums must not share the folder overrides' file (ADR-0021).
    albumOverrideStore: FolderViewPreferencesStore =
        SharedPreferencesFolderViewPreferencesStore(application, ALBUM_DISPLAY_PREFERENCES_FILE)
) : AndroidViewModel(application) {

    private data class ComputedAlbum(
        val album: StoredAlbum,
        val memberships: List<StoredMembership>,
        val members: List<AlbumDetailMember>,
        val cover: MediaItem?,
        val isCoverFallback: Boolean,
        val resolvedMemberCount: Int,
        val storedMemberCount: Int,
        val hiddenMemberCount: Int,
        val storedMembershipCount: Int,
        val status: AlbumEvaluationStatus,
        val hasTimeClause: Boolean,
        val hasRegionClause: Boolean,
        val coverage: MediaIndexCoverage?,
        val locationAccessGranted: Boolean,
        val additionIdsByUri: Map<String, List<Long>>,
        val uriRewrites: List<MembershipUriRewrite>,
        val orphanStateChanges: List<MembershipOrphanStateChange>
    ) {
        // An unevaluatable rule counts: a newer build may read it and re-match what the user removed.
        val hasStoredRule: Boolean
            get() = album.rule != null

        fun toListItem(): AlbumListItem {
            return AlbumListItem(
                id = album.id,
                name = album.name,
                createdAt = album.createdAt,
                cover = cover,
                isCoverFallback = isCoverFallback,
                resolvedMemberCount = resolvedMemberCount,
                storedMemberCount = storedMemberCount,
                hiddenMemberCount = hiddenMemberCount,
                storedMembershipCount = storedMembershipCount,
                status = status,
                hasTimeClause = hasTimeClause,
                hasRegionClause = hasRegionClause,
                coverage = coverage,
                locationAccessGranted = locationAccessGranted
            )
        }
    }

    private data class PendingHeal(
        val rewrites: List<MembershipUriRewrite>,
        val orphanChanges: List<MembershipOrphanStateChange>
    )

    private val healMutex = Mutex()
    private val lastHealsByAlbumId = mutableMapOf<Long, PendingHeal>()

    // Null until MediaStore has been read once; a rule album is then WAITING_ON_INDEX, not empty.
    private val _allMedia = MutableStateFlow<List<MediaItem>?>(null)
    private val _mediaVersionEpoch = MutableStateFlow(0L)
    private val _selectedAlbumId = MutableStateFlow<Long?>(null)
    private val _albumSelectionId = MutableStateFlow<Long?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val mediaStoreVersionCheckMutex = Mutex()

    val selectedAlbumId: StateFlow<Long?> = _selectedAlbumId.asStateFlow()

    /** The Album selected by long-press in Albums View (Album selection mode); not the open Album Detail. */
    val albumSelectionId: StateFlow<Long?> = _albumSelectionId.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val albumDisplayPrefs = FolderViewPreferencesState(albumOverrideStore)

    /** Sparse per-album display overrides, keyed by [albumOverrideKey] or `FAVOURITES_OVERRIDE_KEY`. */
    val albumDisplayOverrides: StateFlow<Map<String, FolderViewOverrides>> = albumDisplayPrefs.overrides

    // The one index-and-live-media join, built once per emission of either input and shared by every rule
    // album and the picker (ADR-0020). Only collected while a rule album or the open picker needs it, so a
    // manual-only library pays no Room invalidation. The stop timeout keeps one index query alive across the
    // album list re-emitting (a rename); the reset drops the join instead of replaying a stale one later.
    private val liveJoin: Flow<MediaIndexLiveJoin?> = combine(
        mediaIndexStore.observeEvaluationProjection(),
        _allMedia
    ) { projection, media ->
        media?.let { joinFactory(projection, it) }
    }.flowOn(evaluationDispatcher)
        .shareIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
            replay = 1
        )

    // Re-runs on albums, memberships, live media (app open) and every index store emission (after a batch).
    private val computedAlbums: StateFlow<List<ComputedAlbum>> =
        combine(albumStore.observeAlbums(), _mediaVersionEpoch) { albums, _ -> albums }
            .flatMapLatest { albums ->
                if (albums.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(albums.map { album -> observeComputedAlbum(album) }) { computed ->
                        computed.toList()
                    }
                }
            }.flowOn(evaluationDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<AlbumListItem>> = computedAlbums
        .map { computed ->
            computed.map { it.toListItem() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedAlbum: StateFlow<AlbumListItem?> =
        combine(_selectedAlbumId, computedAlbums) { selectedAlbumId, computed ->
            computed.firstOrNull { it.album.id == selectedAlbumId }?.toListItem()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedAlbumMembers: StateFlow<List<AlbumDetailMember>> =
        combine(_selectedAlbumId, computedAlbums) { selectedAlbumId, computed ->
            computed.firstOrNull { it.album.id == selectedAlbumId }?.members.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Counts the same join the albums above evaluate over, so a count here is a member count there.
    val automaticAlbumPicker: AutomaticAlbumPickerState = AutomaticAlbumPickerState(
        scope = viewModelScope,
        liveJoin = liveJoin,
        existingAlbumNames = albums.map { items -> items.map { it.name } },
        zoneProvider = zoneProvider,
        localeProvider = localeProvider,
        locationAccess = locationAccessChecker,
        namesLoader = gazetteerNamesLoader,
        workDispatcher = evaluationDispatcher
    )

    init {
        viewModelScope.launch {
            computedAlbums.collect { computed ->
                if (_selectedAlbumId.value != null && computed.none { it.album.id == _selectedAlbumId.value }) {
                    _selectedAlbumId.value = null
                }
                if (_albumSelectionId.value != null && computed.none { it.album.id == _albumSelectionId.value }) {
                    _albumSelectionId.value = null
                }

                computed.forEach { item ->
                    applyHealingIfNeeded(item)
                }
            }
        }
    }

    fun setAllMedia(media: List<MediaItem>) {
        if (_allMedia.value === media) return

        _allMedia.value = media
        viewModelScope.launch(Dispatchers.IO) {
            mediaStoreVersionCheckMutex.withLock {
                if (didMediaStoreVersionChange()) {
                    _mediaVersionEpoch.value = _mediaVersionEpoch.value + 1
                }
            }
        }
    }

    fun selectAlbum(albumId: Long?) {
        // Opening an album leaves Album selection mode, so Back lands on Albums View as it was (ADR-0013 Am. 2 point 7).
        if (albumId != null) _albumSelectionId.value = null
        _selectedAlbumId.value = albumId
    }

    fun enterAlbumSelection(albumId: Long) {
        _albumSelectionId.value = albumId
    }

    fun exitAlbumSelection() {
        _albumSelectionId.value = null
    }

    /** A main-view tab tap: leaves Album Detail View and Album selection mode. Favourites is not held here. */
    fun cancelAlbumSelectionAndDetail() {
        _albumSelectionId.value = null
        _selectedAlbumId.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /** The first unused "My album N", for the New album screen to open on. */
    fun suggestAlbumName(): String {
        val context = LocaleHelper.wrap(getApplication())
        val format = context.getString(R.string.album_default_name)
        val locale = context.resources.configuration.locales[0]
        return AlbumNaming.suggestName(albums.value.map { it.name }) { ordinal ->
            albumNameFormatter(locale, format, ordinal)
        }
    }

    /** Returns false, and sets [errorMessage], when the name is blank or already taken. */
    fun createAlbum(name: String, members: Collection<MediaItem> = emptyList()): Boolean {
        val sanitizedName = sanitizeName(name)
        if (!validateAlbumName(sanitizedName, ignoreAlbumId = null)) return false

        viewModelScope.launch {
            val albumId = albumStore.createAlbum(
                name = sanitizedName,
                createdAt = nowProvider(),
                rule = null
            )
            members.distinctBy { it.uri }.forEach { item ->
                albumStore.addMember(
                    albumId = albumId,
                    mediaUri = item.uri.toString(),
                    displayName = item.name,
                    size = item.size,
                    dateModified = item.dateModified
                )
            }
            _errorMessage.value = null
        }
        return true
    }

    /**
     * Writes the album row with [rule] encoded and no membership rows: the members are whatever the rule
     * matches (ADR-0016 Amendment 2). Returns false, and sets [errorMessage], when the name is blank or taken.
     */
    fun createAutomaticAlbum(name: String, rule: AlbumRule.Evaluatable): Boolean {
        val sanitizedName = sanitizeName(name)
        if (!validateAlbumName(sanitizedName, ignoreAlbumId = null)) return false

        val encodedRule = AlbumRuleJson.encode(rule)
        viewModelScope.launch {
            albumStore.createAlbum(
                name = sanitizedName,
                createdAt = nowProvider(),
                rule = encodedRule
            )
            _errorMessage.value = null
        }
        return true
    }

    /** Starts a fresh picker draft named with the first unused "My album N". */
    fun openAutomaticAlbumPicker() {
        automaticAlbumPicker.open(suggestAlbumName())
    }

    fun renameAlbum(albumId: Long, newName: String) {
        val sanitizedName = sanitizeName(newName)
        if (!validateAlbumName(sanitizedName, ignoreAlbumId = albumId)) return

        viewModelScope.launch {
            albumStore.renameAlbum(albumId, sanitizedName)
            _errorMessage.value = null
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            albumStore.deleteAlbum(albumId)
            albumDisplayPrefs.remove(albumOverrideKey(albumId))
            if (_selectedAlbumId.value == albumId) {
                _selectedAlbumId.value = null
            }
            if (_albumSelectionId.value == albumId) {
                _albumSelectionId.value = null
            }
        }
    }

    // CURRENT_FOLDER means "this album" and ALL_FOLDERS "every album" here (ADR-0021 point 9).
    // For ALL_FOLDERS, writeGlobal runs first, then the group is cleared from every key incl. Favourites.
    fun commitAlbumSortPreference(
        albumKey: String?,
        sortType: SortType,
        sortOrder: SortOrder,
        applyTarget: PreferenceApplyTarget,
        writeGlobal: () -> Unit
    ) {
        when (applyTarget) {
            PreferenceApplyTarget.CURRENT_FOLDER -> albumDisplayPrefs.setSort(albumKey, sortType, sortOrder)
            PreferenceApplyTarget.ALL_FOLDERS -> {
                writeGlobal()
                albumDisplayPrefs.clear(OverridablePreference.SORT)
            }
        }
    }

    fun commitAlbumColumnsPreference(
        albumKey: String?,
        columns: Int,
        applyTarget: PreferenceApplyTarget,
        writeGlobal: () -> Unit
    ) {
        when (applyTarget) {
            PreferenceApplyTarget.CURRENT_FOLDER -> setAlbumColumns(albumKey, columns)
            PreferenceApplyTarget.ALL_FOLDERS -> {
                writeGlobal()
                albumDisplayPrefs.clear(OverridablePreference.COLUMNS)
            }
        }
    }

    fun commitAlbumGroupByPreference(
        albumKey: String?,
        groupBy: GroupByType,
        groupOrder: SortOrder,
        applyTarget: PreferenceApplyTarget,
        writeGlobal: () -> Unit
    ) {
        when (applyTarget) {
            PreferenceApplyTarget.CURRENT_FOLDER -> albumDisplayPrefs.setGroupBy(albumKey, groupBy, groupOrder)
            PreferenceApplyTarget.ALL_FOLDERS -> {
                writeGlobal()
                albumDisplayPrefs.clear(OverridablePreference.GROUP_BY)
            }
        }
    }

    fun commitAlbumMediaTypesPreference(
        albumKey: String?,
        mediaTypes: Set<MediaType>,
        applyTarget: PreferenceApplyTarget,
        writeGlobal: () -> Unit
    ) {
        when (applyTarget) {
            PreferenceApplyTarget.CURRENT_FOLDER -> albumDisplayPrefs.setMediaTypes(albumKey, mediaTypes)
            PreferenceApplyTarget.ALL_FOLDERS -> {
                writeGlobal()
                albumDisplayPrefs.clear(OverridablePreference.MEDIA_TYPES)
            }
        }
    }

    fun commitAlbumViewTypePreference(
        albumKey: String?,
        viewType: ViewType,
        applyTarget: PreferenceApplyTarget,
        writeGlobal: () -> Unit
    ) {
        when (applyTarget) {
            PreferenceApplyTarget.CURRENT_FOLDER -> albumDisplayPrefs.setViewType(albumKey, viewType)
            PreferenceApplyTarget.ALL_FOLDERS -> {
                writeGlobal()
                albumDisplayPrefs.clear(OverridablePreference.VIEW_TYPE)
            }
        }
    }

    /** Pinch-to-zoom: always a per-album override, clamped to the valid column range. */
    fun setAlbumColumns(albumKey: String?, columns: Int) {
        albumDisplayPrefs.setColumns(
            albumKey,
            columns.coerceIn(ViewPreferences.MIN_COLUMNS, ViewPreferences.MAX_COLUMNS)
        )
    }

    fun addMembers(albumId: Long, mediaItems: Collection<MediaItem>) {
        if (mediaItems.isEmpty()) return

        viewModelScope.launch {
            val computed = computedAlbums.value.firstOrNull { it.album.id == albumId } ?: return@launch
            val additionsByUri = computed.memberships
                .filter { it.kind == StoredMembershipKind.ADDITION }
                .associateBy { it.mediaUri }
            val exclusionsByUri = computed.memberships
                .filter { it.kind == StoredMembershipKind.EXCLUSION }
                .groupBy { it.mediaUri }

            for (item in mediaItems) {
                val uriString = item.uri.toString()

                exclusionsByUri[uriString].orEmpty().forEach { exclusion ->
                    albumStore.removeMembership(albumId, exclusion.id)
                }

                if (additionsByUri.containsKey(uriString)) continue

                albumStore.addMember(
                    albumId = albumId,
                    mediaUri = uriString,
                    displayName = item.name,
                    size = item.size,
                    dateModified = item.dateModified
                )
            }
        }
    }

    fun removeMember(albumId: Long, membershipId: Long) {
        removeMembers(albumId, listOf(membershipId))
    }

    fun removeMembers(albumId: Long, membershipIds: Collection<Long>) {
        if (membershipIds.isEmpty()) return

        viewModelScope.launch {
            val computed = computedAlbums.value
                .firstOrNull { it.album.id == albumId }
                ?.takeIf { it.hasStoredRule }

            membershipIds.forEach { membershipId ->
                val member = computed?.members?.firstOrNull { it.membershipId == membershipId }
                if (computed != null && member != null) {
                    removeFromAlbum(computed, listOf(member))
                } else {
                    albumStore.removeMembership(albumId, membershipId)
                }
            }
        }
    }

    fun removeMembersByMedia(albumId: Long, mediaItems: Collection<MediaItem>) {
        if (mediaItems.isEmpty()) return

        viewModelScope.launch {
            val targetUris = mediaItems.map { it.uri.toString() }.toSet()
            val computed = computedAlbums.value.firstOrNull { it.album.id == albumId } ?: return@launch
            val targets = computed.members
                .filter { member -> member.mediaItem.uri.toString() in targetUris }

            removeFromAlbum(computed, targets)
        }
    }

    /**
     * A rule match has no row to delete, so removing one records an exclusion (ADR-0012), and any
     * addition row for the item goes too or the addition would keep it in. Without a stored rule
     * nothing can re-match the item, so removal is just deleting its row.
     */
    private suspend fun removeFromAlbum(computed: ComputedAlbum, targets: List<AlbumDetailMember>) {
        val albumId = computed.album.id

        targets.forEach { member ->
            if (!computed.hasStoredRule) {
                member.membershipId?.let { albumStore.removeMembership(albumId, it) }
                return@forEach
            }

            val item = member.mediaItem
            val uriString = item.uri.toString()
            albumStore.recordExclusion(
                albumId = albumId,
                mediaUri = uriString,
                displayName = item.name,
                size = item.size,
                dateModified = item.dateModified
            )
            computed.additionIdsByUri[uriString].orEmpty().forEach { additionId ->
                albumStore.removeMembership(albumId, additionId)
            }
        }
    }

    private suspend fun applyHealingIfNeeded(computed: ComputedAlbum) {
        val nextHeal = PendingHeal(
            rewrites = computed.uriRewrites,
            orphanChanges = computed.orphanStateChanges
        )

        if (nextHeal.rewrites.isEmpty() && nextHeal.orphanChanges.isEmpty()) {
            lastHealsByAlbumId.remove(computed.album.id)
            return
        }

        val previous = lastHealsByAlbumId[computed.album.id]
        if (previous == nextHeal) return

        healMutex.withLock {
            val currentPrevious = lastHealsByAlbumId[computed.album.id]
            if (currentPrevious == nextHeal) return

            lastHealsByAlbumId[computed.album.id] = nextHeal
            albumStore.writeBackHealedUris(nextHeal.rewrites)
            albumStore.updateOrphanStates(nextHeal.orphanChanges)
        }
    }

    // A manual album never reads the index, so an index emission does not recompute it.
    private fun observeComputedAlbum(album: StoredAlbum): Flow<ComputedAlbum> {
        val memberships = albumStore.observeMembershipsForAlbum(album.id)
        // The stored rule is only read here and never written back, whatever it parses to.
        val rule = AlbumRuleJson.parse(album.rule)
        if (rule == null) {
            return combine(memberships, _allMedia) { currentMemberships, media ->
                computeAlbum(album, currentMemberships, rule = null, join = null, liveMedia = media)
            }
        }

        return combine(memberships, liveJoin) { currentMemberships, join ->
            computeAlbum(album, currentMemberships, rule, join, liveMedia = join?.liveMedia)
        }
    }

    // [liveMedia] is the join's own list for a rule album, so the one resolve below matches the evaluation.
    private fun computeAlbum(
        album: StoredAlbum,
        memberships: List<StoredMembership>,
        rule: AlbumRule?,
        join: MediaIndexLiveJoin?,
        liveMedia: List<MediaItem>?
    ): ComputedAlbum {
        val resolution = AlbumMembership.resolve(memberships, liveMedia.orEmpty())

        val additionIdsByUri = LinkedHashMap<String, MutableList<Long>>()
        resolution.resolvedAdditions.forEach { resolved ->
            additionIdsByUri.getOrPut(resolved.resolvedMediaUri) { mutableListOf() } += resolved.membershipId
        }

        val evaluation = rule?.let { AlbumRuleEvaluator.evaluate(it, join, resolution, zoneProvider()) }
        val items = evaluation?.members ?: resolution.albumItems
        val status = evaluation?.status ?: AlbumEvaluationStatus.MANUAL

        val clauses = (rule as? AlbumRule.Evaluatable)?.clauses.orEmpty()
        val hasRegionClause = clauses.any { it is AlbumRuleClause.Region }

        val members = items.map { item ->
            AlbumDetailMember(
                membershipId = additionIdsByUri[item.uri.toString()]?.firstOrNull(),
                mediaItem = item
            )
        }

        val coverMembershipId = album.coverMembershipId
        val directCover = coverMembershipId?.let { id ->
            members.firstOrNull { it.membershipId == id }?.mediaItem
        }
        val fallbackCover = members.firstOrNull()?.mediaItem
        val cover = directCover ?: fallbackCover
        val isCoverFallback = directCover == null && fallbackCover != null

        val storedMemberCount = memberships.count { it.kind == StoredMembershipKind.ADDITION }
        return ComputedAlbum(
            album = album,
            memberships = memberships,
            members = members,
            cover = cover,
            isCoverFallback = isCoverFallback,
            resolvedMemberCount = members.size,
            storedMemberCount = storedMemberCount,
            hiddenMemberCount = storedMemberCount - resolution.resolvedAdditions.size,
            storedMembershipCount = memberships.size,
            status = status,
            hasTimeClause = clauses.any { it is AlbumRuleClause.Time },
            hasRegionClause = hasRegionClause,
            // An unevaluatable rule reaches nothing, so a statement about what it can reach would be false.
            coverage = if (rule is AlbumRule.Evaluatable) join?.coverage() else null,
            locationAccessGranted = !hasRegionClause || locationAccessChecker.isGranted(),
            additionIdsByUri = additionIdsByUri,
            // Before MediaStore is read every row looks unresolved; writing that back would be false.
            uriRewrites = if (liveMedia == null) emptyList() else resolution.uriRewrites,
            orphanStateChanges = if (liveMedia == null) emptyList() else resolution.orphanStateChanges
        )
    }

    private fun validateAlbumName(name: String, ignoreAlbumId: Long?): Boolean {
        if (name.isBlank()) {
            _errorMessage.value = localizedString(R.string.album_name_blank)
            return false
        }

        if (isDuplicateName(name, ignoreAlbumId)) {
            _errorMessage.value = localizedString(R.string.album_name_duplicate)
            return false
        }

        return true
    }

    private fun sanitizeName(name: String): String = name.trim()

    private fun isDuplicateName(name: String, ignoreAlbumId: Long?): Boolean {
        val target = canonicalName(name)
        return albums.value.any { album ->
            if (ignoreAlbumId != null && album.id == ignoreAlbumId) {
                false
            } else {
                canonicalName(album.name) == target
            }
        }
    }

    private fun canonicalName(name: String): String =
        name.trim().lowercase(Locale.ROOT)

    private fun localizedString(resId: Int): String =
        LocaleHelper.wrap(getApplication()).getString(resId)

    private fun didMediaStoreVersionChange(): Boolean {
        val current = mediaStoreVersionProvider.currentVersion(getApplication()) ?: return false
        val previous = mediaStoreVersionStore.readVersion() ?: run {
            mediaStoreVersionStore.writeVersion(current)
            return false
        }

        if (previous == current) return false

        mediaStoreVersionStore.writeVersion(current)
        return true
    }
}