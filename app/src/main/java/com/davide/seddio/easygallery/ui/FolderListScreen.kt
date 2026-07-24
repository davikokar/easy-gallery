package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.ui.components.SearchTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(viewModel: GalleryViewModel) {
    val uiState by viewModel.filteredFolders.collectAsState()
    val columnsCount by viewModel.columnsCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    var cumulativeScale by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            SearchTopBar(
                title = "Easy Gallery",
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onSearchActiveChange = { viewModel.setSearchActive(it) }
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
            when (val state = uiState) {
                is GalleryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is GalleryUiState.Success -> {
                    FolderGrid(
                        folders = state.folders,
                        columns = columnsCount,
                        onFolderClick = { viewModel.selectFolder(it) }
                    )
                }
                is GalleryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun FolderGrid(folders: List<Folder>, columns: Int, onFolderClick: (Folder) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(folders) { folder ->
            FolderItem(folder, onClick = { onFolderClick(folder) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderItem(folder: Folder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(8.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = folder.thumbnailUri,
                contentDescription = folder.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Gradient and text overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 200f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = folder.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.imageCount} images",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
