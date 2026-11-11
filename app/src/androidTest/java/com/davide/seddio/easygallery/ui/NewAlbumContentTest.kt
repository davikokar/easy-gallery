package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.GalleryUiState
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.ViewPreferences
import com.davide.seddio.easygallery.data.ViewType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewAlbumContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val folderA = Folder(
        name = "Camera",
        imageCount = 2,
        thumbnailUri = Uri.EMPTY,
        path = "/storage/emulated/0/DCIM/Camera",
        isPinned = false
    )
    private val folderB = Folder(
        name = "Trips",
        imageCount = 2,
        thumbnailUri = Uri.EMPTY,
        path = "/storage/emulated/0/Pictures/Trips",
        isPinned = false
    )

    private val cameraFirst = mediaItem(id = 1, bucketName = folderA.name, folderPath = folderA.path)
    private val cameraSecond = mediaItem(id = 2, bucketName = folderA.name, folderPath = folderA.path)
    private val tripsFirst = mediaItem(id = 3, bucketName = folderB.name, folderPath = folderB.path)
    private val tripsSecond = mediaItem(id = 4, bucketName = folderB.name, folderPath = folderB.path)

    @Test
    fun folderLevelRendersFolders() {
        composeTestRule.setContent {
            NewAlbumContentWrapper()
        }

        composeTestRule.onNodeWithTag("new_album_folder_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_tile_${folderA.path}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_tile_${folderB.path}", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tappingFolderShowsThatFoldersMedia() {
        composeTestRule.setContent {
            NewAlbumContentWrapper()
        }

        composeTestRule.onNodeWithTag("folder_tile_${folderA.path}", useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithTag("new_album_media_level").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_tile_${cameraFirst.uri}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_tile_${tripsFirst.uri}", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun topBarBackButtonReturnsToFolderLevel() {
        composeTestRule.setContent {
            NewAlbumContentWrapper()
        }

        composeTestRule.onNodeWithTag("folder_tile_${folderA.path}", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("new_album_back_button").performClick()

        composeTestRule.onNodeWithTag("new_album_folder_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_album_media_level").assertDoesNotExist()
    }

    @Test
    fun selectionAccumulatesAcrossFolders() {
        composeTestRule.setContent {
            NewAlbumContentWrapper()
        }

        composeTestRule.onNodeWithTag("folder_tile_${folderA.path}", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("media_tile_${cameraFirst.uri}", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("media_tile_${cameraSecond.uri}", useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithTag("new_album_back_button").performClick()

        composeTestRule.onNodeWithTag("folder_tile_${folderB.path}", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("media_tile_${tripsFirst.uri}", useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithTag("new_album_subtitle", useUnmergedTree = true)
            .assertTextEquals("3 items")
    }

    @Test
    fun floatingCreateButtonReportsNameAndAccumulatedSelection() {
        var createdName: String? = null
        var createdUris: Set<Uri> = emptySet()

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                initialName = "My album 7",
                onCreate = { name, uris ->
                    createdName = name
                    createdUris = uris
                }
            )
        }

        composeTestRule.onNodeWithTag("folder_tile_${folderA.path}", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("media_tile_${cameraFirst.uri}", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("new_album_back_button").performClick()

        composeTestRule.onNodeWithTag("folder_tile_${folderB.path}", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("media_tile_${tripsFirst.uri}", useUnmergedTree = true).performClick()

        composeTestRule.onNodeWithTag("new_album_create_button").performClick()

        assertEquals("My album 7", createdName)
        assertEquals(setOf(cameraFirst.uri, tripsFirst.uri), createdUris)
    }

    @Test
    fun duplicateNameBlocksCreationAndExplainsWhy() {
        composeTestRule.setContent {
            NewAlbumContentWrapper(initialName = "Taken", isNameDuplicate = true)
        }

        composeTestRule.onNodeWithTag("new_album_subtitle", useUnmergedTree = true)
            .assertTextEquals("An album with this name already exists")
        composeTestRule.onNodeWithTag("new_album_create_button")
            .assertIsNotEnabled()
            .assertHasNoClickAction()
    }

    @Test
    fun blankNameBlocksCreationAndExplainsWhy() {
        composeTestRule.setContent {
            NewAlbumContentWrapper(initialName = "   ")
        }

        composeTestRule.onNodeWithTag("new_album_subtitle", useUnmergedTree = true)
            .assertTextEquals("Album name cannot be blank")
        composeTestRule.onNodeWithTag("new_album_create_button")
            .assertIsNotEnabled()
            .assertHasNoClickAction()
    }

    @Test
    fun duplicateNameCannotCreateAlbum() {
        var created = false

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                initialName = "Taken",
                isNameDuplicate = true,
                onCreate = { _, _ -> created = true }
            )
        }

        composeTestRule.onNodeWithTag("new_album_create_button")
            .performTouchInput { click() }

        assertFalse(created)
    }

    @Test
    fun blankNameCannotCreateAlbum() {
        var created = false

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                initialName = "   ",
                onCreate = { _, _ -> created = true }
            )
        }

        composeTestRule.onNodeWithTag("new_album_create_button")
            .performTouchInput { click() }

        assertFalse(created)
    }

    @Test
    fun closeCancelsWithoutCreating() {
        var created = false
        var cancelled = false

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                onCreate = { _, _ -> created = true },
                onCancel = { cancelled = true }
            )
        }

        composeTestRule.onNodeWithTag("new_album_close_button").performClick()

        assertTrue(cancelled)
        assertEquals(false, created)
    }

    @Test
    fun floatingCreateButtonDoesNotOverlapLastMediaRowInList() {
        val manyMedia = (1..60).map { id ->
            mediaItem(id = id, bucketName = folderA.name, folderPath = folderA.path)
        }

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                folders = listOf(folderA),
                mediaByFolder = mapOf(folderA.path to manyMedia),
                selectedFolderViewType = ViewType.LIST
            )
        }

        composeTestRule.onNodeWithTag("folder_tile_${folderA.path}", useUnmergedTree = true).performClick()
        val lastMediaTag = "media_tile_${manyMedia.last().uri}"
        assertCanScrollLastItemAboveCreateButton("new_album_media_level", lastMediaTag)
    }

    @Test
    fun floatingCreateButtonDoesNotOverlapLastMediaRowInGrid() {
        val manyMedia = (1..60).map { id ->
            mediaItem(id = id, bucketName = folderA.name, folderPath = folderA.path)
        }

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                folders = listOf(folderA),
                mediaByFolder = mapOf(folderA.path to manyMedia),
                selectedFolderViewType = ViewType.GRID
            )
        }

        composeTestRule.onNodeWithTag("folder_tile_${folderA.path}", useUnmergedTree = true).performClick()
        val lastMediaTag = "media_tile_${manyMedia.last().uri}"
        assertCanScrollLastItemAboveCreateButton("new_album_media_level", lastMediaTag)
    }

    @Test
    fun floatingCreateButtonDoesNotOverlapLastFolderRowInGrid() {
        val manyFolders = (1..60).map { id ->
            Folder(
                name = "Folder$id",
                imageCount = 1,
                thumbnailUri = Uri.EMPTY,
                path = "/storage/emulated/0/Pictures/Folder$id",
                isPinned = false
            )
        }

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                folders = manyFolders,
                foldersViewType = ViewType.GRID,
                mediaByFolder = manyFolders.associate { folder ->
                    folder.path to listOf(mediaItem(id = folder.name.removePrefix("Folder").toInt(), bucketName = folder.name, folderPath = folder.path))
                }
            )
        }

        val lastFolderTag = "folder_tile_${manyFolders.last().path}"
        assertCanScrollLastItemAboveCreateButton("new_album_folder_grid", lastFolderTag)
    }

    @Test
    fun floatingCreateButtonDoesNotOverlapLastFolderRowInList() {
        val manyFolders = (1..60).map { id ->
            Folder(
                name = "Folder$id",
                imageCount = 1,
                thumbnailUri = Uri.EMPTY,
                path = "/storage/emulated/0/Pictures/Folder$id",
                isPinned = false
            )
        }

        composeTestRule.setContent {
            NewAlbumContentWrapper(
                folders = manyFolders,
                foldersViewType = ViewType.LIST,
                mediaByFolder = manyFolders.associate { folder ->
                    folder.path to listOf(mediaItem(id = folder.name.removePrefix("Folder").toInt(), bucketName = folder.name, folderPath = folder.path))
                }
            )
        }

        val lastFolderTag = "folder_tile_${manyFolders.last().path}"
        assertCanScrollLastItemAboveCreateButton("new_album_folder_list", lastFolderTag)
    }

    @Test
    fun albumNameFieldCanBeEditedByTag() {
        composeTestRule.setContent {
            NewAlbumContentWrapper(initialName = "My album 1")
        }

        composeTestRule.onNodeWithTag("new_album_name_field").performTextInput(" updated")
        composeTestRule.onNodeWithTag("new_album_name_field").assertIsDisplayed()
    }

    @Composable
    private fun NewAlbumContentWrapper(
        initialName: String = "My album 1",
        isNameDuplicate: Boolean = false,
        folders: List<Folder> = listOf(folderA, folderB),
        foldersViewType: ViewType = ViewType.GRID,
        selectedFolderViewType: ViewType = ViewType.GRID,
        mediaByFolder: Map<String, List<MediaItem>> = mapOf(
            folderA.path to listOf(cameraFirst, cameraSecond),
            folderB.path to listOf(tripsFirst, tripsSecond)
        ),
        onCreate: (String, Set<Uri>) -> Unit = { _, _ -> },
        onCancel: () -> Unit = {}
    ) {
        var name by remember { mutableStateOf(initialName) }
        var openFolderPath by remember { mutableStateOf<String?>(null) }
        var selectedUris by remember { mutableStateOf(emptySet<Uri>()) }

        val selectedFolderMedia = mediaByFolder[openFolderPath].orEmpty()

        NewAlbumContent(
            name = name,
            isNameDuplicate = isNameDuplicate,
            foldersState = GalleryUiState.Success(folders),
            foldersPreferences = ViewPreferences.defaultFor(PreferenceScope.FOLDERS).copy(
                viewType = foldersViewType,
                columns = 2
            ),
            openFolderPath = openFolderPath,
            selectedFolderPreferences = ViewPreferences.defaultFor(PreferenceScope.FOLDER_DETAIL).copy(
                viewType = selectedFolderViewType,
                columns = 3,
                groupBy = GroupByType.NONE
            ),
            groupedFolderMedia = mapOf("" to selectedFolderMedia),
            selectedFolderMedia = selectedFolderMedia,
            selectedUris = selectedUris,
            onNameChange = { name = it },
            onOpenFolder = { openFolderPath = it.path },
            onBackToFolders = { openFolderPath = null },
            onToggleMedia = { item ->
                selectedUris = if (item.uri in selectedUris) {
                    selectedUris - item.uri
                } else {
                    selectedUris + item.uri
                }
            },
            onCreate = { onCreate(name, selectedUris) },
            onCancel = onCancel
        )
    }

    private fun assertCanScrollLastItemAboveCreateButton(containerTag: String, targetTag: String) {
        for (attempt in 0 until 20) {
            val targetIsComposed = composeTestRule
                .onAllNodesWithTag(targetTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (targetIsComposed) {
                break
            }
            composeTestRule.onNodeWithTag(containerTag).performTouchInput { swipeUp() }
        }

        var lastTargetBounds: Rect? = null
        var createButtonBounds: Rect? = null
        var scrolledClear = false

        for (attempt in 0 until 20) {
            val targetNodes = composeTestRule
                .onAllNodesWithTag(targetTag, useUnmergedTree = true)
                .fetchSemanticsNodes()

            if (targetNodes.isEmpty()) {
                composeTestRule.onNodeWithTag(containerTag).performTouchInput { swipeUp() }
                continue
            }

            val targetBounds = targetNodes.first().boundsInRoot
            val buttonBounds = composeTestRule
                .onNodeWithTag("new_album_create_button")
                .fetchSemanticsNode()
                .boundsInRoot

            lastTargetBounds = targetBounds
            createButtonBounds = buttonBounds

            if (targetBounds.bottom <= buttonBounds.top) {
                scrolledClear = true
                break
            }

            composeTestRule.onNodeWithTag(containerTag).performTouchInput { swipeUp() }
        }

        assertTrue(
            "Expected to be able to scroll the last item fully above the New album create FAB",
            scrolledClear
        )
        assertFalse(
            "Expected the New album create FAB not to overlap the last visible item after scrolling",
            boundsOverlap(lastTargetBounds!!, createButtonBounds!!)
        )
    }

    private fun boundsOverlap(first: Rect, second: Rect): Boolean {
        return first.left < second.right &&
            first.right > second.left &&
            first.top < second.bottom &&
            first.bottom > second.top
    }

    private fun mediaItem(id: Int, bucketName: String, folderPath: String): MediaItem = MediaItem(
        uri = Uri.parse("content://media/external/images/media/$id"),
        name = "$id.jpg",
        dateAdded = id.toLong(),
        dateModified = id.toLong(),
        size = id.toLong(),
        type = MediaType.IMAGE,
        bucketName = bucketName,
        folderPath = folderPath
    )
}
