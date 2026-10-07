package com.davide.seddio.easygallery.data

/**
 * Android-free album membership resolution and healing model.
 *
 * Resolution order per membership:
 * 1) Direct URI match against current media
 * 2) Healing-key match (displayName + size + dateModified), with URI rewrite
 * 3) Orphaned (retained, never deleted)
 */
data class StoredMembership(
    val id: Long,
    val mediaUri: String,
    val displayName: String,
    val size: Long,
    val dateModified: Long,
    val kind: StoredMembershipKind,
    val isOrphaned: Boolean
)

enum class StoredMembershipKind {
    ADDITION,
    EXCLUSION
}

data class ResolvedMembership(
    val membershipId: Long,
    val kind: StoredMembershipKind,
    val resolvedMediaUri: String,
    val mediaItem: MediaItem
)

data class MembershipUriRewrite(
    val membershipId: Long,
    val fromMediaUri: String,
    val toMediaUri: String
)

data class MembershipOrphanStateChange(
    val membershipId: Long,
    val isOrphaned: Boolean
)

data class AlbumMembershipResolution(
    val albumItems: List<MediaItem>,
    val resolvedAdditions: List<ResolvedMembership>,
    val resolvedExclusions: List<ResolvedMembership>,
    val uriRewrites: List<MembershipUriRewrite>,
    val orphanedMembershipIds: Set<Long>,
    val orphanStateChanges: List<MembershipOrphanStateChange>
)

private data class HealingKey(
    val displayName: String,
    val size: Long,
    val dateModified: Long
)

private data class MembershipMatch(
    val resolvedMediaUri: String,
    val mediaItem: MediaItem,
    val requiresUriRewrite: Boolean
)

object AlbumMembership {

    fun resolve(
        memberships: List<StoredMembership>,
        liveMedia: List<MediaItem>
    ): AlbumMembershipResolution {
        val mediaByUri = HashMap<String, MediaItem>(liveMedia.size)
        val mediaByHealingKey = HashMap<HealingKey, MediaItem>(liveMedia.size)

        for (item in liveMedia) {
            val uriString = item.uri.toString()
            mediaByUri.putIfAbsent(uriString, item)
            mediaByHealingKey.putIfAbsent(item.toHealingKey(), item)
        }

        val resolvedAdditions = mutableListOf<ResolvedMembership>()
        val resolvedExclusions = mutableListOf<ResolvedMembership>()
        val uriRewrites = mutableListOf<MembershipUriRewrite>()
        val orphanedMembershipIds = linkedSetOf<Long>()
        val orphanStateChanges = mutableListOf<MembershipOrphanStateChange>()

        for (membership in memberships) {
            val match = resolveMembership(membership, mediaByUri, mediaByHealingKey)
            if (match == null) {
                orphanedMembershipIds += membership.id
                if (!membership.isOrphaned) {
                    orphanStateChanges += MembershipOrphanStateChange(
                        membershipId = membership.id,
                        isOrphaned = true
                    )
                }
                continue
            }

            if (membership.isOrphaned) {
                orphanStateChanges += MembershipOrphanStateChange(
                    membershipId = membership.id,
                    isOrphaned = false
                )
            }

            if (match.requiresUriRewrite) {
                uriRewrites += MembershipUriRewrite(
                    membershipId = membership.id,
                    fromMediaUri = membership.mediaUri,
                    toMediaUri = match.resolvedMediaUri
                )
            }

            val resolved = ResolvedMembership(
                membershipId = membership.id,
                kind = membership.kind,
                resolvedMediaUri = match.resolvedMediaUri,
                mediaItem = match.mediaItem
            )

            when (membership.kind) {
                StoredMembershipKind.ADDITION -> resolvedAdditions += resolved
                StoredMembershipKind.EXCLUSION -> resolvedExclusions += resolved
            }
        }

        val excludedUris = resolvedExclusions
            .asSequence()
            .map { it.resolvedMediaUri }
            .toHashSet()

        val includedByUri = LinkedHashMap<String, MediaItem>()
        for (resolvedAddition in resolvedAdditions) {
            if (resolvedAddition.resolvedMediaUri in excludedUris) continue
            includedByUri.putIfAbsent(resolvedAddition.resolvedMediaUri, resolvedAddition.mediaItem)
        }

        return AlbumMembershipResolution(
            albumItems = includedByUri.values.toList(),
            resolvedAdditions = resolvedAdditions,
            resolvedExclusions = resolvedExclusions,
            uriRewrites = uriRewrites,
            orphanedMembershipIds = orphanedMembershipIds,
            orphanStateChanges = orphanStateChanges
        )
    }

    private fun resolveMembership(
        membership: StoredMembership,
        mediaByUri: Map<String, MediaItem>,
        mediaByHealingKey: Map<HealingKey, MediaItem>
    ): MembershipMatch? {
        val direct = mediaByUri[membership.mediaUri]
        if (direct != null) {
            return MembershipMatch(
                resolvedMediaUri = direct.uri.toString(),
                mediaItem = direct,
                requiresUriRewrite = false
            )
        }

        val healed = mediaByHealingKey[membership.toHealingKey()] ?: return null
        return MembershipMatch(
            resolvedMediaUri = healed.uri.toString(),
            mediaItem = healed,
            requiresUriRewrite = true
        )
    }

    private fun StoredMembership.toHealingKey(): HealingKey {
        return HealingKey(
            displayName = displayName,
            size = size,
            dateModified = dateModified
        )
    }

    private fun MediaItem.toHealingKey(): HealingKey {
        return HealingKey(
            displayName = name,
            size = size,
            dateModified = dateModified
        )
    }
}
