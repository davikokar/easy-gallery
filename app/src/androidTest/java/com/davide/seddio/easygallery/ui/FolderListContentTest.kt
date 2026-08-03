package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.davide.seddio.easygallery.data.*
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

    @Composable
    private fun FolderListContentWrapper(
        uiState: GalleryUiState = GalleryUiState.Success(emptyList()),
        isSelectionMode: Boolean = false,
        selectedFolders: Set<String> = emptySet()
    ) {
        FolderListContent(
            uiState = uiState,
            folderColumns = 2,
            pictureColumns = 3,
            searchQuery = "",
            isSearchActive = false,
            isSelectionMode = isSelectionMode,
            isMediaSelectionMode = false,
            selectedMediaItems = emptySet(),
            selectedFolders = selectedFolders,
            displayMode = DisplayMode.GALLERY,
            groupedAllMedia = emptyMap(),
            showInfo = false,
            folderSortType = SortType.NAME,
            pictureSortType = SortType.DATE_TAKEN,
            folderSortOrder = SortOrder.ASCENDING,
            pictureSortOrder = SortOrder.DESCENDING,
            pictureGroupBy = GroupByType.NONE,
            pictureGroupOrder = SortOrder.DESCENDING,
            folderViewType = ViewType.GRID,
            pictureViewType = ViewType.GRID,
            selectedMediaTypes = MediaType.entries.toSet(),
            isDestinationPickerActive = false,
            pendingOperation = null,
            browsingPath = "",
            browsingFolders = emptyList(),
            onExitMediaSelectionMode = {},
            onExitSelectionMode = {},
            onDeleteSelectedMedia = {},
            onDeleteSelectedFolders = {},
            onPinSelected = {},
            onSelectAllMedia = {},
            onSelectAllFolders = {},
            onExcludeSelected = {},
            onStartOperation = {},
            onSetSearchQuery = {},
            onSetSearchActive = {},
            onToggleDisplayMode = {},
            onSetSortType = { _, _ -> },
            onSetSortOrder = { _, _ -> },
            onSetGroupBy = {},
            onSetGroupOrder = {},
            onSetColumnsCount = { _, _ -> },
            onSetViewType = { _, _ -> },
            onSetSelectedMediaTypes = {},
            onSetShowExcludedTemporarily = {},
            onSetSettingsMode = {},
            onUpdateBrowsingPath = {},
            onPerformOperationWithPath = {},
            onCancelOperation = {},
            onSelectFolder = {},
            onEnterSelectionMode = {},
            onDecreaseColumns = {},
            onIncreaseColumns = {},
            onToggleInfo = {},
            getSelectedMediaData = { emptyList() },
            getSelectedFoldersData = { emptyList() },
            onRotateSelectedMedia = {},
            onSelectMedia = {},
            onEnterMediaSelectionMode = {},
            calendarContent = {}
        )
    }
}
