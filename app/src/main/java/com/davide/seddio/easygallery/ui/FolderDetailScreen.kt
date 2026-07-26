package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.ui.components.MediaGridItem
import com.davide.seddio.easygallery.ui.components.MediaListItem
import com.davide.seddio.easygallery.ui.components.SearchTopBar
import com.davide.seddio.easygallery.ui.components.ColumnCountDialog
import com.davide.seddio.easygallery.ui.components.FilterMediaDialog
import com.davide.seddio.easygallery.ui.theme.BottomGrey
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(viewModel: GalleryViewModel) {
    val media by viewModel.filteredMedia.collectAsState()
    val selectedFolder: com.davide.seddio.easygallery.data.Folder? by viewModel.selectedFolder.collectAsState()
    val columnsCount by viewModel.pictureColumns.collectAsState()
    val showInfo by viewModel.showInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val selectedMediaTypes by viewModel.selectedMediaTypes.collectAsState()
    val pictureSortType by viewModel.pictureSortType.collectAsState()
    val pictureSortOrder by viewModel.pictureSortOrder.collectAsState()
    val pictureViewType by viewModel.pictureViewType.collectAsState()

    var showColumnCountDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showViewTypeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SearchTopBar(
                title = selectedFolder?.name ?: "Gallery",
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onSearchActiveChange = { viewModel.setSearchActive(it) },
                onColumnCountClick = { showColumnCountDialog = true },
                onFilterMediaClick = { showFilterDialog = true },
                onSortClick = { showSortDialog = true },
                onViewTypeClick = { showViewTypeDialog = true },
                onSettingsClick = { viewModel.setSettingsMode(true) },
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
        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = columnsCount,
                onCountSelected = {
                    viewModel.setColumnsCount(it, forPictures = true)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showFilterDialog) {
            FilterMediaDialog(
                initialSelectedTypes = selectedMediaTypes,
                onConfirm = {
                    viewModel.setSelectedMediaTypes(it)
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false }
            )
        }

        if (showSortDialog) {
            SortDialog(
                currentSort = pictureSortType,
                currentOrder = pictureSortOrder,
                onSortSelected = {
                    viewModel.setSortType(it, forPictures = true)
                },
                onOrderSelected = {
                    viewModel.setSortOrder(it, forPictures = true)
                },
                onDismiss = { showSortDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = pictureViewType,
                onViewTypeSelected = {
                    viewModel.setViewType(it, forPictures = true)
                    showViewTypeDialog = false
                },
                onDismiss = { showViewTypeDialog = false }
            )
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BottomGrey)
        ) {
            if (pictureViewType == ViewType.GRID) {
                MediaGrid(
                    media = media,
                    columns = columnsCount,
                    showInfo = showInfo,
                    onItemClick = { viewModel.selectMedia(it) },
                    onZoomIn = { viewModel.decreaseColumns(forPictures = true) },
                    onZoomOut = { viewModel.increaseColumns(forPictures = true) }
                )
            } else {
                MediaList(
                    media = media,
                    showInfo = showInfo,
                    onItemClick = { viewModel.selectMedia(it) }
                )
            }
        }
    }
}

@Composable
fun MediaGrid(
    media: List<MediaItem>,
    columns: Int,
    showInfo: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    var cumulativeScale by remember { mutableFloatStateOf(1f) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
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
                                onZoomIn()
                                cumulativeScale = 1f
                            } else if (cumulativeScale < 0.75f) {
                                onZoomOut()
                                cumulativeScale = 1f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    cumulativeScale = 1f
                }
            }
    ) {
        items(media) { item ->
            MediaGridItem(
                item = item,
                showInfo = showInfo,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
fun MediaList(
    media: List<MediaItem>,
    showInfo: Boolean,
    onItemClick: (MediaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BottomGrey),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(media) { item ->
            MediaListItem(
                item = item,
                showInfo = showInfo,
                onClick = { onItemClick(item) }
            )
        }
    }
}
