package com.davide.seddio.easygallery.data

import androidx.room.withTransaction
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StoredAlbum(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val rule: String?,
    val coverMembershipId: Long?
)

interface AlbumStore {
    fun observeAlbums(): Flow<List<StoredAlbum>>
    fun observeMembershipsForAlbum(albumId: Long): Flow<List<StoredMembership>>

    suspend fun createAlbum(name: String, createdAt: Long, rule: String? = null): Long
    suspend fun renameAlbum(albumId: Long, newName: String)
    suspend fun deleteAlbum(albumId: Long)

    suspend fun addMember(
        albumId: Long,
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): Long

    suspend fun removeMembership(albumId: Long, membershipId: Long)

    suspend fun recordExclusion(
        albumId: Long,
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): Long

    suspend fun writeBackHealedUris(rewrites: List<MembershipUriRewrite>)
    suspend fun updateOrphanStates(changes: List<MembershipOrphanStateChange>)
}

class RoomAlbumStore(
    private val database: EasyGalleryDatabase,
    private val dao: AlbumDao = database.albumDao(),
    private val random: Random = Random.Default
) : AlbumStore {

    override fun observeAlbums(): Flow<List<StoredAlbum>> {
        return dao.observeAlbums().map { albums ->
            albums.map { it.toStoredAlbum() }
        }
    }

    override fun observeMembershipsForAlbum(albumId: Long): Flow<List<StoredMembership>> {
        return dao.observeMembershipsForAlbum(albumId).map { memberships ->
            memberships.map { it.toStoredMembership() }
        }
    }

    override suspend fun createAlbum(name: String, createdAt: Long, rule: String?): Long {
        return dao.insertAlbum(
            AlbumEntity(
                name = name,
                createdAt = createdAt,
                rule = rule,
                coverMembershipId = null
            )
        )
    }

    override suspend fun renameAlbum(albumId: Long, newName: String) {
        val album = dao.getAlbumById(albumId) ?: return
        dao.updateAlbum(album.copy(name = newName))
    }

    override suspend fun deleteAlbum(albumId: Long) {
        val album = dao.getAlbumById(albumId) ?: return
        dao.deleteAlbum(album)
    }

    override suspend fun addMember(
        albumId: Long,
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): Long {
        var membershipId = 0L

        database.withTransaction {
            membershipId = dao.insertMembership(
                AlbumMembershipEntity(
                    albumId = albumId,
                    mediaUri = mediaUri,
                    displayName = displayName,
                    size = size,
                    dateModified = dateModified,
                    kind = AlbumMembershipKind.ADDITION,
                    isOrphaned = false
                )
            )
            assignCoverIfNeeded(albumId)
        }

        return membershipId
    }

    override suspend fun removeMembership(albumId: Long, membershipId: Long) {
        database.withTransaction {
            val album = dao.getAlbumById(albumId) ?: return@withTransaction
            val membership = dao.getMembershipById(membershipId) ?: return@withTransaction
            if (membership.albumId != albumId) return@withTransaction

            val removingCurrentCover = album.coverMembershipId == membershipId
            dao.deleteMembershipById(membershipId)

            if (removingCurrentCover) {
                rerollCover(albumId)
            }
        }
    }

    override suspend fun recordExclusion(
        albumId: Long,
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): Long {
        return dao.insertMembership(
            AlbumMembershipEntity(
                albumId = albumId,
                mediaUri = mediaUri,
                displayName = displayName,
                size = size,
                dateModified = dateModified,
                kind = AlbumMembershipKind.EXCLUSION,
                isOrphaned = false
            )
        )
    }

    override suspend fun writeBackHealedUris(rewrites: List<MembershipUriRewrite>) {
        if (rewrites.isEmpty()) return

        database.withTransaction {
            rewrites.forEach { rewrite ->
                dao.updateMembershipUriIfNoConflict(
                    membershipId = rewrite.membershipId,
                    fromMediaUri = rewrite.fromMediaUri,
                    toMediaUri = rewrite.toMediaUri
                )
            }
        }
    }

    override suspend fun updateOrphanStates(changes: List<MembershipOrphanStateChange>) {
        if (changes.isEmpty()) return

        database.withTransaction {
            changes.forEach { change ->
                val current = dao.getMembershipById(change.membershipId) ?: return@forEach
                if (current.isOrphaned == change.isOrphaned) return@forEach
                dao.updateMembership(current.copy(isOrphaned = change.isOrphaned))
            }
        }
    }

    private suspend fun assignCoverIfNeeded(albumId: Long) {
        val album = dao.getAlbumById(albumId) ?: return
        if (album.coverMembershipId != null) return

        val additions = dao.observeMembershipsForAlbum(albumId)
            .first()
            .filter { it.kind == AlbumMembershipKind.ADDITION }

        if (additions.isEmpty()) return

        val chosen = additions[random.nextInt(additions.size)]
        dao.updateAlbum(album.copy(coverMembershipId = chosen.id))
    }

    private suspend fun rerollCover(albumId: Long) {
        val album = dao.getAlbumById(albumId) ?: return

        val additions = dao.observeMembershipsForAlbum(albumId)
            .first()
            .filter { it.kind == AlbumMembershipKind.ADDITION }

        val nextCoverMembershipId = additions
            .takeIf { it.isNotEmpty() }
            ?.let { candidates -> candidates[random.nextInt(candidates.size)].id }

        dao.updateAlbum(album.copy(coverMembershipId = nextCoverMembershipId))
    }

    private fun AlbumEntity.toStoredAlbum(): StoredAlbum {
        return StoredAlbum(
            id = id,
            name = name,
            createdAt = createdAt,
            rule = rule,
            coverMembershipId = coverMembershipId
        )
    }

    private fun AlbumMembershipEntity.toStoredMembership(): StoredMembership {
        return StoredMembership(
            id = id,
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            kind = kind.toStoredMembershipKind(),
            isOrphaned = isOrphaned
        )
    }

    private fun AlbumMembershipKind.toStoredMembershipKind(): StoredMembershipKind {
        return when (this) {
            AlbumMembershipKind.ADDITION -> StoredMembershipKind.ADDITION
            AlbumMembershipKind.EXCLUSION -> StoredMembershipKind.EXCLUSION
        }
    }
}

class InMemoryAlbumStore(
    private val random: Random = Random.Default
) : AlbumStore {

    private data class MembershipRow(
        val albumId: Long,
        val membership: StoredMembership
    )

    private val lock = Mutex()

    private var nextAlbumId = 1L
    private var nextMembershipId = 1L

    private val albums = linkedMapOf<Long, StoredAlbum>()
    private val memberships = linkedMapOf<Long, MembershipRow>()

    private val albumsFlow = MutableStateFlow<List<StoredAlbum>>(emptyList())
    private val membershipsFlow = MutableStateFlow<List<MembershipRow>>(emptyList())

    override fun observeAlbums(): Flow<List<StoredAlbum>> = albumsFlow

    override fun observeMembershipsForAlbum(albumId: Long): Flow<List<StoredMembership>> {
        return membershipsFlow.map { rows ->
            rows.asSequence()
                .filter { it.albumId == albumId }
                .map { it.membership }
                .toList()
        }
    }

    override suspend fun createAlbum(name: String, createdAt: Long, rule: String?): Long {
        return lock.withLock {
            val albumId = nextAlbumId++
            albums[albumId] = StoredAlbum(
                id = albumId,
                name = name,
                createdAt = createdAt,
                rule = rule,
                coverMembershipId = null
            )
            publishLocked()
            albumId
        }
    }

    override suspend fun renameAlbum(albumId: Long, newName: String) {
        lock.withLock {
            val current = albums[albumId] ?: return@withLock
            albums[albumId] = current.copy(name = newName)
            publishLocked()
        }
    }

    override suspend fun deleteAlbum(albumId: Long) {
        lock.withLock {
            if (albums.remove(albumId) == null) return@withLock
            memberships.entries.removeAll { it.value.albumId == albumId }
            publishLocked()
        }
    }

    override suspend fun addMember(
        albumId: Long,
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): Long {
        return lock.withLock {
            require(albums.containsKey(albumId)) {
                "Album $albumId does not exist"
            }

            val membershipId = nextMembershipId++
            memberships[membershipId] = MembershipRow(
                albumId = albumId,
                membership = StoredMembership(
                    id = membershipId,
                    mediaUri = mediaUri,
                    displayName = displayName,
                    size = size,
                    dateModified = dateModified,
                    kind = StoredMembershipKind.ADDITION,
                    isOrphaned = false
                )
            )

            assignCoverIfNeededLocked(albumId)
            publishLocked()
            membershipId
        }
    }

    override suspend fun removeMembership(albumId: Long, membershipId: Long) {
        lock.withLock {
            val album = albums[albumId] ?: return@withLock
            val row = memberships[membershipId] ?: return@withLock
            if (row.albumId != albumId) return@withLock

            val removingCurrentCover = album.coverMembershipId == membershipId
            memberships.remove(membershipId)

            if (removingCurrentCover) {
                rerollCoverLocked(albumId)
            }

            publishLocked()
        }
    }

    override suspend fun recordExclusion(
        albumId: Long,
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long
    ): Long {
        return lock.withLock {
            require(albums.containsKey(albumId)) {
                "Album $albumId does not exist"
            }

            val membershipId = nextMembershipId++
            memberships[membershipId] = MembershipRow(
                albumId = albumId,
                membership = StoredMembership(
                    id = membershipId,
                    mediaUri = mediaUri,
                    displayName = displayName,
                    size = size,
                    dateModified = dateModified,
                    kind = StoredMembershipKind.EXCLUSION,
                    isOrphaned = false
                )
            )

            publishLocked()
            membershipId
        }
    }

    override suspend fun writeBackHealedUris(rewrites: List<MembershipUriRewrite>) {
        if (rewrites.isEmpty()) return

        lock.withLock {
            rewrites.forEach { rewrite ->
                val row = memberships[rewrite.membershipId] ?: return@forEach
                if (row.membership.mediaUri != rewrite.fromMediaUri) return@forEach
                if (row.membership.mediaUri == rewrite.toMediaUri) return@forEach
                if (hasMembershipUriConflict(
                        albumId = row.albumId,
                        kind = row.membership.kind,
                        mediaUri = rewrite.toMediaUri,
                        excludingMembershipId = rewrite.membershipId
                    )) {
                    return@forEach
                }

                memberships[rewrite.membershipId] = row.copy(
                    membership = row.membership.copy(mediaUri = rewrite.toMediaUri)
                )
            }
            publishLocked()
        }
    }

    override suspend fun updateOrphanStates(changes: List<MembershipOrphanStateChange>) {
        if (changes.isEmpty()) return

        lock.withLock {
            changes.forEach { change ->
                val row = memberships[change.membershipId] ?: return@forEach
                memberships[change.membershipId] = row.copy(
                    membership = row.membership.copy(isOrphaned = change.isOrphaned)
                )
            }
            publishLocked()
        }
    }

    private fun assignCoverIfNeededLocked(albumId: Long) {
        val album = albums[albumId] ?: return
        if (album.coverMembershipId != null) return

        val candidates = memberships.values
            .asSequence()
            .filter { it.albumId == albumId }
            .map { it.membership }
            .filter { it.kind == StoredMembershipKind.ADDITION }
            .toList()

        if (candidates.isEmpty()) return

        val chosen = candidates[random.nextInt(candidates.size)]
        albums[albumId] = album.copy(coverMembershipId = chosen.id)
    }

    private fun rerollCoverLocked(albumId: Long) {
        val album = albums[albumId] ?: return

        val candidates = memberships.values
            .asSequence()
            .filter { it.albumId == albumId }
            .map { it.membership }
            .filter { it.kind == StoredMembershipKind.ADDITION }
            .toList()

        val nextCoverMembershipId = candidates
            .takeIf { it.isNotEmpty() }
            ?.let { nonEmptyCandidates ->
                nonEmptyCandidates[random.nextInt(nonEmptyCandidates.size)].id
            }

        albums[albumId] = album.copy(coverMembershipId = nextCoverMembershipId)
    }

    private fun publishLocked() {
        albumsFlow.value = albums.values.sortedBy { it.id }
        membershipsFlow.value = memberships.values.sortedBy { it.membership.id }
    }

    private fun hasMembershipUriConflict(
        albumId: Long,
        kind: StoredMembershipKind,
        mediaUri: String,
        excludingMembershipId: Long
    ): Boolean {
        return memberships.values.any { row ->
            row.albumId == albumId &&
                row.membership.kind == kind &&
                row.membership.mediaUri == mediaUri &&
                row.membership.id != excludingMembershipId
        }
    }
}
