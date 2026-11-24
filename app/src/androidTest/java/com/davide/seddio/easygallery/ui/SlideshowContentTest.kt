package com.davide.seddio.easygallery.ui

import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SlideshowTransition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SlideshowContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var cancelCount = 0
    private var togglePauseCount = 0

    // Written on the main thread by the Content, read by waitUntil on the test thread.
    @Volatile
    private var failedCount = 0

    private fun image(id: Int) = MediaItem(
        uri = Uri.parse("content://media/external/images/media/$id"),
        name = "image$id.jpg",
        dateAdded = 0L,
        dateModified = 0L,
        size = 1L,
        type = MediaType.IMAGE,
        bucketName = "Camera",
        folderPath = "/storage/emulated/0/DCIM/Camera"
    )

    private fun uiState(
        phase: SlideshowPhase,
        countdownRemaining: Int = 3,
        controlsVisible: Boolean = false,
        current: MediaItem = image(1)
    ) = SlideshowUiState(
        phase = phase,
        countdownRemaining = countdownRemaining,
        current = current,
        next = image(2),
        controlsVisible = controlsVisible,
        transition = SlideshowTransition.NONE
    )

    private fun setSlideshowContent(readState: () -> SlideshowUiState) {
        composeTestRule.setContent {
            SlideshowContent(
                state = readState(),
                onCancel = { cancelCount++ },
                onTogglePause = { togglePauseCount++ },
                onPauseForBackground = {},
                onItemShown = {},
                onItemFailed = { failedCount++ },
                onVideoEnded = {},
                onRecordVideoPosition = { _, _ -> },
                onConsumeVideoPosition = { _ -> null }
            )
        }
    }

    private fun string(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    @Test
    fun countdownShowsDigitsOnly() {
        setSlideshowContent { uiState(SlideshowPhase.COUNTDOWN, countdownRemaining = 3) }

        composeTestRule.onNodeWithTag("slideshow_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slideshow_countdown").assertIsDisplayed().assertTextEquals("3")
    }

    @Test
    fun countdownDigitsFollowState() {
        var state by mutableStateOf(uiState(SlideshowPhase.COUNTDOWN, countdownRemaining = 3))
        setSlideshowContent { state }

        state = uiState(SlideshowPhase.COUNTDOWN, countdownRemaining = 2)

        composeTestRule.onNodeWithTag("slideshow_countdown").assertTextEquals("2")
    }

    @Test
    fun tapDuringCountdownCancels() {
        setSlideshowContent { uiState(SlideshowPhase.COUNTDOWN) }

        composeTestRule.onNodeWithTag("slideshow_screen").performClick()

        assertEquals(1, cancelCount)
        assertEquals(0, togglePauseCount)
    }

    @Test
    fun backDuringCountdownCancels() {
        setSlideshowContent { uiState(SlideshowPhase.COUNTDOWN) }
        composeTestRule.waitForIdle()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeTestRule.waitForIdle()

        assertEquals(1, cancelCount)
    }

    @Test
    fun tapWhilePlayingTogglesPause() {
        setSlideshowContent { uiState(SlideshowPhase.PLAYING) }

        composeTestRule.onNodeWithTag("slideshow_screen").performClick()

        assertEquals(1, togglePauseCount)
        assertEquals(0, cancelCount)
    }

    @Test
    fun countdownHasNoControlsEvenWhenVisible() {
        setSlideshowContent { uiState(SlideshowPhase.COUNTDOWN, controlsVisible = true) }

        composeTestRule.onNodeWithTag("slideshow_pause_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("slideshow_close_button").assertDoesNotExist()
    }

    @Test
    fun pauseButtonInvokesTogglePause() {
        setSlideshowContent { uiState(SlideshowPhase.PLAYING, controlsVisible = true) }

        composeTestRule.onNodeWithTag("slideshow_pause_button").assertIsDisplayed().performClick()

        assertEquals(1, togglePauseCount)
        assertEquals(0, cancelCount)
    }

    @Test
    fun closeButtonInvokesCancel() {
        setSlideshowContent { uiState(SlideshowPhase.PLAYING, controlsVisible = true) }

        composeTestRule.onNodeWithTag("slideshow_close_button").assertIsDisplayed().performClick()

        assertEquals(1, cancelCount)
        assertEquals(0, togglePauseCount)
    }

    @Test
    fun controlsAreHiddenWhenNotVisible() {
        setSlideshowContent { uiState(SlideshowPhase.PLAYING, controlsVisible = false) }

        composeTestRule.onNodeWithTag("slideshow_pause_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("slideshow_close_button").assertDoesNotExist()
    }

    @Test
    fun controlsAppearWhenStateMakesThemVisible() {
        var state by mutableStateOf(uiState(SlideshowPhase.PLAYING, controlsVisible = false))
        setSlideshowContent { state }

        state = uiState(SlideshowPhase.PLAYING, controlsVisible = true)

        composeTestRule.onNodeWithTag("slideshow_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slideshow_close_button").assertIsDisplayed()
    }

    @Test
    fun controlsCarryContentDescriptions() {
        setSlideshowContent { uiState(SlideshowPhase.PLAYING, controlsVisible = true) }

        composeTestRule.onNodeWithContentDescription(string(R.string.slideshow_pause)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.slideshow_close)).assertIsDisplayed()
    }

    @Test
    fun pauseButtonLabelFlipsWithPhase() {
        var state by mutableStateOf(uiState(SlideshowPhase.PLAYING, controlsVisible = true))
        setSlideshowContent { state }

        composeTestRule.onNodeWithContentDescription(string(R.string.slideshow_pause)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.slideshow_resume)).assertDoesNotExist()

        state = uiState(SlideshowPhase.PAUSED, controlsVisible = true)

        composeTestRule.onNodeWithContentDescription(string(R.string.slideshow_resume)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.slideshow_pause)).assertDoesNotExist()
    }

    // The never-renders and ended-before-reported paths need a decodable video and cannot be faked: the player is
    // created inside the screen. A video that cannot be opened exercises the failure report without any media.
    @Test
    fun videoThatCannotBeOpenedIsReportedAsFailed() {
        val missingVideo = MediaItem(
            uri = Uri.parse("content://media/external/video/media/2147483000"),
            name = "missing.mp4",
            dateAdded = 0L,
            dateModified = 0L,
            size = 1L,
            type = MediaType.VIDEO,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
        setSlideshowContent { uiState(SlideshowPhase.PLAYING, current = missingVideo) }

        composeTestRule.waitUntil(timeoutMillis = 15_000) { failedCount > 0 }
    }
}
