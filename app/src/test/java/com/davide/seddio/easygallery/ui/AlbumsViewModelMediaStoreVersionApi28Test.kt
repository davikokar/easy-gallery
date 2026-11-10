package com.davide.seddio.easygallery.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.davide.seddio.easygallery.data.AlbumStore
import com.davide.seddio.easygallery.data.AndroidMediaStoreVersionProvider
import com.davide.seddio.easygallery.data.InMemoryAlbumStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexStore
import com.davide.seddio.easygallery.data.InMemoryMediaStoreVersionStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.StoredMembership
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * albums.md §10 question 5: on API 28 the production [AndroidMediaStoreVersionProvider] must reach
 * [AlbumsViewModel], so a MediaStore version change re-evaluates albums there too.
 *
 * The re-evaluation is observed as a re-subscription to the album's memberships: the version
 * check bumps an epoch that restarts the evaluation, and nothing else restarts it when the live
 * media list is equal to the one already held.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class AlbumsViewModelMediaStoreVersionApi28Test {

    private val dispatcher = UnconfinedTestDispatcher()
    private val currentVersion = AtomicReference("v1")
    private var viewModel: AlbumsViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // MediaStore's static initialiser must run before the static mock is installed.
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `a MediaStore version change re-evaluates albums on API 28`() = runTest {
        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(any<Context>()) } answers { currentVersion.get() }
            val albumStore = SubscriptionCountingAlbumStore()
            val versionStore = InMemoryMediaStoreVersionStore()
            val vm = createViewModel(albumStore, versionStore)
            val media = liveMedia()
            vm.createAlbum("Trips")
            advanceUntilIdle()

            vm.setAllMediaAndAwaitVersionCheck(media)
            assertEquals(28, Build.VERSION.SDK_INT)
            assertEquals("v1", versionStore.readVersion())
            val subscriptionsBeforeChange = albumStore.membershipSubscriptions.get()

            currentVersion.set("v2")
            vm.setAllMediaAndAwaitVersionCheck(media.toList())
            awaitSubscriptionsAbove(albumStore, subscriptionsBeforeChange)

            assertEquals("v2", versionStore.readVersion())
        }
    }

    @Test
    fun `an unchanged MediaStore version does not re-evaluate albums on API 28`() = runTest {
        mockkStatic(MediaStore::class) {
            every { MediaStore.getVersion(any<Context>()) } answers { currentVersion.get() }
            val albumStore = SubscriptionCountingAlbumStore()
            val versionStore = InMemoryMediaStoreVersionStore()
            val vm = createViewModel(albumStore, versionStore)
            val media = liveMedia()
            vm.createAlbum("Trips")
            advanceUntilIdle()
            vm.setAllMediaAndAwaitVersionCheck(media)
            val subscriptionsBeforeRecheck = albumStore.membershipSubscriptions.get()

            vm.setAllMediaAndAwaitVersionCheck(media.toList())
            advanceUntilIdle()

            assertEquals("v1", versionStore.readVersion())
            assertEquals(subscriptionsBeforeRecheck, albumStore.membershipSubscriptions.get())
        }
    }

    private fun createViewModel(
        albumStore: AlbumStore,
        versionStore: InMemoryMediaStoreVersionStore
    ): AlbumsViewModel {
        val application: Application = ApplicationProvider.getApplicationContext()
        return AlbumsViewModel(
            application = application,
            albumStore = albumStore,
            nowProvider = { 1_000L },
            mediaStoreVersionStore = versionStore,
            mediaStoreVersionProvider = AndroidMediaStoreVersionProvider,
            mediaIndexStore = InMemoryMediaIndexStore(),
            evaluationDispatcher = dispatcher
        ).also { viewModel = it }
    }

    // setAllMedia checks the version on Dispatchers.IO; join that one job rather than sleeping.
    private suspend fun AlbumsViewModel.setAllMediaAndAwaitVersionCheck(media: List<MediaItem>) {
        val scopeJob = viewModelScope.coroutineContext.job
        val childrenBefore = scopeJob.children.toSet()
        setAllMedia(media)
        val versionCheck = scopeJob.children.filter { it !in childrenBefore }.toList()
        withContext(Dispatchers.Default) {
            withTimeout(AWAIT_TIMEOUT_MS) { versionCheck.joinAll() }
        }
    }

    private suspend fun awaitSubscriptionsAbove(store: SubscriptionCountingAlbumStore, floor: Int) {
        withContext(Dispatchers.Default) {
            withTimeout(AWAIT_TIMEOUT_MS) {
                while (store.membershipSubscriptions.get() <= floor) delay(10)
            }
        }
    }

    private fun liveMedia(): List<MediaItem> = listOf(1L, 2L).map { id ->
        MediaItem(
            uri = Uri.parse("content://media/external/images/media/$id"),
            name = "$id.jpg",
            dateAdded = id,
            dateModified = id,
            size = id,
            type = MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
    }

    private class SubscriptionCountingAlbumStore(
        private val delegate: AlbumStore = InMemoryAlbumStore()
    ) : AlbumStore by delegate {
        val membershipSubscriptions = AtomicInteger(0)

        override fun observeMembershipsForAlbum(albumId: Long): Flow<List<StoredMembership>> {
            membershipSubscriptions.incrementAndGet()
            return delegate.observeMembershipsForAlbum(albumId)
        }
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 10_000L
    }
}
