package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
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
import com.davide.seddio.easygallery.ui.components.PhotoItem
import com.davide.seddio.easygallery.ui.components.SearchTopBar
import com.davide.seddio.easygallery.ui.theme.BottomGrey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(viewModel: GalleryViewModel) {
    val photos by viewModel.filteredPhotos.collectAsState()
    val selectedFolder: com.davide.seddio.easygallery.data.Folder? by viewModel.selectedFolder.collectAsState()
    val columnsCount by viewModel.columnsCount.collectAsState()
    val showInfo by viewModel.showInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()

    Scaffold(
        topBar = {
            SearchTopBar(
                title = selectedFolder?.name ?: "Photos",
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onSearchActiveChange = { viewModel.setSearchActive(it) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.backToFolders() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleInfo() }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Toggle Info",
                            tint = if (showInfo) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            )
        },
        containerColor = BottomGrey
    ) { padding ->
        var cumulativeScale by remember { mutableFloatStateOf(1f) }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BottomGrey)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    cumulativeScale *= zoom
                                    if (cumulativeScale > 1.25f) {
                                        viewModel.decreaseColumns()
                                        cumulativeScale = 1f
                                    } else if (cumulativeScale < 0.75f) {
                                        viewModel.increaseColumns()
                                        cumulativeScale = 1f
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            cumulativeScale = 1f
                        }
                    }
            ) {
                items(photos) { photo ->
                    PhotoItem(
                        photo = photo,
                        showInfo = showInfo,
                        onClick = { viewModel.selectPhoto(photo) }
                    )
                }
            }
        }
    }
}
