package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

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

        composeTestRule.onNodeWithContentDescription("More options").performClick()

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

        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithText("Properties").assertIsDisplayed()
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

    @Test
    fun sortDialogShowsApplyOnlyToThisFolderCheckedByDefault() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(media = listOf(fakeMedia))
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Sort by").performClick()

        composeTestRule
            .onNode(
                isToggleable() and hasText("Apply only to this folder"),
                useUnmergedTree = true
            )
            .assertIsOn()
    }

    @Test
    fun sortDialogSelectionCommitsOnlyAfterOk() {
        var committedSort: SortType? = null

        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                onCommitSortPreference = { sortType, _, _ ->
                    committedSort = sortType
                }
            )
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Sort by").performClick()

        composeTestRule.onNodeWithText("Size").performClick()
        composeTestRule.runOnIdle {
            assertNull(committedSort)
        }

        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.runOnIdle {
            assertEquals(SortType.SIZE, committedSort)
        }
    }

    @Test
    fun overflowMenuShowsChangeFolderThumbnailItem() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(media = listOf(fakeMedia))
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()

        composeTestRule
            .onNodeWithText(appContext.getString(R.string.menu_change_folder_thumbnail))
            .assertIsDisplayed()
    }

    @Test
    fun thumbnailPickerModeShowsPickerTopBarAndHidesSearchAndOverflow() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isThumbnailPickerMode = true
            )
        }

        composeTestRule
            .onNodeWithText(appContext.getString(R.string.thumbnail_picker_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_search))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_more_options))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_exit_selection))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_back))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag("selected_checkmark", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun thumbnailPickerModeWithDraftShowsBackAndSelectedCheckmark() {
        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isThumbnailPickerMode = true,
                draftThumbnailUri = mockUri
            )
        }

        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_back))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_exit_selection))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag("selected_checkmark", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun thumbnailPickerBackIconCommitsDraftSelection() {
        var commitCalls = 0
        var exitCalls = 0

        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isThumbnailPickerMode = true,
                draftThumbnailUri = mockUri,
                onCommitThumbnailPickerSelection = { commitCalls++ },
                onExitThumbnailPickerMode = { exitCalls++ }
            )
        }

        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, commitCalls)
            assertEquals(0, exitCalls)
        }
    }

    @Test
    fun thumbnailPickerCloseIconExitsWithoutCommitWhenNoDraft() {
        var commitCalls = 0
        var exitCalls = 0

        composeTestRule.setContent {
            FolderDetailContentWrapper(
                media = listOf(fakeMedia),
                isThumbnailPickerMode = true,
                onCommitThumbnailPickerSelection = { commitCalls++ },
                onExitThumbnailPickerMode = { exitCalls++ }
            )
        }

        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_exit_selection))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(0, commitCalls)
            assertEquals(1, exitCalls)
        }
    }

    @Composable
    private fun FolderDetailContentWrapper(
        media: List<MediaItem> = emptyList(),
        isMediaSelectionMode: Boolean = false,
        selectedMediaItems: Set<Uri> = emptySet(),
        isThumbnailPickerMode: Boolean = false,
        draftThumbnailUri: Uri? = null,
        onEnterThumbnailPickerMode: () -> Unit = {},
        onExitThumbnailPickerMode: () -> Unit = {},
        onCommitThumbnailPickerSelection: () -> Unit = {},
        onCommitSortPreference: (SortType, SortOrder, PreferenceApplyTarget) -> Unit = { _, _, _ -> }
    ) {
        FolderDetailContent(
            media = media,
            selectedFolder = null,
            preferences = ViewPreferences.defaultFor(PreferenceScope.FOLDER_DETAIL),
            searchQuery = "",
            isSearchActive = false,
            groupedMedia = emptyMap(),
            isMediaSelectionMode = isMediaSelectionMode,
            isThumbnailPickerMode = isThumbnailPickerMode,
            draftThumbnailUri = draftThumbnailUri,
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
            onEnterThumbnailPickerMode = onEnterThumbnailPickerMode,
            onExitThumbnailPickerMode = onExitThumbnailPickerMode,
            onCommitThumbnailPickerSelection = onCommitThumbnailPickerSelection,
            onCommitColumnsPreference = { _, _ -> },
            onCommitMediaTypesPreference = { _, _ -> },
            onCommitSortPreference = onCommitSortPreference,
            onCommitGroupByPreference = { _, _, _ -> },
            onCommitViewTypePreference = { _, _ -> },
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
