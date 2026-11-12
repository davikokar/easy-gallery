package com.davide.seddio.easygallery.ui.components

import com.davide.seddio.easygallery.data.DisplayMode

/** What `MainActivity`'s branch chain is showing; the bar's rules are a function of this. */
enum class ShownScreen {
    FULL_SCREEN_VIEWER,
    MANAGE_EXCLUDED,
    SETTINGS,
    FOLDER_DETAIL,
    ALBUM_DETAIL,
    FAVOURITES,
    NEW_ALBUM,
    NEW_AUTOMATIC_ALBUM,
    MAIN_VIEW,
    PERMISSION_DENIED
}

/** Mirrors the order of `MainActivity`'s chain (the viewer first, permission-denied last); keep them in step. */
fun resolveShownScreen(
    isMediaOpen: Boolean,
    isManageExcludedMode: Boolean,
    isSettingsMode: Boolean,
    hasSelectedFolder: Boolean,
    hasSelectedAlbum: Boolean,
    isFavouritesSelected: Boolean,
    isNewAlbumOpen: Boolean,
    isNewAutomaticAlbumOpen: Boolean,
    hasPermission: Boolean
): ShownScreen = when {
    isMediaOpen -> ShownScreen.FULL_SCREEN_VIEWER
    isManageExcludedMode -> ShownScreen.MANAGE_EXCLUDED
    isSettingsMode -> ShownScreen.SETTINGS
    hasSelectedFolder -> ShownScreen.FOLDER_DETAIL
    hasSelectedAlbum -> ShownScreen.ALBUM_DETAIL
    isFavouritesSelected -> ShownScreen.FAVOURITES
    isNewAlbumOpen -> ShownScreen.NEW_ALBUM
    isNewAutomaticAlbumOpen -> ShownScreen.NEW_AUTOMATIC_ALBUM
    hasPermission -> ShownScreen.MAIN_VIEW
    else -> ShownScreen.PERMISSION_DENIED
}

/** ADR-0013 Amendment 2, point 3 and P1. The thumbnail picker is a mode of Folder Detail View, not a screen. */
fun shouldShowMainViewBar(screen: ShownScreen, isThumbnailPickerMode: Boolean): Boolean = when (screen) {
    ShownScreen.MAIN_VIEW,
    ShownScreen.ALBUM_DETAIL,
    ShownScreen.FAVOURITES -> true
    ShownScreen.FOLDER_DETAIL -> !isThumbnailPickerMode
    ShownScreen.FULL_SCREEN_VIEWER,
    ShownScreen.MANAGE_EXCLUDED,
    ShownScreen.SETTINGS,
    ShownScreen.NEW_ALBUM,
    ShownScreen.NEW_AUTOMATIC_ALBUM,
    ShownScreen.PERMISSION_DENIED -> false
}

/**
 * Derived from the screen, not only [displayMode]: after process death `displayMode` resets to Folders
 * while the Favourites selection is restored (ADR-0013 Amendment 2, point 5).
 */
fun selectedMainViewTab(screen: ShownScreen, displayMode: DisplayMode): MainViewTab = when (screen) {
    ShownScreen.FOLDER_DETAIL -> MainViewTab.FOLDERS
    ShownScreen.ALBUM_DETAIL,
    ShownScreen.FAVOURITES -> MainViewTab.ALBUMS
    else -> displayMode.toMainViewTab()
}

fun MainViewTab.toDisplayMode(): DisplayMode = when (this) {
    MainViewTab.FOLDERS -> DisplayMode.FOLDERS
    MainViewTab.ALBUMS -> DisplayMode.ALBUMS
    MainViewTab.TIMELINE -> DisplayMode.TIMELINE
}
