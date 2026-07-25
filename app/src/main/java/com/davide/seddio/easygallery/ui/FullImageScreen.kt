package com.davide.seddio.easygallery.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.ui.components.ZoomableImage
import com.davide.seddio.easygallery.ui.theme.BottomGrey
import com.davide.seddio.easygallery.ui.theme.TopBarBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullImageScreen(viewModel: GalleryViewModel) {
    val currentItem by viewModel.selectedMedia.collectAsState()
    val mediaList by viewModel.currentMediaList.collectAsState()
    val isImmersive by viewModel.isImmersiveMode.collectAsState()
    val rotation by viewModel.currentRotation.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }

    if (mediaList.isEmpty()) {
        viewModel.closeMedia()
        return
    }

    val initialIndex = remember(mediaList) {
        val index = mediaList.indexOf(currentItem)
        if (index >= 0) index else 0
    }

    key(mediaList) {
        val pagerState = rememberPagerState(initialPage = initialIndex) {
            mediaList.size
        }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage in mediaList.indices) {
                viewModel.setCurrentMedia(mediaList[pagerState.currentPage])
            }
        }

        val item = currentItem
        if (item != null) {
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Media") },
                    text = { Text("Are you sure you want to delete this ${item.type.name.lowercase()}?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteMedia(item)
                            showDeleteDialog = false
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 16.dp,
                    userScrollEnabled = !isZoomed,
                    key = { index -> if (index < mediaList.size) mediaList[index].uri.toString() else index }
                ) { page ->
                    if (page in mediaList.indices) {
                        val p = mediaList[page]
                        if (p.type == MediaType.VIDEO) {
                            VideoPlayer(p)
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

                // Top Bar
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TopAppBar(
                        title = { Text(item.name, color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.closeMedia() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = TopBarBlue,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                }

                // Bottom Bar
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = BottomGrey,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                            }
                            IconButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (item.type == MediaType.VIDEO) "video/*" else "image/*"
                                    putExtra(Intent.EXTRA_STREAM, item.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                            if (item.type != MediaType.VIDEO) {
                                IconButton(onClick = { viewModel.rotatePhoto() }) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
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
fun VideoPlayer(item: MediaItem) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
