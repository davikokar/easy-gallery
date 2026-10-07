package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.OperationType
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewPreferences
import com.davide.seddio.easygallery.data.ViewType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class AlbumDetailContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val mediaUri = Uri.parse("content://media/external/images/media/1")
    private val mediaItem = MediaItem(
        uri = mediaUri,
        name = "image.jpg",
        dateAdded = 1000L,
        dateModified = 1000L,
        size = 100L,
        type = MediaType.IMAGE,
        bucketName = "Pictures",
        folderPath = "/storage/emulated/0/Pictures"
    )

    @Test
    fun partiallyResolvedAlbumShowsHiddenItemsNoticeWithVisibleMedia() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                storedMemberCount = 2,
                resolvedMemberCount = 1
            )
        }

        composeTestRule.onNodeWithTag("album_hidden_items_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_tile_$mediaUri").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("album_empty_state").assertCountEquals(0)
    }

    @Test
    fun fullyOrphanedAlbumReadsAsEmptyAndWaiting() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = emptyList(),
                storedMemberCount = 2,
                resolvedMemberCount = 0
            )
        }

        composeTestRule.onNodeWithTag("album_hidden_items_notice").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.album_hidden_items_notice))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_empty_state").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.album_empty))
            .assertIsDisplayed()
    }

    @Test
    fun emptyAlbumWithoutStoredMembersDoesNotShowHiddenItemsNotice() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = emptyList(),
                storedMemberCount = 0,
                resolvedMemberCount = 0
            )
        }

        composeTestRule.onAllNodesWithTag("album_hidden_items_notice").assertCountEquals(0)
        composeTestRule.onNodeWithTag("album_empty_state").assertIsDisplayed()
    }

    @Test
    fun backButtonReturnsToAlbums() {
        var backCalls = 0
        composeTestRule.setContent {
            AlbumDetailContentWrapper(onBackToAlbums = { backCalls++ })
        }

        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, backCalls)
        }
    }

    @Test
    fun openingMediaDelegatesTheSelectedResolvedMember() {
        var openedMedia: MediaItem? = null
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                storedMemberCount = 1,
                resolvedMemberCount = 1,
                onSelectMedia = { openedMedia = it }
            )
        }

        composeTestRule.onNodeWithTag("media_tile_$mediaUri").performClick()

        composeTestRule.runOnIdle {
            assertSame(mediaItem, openedMedia)
        }
    }

    @Test
    fun selectionMenuSeparatesRemoveFromAlbumFromPermanentDelete() {
        var removeCalls = 0
        var deleteCalls = 0
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                storedMemberCount = 1,
                resolvedMemberCount = 1,
                isMediaSelectionMode = true,
                selectedMediaItems = setOf(mediaUri),
                selectedMedia = listOf(mediaItem),
                onRemoveSelectedFromAlbum = { removeCalls++ },
                onDeleteSelectedMedia = { deleteCalls++ }
            )
        }

        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_more_options))
            .performClick()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.menu_remove_from_album))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, removeCalls)
            assertEquals(0, deleteCalls)
        }

        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_more_options))
            .performClick()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.action_delete))
            .performClick()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.action_delete))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, removeCalls)
            assertEquals(1, deleteCalls)
        }
    }

    @Composable
    private fun AlbumDetailContentWrapper(
        media: List<MediaItem> = emptyList(),
        storedMemberCount: Int = 0,
        resolvedMemberCount: Int = 0,
        isMediaSelectionMode: Boolean = false,
        selectedMediaItems: Set<Uri> = emptySet(),
        selectedMedia: List<MediaItem> = emptyList(),
        onBackToAlbums: () -> Unit = {},
        onRemoveSelectedFromAlbum: () -> Unit = {},
        onDeleteSelectedMedia: () -> Unit = {},
        onSelectMedia: (MediaItem) -> Unit = {}
    ) {
        AlbumDetailContent(
            albumName = "A deliberately long user supplied album name that must remain usable",
            media = media,
            storedMemberCount = storedMemberCount,
            resolvedMemberCount = resolvedMemberCount,
            preferences = ViewPreferences.defaultFor(PreferenceScope.ALBUM_DETAIL),
            searchQuery = "",
            isSearchActive = false,
            groupedMedia = emptyMap(),
            isMediaSelectionMode = isMediaSelectionMode,
            selectedMediaItems = selectedMediaItems,
            isDestinationPickerActive = false,
            pendingOperation = null,
            browsingPath = "",
            browsingFolders = emptyList<Folder>(),
            selectedMedia = selectedMedia,
            onBackToAlbums = onBackToAlbums,
            onExitMediaSelectionMode = {},
            onRemoveSelectedFromAlbum = onRemoveSelectedFromAlbum,
            onDeleteSelectedMedia = onDeleteSelectedMedia,
            onStartOperation = {},
            onSelectAllMedia = {},
            onSetSearchQuery = {},
            onSetSearchActive = {},
            onSetColumnsCount = {},
            onSetMediaTypes = {},
            onSetSort = { _: SortType, _: SortOrder -> },
            onSetGroupBy = { _: GroupByType, _: SortOrder -> },
            onSetViewType = { _: ViewType -> },
            onToggleInfo = {},
            onUpdateBrowsingPath = {},
            onPerformOperationWithPath = {},
            onCancelOperation = {},
            onSelectMedia = onSelectMedia,
            onEnterMediaSelectionMode = {},
            onDecreaseColumns = {},
            onIncreaseColumns = {}
        )
    }
}
