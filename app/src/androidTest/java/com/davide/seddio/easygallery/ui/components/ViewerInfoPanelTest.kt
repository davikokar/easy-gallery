package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaCoordinates
import com.davide.seddio.easygallery.data.ResolvedDateTaken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ViewerInfoPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = appContext.resources.configuration.locales[0]
    private val instant = Instant.parse("2024-01-05T15:04:00Z").toEpochMilli()

    private val coordinates = MediaCoordinates(latitude = 45.464211, longitude = 9.191383)

    private fun panel(
        name: String = "IMG_0001.jpg",
        path: String = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
        dateTaken: ResolvedDateTaken? = ResolvedDateTaken(instant, isCaptureDate = true),
        coords: MediaCoordinates? = null,
        onOpenLocation: (MediaCoordinates) -> Unit = {}
    ): @Composable () -> Unit = {
        ViewerInfoPanel(
            name = name,
            path = path,
            dateTaken = dateTaken,
            coordinates = coords,
            onOpenLocation = onOpenLocation
        )
    }

    private fun showPanelInArea(
        info: @Composable () -> Unit,
        onMediaTap: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            ViewerMediaArea(
                showInfo = true,
                infoContent = info,
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("viewer_test_media")
                        .pointerInput(Unit) { detectTapGestures { onMediaTap() } }
                )
            }
        }
    }

    @Test
    fun panelShowsNamePathAndDateTaken() {
        val expectedDate = appContext.getString(
            R.string.properties_date_taken,
            formatMediaDateTime(instant, locale)
        )
        showPanelInArea(panel())

        composeTestRule.onNodeWithTag("viewer_info_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_info_path").assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedDate).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.properties_name, "IMG_0001.jpg"))
            .assertIsDisplayed()
    }

    @Test
    fun captureDateShowsTheDateTakenRowOnly() {
        val expectedDate = appContext.getString(
            R.string.properties_date_taken,
            formatMediaDateTime(instant, locale)
        )
        showPanelInArea(panel(dateTaken = ResolvedDateTaken(instant, isCaptureDate = true)))

        composeTestRule.onNodeWithTag("viewer_info_date_taken").assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedDate).assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_info_date_added").assertDoesNotExist()
    }

    @Test
    fun fallbackDateShowsTheDateAddedRowOnly() {
        val expectedDate = appContext.getString(
            R.string.properties_date_added,
            formatMediaDateTime(instant, locale)
        )
        showPanelInArea(panel(dateTaken = ResolvedDateTaken(instant, isCaptureDate = false)))

        composeTestRule.onNodeWithTag("viewer_info_date_added").assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedDate).assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_info_date_taken").assertDoesNotExist()
    }

    @Test
    fun noDateKnownHidesBothDateRows() {
        showPanelInArea(panel(dateTaken = null))

        composeTestRule.onNodeWithTag("viewer_info_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_info_date_taken").assertDoesNotExist()
        composeTestRule.onNodeWithTag("viewer_info_date_added").assertDoesNotExist()
    }

    @Test
    fun nonPositiveDatesHideBothRowsOnceFilteredByDisplayableDateTaken() {
        showPanelInArea(
            panel(dateTaken = displayableDateTaken(ResolvedDateTaken(0L, isCaptureDate = false)))
        )

        composeTestRule.onNodeWithTag("viewer_info_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_info_date_taken").assertDoesNotExist()
        composeTestRule.onNodeWithTag("viewer_info_date_added").assertDoesNotExist()
    }

    @Test
    fun gpsRowIsAbsentWithoutCoordinates() {
        showPanelInArea(panel(coords = null))

        composeTestRule.onNodeWithTag("viewer_info_gps").assertDoesNotExist()
    }

    @Test
    fun gpsRowOpensTheLocationWithItsCoordinates() {
        var opened: MediaCoordinates? = null
        showPanelInArea(panel(coords = coordinates, onOpenLocation = { opened = it }))

        composeTestRule.onNodeWithTag("viewer_info_gps").performClick()

        assertEquals(coordinates, opened)
    }

    @Test
    fun showingTheInfoDoesNotMoveOrResizeTheMediaOrTheBar() {
        var showInfo by mutableStateOf(false)
        composeTestRule.setContent {
            Column(Modifier.fillMaxSize()) {
                ViewerMediaArea(
                    showInfo = showInfo,
                    infoContent = panel(coords = coordinates),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    Box(Modifier.fillMaxSize().testTag("viewer_test_media"))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("viewer_test_bar")
                )
            }
        }
        val mediaBefore = composeTestRule.onNodeWithTag("viewer_test_media").fetchSemanticsNode().boundsInRoot
        val barBefore = composeTestRule.onNodeWithTag("viewer_test_bar").fetchSemanticsNode().boundsInRoot

        composeTestRule.runOnIdle { showInfo = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_info_overlay").assertIsDisplayed()
        val mediaShown = composeTestRule.onNodeWithTag("viewer_test_media").fetchSemanticsNode().boundsInRoot
        val barShown = composeTestRule.onNodeWithTag("viewer_test_bar").fetchSemanticsNode().boundsInRoot

        composeTestRule.runOnIdle { showInfo = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_info_overlay").assertDoesNotExist()
        val mediaAfter = composeTestRule.onNodeWithTag("viewer_test_media").fetchSemanticsNode().boundsInRoot

        assertEquals(mediaBefore, mediaShown)
        assertEquals(barBefore, barShown)
        assertEquals(mediaBefore, mediaAfter)
    }

    @Test
    fun panelSitsOverTheBottomEdgeOfTheMediaAndInsideIt() {
        showPanelInArea(panel(coords = coordinates))

        val media = composeTestRule.onNodeWithTag("viewer_test_media").fetchSemanticsNode().boundsInRoot
        val panel = composeTestRule.onNodeWithTag("viewer_info_overlay").fetchSemanticsNode().boundsInRoot

        assertTrue("panel $panel inside media $media", panel.left >= media.left && panel.right <= media.right)
        assertTrue("panel $panel inside media $media", panel.top >= media.top && panel.bottom <= media.bottom)
        assertTrue("panel is bottom aligned", media.bottom - panel.bottom < media.height / 4)
    }

    @Test
    fun longNameAndPathStayInsideTheMediaWidth() {
        val long = "a".repeat(400)
        showPanelInArea(panel(name = "$long.jpg", path = "/storage/emulated/0/$long/$long.jpg"))

        val media = composeTestRule.onNodeWithTag("viewer_test_media").fetchSemanticsNode().boundsInRoot
        val panel = composeTestRule.onNodeWithTag("viewer_info_overlay").fetchSemanticsNode().boundsInRoot

        assertTrue("panel $panel inside media $media", panel.left >= media.left && panel.right <= media.right)
        assertTrue("panel is not taller than the media", panel.height <= media.height)
    }

    @Test
    fun tapOnPanelTextReachesTheMediaBelowIt() {
        var mediaTaps by mutableIntStateOf(0)
        showPanelInArea(panel(), onMediaTap = { mediaTaps++ })

        composeTestRule.onNodeWithTag("viewer_info_name").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, mediaTaps)
    }

    @Test
    fun textStartsAtTheLeftInLeftToRight() {
        showPanelInArea(panel())

        val panel = composeTestRule.onNodeWithTag("viewer_info_overlay").fetchSemanticsNode().boundsInRoot
        val name = composeTestRule.onNodeWithTag("viewer_info_name").fetchSemanticsNode().boundsInRoot

        assertTrue("name $name starts at the left of $panel", name.left - panel.left < panel.width / 4)
    }

    @Test
    fun textStartsAtTheRightInRightToLeft() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ViewerMediaArea(
                    showInfo = true,
                    infoContent = panel(),
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        val panel = composeTestRule.onNodeWithTag("viewer_info_overlay").fetchSemanticsNode().boundsInRoot
        val name = composeTestRule.onNodeWithTag("viewer_info_name").fetchSemanticsNode().boundsInRoot

        assertTrue("name $name starts at the right of $panel", panel.right - name.right < panel.width / 4)
    }
}
