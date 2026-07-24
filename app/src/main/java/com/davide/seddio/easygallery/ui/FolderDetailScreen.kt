package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.Photo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(viewModel: GalleryViewModel) {
    val photos by viewModel.photosInFolder.collectAsState()
    val selectedFolder: com.davide.seddio.easygallery.data.Folder? by viewModel.selectedFolder.collectAsState()
    val columnsCount by viewModel.columnsCount.collectAsState()
    val showInfo by viewModel.showInfo.collectAsState()
    var cumulativeScale by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(selectedFolder?.name ?: "Photos") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToFolders() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleInfo() }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Toggle Info",
                            tint = if (showInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        cumulativeScale *= zoom
                        if (cumulativeScale > 1.2f) {
                            viewModel.decreaseColumns()
                            cumulativeScale = 1f
                        } else if (cumulativeScale < 0.8f) {
                            viewModel.increaseColumns()
                            cumulativeScale = 1f
                        }
                    }
                }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos) { photo ->
                    PhotoItem(photo, showInfo)
                }
            }
        }
    }
}

@Composable
fun PhotoItem(photo: Photo, showInfo: Boolean) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (showInfo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f
                            )
                        )
                )
                Text(
                    text = photo.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(4.dp)
                )
            }
        }
    }
}
