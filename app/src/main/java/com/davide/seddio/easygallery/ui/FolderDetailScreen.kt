package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.data.GalleryUiState
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.ViewPreferences
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.davide.seddio.easygallery.R
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.ui.components.*
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(viewModel: GalleryViewModel) {
    val scope = PreferenceScope.FOLDER_DETAIL
    val media by viewModel.filteredMedia.collectAsState()
    val selectedFolder: com.davide.seddio.easygallery.data.Folder? by viewModel.selectedFolder.collectAsState()
    val preferences by viewModel.preferences(scope).collectAsState()
    val searchQuery by viewModel.searchQuery(scope).collectAsState()
    val isSearchActive by viewModel.isSearchActive(scope).collectAsState()
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
        preferences = preferences,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
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
        onSetSearchQuery = { viewModel.setSearchQuery(it, scope) },
        onSetSearchActive = { viewModel.setSearchActive(it, scope) },
        onSetColumnsCount = { viewModel.setColumnsCount(it, scope) },
        onSetSelectedMediaTypes = { viewModel.setSelectedMediaTypes(it, scope) },
        onSetSortType = { viewModel.setSortType(it, scope) },
        onSetSortOrder = { viewModel.setSortOrder(it, scope) },
        onSetGroupBy = { viewModel.setGroupBy(it, scope) },
        onSetGroupOrder = { viewModel.setGroupOrder(it, scope) },
        onSetViewType = { viewModel.setViewType(it, scope) },
        onSetShowExcludedTemporarily = { viewModel.setShowExcludedTemporarily(it) },
        onSetSettingsMode = { viewModel.setSettingsMode(it) },
        onBackToFolders = { viewModel.backToFolders() },
        onToggleInfo = { viewModel.toggleInfo(scope) },
        onUpdateBrowsingPath = { viewModel.updateBrowsingPath(it) },
        onPerformOperationWithPath = { viewModel.performOperationWithPath(it) },
        onCancelOperation = { viewModel.cancelOperation() },
        getSelectedMediaData = { viewModel.getSelectedMediaData() },
        onSelectMedia = { viewModel.selectMedia(it) },
        onEnterMediaSelectionMode = { viewModel.enterMediaSelectionMode(it) },
        onDecreaseColumns = { viewModel.decreaseColumns(scope) },
        onIncreaseColumns = { viewModel.increaseColumns(scope) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailContent(
    media: List<MediaItem>,
    selectedFolder: com.davide.seddio.easygallery.data.Folder?,
    preferences: ViewPreferences,
    searchQuery: String,
    isSearchActive: Boolean,
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
    onSetColumnsCount: (Int) -> Unit,
    onSetSelectedMediaTypes: (Set<com.davide.seddio.easygallery.data.MediaType>) -> Unit,
    onSetSortType: (SortType) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onSetGroupBy: (GroupByType) -> Unit,
    onSetGroupOrder: (SortOrder) -> Unit,
    onSetViewType: (ViewType) -> Unit,
    onSetShowExcludedTemporarily: (Boolean) -> Unit,
    onSetSettingsMode: (Boolean) -> Unit,
    onBackToFolders: () -> Unit,
    onToggleInfo: () -> Unit,
    onUpdateBrowsingPath: (String) -> Unit,
    onPerformOperationWithPath: (String) -> Unit,
    onCancelOperation: () -> Unit,
    getSelectedMediaData: () -> List<MediaItem>,
    onSelectMedia: (MediaItem) -> Unit,
    onEnterMediaSelectionMode: (MediaItem) -> Unit,
    onDecreaseColumns: () -> Unit,
    onIncreaseColumns: () -> Unit
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = LocalContext.current
    val resolvedSelectedMedia = remember(selectedMediaItems) { getSelectedMediaData() }
    val wallpaperEligibleMedia = resolvedSelectedMedia.singleOrNull()
        ?.takeIf { supportsWallpaper(it.type) }

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
    
    Scaffold(
        topBar = {
            if (isMediaSelectionMode) {
                MediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = media.size,
                    onClose = { onExitMediaSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onShare = { shareMedia(context, resolvedSelectedMedia) },
                    onInfoClick = { showPropertiesDialog = true },
                    canUseAsBackground = wallpaperEligibleMedia != null,
                    onUseAsBackground = {
                        wallpaperEligibleMedia?.let { setImageAsWallpaper(context, it.uri) }
                    },
                    onCopyTo = { onStartOperation(OperationType.COPY) },
                    onMoveTo = { onStartOperation(OperationType.MOVE) },
                    onSelectAll = { onSelectAllMedia() }
                )
            } else {
                SearchTopBar(
                    title = selectedFolder?.name ?: stringResource(R.string.gallery_title),
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onToggleInfo() },
                            modifier = if (preferences.showInfo) Modifier.background(Color.White, CircleShape) else Modifier
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.cd_toggle_info),
                                tint = if (preferences.showInfo) BrandBlue else Color.White
                            )
                        }
                    }
                )
            }
        },
        containerColor = AppBackground
    ) { padding ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete_media_title)) },
                text = { Text(stringResource(R.string.delete_media_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteSelectedMedia()
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

        if (showPropertiesDialog) {
            MediaPropertiesDialog(
                media = getSelectedMediaData(),
                onDismiss = { showPropertiesDialog = false }
            )
        }

        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = preferences.columns,
                onCountSelected = {
                    onSetColumnsCount(it)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showFilterDialog) {
            FilterMediaDialog(
                initialSelectedTypes = preferences.mediaTypes,
                onConfirm = {
                    onSetSelectedMediaTypes(it)
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false }
            )
        }

        if (showSortDialog) {
            SortDialog(
                currentSort = preferences.sortType,
                currentOrder = preferences.sortOrder,
                onSortSelected = {
                    onSetSortType(it)
                },
                onOrderSelected = {
                    onSetSortOrder(it)
                },
                onDismiss = { showSortDialog = false }
            )
        }

        if (showGroupByDialog) {
            GroupByDialog(
                currentGroupBy = preferences.groupBy,
                currentOrder = preferences.groupOrder,
                onGroupBySelected = { onSetGroupBy(it) },
                onOrderSelected = { onSetGroupOrder(it) },
                onDismiss = { showGroupByDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = preferences.viewType,
                onViewTypeSelected = {
                    onSetViewType(it)
                    showViewTypeDialog = false
                },
                onDismiss = { showViewTypeDialog = false }
            )
        }

        if (isDestinationPickerActive) {
            DestinationFolderPickerDialog(
                title = if (pendingOperation == OperationType.MOVE) stringResource(R.string.destination_move_title) else stringResource(R.string.destination_copy_title),
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
                .background(AppBackground)
        ) {
            if (preferences.groupBy == GroupByType.NONE) {
                if (preferences.viewType == ViewType.GRID) {
                    MediaGrid(
                        media = media,
                        columns = preferences.columns,
                        state = gridState,
                        showInfo = preferences.showInfo,
                        selectedItems = selectedMediaItems,
                        onItemClick = { onSelectMedia(it) },
                        onItemLongClick = { onEnterMediaSelectionMode(it) },
                        onZoomIn = { onDecreaseColumns() },
                        onZoomOut = { onIncreaseColumns() }
                    )
                } else {
                    MediaList(
                        media = media,
                        state = listState,
                        showInfo = preferences.showInfo,
                        selectedItems = selectedMediaItems,
                        onItemClick = { onSelectMedia(it) },
                        onItemLongClick = { onEnterMediaSelectionMode(it) }
                    )
                }
            } else {
                GroupedMediaContent(
                    groupedMedia = groupedMedia,
                    viewType = preferences.viewType,
                    columns = preferences.columns,
                    gridState = gridState,
                    listState = listState,
                    showInfo = preferences.showInfo,
                    selectedItems = selectedMediaItems,
                    onItemClick = { onSelectMedia(it) },
                    onItemLongClick = { onEnterMediaSelectionMode(it) },
                    onZoomIn = { onDecreaseColumns() },
                    onZoomOut = { onIncreaseColumns() }
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
                .background(AppBackground)
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
                .background(AppBackground),
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
        modifier = Modifier.fillMaxSize().background(AppBackground),
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
