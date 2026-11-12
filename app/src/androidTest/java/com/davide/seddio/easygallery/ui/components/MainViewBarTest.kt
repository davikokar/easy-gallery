package com.davide.seddio.easygallery.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val folders = "main_view_tab_folders"
    private val albums = "main_view_tab_albums"
    private val timeline = "main_view_tab_timeline"

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun leftOf(tag: String): Float =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left

    @Test
    fun tabsAreOrderedFoldersAlbumsTimeline() {
        composeTestRule.setContent {
            MainViewBar(selectedTab = MainViewTab.FOLDERS, onTabSelected = {})
        }

        composeTestRule.onNodeWithTag("main_view_bar").assertIsDisplayed()
        assertTrue(leftOf(folders) < leftOf(albums))
        assertTrue(leftOf(albums) < leftOf(timeline))
    }

    @Test
    fun tabsAreMirroredInRightToLeft() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MainViewBar(selectedTab = MainViewTab.FOLDERS, onTabSelected = {})
            }
        }

        assertTrue(leftOf(folders) > leftOf(albums))
        assertTrue(leftOf(albums) > leftOf(timeline))
    }

    @Test
    fun tabsShowTheirExistingTitles() {
        composeTestRule.setContent {
            MainViewBar(selectedTab = MainViewTab.FOLDERS, onTabSelected = {})
        }

        composeTestRule.onNodeWithTag(folders).assertTextEquals(string(R.string.folders_title))
        composeTestRule.onNodeWithTag(albums).assertTextEquals(string(R.string.albums_title))
        composeTestRule.onNodeWithTag(timeline).assertTextEquals(string(R.string.timeline_title))
    }

    @Test
    fun onlyTheSelectedTabIsMarkedSelected() {
        var selected by mutableStateOf(MainViewTab.FOLDERS)
        composeTestRule.setContent {
            MainViewBar(selectedTab = selected, onTabSelected = {})
        }

        composeTestRule.onNodeWithTag(folders).assertIsSelected()
        composeTestRule.onNodeWithTag(albums).assertIsNotSelected()
        composeTestRule.onNodeWithTag(timeline).assertIsNotSelected()

        selected = MainViewTab.TIMELINE
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(folders).assertIsNotSelected()
        composeTestRule.onNodeWithTag(albums).assertIsNotSelected()
        composeTestRule.onNodeWithTag(timeline).assertIsSelected()

        selected = MainViewTab.ALBUMS
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(albums).assertIsSelected()
        composeTestRule.onNodeWithTag(timeline).assertIsNotSelected()
    }

    @Test
    fun tabsExposeTheTabRole() {
        composeTestRule.setContent {
            MainViewBar(selectedTab = MainViewTab.FOLDERS, onTabSelected = {})
        }

        val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeTestRule.onNodeWithTag(folders).assert(isTab)
        composeTestRule.onNodeWithTag(albums).assert(isTab)
        composeTestRule.onNodeWithTag(timeline).assert(isTab)
    }

    @Test
    fun tappingEachTabReportsThatTab() {
        val taps = mutableListOf<MainViewTab>()
        composeTestRule.setContent {
            MainViewBar(selectedTab = MainViewTab.FOLDERS, onTabSelected = { taps += it })
        }

        composeTestRule.onNodeWithTag(timeline).performClick()
        composeTestRule.onNodeWithTag(albums).performClick()
        composeTestRule.onNodeWithTag(folders).performClick()

        assertEquals(listOf(MainViewTab.TIMELINE, MainViewTab.ALBUMS, MainViewTab.FOLDERS), taps)
    }

    @Test
    fun retappingTheSelectedTabStillCallsBack() {
        val taps = mutableListOf<MainViewTab>()
        composeTestRule.setContent {
            MainViewBar(selectedTab = MainViewTab.ALBUMS, onTabSelected = { taps += it })
        }

        composeTestRule.onNodeWithTag(albums).performClick()
        composeTestRule.onNodeWithTag(albums).performClick()

        assertEquals(listOf(MainViewTab.ALBUMS, MainViewTab.ALBUMS), taps)
    }

    @Test
    fun everyTabIsAtLeast48dpTall() {
        composeTestRule.setContent {
            MainViewBar(selectedTab = MainViewTab.FOLDERS, onTabSelected = {})
        }

        listOf(folders, albums, timeline).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun allTabsStayVisibleAndTappableAtFontScaleTwo() {
        val taps = mutableListOf<MainViewTab>()
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                MainViewBar(selectedTab = MainViewTab.TIMELINE, onTabSelected = { taps += it })
            }
        }

        listOf(folders, albums, timeline).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag(timeline).performClick()

        assertEquals(listOf(MainViewTab.TIMELINE), taps)
    }

    @Test
    fun displayModeMapsToTheTabOfTheSameView() {
        assertEquals(MainViewTab.FOLDERS, DisplayMode.FOLDERS.toMainViewTab())
        assertEquals(MainViewTab.ALBUMS, DisplayMode.ALBUMS.toMainViewTab())
        assertEquals(MainViewTab.TIMELINE, DisplayMode.TIMELINE.toMainViewTab())
    }
}
