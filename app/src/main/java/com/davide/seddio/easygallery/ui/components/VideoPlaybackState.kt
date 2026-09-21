package com.davide.seddio.easygallery.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Stable
class VideoPlaybackState internal constructor(
    private val player: Player
) {

    var isPlaying by mutableStateOf(false)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    private var playbackPositionMs by mutableLongStateOf(0L)
    private var scrubPositionMs by mutableLongStateOf(0L)

    var isScrubbing by mutableStateOf(false)
        private set

    val positionMs: Long
        get() = if (isScrubbing) scrubPositionMs else playbackPositionMs

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun restart() {
        player.seekTo(0L)
        player.play()
    }

    fun seekTo(positionMs: Long) {
        val target = normalizeSeek(positionMs, durationMs)
        player.seekTo(target)
        playbackPositionMs = target
        if (isScrubbing) {
            scrubPositionMs = target
        }
    }

    fun startScrubbing() {
        if (isScrubbing) return
        isScrubbing = true
        scrubPositionMs = positionMs
    }

    fun updateScrubPosition(positionMs: Long) {
        if (!isScrubbing) return
        scrubPositionMs = normalizeSeek(positionMs, durationMs)
    }

    fun finishScrubbing() {
        if (!isScrubbing) return
        val target = scrubPositionMs
        isScrubbing = false
        seekTo(target)
    }

    fun cancelScrubbing() {
        if (!isScrubbing) return
        isScrubbing = false
    }

    internal fun refreshFromPlayer() {
        isPlaying = player.isPlaying
        durationMs = sanitizeDuration(player.duration)
        if (!isScrubbing) {
            playbackPositionMs = sanitizePosition(player.currentPosition, durationMs)
        }
    }

    internal fun refreshPosition() {
        if (!isScrubbing) {
            playbackPositionMs = sanitizePosition(player.currentPosition, durationMs)
        }
    }

    private fun sanitizeDuration(durationMs: Long): Long {
        // Unknown durations must stay at zero to keep slider ranges safe.
        return if (durationMs == C.TIME_UNSET || durationMs <= 0L) 0L else durationMs
    }

    private fun sanitizePosition(positionMs: Long, durationMs: Long): Long {
        val nonNegativePosition = positionMs.coerceAtLeast(0L)
        return if (durationMs > 0L) {
            nonNegativePosition.coerceAtMost(durationMs)
        } else {
            nonNegativePosition
        }
    }

    private fun normalizeSeek(positionMs: Long, durationMs: Long): Long {
        return if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
    }
}

@Composable
fun rememberVideoPlaybackState(
    player: Player,
    isPageActive: Boolean,
    pollIntervalMs: Long = 250L
): VideoPlaybackState {
    val state = remember(player) { VideoPlaybackState(player) }

    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (
                    events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                ) {
                    state.refreshFromPlayer()
                }
            }
        }

        player.addListener(listener)
        state.refreshFromPlayer()

        onDispose {
            player.removeListener(listener)
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        player,
        isPageActive,
        state.isPlaying,
        state.isScrubbing,
        pollIntervalMs
    ) {
        if (!isPageActive || !state.isPlaying || state.isScrubbing) {
            return@LaunchedEffect
        }

        while (currentCoroutineContext().isActive && isPageActive && state.isPlaying && !state.isScrubbing) {
            state.refreshPosition()
            delay(pollIntervalMs)
        }
    }

    return state
}