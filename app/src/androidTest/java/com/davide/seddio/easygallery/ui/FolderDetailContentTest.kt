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
    private val secondMockUri = Uri.parse("content://media/external/images/media/2")
    private val secondFakeMedia = fakeMedia.copy(
        uri = secondMockUri,
        name = "image2.jpg"
    )
    private val videoMockUri = Uri.parse("content://media/external/video/media/1")
    private val fakeVideoMedia = fakeMedia.copy(
        uri = videoMockUri,
        name = "video.mp4",
        type = MediaType.VIDEO
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

    @Test
    fun mediaSelectionModeShowsShareAction() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(mockUri)
            )
        }

        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed()
    }

    @Test
    fun rotateOptionIsMissingFromSelectionMenu() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(mockUri)
            )
        }

        // Open overflow menu
        composeTestRule.onNodeWithContentDescription("More options").performClick()

        // Verify "Rotate" is not present
        composeTestRule.onNodeWithText("Rotate").assertDoesNotExist()
        
        // Verify other options are still there
        composeTestRule.onNodeWithText("Copy to").assertIsDisplayed()
        composeTestRule.onNodeWithText("Move to").assertIsDisplayed()
    }

    @Test
    fun useAsBackgroundShownWhenExactlyOneImageSelected() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(mockUri)
            )
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("Use as background").assertIsDisplayed()
    }

    @Test
    fun useAsBackgroundHiddenWhenTwoItemsSelected() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia, secondFakeMedia),
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(mockUri, secondMockUri)
            )
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("Use as background").assertDoesNotExist()
        composeTestRule.onNodeWithText("Copy to").assertIsDisplayed()
        composeTestRule.onNodeWithText("Move to").assertIsDisplayed()
    }

    @Test
    fun useAsBackgroundHiddenWhenSingleVideoSelected() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeVideoMedia),
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(videoMockUri)
            )
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("Use as background").assertDoesNotExist()
        composeTestRule.onNodeWithText("Copy to").assertIsDisplayed()
        composeTestRule.onNodeWithText("Move to").assertIsDisplayed()
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
            preferences = ViewPreferences.defaultFor(PreferenceScope.FOLDER_DETAIL),
            searchQuery = "",
            isSearchActive = false,
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
            onSetColumnsCount = {},
            onSetSelectedMediaTypes = {},
            onSetSortType = {},
            onSetSortOrder = {},
            onSetGroupBy = {},
            onSetGroupOrder = {},
            onSetViewType = {},
            onSetShowExcludedTemporarily = {},
            onSetSettingsMode = {},
            onBackToFolders = {},
            onToggleInfo = {},
            onUpdateBrowsingPath = {},
            onPerformOperationWithPath = {},
            onCancelOperation = {},
            getSelectedMediaData = {
                media.filter { it.uri in selectedMediaItems }
            },
            onSelectMedia = {},
            onEnterMediaSelectionMode = {},
            onDecreaseColumns = {},
            onIncreaseColumns = {}
        )
    }
}
