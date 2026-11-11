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

    private fun createMockUri(uriString: String): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns uriString
        return uri
    }

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
    fun `thumbnail override picks matching item in folder`() {
        val firstUri = createMockUri("content://media/a-first")
        val overrideUri = createMockUri("content://media/a-override")
        val folderPath = "/storage/emulated/0/Pictures/A"

        val media = listOf(
            createMediaItem(uri = firstUri, folderPath = folderPath, dateAdded = 1000L),
            createMediaItem(uri = overrideUri, folderPath = folderPath, dateAdded = 2000L)
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet(),
            thumbnailOverrides = mapOf(folderPath to overrideUri.toString())
        )

        assertEquals(1, folders.size)
        assertEquals(overrideUri, folders[0].thumbnailUri)
    }

    @Test
    fun `empty thumbnail overrides keep previous behaviour`() {
        val media = listOf(
            createMediaItem(mockUri1, folderPath = "/a"),
            createMediaItem(mockUri2, folderPath = "/b")
        )

        val foldersWithDefault = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet()
        )

        val foldersWithEmptyOverrides = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet(),
            thumbnailOverrides = emptyMap()
        )

        assertEquals(foldersWithDefault, foldersWithEmptyOverrides)
    }

    @Test
    fun `thumbnail override for missing uri is ignored and falls back to automatic thumbnail`() {
        val firstUri = createMockUri("content://media/missing-first")
        val secondUri = createMockUri("content://media/missing-second")
        val deletedUri = createMockUri("content://media/deleted")
        val folderPath = "/storage/emulated/0/Pictures/Missing"

        val media = listOf(
            createMediaItem(uri = firstUri, folderPath = folderPath, dateAdded = 1000L),
            createMediaItem(uri = secondUri, folderPath = folderPath, dateAdded = 2000L)
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet(),
            thumbnailOverrides = mapOf(folderPath to deletedUri.toString())
        )

        assertEquals(1, folders.size)
        assertEquals(firstUri, folders[0].thumbnailUri)
    }

    @Test
    fun `thumbnail override does not apply when uri moved to different folder and does not leak`() {
        val aFirst = createMockUri("content://media/a-first")
        val bFirst = createMockUri("content://media/b-first")
        val movedToB = createMockUri("content://media/moved-to-b")
        val folderA = "/storage/emulated/0/Pictures/A"
        val folderB = "/storage/emulated/0/Pictures/B"

        val media = listOf(
            createMediaItem(uri = aFirst, folderPath = folderA, dateAdded = 1000L),
            createMediaItem(uri = bFirst, folderPath = folderB, dateAdded = 1000L),
            createMediaItem(uri = movedToB, folderPath = folderB, dateAdded = 2000L)
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet(),
            thumbnailOverrides = mapOf(folderA to movedToB.toString())
        )

        val aFolder = folders.first { it.path == folderA }
        val bFolder = folders.first { it.path == folderB }
        assertEquals(aFirst, aFolder.thumbnailUri)
        assertEquals(bFirst, bFolder.thumbnailUri)
    }

    @Test
    fun `thumbnail override applies even when override item type is excluded by filter`() {
        val imageUri = createMockUri("content://media/mix-image")
        val videoUri = createMockUri("content://media/mix-video")
        val folderPath = "/storage/emulated/0/Pictures/Mixed"

        val media = listOf(
            createMediaItem(uri = imageUri, folderPath = folderPath, type = MediaType.IMAGE, dateAdded = 1000L),
            createMediaItem(uri = videoUri, folderPath = folderPath, type = MediaType.VIDEO, dateAdded = 2000L)
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = setOf(MediaType.VIDEO),
            thumbnailOverrides = mapOf(folderPath to imageUri.toString())
        )

        assertEquals(1, folders.size)
        assertEquals(1, folders[0].imageCount)
        assertEquals(imageUri, folders[0].thumbnailUri)
    }

    @Test
    fun `date taken sort uses indexed capture when available`() {
        val itemAUri = createMockUri("content://media/external/images/media/1")
        val itemBUri = createMockUri("content://media/external/images/media/2")

        val itemA = createMediaItem(uri = itemAUri, dateAdded = 10L)
        val itemB = createMediaItem(uri = itemBUri, dateAdded = 20L)

        val sorted = GalleryTransformations.sortMedia(
            media = listOf(itemA, itemB),
            sort = SortType.DATE_TAKEN,
            order = SortOrder.DESCENDING,
            indexedDateTakenByCanonicalMediaUri = mapOf(
                itemAUri.toString() to 1_000L,
                itemBUri.toString() to 2_000L
            )
        )

        assertEquals(itemBUri, sorted[0].uri)
        assertEquals(itemAUri, sorted[1].uri)
    }

    @Test
    fun `date taken sort degrades indexed null and not-indexed rows to existing dateAdded behaviour`() {
        val indexedWithDateUri = createMockUri("content://media/external/images/media/10")
        val indexedWithoutDateUri = createMockUri("content://media/external/images/media/20")
        val notIndexedUri = createMockUri("content://media/external/images/media/30")

        val indexedWithDate = createMediaItem(uri = indexedWithDateUri, dateAdded = 5L)
        val indexedWithoutDate = createMediaItem(uri = indexedWithoutDateUri, dateAdded = 20L)
        val notIndexed = createMediaItem(uri = notIndexedUri, dateAdded = 30L)

        val sorted = GalleryTransformations.sortMedia(
            media = listOf(indexedWithDate, indexedWithoutDate, notIndexed),
            sort = SortType.DATE_TAKEN,
            order = SortOrder.ASCENDING,
            indexedDateTakenByCanonicalMediaUri = mapOf(
                indexedWithDateUri.toString() to 1_000_000L,
                indexedWithoutDateUri.toString() to null
            )
        )

        assertEquals(indexedWithoutDateUri, sorted[0].uri)
        assertEquals(notIndexedUri, sorted[1].uri)
        assertEquals(indexedWithDateUri, sorted[2].uri)
    }

    @Test
    fun `date taken sort canonicalizes item uri lookup and requires canonical projection keys`() {
        val itemUri = createMockUri("content://media/external_primary/images/media/99")
        val otherUri = createMockUri("content://media/external/images/media/100")

        val withIndexedDate = createMediaItem(uri = itemUri, dateAdded = 10L)
        val fallbackItem = createMediaItem(uri = otherUri, dateAdded = 50L)

        val sortedWithCanonicalProjection = GalleryTransformations.sortMedia(
            media = listOf(withIndexedDate, fallbackItem),
            sort = SortType.DATE_TAKEN,
            order = SortOrder.DESCENDING,
            indexedDateTakenByCanonicalMediaUri = mapOf(
                "content://media/external/images/media/99" to 5_000_000L
            )
        )

        assertEquals(itemUri, sortedWithCanonicalProjection[0].uri)
        assertEquals(otherUri, sortedWithCanonicalProjection[1].uri)

        val sortedWithNonCanonicalProjection = GalleryTransformations.sortMedia(
            media = listOf(withIndexedDate, fallbackItem),
            sort = SortType.DATE_TAKEN,
            order = SortOrder.DESCENDING,
            indexedDateTakenByCanonicalMediaUri = mapOf(
                "content://media/external_primary/images/media/99" to 5_000_000L
            )
        )

        assertEquals(otherUri, sortedWithNonCanonicalProjection[0].uri)
        assertEquals(itemUri, sortedWithNonCanonicalProjection[1].uri)
    }

    @Test
    fun `thumbnail overrides resolve independently for multiple folders`() {
        val aFirst = createMockUri("content://media/multi-a-first")
        val aOverride = createMockUri("content://media/multi-a-override")
        val bFirst = createMockUri("content://media/multi-b-first")
        val bOverride = createMockUri("content://media/multi-b-override")
        val folderA = "/storage/emulated/0/Pictures/MultiA"
        val folderB = "/storage/emulated/0/Pictures/MultiB"

        val media = listOf(
            createMediaItem(uri = aFirst, folderPath = folderA, dateAdded = 1000L),
            createMediaItem(uri = aOverride, folderPath = folderA, dateAdded = 2000L),
            createMediaItem(uri = bFirst, folderPath = folderB, dateAdded = 1000L),
            createMediaItem(uri = bOverride, folderPath = folderB, dateAdded = 2000L)
        )

        val folders = GalleryTransformations.filterAndSortFolders(
            allMedia = media,
            query = "",
            pinned = emptySet(),
            sort = SortType.NAME,
            order = SortOrder.ASCENDING,
            excluded = emptySet(),
            showExcluded = false,
            types = MediaType.entries.toSet(),
            thumbnailOverrides = mapOf(
                folderA to aOverride.toString(),
                folderB to bOverride.toString()
            )
        )

        val aFolder = folders.first { it.path == folderA }
        val bFolder = folders.first { it.path == folderB }
        assertEquals(aOverride, aFolder.thumbnailUri)
        assertEquals(bOverride, bFolder.thumbnailUri)
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
