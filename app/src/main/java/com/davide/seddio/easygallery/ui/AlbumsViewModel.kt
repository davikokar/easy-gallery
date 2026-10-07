package com.davide.seddio.easygallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.LocaleHelper
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.AlbumMembership
import com.davide.seddio.easygallery.data.AlbumStore
import com.davide.seddio.easygallery.data.AndroidMediaStoreVersionProvider
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaStoreVersionProvider
import com.davide.seddio.easygallery.data.MediaStoreVersionStore
import com.davide.seddio.easygallery.data.MembershipOrphanStateChange
import com.davide.seddio.easygallery.data.MembershipUriRewrite
import com.davide.seddio.easygallery.data.RoomAlbumStore
import com.davide.seddio.easygallery.data.SharedPreferencesMediaStoreVersionStore
import com.davide.seddio.easygallery.data.StoredAlbum
import com.davide.seddio.easygallery.data.StoredMembership
import com.davide.seddio.easygallery.data.StoredMembershipKind
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val hiddenMemberCount: Int,
    val storedMembershipCount: Int
)

data class AlbumDetailMember(
    val membershipId: Long,
    val mediaItem: MediaItem
)

class AlbumsViewModel @JvmOverloads constructor(
    application: Application,
    private val albumStore: AlbumStore = RoomAlbumStore((application as EasyGalleryApp).database),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val mediaStoreVersionStore: MediaStoreVersionStore =
        SharedPreferencesMediaStoreVersionStore(application),
    private val mediaStoreVersionProvider: MediaStoreVersionProvider =
        AndroidMediaStoreVersionProvider
) : AndroidViewModel(application) {

    private data class ComputedAlbum(
        val album: StoredAlbum,
        val memberships: List<StoredMembership>,
        val members: List<AlbumDetailMember>,
        val cover: MediaItem?,
        val isCoverFallback: Boolean,
        val resolvedMemberCount: Int,
        val storedMemberCount: Int,
        val storedMembershipCount: Int,
        val uriRewrites: List<MembershipUriRewrite>,
        val orphanStateChanges: List<MembershipOrphanStateChange>
    ) {
        fun toListItem(): AlbumListItem {
            val hidden = (storedMemberCount - resolvedMemberCount).coerceAtLeast(0)
            return AlbumListItem(
                id = album.id,
                name = album.name,
                createdAt = album.createdAt,
                cover = cover,
                isCoverFallback = isCoverFallback,
                resolvedMemberCount = resolvedMemberCount,
                storedMemberCount = storedMemberCount,
                hiddenMemberCount = hidden,
                storedMembershipCount = storedMembershipCount
            )
        }
    }

    private data class PendingHeal(
        val rewrites: List<MembershipUriRewrite>,
        val orphanChanges: List<MembershipOrphanStateChange>
    )

    private val healMutex = Mutex()
    private val lastHealsByAlbumId = mutableMapOf<Long, PendingHeal>()

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _mediaVersionEpoch = MutableStateFlow(0L)
    private val _selectedAlbumId = MutableStateFlow<Long?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val mediaStoreVersionCheckMutex = Mutex()

    val selectedAlbumId: StateFlow<Long?> = _selectedAlbumId.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val computedAlbums: StateFlow<List<ComputedAlbum>> =
        combine(albumStore.observeAlbums(), _allMedia, _mediaVersionEpoch) { albums, media, _ ->
            albums to media
        }.flatMapLatest { (albums, media) ->
            if (albums.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(albums.map { album ->
                    albumStore.observeMembershipsForAlbum(album.id).map { memberships ->
                        computeAlbum(
                            album = album,
                            memberships = memberships,
                            liveMedia = media
                        )
                    }
                }) { computed ->
                    computed.toList()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    init {
        viewModelScope.launch {
            computedAlbums.collect { computed ->
                if (_selectedAlbumId.value != null && computed.none { it.album.id == _selectedAlbumId.value }) {
                    _selectedAlbumId.value = null
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
        _selectedAlbumId.value = albumId
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun createAlbum(name: String) {
        val sanitizedName = sanitizeName(name)
        if (!validateAlbumName(sanitizedName, ignoreAlbumId = null)) return

        viewModelScope.launch {
            albumStore.createAlbum(
                name = sanitizedName,
                createdAt = nowProvider(),
                rule = null
            )
            _errorMessage.value = null
        }
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
            if (_selectedAlbumId.value == albumId) {
                _selectedAlbumId.value = null
            }
        }
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
        viewModelScope.launch {
            albumStore.removeMembership(albumId, membershipId)
        }
    }

    fun removeMembers(albumId: Long, membershipIds: Collection<Long>) {
        if (membershipIds.isEmpty()) return

        viewModelScope.launch {
            membershipIds.forEach { membershipId ->
                albumStore.removeMembership(albumId, membershipId)
            }
        }
    }

    fun removeMembersByMedia(albumId: Long, mediaItems: Collection<MediaItem>) {
        if (mediaItems.isEmpty()) return

        viewModelScope.launch {
            val targetUris = mediaItems.map { it.uri.toString() }.toSet()
            val computed = computedAlbums.value.firstOrNull { it.album.id == albumId } ?: return@launch
            val membershipIds = computed.members
                .filter { member -> member.mediaItem.uri.toString() in targetUris }
                .map { it.membershipId }

            membershipIds.forEach { membershipId ->
                albumStore.removeMembership(albumId, membershipId)
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

    private fun computeAlbum(
        album: StoredAlbum,
        memberships: List<StoredMembership>,
        liveMedia: List<MediaItem>
    ): ComputedAlbum {
        val resolution = AlbumMembership.resolve(memberships, liveMedia)
        val excludedUris = resolution.resolvedExclusions
            .asSequence()
            .map { it.resolvedMediaUri }
            .toHashSet()

        val visibleMembersByUri = LinkedHashMap<String, AlbumDetailMember>()
        resolution.resolvedAdditions.forEach { resolved ->
            if (resolved.resolvedMediaUri in excludedUris) return@forEach
            visibleMembersByUri.putIfAbsent(
                resolved.resolvedMediaUri,
                AlbumDetailMember(
                    membershipId = resolved.membershipId,
                    mediaItem = resolved.mediaItem
                )
            )
        }

        val members = visibleMembersByUri.values.toList()
        val directCover = members.firstOrNull { it.membershipId == album.coverMembershipId }?.mediaItem
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
            storedMembershipCount = memberships.size,
            uriRewrites = resolution.uriRewrites,
            orphanStateChanges = resolution.orphanStateChanges
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