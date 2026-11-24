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
            types = setOf(MediaType.IMAGE),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            types = MediaType.entries.toSet(),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            types = MediaType.entries.toSet(),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            types = MediaType.entries.toSet(),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            types = MediaType.entries.toSet(),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            types = MediaType.entries.toSet(),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            thumbnailOverrides = mapOf(folderPath to overrideUri.toString()),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            types = MediaType.entries.toSet(),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            thumbnailOverrides = emptyMap(),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            thumbnailOverrides = mapOf(folderPath to deletedUri.toString()),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            thumbnailOverrides = mapOf(folderA to movedToB.toString()),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
            thumbnailOverrides = mapOf(folderPath to imageUri.toString()),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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
    fun `date taken instant is the indexed capture date when the item is indexed with one`() {
        val uri = createMockUri("content://media/external/images/media/1")
        val item = createMediaItem(uri = uri, dateAdded = 10L)

        val millis = GalleryTransformations.dateTakenEpochMillis(item, mapOf(uri.toString() to 1_234L))

        assertEquals(1_234L, millis)
    }

    @Test
    fun `date taken instant falls back to dateAdded in millis when indexed without a date`() {
        val uri = createMockUri("content://media/external/images/media/2")
        val item = createMediaItem(uri = uri, dateAdded = 10L)

        val millis = GalleryTransformations.dateTakenEpochMillis(item, mapOf(uri.toString() to null))

        assertEquals(10_000L, millis)
    }

    @Test
    fun `date taken instant falls back to dateAdded in millis when the item is not indexed`() {
        val uri = createMockUri("content://media/external/images/media/3")
        val item = createMediaItem(uri = uri, dateAdded = 10L)

        val millis = GalleryTransformations.dateTakenEpochMillis(item, emptyMap())

        assertEquals(10_000L, millis)
    }

    @Test
    fun `date taken instant canonicalizes a per-volume item uri like the sort does`() {
        val uri = createMockUri("content://media/external_primary/images/media/4")
        val item = createMediaItem(uri = uri, dateAdded = 10L)

        val millis = GalleryTransformations.dateTakenEpochMillis(
            item,
            mapOf("content://media/external/images/media/4" to 7_000L)
        )

        assertEquals(7_000L, millis)
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
            ),
            indexedDateTakenByCanonicalMediaUri = emptyMap()
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

    private fun sortedUris(
        media: List<MediaItem>,
        sort: SortType,
        order: SortOrder
    ): List<Uri> = GalleryTransformations.sortMedia(media, sort, order, emptyMap()).map { it.uri }

    @Test
    fun `media path sort orders by folder path then name ascending and descending`() {
        val aB = createMockUri("content://media/external/images/media/1")
        val aC = createMockUri("content://media/external/images/media/2")
        val bA = createMockUri("content://media/external/images/media/3")
        val media = listOf(
            createMediaItem(bA, name = "a.jpg", folderPath = "/pics/B"),
            createMediaItem(aC, name = "c.jpg", folderPath = "/pics/A"),
            createMediaItem(aB, name = "b.jpg", folderPath = "/pics/A")
        )

        assertEquals(listOf(aB, aC, bA), sortedUris(media, SortType.PATH, SortOrder.ASCENDING))
        assertEquals(listOf(bA, aC, aB), sortedUris(media, SortType.PATH, SortOrder.DESCENDING))
    }

    @Test
    fun `media path sort ignores date added`() {
        val first = createMockUri("content://media/external/images/media/1")
        val second = createMockUri("content://media/external/images/media/2")
        val media = listOf(
            createMediaItem(first, name = "a.jpg", dateAdded = 900L),
            createMediaItem(second, name = "b.jpg", dateAdded = 100L)
        )

        assertEquals(listOf(first, second), sortedUris(media, SortType.PATH, SortOrder.ASCENDING))
    }

    @Test
    fun `media path sort is case sensitive like the folders path sort`() {
        val upper = createMockUri("content://media/external/images/media/1")
        val lower = createMockUri("content://media/external/images/media/2")
        val media = listOf(
            createMediaItem(lower, name = "a.jpg"),
            createMediaItem(upper, name = "B.jpg")
        )

        assertEquals(listOf(upper, lower), sortedUris(media, SortType.PATH, SortOrder.ASCENDING))
    }

    @Test
    fun `media path ties fall back to uri in both directions`() {
        val low = createMockUri("content://media/external/images/media/1")
        val high = createMockUri("content://media/external/video/media/1")
        val media = listOf(
            createMediaItem(high, name = "same.jpg"),
            createMediaItem(low, name = "same.jpg")
        )

        assertEquals(listOf(low, high), sortedUris(media, SortType.PATH, SortOrder.ASCENDING))
        assertEquals(listOf(low, high), sortedUris(media, SortType.PATH, SortOrder.DESCENDING))
    }

    @Test
    fun `media size sort orders smallest first ascending and largest first descending`() {
        val small = createMockUri("content://media/external/images/media/1")
        val medium = createMockUri("content://media/external/images/media/2")
        val large = createMockUri("content://media/external/images/media/3")
        val media = listOf(
            createMediaItem(medium, name = "m.jpg", size = 500L, dateAdded = 1L),
            createMediaItem(large, name = "l.jpg", size = 9_000L, dateAdded = 2L),
            createMediaItem(small, name = "s.jpg", size = 5L, dateAdded = 3L)
        )

        assertEquals(listOf(small, medium, large), sortedUris(media, SortType.SIZE, SortOrder.ASCENDING))
        assertEquals(listOf(large, medium, small), sortedUris(media, SortType.SIZE, SortOrder.DESCENDING))
    }

    @Test
    fun `media size ties fall back to path then uri in both directions`() {
        val pathA = createMockUri("content://media/external/images/media/9")
        val pathB = createMockUri("content://media/external/images/media/1")
        val samePathLow = createMockUri("content://media/external/images/media/2")
        val samePathHigh = createMockUri("content://media/external/video/media/2")
        val media = listOf(
            createMediaItem(pathB, name = "b.jpg", size = 10L),
            createMediaItem(samePathHigh, name = "a.jpg", size = 10L, folderPath = "/z"),
            createMediaItem(pathA, name = "a.jpg", size = 10L),
            createMediaItem(samePathLow, name = "a.jpg", size = 10L, folderPath = "/z")
        )
        val expected = listOf(pathA, pathB, samePathLow, samePathHigh)

        assertEquals(expected, sortedUris(media, SortType.SIZE, SortOrder.ASCENDING))
        assertEquals(expected, sortedUris(media, SortType.SIZE, SortOrder.DESCENDING))
    }

    @Test
    fun `media path and size sorts do not depend on input order`() {
        val items = (1..6).map { index ->
            createMediaItem(
                createMockUri("content://media/external/images/media/$index"),
                name = "n${index % 3}.jpg",
                size = (index % 2).toLong()
            )
        }

        for (sort in listOf(SortType.PATH, SortType.SIZE)) {
            for (order in SortOrder.entries) {
                assertEquals(
                    sortedUris(items, sort, order),
                    sortedUris(items.reversed(), sort, order)
                )
            }
        }
    }

    @Test
    fun `media name sort still orders by name and reverses for descending`() {
        val a = createMockUri("content://media/external/images/media/1")
        val b = createMockUri("content://media/external/images/media/2")
        val media = listOf(createMediaItem(b, name = "b.jpg"), createMediaItem(a, name = "a.jpg"))

        assertEquals(listOf(a, b), sortedUris(media, SortType.NAME, SortOrder.ASCENDING))
        assertEquals(listOf(b, a), sortedUris(media, SortType.NAME, SortOrder.DESCENDING))
    }

    @Test
    fun `media last modified sort still orders by date modified and reverses for descending`() {
        val older = createMockUri("content://media/external/images/media/1")
        val newer = createMockUri("content://media/external/images/media/2")
        val media = listOf(
            createMediaItem(newer, name = "a.jpg", dateAdded = 200L),
            createMediaItem(older, name = "b.jpg", dateAdded = 100L)
        )

        assertEquals(listOf(older, newer), sortedUris(media, SortType.LAST_MODIFIED, SortOrder.ASCENDING))
        assertEquals(listOf(newer, older), sortedUris(media, SortType.LAST_MODIFIED, SortOrder.DESCENDING))
    }

    @Test
    fun `media date taken sort without index still orders by date added`() {
        val older = createMockUri("content://media/external/images/media/1")
        val newer = createMockUri("content://media/external/images/media/2")
        val media = listOf(
            createMediaItem(newer, name = "a.jpg", dateAdded = 200L, size = 1L),
            createMediaItem(older, name = "b.jpg", dateAdded = 100L, size = 9L)
        )

        assertEquals(listOf(older, newer), sortedUris(media, SortType.DATE_TAKEN, SortOrder.ASCENDING))
        assertEquals(listOf(newer, older), sortedUris(media, SortType.DATE_TAKEN, SortOrder.DESCENDING))
    }

    @Test
    fun `media random sort keeps every item whatever the order`() {
        val media = (1..8).map {
            createMediaItem(createMockUri("content://media/external/images/media/$it"), name = "$it.jpg")
        }

        for (order in SortOrder.entries) {
            val sorted = GalleryTransformations.sortMedia(media, SortType.RANDOM, order, emptyMap())
            assertEquals(media.map { it.uri }.toSet(), sorted.map { it.uri }.toSet())
            assertEquals(media.size, sorted.size)
        }
    }

    @Test
    fun `grouping by file type keeps the path or size order inside each group`() {
        val imageB = createMockUri("content://media/external/images/media/1")
        val imageA = createMockUri("content://media/external/images/media/2")
        val video = createMockUri("content://media/external/video/media/3")
        val media = listOf(
            createMediaItem(imageB, name = "b.jpg", size = 1L),
            createMediaItem(video, name = "v.mp4", size = 5L, type = MediaType.VIDEO),
            createMediaItem(imageA, name = "a.jpg", size = 9L)
        )
        val labels = mapOf(MediaType.IMAGE to "Images", MediaType.VIDEO to "Videos")

        val byPath = GalleryTransformations.groupMedia(
            GalleryTransformations.sortMedia(media, SortType.PATH, SortOrder.ASCENDING, emptyMap()),
            GroupByType.FILE_TYPE, SortOrder.ASCENDING, emptyMap(), fileTypeLabels = labels
        )
        val bySize = GalleryTransformations.groupMedia(
            GalleryTransformations.sortMedia(media, SortType.SIZE, SortOrder.DESCENDING, emptyMap()),
            GroupByType.FILE_TYPE, SortOrder.ASCENDING, emptyMap(), fileTypeLabels = labels
        )

        assertEquals(listOf(imageA, imageB), byPath.getValue("Images").map { it.uri })
        assertEquals(listOf(imageA, imageB), bySize.getValue("Images").map { it.uri })
    }

    // region resolveDateTaken (ADR-0026)

    private fun resolved(
        item: MediaItem,
        index: Map<String, Long?> = emptyMap()
    ) = GalleryTransformations.resolveDateTaken(item, index)

    @Test
    fun `resolved date taken prefers the indexed capture date over MediaStore and date added`() {
        val uri = createMockUri("content://media/external/images/media/1")
        val item = createMediaItem(uri = uri, dateAdded = 10L).copy(mediaStoreDateTakenMillis = 5_000L)

        val result = resolved(item, mapOf(uri.toString() to 1_234L))

        assertEquals(ResolvedDateTaken(1_234L, isCaptureDate = true), result)
    }

    @Test
    fun `resolved date taken uses MediaStore when the item is not indexed`() {
        val uri = createMockUri("content://media/external/images/media/2")
        val item = createMediaItem(uri = uri, dateAdded = 10L).copy(mediaStoreDateTakenMillis = 5_000L)

        assertEquals(ResolvedDateTaken(5_000L, isCaptureDate = true), resolved(item))
    }

    @Test
    fun `resolved date taken uses MediaStore when the item is indexed without a date`() {
        val uri = createMockUri("content://media/external/images/media/3")
        val item = createMediaItem(uri = uri, dateAdded = 10L).copy(mediaStoreDateTakenMillis = 5_000L)

        val result = resolved(item, mapOf(uri.toString() to null))

        assertEquals(ResolvedDateTaken(5_000L, isCaptureDate = true), result)
    }

    @Test
    fun `resolved date taken falls back to date added in millis and says it is not a capture date`() {
        val uri = createMockUri("content://media/external/images/media/4")
        val item = createMediaItem(uri = uri, dateAdded = 10L)

        assertEquals(ResolvedDateTaken(10_000L, isCaptureDate = false), resolved(item))
        assertEquals(
            ResolvedDateTaken(10_000L, isCaptureDate = false),
            resolved(item, mapOf(uri.toString() to null))
        )
    }

    @Test
    fun `resolved date taken canonicalizes a per-volume item uri when reading the index`() {
        val uri = createMockUri("content://media/external_primary/images/media/5")
        val item = createMediaItem(uri = uri, dateAdded = 10L).copy(mediaStoreDateTakenMillis = 5_000L)

        val result = resolved(item, mapOf("content://media/external/images/media/5" to 7_000L))

        assertEquals(ResolvedDateTaken(7_000L, isCaptureDate = true), result)
    }

    @Test
    fun `date taken sort places an item with a MediaStore date by that date`() {
        val withStoreDate = createMockUri("content://media/external/images/media/6")
        val plain = createMockUri("content://media/external/images/media/7")
        val media = listOf(
            createMediaItem(uri = plain, dateAdded = 500L),
            createMediaItem(uri = withStoreDate, dateAdded = 900L).copy(mediaStoreDateTakenMillis = 100_000L)
        )

        assertEquals(
            listOf(withStoreDate, plain),
            sortedUris(media, SortType.DATE_TAKEN, SortOrder.ASCENDING)
        )
    }

    // endregion

    // region Folder date taken (ADR-0026)

    private fun foldersByDateTaken(
        media: List<MediaItem>,
        order: SortOrder,
        index: Map<String, Long?> = emptyMap(),
        types: Set<MediaType> = MediaType.entries.toSet()
    ) = GalleryTransformations.filterAndSortFolders(
        allMedia = media,
        query = "",
        pinned = emptySet(),
        sort = SortType.DATE_TAKEN,
        order = order,
        excluded = emptySet(),
        showExcluded = false,
        types = types,
        indexedDateTakenByCanonicalMediaUri = index
    )

    @Test
    fun `folder date taken is the newest resolved date in milliseconds`() {
        val indexed = createMockUri("content://media/external/images/media/1")
        val storeDated = createMockUri("content://media/external/images/media/2")
        val plain = createMockUri("content://media/external/images/media/3")
        val media = listOf(
            createMediaItem(uri = indexed, folderPath = "/f", dateAdded = 1L),
            createMediaItem(uri = storeDated, folderPath = "/f", dateAdded = 1L).copy(mediaStoreDateTakenMillis = 40_000L),
            createMediaItem(uri = plain, folderPath = "/f", dateAdded = 9L)
        )

        val folder = foldersByDateTaken(media, SortOrder.ASCENDING, mapOf(indexed.toString() to 70_000L)).single()

        assertEquals(70_000L, folder.dateTaken)
    }

    @Test
    fun `folder date taken without any capture date is the newest date added in milliseconds`() {
        val media = listOf(
            createMediaItem(uri = createMockUri("content://media/external/images/media/1"), folderPath = "/f", dateAdded = 3L),
            createMediaItem(uri = createMockUri("content://media/external/images/media/2"), folderPath = "/f", dateAdded = 8L)
        )

        assertEquals(8_000L, foldersByDateTaken(media, SortOrder.ASCENDING).single().dateTaken)
    }

    @Test
    fun `folder date taken sort orders by the newest resolved date and ascending lists newest first like size`() {
        val oldAdded = createMockUri("content://media/external/images/media/1")
        val recentCapture = createMockUri("content://media/external/images/media/2")
        val media = listOf(
            // Folder A was added last but captured long ago; folder B was added first but captured recently.
            createMediaItem(uri = oldAdded, folderPath = "/A", dateAdded = 900L),
            createMediaItem(uri = recentCapture, folderPath = "/B", dateAdded = 100L)
        )
        val index = mapOf(oldAdded.toString() to 1_000L, recentCapture.toString() to 5_000_000L)

        val ascending = foldersByDateTaken(media, SortOrder.ASCENDING, index)
        val descending = foldersByDateTaken(media, SortOrder.DESCENDING, index)

        assertEquals(listOf("/B", "/A"), ascending.map { it.path })
        assertEquals(listOf("/A", "/B"), descending.map { it.path })
    }

    @Test
    fun `folder date taken counts only items that pass the media type filter`() {
        val image = createMockUri("content://media/external/images/media/1")
        val video = createMockUri("content://media/external/video/media/2")
        val media = listOf(
            createMediaItem(uri = image, folderPath = "/f", dateAdded = 1L, type = MediaType.IMAGE),
            createMediaItem(uri = video, folderPath = "/f", dateAdded = 1L, type = MediaType.VIDEO)
        )
        val index = mapOf(image.toString() to 1_000L, video.toString() to 9_000L)

        val folder = foldersByDateTaken(media, SortOrder.ASCENDING, index, setOf(MediaType.IMAGE)).single()

        assertEquals(1_000L, folder.dateTaken)
    }

    @Test
    fun `folder thumbnail stays the first item by date added whatever the resolved date`() {
        val newestAdded = createMockUri("content://media/external/images/media/1")
        val newestCapture = createMockUri("content://media/external/images/media/2")
        val media = listOf(
            createMediaItem(uri = newestAdded, folderPath = "/f", dateAdded = 900L),
            createMediaItem(uri = newestCapture, folderPath = "/f", dateAdded = 100L)
        )

        val folder = foldersByDateTaken(media, SortOrder.ASCENDING, mapOf(newestCapture.toString() to 9_000_000L)).single()

        assertEquals(newestAdded, folder.thumbnailUri)
    }

    // endregion
}
