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
    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockUri1 = mockk<Uri>()
    private val mockUri2 = mockk<Uri>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()

        mockkStatic(Environment::class)
        val mockFile = mockk<File>()
        every { mockFile.absolutePath } returns "/storage/emulated/0"
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
        val viewModel = GalleryViewModel(application, repository)
        val folderPath = "/storage/emulated/0/Pictures"
        
        viewModel.enterSelectionMode(folderPath)
        
        assertTrue(viewModel.isSelectionMode.value)
        assertEquals(setOf(folderPath), viewModel.selectedFolders.value)
    }

    @Test
    fun `selecting a second folder adds it to selection`() = runTest {
        val viewModel = GalleryViewModel(application, repository)
        val folder1 = "/path/1"
        val folder2 = "/path/2"
        
        viewModel.enterSelectionMode(folder1)
        viewModel.toggleSelection(folder2)
        
        assertEquals(setOf(folder1, folder2), viewModel.selectedFolders.value)
    }

    @Test
    fun `selecting the last selected folder exits selection mode`() = runTest {
        val viewModel = GalleryViewModel(application, repository)
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
        
        val viewModel = GalleryViewModel(application, repository)
        
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
        val viewModel = GalleryViewModel(application, repository)
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        
        assertTrue(viewModel.isMediaSelectionMode.value)
        assertEquals(setOf(mockUri1), viewModel.selectedMediaItems.value)
    }

    @Test
    fun `exiting media selection clears selected media`() = runTest {
        val viewModel = GalleryViewModel(application, repository)
        val item = createMediaItem(mockUri1, "/path")
        
        viewModel.enterMediaSelectionMode(item)
        viewModel.exitMediaSelectionMode()
        
        assertFalse(viewModel.isMediaSelectionMode.value)
        assertTrue(viewModel.selectedMediaItems.value.isEmpty())
    }

    @Test
    fun `exiting folder selection clears selected folders`() = runTest {
        val viewModel = GalleryViewModel(application, repository)
        
        viewModel.enterSelectionMode("/path")
        viewModel.exitSelectionMode()
        
        assertFalse(viewModel.isSelectionMode.value)
        assertTrue(viewModel.selectedFolders.value.isEmpty())
    }
}
