package com.davide.seddio.easygallery.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewerFilmstripTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mainState: PagerState
    private lateinit var stripState: PagerState
    private val drivenChanges = mutableListOf<Boolean>()

    private fun media(count: Int): List<MediaItem> = List(count) { index ->
        MediaItem(
            uri = Uri.parse("content://media/external/images/media/$index"),
            name = "IMG_$index.jpg",
            dateAdded = index.toLong(),
            dateModified = index.toLong(),
            size = 1L,
            type = MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
    }

    // Stand-in for the viewer: a main pager above the Filmstrip, wired exactly as FullImageScreen wires them.
    private fun showFilmstrip(
        count: Int = 40,
        initialPage: Int = 0,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                val items = remember(count) { media(count) }
                mainState = rememberPagerState(initialPage = initialPage) { count }
                stripState = rememberPagerState(initialPage = initialPage) { count }
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    HorizontalPager(
                        state = mainState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("test_main_pager")
                    ) { page ->
                        Box(Modifier.fillMaxSize().testTag("test_main_page_$page"))
                    }
                    ViewerFilmstrip(
                        items = items,
                        mainState = mainState,
                        stripState = stripState,
                        onUserDrivenChange = { drivenChanges += it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun item(index: Int) = composeTestRule.onNodeWithTag("viewer_filmstrip_item_$index")

    private fun boundsOfItem(index: Int) = item(index).fetchSemanticsNode().boundsInRoot

    private fun stripBounds() = composeTestRule.onNodeWithTag("viewer_filmstrip").fetchSemanticsNode().boundsInRoot

    private fun mainPage(): Int = composeTestRule.runOnIdle { mainState.currentPage }

    private fun stripPage(): Int = composeTestRule.runOnIdle { stripState.currentPage }

    private fun settle() {
        composeTestRule.waitForIdle()
        repeat(5) { composeTestRule.mainClock.advanceTimeByFrame() }
        composeTestRule.waitForIdle()
    }

    private fun assertCentred(index: Int) {
        val item = boundsOfItem(index)
        val strip = stripBounds()
        assertEquals("item $index centre $item vs strip $strip", strip.center.x, item.center.x, 1.5f)
    }

    @Test
    fun firstItemIsCentredWithEmptySpaceBefore() {
        showFilmstrip(initialPage = 0)

        assertCentred(0)
        val first = boundsOfItem(0)
        val strip = stripBounds()
        assertTrue("room to the left of the first item", first.left - strip.left > first.width)
    }

    @Test
    fun openingOnAMiddleItemCentresIt() {
        showFilmstrip(initialPage = 17)

        assertCentred(17)
        assertEquals(17, mainPage())
        assertEquals(17, stripPage())
    }

    @Test
    fun lastItemCanReachTheCentre() {
        showFilmstrip(count = 12, initialPage = 11)

        assertCentred(11)
        val last = boundsOfItem(11)
        val strip = stripBounds()
        assertTrue("room to the right of the last item", strip.right - last.right > last.width)
    }

    @Test
    fun currentItemIsSelectedAndLargerThanItsNeighbours() {
        showFilmstrip(initialPage = 5)

        item(5).assertIsSelected()
        item(4).assertIsNotSelected()
        item(6).assertIsNotSelected()
        val current = boundsOfItem(5).width
        assertTrue("current $current vs previous ${boundsOfItem(4).width}", current > boundsOfItem(4).width)
        assertTrue("current $current vs next ${boundsOfItem(6).width}", current > boundsOfItem(6).width)
    }

    @Test
    fun eachItemIsDescribedByItsName() {
        showFilmstrip(initialPage = 0)

        item(0).assertContentDescriptionEquals("IMG_0.jpg")
        item(2).assertContentDescriptionEquals("IMG_2.jpg")
    }

    @Test
    fun tappingAnItemMovesTheMainPagerAndRecentresTheStrip() {
        showFilmstrip(initialPage = 0)

        item(3).performClick()
        settle()

        assertEquals(3, mainPage())
        assertEquals(3, stripPage())
        assertCentred(3)
        item(3).assertIsSelected()
    }

    @Test
    fun tappingTheCentredItemLeavesTheMainPagerWhereItIs() {
        showFilmstrip(initialPage = 4)

        item(4).performClick()
        settle()

        assertEquals(4, mainPage())
        assertCentred(4)
    }

    @Test
    fun swipingTheMainPagerRecentresTheFilmstrip() {
        showFilmstrip(initialPage = 0)

        composeTestRule.onNodeWithTag("test_main_pager").performTouchInput {
            swipe(start = Offset(width * 0.9f, centerY), end = Offset(width * 0.1f, centerY), durationMillis = 300)
        }
        settle()

        assertTrue("main moved on", mainPage() > 0)
        assertEquals(mainPage(), stripPage())
        assertCentred(mainPage())
    }

    @Test
    fun swipingTheFilmstripMovesTheMainPagerAndBothSettleOnTheSamePage() {
        showFilmstrip(initialPage = 0)

        composeTestRule.onNodeWithTag("viewer_filmstrip").performTouchInput {
            swipe(start = Offset(centerX + 3 * 48.dp.toPx(), centerY), end = Offset(centerX, centerY), durationMillis = 800)
        }
        settle()

        val main = mainPage()
        assertTrue("main moved on", main > 0)
        assertEquals(main, stripPage())
        assertCentred(main)

        settle()
        assertEquals("stable after more frames", main, mainPage())
        assertEquals("stable after more frames", main, stripPage())
    }

    @Test
    fun flingMovesMoreThanOneItem() {
        showFilmstrip(initialPage = 0)

        composeTestRule.onNodeWithTag("viewer_filmstrip").performTouchInput {
            swipe(start = Offset(width * 0.85f, centerY), end = Offset(width * 0.15f, centerY), durationMillis = 80)
        }
        settle()

        assertTrue("fling moved only to ${stripPage()}", stripPage() > 2)
        assertEquals(stripPage(), mainPage())
    }

    @Test
    fun userDrivenIsReportedWhileSwipingTheFilmstripAndClearedAfterwards() {
        showFilmstrip(initialPage = 0)

        composeTestRule.onNodeWithTag("viewer_filmstrip").performTouchInput {
            swipe(start = Offset(centerX + 3 * 48.dp.toPx(), centerY), end = Offset(centerX, centerY), durationMillis = 600)
        }
        settle()

        assertTrue("reported driven: $drivenChanges", drivenChanges.contains(true))
        assertFalse("still driven after settling: $drivenChanges", drivenChanges.last())
    }

    @Test
    fun tappingAnItemDoesNotReportUserDriven() {
        showFilmstrip(initialPage = 0)

        item(3).performClick()
        settle()

        assertFalse("tap reported driven: $drivenChanges", drivenChanges.contains(true))
    }

    @Test
    fun rightToLeftLayoutCentresFirstItemAndMirrors() {
        showFilmstrip(initialPage = 0, layoutDirection = LayoutDirection.Rtl)

        assertCentred(0)
        assertTrue(
            "next item is to the left of the first one in right-to-left",
            boundsOfItem(1).center.x < boundsOfItem(0).center.x
        )
    }
}
