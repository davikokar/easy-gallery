package com.davide.seddio.easygallery.ui

import android.app.Application
import android.net.Uri
import app.cash.turbine.test
import com.davide.seddio.easygallery.data.FavouriteTier
import com.davide.seddio.easygallery.data.InMemoryFavouritesDataSource
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val uriA = mockk<Uri>(relaxed = true)
    private val uriB = mockk<Uri>(relaxed = true)

    @Test
    fun `toggle favourite on and off updates observable set`() = runTest {
        val dataSource = InMemoryFavouritesDataSource(sdkInt = 36)
        val viewModel = FavouritesViewModel(
            application = application,
            dataSource = dataSource,
            supportsManageMediaRationale = true
        )

        viewModel.favourites.test {
            assertTrue(awaitItem().isEmpty())

            viewModel.toggleFavourite(uriA)
            assertEquals(setOf(uriA), awaitItem())
            assertTrue(dataSource.isFavourite(uriA))

            viewModel.toggleFavourite(uriA)
            assertTrue(awaitItem().isEmpty())
            assertFalse(dataSource.isFavourite(uriA))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle favourites supports multi-selection on and off`() = runTest {
        val dataSource = InMemoryFavouritesDataSource(sdkInt = 36)
        val viewModel = FavouritesViewModel(
            application = application,
            dataSource = dataSource,
            supportsManageMediaRationale = true
        )

        viewModel.favourites.test {
            assertTrue(awaitItem().isEmpty())

            viewModel.toggleFavourites(listOf(uriA, uriB))
            assertEquals(setOf(uriA, uriB), awaitItem())

            viewModel.toggleFavourites(listOf(uriA, uriB))
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `api 28 and 29 report unavailable and writes are no-op`() {
        val dataSource = InMemoryFavouritesDataSource(sdkInt = 29)
        val viewModel = FavouritesViewModel(
            application = application,
            dataSource = dataSource,
            supportsManageMediaRationale = true
        )

        assertFalse(viewModel.isAvailable.value)
        assertEquals(FavouriteTier.UNSUPPORTED, viewModel.currentTier.value)

        viewModel.toggleFavourite(uriA)

        assertTrue(viewModel.favourites.value.isEmpty())
        assertNull(viewModel.pendingWriteRequest.value)
        assertFalse(dataSource.isFavourite(uriA))
    }

    @Test
    fun `tier 2 write emits pending request and completes after grant`() = runTest {
        val dataSource = InMemoryFavouritesDataSource(sdkInt = 31, hasManageMedia = true)
        val viewModel = FavouritesViewModel(
            application = application,
            dataSource = dataSource,
            supportsManageMediaRationale = true
        )

        assertEquals(FavouriteTier.MANAGE_MEDIA_WRITE, viewModel.currentTier.value)

        viewModel.pendingWriteRequest.test {
            assertNull(awaitItem())

            viewModel.toggleFavourite(uriA)
            assertTrue(viewModel.isFavourite(uriA))
            assertNull(awaitItem()?.intentSender)

            viewModel.onPendingWriteRequestResult(granted = true)
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(viewModel.isFavourite(uriA))
        assertTrue(dataSource.isFavourite(uriA))
    }

    @Test
    fun `denied grant reverts optimistic heart state and clears pending request`() {
        val dataSource = InMemoryFavouritesDataSource(sdkInt = 31, hasManageMedia = true)
        val viewModel = FavouritesViewModel(
            application = application,
            dataSource = dataSource,
            supportsManageMediaRationale = true
        )

        viewModel.toggleFavourite(uriA)
        assertTrue(viewModel.isFavourite(uriA))
        assertNull(viewModel.pendingWriteRequest.value?.intentSender)

        viewModel.onPendingWriteRequestResult(granted = false)

        assertFalse(viewModel.isFavourite(uriA))
        assertNull(viewModel.pendingWriteRequest.value)
        assertFalse(dataSource.isFavourite(uriA))
    }

    @Test
    fun `manage media rationale is offered only after a system prompt and not re-offered after decline`() {
        val dataSource = InMemoryFavouritesDataSource(sdkInt = 31, hasManageMedia = false)
        val viewModel = FavouritesViewModel(
            application = application,
            dataSource = dataSource,
            supportsManageMediaRationale = true
        )

        assertFalse(viewModel.shouldOfferManageMediaRationale.value)

        viewModel.toggleFavourite(uriA)

        assertTrue(viewModel.shouldOfferManageMediaRationale.value)

        viewModel.onManageMediaRationaleDeclined()
        assertFalse(viewModel.shouldOfferManageMediaRationale.value)

        viewModel.onPendingWriteRequestResult(granted = false)
        viewModel.toggleFavourite(uriB)

        assertFalse(viewModel.shouldOfferManageMediaRationale.value)
    }
}
