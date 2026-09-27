package com.davide.seddio.easygallery.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import com.davide.seddio.easygallery.data.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val repository = FakeMediaRepository()
    private val permissionHandler = mockk<MediaPermissionHandler>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockUri = mockk<Uri>(relaxed = true)
    private val mockUri1 = mockk<Uri>(relaxed = true)
    private val mockUri2 = mockk<Uri>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockUri
        
        mockkStatic(Environment::class)
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.absolutePath } returns "/storage/emulated/0"
        every { mockFile.path } returns "/storage/emulated/0"
        every { mockFile.getPath() } returns "/storage/emulated/0"
        every { Environment.getExternalStorageDirectory() } returns mockFile
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMediaItem(uri: Uri, folderPath: String) = MediaItem(
        uri = uri,
        name = "file.jpg",
        dateAdded = 1000L,
        dateModified = 1000L,
        size = 100L,
        type = MediaType.IMAGE,
        bucketName = folderPath.substringAfterLast("/"),
        folderPath = folderPath
    )

    private fun createViewModel(
        displayPreferencesStore: DisplayPreferencesStore = InMemoryDisplayPreferencesStore(),
        folderViewPreferencesStore: FolderViewPreferencesStore = InMemoryFolderViewPreferencesStore(),
        folderThumbnailStore: FolderThumbnailStore = SharedPreferencesFolderThumbnailStore(application)
    ) = GalleryViewModel(
        application,
        repository,
        permissionHandler,
        folderViewPreferencesStore,
        folderThumbnailStore,
        displayPreferencesStore
    )

    @Test
    fun `long-pressing a folder enters selection mode`() = runTest {
        val viewModel = createViewModel()
        val folderPath = "/storage/emulated/0/Pictures"
        
        viewModel.enterSelectionMode(folderPath)
        
        assertTrue(viewModel.isSelectionMode.value)
        assertEquals(setOf(folderPath), viewModel.selectedFolders.value)
    }

    @Test
    fun `selecting a second folder adds it to selection`() = runTest {
        val viewModel = createViewModel()
        val folder1 = "/path/1"
        val folder2 = "/path/2"
        
        viewModel.enterSelectionMode(folder1)
        viewModel.toggleSelection(folder2)
        
        assertEquals(setOf(folder1, folder2), viewModel.selectedFolders.value)
    }

    @Test
    fun `selecting the last selected folder exits selection mode`() = runTest {
        val viewModel = createViewModel()
        val folderPath = "/path/1"
        
        viewModel.enterSelectionMode(folderPath)
        viewModel.toggleSelection(folderPath)
        
        assertFalse(viewModel.isSelectionMode.value)
        assertTrue(viewModel.selectedFolders.value.isEmpty())
    }

    @Test
    fun `select all selects folder paths`() = runTest {
        val media = listOf(
            createMediaItem(mockUri1, "/storage/emulated/0/A"),
            createMediaItem(mockUri2, "/storage/emulated/0/B")
        )
        repository.mediaItems = media
        
        val viewModel = createViewModel()
        
        // Start collecting filteredFolders to activate stateIn
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredFolders.collect {}
        }
        
        viewModel.loadFolders()
        
        val uiState = viewModel.filteredFolders.value
        assertTrue("Expected Success state but got $uiState", uiState is GalleryUiState.Success)
        
        viewModel.selectAll()
        
        assertEquals(setOf("/storage/emulated/0/A", "/storage/emulated/0/B"), viewModel.selectedFolders.value)
        
        job.cancel()
    }

    @Test
    fun `selected media uses Uri identity`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        
        assertTrue(viewModel.isMediaSelectionMode.value)
        assertEquals(setOf(mockUri1), viewModel.selectedMediaItems.value)
    }

    @Test
    fun `recording and consuming video playback position delegates through view model`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")

        viewModel.selectMedia(item)
        viewModel.recordVideoPlaybackPosition(mockUri1, 3_000L, playWhenReady = true)

        val remembered = viewModel.consumeVideoPlaybackPosition(mockUri1)

        assertNotNull(remembered)
        assertEquals(mockUri1, remembered?.uri)
        assertEquals(3_000L, remembered?.positionMs)
        assertEquals(true, remembered?.playWhenReady)
    }

    @Test
    fun `closing media clears remembered video playback position`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")

        viewModel.selectMedia(item)
        viewModel.recordVideoPlaybackPosition(mockUri1, 1_500L, playWhenReady = false)
        viewModel.closeMedia()

        assertNull(viewModel.consumeVideoPlaybackPosition(mockUri1))
    }

    @Test
    fun `exiting media selection clears selected media`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.exitMediaSelectionMode()
        
        assertFalse(viewModel.isMediaSelectionMode.value)
        assertTrue(viewModel.selectedMediaItems.value.isEmpty())
    }

    @Test
    fun `exiting folder selection clears selected folders`() = runTest {
        val viewModel = createViewModel()
        
        viewModel.enterSelectionMode("/path")
        viewModel.exitSelectionMode()
        
        assertFalse(viewModel.isSelectionMode.value)
        assertTrue(viewModel.selectedFolders.value.isEmpty())
    }

    @Test
    fun `enterThumbnailPickerMode is no-op when no folder is selected`() = runTest {
        val folderThumbnailStore = InMemoryFolderThumbnailStore()
        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.enterSelectionMode("/storage/emulated/0/Pictures")
        viewModel.enterThumbnailPickerMode()

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertTrue(viewModel.isSelectionMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertTrue(folderThumbnailStore.loadAll().isEmpty())
    }

    @Test
    fun `enterThumbnailPickerMode with selected folder activates picker and exits selection modes`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val selectedMediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(selectedMediaItem)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())

        viewModel.selectFolder(selectedFolder)
        viewModel.enterSelectionMode(folderPath)
        viewModel.enterThumbnailPickerMode()

        assertTrue(viewModel.isThumbnailPickerMode.value)
        assertFalse(viewModel.isSelectionMode.value)
        assertFalse(viewModel.isMediaSelectionMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)

        viewModel.selectMedia(selectedMediaItem)
        assertEquals(selectedMediaItem.uri, viewModel.folderThumbnailDraftUri.value)

        viewModel.enterMediaSelectionMode(selectedMediaItem)
        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)

        viewModel.enterThumbnailPickerMode()

        assertTrue(viewModel.isThumbnailPickerMode.value)
        assertFalse(viewModel.isMediaSelectionMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
    }

    @Test
    fun `enterThumbnailPickerMode seeds pre-selection from persisted override without seeding draft or writing`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 2, thumbnailUri = mockUri2, path = folderPath)
        val overrideItem = createMediaItem(mockUri1, folderPath)
        val otherItem = createMediaItem(mockUri2, folderPath)
        repository.mediaItems = listOf(otherItem, overrideItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()
        folderThumbnailStore.save(folderPath, overrideItem.uri.toString())
        val storeBeforeEnter = folderThumbnailStore.loadAll()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()

        assertEquals(overrideItem.uri, viewModel.folderThumbnailPreselectedUri.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertEquals(storeBeforeEnter, folderThumbnailStore.loadAll())
    }

    @Test
    fun `enterThumbnailPickerMode leaves pre-selection null when selected folder has no persisted override`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()

        assertNull(viewModel.folderThumbnailPreselectedUri.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
    }

    @Test
    fun `enterThumbnailPickerMode leaves pre-selection null when persisted override is stale for current folder media`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()
        folderThumbnailStore.save(folderPath, mockUri2.toString())

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()

        assertNull(viewModel.folderThumbnailPreselectedUri.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
    }

    @Test
    fun `enterThumbnailPickerMode refreshes pre-selection from persisted override after commit`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 2, thumbnailUri = mockUri2, path = folderPath)
        val committedItem = createMediaItem(mockUri1, folderPath)
        val originalItem = createMediaItem(mockUri2, folderPath)
        repository.mediaItems = listOf(originalItem, committedItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(committedItem)
        viewModel.commitFolderThumbnailDraft()

        viewModel.enterThumbnailPickerMode()

        assertTrue(viewModel.isThumbnailPickerMode.value)
        assertEquals(committedItem.uri, viewModel.folderThumbnailPreselectedUri.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertEquals(committedItem.uri.toString(), folderThumbnailStore.loadAll()[folderPath])
    }

    @Test
    fun `selectMedia in thumbnail picker mode sets draft keeps picker active writes nothing and keeps viewer closed`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 2, thumbnailUri = mockUri2, path = folderPath)
        val thumbnailItem = createMediaItem(mockUri1, folderPath)
        val originalItem = createMediaItem(mockUri2, folderPath)
        repository.mediaItems = listOf(originalItem, thumbnailItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(thumbnailItem)

        assertTrue(viewModel.isThumbnailPickerMode.value)
        assertEquals(thumbnailItem.uri, viewModel.folderThumbnailDraftUri.value)
        assertTrue(folderThumbnailStore.loadAll().isEmpty())
        assertNull(viewModel.selectedMedia.value)
    }

    @Test
    fun `selectMedia in thumbnail picker mode updates draft when tapping a second item`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 2, thumbnailUri = mockUri1, path = folderPath)
        val firstItem = createMediaItem(mockUri1, folderPath)
        val secondItem = createMediaItem(mockUri2, folderPath)
        repository.mediaItems = listOf(firstItem, secondItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)
        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(firstItem)
        viewModel.selectMedia(secondItem)

        assertTrue(viewModel.isThumbnailPickerMode.value)
        assertEquals(secondItem.uri, viewModel.folderThumbnailDraftUri.value)
        assertTrue(folderThumbnailStore.loadAll().isEmpty())
        assertNull(viewModel.selectedMedia.value)
    }

    @Test
    fun `selectMedia when not in thumbnail picker mode still opens viewer`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val item = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(item)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())
        viewModel.selectMedia(item)

        assertEquals(item.uri, viewModel.selectedMedia.value?.uri)
    }

    @Test
    fun `commitFolderThumbnailDraft persists draft exits picker clears draft and updates filteredFolders`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 2, thumbnailUri = mockUri2, path = folderPath)
        val originalFolderItem = createMediaItem(mockUri2, folderPath)
        val customThumbnailItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(originalFolderItem, customThumbnailItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)
        val filteredFoldersJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredFolders.collect {}
        }

        viewModel.loadFolders()
        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(customThumbnailItem)
        viewModel.commitFolderThumbnailDraft()

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertEquals(customThumbnailItem.uri.toString(), folderThumbnailStore.loadAll()[folderPath])

        val uiState = viewModel.filteredFolders.value as GalleryUiState.Success
        val folder = uiState.folders.first { it.path == folderPath }
        assertEquals(customThumbnailItem.uri, folder.thumbnailUri)

        filteredFoldersJob.cancel()
    }

    @Test
    fun `exitThumbnailPickerMode discards draft and writes nothing`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(mediaItem)
        viewModel.exitThumbnailPickerMode()

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertTrue(folderThumbnailStore.loadAll().isEmpty())
    }

    @Test
    fun `exitThumbnailPickerMode clears pre-selection and writes nothing`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()
        folderThumbnailStore.save(folderPath, mediaItem.uri.toString())
        val storeBeforeExit = folderThumbnailStore.loadAll()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        assertEquals(mediaItem.uri, viewModel.folderThumbnailPreselectedUri.value)

        viewModel.exitThumbnailPickerMode()

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailPreselectedUri.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertEquals(storeBeforeExit, folderThumbnailStore.loadAll())
    }

    @Test
    fun `commitFolderThumbnailDraft with no draft writes nothing and exits cleanly`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.commitFolderThumbnailDraft()

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertTrue(folderThumbnailStore.loadAll().isEmpty())
    }

    @Test
    fun `re-entering thumbnail picker after commit starts with null draft`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()
        repository.mediaItems = listOf(mediaItem)

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(mediaItem)
        viewModel.commitFolderThumbnailDraft()
        viewModel.enterThumbnailPickerMode()

        assertTrue(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertEquals(mediaItem.uri.toString(), folderThumbnailStore.loadAll()[folderPath])
    }

    @Test
    fun `filteredFolders reflects committed custom thumbnail after picker draft flow`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 2, thumbnailUri = mockUri2, path = folderPath)
        val originalFolderItem = createMediaItem(mockUri2, folderPath)
        val customThumbnailItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(originalFolderItem, customThumbnailItem)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())
        val filteredFoldersJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredFolders.collect {}
        }

        viewModel.loadFolders()
        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(customThumbnailItem)
        viewModel.commitFolderThumbnailDraft()

        val uiState = viewModel.filteredFolders.value as GalleryUiState.Success
        val folder = uiState.folders.first { it.path == folderPath }
        assertEquals(customThumbnailItem.uri, folder.thumbnailUri)

        filteredFoldersJob.cancel()
    }

    @Test
    fun `enterSelectionMode clears thumbnail picker mode and draft`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(mediaItem)
        viewModel.enterSelectionMode(folderPath)

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertTrue(viewModel.isSelectionMode.value)
    }

    @Test
    fun `selectFolder clears thumbnail picker mode and draft`() = runTest {
        val folderAPath = "/storage/emulated/0/Pictures/Camera"
        val folderBPath = "/storage/emulated/0/Pictures/Screenshots"
        val folderA = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderAPath)
        val folderB = Folder(name = "Screenshots", imageCount = 1, thumbnailUri = mockUri2, path = folderBPath)
        val folderAItem = createMediaItem(mockUri1, folderAPath)
        val folderBItem = createMediaItem(mockUri2, folderBPath)
        repository.mediaItems = listOf(folderAItem, folderBItem)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())

        viewModel.selectFolder(folderA)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(folderAItem)
        viewModel.selectFolder(folderB)

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertEquals(folderBPath, viewModel.selectedFolder.value?.path)
    }

    @Test
    fun `enterMediaSelectionMode clears thumbnail picker mode and draft`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(mediaItem)
        viewModel.enterMediaSelectionMode(mediaItem)

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
        assertTrue(viewModel.isMediaSelectionMode.value)
    }

    @Test
    fun `folder thumbnail override persists across view model instances with same in-memory store`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 2, thumbnailUri = mockUri2, path = folderPath)
        val originalFolderItem = createMediaItem(mockUri2, folderPath)
        val customThumbnailItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(originalFolderItem, customThumbnailItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()

        val firstViewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)
        firstViewModel.loadFolders()
        firstViewModel.selectFolder(selectedFolder)
        firstViewModel.enterThumbnailPickerMode()
        firstViewModel.selectMedia(customThumbnailItem)
        firstViewModel.commitFolderThumbnailDraft()

        val secondViewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)
        val filteredFoldersJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            secondViewModel.filteredFolders.collect {}
        }
        secondViewModel.loadFolders()
        secondViewModel.selectFolder(selectedFolder)

        val uiState = secondViewModel.filteredFolders.value as GalleryUiState.Success
        val folder = uiState.folders.first { it.path == folderPath }
        assertEquals(customThumbnailItem.uri, folder.thumbnailUri)

        filteredFoldersJob.cancel()
    }

    @Test
    fun `backToFolders exits thumbnail picker mode and clears draft`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)

        val viewModel = createViewModel(folderThumbnailStore = InMemoryFolderThumbnailStore())

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        viewModel.selectMedia(mediaItem)
        viewModel.backToFolders()

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailDraftUri.value)
    }

    @Test
    fun `backToFolders and enterMediaSelectionMode clear thumbnail pre-selection`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/Camera"
        val selectedFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        val mediaItem = createMediaItem(mockUri1, folderPath)
        repository.mediaItems = listOf(mediaItem)
        val folderThumbnailStore = InMemoryFolderThumbnailStore()
        folderThumbnailStore.save(folderPath, mediaItem.uri.toString())

        val viewModel = createViewModel(folderThumbnailStore = folderThumbnailStore)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        assertEquals(mediaItem.uri, viewModel.folderThumbnailPreselectedUri.value)

        viewModel.backToFolders()

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertNull(viewModel.folderThumbnailPreselectedUri.value)

        viewModel.selectFolder(selectedFolder)
        viewModel.enterThumbnailPickerMode()
        assertEquals(mediaItem.uri, viewModel.folderThumbnailPreselectedUri.value)

        viewModel.enterMediaSelectionMode(mediaItem)

        assertFalse(viewModel.isThumbnailPickerMode.value)
        assertTrue(viewModel.isMediaSelectionMode.value)
        assertNull(viewModel.folderThumbnailPreselectedUri.value)
    }

    @Test
    fun `deleteMedia calls repository and reloads folders`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.deleteMedia(item)
        
        assertEquals(listOf(mockUri1), repository.deletedUris)
        // Verify loadFolders was called by checking repository interaction
        // Since we are using UnconfinedTestDispatcher and a fake, we check side effects.
    }

    @Test
    fun `deleteSelectedMedia deletes all selected media Uris`() = runTest {
        val viewModel = createViewModel()
        val item1 = createMediaItem(mockUri1, "/path1")
        val item2 = createMediaItem(mockUri2, "/path2")
        
        viewModel.enterMediaSelectionMode(item1)
        viewModel.toggleMediaSelection(item2)
        
        viewModel.deleteSelectedMedia()
        
        assertEquals(setOf(mockUri1, mockUri2), repository.deletedUris.toSet())
        assertFalse(viewModel.isMediaSelectionMode.value)
    }

    @Test
    fun `deleteSelected folder deletes all media in those folders`() = runTest {
        val media = listOf(
            createMediaItem(mockUri1, "/path/A"),
            createMediaItem(mockUri2, "/path/B"),
            createMediaItem(mockk(), "/path/C")
        )
        repository.mediaItems = media
        val viewModel = createViewModel()
        viewModel.loadFolders()
        
        viewModel.enterSelectionMode("/path/A")
        viewModel.toggleSelection("/path/B")
        
        viewModel.deleteSelected()
        
        assertEquals(setOf(mockUri1, mockUri2), repository.deletedUris.toSet())
        assertFalse(viewModel.isSelectionMode.value)
    }

    @Test
    fun `SecurityException in deletion emits pending permission request`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        val mockIntentSender = mockk<android.content.IntentSender>()
        
        repository.shouldThrowSecurityException = true
        every { permissionHandler.createDeleteRequest(any(), any()) } returns mockIntentSender
        
        viewModel.deleteMedia(item)
        
        assertNotNull(viewModel.pendingWriteRequest.value)
        assertEquals(mockIntentSender, viewModel.pendingWriteRequest.value?.intentSender)
    }

    @Test
    fun `moving selected media calls repository with correct parameters`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        val destPath = "/storage/emulated/0/NewFolder"
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath(destPath)
        
        assertEquals(1, repository.movedUris.size)
        assertEquals(listOf(mockUri1), repository.movedUris[0].first)
        assertEquals("NewFolder/", repository.movedUris[0].second)
    }

    @Test
    fun `copy initiated from viewer copies only viewed item and keeps viewer open`() = runTest {
        val viewModel = createViewModel()
        val viewedItem = createMediaItem(mockUri1, "/storage/emulated/0/Pictures/Source")
            .copy(name = "viewed.jpg")
        val destinationPath = "/storage/emulated/0/Pictures/Target"

        viewModel.selectMedia(viewedItem)
        viewModel.startOperationForMedia(viewedItem, OperationType.COPY)
        viewModel.performOperationWithPath(destinationPath)

        assertEquals(1, repository.copiedFiles.size)
        assertEquals(
            Triple(viewedItem.folderPath, viewedItem.name, destinationPath),
            repository.copiedFiles.single()
        )
        assertEquals(viewedItem.uri, viewModel.selectedMedia.value?.uri)
    }

    @Test
    fun `move initiated from viewer moves only viewed item and closes viewer`() = runTest {
        val viewModel = createViewModel()
        val viewedItem = createMediaItem(mockUri1, "/storage/emulated/0/Pictures/Source")
        val destinationPath = "/storage/emulated/0/Pictures/Target"

        viewModel.selectMedia(viewedItem)
        viewModel.startOperationForMedia(viewedItem, OperationType.MOVE)
        viewModel.performOperationWithPath(destinationPath)

        assertEquals(1, repository.movedUris.size)
        assertEquals(listOf(viewedItem.uri), repository.movedUris.single().first)
        assertEquals("Pictures/Target/", repository.movedUris.single().second)
        assertNull(viewModel.selectedMedia.value)
    }

    @Test
    fun `viewer operation does not enter media selection mode or change selected media items`() = runTest {
        val viewModel = createViewModel()
        val viewedItem = createMediaItem(mockUri1, "/storage/emulated/0/Pictures/Source")

        viewModel.selectMedia(viewedItem)
        viewModel.startOperationForMedia(viewedItem, OperationType.COPY)
        viewModel.performOperationWithPath("/storage/emulated/0/Pictures/Target")

        assertFalse(viewModel.isMediaSelectionMode.value)
        assertTrue(viewModel.selectedMediaItems.value.isEmpty())
    }

    @Test
    fun `cancel operation clears explicit target so next selection copy does not include old viewer item`() = runTest {
        val viewerItem = createMediaItem(mockUri1, "/storage/emulated/0/Pictures/Viewer")
            .copy(name = "viewer.jpg")
        val selectedItem = createMediaItem(mockUri2, "/storage/emulated/0/Pictures/Selection")
            .copy(name = "selected.jpg")
        repository.mediaItems = listOf(viewerItem, selectedItem)

        val viewModel = createViewModel()
        viewModel.loadFolders()

        viewModel.selectMedia(viewerItem)
        viewModel.startOperationForMedia(viewerItem, OperationType.COPY)
        viewModel.cancelOperation()

        viewModel.enterMediaSelectionMode(selectedItem)
        viewModel.startOperation(OperationType.COPY)
        viewModel.performOperationWithPath("/storage/emulated/0/Pictures/Target")

        assertEquals(1, repository.copiedFiles.size)
        assertEquals(
            Triple(selectedItem.folderPath, selectedItem.name, "/storage/emulated/0/Pictures/Target"),
            repository.copiedFiles.single()
        )
    }

    @Test
    fun `copying viewed item into same folder is ignored`() = runTest {
        val viewModel = createViewModel()
        val viewedItem = createMediaItem(mockUri1, "/storage/emulated/0/Pictures/Source")

        viewModel.selectMedia(viewedItem)
        viewModel.startOperationForMedia(viewedItem, OperationType.COPY)
        viewModel.performOperationWithPath("/storage/emulated/0/Pictures/Source")

        assertTrue(repository.copiedFiles.isEmpty())
        assertEquals(viewedItem.uri, viewModel.selectedMedia.value?.uri)
    }

    @Test
    fun `viewer copy to invalid path outside root does nothing`() = runTest {
        val viewModel = createViewModel()
        val viewedItem = createMediaItem(mockUri1, "/storage/emulated/0/Pictures/Source")

        viewModel.selectMedia(viewedItem)
        viewModel.startOperationForMedia(viewedItem, OperationType.COPY)
        viewModel.performOperationWithPath("/data/user/0/invalid")

        assertTrue(repository.copiedFiles.isEmpty())
        assertEquals(OperationType.COPY, viewModel.pendingOperation.value)
        assertTrue(viewModel.isDestinationPickerActive.value)
    }

    @Test
    fun `moving selected folders moves all contained media`() = runTest {
        val media = listOf(
            createMediaItem(mockUri1, "/storage/emulated/0/A"),
            createMediaItem(mockUri2, "/storage/emulated/0/A"),
            createMediaItem(mockk(), "/storage/emulated/0/B")
        )
        repository.mediaItems = media
        val viewModel = createViewModel()
        viewModel.loadFolders()
        
        viewModel.enterSelectionMode("/storage/emulated/0/A")
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath("/storage/emulated/0/Target")
        
        assertEquals(1, repository.movedUris.size)
        assertEquals(setOf(mockUri1, mockUri2), repository.movedUris[0].first.toSet())
        assertEquals("Target/", repository.movedUris[0].second)
    }

    @Test
    fun `successful move cleans up UI state`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath("/storage/emulated/0/Target")
        
        assertFalse(viewModel.isMediaSelectionMode.value)
        assertFalse(viewModel.isDestinationPickerActive.value)
        assertNull(viewModel.pendingOperation.value)
    }

    @Test
    fun `move to invalid path outside root does nothing`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath("/data/user/0/invalid")
        
        assertTrue(repository.movedUris.isEmpty())
        assertTrue(viewModel.isMediaSelectionMode.value) // Still in mode
    }

    @Test
    fun `SecurityException during move stores pending operation and emits request`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        val mockIntentSender = mockk<android.content.IntentSender>()
        
        repository.shouldThrowSecurityException = true
        every { permissionHandler.createWriteRequest(any(), any()) } returns mockIntentSender
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath("/storage/emulated/0/Target")
        
        assertNotNull(viewModel.pendingMoveOperation.value)
        assertEquals(listOf(mockUri1), viewModel.pendingMoveOperation.value?.uris)
        assertEquals("Target/", viewModel.pendingMoveOperation.value?.targetRelativePath)
        assertEquals(mockIntentSender, viewModel.pendingWriteRequest.value?.intentSender)
    }

    @Test
    fun `onWriteRequestResult true retries pending move`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        
        // 1. Simulate SecurityException to set up pending operation
        repository.shouldThrowSecurityException = true
        viewModel.enterMediaSelectionMode(item)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath("/storage/emulated/0/Target")
        
        // 2. Clear exception flag and grant permission
        repository.shouldThrowSecurityException = false
        viewModel.onWriteRequestResult(true)
        
        // 3. Verify move finally happened
        assertEquals(1, repository.movedUris.size)
        assertEquals(listOf(mockUri1), repository.movedUris[0].first)
        assertNull(viewModel.pendingMoveOperation.value)
    }

    @Test
    fun `onWriteRequestResult false clears pending move`() = runTest {
        val viewModel = createViewModel()
        val item = createMediaItem(mockUri1, "/path")
        
        repository.shouldThrowSecurityException = true
        viewModel.enterMediaSelectionMode(item)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath("/storage/emulated/0/Target")
        
        viewModel.onWriteRequestResult(false)
        
        assertNull(viewModel.pendingMoveOperation.value)
        assertTrue(repository.movedUris.isEmpty())
    }

    @Test
    fun `moved media disappears from source folder immediately`() = runTest {
        // 1. Setup folders and media
        val sourcePath = "/storage/emulated/0/Pictures/Source"
        val targetPath = "/storage/emulated/0/Pictures/Target"
        val sourceBucket = "Source"

        val movedItem = createMediaItem(mockUri1, sourcePath).copy(name = "moved.jpg", bucketName = sourceBucket)
        val stayItem = createMediaItem(mockUri2, sourcePath).copy(name = "stay.jpg", bucketName = sourceBucket)

        repository.mediaItems = listOf(movedItem, stayItem)

        val viewModel = createViewModel()

        // Start collecting flows to activate stateIn
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredMedia.collect {}
        }

        // 2. Load folders and select source folder
        viewModel.loadFolders()
        val sourceFolder = Folder(name = sourceBucket, imageCount = 2, thumbnailUri = mockUri1, path = sourcePath)
        viewModel.selectFolder(sourceFolder)

        // Assert filteredMedia contains both items
        assertEquals(2, viewModel.filteredMedia.value.size)
        assertTrue(viewModel.filteredMedia.value.any { it.name == "moved.jpg" })
        assertTrue(viewModel.filteredMedia.value.any { it.name == "stay.jpg" })

        // 3. Perform move
        viewModel.enterMediaSelectionMode(movedItem)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath(targetPath)

        // 4. Assertions
        // Verify repository was called
        assertEquals(1, repository.movedUris.size)
        assertEquals(listOf(mockUri1), repository.movedUris[0].first)

        // Verify filteredMedia for the currently open Source folder no longer contains moved.jpg.
        // After a successful move, tryMoveMedia() calls loadFolders(), which refreshes _mediaInFolder
        // for the open folder, so the moved item is removed immediately while the other item stays.
        val currentMedia = viewModel.filteredMedia.value
        assertFalse("Moved item should be gone from filteredMedia", currentMedia.any { it.uri == mockUri1 })
        assertEquals(1, currentMedia.size)
        assertTrue(currentMedia.any { it.name == "stay.jpg" })
    }

    @Test
    fun `excluding folder by path correctly filters it out`() = runTest {
        val folderPath = "/storage/emulated/0/DCIM/Camera"
        val folderName = "Camera"
        val media = listOf(createMediaItem(mockUri1, folderPath))
        repository.mediaItems = media
        repository.folders = listOf(Folder(name = folderName, imageCount = 1, thumbnailUri = mockUri1, path = folderPath))
        
        val viewModel = createViewModel()
        
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredFolders.collect {}
        }
        
        viewModel.loadFolders()
        
        // Verify initially visible
        val uiStateInitial = viewModel.filteredFolders.value
        assertTrue((uiStateInitial as GalleryUiState.Success).folders.any { it.path == folderPath })

        // Exclude by path
        viewModel.excludeFolder(folderPath)
        
        // Assert: It is in excludedFolders set
        assertTrue(viewModel.excludedFolders.value.contains(folderPath))
        
        // Assert: filteredFolders correctly excludes it
        val uiStateExcluded = viewModel.filteredFolders.value
        assertFalse("Folder should be hidden when excluded by path", 
            (uiStateExcluded as GalleryUiState.Success).folders.any { it.path == folderPath })
        
        // Un-exclude by path
        viewModel.unexcludeFolder(folderPath)
        
        // Assert: It is no longer in excludedFolders set
        assertFalse(viewModel.excludedFolders.value.contains(folderPath))
        
        // Assert: filteredFolders correctly includes it again
        val uiStateIncluded = viewModel.filteredFolders.value
        assertTrue("Folder should be visible again after un-excluding by path", 
            (uiStateIncluded as GalleryUiState.Success).folders.any { it.path == folderPath })
            
        job.cancel()
    }

    @Test
    fun `folder detail preferences with no selected folder use global values and current-folder commits are no-ops`() = runTest {
        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.setSortType(SortType.DATE_TAKEN, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSortOrder(SortOrder.DESCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setColumnsCount(5, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupBy(GroupByType.DATE_TAKEN_MONTHLY, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupOrder(SortOrder.ASCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSelectedMediaTypes(setOf(MediaType.IMAGE, MediaType.VIDEO), PreferenceScope.FOLDER_DETAIL)
        viewModel.setViewType(ViewType.LIST, PreferenceScope.FOLDER_DETAIL)

        val globalBefore = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value

        viewModel.commitFolderSortPreference(SortType.NAME, SortOrder.ASCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderColumnsPreference(9, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderGroupByPreference(GroupByType.FILE_TYPE, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderMediaTypesPreference(setOf(MediaType.GIF), PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderViewTypePreference(ViewType.GRID, PreferenceApplyTarget.CURRENT_FOLDER)

        val globalAfter = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(globalBefore, globalAfter)

        prefsJob.cancel()
    }

    @Test
    fun `folder detail preferences resolve to override for selected folder and back to global for others`() = runTest {
        val folderAPath = "/storage/emulated/0/Pictures/FolderA"
        val folderBPath = "/storage/emulated/0/Pictures/FolderB"
        val folderA = Folder(name = "FolderA", imageCount = 1, thumbnailUri = mockUri1, path = folderAPath)
        val folderB = Folder(name = "FolderB", imageCount = 1, thumbnailUri = mockUri2, path = folderBPath)

        repository.mediaItems = listOf(
            createMediaItem(mockUri1, folderAPath),
            createMediaItem(mockUri2, folderBPath)
        )

        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.setSortType(SortType.NAME, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSortOrder(SortOrder.ASCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setColumnsCount(4, PreferenceScope.FOLDER_DETAIL)

        val globalBefore = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value

        viewModel.selectFolder(folderA)
        viewModel.commitFolderSortPreference(SortType.DATE_TAKEN, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderColumnsPreference(7, PreferenceApplyTarget.CURRENT_FOLDER)

        val folderAPrefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.DATE_TAKEN, folderAPrefs.sortType)
        assertEquals(SortOrder.DESCENDING, folderAPrefs.sortOrder)
        assertEquals(7, folderAPrefs.columns)
        assertEquals(globalBefore.viewType, folderAPrefs.viewType)

        viewModel.selectFolder(folderB)
        val folderBPrefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(globalBefore.sortType, folderBPrefs.sortType)
        assertEquals(globalBefore.sortOrder, folderBPrefs.sortOrder)
        assertEquals(globalBefore.columns, folderBPrefs.columns)

        viewModel.selectFolder(folderA)
        val folderAResolvedAgain = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.DATE_TAKEN, folderAResolvedAgain.sortType)
        assertEquals(SortOrder.DESCENDING, folderAResolvedAgain.sortOrder)
        assertEquals(7, folderAResolvedAgain.columns)

        prefsJob.cancel()
    }

    @Test
    fun `current-folder folder detail commits only affect selected folder and keep global untouched`() = runTest {
        val folderAPath = "/storage/emulated/0/Pictures/FolderA"
        val folderBPath = "/storage/emulated/0/Pictures/FolderB"
        val folderA = Folder(name = "FolderA", imageCount = 1, thumbnailUri = mockUri1, path = folderAPath)
        val folderB = Folder(name = "FolderB", imageCount = 1, thumbnailUri = mockUri2, path = folderBPath)

        repository.mediaItems = listOf(
            createMediaItem(mockUri1, folderAPath),
            createMediaItem(mockUri2, folderBPath)
        )

        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.setSortType(SortType.NAME, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSortOrder(SortOrder.ASCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setColumnsCount(4, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupBy(GroupByType.NONE, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupOrder(SortOrder.DESCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSelectedMediaTypes(MediaType.entries.toSet(), PreferenceScope.FOLDER_DETAIL)
        viewModel.setViewType(ViewType.GRID, PreferenceScope.FOLDER_DETAIL)
        val globalBefore = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value

        viewModel.selectFolder(folderA)
        viewModel.commitFolderSortPreference(SortType.LAST_MODIFIED, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderColumnsPreference(8, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderGroupByPreference(GroupByType.DATE_TAKEN_DAILY, SortOrder.ASCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderMediaTypesPreference(setOf(MediaType.VIDEO), PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderViewTypePreference(ViewType.LIST, PreferenceApplyTarget.CURRENT_FOLDER)

        val folderAPrefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.LAST_MODIFIED, folderAPrefs.sortType)
        assertEquals(SortOrder.DESCENDING, folderAPrefs.sortOrder)
        assertEquals(8, folderAPrefs.columns)
        assertEquals(GroupByType.DATE_TAKEN_DAILY, folderAPrefs.groupBy)
        assertEquals(SortOrder.ASCENDING, folderAPrefs.groupOrder)
        assertEquals(setOf(MediaType.VIDEO), folderAPrefs.mediaTypes)
        assertEquals(ViewType.LIST, folderAPrefs.viewType)

        viewModel.selectFolder(folderB)
        val folderBPrefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(globalBefore, folderBPrefs)

        viewModel.backToFolders()
        assertEquals(globalBefore, viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value)

        prefsJob.cancel()
    }

    @Test
    fun `all-folders folder detail commits update global clear matching overrides and preserve other override groups`() = runTest {
        val folderAPath = "/storage/emulated/0/Pictures/FolderA"
        val folderBPath = "/storage/emulated/0/Pictures/FolderB"
        val folderA = Folder(name = "FolderA", imageCount = 1, thumbnailUri = mockUri1, path = folderAPath)
        val folderB = Folder(name = "FolderB", imageCount = 1, thumbnailUri = mockUri2, path = folderBPath)

        repository.mediaItems = listOf(
            createMediaItem(mockUri1, folderAPath),
            createMediaItem(mockUri2, folderBPath)
        )

        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.setSortType(SortType.NAME, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSortOrder(SortOrder.ASCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setColumnsCount(4, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupBy(GroupByType.NONE, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupOrder(SortOrder.DESCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSelectedMediaTypes(MediaType.entries.toSet(), PreferenceScope.FOLDER_DETAIL)
        viewModel.setViewType(ViewType.GRID, PreferenceScope.FOLDER_DETAIL)

        viewModel.selectFolder(folderA)
        viewModel.commitFolderSortPreference(SortType.DATE_TAKEN, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderColumnsPreference(8, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderGroupByPreference(GroupByType.DATE_TAKEN_MONTHLY, SortOrder.ASCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderMediaTypesPreference(setOf(MediaType.VIDEO), PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderViewTypePreference(ViewType.LIST, PreferenceApplyTarget.CURRENT_FOLDER)

        viewModel.selectFolder(folderB)
        viewModel.commitFolderSortPreference(SortType.LAST_MODIFIED, SortOrder.ASCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderColumnsPreference(6, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderGroupByPreference(GroupByType.LAST_MODIFIED_DAILY, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderMediaTypesPreference(setOf(MediaType.IMAGE), PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderViewTypePreference(ViewType.LIST, PreferenceApplyTarget.CURRENT_FOLDER)

        viewModel.selectFolder(folderA)
        viewModel.commitFolderSortPreference(SortType.RANDOM, SortOrder.ASCENDING, PreferenceApplyTarget.ALL_FOLDERS)
        val folderASortCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.RANDOM, folderASortCleared.sortType)
        assertEquals(SortOrder.ASCENDING, folderASortCleared.sortOrder)
        assertEquals(8, folderASortCleared.columns)

        viewModel.selectFolder(folderB)
        val folderBSortCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.RANDOM, folderBSortCleared.sortType)
        assertEquals(SortOrder.ASCENDING, folderBSortCleared.sortOrder)
        assertEquals(6, folderBSortCleared.columns)

        viewModel.selectFolder(folderA)
        viewModel.commitFolderColumnsPreference(3, PreferenceApplyTarget.ALL_FOLDERS)
        val folderAColumnsCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(3, folderAColumnsCleared.columns)
        assertEquals(GroupByType.DATE_TAKEN_MONTHLY, folderAColumnsCleared.groupBy)

        viewModel.selectFolder(folderB)
        val folderBColumnsCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(3, folderBColumnsCleared.columns)
        assertEquals(GroupByType.LAST_MODIFIED_DAILY, folderBColumnsCleared.groupBy)

        viewModel.selectFolder(folderA)
        viewModel.commitFolderGroupByPreference(GroupByType.FILE_TYPE, SortOrder.ASCENDING, PreferenceApplyTarget.ALL_FOLDERS)
        val folderAGroupCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(GroupByType.FILE_TYPE, folderAGroupCleared.groupBy)
        assertEquals(SortOrder.ASCENDING, folderAGroupCleared.groupOrder)
        assertEquals(setOf(MediaType.VIDEO), folderAGroupCleared.mediaTypes)

        viewModel.selectFolder(folderB)
        val folderBGroupCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(GroupByType.FILE_TYPE, folderBGroupCleared.groupBy)
        assertEquals(SortOrder.ASCENDING, folderBGroupCleared.groupOrder)
        assertEquals(setOf(MediaType.IMAGE), folderBGroupCleared.mediaTypes)

        viewModel.selectFolder(folderA)
        viewModel.commitFolderMediaTypesPreference(setOf(MediaType.GIF), PreferenceApplyTarget.ALL_FOLDERS)
        val folderAMediaTypesCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(setOf(MediaType.GIF), folderAMediaTypesCleared.mediaTypes)
        assertEquals(ViewType.LIST, folderAMediaTypesCleared.viewType)

        viewModel.selectFolder(folderB)
        val folderBMediaTypesCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(setOf(MediaType.GIF), folderBMediaTypesCleared.mediaTypes)
        assertEquals(ViewType.LIST, folderBMediaTypesCleared.viewType)

        viewModel.selectFolder(folderA)
        viewModel.commitFolderViewTypePreference(ViewType.GRID, PreferenceApplyTarget.ALL_FOLDERS)
        val folderAViewTypeCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(ViewType.GRID, folderAViewTypeCleared.viewType)

        viewModel.selectFolder(folderB)
        val folderBViewTypeCleared = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(ViewType.GRID, folderBViewTypeCleared.viewType)

        viewModel.backToFolders()
        val globalFinal = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.RANDOM, globalFinal.sortType)
        assertEquals(SortOrder.ASCENDING, globalFinal.sortOrder)
        assertEquals(3, globalFinal.columns)
        assertEquals(GroupByType.FILE_TYPE, globalFinal.groupBy)
        assertEquals(SortOrder.ASCENDING, globalFinal.groupOrder)
        assertEquals(setOf(MediaType.GIF), globalFinal.mediaTypes)
        assertEquals(ViewType.GRID, globalFinal.viewType)

        prefsJob.cancel()
    }

    @Test
    fun `folder media derived flows react to per-folder-only override changes`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/FolderA"
        val folder = Folder(name = "FolderA", imageCount = 2, thumbnailUri = mockUri1, path = folderPath)
        val first = createMediaItem(mockUri1, folderPath).copy(
            name = "b.jpg",
            dateAdded = 1_000L,
            dateModified = 1_000L,
            type = MediaType.IMAGE
        )
        val second = createMediaItem(mockUri2, folderPath).copy(
            name = "a.jpg",
            dateAdded = 2_000_000L,
            dateModified = 2_000_000L,
            type = MediaType.VIDEO
        )
        repository.mediaItems = listOf(first, second)

        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }
        val filteredMediaJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredMedia.collect {}
        }
        val groupedMediaJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.groupedFolderMedia.collect {}
        }

        viewModel.setSortType(SortType.NAME, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSortOrder(SortOrder.ASCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupBy(GroupByType.NONE, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupOrder(SortOrder.DESCENDING, PreferenceScope.FOLDER_DETAIL)

        viewModel.selectFolder(folder)
        assertEquals(listOf("a.jpg", "b.jpg"), viewModel.filteredMedia.value.map { it.name })
        assertEquals(setOf(""), viewModel.groupedFolderMedia.value.keys)

        viewModel.commitFolderSortPreference(SortType.NAME, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        assertEquals(listOf("b.jpg", "a.jpg"), viewModel.filteredMedia.value.map { it.name })

        viewModel.commitFolderGroupByPreference(GroupByType.DATE_TAKEN_DAILY, SortOrder.ASCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        assertTrue(viewModel.groupedFolderMedia.value.keys.size >= 2)

        viewModel.backToFolders()
        val globalAfter = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.NAME, globalAfter.sortType)
        assertEquals(SortOrder.ASCENDING, globalAfter.sortOrder)
        assertEquals(GroupByType.NONE, globalAfter.groupBy)

        prefsJob.cancel()
        filteredMediaJob.cancel()
        groupedMediaJob.cancel()
    }

    @Test
    fun `pinch zoom columns in folder detail write per-folder override not global`() = runTest {
        val folderAPath = "/storage/emulated/0/Pictures/FolderA"
        val folderBPath = "/storage/emulated/0/Pictures/FolderB"
        val folderA = Folder(name = "FolderA", imageCount = 1, thumbnailUri = mockUri1, path = folderAPath)
        val folderB = Folder(name = "FolderB", imageCount = 1, thumbnailUri = mockUri2, path = folderBPath)

        repository.mediaItems = listOf(
            createMediaItem(mockUri1, folderAPath),
            createMediaItem(mockUri2, folderBPath)
        )

        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.setColumnsCount(4, PreferenceScope.FOLDER_DETAIL)

        viewModel.selectFolder(folderA)
        viewModel.increaseColumns(PreferenceScope.FOLDER_DETAIL)
        val folderAAfterIncrease = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(5, folderAAfterIncrease.columns)

        viewModel.backToFolders()
        val globalAfterIncrease = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(4, globalAfterIncrease.columns)

        viewModel.selectFolder(folderB)
        val folderBPrefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(4, folderBPrefs.columns)

        viewModel.selectFolder(folderA)
        viewModel.decreaseColumns(PreferenceScope.FOLDER_DETAIL)
        val folderAAfterDecrease = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(4, folderAAfterDecrease.columns)

        viewModel.backToFolders()
        val globalAfterDecrease = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(4, globalAfterDecrease.columns)

        prefsJob.cancel()
    }

    @Test
    fun `folder overrides do not affect folders or timeline preference scopes`() = runTest {
        val folderPath = "/storage/emulated/0/Pictures/FolderA"
        val folder = Folder(name = "FolderA", imageCount = 1, thumbnailUri = mockUri1, path = folderPath)
        repository.mediaItems = listOf(createMediaItem(mockUri1, folderPath))

        val viewModel = createViewModel()
        val folderDetailPrefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.setSortType(SortType.PATH, PreferenceScope.FOLDERS)
        viewModel.setColumnsCount(6, PreferenceScope.FOLDERS)
        viewModel.setGroupBy(GroupByType.LAST_MODIFIED_MONTHLY, PreferenceScope.TIMELINE)
        viewModel.setSortOrder(SortOrder.ASCENDING, PreferenceScope.TIMELINE)

        val foldersBefore = viewModel.preferences(PreferenceScope.FOLDERS).value
        val timelineBefore = viewModel.preferences(PreferenceScope.TIMELINE).value

        viewModel.selectFolder(folder)
        viewModel.commitFolderSortPreference(SortType.DATE_TAKEN, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderColumnsPreference(9, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderGroupByPreference(GroupByType.DATE_TAKEN_DAILY, SortOrder.ASCENDING, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderMediaTypesPreference(setOf(MediaType.VIDEO), PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderViewTypePreference(ViewType.LIST, PreferenceApplyTarget.CURRENT_FOLDER)
        viewModel.commitFolderSortPreference(SortType.RANDOM, SortOrder.ASCENDING, PreferenceApplyTarget.ALL_FOLDERS)

        val foldersAfter = viewModel.preferences(PreferenceScope.FOLDERS).value
        val timelineAfter = viewModel.preferences(PreferenceScope.TIMELINE).value

        assertEquals(foldersBefore, foldersAfter)
        assertEquals(timelineBefore, timelineAfter)

        folderDetailPrefsJob.cancel()
    }

    @Test
    fun `camera folder with no overrides resolves to date taken descending`() = runTest {
        val cameraFolderPath = "/storage/emulated/0/DCIM/Camera"
        val cameraFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = cameraFolderPath)
        repository.mediaItems = listOf(createMediaItem(mockUri1, cameraFolderPath))

        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.selectFolder(cameraFolder)

        val prefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.DATE_TAKEN, prefs.sortType)
        assertEquals(SortOrder.DESCENDING, prefs.sortOrder)

        prefsJob.cancel()
    }

    @Test
    fun `non-camera folder keeps name ascending default`() = runTest {
        val nonCameraFolderPath = "/storage/emulated/0/Pictures/Camera"
        val nonCameraFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = nonCameraFolderPath)
        repository.mediaItems = listOf(createMediaItem(mockUri1, nonCameraFolderPath))

        val viewModel = createViewModel()
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.selectFolder(nonCameraFolder)

        val prefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.NAME, prefs.sortType)
        assertEquals(SortOrder.ASCENDING, prefs.sortOrder)

        prefsJob.cancel()
    }

    @Test
    fun `explicit per-folder sort override wins over implicit camera default`() = runTest {
        val cameraFolderPath = "/storage/emulated/0/DCIM/Camera"
        val cameraFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = cameraFolderPath)
        val folderViewPreferencesStore = InMemoryFolderViewPreferencesStore().apply {
            save(
                cameraFolderPath,
                FolderViewOverrides(
                    sortType = SortType.LAST_MODIFIED,
                    sortOrder = SortOrder.ASCENDING
                )
            )
        }
        repository.mediaItems = listOf(createMediaItem(mockUri1, cameraFolderPath))

        val viewModel = createViewModel(folderViewPreferencesStore = folderViewPreferencesStore)
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.selectFolder(cameraFolder)

        val prefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.LAST_MODIFIED, prefs.sortType)
        assertEquals(SortOrder.ASCENDING, prefs.sortOrder)

        prefsJob.cancel()
    }

    @Test
    fun `all-folders sort commit disables camera implicit default on reselection`() = runTest {
        val cameraFolderPath = "/storage/emulated/0/DCIM/Camera"
        val cameraFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = cameraFolderPath)
        repository.mediaItems = listOf(createMediaItem(mockUri1, cameraFolderPath))

        val viewModel = createViewModel(displayPreferencesStore = InMemoryDisplayPreferencesStore())
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.selectFolder(cameraFolder)
        assertEquals(SortType.DATE_TAKEN, viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value.sortType)
        assertEquals(SortOrder.DESCENDING, viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value.sortOrder)

        viewModel.commitFolderSortPreference(SortType.NAME, SortOrder.ASCENDING, PreferenceApplyTarget.ALL_FOLDERS)
        viewModel.backToFolders()
        viewModel.selectFolder(cameraFolder)

        val prefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.NAME, prefs.sortType)
        assertEquals(SortOrder.ASCENDING, prefs.sortOrder)

        prefsJob.cancel()
    }

    @Test
    fun `implicit camera default only affects sort fields`() = runTest {
        val cameraFolderPath = "/storage/emulated/0/DCIM/Camera"
        val cameraFolder = Folder(name = "Camera", imageCount = 1, thumbnailUri = mockUri1, path = cameraFolderPath)
        repository.mediaItems = listOf(createMediaItem(mockUri1, cameraFolderPath))

        val viewModel = createViewModel(displayPreferencesStore = InMemoryDisplayPreferencesStore())
        val prefsJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.preferences(PreferenceScope.FOLDER_DETAIL).collect {}
        }

        viewModel.setColumnsCount(5, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupBy(GroupByType.FILE_TYPE, PreferenceScope.FOLDER_DETAIL)
        viewModel.setGroupOrder(SortOrder.ASCENDING, PreferenceScope.FOLDER_DETAIL)
        viewModel.setViewType(ViewType.LIST, PreferenceScope.FOLDER_DETAIL)
        viewModel.setSelectedMediaTypes(setOf(MediaType.VIDEO), PreferenceScope.FOLDER_DETAIL)

        viewModel.selectFolder(cameraFolder)

        val prefs = viewModel.preferences(PreferenceScope.FOLDER_DETAIL).value
        assertEquals(SortType.DATE_TAKEN, prefs.sortType)
        assertEquals(SortOrder.DESCENDING, prefs.sortOrder)
        assertEquals(5, prefs.columns)
        assertEquals(GroupByType.FILE_TYPE, prefs.groupBy)
        assertEquals(SortOrder.ASCENDING, prefs.groupOrder)
        assertEquals(ViewType.LIST, prefs.viewType)
        assertEquals(setOf(MediaType.VIDEO), prefs.mediaTypes)

        prefsJob.cancel()
    }

    @Test
    fun `filtered media in camera folder is date added descending by default`() = runTest {
        val cameraFolderPath = "/storage/emulated/0/DCIM/Camera"
        val cameraFolder = Folder(name = "Camera", imageCount = 3, thumbnailUri = mockUri1, path = cameraFolderPath)
        val mockUri3 = mockk<Uri>(relaxed = true)
        repository.mediaItems = listOf(
            createMediaItem(mockUri1, cameraFolderPath).copy(name = "z.jpg", dateAdded = 1_000L),
            createMediaItem(mockUri2, cameraFolderPath).copy(name = "a.jpg", dateAdded = 5_000L),
            createMediaItem(mockUri3, cameraFolderPath).copy(name = "m.jpg", dateAdded = 3_000L)
        )

        val viewModel = createViewModel()
        val filteredMediaJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filteredMedia.collect {}
        }

        viewModel.selectFolder(cameraFolder)

        assertEquals(listOf(5_000L, 3_000L, 1_000L), viewModel.filteredMedia.value.map { it.dateAdded })

        filteredMediaJob.cancel()
    }
}
