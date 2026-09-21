package com.davide.seddio.easygallery.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.formatCoordinatesForDisplay
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.ui.components.openMediaLocationInMaps
import com.davide.seddio.easygallery.ui.components.rememberMediaLocation
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

                // Bottom Bar
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
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
