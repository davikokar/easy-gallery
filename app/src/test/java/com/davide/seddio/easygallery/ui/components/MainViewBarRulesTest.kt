package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.data.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewBarRulesTest {

    private fun resolve(
        isMediaOpen: Boolean = false,
        isManageExcludedMode: Boolean = false,
        isSettingsMode: Boolean = false,
        hasSelectedFolder: Boolean = false,
        hasSelectedAlbum: Boolean = false,
        isFavouritesSelected: Boolean = false,
        isNewAlbumOpen: Boolean = false,
        isNewAutomaticAlbumOpen: Boolean = false,
        hasPermission: Boolean = true
    ) = resolveShownScreen(
        isMediaOpen = isMediaOpen,
        isManageExcludedMode = isManageExcludedMode,
        isSettingsMode = isSettingsMode,
        hasSelectedFolder = hasSelectedFolder,
        hasSelectedAlbum = hasSelectedAlbum,
        isFavouritesSelected = isFavouritesSelected,
        isNewAlbumOpen = isNewAlbumOpen,
        isNewAutomaticAlbumOpen = isNewAutomaticAlbumOpen,
        hasPermission = hasPermission
    )

    // --- resolveShownScreen -------------------------------------------------------------------

    @Test
    fun `each flag resolves to its own screen`() {
        assertEquals(ShownScreen.FULL_SCREEN_VIEWER, resolve(isMediaOpen = true))
        assertEquals(ShownScreen.MANAGE_EXCLUDED, resolve(isManageExcludedMode = true))
        assertEquals(ShownScreen.SETTINGS, resolve(isSettingsMode = true))
        assertEquals(ShownScreen.FOLDER_DETAIL, resolve(hasSelectedFolder = true))
        assertEquals(ShownScreen.ALBUM_DETAIL, resolve(hasSelectedAlbum = true))
        assertEquals(ShownScreen.FAVOURITES, resolve(isFavouritesSelected = true))
        assertEquals(ShownScreen.NEW_ALBUM, resolve(isNewAlbumOpen = true))
        assertEquals(ShownScreen.NEW_AUTOMATIC_ALBUM, resolve(isNewAutomaticAlbumOpen = true))
        assertEquals(ShownScreen.MAIN_VIEW, resolve())
        assertEquals(ShownScreen.PERMISSION_DENIED, resolve(hasPermission = false))
    }

    @Test
    fun `the viewer wins over every other flag`() {
        val screen = resolve(
            isMediaOpen = true,
            isManageExcludedMode = true,
            isSettingsMode = true,
            hasSelectedFolder = true,
            hasSelectedAlbum = true,
            isFavouritesSelected = true
        )

        assertEquals(ShownScreen.FULL_SCREEN_VIEWER, screen)
    }

    @Test
    fun `settings opened from folder detail wins over the folder`() {
        assertEquals(ShownScreen.SETTINGS, resolve(isSettingsMode = true, hasSelectedFolder = true))
    }

    @Test
    fun `an album id wins over favourites`() {
        assertEquals(
            ShownScreen.ALBUM_DETAIL,
            resolve(hasSelectedAlbum = true, isFavouritesSelected = true)
        )
    }

    @Test
    fun `a restored favourites selection without permission still resolves to favourites`() {
        assertEquals(ShownScreen.FAVOURITES, resolve(isFavouritesSelected = true, hasPermission = false))
    }

    // --- shouldShowMainViewBar ----------------------------------------------------------------

    @Test
    fun `the bar is shown on main views and both album detail screens`() {
        assertTrue(shouldShowMainViewBar(ShownScreen.MAIN_VIEW, isThumbnailPickerMode = false))
        assertTrue(shouldShowMainViewBar(ShownScreen.ALBUM_DETAIL, isThumbnailPickerMode = false))
        assertTrue(shouldShowMainViewBar(ShownScreen.FAVOURITES, isThumbnailPickerMode = false))
    }

    @Test
    fun `the bar is shown on folder detail outside the thumbnail picker`() {
        assertTrue(shouldShowMainViewBar(ShownScreen.FOLDER_DETAIL, isThumbnailPickerMode = false))
    }

    @Test
    fun `the bar is hidden on folder detail while the thumbnail picker is open`() {
        assertFalse(shouldShowMainViewBar(ShownScreen.FOLDER_DETAIL, isThumbnailPickerMode = true))
    }

    @Test
    fun `the bar is hidden on every focused screen`() {
        listOf(
            ShownScreen.FULL_SCREEN_VIEWER,
            ShownScreen.MANAGE_EXCLUDED,
            ShownScreen.SETTINGS,
            ShownScreen.NEW_ALBUM,
            ShownScreen.NEW_AUTOMATIC_ALBUM,
            ShownScreen.PERMISSION_DENIED
        ).forEach { screen ->
            assertFalse("$screen", shouldShowMainViewBar(screen, isThumbnailPickerMode = false))
        }
    }

    @Test
    fun `a stale picker flag does not show the bar on a hidden screen or hide it on a main view`() {
        assertFalse(shouldShowMainViewBar(ShownScreen.SETTINGS, isThumbnailPickerMode = true))
        assertTrue(shouldShowMainViewBar(ShownScreen.MAIN_VIEW, isThumbnailPickerMode = true))
    }

    @Test
    fun `every screen has an explicit visibility rule`() {
        val shown = ShownScreen.entries.filter { shouldShowMainViewBar(it, isThumbnailPickerMode = false) }

        assertEquals(
            setOf(
                ShownScreen.MAIN_VIEW,
                ShownScreen.FOLDER_DETAIL,
                ShownScreen.ALBUM_DETAIL,
                ShownScreen.FAVOURITES
            ),
            shown.toSet()
        )
    }

    // --- selectedMainViewTab ------------------------------------------------------------------

    @Test
    fun `a main view selects the tab of its display mode`() {
        assertEquals(MainViewTab.FOLDERS, selectedMainViewTab(ShownScreen.MAIN_VIEW, DisplayMode.FOLDERS))
        assertEquals(MainViewTab.ALBUMS, selectedMainViewTab(ShownScreen.MAIN_VIEW, DisplayMode.ALBUMS))
        assertEquals(MainViewTab.TIMELINE, selectedMainViewTab(ShownScreen.MAIN_VIEW, DisplayMode.TIMELINE))
    }

    @Test
    fun `folder detail selects folders whatever the display mode`() {
        DisplayMode.entries.forEach { mode ->
            assertEquals("$mode", MainViewTab.FOLDERS, selectedMainViewTab(ShownScreen.FOLDER_DETAIL, mode))
        }
    }

    @Test
    fun `album detail selects albums whatever the display mode`() {
        DisplayMode.entries.forEach { mode ->
            assertEquals("$mode", MainViewTab.ALBUMS, selectedMainViewTab(ShownScreen.ALBUM_DETAIL, mode))
        }
    }

    @Test
    fun `favourites selects albums after process death when display mode is folders`() {
        assertEquals(MainViewTab.ALBUMS, selectedMainViewTab(ShownScreen.FAVOURITES, DisplayMode.FOLDERS))
    }

    @Test
    fun `favourites selects albums in every display mode`() {
        DisplayMode.entries.forEach { mode ->
            assertEquals("$mode", MainViewTab.ALBUMS, selectedMainViewTab(ShownScreen.FAVOURITES, mode))
        }
    }

    // --- toDisplayMode ------------------------------------------------------------------------

    @Test
    fun `each tab maps to its display mode and back`() {
        assertEquals(DisplayMode.FOLDERS, MainViewTab.FOLDERS.toDisplayMode())
        assertEquals(DisplayMode.ALBUMS, MainViewTab.ALBUMS.toDisplayMode())
        assertEquals(DisplayMode.TIMELINE, MainViewTab.TIMELINE.toDisplayMode())
        MainViewTab.entries.forEach { tab ->
            assertEquals(tab, tab.toDisplayMode().toMainViewTab())
        }
    }
}
