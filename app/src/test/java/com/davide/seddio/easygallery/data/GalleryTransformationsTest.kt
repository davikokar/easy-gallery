package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GalleryTransformationsTest {

    private val mockUri1 = mockk<Uri>()
    private val mockUri2 = mockk<Uri>()
    private val mockUri3 = mockk<Uri>()

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()
    }

    private fun createMediaItem(
        uri: Uri = mockUri1,
        name: String = "image.jpg",
        dateAdded: Long = 1000L,
        folderPath: String = "/storage/emulated/0/Pictures",
        type: MediaType = MediaType.IMAGE,
        size: Long = 100L
    ) = MediaItem(
        uri = uri,
        name = name,
        dateAdded = dateAdded,
        dateModified = dateAdded,
        size = size,
        type = type,
        bucketName = folderPath.substringAfterLast("/"),
        folderPath = folderPath
    )

    @Test
    fun `media type filtering updates folder counts`() {
        val media = listOf(
            createMediaItem(mockUri1, type = MediaType.IMAGE),
            createMediaItem(mockUri2, type = MediaType.IMAGE),
            createMediaItem(mockUri3, type = MediaType.VIDEO)
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = setOf(MediaType.IMAGE)
        )

        assertEquals(1, folders.size)
        assertEquals(2, folders[0].imageCount)
    }

    @Test
    fun `pinned folders are sorted first and have isPinned true`() {
        val media = listOf(
            createMediaItem(mockUri1, folderPath = "/a"),
            createMediaItem(mockUri2, folderPath = "/b")
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = setOf("/b"),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet()
        )

        assertEquals(2, folders.size)
        assertEquals("/b", folders[0].path)
        assertTrue(folders[0].isPinned)
        assertFalse(folders[1].isPinned)
    }

    @Test
    fun `excluded folders are hidden unless showExcludedTemporarily is true`() {
        val media = listOf(
            createMediaItem(mockUri1, folderPath = "/excluded"),
            createMediaItem(mockUri2, folderPath = "/included")
        )

        // Test hidden
        val foldersHidden = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = setOf("/excluded"),
            showExcluded = false,
            types = MediaType.entries.toSet()
        )
        assertEquals(1, foldersHidden.size)
        assertEquals("/included", foldersHidden[0].path)

        // Test shown
        val foldersShown = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = setOf("/excluded"),
            showExcluded = true,
            types = MediaType.entries.toSet()
        )
        assertEquals(2, foldersShown.size)
    }

    @Test
    fun `sorting by name works`() {
        val media = listOf(
            createMediaItem(mockUri1, folderPath = "/B"),
            createMediaItem(mockUri2, folderPath = "/A")
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet()
        )

        assertEquals("A", folders[0].name)
        assertEquals("B", folders[1].name)
    }

    @Test
    fun `sorting by size works`() {
        val media = listOf(
            createMediaItem(mockUri1, folderPath = "/small", size = 10L),
            createMediaItem(mockUri2, folderPath = "/large", size = 100L)
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.SIZE,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet()
        )

        // The original logic sorts SIZE/DATE/MODIFIED descending by default and reverses if order is DESCENDING
        // So ASCENDING for SIZE results in Descending order (large first)
        assertEquals("large", folders[0].name) 
        assertEquals("small", folders[1].name)
    }

    @Test
    fun `absolute external-storage path converts to MediaStore RELATIVE_PATH correctly`() {
        val root = "/storage/emulated/0"
        
        // Root directory
        assertEquals("", GalleryTransformations.absoluteToRelativePath("/storage/emulated/0", root))
        
        // Single level
        assertEquals("Pictures/", GalleryTransformations.absoluteToRelativePath("/storage/emulated/0/Pictures", root))
        
        // Nested level
        assertEquals("Pictures/Screenshots/", GalleryTransformations.absoluteToRelativePath("/storage/emulated/0/Pictures/Screenshots", root))
        
        // Outside root
        assertNull(GalleryTransformations.absoluteToRelativePath("/data/user/0", root))
    }
}
