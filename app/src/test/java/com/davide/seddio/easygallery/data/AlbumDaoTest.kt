package com.davide.seddio.easygallery.data

import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class AlbumDaoTest {

    private lateinit var database: EasyGalleryDatabase
    private lateinit var dao: AlbumDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyGalleryDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.albumDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserted album is emitted and can be read back`() = runTest {
        dao.observeAlbums().test {
            assertTrue(awaitItem().isEmpty())

            val id = dao.insertAlbum(
                AlbumEntity(
                    name = "Road Trip",
                    createdAt = 1_000L,
                    rule = null,
                    coverMembershipId = null
                )
            )

            val albums = awaitItem()
            assertEquals(1, albums.size)
            assertEquals(id, albums.single().id)
            assertEquals("Road Trip", albums.single().name)

            val stored = dao.getAlbumById(id)
            assertNotNull(stored)
            assertEquals("Road Trip", stored?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting an album cascades to its memberships`() = runTest {
        val albumId = dao.insertAlbum(AlbumEntity(name = "Cascade", createdAt = 5L))

        dao.observeMembershipsForAlbum(albumId).test {
            assertTrue(awaitItem().isEmpty())

            dao.insertMembership(
                AlbumMembershipEntity(
                    albumId = albumId,
                    mediaUri = "content://media/external/images/media/10",
                    displayName = "p1.jpg",
                    size = 12L,
                    dateModified = 34L,
                    kind = AlbumMembershipKind.ADDITION,
                    isOrphaned = false
                )
            )

            assertEquals(1, awaitItem().size)

            dao.deleteAlbum(dao.getAlbumById(albumId)!!)

            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate albumId mediaUri and kind is rejected by unique index`() = runTest {
        val albumId = dao.insertAlbum(AlbumEntity(name = "Unique", createdAt = 10L))

        dao.insertMembership(
            AlbumMembershipEntity(
                albumId = albumId,
                mediaUri = "content://media/external/images/media/24",
                displayName = "a.jpg",
                size = 100L,
                dateModified = 500L,
                kind = AlbumMembershipKind.ADDITION,
                isOrphaned = false
            )
        )

        val failure = runCatching {
            dao.insertMembership(
                AlbumMembershipEntity(
                    albumId = albumId,
                    mediaUri = "content://media/external/images/media/24",
                    displayName = "renamed.jpg",
                    size = 101L,
                    dateModified = 600L,
                    kind = AlbumMembershipKind.ADDITION,
                    isOrphaned = true
                )
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(
            failure is SQLiteConstraintException ||
                failure?.cause is SQLiteConstraintException ||
                failure?.message.orEmpty().contains("UNIQUE", ignoreCase = true)
        )
    }

    @Test
    fun `orphaned membership survives a round trip`() = runTest {
        val albumId = dao.insertAlbum(AlbumEntity(name = "Orphans", createdAt = 22L))

        val membershipId = dao.insertMembership(
            AlbumMembershipEntity(
                albumId = albumId,
                mediaUri = "content://media/external/video/media/7",
                displayName = "clip.mp4",
                size = 1_000L,
                dateModified = 2_000L,
                kind = AlbumMembershipKind.EXCLUSION,
                isOrphaned = true
            )
        )

        val stored = dao.getMembershipById(membershipId)
        assertNotNull(stored)
        assertTrue(stored?.isOrphaned == true)

        dao.observeAllMemberships().test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertTrue(rows.single().isOrphaned)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting cover membership clears coverMembershipId via foreign key set null`() = runTest {
        val albumId = dao.insertAlbum(AlbumEntity(name = "Covers", createdAt = 99L))

        val membershipId = dao.insertMembership(
            AlbumMembershipEntity(
                albumId = albumId,
                mediaUri = "content://media/external/images/media/99",
                displayName = "cover.jpg",
                size = 5L,
                dateModified = 6L,
                kind = AlbumMembershipKind.ADDITION,
                isOrphaned = false
            )
        )

        dao.updateAlbum(
            AlbumEntity(
                id = albumId,
                name = "Covers",
                createdAt = 99L,
                rule = null,
                coverMembershipId = membershipId
            )
        )

        val withCover = dao.getAlbumById(albumId)
        assertEquals(membershipId, withCover?.coverMembershipId)

        dao.deleteMembershipById(membershipId)

        val withoutCover = dao.getAlbumById(albumId)
        assertNotNull(withoutCover)
        assertNull(withoutCover?.coverMembershipId)
    }
}
