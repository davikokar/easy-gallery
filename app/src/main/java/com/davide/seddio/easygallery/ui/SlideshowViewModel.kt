package com.davide.seddio.easygallery.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SharedPreferencesSlideshowOptionsStore
import com.davide.seddio.easygallery.data.SlideshowOptions
import com.davide.seddio.easygallery.data.SlideshowOptionsStore
import com.davide.seddio.easygallery.data.SlideshowOrder
import com.davide.seddio.easygallery.data.SlideshowPlaylist
import com.davide.seddio.easygallery.data.SlideshowTransition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class SlideshowPhase { COUNTDOWN, PLAYING, PAUSED }

private class VideoPosition(val uri: Uri, val positionMs: Long)

/**
 * What the Slideshow screen draws. [itemSerial] changes every time an item is entered, even when the item is the same
 * one again (a one-item loop), so a screen that must restart something per item can key on it.
 */
data class SlideshowUiState(
    val phase: SlideshowPhase,
    val countdownRemaining: Int,
    val current: MediaItem,
    val next: MediaItem?,
    val controlsVisible: Boolean,
    val transition: SlideshowTransition,
    val itemSerial: Long = 0L
)

private const val COUNTDOWN_SECONDS = 3
private const val COUNTDOWN_STEP_MILLIS = 1000L
private const val CONTROLS_HIDE_DELAY_MILLIS = 3000L

// Local files start in well under a second; this only catches a video that will never render (audio-only, undecodable).
internal const val VIDEO_FIRST_FRAME_TIMEOUT_MILLIS = 10_000L

/**
 * The slideshow's playlist, position, phase and interval timer (ADR-0025 §6). Activity-scoped, so a rotation does not
 * reset it. Every function is called on the main thread and every coroutine runs on [dispatcher], so the fields below
 * are never touched concurrently.
 */
class SlideshowViewModel @JvmOverloads constructor(
    application: Application,
    private val store: SlideshowOptionsStore = SharedPreferencesSlideshowOptionsStore(application),
    private val random: Random = Random.Default,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) : AndroidViewModel(application) {

    private val _savedOptions = MutableStateFlow(store.load())
    val savedOptions: StateFlow<SlideshowOptions> = _savedOptions.asStateFlow()

    private val _state = MutableStateFlow<SlideshowUiState?>(null)

    /** Null when no slideshow is shown. */
    val state: StateFlow<SlideshowUiState?> = _state.asStateFlow()

    // An empty playlist means no slideshow.
    private var playlist: List<MediaItem> = emptyList()
    private var nextPass: List<MediaItem>? = null
    private var options = SlideshowOptions()
    private var index = 0
    private var phase = SlideshowPhase.COUNTDOWN
    private var countdownRemaining = 0
    private var itemSerial = 0L

    // The screen has displayed the current item; reset on entering each item.
    private var itemShown = false

    // Stops a looping slideshow of media that can no longer be shown from spinning (ADR-0025 §6).
    private var shownInPass = false

    private var remainingMillis = 0L
    private var timerStartedAt = 0L
    private var controlsRequested = false
    private var pendingVideoPosition: VideoPosition? = null

    private var countdownJob: Job? = null
    private var timerJob: Job? = null
    private var loadJob: Job? = null
    private var controlsJob: Job? = null

    /** Snapshots the playlist once; an empty playlist changes nothing, not even the saved options. */
    fun start(baseOrder: List<MediaItem>, options: SlideshowOptions) {
        val built = SlideshowPlaylist.build(baseOrder, options, random)
        if (built.isEmpty()) return

        cancel()
        store.save(options)
        _savedOptions.value = options

        this.options = options
        playlist = built
        index = 0
        phase = SlideshowPhase.COUNTDOWN
        countdownRemaining = COUNTDOWN_SECONDS
        itemSerial = 0L
        itemShown = false
        shownInPass = false
        remainingMillis = 0L
        controlsRequested = false
        refreshNextPass()
        publish()

        countdownJob = viewModelScope.launch(dispatcher) {
            for (remaining in COUNTDOWN_SECONDS - 1 downTo 1) {
                delay(COUNTDOWN_STEP_MILLIS)
                countdownRemaining = remaining
                publish()
            }
            delay(COUNTDOWN_STEP_MILLIS)
            countdownRemaining = 0
            phase = SlideshowPhase.PLAYING
            publish()
            startIntervalTimerIfDue()
            startFirstFrameWatchdogIfDue()
        }
    }

    /** Ends the slideshow from any phase. */
    fun cancel() {
        countdownJob?.cancel()
        timerJob?.cancel()
        loadJob?.cancel()
        controlsJob?.cancel()
        countdownJob = null
        timerJob = null
        loadJob = null
        controlsJob = null
        playlist = emptyList()
        nextPass = null
        pendingVideoPosition = null
        controlsRequested = false
        publish()
    }

    /** A tap while playing or paused; a tap during the countdown is [cancel], which the screen calls instead. */
    fun togglePause() {
        if (playlist.isEmpty()) return
        when (phase) {
            SlideshowPhase.PLAYING -> {
                freezeTimer()
                phase = SlideshowPhase.PAUSED
                showControlsBriefly()
            }
            SlideshowPhase.PAUSED -> {
                phase = SlideshowPhase.PLAYING
                startIntervalTimerIfDue()
                startFirstFrameWatchdogIfDue()
                showControlsBriefly()
            }
            SlideshowPhase.COUNTDOWN -> Unit
        }
    }

    /** ON_STOP outside a configuration change. Stays paused on return; a countdown stops on the first item (ADR-0025 §6). */
    fun pauseForBackground() {
        if (playlist.isEmpty()) return
        when (phase) {
            SlideshowPhase.PLAYING -> {
                freezeTimer()
                phase = SlideshowPhase.PAUSED
                publish()
            }
            SlideshowPhase.COUNTDOWN -> {
                countdownJob?.cancel()
                countdownJob = null
                countdownRemaining = 0
                phase = SlideshowPhase.PAUSED
                publish()
            }
            SlideshowPhase.PAUSED -> Unit
        }
    }

    /** The current item is on screen. Repeated calls for the same item (after a rotation) change nothing. */
    fun onItemShown() {
        if (playlist.isEmpty() || itemShown) return
        itemShown = true
        shownInPass = true
        stopFirstFrameWatchdog()
        remainingMillis = options.intervalSeconds * 1000L
        startIntervalTimerIfDue()
    }

    /** The current item cannot be shown; skip it at once. */
    fun onItemFailed() {
        if (playlist.isEmpty() || phase == SlideshowPhase.COUNTDOWN) return
        advance()
    }

    /** A video plays to its end instead of for the interval. */
    fun onVideoEnded() {
        if (playlist.isEmpty() || phase == SlideshowPhase.COUNTDOWN) return
        if (!itemShown || playlist[index].type != MediaType.VIDEO) return
        advance()
    }

    // Plain functions, never state: a 250 ms position would recompose the screen on every tick (ADR-0002).
    // Keyed by URI, as ADR-0002's consume is, so a position can only go back to the video it was read from.
    fun recordVideoPosition(uri: Uri, positionMs: Long) {
        if (playlist.isEmpty() || playlist[index].uri != uri) return
        pendingVideoPosition = VideoPosition(uri, positionMs)
    }

    fun consumeVideoPosition(uri: Uri): Long? {
        val pending = pendingVideoPosition ?: return null
        if (pending.uri != uri) return null
        pendingVideoPosition = null
        return pending.positionMs
    }

    private fun advance() {
        if (index < playlist.lastIndex) {
            index++
            enterItem(keepShown = false)
            return
        }

        val following = nextPass
        if (following == null || !shownInPass) {
            cancel()
            return
        }

        playlist = following
        index = 0
        // A one-item loop of a still has nothing new to display, so the screen is not asked again.
        val repeatsStill = playlist.size == 1 && playlist[0].type != MediaType.VIDEO
        shownInPass = repeatsStill
        enterItem(keepShown = repeatsStill)
    }

    private fun enterItem(keepShown: Boolean) {
        timerJob?.cancel()
        timerJob = null
        stopFirstFrameWatchdog()
        pendingVideoPosition = null
        itemSerial++
        itemShown = keepShown
        refreshNextPass()
        publish()
        if (keepShown) {
            remainingMillis = options.intervalSeconds * 1000L
            startIntervalTimerIfDue()
        } else {
            startFirstFrameWatchdogIfDue()
        }
    }

    // Decided on entering the last item, so the next item can be prefetched across the wrap.
    private fun refreshNextPass() {
        nextPass = when {
            !options.loop || index != playlist.lastIndex -> null
            options.order == SlideshowOrder.RANDOM ->
                SlideshowPlaylist.reshuffle(playlist, lastShown = playlist[index], random = random)
            else -> playlist
        }
    }

    private fun startIntervalTimerIfDue() {
        if (phase != SlideshowPhase.PLAYING || !itemShown || playlist[index].type == MediaType.VIDEO) return
        timerJob?.cancel()
        timerStartedAt = monotonicMillis()
        timerJob = viewModelScope.launch(dispatcher) {
            delay(remainingMillis)
            timerJob = null
            advance()
        }
    }

    // A video that never reports a first frame is skipped as a failure; paused time does not count, and resuming restarts the bound.
    private fun startFirstFrameWatchdogIfDue() {
        if (phase != SlideshowPhase.PLAYING || itemShown || playlist[index].type != MediaType.VIDEO) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(dispatcher) {
            delay(VIDEO_FIRST_FRAME_TIMEOUT_MILLIS)
            loadJob = null
            advance()
        }
    }

    private fun stopFirstFrameWatchdog() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun freezeTimer() {
        stopFirstFrameWatchdog()
        val job = timerJob ?: return
        remainingMillis = (remainingMillis - (monotonicMillis() - timerStartedAt)).coerceAtLeast(0L)
        job.cancel()
        timerJob = null
    }

    private fun showControlsBriefly() {
        controlsRequested = true
        publish()
        controlsJob?.cancel()
        controlsJob = viewModelScope.launch(dispatcher) {
            delay(CONTROLS_HIDE_DELAY_MILLIS)
            controlsRequested = false
            publish()
        }
    }

    private fun publish() {
        if (playlist.isEmpty()) {
            _state.value = null
            return
        }
        val next = if (index < playlist.lastIndex) playlist[index + 1] else nextPass?.firstOrNull()
        _state.value = SlideshowUiState(
            phase = phase,
            countdownRemaining = countdownRemaining,
            current = playlist[index],
            next = next,
            controlsVisible = controlsRequested || phase == SlideshowPhase.PAUSED,
            transition = options.transition,
            itemSerial = itemSerial
        )
    }
}
