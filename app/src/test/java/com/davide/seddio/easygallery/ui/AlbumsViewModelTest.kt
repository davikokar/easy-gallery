package com.davide.seddio.easygallery.ui

import android.app.Application
import android.net.Uri
import app.cash.turbine.test
import com.davide.seddio.easygallery.data.AlbumStore
import com.davide.seddio.easygallery.data.InMemoryAlbumStore
import com.davide.seddio.easygallery.data.InMemoryMediaStoreVersionStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaStoreVersionProvider
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.MembershipOrphanStateChange
import com.davide.seddio.easygallery.data.MembershipUriRewrite
import com.davide.seddio.easygallery.data.StoredAlbum
import com.davide.seddio.easygallery.data.StoredMembership
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val application = mockk<Application>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { application.getString(com.davide.seddio.easygallery.R.string.album_name_blank) } returns "Album name cannot be blank"
        every { application.getString(com.davide.seddio.easygallery.R.string.album_name_duplicate) } returns "An album with this name already exists"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create rename and delete album`() = runTest {
        val viewModel = createViewModel()

        viewModel.albums.test {
            assertTrue(awaitItem().isEmpty())

            viewModel.createAlbum("Trips")
            val created = awaitItem()
            assertEquals(1, created.size)
            val albumId = created.single().id
            assertEquals("Trips", created.single().name)

            viewModel.renameAlbum(albumId, "Summer Trips")
            val renamed = awaitItem()
            assertEquals("Summer Trips", renamed.single().name)

            viewModel.deleteAlbum(albumId)
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate and blank names are rejected`() = runTest {
        val viewModel = createViewModel()

        viewModel.createAlbum("A")
        advanceUntilIdle()
        assertNull(viewModel.errorMessage.value)

        viewModel.createAlbum("   ")
        assertEquals("Album name cannot be blank", viewModel.errorMessage.value)

        viewModel.createAlbum("  a  ")
        assertEquals("An album with this name already exists", viewModel.errorMessage.value)
    }

    @Test
    fun `add and remove members`() = runTest {
        val viewModel = createViewModel()

        viewModel.createAlbum("Members")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(first, second))

        viewModel.addMembers(albumId, listOf(first, second))
        advanceUntilIdle()

        assertEquals(2, viewModel.albums.value.single().resolvedMemberCount)

        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val membershipIdToRemove = viewModel.selectedAlbumMembers.value.first().membershipId
        viewModel.removeMember(albumId, membershipIdToRemove)
        advanceUntilIdle()

        assertEquals(1, viewModel.albums.value.single().resolvedMemberCount)
        assertEquals(1, viewModel.selectedAlbumMembers.value.size)
    }

    @Test
    fun `cover appears on first member and rerolls when that member is removed`() = runTest {
        val store = InMemoryAlbumStore(random = kotlin.random.Random(1))
        val viewModel = createViewModel(albumStore = store)

        viewModel.createAlbum("Cover")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val first = mediaItem("content://media/external/images/media/10", "10.jpg")
        val second = mediaItem("content://media/external/images/media/11", "11.jpg")
        viewModel.setAllMedia(listOf(first, second))

        viewModel.addMembers(albumId, listOf(first, second))
        advanceUntilIdle()

        val before = viewModel.albums.value.single()
        assertTrue(before.cover != null)

        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val coverMembershipId = viewModel.selectedAlbumMembers.value
            .first { it.mediaItem.uri.toString() == before.cover?.uri.toString() }
            .membershipId

        viewModel.removeMember(albumId, coverMembershipId)
        advanceUntilIdle()

        val after = viewModel.albums.value.single()
        assertTrue(after.cover != null)
        assertFalse(after.cover?.uri.toString() == before.cover?.uri.toString())
    }

    @Test
    fun `resolved members exclude orphans while stored count still includes them`() = runTest {
        val viewModel = createViewModel()

        viewModel.createAlbum("Orphans")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val existing = mediaItem("content://media/external/images/media/30", "30.jpg")
        val orphanSoon = mediaItem("content://media/external/images/media/31", "31.jpg")

        viewModel.setAllMedia(listOf(existing, orphanSoon))
        viewModel.addMembers(albumId, listOf(existing, orphanSoon))
        advanceUntilIdle()

        viewModel.setAllMedia(listOf(existing))
        advanceUntilIdle()

        val album = viewModel.albums.value.single()
        assertEquals(1, album.resolvedMemberCount)
        assertEquals(2, album.storedMemberCount)
        assertEquals(1, album.hiddenMemberCount)
    }

    @Test
    fun `healing write back runs once and does not repeat for identical second resolution`() = runTest {
        val countingStore = CountingAlbumStore()
        val viewModel = createViewModel(albumStore = countingStore)

        viewModel.createAlbum("Healing")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val original = mediaItem("content://media/external/images/media/50", "50.jpg", size = 123L, dateModified = 999L)
        viewModel.setAllMedia(listOf(original))
        viewModel.addMembers(albumId, listOf(original))
        advanceUntilIdle()

        val healed = mediaItem("content://media/external/images/media/500", "50.jpg", size = 123L, dateModified = 999L)
        viewModel.setAllMedia(listOf(healed))
        advanceUntilIdle()

        val writesAfterFirstHeal = countingStore.writeBackCalls
        assertEquals(1, writesAfterFirstHeal)

        viewModel.setAllMedia(listOf(healed))
        advanceUntilIdle()

        assertEquals(writesAfterFirstHeal, countingStore.writeBackCalls)
    }

    private fun createViewModel(albumStore: AlbumStore = InMemoryAlbumStore()): AlbumsViewModel {
        return AlbumsViewModel(
            application = application,
            albumStore = albumStore,
            nowProvider = { 1_000L },
            mediaStoreVersionStore = InMemoryMediaStoreVersionStore(),
            mediaStoreVersionProvider = FixedMediaStoreVersionProvider("v1")
        )
    }

    private fun mediaItem(
        uri: String,
        name: String,
        size: Long = 1L,
        dateModified: Long = 1L
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

    private class FixedMediaStoreVersionProvider(
        private val value: String?
    ) : MediaStoreVersionProvider {
        override fun currentVersion(context: android.content.Context): String? = value
    }

    private class CountingAlbumStore(
        private val delegate: AlbumStore = InMemoryAlbumStore()
    ) : AlbumStore by delegate {
        var writeBackCalls: Int = 0
            private set

        var orphanUpdateCalls: Int = 0
            private set

        override suspend fun writeBackHealedUris(rewrites: List<MembershipUriRewrite>) {
            if (rewrites.isNotEmpty()) {
                writeBackCalls += 1
            }
            delegate.writeBackHealedUris(rewrites)
        }

        override suspend fun updateOrphanStates(changes: List<MembershipOrphanStateChange>) {
            if (changes.isNotEmpty()) {
                orphanUpdateCalls += 1
            }
            delegate.updateOrphanStates(changes)
        }

        override fun observeAlbums(): Flow<List<StoredAlbum>> = delegate.observeAlbums()

        override fun observeMembershipsForAlbum(albumId: Long): Flow<List<StoredMembership>> {
            return delegate.observeMembershipsForAlbum(albumId)
        }
    }
}