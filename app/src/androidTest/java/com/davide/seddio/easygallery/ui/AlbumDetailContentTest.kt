package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
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
import org.junit.Assert.assertTrue
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
                hiddenMemberCount = 1
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
                hiddenMemberCount = 2
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
                hiddenMemberCount = 0
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

    @Test
    fun unevaluatableAlbumShowsNewerAppNoticeInsteadOfTheHiddenItemsNotice() {
        val album = ruleAlbum(AlbumEvaluationStatus.UNEVALUATABLE, resolved = 1, stored = 1, hidden = 0)

        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                hiddenMemberCount = album.hiddenMemberCount,
                evaluationStatus = album.status
            )
        }

        composeTestRule.onNodeWithTag("album_rule_unevaluatable_notice").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.album_rule_unevaluatable_notice))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("album_hidden_items_notice").assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(appContext.getString(R.string.album_hidden_items_notice))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("album_rule_waiting_notice").assertCountEquals(0)
        composeTestRule.onNodeWithTag("media_tile_$mediaUri").assertIsDisplayed()
    }

    @Test
    fun waitingAlbumShowsStillScanningNoticePointingToSettingsWithoutAnyFigure() {
        val album = ruleAlbum(AlbumEvaluationStatus.WAITING_ON_INDEX, resolved = 1, stored = 0, hidden = 0)

        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                hiddenMemberCount = album.hiddenMemberCount,
                evaluationStatus = album.status
            )
        }

        composeTestRule.onNodeWithTag("album_rule_waiting_notice").assertIsDisplayed()
        composeTestRule.onNodeWithText(waitingNoticeText()).assertIsDisplayed()
        val shown = noticeText("album_rule_waiting_notice")
        assertTrue(
            "The notice must name the Media index area and Settings: $shown",
            shown.contains(appContext.getString(R.string.settings_media_index)) &&
                shown.contains(appContext.getString(R.string.settings_title))
        )
        assertTrue(
            "The notice must carry no count or percentage: $shown",
            shown.none { it.isDigit() || it == '%' }
        )
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("album_rule_unevaluatable_notice").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("album_hidden_items_notice").assertCountEquals(0)
    }

    @Test
    fun waitingAlbumWithHiddenItemsShowsBothNoticesWithoutOverlap() {
        val album = ruleAlbum(AlbumEvaluationStatus.WAITING_ON_INDEX, resolved = 0, stored = 2, hidden = 2)

        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = emptyList(),
                hiddenMemberCount = album.hiddenMemberCount,
                evaluationStatus = album.status
            )
        }

        composeTestRule.onNodeWithTag("album_rule_waiting_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_hidden_items_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_empty_state").assertIsDisplayed()
        val waiting = composeTestRule.onNodeWithTag("album_rule_waiting_notice")
            .fetchSemanticsNode().boundsInRoot
        val hidden = composeTestRule.onNodeWithTag("album_hidden_items_notice")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The two notices must stack, not overlap", waiting.bottom <= hidden.top)
    }

    @Test
    fun unevaluatableAlbumWithHiddenItemsShowsBothNotices() {
        val album = ruleAlbum(AlbumEvaluationStatus.UNEVALUATABLE, resolved = 1, stored = 2, hidden = 1)

        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                hiddenMemberCount = album.hiddenMemberCount,
                evaluationStatus = album.status
            )
        }

        composeTestRule.onNodeWithTag("album_rule_unevaluatable_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_hidden_items_notice").assertIsDisplayed()
    }

    @Test
    fun completeAlbumWithNoHiddenMembersShowsNoNotices() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                hiddenMemberCount = 0,
                evaluationStatus = AlbumEvaluationStatus.COMPLETE
            )
        }

        composeTestRule.onAllNodesWithTag("album_hidden_items_notice").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("album_rule_unevaluatable_notice").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("album_rule_waiting_notice").assertCountEquals(0)
    }

    @Test
    fun completeAlbumWithHiddenMembersShowsHiddenItemsNotice() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                hiddenMemberCount = 1,
                evaluationStatus = AlbumEvaluationStatus.COMPLETE
            )
        }

        composeTestRule.onNodeWithTag("album_hidden_items_notice").assertIsDisplayed()
    }

    @Test
    fun noticesStayWithinTheScreenAtLargeFontScaleInRightToLeft() {
        val album = ruleAlbum(AlbumEvaluationStatus.WAITING_ON_INDEX, resolved = 0, stored = 2, hidden = 2)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                AlbumDetailContentWrapper(
                    media = emptyList(),
                    hiddenMemberCount = album.hiddenMemberCount,
                    evaluationStatus = album.status
                )
            }
        }

        val root = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("album_rule_waiting_notice", "album_hidden_items_notice").forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag must fit inside the screen: $bounds in $root",
                bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom
            )
        }
    }

    private fun waitingNoticeText(): String = appContext.getString(
        R.string.album_rule_waiting_notice,
        appContext.getString(R.string.settings_media_index),
        appContext.getString(R.string.settings_title)
    )

    private fun noticeText(tag: String): String = composeTestRule
        .onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(" ") { it.text }

    private fun ruleAlbum(
        status: AlbumEvaluationStatus,
        resolved: Int,
        stored: Int,
        hidden: Int
    ) = AlbumListItem(
        id = 1L,
        name = "Rule album",
        createdAt = 1L,
        cover = null,
        isCoverFallback = false,
        resolvedMemberCount = resolved,
        storedMemberCount = stored,
        hiddenMemberCount = hidden,
        storedMembershipCount = stored,
        status = status
    )

    @Composable
    private fun AlbumDetailContentWrapper(
        media: List<MediaItem> = emptyList(),
        hiddenMemberCount: Int = 0,
        evaluationStatus: AlbumEvaluationStatus = AlbumEvaluationStatus.MANUAL,
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
            hiddenMemberCount = hiddenMemberCount,
            evaluationStatus = evaluationStatus,
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
