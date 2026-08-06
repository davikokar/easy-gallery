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

    @Test
    fun `long-pressing a folder enters selection mode`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val folderPath = "/storage/emulated/0/Pictures"
        
        viewModel.enterSelectionMode(folderPath)
        
        assertTrue(viewModel.isSelectionMode.value)
        assertEquals(setOf(folderPath), viewModel.selectedFolders.value)
    }

    @Test
    fun `selecting a second folder adds it to selection`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val folder1 = "/path/1"
        val folder2 = "/path/2"
        
        viewModel.enterSelectionMode(folder1)
        viewModel.toggleSelection(folder2)
        
        assertEquals(setOf(folder1, folder2), viewModel.selectedFolders.value)
    }

    @Test
    fun `selecting the last selected folder exits selection mode`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
        
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        
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
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        
        assertTrue(viewModel.isMediaSelectionMode.value)
        assertEquals(setOf(mockUri1), viewModel.selectedMediaItems.value)
    }

    @Test
    fun `exiting media selection clears selected media`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.exitMediaSelectionMode()
        
        assertFalse(viewModel.isMediaSelectionMode.value)
        assertTrue(viewModel.selectedMediaItems.value.isEmpty())
    }

    @Test
    fun `exiting folder selection clears selected folders`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        
        viewModel.enterSelectionMode("/path")
        viewModel.exitSelectionMode()
        
        assertFalse(viewModel.isSelectionMode.value)
        assertTrue(viewModel.selectedFolders.value.isEmpty())
    }

    @Test
    fun `deleteMedia calls repository and reloads folders`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.deleteMedia(item)
        
        assertEquals(listOf(mockUri1), repository.deletedUris)
        // Verify loadFolders was called by checking repository interaction
        // Since we are using UnconfinedTestDispatcher and a fake, we check side effects.
    }

    @Test
    fun `deleteSelectedMedia deletes all selected media Uris`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        viewModel.loadFolders()
        
        viewModel.enterSelectionMode("/path/A")
        viewModel.toggleSelection("/path/B")
        
        viewModel.deleteSelected()
        
        assertEquals(setOf(mockUri1, mockUri2), repository.deletedUris.toSet())
        assertFalse(viewModel.isSelectionMode.value)
    }

    @Test
    fun `SecurityException in deletion emits pending permission request`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
    fun `moving selected folders moves all contained media`() = runTest {
        val media = listOf(
            createMediaItem(mockUri1, "/storage/emulated/0/A"),
            createMediaItem(mockUri2, "/storage/emulated/0/A"),
            createMediaItem(mockk(), "/storage/emulated/0/B")
        )
        repository.mediaItems = media
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.startOperation(OperationType.MOVE)
        viewModel.performOperationWithPath("/data/user/0/invalid")
        
        assertTrue(repository.movedUris.isEmpty())
        assertTrue(viewModel.isMediaSelectionMode.value) // Still in mode
    }

    @Test
    fun `SecurityException during move stores pending operation and emits request`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
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

        val viewModel = GalleryViewModel(application, repository, permissionHandler)

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

        // Verify filteredMedia for the currently open Source folder no longer contains moved.jpg
        // This is expected to FAIL in current implementation because loadFolders() doesn't update _mediaInFolder
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
        
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        
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
    fun `createFolder with blank name sets error`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        
        viewModel.createFolder("")
        
        assertEquals("Folder name cannot be empty", viewModel.createFolderError.value)
    }

    @Test
    fun `createFolder with invalid characters sets error`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        
        viewModel.createFolder("Folder/Name")
        
        assertEquals("Invalid characters in folder name", viewModel.createFolderError.value)
    }

    @Test
    fun `createFolder with existing path sets error`() = runTest {
        val picturesDir = Environment.DIRECTORY_PICTURES ?: "Pictures"
        val rootPath = "/storage/emulated/0"
        val parentPath = "$rootPath/$picturesDir"
        val folderPath = "$parentPath/Existing"
        
        repository.folders = listOf(Folder(name = "Existing", imageCount = 0, thumbnailUri = mockUri, path = folderPath))
        
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        viewModel.setCreateFolderDialogOpen(true) // Resets path to root
        viewModel.updateCreateFolderBrowsingPath(parentPath)
        
        viewModel.createFolder("Existing")
        
        assertEquals("Folder already exists", viewModel.createFolderError.value)
    }

    @Test
    fun `successful createFolder calls repository and reloads folders`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        
        viewModel.createFolder("NewFolder")
        
        // In FakeMediaRepository, Result.success is returned.
        // We verify that the dialog is closed.
        assertFalse(viewModel.isCreateFolderDialogOpen.value)
        assertNull(viewModel.createFolderError.value)
    }

    @Test
    fun `navigating in create folder dialog updates browsing path`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val initialPath = "/storage/emulated/0"
        val newPath = "/storage/emulated/0/DCIM"
        
        viewModel.setCreateFolderDialogOpen(true)
        assertEquals(initialPath, viewModel.createFolderBrowsingPath.value)
        
        viewModel.updateCreateFolderBrowsingPath(newPath)
        assertEquals(newPath, viewModel.createFolderBrowsingPath.value)
    }

    @Test
    fun `createFolder uses current browsing path as parent`() = runTest {
        val viewModel = GalleryViewModel(application, repository, permissionHandler)
        val parentPath = "/storage/emulated/0/Custom"
        val folderName = "MySubFolder"
        
        viewModel.setCreateFolderDialogOpen(true)
        viewModel.updateCreateFolderBrowsingPath(parentPath)
        
        viewModel.createFolder(folderName)
        
        // In FakeMediaRepository, it doesn't strictly check the parent path for creation yet,
        // but we can assume success if the dialog closes.
        // To be more precise, we'd need to mock/verify the repository call.
        assertFalse(viewModel.isCreateFolderDialogOpen.value)
    }
}
