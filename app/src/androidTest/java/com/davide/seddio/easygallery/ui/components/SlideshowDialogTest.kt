package com.davide.seddio.easygallery.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SlideshowOptions
import com.davide.seddio.easygallery.data.SlideshowOrder
import com.davide.seddio.easygallery.data.SlideshowOrientation
import com.davide.seddio.easygallery.data.SlideshowTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SlideshowDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private fun item(id: Int, type: MediaType, width: Int?, height: Int?) = MediaItem(
        uri = Uri.parse("content://media/external/images/media/$id"),
        name = "item$id",
        dateAdded = 1000L,
        dateModified = 1000L,
        size = 100L,
        type = type,
        bucketName = "Pictures",
        folderPath = "/storage/emulated/0/Pictures",
        displayWidth = width,
        displayHeight = height
    )

    // Landscape image, portrait image, landscape video, landscape GIF.
    private val baseOrder = listOf(
        item(1, MediaType.IMAGE, 200, 100),
        item(2, MediaType.IMAGE, 100, 200),
        item(3, MediaType.VIDEO, 200, 100),
        item(4, MediaType.GIF, 200, 100)
    )

    private fun countText(count: Int) =
        appContext.resources.getQuantityString(R.plurals.slideshow_count, count, count)

    private fun secondsText(seconds: Int) =
        appContext.resources.getQuantityString(R.plurals.slideshow_interval_seconds, seconds, seconds)

    private fun expandOptions() {
        composeTestRule.onNodeWithTag("slideshow_options_toggle").performClick()
    }

    @Test
    fun dialogShowsTheLiveCountAndAnEnabledPlayButton() {
        composeTestRule.setContent { Harness(count = 3) }

        composeTestRule.onNodeWithTag("slideshow_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(3))
        composeTestRule.onNodeWithTag("slideshow_play_button").assertIsEnabled()
    }

    @Test
    fun countOfOneUsesTheSingularForm() {
        composeTestRule.setContent { Harness(count = 1) }

        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(1))
    }

    @Test
    fun zeroCountSaysNothingToPlayAndDisablesPlay() {
        var plays = 0
        composeTestRule.setContent { Harness(count = 0, onPlay = { plays++ }) }

        composeTestRule
            .onNodeWithTag("slideshow_count")
            .assertTextEquals(appContext.getString(R.string.slideshow_nothing_to_play))
        composeTestRule.onNodeWithTag("slideshow_play_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("slideshow_play_button").performClick()

        composeTestRule.runOnIdle { assertEquals(0, plays) }
    }

    @Test
    fun optionsAreCollapsedWhenTheDialogOpens() {
        composeTestRule.setContent { Harness() }

        composeTestRule.onAllNodesWithTag("slideshow_options_area").assertCountEquals(0)
        composeTestRule.onNodeWithTag("slideshow_options_toggle").assertIsOff()
    }

    @Test
    fun optionsToggleExpandsAndCollapsesTheArea() {
        composeTestRule.setContent { Harness() }

        expandOptions()
        composeTestRule.onNodeWithTag("slideshow_options_area").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slideshow_options_toggle").assertIsOn()

        expandOptions()
        composeTestRule.onAllNodesWithTag("slideshow_options_area").assertCountEquals(0)
        composeTestRule.onNodeWithTag("slideshow_options_toggle").assertIsOff()
    }

    @Test
    fun optionsToggleHasTheOptionsDescriptionAndATouchTargetOf48dp() {
        composeTestRule.setContent { Harness() }

        composeTestRule
            .onNodeWithTag("slideshow_options_toggle")
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.slideshow_options))
            .assertIsDisplayed()
    }

    @Test
    fun intervalStepperChangesTheValueByOneSecond() {
        composeTestRule.setContent { Harness(initial = SlideshowOptions(intervalSeconds = 3)) }
        expandOptions()

        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(3))
        composeTestRule.onNodeWithTag("slideshow_interval_increase").performClick()
        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(4))
        composeTestRule.onNodeWithTag("slideshow_interval_decrease").performClick()
        composeTestRule.onNodeWithTag("slideshow_interval_decrease").performClick()
        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(2))
    }

    @Test
    fun intervalStepperStopsAtOneSecond() {
        composeTestRule.setContent { Harness(initial = SlideshowOptions(intervalSeconds = 1)) }
        expandOptions()

        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(1))
        composeTestRule.onNodeWithTag("slideshow_interval_decrease").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("slideshow_interval_increase").assertIsEnabled()
    }

    @Test
    fun intervalStepperStopsAtSixtySeconds() {
        composeTestRule.setContent { Harness(initial = SlideshowOptions(intervalSeconds = 60)) }
        expandOptions()

        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(60))
        composeTestRule.onNodeWithTag("slideshow_interval_increase").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("slideshow_interval_decrease").assertIsEnabled()
    }

    @Test
    fun intervalStepperButtonsHaveDescriptionsAndATouchTargetOf48dp() {
        composeTestRule.setContent { Harness() }
        expandOptions()

        composeTestRule
            .onNodeWithTag("slideshow_interval_decrease")
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeTestRule
            .onNodeWithTag("slideshow_interval_increase")
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.slideshow_interval_decrease))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(appContext.getString(R.string.slideshow_interval_increase))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.slideshow_interval_videos_note))
            .assertIsDisplayed()
    }

    @Test
    fun switchesAndChoicesReflectTheOptionsAndEditThem() {
        composeTestRule.setContent { Harness(initial = SlideshowOptions()) }
        expandOptions()

        composeTestRule.onNodeWithTag("slideshow_include_videos").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithTag("slideshow_include_gifs").performScrollTo().assertIsOff()
        composeTestRule.onNodeWithTag("slideshow_loop").performScrollTo().assertIsOff()
        composeTestRule.onNodeWithTag("slideshow_transition_fade").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithTag("slideshow_order_ascending").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithTag("slideshow_orientation_all").performScrollTo().assertIsSelected()

        composeTestRule.onNodeWithTag("slideshow_include_gifs").performClick().assertIsOn()
        composeTestRule.onNodeWithTag("slideshow_include_videos").performClick().assertIsOff()
        composeTestRule.onNodeWithTag("slideshow_loop").performScrollTo().performClick().assertIsOn()
        composeTestRule.onNodeWithTag("slideshow_transition_none").performScrollTo().performClick().assertIsSelected()
        composeTestRule.onNodeWithTag("slideshow_order_random").performScrollTo().performClick().assertIsSelected()
        composeTestRule.onNodeWithTag("slideshow_orientation_portrait").performScrollTo().performClick().assertIsSelected()
        composeTestRule.onNodeWithTag("slideshow_order_ascending").assertIsNotSelected()
    }

    @Test
    fun playAndCancelInvokeTheirCallbacks() {
        var plays = 0
        var dismisses = 0
        composeTestRule.setContent { Harness(onPlay = { plays++ }, onDismiss = { dismisses++ }) }

        composeTestRule.onNodeWithTag("slideshow_play_button").performClick()
        composeTestRule.onNodeWithText(appContext.getString(R.string.action_cancel)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, plays)
            assertEquals(1, dismisses)
        }
    }

    @Test
    fun hostCountFollowsTheDraftOverTheSameBaseOrder() {
        composeTestRule.setContent {
            SlideshowDialogHost(
                savedOptions = SlideshowOptions(),
                baseOrder = baseOrder,
                onPlay = {},
                onDismiss = {}
            )
        }

        // Defaults: images and videos, no GIFs.
        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(3))
        expandOptions()
        composeTestRule.onNodeWithTag("slideshow_include_gifs").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(4))
        composeTestRule.onNodeWithTag("slideshow_include_videos").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(3))
        composeTestRule.onNodeWithTag("slideshow_orientation_landscape").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(2))
        composeTestRule.onNodeWithTag("slideshow_orientation_portrait").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(1))
    }

    @Test
    fun hostCountIsUnaffectedByTheOrderOption() {
        composeTestRule.setContent {
            SlideshowDialogHost(
                savedOptions = SlideshowOptions(),
                baseOrder = baseOrder,
                onPlay = {},
                onDismiss = {}
            )
        }

        expandOptions()
        composeTestRule.onNodeWithTag("slideshow_order_random").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("slideshow_count").assertTextEquals(countText(3))
    }

    @Test
    fun hostShowsNothingToPlayForAnEmptyBaseOrder() {
        composeTestRule.setContent {
            SlideshowDialogHost(
                savedOptions = SlideshowOptions(),
                baseOrder = emptyList(),
                onPlay = {},
                onDismiss = {}
            )
        }

        composeTestRule
            .onNodeWithTag("slideshow_count")
            .assertTextEquals(appContext.getString(R.string.slideshow_nothing_to_play))
        composeTestRule.onNodeWithTag("slideshow_play_button").assertIsNotEnabled()
    }

    @Test
    fun hostStartsFromTheSavedOptions() {
        composeTestRule.setContent {
            SlideshowDialogHost(
                savedOptions = SlideshowOptions(
                    intervalSeconds = 7,
                    transition = SlideshowTransition.NONE,
                    loop = true
                ),
                baseOrder = baseOrder,
                onPlay = {},
                onDismiss = {}
            )
        }
        expandOptions()

        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(7))
        composeTestRule.onNodeWithTag("slideshow_loop").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithTag("slideshow_transition_none").performScrollTo().assertIsSelected()
    }

    @Test
    fun hostPlayPassesTheEditedDraft() {
        var played: SlideshowOptions? = null
        composeTestRule.setContent {
            SlideshowDialogHost(
                savedOptions = SlideshowOptions(),
                baseOrder = baseOrder,
                onPlay = { played = it },
                onDismiss = {}
            )
        }

        expandOptions()
        composeTestRule.onNodeWithTag("slideshow_interval_increase").performClick()
        composeTestRule.onNodeWithTag("slideshow_interval_increase").performClick()
        composeTestRule.onNodeWithTag("slideshow_order_random").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("slideshow_orientation_landscape").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("slideshow_play_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                SlideshowOptions(
                    intervalSeconds = 5,
                    order = SlideshowOrder.RANDOM,
                    orientation = SlideshowOrientation.LANDSCAPE_ONLY
                ),
                played
            )
        }
    }

    @Test
    fun hostCancelDiscardsTheDraftWithoutPlaying() {
        var played: SlideshowOptions? = null
        var dismisses = 0
        composeTestRule.setContent {
            SlideshowDialogHost(
                savedOptions = SlideshowOptions(),
                baseOrder = baseOrder,
                onPlay = { played = it },
                onDismiss = { dismisses++ }
            )
        }

        expandOptions()
        composeTestRule.onNodeWithTag("slideshow_interval_increase").performClick()
        composeTestRule.onNodeWithText(appContext.getString(R.string.action_cancel)).performClick()

        composeTestRule.runOnIdle {
            assertNull(played)
            assertEquals(1, dismisses)
        }
    }

    @Test
    fun hostKeepsTheDraftAndTheExpandedStateAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            SlideshowDialogHost(
                savedOptions = SlideshowOptions(),
                baseOrder = baseOrder,
                onPlay = {},
                onDismiss = {}
            )
        }

        expandOptions()
        composeTestRule.onNodeWithTag("slideshow_interval_increase").performClick()
        composeTestRule.onNodeWithTag("slideshow_loop").performScrollTo().performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag("slideshow_options_area").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(4))
        composeTestRule.onNodeWithTag("slideshow_loop").performScrollTo().assertIsOn()
    }

    @Test
    fun dialogStaysUsableInARightToLeftLayout() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Harness(initial = SlideshowOptions(intervalSeconds = 3))
            }
        }
        expandOptions()

        composeTestRule.onNodeWithTag("slideshow_interval_increase").performClick()

        composeTestRule.onNodeWithTag("slideshow_interval_value").assertTextEquals(secondsText(4))
        composeTestRule.onNodeWithTag("slideshow_play_button").assertIsDisplayed()
    }

    @Composable
    private fun Harness(
        initial: SlideshowOptions = SlideshowOptions(),
        count: Int = 5,
        onPlay: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        var options by remember { mutableStateOf(initial) }
        var expanded by remember { mutableStateOf(false) }
        SlideshowDialog(
            options = options,
            count = count,
            optionsExpanded = expanded,
            onOptionsChange = { options = it },
            onOptionsExpandedChange = { expanded = it },
            onPlay = onPlay,
            onDismiss = onDismiss
        )
    }

}
