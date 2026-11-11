package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesTest {

    @Test
    fun `resolveFavouriteTier covers sdk and manage media matrix`() {
        val cases = listOf(
            Triple(28, false, FavouriteTier.UNSUPPORTED),
            Triple(28, true, FavouriteTier.UNSUPPORTED),
            Triple(29, false, FavouriteTier.UNSUPPORTED),
            Triple(29, true, FavouriteTier.UNSUPPORTED),
            Triple(30, false, FavouriteTier.FAVORITE_REQUEST),
            Triple(30, true, FavouriteTier.FAVORITE_REQUEST),
            Triple(31, false, FavouriteTier.FAVORITE_REQUEST),
            Triple(31, true, FavouriteTier.MANAGE_MEDIA_WRITE),
            Triple(35, false, FavouriteTier.FAVORITE_REQUEST),
            Triple(35, true, FavouriteTier.MANAGE_MEDIA_WRITE),
            Triple(36, false, FavouriteTier.DIRECT_MARK),
            Triple(36, true, FavouriteTier.DIRECT_MARK)
        )

        cases.forEach { (sdkInt, hasManageMedia, expected) ->
            assertEquals(expected, resolveFavouriteTier(sdkInt, hasManageMedia, rExtensionVersion = 0))
        }
    }

    @Test
    fun `api 28 and 29 are unsupported regardless of manage media`() {
        listOf(28, 29).forEach { sdkInt ->
            assertEquals(FavouriteTier.UNSUPPORTED, resolveFavouriteTier(sdkInt, false))
            assertEquals(FavouriteTier.UNSUPPORTED, resolveFavouriteTier(sdkInt, true))
            assertFalse(isFavouritesSupported(sdkInt))
        }
    }

    @Test
    fun `tier 2 is never selected without manage media`() {
        listOf(28, 29, 30, 31, 35, 36).forEach { sdkInt ->
            val tier = resolveFavouriteTier(sdkInt, hasManageMedia = false, rExtensionVersion = 0)
            assertFalse(tier == FavouriteTier.MANAGE_MEDIA_WRITE)
        }
    }

    @Test
    fun `r extension 16 enables direct mark before api 36`() {
        assertEquals(
            FavouriteTier.DIRECT_MARK,
            resolveFavouriteTier(sdkInt = 35, hasManageMedia = false, rExtensionVersion = 16)
        )
        assertTrue(isFavouritesSupported(35))
    }
}
