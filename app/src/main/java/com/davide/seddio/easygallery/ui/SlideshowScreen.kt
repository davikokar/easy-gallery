package com.davide.seddio.easygallery.ui

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SlideshowTransition
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val FADE_MILLIS = 500

// A video's view is never fully transparent before its first frame, so the compositor keeps consuming frames.
private const val VIDEO_LOADING_ALPHA = 0.004f

@Composable
fun SlideshowScreen(viewModel: SlideshowViewModel) {
    val state by viewModel.state.collectAsState()
    val current = state ?: return

    SlideshowWindowEffects(keepScreenOn = current.phase != SlideshowPhase.PAUSED)
    SlideshowContent(
        state = current,
        onCancel = viewModel::cancel,
        onTogglePause = viewModel::togglePause,
        onPauseForBackground = viewModel::pauseForBackground,
        onItemShown = viewModel::onItemShown,
        onItemFailed = viewModel::onItemFailed,
        onVideoEnded = viewModel::onVideoEnded,
        onRecordVideoPosition = viewModel::recordVideoPosition,
        onConsumeVideoPosition = viewModel::consumeVideoPosition
    )
}

@Composable
fun SlideshowContent(
    state: SlideshowUiState,
    onCancel: () -> Unit,
    onTogglePause: () -> Unit,
    onPauseForBackground: () -> Unit,
    onItemShown: () -> Unit,
    onItemFailed: () -> Unit,
    onVideoEnded: () -> Unit,
    onRecordVideoPosition: (Uri, Long) -> Unit,
    onConsumeVideoPosition: (Uri) -> Long?
) {
    val phase = state.phase
    val isCountdown = phase == SlideshowPhase.COUNTDOWN
    val tapAction by rememberUpdatedState(if (isCountdown) onCancel else onTogglePause)

    BackHandler(onBack = onCancel)
    PrefetchSlides(current = state.current, next = state.next)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("slideshow_screen")
            .pointerInput(Unit) { detectTapGestures(onTap = { tapAction() }) }
            .semantics { onClick { tapAction(); true } }
    ) {
        SlideshowStage(
            current = state.current,
            itemSerial = state.itemSerial,
            phase = phase,
            transition = state.transition,
            onPauseForBackground = onPauseForBackground,
            onItemShown = onItemShown,
            onItemFailed = onItemFailed,
            onVideoEnded = onVideoEnded,
            onRecordVideoPosition = onRecordVideoPosition,
            onConsumeVideoPosition = onConsumeVideoPosition
        )

        if (isCountdown) {
            Text(
                text = state.countdownRemaining.toString(),
                color = Color.White,
                fontSize = 144.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("slideshow_countdown")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        AnimatedVisibility(
            visible = state.controlsVisible && !isCountdown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                ControlButton(
                    icon = Icons.Default.Close,
                    description = stringResource(R.string.slideshow_close),
                    onClick = onCancel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .testTag("slideshow_close_button")
                )
                val isPaused = phase == SlideshowPhase.PAUSED
                ControlButton(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    description = stringResource(if (isPaused) R.string.slideshow_resume else R.string.slideshow_pause),
                    onClick = onTogglePause,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .testTag("slideshow_pause_button")
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White)
    }
}

// Window effects live in the Screen so Content stays drivable by tests without touching the host window.
@Composable
private fun SlideshowWindowEffects(keepScreenOn: Boolean) {
    val view = LocalView.current

    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }

    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
}

// Best effort: the request uses the window size AsyncImage will resolve, so the decode is warm when the slide shows.
@Composable
private fun PrefetchSlides(current: MediaItem, next: MediaItem?) {
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize

    LaunchedEffect(current.uri, next?.uri) {
        val loader = SingletonImageLoader.get(context)
        listOfNotNull(current, next)
            .filter { it.type != MediaType.VIDEO }
            .forEach { item ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(windowSize.width, windowSize.height)
                        .build()
                )
            }
    }
}

// One visible item. A video leaves the screen as a Slide holding its last frame; see ADR-0025 section 9.
@Stable
private class Slide(
    val id: Int,
    val item: MediaItem,
    val snapIn: Boolean = false,
    val still: ImageBitmap? = null
) {
    val fade = Animatable(if (still != null) 1f else 0f)
    var ready by mutableStateOf(still != null)
    var visible by mutableStateOf(false)
    var revealed = still != null
    val isVideo: Boolean get() = still == null && item.type == MediaType.VIDEO
}

@Stable
private class StageState {
    // Outgoing slide first, incoming slide last; the last one is always the current item.
    val slides = mutableStateListOf<Slide>()
    var videoAspect by mutableStateOf<Float?>(null)
    var textureView: TextureView? = null
    var lastFrame: ImageBitmap? = null

    // The ViewModel's itemSerial of the item on stage; -1 before the first.
    var serial = -1L
    private var nextId = 0

    val newest: Slide? get() = slides.lastOrNull()

    fun show(item: MediaItem, itemSerial: Long, snapIn: Boolean) {
        serial = itemSerial
        val previous = slides.lastOrNull { it.revealed }
        val outgoing = when {
            previous == null -> null
            previous.isVideo -> lastFrame?.let { Slide(nextId++, previous.item, still = it) }
            else -> previous
        }
        lastFrame = null
        slides.clear()
        outgoing?.let { slides.add(it) }
        slides.add(Slide(nextId++, item, snapIn = snapIn))
    }

    fun captureLastFrame() {
        val view = textureView
        lastFrame = if (view != null && view.isAvailable && view.width > 0 && view.height > 0) {
            view.getBitmap()?.asImageBitmap()
        } else {
            null
        }
    }
}

@Composable
private fun SlideshowStage(
    current: MediaItem,
    itemSerial: Long,
    phase: SlideshowPhase,
    transition: SlideshowTransition,
    onPauseForBackground: () -> Unit,
    onItemShown: () -> Unit,
    onItemFailed: () -> Unit,
    onVideoEnded: () -> Unit,
    onRecordVideoPosition: (Uri, Long) -> Unit,
    onConsumeVideoPosition: (Uri) -> Long?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val stage = remember { StageState() }
    val player = remember(context) { createSlideshowPlayer(context) }

    val phaseNow by rememberUpdatedState(phase)
    val transitionNow by rememberUpdatedState(transition)
    val onPauseForBackgroundNow by rememberUpdatedState(onPauseForBackground)
    val onItemShownNow by rememberUpdatedState(onItemShown)
    val onItemFailedNow by rememberUpdatedState(onItemFailed)
    val onVideoEndedNow by rememberUpdatedState(onVideoEnded)
    val onRecordVideoPositionNow by rememberUpdatedState(onRecordVideoPosition)
    val onConsumeVideoPositionNow by rememberUpdatedState(onConsumeVideoPosition)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                stage.videoAspect = if (videoSize.width > 0 && videoSize.height > 0) {
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                } else {
                    null
                }
            }

            override fun onRenderedFirstFrame() {
                stage.newest?.takeIf { it.isVideo }?.ready = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val onStage = stage.newest?.takeIf { it.isVideo }
                if (playbackState != Player.STATE_ENDED || onStage == null) return
                if (onStage.ready) {
                    stage.captureLastFrame()
                    // The report is made by a LaunchedEffect, which a very short clip can outrun; the ViewModel ignores repeats.
                    onItemShownNow()
                    onVideoEndedNow()
                } else {
                    // Ended without a first frame (audio-only, undecodable): nothing to show, so skip it.
                    onItemFailedNow()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (stage.newest?.isVideo == true) {
                    stage.captureLastFrame()
                    onItemFailedNow()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Always observed, even in the countdown, so leaving the app then pauses on the first item.
    DisposableEffect(lifecycleOwner, player) {
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Position must be read before pause(): ADR-0002.
                val onStage = stage.newest
                if (onStage != null && onStage.isVideo) onRecordVideoPositionNow(onStage.item.uri, player.currentPosition)
                player.pause()
                if (activity?.isChangingConfigurations != true) onPauseForBackgroundNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (phase == SlideshowPhase.COUNTDOWN) return

    // Survives rotation: an item already reported keeps its timer and is shown without a fade-in.
    var reportedSerial by rememberSaveable { mutableStateOf(-1L) }
    val restored = remember { reportedSerial == itemSerial }

    if (stage.serial != itemSerial) {
        val onStage = stage.newest
        if (onStage != null && onStage.revealed && current.type != MediaType.VIDEO && onStage.item.uri == current.uri) {
            // A one-item loop of a still: nothing new to draw, and the ViewModel restarts the interval itself.
            stage.serial = itemSerial
        } else {
            stage.show(current, itemSerial, snapIn = stage.slides.isEmpty() && restored)
        }
    }
    val incoming = stage.slides.last()
    val videoSlide = incoming.takeIf { it.isVideo }

    LaunchedEffect(incoming) {
        snapshotFlow { incoming.ready }.first { it }
        incoming.revealed = true
        val outgoing = stage.slides.firstOrNull { it !== incoming }
        if (transitionNow == SlideshowTransition.FADE && !incoming.snapIn) {
            coroutineScope {
                if (outgoing != null) launch { outgoing.fade.animateTo(0f, tween(FADE_MILLIS)) }
                incoming.fade.animateTo(1f, tween(FADE_MILLIS))
            }
        } else {
            outgoing?.fade?.snapTo(0f)
            incoming.fade.snapTo(1f)
        }
        stage.slides.removeAll { it !== incoming }
        incoming.visible = true
    }

    // The interval starts only once the item is fully visible, and never while paused.
    // A video reports at its first frame: the ViewModel ignores onVideoEnded until it has, and a short video can end mid-fade.
    val shouldReport = (incoming.visible || (incoming.isVideo && incoming.ready)) && phase == SlideshowPhase.PLAYING
    LaunchedEffect(incoming, itemSerial, shouldReport) {
        if (shouldReport && reportedSerial != itemSerial) {
            reportedSerial = itemSerial
            onItemShownNow()
        }
    }

    LaunchedEffect(videoSlide) {
        if (videoSlide == null) {
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
        stage.videoAspect = null
        val mediaItem = Media3Item.fromUri(videoSlide.item.uri)
        val savedPositionMs = onConsumeVideoPositionNow(videoSlide.item.uri)
        if (savedPositionMs != null) player.setMediaItem(mediaItem, savedPositionMs) else player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = phaseNow == SlideshowPhase.PLAYING
    }

    LaunchedEffect(phase) {
        if (videoSlide != null) player.playWhenReady = phase == SlideshowPhase.PLAYING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        stage.slides.forEach { slide ->
            key(slide.id) {
                when {
                    slide.still != null -> Image(
                        bitmap = slide.still,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = slide.fade.value }
                    )
                    slide.isVideo -> VideoLayer(slide = slide, stage = stage, player = player)
                    else -> AsyncImage(
                        model = slide.item.uri,
                        contentDescription = slide.item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = slide.fade.value },
                        onSuccess = { slide.ready = true },
                        onError = { if (slide === stage.newest) onItemFailedNow() }
                    )
                }
            }
        }
    }
}

// A TextureView is drawn into the app's own hierarchy, so graphicsLayer alpha fades it; a SurfaceView would not.
@Composable
private fun VideoLayer(slide: Slide, stage: StageState, player: ExoPlayer) {
    val aspect = stage.videoAspect

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                TextureView(context).also { view ->
                    player.setVideoTextureView(view)
                    stage.textureView = view
                }
            },
            onRelease = { view -> if (stage.textureView === view) stage.textureView = null },
            modifier = (aspect?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize())
                .graphicsLayer { alpha = maxOf(slide.fade.value, VIDEO_LOADING_ALPHA) }
        )
    }
}

// Muted by disabling the audio track type (nothing decoded, no audio focus requested) and by volume 0.
@OptIn(UnstableApi::class)
private fun createSlideshowPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        volume = 0f
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        repeatMode = Player.REPEAT_MODE_OFF
    }
