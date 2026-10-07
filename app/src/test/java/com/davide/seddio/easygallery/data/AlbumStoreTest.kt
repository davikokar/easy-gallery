package com.davide.seddio.easygallery.data

import android.net.Uri
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class AlbumStoreTest {

    private lateinit var database: EasyGalleryDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyGalleryDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create rename and delete album`() = runTest {
        val store = InMemoryAlbumStore(random = Random(1))

        store.observeAlbums().test {
            assertTrue(awaitItem().isEmpty())

            val albumId = store.createAlbum(name = "Road Trip", createdAt = 10L)

            val created = awaitItem()
            assertEquals(1, created.size)
            assertEquals(albumId, created.single().id)
            assertEquals("Road Trip", created.single().name)

            store.renameAlbum(albumId = albumId, newName = "Summer Road Trip")

            val renamed = awaitItem()
            assertEquals("Summer Road Trip", renamed.single().name)

            store.deleteAlbum(albumId)

            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add and remove members`() = runTest {
        val store = InMemoryAlbumStore(random = Random(2))
        val albumId = store.createAlbum(name = "Members", createdAt = 20L)

        store.observeMembershipsForAlbum(albumId).test {
            assertTrue(awaitItem().isEmpty())

            val firstMembershipId = store.addMember(
                albumId = albumId,
                mediaUri = "content://media/external/images/media/100",
                displayName = "100.jpg",
                size = 100L,
                dateModified = 1_000L
            )

            val afterFirstAdd = awaitItem()
            assertEquals(1, afterFirstAdd.size)
            assertEquals(firstMembershipId, afterFirstAdd.single().id)

            val secondMembershipId = store.addMember(
                albumId = albumId,
                mediaUri = "content://media/external/images/media/101",
                displayName = "101.jpg",
                size = 101L,
                dateModified = 1_001L
            )

            val afterSecondAdd = awaitItem()
            assertEquals(2, afterSecondAdd.size)

            store.removeMembership(albumId = albumId, membershipId = secondMembershipId)

            val afterRemove = awaitItem()
            assertEquals(1, afterRemove.size)
            assertEquals(firstMembershipId, afterRemove.single().id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cover is assigned when first member is added`() = runTest {
        val store = InMemoryAlbumStore(random = Random(3))
        val albumId = store.createAlbum(name = "Cover", createdAt = 30L)

        val membershipId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/200",
            displayName = "200.jpg",
            size = 200L,
            dateModified = 2_000L
        )

        val album = store.observeAlbums().first().single()
        assertEquals(membershipId, album.coverMembershipId)
    }

    @Test
    fun `cover is rerolled when current cover membership is removed`() = runTest {
        val store = InMemoryAlbumStore(random = Random(4))
        val albumId = store.createAlbum(name = "Reroll", createdAt = 40L)

        store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/300",
            displayName = "300.jpg",
            size = 300L,
            dateModified = 3_000L
        )
        store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/301",
            displayName = "301.jpg",
            size = 301L,
            dateModified = 3_001L
        )
        store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/302",
            displayName = "302.jpg",
            size = 302L,
            dateModified = 3_002L
        )

        val before = store.observeAlbums().first().single()
        val previousCoverId = before.coverMembershipId
        assertNotNull(previousCoverId)

        store.removeMembership(albumId = albumId, membershipId = previousCoverId!!)

        val after = store.observeAlbums().first().single()
        val remainingMembershipIds = store.observeMembershipsForAlbum(albumId)
            .first()
            .map { it.id }
            .toSet()

        assertNotNull(after.coverMembershipId)
        assertNotEquals(previousCoverId, after.coverMembershipId)
        assertTrue(after.coverMembershipId in remainingMembershipIds)
    }

    @Test
    fun `cover becomes null when last member is removed`() = runTest {
        val store = InMemoryAlbumStore(random = Random(5))
        val albumId = store.createAlbum(name = "Empty", createdAt = 50L)

        val membershipId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/400",
            displayName = "400.jpg",
            size = 400L,
            dateModified = 4_000L
        )

        store.removeMembership(albumId = albumId, membershipId = membershipId)

        val album = store.observeAlbums().first().single()
        val memberships = store.observeMembershipsForAlbum(albumId).first()

        assertNull(album.coverMembershipId)
        assertTrue(memberships.isEmpty())
    }

    @Test
    fun `orphaned membership survives rename add remove exclusion and write-back`() = runTest {
        val store = InMemoryAlbumStore(random = Random(6))
        val albumId = store.createAlbum(name = "Orphans", createdAt = 60L)

        val orphanId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/500",
            displayName = "500.jpg",
            size = 500L,
            dateModified = 5_000L
        )

        store.updateOrphanStates(
            listOf(
                MembershipOrphanStateChange(
                    membershipId = orphanId,
                    isOrphaned = true
                )
            )
        )

        val removableId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/501",
            displayName = "501.jpg",
            size = 501L,
            dateModified = 5_001L
        )

        store.recordExclusion(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/502",
            displayName = "502.jpg",
            size = 502L,
            dateModified = 5_002L
        )

        store.renameAlbum(albumId, "Renamed Orphans")
        store.removeMembership(albumId = albumId, membershipId = removableId)

        store.writeBackHealedUris(
            listOf(
                MembershipUriRewrite(
                    membershipId = orphanId,
                    fromMediaUri = "content://media/external/images/media/500",
                    toMediaUri = "content://media/external/images/media/500-healed"
                )
            )
        )

        val orphan = store.observeMembershipsForAlbum(albumId)
            .first()
            .firstOrNull { it.id == orphanId }

        assertNotNull(orphan)
        assertTrue(orphan!!.isOrphaned)
        assertEquals("content://media/external/images/media/500-healed", orphan.mediaUri)
    }

    @Test
    fun `healing write-back updates the stored uri`() = runTest {
        val store = InMemoryAlbumStore(random = Random(7))
        val albumId = store.createAlbum(name = "Healing", createdAt = 70L)

        val membershipId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/600",
            displayName = "600.jpg",
            size = 600L,
            dateModified = 6_000L
        )

        store.writeBackHealedUris(
            listOf(
                MembershipUriRewrite(
                    membershipId = membershipId,
                    fromMediaUri = "content://media/external/images/media/600",
                    toMediaUri = "content://media/external/images/media/900"
                )
            )
        )

        val membership = store.observeMembershipsForAlbum(albumId).first().single()
        assertEquals("content://media/external/images/media/900", membership.mediaUri)
    }

    @Test
    fun `healing write-back skips unique collisions and continues remaining rewrites`() = runTest {
        val store = RoomAlbumStore(database = database, random = Random(70))
        val albumId = store.createAlbum(name = "Collision", createdAt = 700L)

        val firstId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/710",
            displayName = "A.jpg",
            size = 710L,
            dateModified = 7_100L
        )
        val secondId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/711",
            displayName = "B.jpg",
            size = 711L,
            dateModified = 7_101L
        )
        val thirdId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/712",
            displayName = "C.jpg",
            size = 712L,
            dateModified = 7_102L
        )

        store.writeBackHealedUris(
            listOf(
                MembershipUriRewrite(
                    membershipId = firstId,
                    fromMediaUri = "content://media/external/images/media/710",
                    toMediaUri = "content://media/external/images/media/799"
                ),
                MembershipUriRewrite(
                    membershipId = secondId,
                    fromMediaUri = "content://media/external/images/media/711",
                    toMediaUri = "content://media/external/images/media/799"
                ),
                MembershipUriRewrite(
                    membershipId = thirdId,
                    fromMediaUri = "content://media/external/images/media/712",
                    toMediaUri = "content://media/external/images/media/812"
                )
            )
        )

        val membershipsById = store.observeMembershipsForAlbum(albumId)
            .first()
            .associateBy { it.id }

        assertEquals(3, membershipsById.size)
        assertEquals("content://media/external/images/media/799", membershipsById.getValue(firstId).mediaUri)
        assertEquals("content://media/external/images/media/711", membershipsById.getValue(secondId).mediaUri)
        assertEquals("content://media/external/images/media/812", membershipsById.getValue(thirdId).mediaUri)
    }

    @Test
    fun `restore-wide healing keeps all memberships when duplicate pair collides on write-back`() = runTest {
        val store = RoomAlbumStore(database = database, random = Random(71))
        val firstAlbumId = store.createAlbum(name = "Restore A", createdAt = 710L)
        val secondAlbumId = store.createAlbum(name = "Restore B", createdAt = 711L)

        val duplicateFirstId = store.addMember(
            albumId = firstAlbumId,
            mediaUri = "content://old/device/1",
            displayName = "DUP.jpg",
            size = 900L,
            dateModified = 9_000L
        )
        val duplicateSecondId = store.addMember(
            albumId = firstAlbumId,
            mediaUri = "content://old/device/2",
            displayName = "DUP.jpg",
            size = 900L,
            dateModified = 9_000L
        )
        val firstAlbumUniqueId = store.addMember(
            albumId = firstAlbumId,
            mediaUri = "content://old/device/3",
            displayName = "UNIQUE_A.jpg",
            size = 901L,
            dateModified = 9_001L
        )
        val secondAlbumResolvedId = store.addMember(
            albumId = secondAlbumId,
            mediaUri = "content://old/device/4",
            displayName = "UNIQUE_B.jpg",
            size = 902L,
            dateModified = 9_002L
        )
        val secondAlbumMissingId = store.addMember(
            albumId = secondAlbumId,
            mediaUri = "content://old/device/5",
            displayName = "MISSING.jpg",
            size = 903L,
            dateModified = 9_003L
        )

        val liveMedia = listOf(
            mediaItem(
                uri = "content://new/device/100",
                name = "DUP.jpg",
                size = 900L,
                dateModified = 9_000L
            ),
            mediaItem(
                uri = "content://new/device/101",
                name = "UNIQUE_A.jpg",
                size = 901L,
                dateModified = 9_001L
            ),
            mediaItem(
                uri = "content://new/device/102",
                name = "UNIQUE_B.jpg",
                size = 902L,
                dateModified = 9_002L
            )
        )

        val firstResolution = AlbumMembership.resolve(
            memberships = store.observeMembershipsForAlbum(firstAlbumId).first(),
            liveMedia = liveMedia
        )
        val secondResolution = AlbumMembership.resolve(
            memberships = store.observeMembershipsForAlbum(secondAlbumId).first(),
            liveMedia = liveMedia
        )

        store.writeBackHealedUris(firstResolution.uriRewrites + secondResolution.uriRewrites)
        store.updateOrphanStates(firstResolution.orphanStateChanges + secondResolution.orphanStateChanges)

        val firstAlbumMemberships = store.observeMembershipsForAlbum(firstAlbumId)
            .first()
            .associateBy { it.id }
        val secondAlbumMemberships = store.observeMembershipsForAlbum(secondAlbumId)
            .first()
            .associateBy { it.id }

        assertEquals(3, firstAlbumMemberships.size)
        assertEquals(2, secondAlbumMemberships.size)
        assertEquals("content://new/device/100", firstAlbumMemberships.getValue(duplicateFirstId).mediaUri)
        assertEquals("content://old/device/2", firstAlbumMemberships.getValue(duplicateSecondId).mediaUri)
        assertEquals("content://new/device/101", firstAlbumMemberships.getValue(firstAlbumUniqueId).mediaUri)
        assertEquals("content://new/device/102", secondAlbumMemberships.getValue(secondAlbumResolvedId).mediaUri)
        assertEquals("content://old/device/5", secondAlbumMemberships.getValue(secondAlbumMissingId).mediaUri)
        assertTrue(secondAlbumMemberships.getValue(secondAlbumMissingId).isOrphaned)
    }

    @Test
    fun `room implementation matches in-memory behavior for cover and orphan flows`() = runTest {
        val inMemory = InMemoryAlbumStore(random = Random(8))
        val roomBacked = RoomAlbumStore(database = database, random = Random(8))

        val inMemorySnapshot = runParityScenario(inMemory)
        val roomSnapshot = runParityScenario(roomBacked)

        assertEquals(inMemorySnapshot, roomSnapshot)
    }

    private suspend fun runParityScenario(store: AlbumStore): AlbumScenarioSnapshot {
        val albumId = store.createAlbum(name = "Parity", createdAt = 80L)

        store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/700",
            displayName = "700.jpg",
            size = 700L,
            dateModified = 7_000L
        )
        val secondMembershipId = store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/701",
            displayName = "701.jpg",
            size = 701L,
            dateModified = 7_001L
        )
        store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/702",
            displayName = "702.jpg",
            size = 702L,
            dateModified = 7_002L
        )

        store.recordExclusion(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/799",
            displayName = "799.jpg",
            size = 799L,
            dateModified = 7_099L
        )

        store.updateOrphanStates(
            listOf(
                MembershipOrphanStateChange(
                    membershipId = secondMembershipId,
                    isOrphaned = true
                )
            )
        )

        val initialCoverId = store.observeAlbums().first().single().coverMembershipId
        if (initialCoverId != null) {
            store.removeMembership(albumId = albumId, membershipId = initialCoverId)
        }

        store.writeBackHealedUris(
            listOf(
                MembershipUriRewrite(
                    membershipId = secondMembershipId,
                    fromMediaUri = "content://media/external/images/media/701",
                    toMediaUri = "content://media/external/images/media/701-healed"
                )
            )
        )

        val album = store.observeAlbums().first().single()
        val memberships = store.observeMembershipsForAlbum(albumId).first()

        return AlbumScenarioSnapshot(
            album = album,
            memberships = memberships
        )
    }

    private data class AlbumScenarioSnapshot(
        val album: StoredAlbum,
        val memberships: List<StoredMembership>
    )

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
}
