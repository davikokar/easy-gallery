package com.davide.seddio.easygallery.ui

import android.net.Uri
import app.cash.turbine.test
import com.davide.seddio.easygallery.data.InMemorySlideshowOptionsStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SlideshowOptions
import com.davide.seddio.easygallery.data.SlideshowOrder
import com.davide.seddio.easygallery.data.SlideshowTransition
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = InMemorySlideshowOptionsStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(random: Random = Random(7)) = SlideshowViewModel(
        application = mockk(relaxed = true),
        store = store,
        random = random,
        dispatcher = dispatcher,
        monotonicMillis = { dispatcher.scheduler.currentTime }
    )

    private fun item(name: String, type: MediaType = MediaType.IMAGE) = MediaItem(
        uri = mockk<Uri>(),
        name = name,
        dateAdded = 1L,
        dateModified = 1L,
        size = 1L,
        type = type,
        bucketName = "Camera",
        folderPath = "/storage/emulated/0/DCIM/Camera"
    )

    private fun advance(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    private fun SlideshowViewModel.currentName() = state.value!!.current.name

    // Starts a slideshow and runs the three countdown seconds, leaving it PLAYING on its first item.
    private fun SlideshowViewModel.startPlaying(items: List<MediaItem>, options: SlideshowOptions = SlideshowOptions()) {
        start(items, options)
        advance(3000)
    }

    // The screen's part: shows each item, then lets its interval pass. Returns the names in the order shown.
    private fun SlideshowViewModel.playNames(count: Int, intervalMillis: Long = 3000): List<String> {
        val seen = mutableListOf<String>()
        repeat(count) {
            seen += currentName()
            onItemShown()
            advance(intervalMillis)
        }
        return seen
    }

    // ---- options ----

    @Test
    fun `saved options start as what the store holds`() {
        store.save(SlideshowOptions(intervalSeconds = 9))

        val viewModel = createViewModel()

        assertEquals(9, viewModel.savedOptions.value.intervalSeconds)
    }

    @Test
    fun `start saves the options to the store and updates savedOptions`() = runTest(dispatcher) {
        val chosen = SlideshowOptions(intervalSeconds = 12, loop = true, order = SlideshowOrder.DESCENDING)
        val viewModel = createViewModel()

        viewModel.savedOptions.test {
            assertEquals(SlideshowOptions(), awaitItem())

            viewModel.start(listOf(item("a")), chosen)

            assertEquals(chosen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(chosen, store.load())
    }

    @Test
    fun `start with an empty playlist does nothing and saves nothing`() {
        val viewModel = createViewModel()

        viewModel.start(listOf(item("v", MediaType.VIDEO)), SlideshowOptions(includeVideos = false, intervalSeconds = 20))

        assertNull(viewModel.state.value)
        assertEquals(SlideshowOptions(), store.load())
        assertEquals(SlideshowOptions(), viewModel.savedOptions.value)
    }

    @Test
    fun `start with no media does nothing`() {
        val viewModel = createViewModel()

        viewModel.start(emptyList(), SlideshowOptions())

        assertNull(viewModel.state.value)
    }

    @Test
    fun `the state carries the chosen transition`() {
        val viewModel = createViewModel()

        viewModel.start(listOf(item("a")), SlideshowOptions(transition = SlideshowTransition.NONE))

        assertEquals(SlideshowTransition.NONE, viewModel.state.value!!.transition)
    }

    // ---- countdown ----

    @Test
    fun `the countdown shows 3 then 2 then 1 and then plays the first item`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val a = item("a")

        viewModel.state.test {
            assertNull(awaitItem())

            viewModel.start(listOf(a, item("b")), SlideshowOptions())
            val three = awaitItem()!!
            assertEquals(SlideshowPhase.COUNTDOWN, three.phase)
            assertEquals(3, three.countdownRemaining)
            assertEquals(a, three.current)

            advance(1000)
            assertEquals(2, awaitItem()!!.countdownRemaining)

            advance(1000)
            assertEquals(1, awaitItem()!!.countdownRemaining)

            advance(1000)
            val playing = awaitItem()!!
            assertEquals(SlideshowPhase.PLAYING, playing.phase)
            assertEquals(0, playing.countdownRemaining)
            assertEquals(a, playing.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no item advances during the countdown`() {
        val viewModel = createViewModel()
        viewModel.start(listOf(item("a"), item("b")), SlideshowOptions())

        viewModel.onItemShown()
        advance(2999)

        assertEquals(SlideshowPhase.COUNTDOWN, viewModel.state.value!!.phase)
        assertEquals("a", viewModel.currentName())
    }

    // ---- the interval ----

    @Test
    fun `an item advances after its interval from the moment it was shown`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 3))

        viewModel.onItemShown()
        advance(2999)
        assertEquals("a", viewModel.currentName())

        advance(1)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `the interval does not run until the screen reports the item shown`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))

        advance(60_000)

        assertEquals("a", viewModel.currentName())
    }

    @Test
    fun `an item shown during the countdown starts its interval when playing begins`() {
        val viewModel = createViewModel()
        viewModel.start(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 3))
        viewModel.onItemShown()

        advance(3000)
        assertEquals(SlideshowPhase.PLAYING, viewModel.state.value!!.phase)
        assertEquals("a", viewModel.currentName())

        advance(3000)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `reporting the same item shown again does not restart its interval`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 3))
        viewModel.onItemShown()
        advance(2000)

        viewModel.onItemShown()
        advance(1000)

        assertEquals("b", viewModel.currentName())
    }

    // ---- pause ----

    @Test
    fun `pausing freezes the interval and resuming continues with the remaining time`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 3))
        viewModel.onItemShown()
        advance(1000)

        viewModel.togglePause()
        advance(60_000)
        assertEquals(SlideshowPhase.PAUSED, viewModel.state.value!!.phase)
        assertEquals("a", viewModel.currentName())

        viewModel.togglePause()
        assertEquals(SlideshowPhase.PLAYING, viewModel.state.value!!.phase)
        advance(1999)
        assertEquals("a", viewModel.currentName())

        advance(1)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `pausing twice in one interval keeps counting only the time played`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 3))
        viewModel.onItemShown()

        advance(1000)
        viewModel.togglePause()
        advance(5000)
        viewModel.togglePause()
        advance(1000)
        viewModel.togglePause()
        advance(5000)
        viewModel.togglePause()
        advance(999)
        assertEquals("a", viewModel.currentName())

        advance(1)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `an item shown while paused starts its interval on resume`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 3))
        viewModel.togglePause()

        viewModel.onItemShown()
        advance(60_000)
        assertEquals("a", viewModel.currentName())

        viewModel.togglePause()
        advance(3000)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `togglePause does nothing during the countdown`() {
        val viewModel = createViewModel()
        viewModel.start(listOf(item("a")), SlideshowOptions())

        viewModel.togglePause()

        assertEquals(SlideshowPhase.COUNTDOWN, viewModel.state.value!!.phase)
    }

    @Test
    fun `togglePause with no slideshow does nothing`() {
        val viewModel = createViewModel()

        viewModel.togglePause()

        assertNull(viewModel.state.value)
    }

    // ---- background ----

    @Test
    fun `going to the background pauses and the slideshow stays paused`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 3))
        viewModel.onItemShown()
        advance(1000)

        viewModel.pauseForBackground()
        advance(60_000)

        assertEquals(SlideshowPhase.PAUSED, viewModel.state.value!!.phase)
        assertEquals("a", viewModel.currentName())

        viewModel.togglePause()
        advance(1999)
        assertEquals("a", viewModel.currentName())
        advance(1)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `going to the background while already paused changes nothing`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")))
        viewModel.togglePause()

        viewModel.pauseForBackground()

        assertEquals(SlideshowPhase.PAUSED, viewModel.state.value!!.phase)
    }

    @Test
    fun `going to the background during the countdown leaves it paused on the first item`() {
        val viewModel = createViewModel()
        viewModel.start(listOf(item("a"), item("b")), SlideshowOptions())
        advance(1000)

        viewModel.pauseForBackground()
        advance(60_000)

        val state = viewModel.state.value!!
        assertEquals(SlideshowPhase.PAUSED, state.phase)
        assertEquals(0, state.countdownRemaining)
        assertEquals("a", state.current.name)
    }

    @Test
    fun `going to the background with no slideshow does nothing`() {
        val viewModel = createViewModel()

        viewModel.pauseForBackground()

        assertNull(viewModel.state.value)
    }

    // ---- video ----

    @Test
    fun `a video does not advance on the timer but when it ends`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b")), SlideshowOptions(intervalSeconds = 3))

        viewModel.onItemShown()
        advance(60_000)
        assertEquals("v", viewModel.currentName())

        viewModel.onVideoEnded()
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `a video that ends before it was shown is ignored`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b")))

        viewModel.onVideoEnded()

        assertEquals("v", viewModel.currentName())
    }

    @Test
    fun `a video that never renders is skipped after the first frame bound`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b")))

        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS - 1)
        assertEquals("v", viewModel.currentName())

        advance(1)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `a video that shows its first frame is not skipped by the bound`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b")))
        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS - 1)

        viewModel.onItemShown()
        advance(10 * VIDEO_FIRST_FRAME_TIMEOUT_MILLIS)

        assertEquals("v", viewModel.currentName())
    }

    @Test
    fun `the bound is restarted for each video entered`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v1", MediaType.VIDEO), item("v2", MediaType.VIDEO), item("c")))
        viewModel.onItemShown()
        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS - 1)
        viewModel.onVideoEnded()
        assertEquals("v2", viewModel.currentName())

        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS - 1)
        assertEquals("v2", viewModel.currentName())

        advance(1)
        assertEquals("c", viewModel.currentName())
    }

    @Test
    fun `an image never starts the first frame bound`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(intervalSeconds = 60))

        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS * 3)

        assertEquals("a", viewModel.currentName())
    }

    @Test
    fun `a paused video does not count down the first frame bound and resuming restarts it`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b")))
        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS - 1)
        viewModel.togglePause()

        advance(10 * VIDEO_FIRST_FRAME_TIMEOUT_MILLIS)
        assertEquals("v", viewModel.currentName())

        viewModel.togglePause()
        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS - 1)
        assertEquals("v", viewModel.currentName())

        advance(1)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `leaving the app stops the first frame bound`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b")))

        viewModel.pauseForBackground()
        advance(10 * VIDEO_FIRST_FRAME_TIMEOUT_MILLIS)

        assertEquals("v", viewModel.currentName())
    }

    @Test
    fun `a first video starts its bound when the countdown ends and not before`() {
        val viewModel = createViewModel()
        viewModel.start(listOf(item("v", MediaType.VIDEO), item("b")), SlideshowOptions())

        advance(3000 + VIDEO_FIRST_FRAME_TIMEOUT_MILLIS - 1)
        assertEquals("v", viewModel.currentName())

        advance(1)
        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `a video skipped by the bound in a pass that showed nothing ends a looping slideshow`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO)), SlideshowOptions(loop = true))

        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS)

        assertNull(viewModel.state.value)
    }

    @Test
    fun `a video skipped by the bound leaves no timer to advance twice`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b"), item("c")), SlideshowOptions(intervalSeconds = 60))
        advance(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS)
        assertEquals("b", viewModel.currentName())

        viewModel.onItemShown()
        advance(59_999)

        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `a video reported shown and ended in the same turn advances once`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b"), item("c")))

        viewModel.onItemShown()
        viewModel.onVideoEnded()
        viewModel.onVideoEnded()

        assertEquals("b", viewModel.currentName())
        assertEquals(1L, viewModel.state.value!!.itemSerial)
    }

    @Test
    fun `onVideoEnded on an image is ignored`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))
        viewModel.onItemShown()

        viewModel.onVideoEnded()

        assertEquals("a", viewModel.currentName())
    }

    @Test
    fun `a video ending after the last item finishes a slideshow that does not loop`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO)))
        viewModel.onItemShown()

        viewModel.onVideoEnded()

        assertNull(viewModel.state.value)
    }

    @Test
    fun `an image after a video gets its own full interval`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO), item("b"), item("c")), SlideshowOptions(intervalSeconds = 3))
        viewModel.onItemShown()
        advance(10_000)
        viewModel.onVideoEnded()

        viewModel.onItemShown()
        advance(2999)
        assertEquals("b", viewModel.currentName())

        advance(1)
        assertEquals("c", viewModel.currentName())
    }

    @Test
    fun `the recorded video position is consumed once`() {
        val viewModel = createViewModel()
        val video = item("v", MediaType.VIDEO)
        viewModel.startPlaying(listOf(video, item("b")))
        viewModel.onItemShown()

        viewModel.recordVideoPosition(video.uri, 1234L)

        assertEquals(1234L, viewModel.consumeVideoPosition(video.uri))
        assertNull(viewModel.consumeVideoPosition(video.uri))
    }

    @Test
    fun `consuming for another video returns null and keeps the recorded position`() {
        val viewModel = createViewModel()
        val video = item("v", MediaType.VIDEO)
        viewModel.startPlaying(listOf(video, item("b")))
        viewModel.onItemShown()
        viewModel.recordVideoPosition(video.uri, 1234L)

        assertNull(viewModel.consumeVideoPosition(item("other", MediaType.VIDEO).uri))

        assertEquals(1234L, viewModel.consumeVideoPosition(video.uri))
    }

    @Test
    fun `a position for a video that is not the current item is not recorded`() {
        val viewModel = createViewModel()
        val first = item("v1", MediaType.VIDEO)
        val second = item("v2", MediaType.VIDEO)
        viewModel.startPlaying(listOf(first, second))
        viewModel.onItemShown()
        viewModel.onVideoEnded()

        // The screen read it from the outgoing video after the slideshow had moved on.
        viewModel.recordVideoPosition(first.uri, 1234L)

        assertNull(viewModel.consumeVideoPosition(first.uri))
        assertNull(viewModel.consumeVideoPosition(second.uri))
    }

    @Test
    fun `the recorded video position is dropped when the item changes`() {
        val viewModel = createViewModel()
        val video = item("v", MediaType.VIDEO)
        viewModel.startPlaying(listOf(video, item("b")))
        viewModel.onItemShown()
        viewModel.recordVideoPosition(video.uri, 1234L)

        viewModel.onVideoEnded()

        assertNull(viewModel.consumeVideoPosition(video.uri))
    }

    @Test
    fun `a position recorded for one video is not handed to the next video`() {
        val viewModel = createViewModel()
        val first = item("v1", MediaType.VIDEO)
        val second = item("v2", MediaType.VIDEO)
        viewModel.startPlaying(listOf(first, second))
        viewModel.onItemShown()
        viewModel.recordVideoPosition(first.uri, 1234L)

        viewModel.onVideoEnded()

        assertEquals("v2", viewModel.currentName())
        assertNull(viewModel.consumeVideoPosition(second.uri))
    }

    @Test
    fun `a position recorded for a looping video is not handed to its next pass`() {
        val viewModel = createViewModel()
        val video = item("v", MediaType.VIDEO)
        viewModel.startPlaying(listOf(video), SlideshowOptions(loop = true))
        viewModel.onItemShown()
        viewModel.recordVideoPosition(video.uri, 1234L)

        viewModel.onVideoEnded()

        assertEquals("v", viewModel.currentName())
        assertNull(viewModel.consumeVideoPosition(video.uri))
    }

    @Test
    fun `the recorded video position is dropped when a failure skips the video`() {
        val viewModel = createViewModel()
        val video = item("v", MediaType.VIDEO)
        viewModel.startPlaying(listOf(video, item("b")))
        viewModel.onItemShown()
        viewModel.recordVideoPosition(video.uri, 1234L)

        viewModel.onItemFailed()

        assertNull(viewModel.consumeVideoPosition(video.uri))
    }

    @Test
    fun `the recorded video position is dropped when the slideshow ends`() {
        val viewModel = createViewModel()
        val video = item("v", MediaType.VIDEO)
        viewModel.startPlaying(listOf(video))
        viewModel.recordVideoPosition(video.uri, 1234L)

        viewModel.cancel()

        assertNull(viewModel.consumeVideoPosition(video.uri))
    }

    @Test
    fun `a video position recorded with no slideshow is not kept`() {
        val viewModel = createViewModel()
        val video = item("v", MediaType.VIDEO)

        viewModel.recordVideoPosition(video.uri, 1234L)
        viewModel.startPlaying(listOf(video))

        assertNull(viewModel.consumeVideoPosition(video.uri))
    }

    // ---- failures ----

    @Test
    fun `a failed item is skipped at once`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b"), item("c")))

        viewModel.onItemFailed()

        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `a failed item does not leave the previous interval running`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b"), item("c")), SlideshowOptions(intervalSeconds = 3))
        viewModel.onItemShown()
        advance(2000)

        viewModel.onItemFailed()
        advance(60_000)

        assertEquals("b", viewModel.currentName())
    }

    @Test
    fun `the last item failing finishes a slideshow that does not loop`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))
        viewModel.onItemShown()
        advance(3000)

        viewModel.onItemFailed()

        assertNull(viewModel.state.value)
    }

    @Test
    fun `a looping pass in which nothing could be shown ends the slideshow`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(loop = true))

        viewModel.onItemFailed()
        assertEquals("b", viewModel.currentName())
        viewModel.onItemFailed()

        assertNull(viewModel.state.value)
    }

    @Test
    fun `a looping pass that showed something carries on past a failure`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")), SlideshowOptions(loop = true))
        viewModel.onItemShown()
        advance(3000)

        viewModel.onItemFailed()

        assertEquals("a", viewModel.currentName())
    }

    @Test
    fun `a failure during the countdown is ignored`() {
        val viewModel = createViewModel()
        viewModel.start(listOf(item("a"), item("b")), SlideshowOptions())

        viewModel.onItemFailed()

        assertEquals("a", viewModel.currentName())
    }

    // ---- looping and the end ----

    @Test
    fun `a non looping slideshow ends after the last interval`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))

        viewModel.playNames(2)

        assertNull(viewModel.state.value)
    }

    @Test
    fun `an ended slideshow stays ended`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")))
        viewModel.playNames(1)

        advance(60_000)

        assertNull(viewModel.state.value)
    }

    @Test
    fun `an ascending loop restarts at the first item in the same order`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b"), item("c")), SlideshowOptions(loop = true))

        val seen = viewModel.playNames(7)

        assertEquals(listOf("a", "b", "c", "a", "b", "c", "a"), seen)
    }

    @Test
    fun `a descending loop keeps its reversed order`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(
            listOf(item("a"), item("b"), item("c")),
            SlideshowOptions(loop = true, order = SlideshowOrder.DESCENDING)
        )

        val seen = viewModel.playNames(7)

        assertEquals(listOf("c", "b", "a", "c", "b", "a", "c"), seen)
    }

    @Test
    fun `a random loop reshuffles each pass and never opens on the item just shown`() {
        val viewModel = createViewModel(Random(42))
        val names = listOf("a", "b", "c", "d")
        viewModel.startPlaying(names.map { item(it) }, SlideshowOptions(loop = true, order = SlideshowOrder.RANDOM))

        val passes = viewModel.playNames(names.size * 30).chunked(names.size)

        passes.forEach { assertEquals(names.sorted(), it.sorted()) }
        passes.zipWithNext().forEach { (previous, following) -> assertNotEquals(previous.last(), following.first()) }
        assertTrue(passes.map { it.joinToString() }.distinct().size > 1)
    }

    @Test
    fun `a looping slideshow of one still keeps going without being told it was shown again`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")), SlideshowOptions(loop = true, intervalSeconds = 3))
        viewModel.onItemShown()
        val firstSerial = viewModel.state.value!!.itemSerial

        advance(3000)
        val secondSerial = viewModel.state.value!!.itemSerial
        advance(3000)
        val thirdSerial = viewModel.state.value!!.itemSerial

        assertEquals("a", viewModel.currentName())
        assertTrue(secondSerial > firstSerial)
        assertTrue(thirdSerial > secondSerial)
    }

    @Test
    fun `a looping slideshow of one video enters it again when it ends`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("v", MediaType.VIDEO)), SlideshowOptions(loop = true))
        viewModel.onItemShown()
        val firstSerial = viewModel.state.value!!.itemSerial

        viewModel.onVideoEnded()

        assertEquals("v", viewModel.currentName())
        assertTrue(viewModel.state.value!!.itemSerial > firstSerial)
    }

    // ---- cancel ----

    @Test
    fun `cancel during the countdown ends the slideshow`() {
        val viewModel = createViewModel()
        viewModel.start(listOf(item("a"), item("b")), SlideshowOptions())
        advance(1000)

        viewModel.cancel()
        advance(60_000)

        assertNull(viewModel.state.value)
    }

    @Test
    fun `cancel while playing ends the slideshow and stops its timer`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))
        viewModel.onItemShown()
        advance(1000)

        viewModel.cancel()
        advance(60_000)

        assertNull(viewModel.state.value)
    }

    @Test
    fun `cancel while paused ends the slideshow`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))
        viewModel.togglePause()

        viewModel.cancel()
        advance(60_000)

        assertNull(viewModel.state.value)
    }

    @Test
    fun `cancel with no slideshow does nothing`() {
        val viewModel = createViewModel()

        viewModel.cancel()

        assertNull(viewModel.state.value)
    }

    @Test
    fun `a new slideshow can start after one was cancelled`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))
        viewModel.cancel()

        viewModel.startPlaying(listOf(item("c")))

        assertEquals("c", viewModel.currentName())
        assertEquals(SlideshowPhase.PLAYING, viewModel.state.value!!.phase)
    }

    @Test
    fun `starting again replaces a running slideshow and its timers`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))
        viewModel.onItemShown()
        advance(2000)

        viewModel.start(listOf(item("c"), item("d")), SlideshowOptions())
        advance(1000)

        val state = viewModel.state.value!!
        assertEquals(SlideshowPhase.COUNTDOWN, state.phase)
        assertEquals(2, state.countdownRemaining)
        assertEquals("c", state.current.name)
    }

    // ---- snapshot ----

    @Test
    fun `the playlist is a snapshot that later changes to the source do not touch`() {
        val source = mutableListOf(item("a"), item("b"))
        val viewModel = createViewModel()
        viewModel.startPlaying(source)

        source.clear()
        source.add(item("z"))
        val seen = viewModel.playNames(2)

        assertEquals(listOf("a", "b"), seen)
    }

    @Test
    fun `the playlist is built with the options so excluded kinds never play`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(
            listOf(item("g", MediaType.GIF), item("v", MediaType.VIDEO), item("a")),
            SlideshowOptions(includeVideos = false, includeGifs = false)
        )

        val seen = viewModel.playNames(1)

        assertEquals(listOf("a"), seen)
        assertNull(viewModel.state.value)
    }

    // ---- next ----

    @Test
    fun `next is the item after the current one`() {
        val viewModel = createViewModel()
        val items = listOf(item("a"), item("b"), item("c"))
        viewModel.startPlaying(items)

        assertEquals(items[1], viewModel.state.value!!.next)

        viewModel.onItemShown()
        advance(3000)
        assertEquals(items[2], viewModel.state.value!!.next)
    }

    @Test
    fun `next is null on the last item when not looping`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a"), item("b")))
        viewModel.playNames(1)

        assertEquals("b", viewModel.currentName())
        assertNull(viewModel.state.value!!.next)
    }

    @Test
    fun `next wraps to the first item on the last item of an ascending loop`() {
        val viewModel = createViewModel()
        val items = listOf(item("a"), item("b"))
        viewModel.startPlaying(items, SlideshowOptions(loop = true))
        viewModel.playNames(1)

        assertEquals(items[0], viewModel.state.value!!.next)
    }

    @Test
    fun `next on the last item of a random loop is the item the next pass opens on`() {
        val viewModel = createViewModel(Random(5))
        val items = listOf("a", "b", "c").map { item(it) }
        viewModel.startPlaying(items, SlideshowOptions(loop = true, order = SlideshowOrder.RANDOM))
        viewModel.playNames(2)

        val last = viewModel.state.value!!
        val promised = last.next
        viewModel.playNames(1)

        assertEquals(promised, viewModel.state.value!!.current)
        assertNotEquals(last.current, promised)
    }

    @Test
    fun `next of a single item looped is that item`() {
        val viewModel = createViewModel()
        val only = item("a")
        viewModel.startPlaying(listOf(only), SlideshowOptions(loop = true))

        assertEquals(only, viewModel.state.value!!.next)
    }

    // ---- controls ----

    @Test
    fun `controls are hidden while playing until a pause or resume shows them`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")))

        assertFalse(viewModel.state.value!!.controlsVisible)
    }

    @Test
    fun `controls stay visible for as long as the slideshow is paused`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")))

        viewModel.togglePause()
        assertTrue(viewModel.state.value!!.controlsVisible)

        advance(60_000)
        assertTrue(viewModel.state.value!!.controlsVisible)
    }

    @Test
    fun `controls shown by a resume hide again after a short delay`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")))
        viewModel.togglePause()

        viewModel.togglePause()
        assertTrue(viewModel.state.value!!.controlsVisible)

        advance(2999)
        assertTrue(viewModel.state.value!!.controlsVisible)

        advance(1)
        assertFalse(viewModel.state.value!!.controlsVisible)
    }

    @Test
    fun `a second tap restarts the controls delay`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")))
        viewModel.togglePause()
        advance(2000)
        viewModel.togglePause()
        advance(2000)
        viewModel.togglePause()
        viewModel.togglePause()

        advance(2999)

        assertTrue(viewModel.state.value!!.controlsVisible)
    }

    @Test
    fun `controls shown by a resume after a background pause hide again`() {
        val viewModel = createViewModel()
        viewModel.startPlaying(listOf(item("a")))
        viewModel.pauseForBackground()
        assertTrue(viewModel.state.value!!.controlsVisible)

        viewModel.togglePause()
        advance(3000)

        assertFalse(viewModel.state.value!!.controlsVisible)
    }
}
