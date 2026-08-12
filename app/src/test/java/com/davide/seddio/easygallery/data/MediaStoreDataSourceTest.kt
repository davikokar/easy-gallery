package com.davide.seddio.easygallery.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaStoreDataSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    private lateinit var dataSource: MediaStoreDataSource

    @Before
    fun setup() {
        every { context.contentResolver } returns contentResolver
        dataSource = MediaStoreDataSource(context)
    }

    // region File-based operations (real File I/O against a temp directory)

    @Test
    fun `folderExists returns true for an existing directory`() {
        val dir = tempFolder.newFolder("existing")
        assertTrue(dataSource.folderExists(dir.absolutePath))
    }

    @Test
    fun `folderExists returns false for a missing path`() {
        val missing = File(tempFolder.root, "does-not-exist")
        assertFalse(dataSource.folderExists(missing.absolutePath))
    }

    @Test
    fun `getSubdirectories returns only non-hidden directories sorted by name`() {
        tempFolder.newFolder("charlie")
        tempFolder.newFolder("alpha")
        tempFolder.newFolder("bravo")
        tempFolder.newFile("a-file.txt")

        val result = dataSource.getSubdirectories(tempFolder.root.absolutePath)

        assertEquals(listOf("alpha", "bravo", "charlie"), result.map { it.name })
    }

    @Test
    fun `getSubdirectories returns empty list for a non-existent path`() {
        val missing = File(tempFolder.root, "nope").absolutePath
        assertTrue(dataSource.getSubdirectories(missing).isEmpty())
    }

    @Test
    fun `getSubdirectories returns empty list when the path is a file`() {
        val file = tempFolder.newFile("plain.txt")
        assertTrue(dataSource.getSubdirectories(file.absolutePath).isEmpty())
    }

    @Test
    fun `createFolder succeeds when the folder does not yet exist`() = runTest {
        val result = dataSource.createFolder(tempFolder.root.absolutePath, "new-folder")

        assertTrue(result.isSuccess)
        assertTrue(File(tempFolder.root, "new-folder").isDirectory)
    }

    @Test
    fun `createFolder fails when the folder already exists`() = runTest {
        tempFolder.newFolder("already-there")

        val result = dataSource.createFolder(tempFolder.root.absolutePath, "already-there")

        assertTrue(result.isFailure)
        assertEquals("Folder already exists", result.exceptionOrNull()?.message)
    }

    @Test
    fun `copyFile copies file content into the target folder`() = runTest {
        val source = tempFolder.newFolder("src")
        val target = tempFolder.newFolder("dst")
        File(source, "photo.jpg").writeText("hello world")

        dataSource.copyFile(source.absolutePath, "photo.jpg", target.absolutePath)

        val copied = File(target, "photo.jpg")
        assertTrue(copied.exists())
        assertEquals("hello world", copied.readText())
    }

    @Test
    fun `copyFolderContents recursively copies files and subdirectories`() = runTest {
        val source = tempFolder.newFolder("album")
        File(source, "one.jpg").writeText("1")
        val nested = File(source, "nested").apply { mkdirs() }
        File(nested, "two.jpg").writeText("2")
        val targetParent = tempFolder.newFolder("target")

        dataSource.copyFolderContents(source.absolutePath, targetParent.absolutePath)

        val copiedRoot = File(targetParent, "album")
        assertEquals("1", File(copiedRoot, "one.jpg").readText())
        assertEquals("2", File(File(copiedRoot, "nested"), "two.jpg").readText())
    }

    // endregion

    // region ContentResolver operations

    @Test
    fun `deleteMediaItems deletes every uri through the content resolver`() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()

        dataSource.deleteMediaItems(listOf(uri1, uri2))

        verify(exactly = 1) { contentResolver.delete(uri1, null, null) }
        verify(exactly = 1) { contentResolver.delete(uri2, null, null) }
    }

    @Test
    fun `updateMediaRelativePath updates every uri with the target relative path`() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()

        dataSource.updateMediaRelativePath(listOf(uri1, uri2), "DCIM/Camera/")

        verify(exactly = 1) { contentResolver.update(uri1, any(), null, null) }
        verify(exactly = 1) { contentResolver.update(uri2, any(), null, null) }
    }

    // endregion

    // region Cursor parsing

    @Test
    fun `getFolders aggregates media into folders with counts sizes and dates`() = runTest {
        val imageCursor = fakeCursor(
            folderColumns,
            listOf(
                folderRow(id = 1, bucket = "Camera", added = 100, data = "/storage/emulated/0/DCIM/Camera/a.jpg", size = 10, modified = 50),
                folderRow(id = 2, bucket = "Camera", added = 200, data = "/storage/emulated/0/DCIM/Camera/b.jpg", size = 20, modified = 80),
                folderRow(id = 3, bucket = "Downloads", added = 150, data = "/storage/emulated/0/Download/c.jpg", size = 30, modified = 60)
            )
        )
        val videoCursor = fakeCursor(folderColumns, emptyList())
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returnsMany listOf(imageCursor, videoCursor)

        val folders = dataSource.getFolders()

        assertEquals(2, folders.size)
        val camera = folders.first { it.name == "Camera" }
        assertEquals(2, camera.imageCount)
        assertEquals(30L, camera.size)
        assertEquals(80L, camera.dateModified)
        assertEquals(200L, camera.dateTaken)
        assertEquals("/storage/emulated/0/DCIM/Camera", camera.path)

        val downloads = folders.first { it.name == "Downloads" }
        assertEquals(1, downloads.imageCount)
        assertEquals(30L, downloads.size)
    }

    @Test
    fun `getMediaInFolder maps types and sorts by dateAdded descending`() = runTest {
        val imageCursor = fakeCursor(
            imageColumns,
            listOf(
                mediaRow(id = 1, name = "pic.jpg", bucket = "Camera", added = 100, modified = 50, mime = "image/jpeg", data = "/storage/emulated/0/DCIM/Camera/pic.jpg", size = 10),
                mediaRow(id = 2, name = "anim.gif", bucket = "Camera", added = 300, modified = 70, mime = "image/gif", data = "/storage/emulated/0/DCIM/Camera/anim.gif", size = 5)
            )
        )
        val videoCursor = fakeCursor(
            videoColumns,
            listOf(
                mediaRow(id = 3, name = "clip.mp4", bucket = "Camera", added = 200, modified = 60, mime = "video/mp4", data = "/storage/emulated/0/DCIM/Camera/clip.mp4", size = 100, duration = 5000)
            )
        )
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returnsMany listOf(imageCursor, videoCursor)

        val media = dataSource.getMediaInFolder("Camera")

        assertEquals(listOf("anim.gif", "clip.mp4", "pic.jpg"), media.map { it.name })
        assertEquals(listOf(MediaType.GIF, MediaType.VIDEO, MediaType.IMAGE), media.map { it.type })
        assertEquals("/storage/emulated/0/DCIM/Camera", media.first().folderPath)
        assertEquals(5000L, media.first { it.type == MediaType.VIDEO }.duration)
        assertNull(media.first { it.type == MediaType.IMAGE }.duration)
    }

    @Test
    fun `getAllMedia combines images and videos sorted by dateAdded descending`() = runTest {
        val imageCursor = fakeCursor(
            imageColumns,
            listOf(
                mediaRow(id = 1, name = "old.jpg", bucket = "Camera", added = 100, modified = 50, mime = "image/jpeg", data = "/a/old.jpg", size = 10)
            )
        )
        val videoCursor = fakeCursor(
            videoColumns,
            listOf(
                mediaRow(id = 2, name = "new.mp4", bucket = "Camera", added = 500, modified = 60, mime = "video/mp4", data = "/a/new.mp4", size = 100, duration = 1000)
            )
        )
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returnsMany listOf(imageCursor, videoCursor)

        val media = dataSource.getAllMedia()

        assertEquals(listOf("new.mp4", "old.jpg"), media.map { it.name })
    }

    // endregion

    // region Fake cursor helpers

    private val folderColumns = mapOf(
        MediaStore.MediaColumns._ID to 0,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME to 1,
        MediaStore.MediaColumns.DATE_ADDED to 2,
        MediaStore.MediaColumns.DATA to 3,
        MediaStore.MediaColumns.SIZE to 4,
        MediaStore.MediaColumns.DATE_MODIFIED to 5
    )

    private fun folderRow(id: Long, bucket: String, added: Long, data: String, size: Long, modified: Long) =
        mapOf<Int, Any?>(0 to id, 1 to bucket, 2 to added, 3 to data, 4 to size, 5 to modified)

    private val imageColumns = mapOf(
        MediaStore.MediaColumns._ID to 0,
        MediaStore.MediaColumns.DISPLAY_NAME to 1,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME to 2,
        MediaStore.MediaColumns.DATE_ADDED to 3,
        MediaStore.MediaColumns.DATE_MODIFIED to 4,
        MediaStore.MediaColumns.MIME_TYPE to 5,
        MediaStore.MediaColumns.DATA to 6,
        MediaStore.MediaColumns.SIZE to 7
    )

    private val videoColumns = imageColumns + (MediaStore.Video.Media.DURATION to 8)

    private fun mediaRow(
        id: Long,
        name: String,
        bucket: String,
        added: Long,
        modified: Long,
        mime: String,
        data: String,
        size: Long,
        duration: Long? = null
    ) = mapOf<Int, Any?>(
        0 to id,
        1 to name,
        2 to bucket,
        3 to added,
        4 to modified,
        5 to mime,
        6 to data,
        7 to size,
        8 to duration
    )

    private fun fakeCursor(columns: Map<String, Int>, rows: List<Map<Int, Any?>>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        columns.forEach { (name, index) ->
            every { cursor.getColumnIndexOrThrow(name) } returns index
        }
        var position = -1
        every { cursor.moveToNext() } answers {
            position++
            position < rows.size
        }
        every { cursor.getLong(any()) } answers {
            (rows.getOrNull(position)?.get(firstArg<Int>()) as? Long) ?: 0L
        }
        every { cursor.getString(any()) } answers {
            rows.getOrNull(position)?.get(firstArg<Int>()) as? String
        }
        return cursor
    }

    // endregion
}
