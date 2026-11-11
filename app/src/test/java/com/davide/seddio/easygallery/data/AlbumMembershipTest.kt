package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumMembershipTest {

    @Test
    fun `membership heals by key when uri no longer resolves`() {
        val oldUri = "content://media/old/1"
        val healedUri = "content://media/new/1"
        val liveItem = mediaItem(
            uri = healedUri,
            name = "IMG_0001.jpg",
            size = 42L,
            dateModified = 1234L
        )
        val membership = membership(
            id = 1L,
            mediaUri = oldUri,
            displayName = "IMG_0001.jpg",
            size = 42L,
            dateModified = 1234L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = false
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(membership),
            liveMedia = listOf(liveItem)
        )

        assertEquals(listOf(liveItem), result.albumItems)
        assertEquals(1, result.uriRewrites.size)
        assertEquals(oldUri, result.uriRewrites.single().fromMediaUri)
        assertEquals(healedUri, result.uriRewrites.single().toMediaUri)
        assertTrue(result.orphanedMembershipIds.isEmpty())
    }

    @Test
    fun `unmatched membership becomes orphaned is retained and excluded from album items`() {
        val membership = membership(
            id = 2L,
            mediaUri = "content://media/missing/2",
            displayName = "missing.jpg",
            size = 999L,
            dateModified = 5678L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = false
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(membership),
            liveMedia = emptyList()
        )

        assertTrue(result.albumItems.isEmpty())
        assertEquals(setOf(2L), result.orphanedMembershipIds)
        assertEquals(1, result.orphanStateChanges.size)
        assertEquals(2L, result.orphanStateChanges.single().membershipId)
        assertTrue(result.orphanStateChanges.single().isOrphaned)
    }

    @Test
    fun `orphaned membership rejoins album when file reappears`() {
        val uri = "content://media/item/3"
        val liveItem = mediaItem(
            uri = uri,
            name = "IMG_0003.jpg",
            size = 73L,
            dateModified = 9876L
        )
        val membership = membership(
            id = 3L,
            mediaUri = uri,
            displayName = "IMG_0003.jpg",
            size = 73L,
            dateModified = 9876L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = true
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(membership),
            liveMedia = listOf(liveItem)
        )

        assertEquals(listOf(liveItem), result.albumItems)
        assertTrue(result.orphanedMembershipIds.isEmpty())
        assertEquals(1, result.orphanStateChanges.size)
        assertEquals(3L, result.orphanStateChanges.single().membershipId)
        assertFalse(result.orphanStateChanges.single().isOrphaned)
    }

    @Test
    fun `unresolved exclusion tombstone is retained as orphan and not dropped`() {
        val exclusion = membership(
            id = 4L,
            mediaUri = "content://media/missing/4",
            displayName = "removed.jpg",
            size = 11L,
            dateModified = 22L,
            kind = StoredMembershipKind.EXCLUSION,
            isOrphaned = false
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(exclusion),
            liveMedia = emptyList()
        )

        assertTrue(result.resolvedExclusions.isEmpty())
        assertEquals(setOf(4L), result.orphanedMembershipIds)
        assertEquals(1, result.orphanStateChanges.size)
        assertTrue(result.orphanStateChanges.single().isOrphaned)
    }

    @Test
    fun `item that is both added and excluded is excluded from final album items`() {
        val uri = "content://media/item/5"
        val liveItem = mediaItem(
            uri = uri,
            name = "IMG_0005.jpg",
            size = 5L,
            dateModified = 5L
        )
        val addition = membership(
            id = 5L,
            mediaUri = uri,
            displayName = "IMG_0005.jpg",
            size = 5L,
            dateModified = 5L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = false
        )
        val exclusion = membership(
            id = 6L,
            mediaUri = uri,
            displayName = "IMG_0005.jpg",
            size = 5L,
            dateModified = 5L,
            kind = StoredMembershipKind.EXCLUSION,
            isOrphaned = false
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(addition, exclusion),
            liveMedia = listOf(liveItem)
        )

        assertTrue(result.albumItems.isEmpty())
        assertEquals(1, result.resolvedAdditions.size)
        assertEquals(1, result.resolvedExclusions.size)
    }

    @Test
    fun `healing key collision resolves deterministically to first matching live item`() {
        val first = mediaItem(
            uri = "content://media/item/6-first",
            name = "DUPLICATE.jpg",
            size = 88L,
            dateModified = 1000L
        )
        val second = mediaItem(
            uri = "content://media/item/6-second",
            name = "DUPLICATE.jpg",
            size = 88L,
            dateModified = 1000L
        )
        val membership = membership(
            id = 7L,
            mediaUri = "content://media/missing/7",
            displayName = "DUPLICATE.jpg",
            size = 88L,
            dateModified = 1000L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = false
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(membership),
            liveMedia = listOf(first, second)
        )

        assertEquals(listOf(first), result.albumItems)
        assertEquals("content://media/item/6-first", result.uriRewrites.single().toMediaUri)
    }

    @Test
    fun `duplicate memberships sharing one healing key emit duplicate rewrites to same live uri`() {
        val liveItem = mediaItem(
            uri = "content://media/item/7-healed",
            name = "SAME.jpg",
            size = 90L,
            dateModified = 1_200L
        )
        val firstMembership = membership(
            id = 70L,
            mediaUri = "content://media/missing/70",
            displayName = "SAME.jpg",
            size = 90L,
            dateModified = 1_200L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = false
        )
        val secondMembership = membership(
            id = 71L,
            mediaUri = "content://media/missing/71",
            displayName = "SAME.jpg",
            size = 90L,
            dateModified = 1_200L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = false
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(firstMembership, secondMembership),
            liveMedia = listOf(liveItem)
        )

        assertEquals(2, result.resolvedAdditions.size)
        assertEquals(2, result.uriRewrites.size)
        assertTrue(result.uriRewrites.all { it.toMediaUri == "content://media/item/7-healed" })
        assertEquals(listOf(liveItem), result.albumItems)
    }

    @Test
    fun `already orphaned unresolved membership does not emit duplicate orphan state change`() {
        val membership = membership(
            id = 8L,
            mediaUri = "content://media/missing/8",
            displayName = "still-missing.jpg",
            size = 80L,
            dateModified = 800L,
            kind = StoredMembershipKind.ADDITION,
            isOrphaned = true
        )

        val result = AlbumMembership.resolve(
            memberships = listOf(membership),
            liveMedia = emptyList()
        )

        assertEquals(setOf(8L), result.orphanedMembershipIds)
        assertTrue(result.orphanStateChanges.isEmpty())
    }

    private fun mediaItem(
        uri: String,
        name: String,
        size: Long,
        dateModified: Long
    ): MediaItem {
        val uriMock = mockk<Uri>()
        every { uriMock.toString() } returns uri

        return MediaItem(
            uri = uriMock,
            name = name,
            dateAdded = dateModified,
            dateModified = dateModified,
            size = size,
            type = MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
    }

    private fun membership(
        id: Long,
        mediaUri: String,
        displayName: String,
        size: Long,
        dateModified: Long,
        kind: StoredMembershipKind,
        isOrphaned: Boolean
    ): StoredMembership {
        return StoredMembership(
            id = id,
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            kind = kind,
            isOrphaned = isOrphaned
        )
    }
}
