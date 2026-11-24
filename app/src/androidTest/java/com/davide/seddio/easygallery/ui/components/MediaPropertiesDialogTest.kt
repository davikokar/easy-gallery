package com.davide.seddio.easygallery.ui.components

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MediaPropertiesDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = appContext.resources.configuration.locales[0]

    private val addedSeconds = Instant.parse("2024-03-10T08:00:00Z").epochSecond
    private val capturedMillis = Instant.parse("2019-07-04T12:30:00Z").toEpochMilli()

    private val item = MediaItem(
        uri = Uri.parse("content://media/external/images/media/1"),
        name = "image.jpg",
        dateAdded = addedSeconds,
        dateModified = addedSeconds,
        size = 100L,
        type = MediaType.IMAGE,
        bucketName = "Pictures",
        folderPath = "/storage/emulated/0/Pictures"
    )
    private val second = item.copy(
        uri = Uri.parse("content://media/external/images/media/2"),
        name = "image2.jpg"
    )

    private fun show(media: List<MediaItem>, indexed: Map<String, Long?> = emptyMap()) {
        composeTestRule.setContent {
            MediaPropertiesDialog(
                media = media,
                indexedDateTakenByMediaUri = indexed,
                onDismiss = {}
            )
        }
    }

    @Test
    fun indexedCaptureDateShowsTheDateTakenRowOnly() {
        show(listOf(item), indexed = mapOf(item.uri.toString() to capturedMillis))

        composeTestRule.onNodeWithTag("media_properties_date_taken").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            appContext.getString(R.string.properties_date_taken, formatMediaDateTime(capturedMillis, locale))
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_properties_date_added").assertDoesNotExist()
    }

    @Test
    fun mediaStoreDateTakenShowsTheDateTakenRowWhenNotIndexed() {
        show(listOf(item.copy(mediaStoreDateTakenMillis = capturedMillis)))

        composeTestRule.onNodeWithText(
            appContext.getString(R.string.properties_date_taken, formatMediaDateTime(capturedMillis, locale))
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_properties_date_added").assertDoesNotExist()
    }

    @Test
    fun noCaptureDateShowsTheDateAddedRowOnly() {
        show(listOf(item))

        composeTestRule.onNodeWithTag("media_properties_date_added").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            appContext.getString(R.string.properties_date_added, formatMediaDateTime(addedSeconds * 1000L, locale))
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_properties_date_taken").assertDoesNotExist()
    }

    @Test
    fun indexedWithoutDateFallsBackToDateAdded() {
        show(listOf(item), indexed = mapOf(item.uri.toString() to null))

        composeTestRule.onNodeWithTag("media_properties_date_added").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_properties_date_taken").assertDoesNotExist()
    }

    @Test
    fun nonPositiveDateAddedHidesBothRows() {
        show(listOf(item.copy(dateAdded = 0L)))

        composeTestRule.onNodeWithTag("media_properties_date_taken").assertDoesNotExist()
        composeTestRule.onNodeWithTag("media_properties_date_added").assertDoesNotExist()
    }

    @Test
    fun severalItemsShowNoDateRow() {
        show(listOf(item, second), indexed = mapOf(item.uri.toString() to capturedMillis))

        composeTestRule.onNodeWithTag("media_properties_date_taken").assertDoesNotExist()
        composeTestRule.onNodeWithTag("media_properties_date_added").assertDoesNotExist()
    }
}
