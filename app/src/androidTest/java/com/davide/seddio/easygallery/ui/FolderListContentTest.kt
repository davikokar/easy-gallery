package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FolderListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val fakeFolder = Folder(
        name = "Pictures",
        imageCount = 10,
        thumbnailUri = Uri.EMPTY,
        path = "/storage/emulated/0/Pictures",
        isPinned = false
    )
    private val fakeMediaUri = Uri.parse("content://media/external/images/media/1")
    private val fakeMedia = MediaItem(
        uri = fakeMediaUri,
        name = "image.jpg",
        dateAdded = 1000L,
        dateModified = 1000L,
        size = 100L,
        type = MediaType.IMAGE,
        bucketName = "Pictures",
        folderPath = fakeFolder.path
    )

    @Test
    fun pinnedFolderDisplaysPushPinIcon() {
        val pinnedFolder = fakeFolder.copy(isPinned = true)
        
        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(listOf(pinnedFolder))
            )
        }

        composeTestRule.onNodeWithTag("pin_icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun selectedFolderDisplaysCheckmark() {
        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(listOf(fakeFolder)),
                isSelectionMode = true,
                selectedFolders = setOf(fakeFolder.path)
            )
        }

        composeTestRule.onNodeWithTag("selected_checkmark", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun selectionModeShowsDeleteButton() {
        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(listOf(fakeFolder)),
                isSelectionMode = true,
                selectedFolders = setOf(fakeFolder.path)
            )
        }

        composeTestRule.onNodeWithTag("delete_button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun mediaCountTextIsDisplayedCorrectly() {
        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(listOf(fakeFolder))
            )
        }

        composeTestRule.onNodeWithText("10").assertIsDisplayed()
    }

    @Test
    fun scrollPositionIsMaintainedAfterRecomposition() {
        val manyFolders = (1..100).map { 
            fakeFolder.copy(name = "Folder $it", path = "/path/$it")
        }
        
        var isSelectionMode by mutableStateOf(false)
        var selectedFolders by mutableStateOf(emptySet<String>())
        val targetIndex = 50

        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(manyFolders),
                isSelectionMode = isSelectionMode,
                selectedFolders = selectedFolders
            )
        }

        // Scroll to a specific index
        composeTestRule.onNodeWithTag("folder_grid").performScrollToIndex(targetIndex)
        
        // Verify target is displayed
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).assertIsDisplayed()
        
        // Trigger a state change that causes recomposition (selection mode)
        isSelectionMode = true
        selectedFolders = setOf(manyFolders[targetIndex].path)
        
        // Verify we are still at the same item and it's selected
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("selected_checkmark", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun scrollPositionIsMaintainedWhenEnteringSelectionMode() {
        val manyFolders = (1..80).map {
            fakeFolder.copy(name = "Folder $it", path = "/path/$it")
        }

        var isSelectionMode by mutableStateOf(false)
        var selectedFolders by mutableStateOf(emptySet<String>())
        val targetIndex = 60

        composeTestRule.setContent {
            // Use a stable branch as in the fixed MainActivity
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(manyFolders),
                isSelectionMode = isSelectionMode,
                selectedFolders = selectedFolders
            )
        }

        // Scroll to a specific index
        composeTestRule.onNodeWithTag("folder_grid").performScrollToIndex(targetIndex)

        // Verify target is displayed
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).assertIsDisplayed()

        // Long click to enter selection mode
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).performTouchInput {
            longClick()
        }
        
        // Simulate app logic: enter selection mode and select the folder
        isSelectionMode = true
        selectedFolders = setOf(manyFolders[targetIndex].path)

        // Verify we are still at the same item and it's selected
        // In the buggy implementation, this will fail because scroll resets to top (targetIndex 0)
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("selected_checkmark", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun scrollPositionIsMaintainedInListViewWhenEnteringSelectionMode() {
        val manyFolders = (1..80).map {
            fakeFolder.copy(name = "Folder $it", path = "/path/$it")
        }

        var isSelectionMode by mutableStateOf(false)
        var selectedFolders by mutableStateOf(emptySet<String>())
        val targetIndex = 60

        composeTestRule.setContent {
            // Use a stable branch as in the fixed MainActivity
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(manyFolders),
                isSelectionMode = isSelectionMode,
                selectedFolders = selectedFolders,
                folderViewType = ViewType.LIST
            )
        }

        // Scroll to a specific index
        composeTestRule.onNodeWithTag("folder_list").performScrollToIndex(targetIndex)

        // Verify target is displayed
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).assertIsDisplayed()

        // Long click to enter selection mode
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).performTouchInput {
            longClick()
        }

        // Simulate app logic: enter selection mode and select the folder
        isSelectionMode = true
        selectedFolders = setOf(manyFolders[targetIndex].path)

        // Verify we are still at the same item and it's selected
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("selected_checkmark", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun sortDialogDoesNotShowApplyOnlyToThisFolderOption() {
        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(listOf(fakeFolder))
            )
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Sort by").performClick()

        composeTestRule.onNodeWithText("Apply only to this folder").assertDoesNotExist()
    }

    @Test
    fun mainViewTitleShowsAppIconAndAppNameOnEveryMainView() {
        var displayMode by mutableStateOf(DisplayMode.FOLDERS)

        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = displayMode)
        }

        DisplayMode.entries.forEach { mode ->
            displayMode = mode
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("main_view_app_icon", useUnmergedTree = true)
                .assertIsDisplayed()
            composeTestRule
                .onNodeWithText(appContext.getString(R.string.app_name))
                .assertIsDisplayed()
        }
    }

    @Test
    fun mainViewTitleIsEasyGalleryAndNotTheViewName() {
        var displayMode by mutableStateOf(DisplayMode.TIMELINE)

        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = displayMode)
        }

        composeTestRule.onNodeWithText("Easy Gallery").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.timeline_title)).assertCountEquals(0)
        displayMode = DisplayMode.ALBUMS
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.albums_title)).assertCountEquals(0)
    }

    @Test
    fun noSwitchViewControlExistsInTheTopBar() {
        composeTestRule.setContent {
            FolderListContentWrapper()
        }

        composeTestRule.onAllNodesWithContentDescription("Switch view").assertCountEquals(0)
        composeTestRule.onNodeWithText("Easy Gallery").performClick()
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.albums_title)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.timeline_title)).assertCountEquals(0)
    }

    @Test
    fun foldersAndAlbumsViewsKeepTheirOwnScrollPositions() {
        val manyFolders = (1..100).map { fakeFolder.copy(name = "Folder $it", path = "/path/$it") }
        // Zero-padded so the default Name sort keeps list index == id - 1.
        val manyAlbums = (1L..60L).map { albumItem(AlbumEvaluationStatus.MANUAL, id = it, name = "Album ${it.toString().padStart(2, '0')}") }
        var displayMode by mutableStateOf(DisplayMode.FOLDERS)
        val folderIndex = 60
        val albumIndex = 40

        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(manyFolders),
                displayMode = displayMode,
                albums = manyAlbums
            )
        }

        composeTestRule.onNodeWithTag("folder_grid").performScrollToIndex(folderIndex)
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[folderIndex].path}", useUnmergedTree = true)
            .assertIsDisplayed()

        displayMode = DisplayMode.ALBUMS
        composeTestRule.waitForIdle()
        // Albums starts at its own top, not at Folders' position.
        composeTestRule.onNodeWithTag("album_tile_${manyAlbums.first().id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_grid").performScrollToIndex(albumIndex)
        composeTestRule.onNodeWithTag("album_tile_${manyAlbums[albumIndex].id}").assertIsDisplayed()

        displayMode = DisplayMode.FOLDERS
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[folderIndex].path}", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_tile_${manyFolders.first().path}", useUnmergedTree = true)
            .assertDoesNotExist()

        displayMode = DisplayMode.ALBUMS
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_tile_${manyAlbums[albumIndex].id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_tile_${manyAlbums.first().id}").assertDoesNotExist()
    }

    @Test
    fun foldersViewKeepsItsListScrollPositionAcrossAViewSwitch() {
        val manyFolders = (1..80).map { fakeFolder.copy(name = "Folder $it", path = "/path/$it") }
        var displayMode by mutableStateOf(DisplayMode.FOLDERS)
        val targetIndex = 60

        composeTestRule.setContent {
            FolderListContentWrapper(
                uiState = GalleryUiState.Success(manyFolders),
                displayMode = displayMode,
                folderViewType = ViewType.LIST
            )
        }

        composeTestRule.onNodeWithTag("folder_list").performScrollToIndex(targetIndex)
        displayMode = DisplayMode.TIMELINE
        composeTestRule.waitForIdle()
        displayMode = DisplayMode.FOLDERS
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("folder_tile_${manyFolders[targetIndex].path}", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun timelineViewKeepsItsScrollPositionAfterLeavingAndReturning() {
        var displayMode by mutableStateOf(DisplayMode.TIMELINE)
        val targetIndex = 70

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = displayMode,
                calendarContent = { TimelineStandIn() }
            )
        }

        composeTestRule.onNodeWithTag("timeline_stand_in").performScrollToIndex(targetIndex)
        composeTestRule.onNodeWithText("Timeline item $targetIndex").assertIsDisplayed()

        displayMode = DisplayMode.FOLDERS
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("timeline_stand_in").assertCountEquals(0)
        displayMode = DisplayMode.TIMELINE
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Timeline item $targetIndex").assertIsDisplayed()
        composeTestRule.onNodeWithText("Timeline item 0").assertDoesNotExist()
    }

    @Test
    fun albumSelectionTopBarIsAbsentWhileNoAlbumIsSelected() {
        val album = albumItem(AlbumEvaluationStatus.MANUAL, id = 41L, name = "Trips")

        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS, albums = listOf(album))
        }

        composeTestRule.onNodeWithTag("album_tile_41").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(appContext.getString(R.string.album_rename_title))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription(appContext.getString(R.string.album_delete_title))
            .assertCountEquals(0)
    }

    @Test
    fun albumSelectionTopBarAppearsWhileAnAlbumIsSelectedAndClosesOnExit() {
        val album = albumItem(AlbumEvaluationStatus.MANUAL, id = 41L, name = "Trips")
        val renameDescription = appContext.getString(R.string.album_rename_title)

        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS, albums = listOf(album))
        }

        composeTestRule.onNodeWithTag("album_tile_41").performTouchInput { longClick() }
        composeTestRule.onNodeWithContentDescription(renameDescription).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(appContext.getString(R.string.album_delete_title))
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(appContext.getString(R.string.cd_exit_selection))
            .performClick()

        composeTestRule.onAllNodesWithContentDescription(renameDescription).assertCountEquals(0)
    }

    @Test
    fun albumSelectionTopBarIsShownFromTheAlbumSelectionIdParameter() {
        val album = albumItem(AlbumEvaluationStatus.MANUAL, id = 41L, name = "Trips")

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                initialAlbumSelectionId = 41L
            )
        }

        composeTestRule.onNodeWithContentDescription(appContext.getString(R.string.album_rename_title))
            .assertIsDisplayed()
    }

    @Test
    fun albumSelectionIdWithoutAMatchingAlbumShowsNoSelectionTopBar() {
        val album = albumItem(AlbumEvaluationStatus.MANUAL, id = 41L, name = "Trips")

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                initialAlbumSelectionId = 99L
            )
        }

        composeTestRule.onAllNodesWithContentDescription(appContext.getString(R.string.album_rename_title))
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag("add_album_button").assertIsDisplayed()
    }

    @Test
    fun addAlbumButtonIsHiddenWhileAnAlbumIsSelected() {
        val album = albumItem(AlbumEvaluationStatus.MANUAL, id = 41L, name = "Trips")

        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS, albums = listOf(album))
        }
        composeTestRule.onNodeWithTag("add_album_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("album_tile_41").performTouchInput { longClick() }
        composeTestRule.onNodeWithTag("add_album_button").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(appContext.getString(R.string.cd_exit_selection))
            .performClick()
        composeTestRule.onNodeWithTag("add_album_button").assertIsDisplayed()
    }

    @Test
    fun renameActionLeavesAlbumSelectionMode() {
        val album = albumItem(AlbumEvaluationStatus.MANUAL, id = 41L, name = "Trips")
        val exitDescription = appContext.getString(R.string.cd_exit_selection)

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                initialAlbumSelectionId = 41L
            )
        }
        composeTestRule.onNodeWithContentDescription(exitDescription).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(appContext.getString(R.string.album_rename_title))
            .performClick()

        composeTestRule.onAllNodesWithContentDescription(exitDescription).assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription(appContext.getString(R.string.album_delete_title))
            .assertCountEquals(0)
    }

    @Test
    fun deleteAlbumActionOpensConfirmationAndLeavesAlbumSelectionMode() {
        val album = albumItem(AlbumEvaluationStatus.MANUAL, id = 41L, name = "Trips")

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                initialAlbumSelectionId = 41L
            )
        }

        composeTestRule.onNodeWithContentDescription(appContext.getString(R.string.album_delete_title))
            .performClick()

        composeTestRule.onNodeWithText(appContext.getString(R.string.album_delete_message)).assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(appContext.getString(R.string.cd_exit_selection))
            .assertCountEquals(0)
    }

    @Test
    fun albumsViewShowsFavouritesAndManualAlbums() {
        val album = AlbumListItem(
            id = 7L,
            name = "Trips",
            createdAt = 1L,
            cover = null,
            isCoverFallback = false,
            resolvedMemberCount = 3,
            storedMemberCount = 3,
            hiddenMemberCount = 0,
            storedMembershipCount = 3,
            status = AlbumEvaluationStatus.MANUAL
        )

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                favouritesAvailable = true
            )
        }

        composeTestRule.onNodeWithTag("album_tile_favourites").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_tile_7").assertIsDisplayed()
    }

    @Test
    fun albumsOverflowShowsNewAlbumAndHidesMediaOnlyActions() {
        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS)
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("New album").assertIsDisplayed()
        composeTestRule.onNodeWithText("Temporarily show excluded").assertDoesNotExist()
        composeTestRule.onNodeWithText("Filter media").assertDoesNotExist()
    }

    @Test
    fun addAlbumButtonIsShownOnlyInAlbumsView() {
        var displayMode by mutableStateOf(DisplayMode.ALBUMS)

        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = displayMode)
        }

        composeTestRule.onNodeWithTag("add_album_button").assertIsDisplayed()

        displayMode = DisplayMode.FOLDERS
        composeTestRule.onNodeWithTag("add_album_button").assertDoesNotExist()

        displayMode = DisplayMode.TIMELINE
        composeTestRule.onNodeWithTag("add_album_button").assertDoesNotExist()
    }

    @Test
    fun addAlbumButtonOpensSameAlbumTypeDialogAsOverflowAction() {
        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS)
        }

        composeTestRule.onNodeWithTag("add_album_button").performClick()
        composeTestRule.onNodeWithTag("album_type_manual").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("New album").performClick()
        composeTestRule.onNodeWithTag("album_type_manual").assertIsDisplayed()
    }

    @Test
    fun albumTypeDialogOffersAutomaticAlbumsAsAnEnabledChoice() {
        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS)
        }

        composeTestRule.onNodeWithTag("add_album_button").performClick()

        composeTestRule.onNodeWithTag("album_type_automatic")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        composeTestRule.onNodeWithTag("album_type_manual")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun albumTypeDialogDoesNotSayAutomaticIsNotAvailable() {
        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS)
        }

        composeTestRule.onNodeWithTag("add_album_button").performClick()

        composeTestRule.onAllNodesWithText("Not available yet").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("available", substring = true, ignoreCase = true)
            .assertCountEquals(0)
    }

    @Test
    fun choosingAutomaticAlbumLeavesTheDialogAndStartsTheNewAutomaticAlbumFlow() {
        var automaticStarts = 0
        var manualStarts = 0

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                onStartManualAlbum = { manualStarts++ },
                onStartAutomaticAlbum = { automaticStarts++ }
            )
        }

        composeTestRule.onNodeWithTag("add_album_button").performClick()
        composeTestRule.onNodeWithTag("album_type_automatic").performClick()

        composeTestRule.onNodeWithTag("album_type_automatic").assertDoesNotExist()
        assertEquals(1, automaticStarts)
        assertEquals(0, manualStarts)
    }

    @Test
    fun choosingManualAlbumLeavesTheDialogAndStartsTheNewAlbumFlow() {
        var manualStarts = 0
        var automaticStarts = 0

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                onStartManualAlbum = { manualStarts++ },
                onStartAutomaticAlbum = { automaticStarts++ }
            )
        }

        composeTestRule.onNodeWithTag("add_album_button").performClick()
        composeTestRule.onNodeWithTag("album_type_manual").performClick()

        composeTestRule.onNodeWithTag("album_type_manual").assertDoesNotExist()
        assertEquals(1, manualStarts)
        assertEquals(0, automaticStarts)
    }

    @Test
    fun albumsGridLastAlbumDoesNotOverlapAddAlbumButtonAtBottom() {
        val albums = (1L..40L).map { index ->
            AlbumListItem(
                id = index,
                name = "Album $index",
                createdAt = index,
                cover = null,
                isCoverFallback = false,
                resolvedMemberCount = index.toInt(),
                storedMemberCount = index.toInt(),
                hiddenMemberCount = 0,
                storedMembershipCount = index.toInt(),
                status = AlbumEvaluationStatus.MANUAL
            )
        }

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = albums,
                albumsViewType = ViewType.GRID
            )
        }

        composeTestRule.onNodeWithTag("album_grid").performScrollToIndex(albums.lastIndex)

        val lastAlbumBounds = composeTestRule
            .onNodeWithTag("album_tile_${albums.last().id}")
            .fetchSemanticsNode()
            .boundsInRoot
        val addAlbumButtonBounds = composeTestRule
            .onNodeWithTag("add_album_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertFalse(
            "Expected the last album tile to remain uncovered by the add album button in grid layout",
            boundsOverlap(lastAlbumBounds, addAlbumButtonBounds)
        )
    }

    @Test
    fun albumsListLastAlbumDoesNotOverlapAddAlbumButtonAtBottom() {
        val albums = (1L..40L).map { index ->
            AlbumListItem(
                id = index,
                name = "Album $index",
                createdAt = index,
                cover = null,
                isCoverFallback = false,
                resolvedMemberCount = index.toInt(),
                storedMemberCount = index.toInt(),
                hiddenMemberCount = 0,
                storedMembershipCount = index.toInt(),
                status = AlbumEvaluationStatus.MANUAL
            )
        }

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = albums,
                albumsViewType = ViewType.LIST
            )
        }

        composeTestRule.onNodeWithTag("album_list").performScrollToIndex(albums.lastIndex)

        val lastAlbumBounds = composeTestRule
            .onNodeWithTag("album_tile_${albums.last().id}")
            .fetchSemanticsNode()
            .boundsInRoot
        val addAlbumButtonBounds = composeTestRule
            .onNodeWithTag("add_album_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertFalse(
            "Expected the last album row to remain uncovered by the add album button in list layout",
            boundsOverlap(lastAlbumBounds, addAlbumButtonBounds)
        )
    }

    @Test
    fun timelineSelectionMenuShowsFavouriteAndAddToAlbumActions() {
        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.TIMELINE,
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(fakeMediaUri),
                groupedAllMedia = mapOf("" to listOf(fakeMedia)),
                favouritesAvailable = true
            )
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("Add to Favourites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to album").assertIsDisplayed()
    }

    @Test
    fun albumsViewShowsHiddenMembersNoticeForFullyOrphanedAlbumOnly() {
        val waitingAlbum = AlbumListItem(
            id = 9L,
            name = "Waiting",
            createdAt = 1L,
            cover = null,
            isCoverFallback = true,
            resolvedMemberCount = 0,
            storedMemberCount = 5,
            hiddenMemberCount = 5,
            storedMembershipCount = 5,
            status = AlbumEvaluationStatus.MANUAL
        )
        val emptyAlbum = AlbumListItem(
            id = 10L,
            name = "Empty",
            createdAt = 2L,
            cover = null,
            isCoverFallback = false,
            resolvedMemberCount = 0,
            storedMemberCount = 0,
            hiddenMemberCount = 0,
            storedMembershipCount = 0,
            status = AlbumEvaluationStatus.MANUAL
        )

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(waitingAlbum, emptyAlbum)
            )
        }

        composeTestRule.onAllNodesWithTag("album_hidden_items_notice", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun albumsViewShowsNewerAppNoticeOnTileAndRow() {
        val album = albumItem(AlbumEvaluationStatus.UNEVALUATABLE)
        var albumsViewType by mutableStateOf(ViewType.GRID)

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                albumsViewType = albumsViewType
            )
        }

        listOf(ViewType.GRID, ViewType.LIST).forEach { viewType ->
            albumsViewType = viewType
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("album_rule_unevaluatable_notice", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertTextEquals(appContext.getString(R.string.album_rule_unevaluatable_notice_short))
            composeTestRule.onAllNodesWithTag("album_hidden_items_notice", useUnmergedTree = true)
                .assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("album_rule_waiting_notice", useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

    @Test
    fun albumsViewShowsStillScanningNoticeWithoutAnyFigureOnTileAndRow() {
        val album = albumItem(AlbumEvaluationStatus.WAITING_ON_INDEX)
        var albumsViewType by mutableStateOf(ViewType.GRID)

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                albumsViewType = albumsViewType
            )
        }

        listOf(ViewType.GRID, ViewType.LIST).forEach { viewType ->
            albumsViewType = viewType
            composeTestRule.waitForIdle()

            val notice = composeTestRule
                .onNodeWithTag("album_rule_waiting_notice", useUnmergedTree = true)
                .assertIsDisplayed()
            val expected = appContext.getString(
                R.string.album_rule_waiting_notice_short,
                appContext.getString(R.string.settings_title)
            )
            notice.assertTextEquals(expected)
            val shown = notice.fetchSemanticsNode().config[SemanticsProperties.Text]
                .joinToString(" ") { it.text }
            assertTrue(
                "The notice must carry no count or percentage: $shown",
                shown.none { it.isDigit() || it == '%' }
            )
            composeTestRule
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                    useUnmergedTree = true
                )
                .assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("album_rule_unevaluatable_notice", useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

    @Test
    fun albumsViewShowsStatusNoticeAndHiddenItemsNoticeTogether() {
        val album = albumItem(
            status = AlbumEvaluationStatus.WAITING_ON_INDEX,
            resolved = 0,
            stored = 3,
            hidden = 3
        )
        var albumsViewType by mutableStateOf(ViewType.GRID)

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                albumsViewType = albumsViewType
            )
        }

        listOf(ViewType.GRID, ViewType.LIST).forEach { viewType ->
            albumsViewType = viewType
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("album_rule_waiting_notice", useUnmergedTree = true)
                .assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("album_hidden_items_notice", useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun albumsViewShowsNoNoticeForManualOrCompleteAlbumsWhoseAdditionsAllResolve() {
        // More live items than stored additions: a stored-versus-resolved comparison misreads this.
        val manual = albumItem(AlbumEvaluationStatus.MANUAL, id = 21L, resolved = 2, stored = 2)
        val complete = albumItem(AlbumEvaluationStatus.COMPLETE, id = 22L, resolved = 5, stored = 1)

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(manual, complete)
            )
        }

        composeTestRule.onNodeWithTag("album_tile_21").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_tile_22").assertIsDisplayed()
        listOf(
            "album_hidden_items_notice",
            "album_rule_unevaluatable_notice",
            "album_rule_waiting_notice"
        ).forEach { tag ->
            composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun albumTileAnnouncesItsNoticeAsStateAndKeepsTheNameAsDescription() {
        val album = albumItem(AlbumEvaluationStatus.UNEVALUATABLE, name = "Castles")

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album)
            )
        }

        val config = composeTestRule.onNodeWithTag("album_tile_${album.id}")
            .fetchSemanticsNode().config
        assertEquals(
            appContext.getString(R.string.album_rule_unevaluatable_notice_short),
            config[SemanticsProperties.StateDescription]
        )
        assertTrue(config[SemanticsProperties.ContentDescription].contains("Castles"))
    }

    @Test
    fun albumRowKeepsItsNoticeFullyVisibleAtLargeFontScaleInRightToLeft() {
        val album = albumItem(AlbumEvaluationStatus.WAITING_ON_INDEX)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                FolderListContentWrapper(
                    displayMode = DisplayMode.ALBUMS,
                    albums = listOf(album),
                    albumsViewType = ViewType.LIST
                )
            }
        }

        val row = composeTestRule.onNodeWithTag("album_tile_${album.id}")
            .fetchSemanticsNode().boundsInRoot
        val notice = composeTestRule
            .onNodeWithTag("album_rule_waiting_notice", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The notice must sit inside its row: $notice in $row",
            notice.left >= row.left && notice.right <= row.right &&
                notice.top >= row.top && notice.bottom <= row.bottom
        )
    }

    @Test
    fun albumTilesAndRowsNeverShowCoverageNotices() {
        var albumsViewType by mutableStateOf(ViewType.GRID)
        val album = albumItem(AlbumEvaluationStatus.COMPLETE).copy(
            hasTimeClause = true,
            hasRegionClause = true,
            coverage = MediaIndexCoverage(withoutLocation = 12, withoutDate = 34),
            locationAccessGranted = false
        )

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                albums = listOf(album),
                albumsViewType = albumsViewType
            )
        }

        composeTestRule.onNodeWithTag("album_tile_${album.id}").assertIsDisplayed()
        assertNoCoverageNotices()

        albumsViewType = ViewType.LIST
        composeTestRule.onNodeWithTag("album_tile_${album.id}").assertIsDisplayed()
        assertNoCoverageNotices()
    }

    private fun assertNoCoverageNotices() {
        composeTestRule.onAllNodesWithTag("album_location_coverage_notice").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("album_date_coverage_notice").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("no usable location", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("no capture date", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Location access is off", substring = true).assertCountEquals(0)
    }

    private fun albumItem(
        status: AlbumEvaluationStatus,
        id: Long = 31L,
        name: String = "Rule album",
        resolved: Int = 2,
        stored: Int = 0,
        hidden: Int = 0
    ) = AlbumListItem(
        id = id,
        name = name,
        createdAt = id,
        cover = null,
        isCoverFallback = false,
        resolvedMemberCount = resolved,
        storedMemberCount = stored,
        hiddenMemberCount = hidden,
        storedMembershipCount = stored,
        status = status
    )

    @Composable
    private fun TimelineStandIn() {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.testTag("timeline_stand_in")
        ) {
            items((0 until 100).toList()) { index ->
                Text("Timeline item $index", modifier = Modifier.height(48.dp))
            }
        }
    }

    @Composable
    private fun FolderListContentWrapper(
        uiState: GalleryUiState = GalleryUiState.Success(emptyList()),
        isSelectionMode: Boolean = false,
        isSearchActive: Boolean = false,
        selectedFolders: Set<String> = emptySet(),
        folderViewType: ViewType = ViewType.GRID,
        albumsViewType: ViewType = ViewType.GRID,
        displayMode: DisplayMode = DisplayMode.FOLDERS,
        albums: List<AlbumListItem> = emptyList(),
        initialAlbumSelectionId: Long? = null,
        favouritesAvailable: Boolean = false,
        isMediaSelectionMode: Boolean = false,
        selectedMediaItems: Set<Uri> = emptySet(),
        groupedAllMedia: Map<String, List<MediaItem>> = emptyMap(),
        onStartManualAlbum: () -> Unit = {},
        onStartAutomaticAlbum: () -> Unit = {},
        calendarContent: @Composable () -> Unit = {}
    ) {
        // Held here, as the ViewModel holds it in the app.
        var albumSelectionId by remember { mutableStateOf(initialAlbumSelectionId) }

        FolderListContent(
            uiState = uiState,
            folderPreferences = ViewPreferences.defaultFor(PreferenceScope.FOLDERS)
                .copy(viewType = folderViewType),
            timelinePreferences = ViewPreferences.defaultFor(PreferenceScope.TIMELINE)
                .copy(groupBy = GroupByType.NONE),
            albumsPreferences = ViewPreferences.defaultFor(PreferenceScope.ALBUMS)
                .copy(viewType = albumsViewType),
            searchQuery = "",
            isSearchActive = isSearchActive,
            isSelectionMode = isSelectionMode,
            isMediaSelectionMode = isMediaSelectionMode,
            selectedMediaItems = selectedMediaItems,
            selectedFolders = selectedFolders,
            displayMode = displayMode,
            albums = albums,
            albumSelectionId = albumSelectionId,
            favouritesAvailable = favouritesAvailable,
            favouriteMedia = emptyList(),
            favouriteUris = emptySet(),
            isFavouritePending = false,
            groupedAllMedia = groupedAllMedia,
            isDestinationPickerActive = false,
            isCreateFolderDialogOpen = false,
            createFolderError = null,
            createFolderBrowsingPath = "",
            createFolderBrowsingFolders = emptyList(),
            pendingOperation = null,
            browsingPath = "",
            browsingFolders = emptyList(),
            onExitMediaSelectionMode = {},
            onExitSelectionMode = {},
            onDeleteSelectedMedia = {},
            onDeleteSelectedFolders = {},
            onPinSelected = {},
            onSelectAllMedia = {},
            onToggleFavourites = {},
            onAddToAlbum = {},
            onSelectAllFolders = {},
            onExcludeSelected = {},
            onStartOperation = {},
            onSetSearchQuery = {},
            onSetSearchActive = {},
            onBackToFolders = {},
            onSetSort = { _, _ -> },
            onSetGroupByAndOrder = { _, _ -> },
            onSetColumnsCount = {},
            onSetViewType = {},
            onSetSelectedMediaTypes = {},
            onSetShowExcludedTemporarily = {},
            onSetCreateFolderDialogOpen = {},
            onStartManualAlbum = onStartManualAlbum,
            onStartAutomaticAlbum = onStartAutomaticAlbum,
            onRenameAlbum = { _, _ -> },
            onDeleteAlbum = {},
            onSelectAlbum = {},
            onEnterAlbumSelection = { albumSelectionId = it },
            onExitAlbumSelection = { albumSelectionId = null },
            onSelectFavourites = {},
            onCreateFolder = {},
            onUpdateCreateFolderBrowsingPath = {},
            onUpdateBrowsingPath = {},
            onPerformOperationWithPath = {},
            onCancelOperation = {},
            onSelectFolder = {},
            onEnterSelectionMode = {},
            onDecreaseColumns = {},
            onIncreaseColumns = {},
            getSelectedMediaData = {
                groupedAllMedia.values.flatten().filter { it.uri in selectedMediaItems }
            },
            getSelectedFoldersData = { emptyList() },
            onSelectMedia = {},
            onEnterMediaSelectionMode = {},
            calendarContent = calendarContent
        )
    }

    private fun boundsOverlap(first: Rect, second: Rect): Boolean {
        return first.left < second.right &&
            first.right > second.left &&
            first.top < second.bottom &&
            first.bottom > second.top
    }
}
