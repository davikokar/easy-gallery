package com.davide.seddio.easygallery.ui

import android.net.Uri
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumListFilteringTest {

    private val allTypes = MediaType.entries.toSet()

    private fun album(
        id: Long,
        name: String,
        images: Int = 0,
        videos: Int = 0,
        gifs: Int = 0,
        createdAt: Long = id,
        cover: MediaItem? = null
    ): AlbumListItem {
        val counts = mapOf(MediaType.IMAGE to images, MediaType.VIDEO to videos, MediaType.GIF to gifs)
        return AlbumListItem(
            id = id,
            name = name,
            createdAt = createdAt,
            cover = cover,
            isCoverFallback = false,
            resolvedMemberCount = counts.values.sum(),
            mediaCountsByType = counts,
            storedMemberCount = counts.values.sum(),
            hiddenMemberCount = 0,
            storedMembershipCount = counts.values.sum(),
            status = AlbumEvaluationStatus.MANUAL
        )
    }

    private fun coverOf(type: MediaType) = MediaItem(
        uri = mockk<Uri>(),
        name = "cover",
        dateAdded = 1L,
        dateModified = 1L,
        size = 1L,
        type = type,
        bucketName = "Cover",
        folderPath = "/cover"
    )

    private fun filterAndSort(
        albums: List<AlbumListItem>,
        query: String = "",
        sortType: SortType = SortType.NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        mediaTypes: Set<MediaType> = allTypes
    ) = filterAndSortAlbums(albums, query, sortType, sortOrder, mediaTypes)

    @Test
    fun `with every type selected an album shows its full count`() {
        val result = filterAndSort(listOf(album(1, "Trips", images = 3, videos = 2, gifs = 1)))

        assertEquals(listOf(6), result.map { it.itemCount })
    }

    @Test
    fun `the filter counts only the selected types`() {
        val albums = listOf(album(1, "Trips", images = 3, videos = 2, gifs = 1))

        assertEquals(3, filterAndSort(albums, mediaTypes = setOf(MediaType.IMAGE)).single().itemCount)
        assertEquals(2, filterAndSort(albums, mediaTypes = setOf(MediaType.VIDEO)).single().itemCount)
        assertEquals(3, filterAndSort(albums, mediaTypes = setOf(MediaType.VIDEO, MediaType.GIF)).single().itemCount)
    }

    @Test
    fun `an album the filter leaves with no item is hidden`() {
        val albums = listOf(album(1, "Photos", images = 5), album(2, "Clips", videos = 4))

        val result = filterAndSort(albums, mediaTypes = setOf(MediaType.VIDEO))

        assertEquals(listOf("Clips"), result.map { it.album.name })
        assertEquals(listOf(4), result.map { it.itemCount })
    }

    @Test
    fun `an album that has no items at all stays listed under any filter`() {
        val albums = listOf(album(1, "Photos", images = 5), album(2, "Empty"))

        val videosOnly = filterAndSort(albums, mediaTypes = setOf(MediaType.VIDEO))
        val nothingSelected = filterAndSort(albums, mediaTypes = emptySet())

        assertEquals(listOf("Empty"), videosOnly.map { it.album.name })
        assertEquals(listOf(0), videosOnly.map { it.itemCount })
        assertEquals(listOf("Empty"), nothingSelected.map { it.album.name })
    }

    @Test
    fun `an empty selection hides every album that has items`() {
        val albums = listOf(album(1, "Photos", images = 5), album(2, "Clips", videos = 4))

        val result = filterAndSort(albums, mediaTypes = emptySet())

        assertEquals(emptyList<VisibleAlbum>(), result)
    }

    @Test
    fun `a listed album keeps its cover even when the cover is of a filtered-out type`() {
        val cover = coverOf(MediaType.IMAGE)
        val original = album(1, "Mixed", images = 2, videos = 1, cover = cover)

        val result = filterAndSort(listOf(original), mediaTypes = setOf(MediaType.VIDEO))

        assertEquals(original, result.single().album)
        assertEquals(cover, result.single().album.cover)
        assertEquals(1, result.single().itemCount)
    }

    @Test
    fun `search still hides an album by name`() {
        val albums = listOf(album(1, "Trips", images = 1), album(2, "Docs", images = 1))

        val result = filterAndSort(albums, query = "tri")

        assertEquals(listOf("Trips"), result.map { it.album.name })
    }

    @Test
    fun `search applies to the albums the filter keeps and the filter still hides the rest`() {
        val albums = listOf(
            album(1, "Trip photos", images = 3),
            album(2, "Trip clips", videos = 2),
            album(3, "Trip empty"),
            album(4, "Docs", videos = 1)
        )

        val result = filterAndSort(albums, query = "trip", mediaTypes = setOf(MediaType.VIDEO))

        assertEquals(listOf("Trip clips", "Trip empty"), result.map { it.album.name })
    }

    @Test
    fun `size sorts by the filtered count and leaves out a hidden album`() {
        val albums = listOf(
            album(1, "Three clips", videos = 3),
            album(2, "Only photos", images = 50),
            album(3, "One clip", images = 9, videos = 1)
        )

        val ascending = filterAndSort(albums, sortType = SortType.SIZE, mediaTypes = setOf(MediaType.VIDEO))
        val descending = filterAndSort(
            albums,
            sortType = SortType.SIZE,
            sortOrder = SortOrder.DESCENDING,
            mediaTypes = setOf(MediaType.VIDEO)
        )

        assertEquals(listOf("One clip", "Three clips"), ascending.map { it.album.name })
        assertEquals(listOf("Three clips", "One clip"), descending.map { it.album.name })
    }

    @Test
    fun `size sorts by the filtered count`() {
        val albums = listOf(
            album(1, "Many photos", images = 50, videos = 1),
            album(2, "Some videos", images = 1, videos = 10),
            album(3, "Few", images = 2, videos = 2)
        )

        val ascending = filterAndSort(albums, sortType = SortType.SIZE, mediaTypes = setOf(MediaType.VIDEO))
        val descending = filterAndSort(
            albums,
            sortType = SortType.SIZE,
            sortOrder = SortOrder.DESCENDING,
            mediaTypes = setOf(MediaType.VIDEO)
        )

        assertEquals(listOf("Many photos", "Few", "Some videos"), ascending.map { it.album.name })
        assertEquals(listOf("Some videos", "Few", "Many photos"), descending.map { it.album.name })
    }

    @Test
    fun `name sorts case insensitively and descending reverses it`() {
        val albums = listOf(album(1, "beta"), album(2, "Alpha"), album(3, "Gamma"))

        assertEquals(listOf("Alpha", "beta", "Gamma"), filterAndSort(albums).map { it.album.name })
        assertEquals(
            listOf("Gamma", "beta", "Alpha"),
            filterAndSort(albums, sortOrder = SortOrder.DESCENDING).map { it.album.name }
        )
    }

    @Test
    fun `last modified and date taken sort by creation time`() {
        val albums = listOf(album(1, "A", createdAt = 30), album(2, "B", createdAt = 10), album(3, "C", createdAt = 20))

        assertEquals(listOf("B", "C", "A"), filterAndSort(albums, sortType = SortType.LAST_MODIFIED).map { it.album.name })
        assertEquals(listOf("B", "C", "A"), filterAndSort(albums, sortType = SortType.DATE_TAKEN).map { it.album.name })
    }

    @Test
    fun `random keeps every album whichever order it lands in`() {
        val albums = (1L..10L).map { album(it, "Album $it", images = it.toInt()) }

        val result = filterAndSort(albums, sortType = SortType.RANDOM, sortOrder = SortOrder.DESCENDING)

        assertEquals(albums.map { it.id }.toSet(), result.map { it.album.id }.toSet())
    }
}
