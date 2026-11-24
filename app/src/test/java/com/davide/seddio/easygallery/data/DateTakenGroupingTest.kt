package com.davide.seddio.easygallery.data

import android.net.Uri
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

// Robolectric because the monthly header pattern comes from android.text.format.DateFormat.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class DateTakenGroupingTest {

    private val previousLocale = Locale.getDefault()
    private val previousZone = TimeZone.getDefault()

    @Before
    fun pinLocaleAndZone() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreLocaleAndZone() {
        Locale.setDefault(previousLocale)
        TimeZone.setDefault(previousZone)
    }

    private fun uri(id: Int): Uri = Uri.parse("content://media/external/images/media/$id")

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun seconds(iso: String) = Instant.parse(iso).epochSecond

    private fun item(
        id: Int,
        dateAdded: Long,
        dateModified: Long = dateAdded,
        mediaStoreDateTakenMillis: Long? = null,
        type: MediaType = MediaType.IMAGE
    ) = MediaItem(
        uri = uri(id),
        name = "$id.jpg",
        dateAdded = dateAdded,
        dateModified = dateModified,
        size = 1L,
        type = type,
        bucketName = "f",
        folderPath = "/f",
        mediaStoreDateTakenMillis = mediaStoreDateTakenMillis
    )

    private fun ids(items: List<MediaItem>) = items.map { it.name.substringBefore('.').toInt() }

    @Test
    fun `daily date taken groups by the indexed capture date and not by date added`() {
        val captured = item(1, dateAdded = seconds("2024-06-20T12:00:00Z"))
        val onlyAdded = item(2, dateAdded = seconds("2024-06-15T12:00:00Z"))
        val index = mapOf(uri(1).toString() to millis("2024-06-10T12:00:00Z"))

        val groups = GalleryTransformations.groupMedia(
            listOf(captured, onlyAdded), GroupByType.DATE_TAKEN_DAILY, SortOrder.DESCENDING, index
        )

        assertEquals(listOf("June 15, 2024", "June 10, 2024"), groups.keys.toList())
        assertEquals(listOf(2), ids(groups.getValue("June 15, 2024")))
        assertEquals(listOf(1), ids(groups.getValue("June 10, 2024")))
    }

    @Test
    fun `daily date taken groups an item with a MediaStore date but no index entry by that date`() {
        val storeDated = item(
            1,
            dateAdded = seconds("2024-06-20T12:00:00Z"),
            mediaStoreDateTakenMillis = millis("2023-01-05T08:00:00Z")
        )

        val groups = GalleryTransformations.groupMedia(
            listOf(storeDated), GroupByType.DATE_TAKEN_DAILY, SortOrder.DESCENDING, emptyMap()
        )

        assertEquals(listOf("January 5, 2023"), groups.keys.toList())
    }

    @Test
    fun `daily date taken groups an item indexed without a date by its MediaStore date`() {
        val storeDated = item(
            1,
            dateAdded = seconds("2024-06-20T12:00:00Z"),
            mediaStoreDateTakenMillis = millis("2023-01-05T08:00:00Z")
        )

        val groups = GalleryTransformations.groupMedia(
            listOf(storeDated), GroupByType.DATE_TAKEN_DAILY, SortOrder.DESCENDING, mapOf(uri(1).toString() to null)
        )

        assertEquals(listOf("January 5, 2023"), groups.keys.toList())
    }

    @Test
    fun `daily date taken falls back to date added when no capture date is known`() {
        val plain = item(1, dateAdded = seconds("2024-06-20T12:00:00Z"))

        val groups = GalleryTransformations.groupMedia(
            listOf(plain), GroupByType.DATE_TAKEN_DAILY, SortOrder.DESCENDING, emptyMap()
        )

        assertEquals(listOf("June 20, 2024"), groups.keys.toList())
    }

    @Test
    fun `date taken grouping orders items and groups by the resolved date in both directions`() {
        val index = mapOf(
            uri(1).toString() to millis("2024-01-10T12:00:00Z"),
            uri(2).toString() to millis("2024-03-10T12:00:00Z")
        )
        // Date added runs the opposite way to the resolved dates, and the input order is neither.
        val oldestCapture = item(1, dateAdded = seconds("2024-09-01T12:00:00Z"))
        val newestCapture = item(2, dateAdded = seconds("2024-02-01T12:00:00Z"))
        val storeDated = item(
            3,
            dateAdded = seconds("2024-05-01T12:00:00Z"),
            mediaStoreDateTakenMillis = millis("2024-02-10T12:00:00Z")
        )
        val input = listOf(storeDated, oldestCapture, newestCapture)

        val descending = GalleryTransformations.groupMedia(
            input, GroupByType.DATE_TAKEN_DAILY, SortOrder.DESCENDING, index
        )
        val ascending = GalleryTransformations.groupMedia(
            input, GroupByType.DATE_TAKEN_DAILY, SortOrder.ASCENDING, index
        )

        assertEquals(listOf(2, 3, 1), descending.values.flatMap { ids(it) })
        assertEquals(listOf(1, 3, 2), ascending.values.flatMap { ids(it) })
        assertEquals(listOf("March 10, 2024", "February 10, 2024", "January 10, 2024"), descending.keys.toList())
    }

    @Test
    fun `daily date taken labels today and yesterday from the resolved date`() {
        val nowMillis = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L
        // Date added is years ago; only the resolved date puts these under Today and Yesterday.
        val today = item(1, dateAdded = 1_000L, mediaStoreDateTakenMillis = nowMillis)
        val yesterday = item(2, dateAdded = 1_000L)
        // Date added is now, but the capture date is old, so it is not Today.
        val addedToday = item(3, dateAdded = nowMillis / 1000)
        val index = mapOf(
            uri(2).toString() to nowMillis - dayMillis,
            uri(3).toString() to millis("2020-02-02T12:00:00Z")
        )

        val groups = GalleryTransformations.groupMedia(
            listOf(today, yesterday, addedToday),
            GroupByType.DATE_TAKEN_DAILY, SortOrder.DESCENDING, index,
            todayLabel = "Today!", yesterdayLabel = "Yesterday!"
        )

        assertEquals(listOf(1), ids(groups.getValue("Today!")))
        assertEquals(listOf(2), ids(groups.getValue("Yesterday!")))
        assertEquals(listOf(3), ids(groups.getValue("February 2, 2020")))
    }

    @Test
    fun `monthly date taken groups and orders by the resolved date`() {
        val index = mapOf(uri(1).toString() to millis("2023-12-31T12:00:00Z"))
        val captured = item(1, dateAdded = seconds("2024-06-20T12:00:00Z"))
        val storeDated = item(
            2,
            dateAdded = seconds("2024-06-20T12:00:00Z"),
            mediaStoreDateTakenMillis = millis("2024-02-03T12:00:00Z")
        )
        val plain = item(3, dateAdded = seconds("2024-06-02T12:00:00Z"))

        val groups = GalleryTransformations.groupMedia(
            listOf(captured, storeDated, plain), GroupByType.DATE_TAKEN_MONTHLY, SortOrder.DESCENDING, index
        )

        assertEquals(listOf("June 2024", "February 2024", "December 2023"), groups.keys.toList())
        assertEquals(listOf(3), ids(groups.getValue("June 2024")))
        assertEquals(listOf(2), ids(groups.getValue("February 2024")))
        assertEquals(listOf(1), ids(groups.getValue("December 2023")))
    }

    @Test
    fun `last modified grouping ignores the resolved date taken`() {
        val index = mapOf(uri(1).toString() to millis("2019-01-01T12:00:00Z"))
        val first = item(
            1,
            dateAdded = seconds("2024-06-20T12:00:00Z"),
            dateModified = seconds("2024-03-01T12:00:00Z"),
            mediaStoreDateTakenMillis = millis("2018-01-01T12:00:00Z")
        )
        val second = item(
            2,
            dateAdded = seconds("2024-06-20T12:00:00Z"),
            dateModified = seconds("2024-04-01T12:00:00Z")
        )

        val daily = GalleryTransformations.groupMedia(
            listOf(first, second), GroupByType.LAST_MODIFIED_DAILY, SortOrder.DESCENDING, index
        )
        val monthly = GalleryTransformations.groupMedia(
            listOf(first, second), GroupByType.LAST_MODIFIED_MONTHLY, SortOrder.ASCENDING, index
        )

        assertEquals(listOf("April 1, 2024", "March 1, 2024"), daily.keys.toList())
        assertEquals(listOf("March 2024", "April 2024"), monthly.keys.toList())
    }

    @Test
    fun `file type grouping and none grouping ignore the resolved date taken`() {
        val index = mapOf(uri(1).toString() to millis("2019-01-01T12:00:00Z"))
        val video = item(1, dateAdded = 1L, type = MediaType.VIDEO)
        val image = item(2, dateAdded = 2L)
        val labels = mapOf(MediaType.IMAGE to "Images", MediaType.VIDEO to "Videos")

        val byType = GalleryTransformations.groupMedia(
            listOf(video, image), GroupByType.FILE_TYPE, SortOrder.ASCENDING, index, fileTypeLabels = labels
        )
        val none = GalleryTransformations.groupMedia(
            listOf(video, image), GroupByType.NONE, SortOrder.DESCENDING, index
        )

        assertEquals(listOf("Images", "Videos"), byType.keys.toList())
        assertEquals(mapOf("" to listOf(video, image)), none)
    }
}
