package com.davide.seddio.easygallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
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
import com.davide.seddio.easygallery.ui.components.*
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
    val pictureGroupBy by viewModel.pictureGroupBy.collectAsState()
    val pictureGroupOrder by viewModel.pictureGroupOrder.collectAsState()
    val groupedMedia by viewModel.groupedFolderMedia.collectAsState()
    
    val isMediaSelectionMode by viewModel.isMediaSelectionMode.collectAsState()
    val selectedMediaItems by viewModel.selectedMediaItems.collectAsState()

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    if (isMediaSelectionMode) {
        BackHandler { viewModel.exitMediaSelectionMode() }
    } else {
        BackHandler { viewModel.backToFolders() }
    }

    var showColumnCountDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupByDialog by remember { mutableStateOf(false) }
    var showViewTypeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showRotateDialog by remember { mutableStateOf(false) }
    
    val isDestinationPickerActive by viewModel.isDestinationPickerActive.collectAsState()
    val pendingOperation by viewModel.pendingOperation.collectAsState()

    Scaffold(
        topBar = {
            if (isMediaSelectionMode) {
                MediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = media.size,
                    onClose = { viewModel.exitMediaSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onInfoClick = { showPropertiesDialog = true },
                    onRename = { /* Placeholder */ },
                    onRotate = { showRotateDialog = true },
                    onCopyTo = { viewModel.startOperation(OperationType.COPY) },
                    onMoveTo = { viewModel.startOperation(OperationType.MOVE) },
                    onSelectAll = { viewModel.selectAllMedia() }
                )
            } else {
                SearchTopBar(
                    title = selectedFolder?.name ?: "Gallery",
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSearchActiveChange = { viewModel.setSearchActive(it) },
                    onColumnCountClick = { showColumnCountDialog = true },
                    onFilterMediaClick = { showFilterDialog = true },
                    onSortClick = { showSortDialog = true },
                    onGroupByClick = { showGroupByDialog = true },
                    onViewTypeClick = { showViewTypeDialog = true },
                    onShowExcludedClick = { viewModel.setShowExcludedTemporarily(true) },
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
            }
        },
        containerColor = BottomGrey
    ) { padding ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Media") },
                text = { Text("Are you sure you want to delete the selected items? This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSelectedMedia()
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

        if (showPropertiesDialog) {
            MediaPropertiesDialog(
                media = viewModel.getSelectedMediaData(),
                onDismiss = { showPropertiesDialog = false }
            )
        }

        if (showRotateDialog) {
            RotateDialog(
                onRotate = {
                    viewModel.rotateSelectedMedia(it)
                    showRotateDialog = false
                },
                onDismiss = { showRotateDialog = false }
            )
        }

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

        if (showGroupByDialog) {
            GroupByDialog(
                currentGroupBy = pictureGroupBy,
                currentOrder = pictureGroupOrder,
                onGroupBySelected = { viewModel.setGroupBy(it) },
                onOrderSelected = { viewModel.setGroupOrder(it) },
                onDismiss = { showGroupByDialog = false }
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

        if (isDestinationPickerActive) {
            val browsingPath by viewModel.browsingPath.collectAsState()
            val browsingFolders by viewModel.browsingFolders.collectAsState()
            
            DestinationFolderPickerDialog(
                title = if (pendingOperation == OperationType.MOVE) "Move to..." else "Copy to...",
                currentPath = browsingPath,
                folders = browsingFolders,
                onFolderSelected = { viewModel.updateBrowsingPath(it.path) },
                onBreadcrumbClick = { viewModel.updateBrowsingPath(it) },
                onConfirm = { viewModel.performOperationWithPath(browsingPath) },
                onDismiss = { viewModel.cancelOperation() }
            )
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BottomGrey)
        ) {
            if (pictureGroupBy == GroupByType.NONE) {
                if (pictureViewType == ViewType.GRID) {
                    MediaGrid(
                        media = media,
                        columns = columnsCount,
                        state = gridState,
                        showInfo = showInfo,
                        selectedItems = selectedMediaItems,
                        onItemClick = { viewModel.selectMedia(it) },
                        onItemLongClick = { viewModel.enterMediaSelectionMode(it) },
                        onZoomIn = { viewModel.decreaseColumns(forPictures = true) },
                        onZoomOut = { viewModel.increaseColumns(forPictures = true) }
                    )
                } else {
                    MediaList(
                        media = media,
                        state = listState,
                        showInfo = showInfo,
                        selectedItems = selectedMediaItems,
                        onItemClick = { viewModel.selectMedia(it) },
                        onItemLongClick = { viewModel.enterMediaSelectionMode(it) }
                    )
                }
            } else {
                GroupedMediaContent(
                    groupedMedia = groupedMedia,
                    viewType = pictureViewType,
                    columns = columnsCount,
                    gridState = gridState,
                    listState = listState,
                    showInfo = showInfo,
                    selectedItems = selectedMediaItems,
                    onItemClick = { viewModel.selectMedia(it) },
                    onItemLongClick = { viewModel.enterMediaSelectionMode(it) },
                    onZoomIn = { viewModel.decreaseColumns(forPictures = true) },
                    onZoomOut = { viewModel.increaseColumns(forPictures = true) }
                )
            }
        }
    }
}

@Composable
fun GroupedMediaContent(
    groupedMedia: Map<String, List<MediaItem>>,
    viewType: ViewType,
    columns: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showInfo: Boolean,
    selectedItems: Set<android.net.Uri>,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    if (viewType == ViewType.GRID) {
        var cumulativeScale by remember { mutableFloatStateOf(1f) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(BottomGrey)
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
            groupedMedia.forEach { (header, items) ->
                if (header.isNotEmpty()) {
                    item(span = { GridItemSpan(columns) }, key = header) {
                        GroupHeader(header)
                    }
                }
                items(items, key = { it.uri.toString() }) { item ->
                    MediaGridItem(
                        item = item,
                        showInfo = showInfo,
                        isSelected = selectedItems.contains(item.uri),
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BottomGrey),
            state = listState
        ) {
            groupedMedia.forEach { (header, items) ->
                if (header.isNotEmpty()) {
                    item(key = header) {
                        GroupHeaderList(header)
                    }
                }
                items(items, key = { it.uri.toString() }) { item ->
                    MediaListItem(
                        item = item,
                        showInfo = showInfo,
                        isSelected = selectedItems.contains(item.uri),
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyGridItemScope.GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .animateItem()
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.GroupHeaderList(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .animateItem()
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp)
    )
}

@Composable
fun MediaGrid(
    media: List<MediaItem>,
    columns: Int,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    showInfo: Boolean,
    selectedItems: Set<android.net.Uri>,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    var cumulativeScale by remember { mutableFloatStateOf(1f) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
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
        items(media, key = { it.uri.toString() }) { item ->
            MediaGridItem(
                item = item,
                showInfo = showInfo,
                isSelected = selectedItems.contains(item.uri),
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }
    }
}

@Composable
fun MediaList(
    media: List<MediaItem>,
    showInfo: Boolean,
    state: androidx.compose.foundation.lazy.LazyListState,
    selectedItems: Set<android.net.Uri>,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BottomGrey),
        state = state,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(media, key = { it.uri.toString() }) { item ->
            MediaListItem(
                item = item,
                showInfo = showInfo,
                isSelected = selectedItems.contains(item.uri),
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }
    }
}
