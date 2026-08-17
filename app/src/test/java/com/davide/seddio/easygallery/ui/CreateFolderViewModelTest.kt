package com.davide.seddio.easygallery.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import com.davide.seddio.easygallery.data.FakeMediaRepository
import com.davide.seddio.easygallery.data.Folder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CreateFolderViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val repository = FakeMediaRepository()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockUri = mockk<Uri>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Environment::class)
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.absolutePath } returns "/storage/emulated/0"
        every { Environment.getExternalStorageDirectory() } returns mockFile
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createFolder with blank name sets error`() = runTest {
        val viewModel = CreateFolderViewModel(application, repository)

        viewModel.createFolder("")

        assertEquals("Folder name cannot be empty", viewModel.error.value)
    }

    @Test
    fun `createFolder with invalid characters sets error`() = runTest {
        val viewModel = CreateFolderViewModel(application, repository)

        viewModel.createFolder("Folder/Name")

        assertEquals("Invalid characters in folder name", viewModel.error.value)
    }

    @Test
    fun `createFolder with existing path sets error`() = runTest {
        val rootPath = "/storage/emulated/0"
        val parentPath = "$rootPath/Pictures"
        val folderPath = "$parentPath/Existing"

        repository.folders = listOf(Folder(name = "Existing", imageCount = 0, thumbnailUri = mockUri, path = folderPath))

        val viewModel = CreateFolderViewModel(application, repository)
        viewModel.setDialogOpen(true) // Resets path to root
        viewModel.updateBrowsingPath(parentPath)

        viewModel.createFolder("Existing")

        assertEquals("Folder already exists", viewModel.error.value)
    }

    @Test
    fun `successful createFolder closes the dialog and clears error`() = runTest {
        val viewModel = CreateFolderViewModel(application, repository)

        viewModel.createFolder("NewFolder")

        assertFalse(viewModel.isDialogOpen.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `navigating in create folder dialog updates browsing path`() = runTest {
        val viewModel = CreateFolderViewModel(application, repository)
        val initialPath = "/storage/emulated/0"
        val newPath = "/storage/emulated/0/DCIM"

        viewModel.setDialogOpen(true)
        assertEquals(initialPath, viewModel.browsingPath.value)

        viewModel.updateBrowsingPath(newPath)
        assertEquals(newPath, viewModel.browsingPath.value)
    }

    @Test
    fun `createFolder uses current browsing path as parent`() = runTest {
        val viewModel = CreateFolderViewModel(application, repository)
        val parentPath = "/storage/emulated/0/Custom"

        viewModel.setDialogOpen(true)
        viewModel.updateBrowsingPath(parentPath)

        viewModel.createFolder("MySubFolder")

        assertFalse(viewModel.isDialogOpen.value)
    }
}
