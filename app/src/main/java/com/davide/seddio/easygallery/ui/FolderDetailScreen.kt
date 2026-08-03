package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.data.GalleryUiState
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.data.OperationType
import com.davide.seddio.easygallery.data.GroupByType
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
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.ui.components.*
import com.davide.seddio.easygallery.ui.theme.BottomGrey

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
    val isDestinationPickerActive by viewModel.isDestinationPickerActive.collectAsState()
    val pendingOperation by viewModel.pendingOperation.collectAsState()
    val browsingPath by viewModel.browsingPath.collectAsState()
    val browsingFolders by viewModel.browsingFolders.collectAsState()

    FolderDetailContent(
        media = media,
        selectedFolder = selectedFolder,
        columnsCount = columnsCount,
        showInfo = showInfo,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        selectedMediaTypes = selectedMediaTypes,
        pictureSortType = pictureSortType,
        pictureSortOrder = pictureSortOrder,
        pictureViewType = pictureViewType,
        pictureGroupBy = pictureGroupBy,
        pictureGroupOrder = pictureGroupOrder,
        groupedMedia = groupedMedia,
        isMediaSelectionMode = isMediaSelectionMode,
        selectedMediaItems = selectedMediaItems,
        isDestinationPickerActive = isDestinationPickerActive,
        pendingOperation = pendingOperation,
        browsingPath = browsingPath,
        browsingFolders = browsingFolders,
        onExitMediaSelectionMode = { viewModel.exitMediaSelectionMode() },
        onDeleteSelectedMedia = { viewModel.deleteSelectedMedia() },
        onStartOperation = { viewModel.startOperation(it) },
        onSelectAllMedia = { viewModel.selectAllMedia() },
        onSetSearchQuery = { viewModel.setSearchQuery(it) },
        onSetSearchActive = { viewModel.setSearchActive(it) },
        onSetColumnsCount = { count, forPictures -> viewModel.setColumnsCount(count, forPictures) },
        onSetSelectedMediaTypes = { viewModel.setSelectedMediaTypes(it) },
        onSetSortType = { type, forPictures -> viewModel.setSortType(type, forPictures) },
        onSetSortOrder = { order, forPictures -> viewModel.setSortOrder(order, forPictures) },
        onSetGroupBy = { viewModel.setGroupBy(it) },
        onSetGroupOrder = { viewModel.setGroupOrder(it) },
        onSetViewType = { type, forPictures -> viewModel.setViewType(type, forPictures) },
        onSetShowExcludedTemporarily = { viewModel.setShowExcludedTemporarily(it) },
        onSetSettingsMode = { viewModel.setSettingsMode(it) },
        onBackToFolders = { viewModel.backToFolders() },
        onToggleInfo = { viewModel.toggleInfo() },
        onUpdateBrowsingPath = { viewModel.updateBrowsingPath(it) },
        onPerformOperationWithPath = { viewModel.performOperationWithPath(it) },
        onCancelOperation = { viewModel.cancelOperation() },
        getSelectedMediaData = { viewModel.getSelectedMediaData() },
        onRotateSelectedMedia = { viewModel.rotateSelectedMedia(it) },
        onSelectMedia = { viewModel.selectMedia(it) },
        onEnterMediaSelectionMode = { viewModel.enterMediaSelectionMode(it) },
        onDecreaseColumns = { viewModel.decreaseColumns(it) },
        onIncreaseColumns = { viewModel.increaseColumns(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailContent(
    media: List<MediaItem>,
    selectedFolder: com.davide.seddio.easygallery.data.Folder?,
    columnsCount: Int,
    showInfo: Boolean,
    searchQuery: String,
    isSearchActive: Boolean,
    selectedMediaTypes: Set<com.davide.seddio.easygallery.data.MediaType>,
    pictureSortType: SortType,
    pictureSortOrder: SortOrder,
    pictureViewType: ViewType,
    pictureGroupBy: GroupByType,
    pictureGroupOrder: SortOrder,
    groupedMedia: Map<String, List<MediaItem>>,
    isMediaSelectionMode: Boolean,
    selectedMediaItems: Set<android.net.Uri>,
    isDestinationPickerActive: Boolean,
    pendingOperation: OperationType?,
    browsingPath: String,
    browsingFolders: List<com.davide.seddio.easygallery.data.Folder>,
    onExitMediaSelectionMode: () -> Unit,
    onDeleteSelectedMedia: () -> Unit,
    onStartOperation: (OperationType) -> Unit,
    onSelectAllMedia: () -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onSetColumnsCount: (Int, Boolean) -> Unit,
    onSetSelectedMediaTypes: (Set<com.davide.seddio.easygallery.data.MediaType>) -> Unit,
    onSetSortType: (SortType, Boolean) -> Unit,
    onSetSortOrder: (SortOrder, Boolean) -> Unit,
    onSetGroupBy: (GroupByType) -> Unit,
    onSetGroupOrder: (SortOrder) -> Unit,
    onSetViewType: (ViewType, Boolean) -> Unit,
    onSetShowExcludedTemporarily: (Boolean) -> Unit,
    onSetSettingsMode: (Boolean) -> Unit,
    onBackToFolders: () -> Unit,
    onToggleInfo: () -> Unit,
    onUpdateBrowsingPath: (String) -> Unit,
    onPerformOperationWithPath: (String) -> Unit,
    onCancelOperation: () -> Unit,
    getSelectedMediaData: () -> List<MediaItem>,
    onRotateSelectedMedia: (Int) -> Unit,
    onSelectMedia: (MediaItem) -> Unit,
    onEnterMediaSelectionMode: (MediaItem) -> Unit,
    onDecreaseColumns: (Boolean) -> Unit,
    onIncreaseColumns: (Boolean) -> Unit
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    if (isMediaSelectionMode) {
        BackHandler { onExitMediaSelectionMode() }
    } else {
        BackHandler { onBackToFolders() }
    }

    var showColumnCountDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupByDialog by remember { mutableStateOf(false) }
    var showViewTypeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showRotateDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            if (isMediaSelectionMode) {
                MediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = media.size,
                    onClose = { onExitMediaSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onInfoClick = { showPropertiesDialog = true },
                    onRotate = { showRotateDialog = true },
                    onCopyTo = { onStartOperation(OperationType.COPY) },
                    onMoveTo = { onStartOperation(OperationType.MOVE) },
                    onSelectAll = { onSelectAllMedia() }
                )
            } else {
                SearchTopBar(
                    title = selectedFolder?.name ?: "Gallery",
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    onSearchQueryChange = { onSetSearchQuery(it) },
                    onSearchActiveChange = { onSetSearchActive(it) },
                    onColumnCountClick = { showColumnCountDialog = true },
                    onFilterMediaClick = { showFilterDialog = true },
                    onSortClick = { showSortDialog = true },
                    onGroupByClick = { showGroupByDialog = true },
                    onViewTypeClick = { showViewTypeDialog = true },
                    onShowExcludedClick = { onSetShowExcludedTemporarily(true) },
                    onSettingsClick = { onSetSettingsMode(true) },
                    navigationIcon = {
                        IconButton(onClick = { onBackToFolders() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onToggleInfo() }) {
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
                        onDeleteSelectedMedia()
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
                media = getSelectedMediaData(),
                onDismiss = { showPropertiesDialog = false }
            )
        }

        if (showRotateDialog) {
            RotateDialog(
                onRotate = {
                    onRotateSelectedMedia(it)
                    showRotateDialog = false
                },
                onDismiss = { showRotateDialog = false }
            )
        }

        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = columnsCount,
                onCountSelected = {
                    onSetColumnsCount(it, true)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showFilterDialog) {
            FilterMediaDialog(
                initialSelectedTypes = selectedMediaTypes,
                onConfirm = {
                    onSetSelectedMediaTypes(it)
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
                    onSetSortType(it, true)
                },
                onOrderSelected = {
                    onSetSortOrder(it, true)
                },
                onDismiss = { showSortDialog = false }
            )
        }

        if (showGroupByDialog) {
            GroupByDialog(
                currentGroupBy = pictureGroupBy,
                currentOrder = pictureGroupOrder,
                onGroupBySelected = { onSetGroupBy(it) },
                onOrderSelected = { onSetGroupOrder(it) },
                onDismiss = { showGroupByDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = pictureViewType,
                onViewTypeSelected = {
                    onSetViewType(it, true)
                    showViewTypeDialog = false
                },
                onDismiss = { showViewTypeDialog = false }
            )
        }

        if (isDestinationPickerActive) {
            DestinationFolderPickerDialog(
                title = if (pendingOperation == OperationType.MOVE) "Move to..." else "Copy to...",
                currentPath = browsingPath,
                folders = browsingFolders,
                onFolderSelected = { onUpdateBrowsingPath(it.path) },
                onBreadcrumbClick = { onUpdateBrowsingPath(it) },
                onConfirm = { onPerformOperationWithPath(browsingPath) },
                onDismiss = { onCancelOperation() }
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
                        onItemClick = { onSelectMedia(it) },
                        onItemLongClick = { onEnterMediaSelectionMode(it) },
                        onZoomIn = { onDecreaseColumns(true) },
                        onZoomOut = { onIncreaseColumns(true) }
                    )
                } else {
                    MediaList(
                        media = media,
                        state = listState,
                        showInfo = showInfo,
                        selectedItems = selectedMediaItems,
                        onItemClick = { onSelectMedia(it) },
                        onItemLongClick = { onEnterMediaSelectionMode(it) }
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
                    onItemClick = { onSelectMedia(it) },
                    onItemLongClick = { onEnterMediaSelectionMode(it) },
                    onZoomIn = { onDecreaseColumns(true) },
                    onZoomOut = { onIncreaseColumns(true) }
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
        fontWeight = FontWeight.Normal,
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
        fontWeight = FontWeight.Normal,
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
