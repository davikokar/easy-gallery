package com.davide.seddio.easygallery.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.formatCoordinatesForDisplay
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.ui.components.VideoPlaybackState
import com.davide.seddio.easygallery.ui.components.formatMediaDuration
import com.davide.seddio.easygallery.ui.components.openMediaLocationInMaps
import com.davide.seddio.easygallery.ui.components.playbackProgressFraction
import com.davide.seddio.easygallery.ui.components.rememberMediaLocation
import com.davide.seddio.easygallery.ui.components.rememberVideoPlaybackState
import com.davide.seddio.easygallery.ui.components.ZoomableImage
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullImageScreen(viewModel: GalleryViewModel) {
    val currentItem by viewModel.selectedMedia.collectAsState()
    val mediaList by viewModel.currentMediaList.collectAsState()
    val isImmersive by viewModel.isImmersiveMode.collectAsState()
    val rotation by viewModel.currentRotation.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    if (mediaList.isEmpty()) {
        viewModel.closeMedia()
        return
    }

    val initialIndex = remember(mediaList) {
        val index = mediaList.indexOf(currentItem)
        if (index >= 0) index else 0
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    key(mediaList) {
        val pagerState = rememberPagerState(initialPage = initialIndex) {
            mediaList.size
        }
        val isPagerScrolling = pagerState.isScrollInProgress
        val activePage = pagerState.settledPage
        val activeMedia = mediaList.getOrNull(activePage)
        val activeVideo = activeMedia?.takeIf { it.type == MediaType.VIDEO }
        var boundVideoUri by remember { mutableStateOf<Uri?>(null) }
        var isLifecycleStarted by remember {
            mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage in mediaList.indices) {
                viewModel.setCurrentMedia(mediaList[pagerState.currentPage])
            }
        }

        DisposableEffect(lifecycleOwner, exoPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> isLifecycleStarted = true
                    Lifecycle.Event.ON_STOP -> {
                        isLifecycleStarted = false
                        boundVideoUri?.let { videoUri ->
                            viewModel.recordVideoPlaybackPosition(
                                uri = videoUri,
                                positionMs = exoPlayer.currentPosition,
                                playWhenReady = exoPlayer.playWhenReady
                            )
                        }
                        exoPlayer.pause()
                    }
                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(activeVideo?.uri, isPagerScrolling, isLifecycleStarted) {
            if (!isLifecycleStarted || isPagerScrolling) {
                exoPlayer.pause()
                return@LaunchedEffect
            }

            if (activeVideo == null) {
                exoPlayer.pause()
                if (boundVideoUri != null) {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    boundVideoUri = null
                }
                return@LaunchedEffect
            }

            val savedPlayback = viewModel.consumeVideoPlaybackPosition(activeVideo.uri)
            val savedPositionMs = savedPlayback?.positionMs
            val currentDurationMs = if (boundVideoUri == activeVideo.uri) exoPlayer.duration else C.TIME_UNSET
            val restoredPositionMs = when {
                savedPositionMs == null -> null
                currentDurationMs != C.TIME_UNSET && savedPositionMs >= currentDurationMs -> null
                else -> savedPositionMs
            }

            if (boundVideoUri != activeVideo.uri) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                val mediaItem = Media3Item.fromUri(activeVideo.uri)
                if (restoredPositionMs != null) {
                    exoPlayer.setMediaItem(mediaItem, restoredPositionMs)
                } else {
                    exoPlayer.setMediaItem(mediaItem)
                }
                exoPlayer.prepare()
                boundVideoUri = activeVideo.uri
            }

            val shouldPlay = savedPlayback?.playWhenReady ?: true
            exoPlayer.playWhenReady = shouldPlay
            if (shouldPlay) {
                exoPlayer.play()
            }
        }

        val item = currentItem
        if (item != null) {
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.delete_media_title)) },
                    text = { Text(stringResource(R.string.delete_single_media_message, item.type.name.lowercase())) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteMedia(item)
                            showDeleteDialog = false
                        }) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    pageSpacing = 16.dp,
                    userScrollEnabled = !isZoomed,
                    key = { index -> if (index < mediaList.size) mediaList[index].uri.toString() else index }
                ) { page ->
                    if (page in mediaList.indices) {
                        val p = mediaList[page]
                        if (p.type == MediaType.VIDEO) {
                            VideoPlayer(
                                player = exoPlayer,
                                isActive = page == activePage && !isPagerScrolling,
                                onTap = { viewModel.toggleImmersiveMode() }
                            )
                        } else {
                            ZoomableImage(
                                uri = p.uri,
                                contentDescription = p.name,
                                rotationZ = if (p == item) rotation else 0f,
                                onTap = { viewModel.toggleImmersiveMode() },
                                onScaleChanged = { isZoomed = it > 1f }
                            )
                        }
                    }
                }

                // Bottom Bar
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        color = AppBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showInfo && !isImmersive) {
                                val coordinates = rememberMediaLocation(item)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = stringResource(R.string.properties_name, item.name),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.properties_path, "${item.folderPath}/${item.name}"),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (coordinates != null) {
                                            val openInMapsDescription = stringResource(R.string.cd_open_in_maps)
                                            Text(
                                                text = stringResource(
                                                    R.string.properties_gps,
                                                    formatCoordinatesForDisplay(coordinates)
                                                ),
                                                color = BrandBlue,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    textDecoration = TextDecoration.Underline
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        openMediaLocationInMaps(
                                                            context = context,
                                                            coordinates = coordinates,
                                                            label = item.name
                                                        )
                                                    }
                                                    .semantics {
                                                        contentDescription = openInMapsDescription
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            if (item.type == MediaType.VIDEO) {
                                VideoPlaybackControls(
                                    player = exoPlayer,
                                    isPageActive = !isPagerScrolling && isLifecycleStarted
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = Color.White)
                                }
                                IconButton(onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (item.type == MediaType.VIDEO) "video/*" else "image/*"
                                        putExtra(Intent.EXTRA_STREAM, item.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_media_title)))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share), tint = Color.White)
                                }
                                if (item.type != MediaType.VIDEO) {
                                    IconButton(onClick = { viewModel.rotatePhoto() }) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = stringResource(R.string.cd_rotate), tint = Color.White)
                                    }
                                }
                                IconButton(
                                    onClick = { showInfo = !showInfo },
                                    modifier = if (showInfo) Modifier.background(Color.White, CircleShape) else Modifier
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = stringResource(R.string.cd_toggle_info),
                                        tint = if (showInfo) BrandBlue else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlaybackControls(
    player: Player,
    isPageActive: Boolean
) {
    val playbackState = rememberVideoPlaybackState(
        player = player,
        isPageActive = isPageActive
    )
    val durationMs = playbackState.durationMs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoPlayPauseButton(playbackState)
        IconButton(onClick = playbackState::restart) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = stringResource(R.string.cd_restart_video),
                tint = Color.White
            )
        }
        VideoProgressSlider(
            playbackState = playbackState,
            durationMs = durationMs,
            modifier = Modifier.weight(1f)
        )
        VideoTimeLabel(
            playbackState = playbackState,
            durationMs = durationMs
        )
    }
}

@Composable
private fun VideoPlayPauseButton(playbackState: VideoPlaybackState) {
    val isPlaying = playbackState.isPlaying
    val description = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)

    IconButton(onClick = playbackState::togglePlayPause) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = description,
            tint = Color.White
        )
    }
}

@Composable
private fun VideoProgressSlider(
    playbackState: VideoPlaybackState,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val positionMs = playbackState.positionMs
    val formattedPosition = formatMediaDuration(positionMs)
    val formattedDuration = formatMediaDuration(durationMs)
    val progressDescription = stringResource(R.string.cd_video_progress)
    val progressStateDescription = stringResource(
        R.string.video_position_of_duration,
        formattedPosition,
        formattedDuration
    )

    Slider(
        value = playbackProgressFraction(positionMs, durationMs),
        onValueChange = { fraction ->
            playbackState.startScrubbing()
            playbackState.updateScrubPosition((fraction * durationMs).toLong())
        },
        onValueChangeFinished = playbackState::finishScrubbing,
        enabled = durationMs > 0L,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = BrandBlue,
            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
            disabledThumbColor = Color.White.copy(alpha = 0.5f),
            disabledActiveTrackColor = Color.White.copy(alpha = 0.35f),
            disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f)
        ),
        modifier = modifier.semantics {
            contentDescription = progressDescription
            stateDescription = progressStateDescription
        }
    )
}

@Composable
private fun VideoTimeLabel(
    playbackState: VideoPlaybackState,
    durationMs: Long
) {
    val positionMs = playbackState.positionMs
    Text(
        text = stringResource(
            R.string.video_position_of_duration,
            formatMediaDuration(positionMs),
            formatMediaDuration(durationMs)
        ),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = Modifier.clearAndSetSemantics { }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    player: ExoPlayer,
    isActive: Boolean,
    onTap: () -> Unit
) {
    val onTapUpdated by rememberUpdatedState(onTap)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapUpdated() })
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    setEnableComposeSurfaceSyncWorkaround(true)
                    // Keep PlayerView non-consuming so pager swipe and parent gestures can propagate.
                    useController = false
                }
            },
            update = { playerView ->
                playerView.player = if (isActive) player else null
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
