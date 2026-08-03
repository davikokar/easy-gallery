package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.davide.seddio.easygallery.data.*
import org.junit.Rule
import org.junit.Test

class FolderDetailContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockUri = Uri.parse("content://media/external/images/media/1")
    private val fakeMedia = MediaItem(
        uri = mockUri,
        name = "image.jpg",
        dateAdded = 1000L,
        dateModified = 1000L,
        size = 100L,
        type = MediaType.IMAGE,
        bucketName = "Pictures",
        folderPath = "/storage/emulated/0/Pictures"
    )

    @Test
    fun selectedMediaDisplaysCheckmark() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(mockUri)
            )
        }

        composeTestRule.onNodeWithTag("selected_checkmark", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun mediaSelectionModeShowsDeleteButton() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(mockUri)
            )
        }

        composeTestRule.onNodeWithTag("delete_button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Composable
    private fun FolderDetailContentWrapper(
        media: List<MediaItem> = emptyList(),
        isMediaSelectionMode: Boolean = false,
        selectedMediaItems: Set<Uri> = emptySet()
    ) {
        FolderDetailContent(
            media = media,
            selectedFolder = null,
            columnsCount = 3,
            showInfo = false,
            searchQuery = "",
            isSearchActive = false,
            selectedMediaTypes = MediaType.entries.toSet(),
            pictureSortType = SortType.DATE_TAKEN,
            pictureSortOrder = SortOrder.DESCENDING,
            pictureViewType = ViewType.GRID,
            pictureGroupBy = GroupByType.NONE,
            pictureGroupOrder = SortOrder.DESCENDING,
            groupedMedia = emptyMap(),
            isMediaSelectionMode = isMediaSelectionMode,
            selectedMediaItems = selectedMediaItems,
            isDestinationPickerActive = false,
            pendingOperation = null,
            browsingPath = "",
            browsingFolders = emptyList(),
            onExitMediaSelectionMode = {},
            onDeleteSelectedMedia = {},
            onStartOperation = {},
            onSelectAllMedia = {},
            onSetSearchQuery = {},
            onSetSearchActive = {},
            onSetColumnsCount = { _, _ -> },
            onSetSelectedMediaTypes = {},
            onSetSortType = { _, _ -> },
            onSetSortOrder = { _, _ -> },
            onSetGroupBy = {},
            onSetGroupOrder = {},
            onSetViewType = { _, _ -> },
            onSetShowExcludedTemporarily = {},
            onSetSettingsMode = {},
            onBackToFolders = {},
            onToggleInfo = {},
            onUpdateBrowsingPath = {},
            onPerformOperationWithPath = {},
            onCancelOperation = {},
            getSelectedMediaData = { emptyList() },
            onRotateSelectedMedia = {},
            onSelectMedia = {},
            onEnterMediaSelectionMode = {},
            onDecreaseColumns = {},
            onIncreaseColumns = {}
        )
    }
}
