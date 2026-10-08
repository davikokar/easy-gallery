package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.davide.seddio.easygallery.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class FolderListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun titleSwitcherListsAllThreeViews() {
        composeTestRule.setContent {
            FolderListContentWrapper()
        }

        composeTestRule.onNodeWithContentDescription("Switch view").performClick()

        composeTestRule.onAllNodesWithText("Folders").assertCountEquals(2)
        composeTestRule.onNodeWithText("Timeline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()
    }

    @Test
    fun mainViewSwitcherShowsAppIcon() {
        composeTestRule.setContent {
            FolderListContentWrapper()
        }

        composeTestRule
            .onNodeWithTag("main_view_app_icon", useUnmergedTree = true)
            .assertIsDisplayed()
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
            storedMembershipCount = 3
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
    fun albumTypeDialogOffersAutomaticAlbumsAsNotAvailableYet() {
        composeTestRule.setContent {
            FolderListContentWrapper(displayMode = DisplayMode.ALBUMS)
        }

        composeTestRule.onNodeWithTag("add_album_button").performClick()

        composeTestRule.onNodeWithTag("album_type_automatic").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not available yet").assertIsDisplayed()
    }

    @Test
    fun choosingManualAlbumLeavesTheDialogAndStartsTheNewAlbumFlow() {
        var manualStarts = 0

        composeTestRule.setContent {
            FolderListContentWrapper(
                displayMode = DisplayMode.ALBUMS,
                onStartManualAlbum = { manualStarts++ }
            )
        }

        composeTestRule.onNodeWithTag("add_album_button").performClick()
        composeTestRule.onNodeWithTag("album_type_manual").performClick()

        composeTestRule.onNodeWithTag("album_type_manual").assertDoesNotExist()
        assertEquals(1, manualStarts)
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
                storedMembershipCount = index.toInt()
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
                storedMembershipCount = index.toInt()
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
            storedMembershipCount = 5
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
            storedMembershipCount = 0
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

    @Composable
    private fun FolderListContentWrapper(
        uiState: GalleryUiState = GalleryUiState.Success(emptyList()),
        isSelectionMode: Boolean = false,
        selectedFolders: Set<String> = emptySet(),
        folderViewType: ViewType = ViewType.GRID,
        albumsViewType: ViewType = ViewType.GRID,
        displayMode: DisplayMode = DisplayMode.FOLDERS,
        albums: List<AlbumListItem> = emptyList(),
        favouritesAvailable: Boolean = false,
        isMediaSelectionMode: Boolean = false,
        selectedMediaItems: Set<Uri> = emptySet(),
        groupedAllMedia: Map<String, List<MediaItem>> = emptyMap(),
        onStartManualAlbum: () -> Unit = {}
    ) {
        FolderListContent(
            uiState = uiState,
            folderPreferences = ViewPreferences.defaultFor(PreferenceScope.FOLDERS)
                .copy(viewType = folderViewType),
            timelinePreferences = ViewPreferences.defaultFor(PreferenceScope.TIMELINE)
                .copy(groupBy = GroupByType.NONE),
            albumsPreferences = ViewPreferences.defaultFor(PreferenceScope.ALBUMS)
                .copy(viewType = albumsViewType),
            searchQuery = "",
            isSearchActive = false,
            isSelectionMode = isSelectionMode,
            isMediaSelectionMode = isMediaSelectionMode,
            selectedMediaItems = selectedMediaItems,
            selectedFolders = selectedFolders,
            displayMode = displayMode,
            albums = albums,
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
            onSetDisplayMode = {},
            onBackToFolders = {},
            onSetSort = { _, _ -> },
            onSetGroupByAndOrder = { _, _ -> },
            onSetColumnsCount = {},
            onSetViewType = {},
            onSetSelectedMediaTypes = {},
            onSetShowExcludedTemporarily = {},
            onSetSettingsMode = {},
            onSetCreateFolderDialogOpen = {},
            onStartManualAlbum = onStartManualAlbum,
            onRenameAlbum = { _, _ -> },
            onDeleteAlbum = {},
            onSelectAlbum = {},
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
            calendarContent = {}
        )
    }

    private fun boundsOverlap(first: Rect, second: Rect): Boolean {
        return first.left < second.right &&
            first.right > second.left &&
            first.top < second.bottom &&
            first.bottom > second.top
    }
}
