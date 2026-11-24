package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlideshowPlaylistTest {

    private fun item(
        name: String,
        type: MediaType = MediaType.IMAGE,
        width: Int? = 4000,
        height: Int? = 3000,
        dateAdded: Long = 1000L,
        uriString: String = "content://media/external/images/media/${name.hashCode()}"
    ): MediaItem {
        val uri = mockk<Uri>()
        every { uri.toString() } returns uriString
        return MediaItem(
            uri = uri,
            name = name,
            dateAdded = dateAdded,
            dateModified = dateAdded,
            size = 100L,
            type = type,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera",
            displayWidth = width,
            displayHeight = height
        )
    }

    private fun names(items: List<MediaItem>) = items.map { it.name }

    // region isPlayable

    @Test
    fun `defaults play images and videos but not gifs`() {
        val options = SlideshowOptions()

        assertTrue(SlideshowPlaylist.isPlayable(item("a", MediaType.IMAGE), options))
        assertTrue(SlideshowPlaylist.isPlayable(item("b", MediaType.VIDEO), options))
        assertFalse(SlideshowPlaylist.isPlayable(item("c", MediaType.GIF), options))
    }

    @Test
    fun `include switches turn videos and gifs on and off while images always play`() {
        val noVideos = SlideshowOptions(includeVideos = false, includeGifs = true)
        val noGifs = SlideshowOptions(includeVideos = true, includeGifs = false)

        assertFalse(SlideshowPlaylist.isPlayable(item("v", MediaType.VIDEO), noVideos))
        assertTrue(SlideshowPlaylist.isPlayable(item("g", MediaType.GIF), noVideos))
        assertTrue(SlideshowPlaylist.isPlayable(item("i", MediaType.IMAGE), noVideos))
        assertFalse(SlideshowPlaylist.isPlayable(item("g", MediaType.GIF), noGifs))
        assertTrue(SlideshowPlaylist.isPlayable(item("i", MediaType.IMAGE), noGifs))
    }

    @Test
    fun `landscape only plays wider than tall items`() {
        val options = SlideshowOptions(orientation = SlideshowOrientation.LANDSCAPE_ONLY)

        assertTrue(SlideshowPlaylist.isPlayable(item("l", width = 4000, height = 3000), options))
        assertFalse(SlideshowPlaylist.isPlayable(item("p", width = 3000, height = 4000), options))
    }

    @Test
    fun `portrait only plays taller than wide items`() {
        val options = SlideshowOptions(orientation = SlideshowOrientation.PORTRAIT_ONLY)

        assertTrue(SlideshowPlaylist.isPlayable(item("p", width = 3000, height = 4000), options))
        assertFalse(SlideshowPlaylist.isPlayable(item("l", width = 4000, height = 3000), options))
    }

    @Test
    fun `square and unknown size items play only under all`() {
        val square = item("s", width = 2000, height = 2000)
        val unknown = item("u", width = null, height = null)

        assertTrue(SlideshowPlaylist.isPlayable(square, SlideshowOptions(orientation = SlideshowOrientation.ALL)))
        assertTrue(SlideshowPlaylist.isPlayable(unknown, SlideshowOptions(orientation = SlideshowOrientation.ALL)))
        listOf(SlideshowOrientation.LANDSCAPE_ONLY, SlideshowOrientation.PORTRAIT_ONLY).forEach { orientation ->
            val options = SlideshowOptions(orientation = orientation)
            assertFalse(SlideshowPlaylist.isPlayable(square, options))
            assertFalse(SlideshowPlaylist.isPlayable(unknown, options))
        }
    }

    @Test
    fun `a rotated photo is judged on its displayed shape`() {
        val (width, height) = displayedDimensions(4000, 3000, 90)!!
        val rotated = item("r", width = width, height = height)

        assertTrue(
            SlideshowPlaylist.isPlayable(rotated, SlideshowOptions(orientation = SlideshowOrientation.PORTRAIT_ONLY))
        )
        assertFalse(
            SlideshowPlaylist.isPlayable(rotated, SlideshowOptions(orientation = SlideshowOrientation.LANDSCAPE_ONLY))
        )
    }

    @Test
    fun `orientation rules apply to videos too`() {
        val portraitVideo = item("v", MediaType.VIDEO, width = 1080, height = 1920)
        val options = SlideshowOptions(orientation = SlideshowOrientation.LANDSCAPE_ONLY)

        assertFalse(SlideshowPlaylist.isPlayable(portraitVideo, options))
    }

    // endregion

    // region baseOrder

    @Test
    fun `base order follows the view's sort type and order`() {
        val membership = listOf(item("b"), item("c"), item("a"))

        val ascending = SlideshowPlaylist.baseOrder(
            membership, SortType.NAME, SortOrder.ASCENDING, emptyMap(), emptyList(), Random(1)
        )
        val descending = SlideshowPlaylist.baseOrder(
            membership, SortType.NAME, SortOrder.DESCENDING, emptyMap(), emptyList(), Random(1)
        )

        assertEquals(listOf("a", "b", "c"), names(ascending))
        assertEquals(listOf("c", "b", "a"), names(descending))
    }

    @Test
    fun `base order uses the indexed date taken for the date taken sort`() {
        val first = item("first", dateAdded = 300L, uriString = "content://media/external/images/media/1")
        val second = item("second", dateAdded = 100L, uriString = "content://media/external/images/media/2")
        val membership = listOf(first, second)
        // Indexed taken dates reverse the dateAdded order.
        val indexed = mapOf<String, Long?>(
            "content://media/external/images/media/1" to 5_000L,
            "content://media/external/images/media/2" to 9_000L
        )

        val result = SlideshowPlaylist.baseOrder(
            membership, SortType.DATE_TAKEN, SortOrder.ASCENDING, indexed, emptyList(), Random(1)
        )

        assertEquals(listOf("first", "second"), names(result))
    }

    @Test
    fun `random sort keeps the shown order and ignores the sort order`() {
        val a = item("a")
        val b = item("b")
        val c = item("c")
        val membership = listOf(a, b, c)
        val shown = listOf(c, a, b)

        val ascending = SlideshowPlaylist.baseOrder(
            membership, SortType.RANDOM, SortOrder.ASCENDING, emptyMap(), shown, Random(1)
        )
        val descending = SlideshowPlaylist.baseOrder(
            membership, SortType.RANDOM, SortOrder.DESCENDING, emptyMap(), shown, Random(1)
        )

        assertEquals(listOf("c", "a", "b"), names(ascending))
        assertEquals(listOf("c", "a", "b"), names(descending))
    }

    @Test
    fun `random sort inserts items the view hides without disturbing the shown order`() {
        val shown = (1..5).map { item("shown$it") }
        val hidden = (1..4).map { item("hidden$it", MediaType.VIDEO) }
        val membership = shown + hidden

        repeat(20) { seed ->
            val result = SlideshowPlaylist.baseOrder(
                membership, SortType.RANDOM, SortOrder.ASCENDING, emptyMap(), shown, Random(seed)
            )

            assertEquals(membership.size, result.size)
            assertEquals(membership.toSet(), result.toSet())
            assertEquals(names(shown), names(result).filter { it.startsWith("shown") })
        }
    }

    @Test
    fun `random sort scatters hidden items instead of appending them`() {
        val shown = (1..10).map { item("shown$it") }
        val hidden = (1..10).map { item("hidden$it", MediaType.VIDEO) }

        val allAtTheEnd = (0 until 20).all { seed ->
            val result = SlideshowPlaylist.baseOrder(
                shown + hidden, SortType.RANDOM, SortOrder.ASCENDING, emptyMap(), shown, Random(seed)
            )
            result.take(shown.size).all { it.name.startsWith("shown") }
        }

        assertFalse(allAtTheEnd)
    }

    @Test
    fun `random sort drops shown items that left the membership`() {
        val a = item("a")
        val b = item("b")
        val gone = item("gone")

        val result = SlideshowPlaylist.baseOrder(
            listOf(a, b), SortType.RANDOM, SortOrder.ASCENDING, emptyMap(), listOf(b, gone, a), Random(1)
        )

        assertEquals(listOf("b", "a"), names(result))
    }

    @Test
    fun `random sort with nothing shown still returns every item once`() {
        val membership = (1..6).map { item("m$it") }

        val result = SlideshowPlaylist.baseOrder(
            membership, SortType.RANDOM, SortOrder.ASCENDING, emptyMap(), emptyList(), Random(3)
        )

        assertEquals(membership.toSet(), result.toSet())
        assertEquals(membership.size, result.size)
    }

    // endregion

    // region build

    @Test
    fun `build filters unplayable items and keeps the base order when ascending`() {
        val base = listOf(item("img1"), item("vid", MediaType.VIDEO), item("gif", MediaType.GIF), item("img2"))
        val options = SlideshowOptions(includeVideos = false, includeGifs = false)

        val result = SlideshowPlaylist.build(base, options, Random(1))

        assertEquals(listOf("img1", "img2"), names(result))
    }

    @Test
    fun `build reverses the filtered base order when descending`() {
        val base = listOf(item("a"), item("v", MediaType.VIDEO), item("b"), item("c"))
        val options = SlideshowOptions(includeVideos = false, order = SlideshowOrder.DESCENDING)

        val result = SlideshowPlaylist.build(base, options, Random(1))

        assertEquals(listOf("c", "b", "a"), names(result))
    }

    @Test
    fun `build shuffles when random and keeps exactly the playable items`() {
        val base = (1..30).map { item("i$it") }
        val options = SlideshowOptions(order = SlideshowOrder.RANDOM)

        val result = SlideshowPlaylist.build(base, options, Random(7))

        assertEquals(base.toSet(), result.toSet())
        assertEquals(base.size, result.size)
        assertNotEquals(names(base), names(result))
    }

    @Test
    fun `build with the same seed gives the same random order`() {
        val base = (1..30).map { item("i$it") }
        val options = SlideshowOptions(order = SlideshowOrder.RANDOM)

        val first = SlideshowPlaylist.build(base, options, Random(42))
        val second = SlideshowPlaylist.build(base, options, Random(42))

        assertEquals(names(first), names(second))
    }

    @Test
    fun `build of an empty base is empty`() {
        assertTrue(SlideshowPlaylist.build(emptyList(), SlideshowOptions(), Random(1)).isEmpty())
    }

    @Test
    fun `build applies the orientation rule`() {
        val base = listOf(
            item("land", width = 4000, height = 3000),
            item("port", width = 3000, height = 4000),
            item("square", width = 1000, height = 1000),
            item("unknown", width = null, height = null)
        )

        val result = SlideshowPlaylist.build(
            base, SlideshowOptions(orientation = SlideshowOrientation.PORTRAIT_ONLY), Random(1)
        )

        assertEquals(listOf("port"), names(result))
    }

    // endregion

    // region reshuffle

    @Test
    fun `reshuffle never starts on the last shown item`() {
        val playlist = (1..5).map { item("i$it") }

        repeat(200) { seed ->
            val last = playlist[seed % playlist.size]

            val result = SlideshowPlaylist.reshuffle(playlist, last, Random(seed))

            assertNotEquals(last.uri, result.first().uri)
            assertEquals(playlist.toSet(), result.toSet())
            assertEquals(playlist.size, result.size)
        }
    }

    @Test
    fun `reshuffle of two items starts on the other one`() {
        val a = item("a")
        val b = item("b")

        repeat(20) { seed ->
            assertEquals("b", SlideshowPlaylist.reshuffle(listOf(a, b), a, Random(seed)).first().name)
            assertEquals("a", SlideshowPlaylist.reshuffle(listOf(a, b), b, Random(seed)).first().name)
        }
    }

    @Test
    fun `reshuffle of a single item returns it even when it was the last shown`() {
        val only = item("only")

        val result = SlideshowPlaylist.reshuffle(listOf(only), only, Random(1))

        assertEquals(listOf("only"), names(result))
    }

    @Test
    fun `reshuffle with no last shown item is a permutation`() {
        val playlist = (1..8).map { item("i$it") }

        val result = SlideshowPlaylist.reshuffle(playlist, null, Random(5))

        assertEquals(playlist.toSet(), result.toSet())
        assertEquals(playlist.size, result.size)
    }

    @Test
    fun `reshuffle of an empty playlist is empty`() {
        assertTrue(SlideshowPlaylist.reshuffle(emptyList(), null, Random(1)).isEmpty())
    }

    // endregion
}
