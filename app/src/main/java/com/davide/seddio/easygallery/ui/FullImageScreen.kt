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
import com.davide.seddio.easygallery.ui.components.ZoomableImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullImageScreen(viewModel: GalleryViewModel) {
    val photo by viewModel.selectedPhoto.collectAsState()
    val photosList by viewModel.currentPhotosList.collectAsState()
    val isImmersive by viewModel.isImmersiveMode.collectAsState()
    val rotation by viewModel.currentRotation.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }

    if (photosList.isEmpty()) {
        viewModel.closePhoto()
        return
    }

    val initialIndex = remember(photosList) {
        val index = photosList.indexOf(photo)
        if (index >= 0) index else 0
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) {
        photosList.size
    }

    // Update current photo in ViewModel when swiping
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage in photosList.indices) {
            viewModel.setCurrentPhoto(photosList[pagerState.currentPage])
        }
    }

    val currentPhoto = photo ?: return

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to delete this photo?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhoto(currentPhoto)
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
            key = { index -> if (index < photosList.size) photosList[index].uri.toString() else index }
        ) { page ->
            if (page in photosList.indices) {
                val p = photosList[page]
                ZoomableImage(
                    uri = p.uri,
                    contentDescription = p.name,
                    rotationZ = if (p == currentPhoto) rotation else 0f,
                    onTap = { viewModel.toggleImmersiveMode() },
                    onScaleChanged = { isZoomed = it > 1f }
                )
            }
        }

        // Top Bar
        AnimatedVisibility(
            visible = !isImmersive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TopAppBar(
                title = { Text(currentPhoto.name, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closePhoto() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    titleContentColor = Color.White
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
                color = Color.Black.copy(alpha = 0.5f),
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
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, currentPhoto.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.rotatePhoto() }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
                    }
                }
            }
        }
    }
}
