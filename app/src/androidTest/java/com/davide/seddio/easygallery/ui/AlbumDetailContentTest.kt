package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
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
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.OperationType
import com.davide.seddio.easygallery.data.PreferenceApplyTarget
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.ViewPreferences
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
        composeTestRule.onNodeWithTag("media_tile_$mediaUri").assertIsDisplayed()
    }

    @Test
    fun waitingAlbumWithHiddenItemsShowsOnlyTheHiddenItemsNotice() {
        val album = ruleAlbum(AlbumEvaluationStatus.WAITING_ON_INDEX, resolved = 0, stored = 2, hidden = 2)

        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = emptyList(),
                hiddenMemberCount = album.hiddenMemberCount,
                evaluationStatus = album.status
            )
        }

        composeTestRule.onNodeWithTag("album_hidden_items_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_empty_state").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("album_rule_waiting_notice").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("album_rule_unevaluatable_notice").assertCountEquals(0)
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
        val album = ruleAlbum(AlbumEvaluationStatus.UNEVALUATABLE, resolved = 0, stored = 2, hidden = 2)

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
        listOf("album_rule_unevaluatable_notice", "album_hidden_items_notice").forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag must fit inside the screen: $bounds in $root",
                bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom
            )
        }
    }

    @Test
    fun noticeBandsAreAnnouncedToScreenReaders() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                hiddenMemberCount = 1,
                evaluationStatus = AlbumEvaluationStatus.UNEVALUATABLE
            )
        }

        listOf("album_rule_unevaluatable_notice", "album_hidden_items_notice").forEach { tag ->
            val config = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertEquals(LiveRegionMode.Polite, config.getOrNull(SemanticsProperties.LiveRegion))
        }
    }

    @Test
    fun everyPreferenceDialogShowsApplyOnlyToThisAlbumCheckedByDefault() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(media = listOf(mediaItem))
        }

        assertEveryDialogOffersApplyOnlyToThisAlbumChecked()
    }

    @Test
    fun favouritesPreferenceDialogsShowApplyOnlyToThisAlbumCheckedByDefault() {
        composeTestRule.setContent {
            AlbumDetailContentWrapper(media = listOf(mediaItem), isFavouritesAlbum = true)
        }

        assertEveryDialogOffersApplyOnlyToThisAlbumChecked()
    }

    @Test
    fun confirmingWithApplyOnlyToThisAlbumCheckedReportsThisAlbumForEveryDialog() {
        val committed = mutableListOf<Pair<String, PreferenceApplyTarget>>()
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                onCommitPreference = { name, target -> committed += name to target }
            )
        }

        assertEveryDialogCommitsThisAlbum(committed)
    }

    @Test
    fun favouritesConfirmingWithApplyOnlyToThisAlbumCheckedReportsThisAlbumForEveryDialog() {
        val committed = mutableListOf<Pair<String, PreferenceApplyTarget>>()
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                isFavouritesAlbum = true,
                onCommitPreference = { name, target -> committed += name to target }
            )
        }

        assertEveryDialogCommitsThisAlbum(committed)
    }

    @Test
    fun uncheckingApplyOnlyToThisAlbumAsksForConfirmationThenReportsAllAlbumsForEveryDialog() {
        val committed = mutableListOf<Pair<String, PreferenceApplyTarget>>()
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                onCommitPreference = { name, target -> committed += name to target }
            )
        }

        assertEveryDialogCommitsAllAlbumsAfterConfirmation(committed)
    }

    @Test
    fun favouritesUncheckingApplyOnlyToThisAlbumAsksForConfirmationThenReportsAllAlbumsForEveryDialog() {
        val committed = mutableListOf<Pair<String, PreferenceApplyTarget>>()
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                isFavouritesAlbum = true,
                onCommitPreference = { name, target -> committed += name to target }
            )
        }

        assertEveryDialogCommitsAllAlbumsAfterConfirmation(committed)
    }

    @Test
    fun cancellingTheAlbumConfirmationReportsNothing() {
        val committed = mutableListOf<Pair<String, PreferenceApplyTarget>>()
        composeTestRule.setContent {
            AlbumDetailContentWrapper(
                media = listOf(mediaItem),
                onCommitPreference = { name, target -> committed += name to target }
            )
        }
        val confirmationTitle = appContext.getString(R.string.apply_to_all_albums_title)

        openPreferenceDialog(R.string.sort_by_title)
        composeTestRule.onNode(applyOnlyToThisAlbumToggle()).performClick()
        composeTestRule.onNodeWithText(appContext.getString(R.string.action_ok)).performClick()
        composeTestRule.onNodeWithText(confirmationTitle).assertIsDisplayed()
        composeTestRule
            .onNode(
                hasText(appContext.getString(R.string.action_cancel)) and
                    hasAnyAncestor(hasAnyDescendant(hasText(confirmationTitle)))
            )
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(committed.isEmpty())
        }
    }

    // Menu label of each dialog, paired with the name the wrapper reports its commit under.
    private val preferenceDialogs = listOf(
        R.string.column_count_title to "columns",
        R.string.filter_media_title to "mediaTypes",
        R.string.sort_by_title to "sort",
        R.string.group_by_title to "groupBy",
        R.string.change_view_type_title to "viewType"
    )

    private fun openPreferenceDialog(menuLabel: Int) {
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.cd_more_options))
            .performClick()
        composeTestRule.onNodeWithText(appContext.getString(menuLabel)).performClick()
    }

    private fun applyOnlyToThisAlbumToggle() = isToggleable() and
        hasText(appContext.getString(R.string.apply_only_to_this_album))

    private fun assertEveryDialogOffersApplyOnlyToThisAlbumChecked() {
        preferenceDialogs.forEach { (menuLabel, _) ->
            openPreferenceDialog(menuLabel)

            composeTestRule.onNode(applyOnlyToThisAlbumToggle()).assertIsOn()
            composeTestRule
                .onAllNodesWithText(appContext.getString(R.string.apply_only_to_this_folder))
                .assertCountEquals(0)

            composeTestRule.onNodeWithText(appContext.getString(R.string.action_cancel)).performClick()
        }
    }

    private fun assertEveryDialogCommitsThisAlbum(
        committed: List<Pair<String, PreferenceApplyTarget>>
    ) {
        preferenceDialogs.forEach { (menuLabel, _) ->
            openPreferenceDialog(menuLabel)
            composeTestRule.onNodeWithText(appContext.getString(R.string.action_ok)).performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                preferenceDialogs.map { (_, name) -> name to PreferenceApplyTarget.CURRENT_FOLDER },
                committed.toList()
            )
        }
    }

    private fun assertEveryDialogCommitsAllAlbumsAfterConfirmation(
        committed: List<Pair<String, PreferenceApplyTarget>>
    ) {
        val confirmationTitle = appContext.getString(R.string.apply_to_all_albums_title)
        preferenceDialogs.forEachIndexed { index, (menuLabel, _) ->
            openPreferenceDialog(menuLabel)
            composeTestRule.onNode(applyOnlyToThisAlbumToggle()).performClick()
            composeTestRule.onNodeWithText(appContext.getString(R.string.action_ok)).performClick()

            composeTestRule.onNodeWithText(confirmationTitle).assertIsDisplayed()
            composeTestRule.runOnIdle {
                assertEquals(index, committed.size)
            }

            composeTestRule
                .onNode(
                    hasText(appContext.getString(R.string.action_ok)) and
                        hasAnyAncestor(hasAnyDescendant(hasText(confirmationTitle)))
                )
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                preferenceDialogs.map { (_, name) -> name to PreferenceApplyTarget.ALL_FOLDERS },
                committed.toList()
            )
        }
    }

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
        isFavouritesAlbum: Boolean = false,
        onBackToAlbums: () -> Unit = {},
        onRemoveSelectedFromAlbum: () -> Unit = {},
        onDeleteSelectedMedia: () -> Unit = {},
        onSelectMedia: (MediaItem) -> Unit = {},
        onCommitPreference: (String, PreferenceApplyTarget) -> Unit = { _, _ -> }
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
            onCommitColumnsPreference = { _, target -> onCommitPreference("columns", target) },
            onCommitMediaTypesPreference = { _, target -> onCommitPreference("mediaTypes", target) },
            onCommitSortPreference = { _, _, target -> onCommitPreference("sort", target) },
            onCommitGroupByPreference = { _, _, target -> onCommitPreference("groupBy", target) },
            onCommitViewTypePreference = { _, target -> onCommitPreference("viewType", target) },
            onToggleInfo = {},
            onUpdateBrowsingPath = {},
            onPerformOperationWithPath = {},
            onCancelOperation = {},
            onSelectMedia = onSelectMedia,
            onEnterMediaSelectionMode = {},
            onDecreaseColumns = {},
            onIncreaseColumns = {},
            isFavouritesAlbum = isFavouritesAlbum
        )
    }
}
