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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.ui.components.*
import com.davide.seddio.easygallery.ui.theme.BottomGrey
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(viewModel: GalleryViewModel) {
    val uiState by viewModel.filteredFolders.collectAsState()
    val folderColumns by viewModel.folderColumns.collectAsState()
    val pictureColumns by viewModel.pictureColumns.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isMediaSelectionMode by viewModel.isMediaSelectionMode.collectAsState()
    val selectedMediaItems by viewModel.selectedMediaItems.collectAsState()
    val selectedFolders by viewModel.selectedFolders.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val groupedAllMedia by viewModel.groupedAllMedia.collectAsState()
    val showInfo by viewModel.showInfo.collectAsState()
    val folderSortType by viewModel.folderSortType.collectAsState()
    val pictureSortType by viewModel.pictureSortType.collectAsState()
    val folderSortOrder by viewModel.folderSortOrder.collectAsState()
    val pictureSortOrder by viewModel.pictureSortOrder.collectAsState()
    val pictureGroupBy by viewModel.pictureGroupBy.collectAsState()
    val pictureGroupOrder by viewModel.pictureGroupOrder.collectAsState()
    val folderViewType by viewModel.folderViewType.collectAsState()
    val pictureViewType by viewModel.pictureViewType.collectAsState()
    val selectedMediaTypes by viewModel.selectedMediaTypes.collectAsState()
    val isDestinationPickerActive by viewModel.isDestinationPickerActive.collectAsState()
    val pendingOperation by viewModel.pendingOperation.collectAsState()
    val browsingPath by viewModel.browsingPath.collectAsState()
    val browsingFolders by viewModel.browsingFolders.collectAsState()

    FolderListContent(
        uiState = uiState,
        folderColumns = folderColumns,
        pictureColumns = pictureColumns,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        isSelectionMode = isSelectionMode,
        isMediaSelectionMode = isMediaSelectionMode,
        selectedMediaItems = selectedMediaItems,
        selectedFolders = selectedFolders,
        displayMode = displayMode,
        groupedAllMedia = groupedAllMedia,
        showInfo = showInfo,
        folderSortType = folderSortType,
        pictureSortType = pictureSortType,
        folderSortOrder = folderSortOrder,
        pictureSortOrder = pictureSortOrder,
        pictureGroupBy = pictureGroupBy,
        pictureGroupOrder = pictureGroupOrder,
        folderViewType = folderViewType,
        pictureViewType = pictureViewType,
        selectedMediaTypes = selectedMediaTypes,
        isDestinationPickerActive = isDestinationPickerActive,
        pendingOperation = pendingOperation,
        browsingPath = browsingPath,
        browsingFolders = browsingFolders,
        onExitMediaSelectionMode = { viewModel.exitMediaSelectionMode() },
        onExitSelectionMode = { viewModel.exitSelectionMode() },
        onDeleteSelectedMedia = { viewModel.deleteSelectedMedia() },
        onDeleteSelectedFolders = { viewModel.deleteSelected() },
        onPinSelected = { viewModel.pinSelected() },
        onSelectAllMedia = { viewModel.selectAllMedia() },
        onSelectAllFolders = { viewModel.selectAll() },
        onExcludeSelected = { viewModel.excludeSelected() },
        onStartOperation = { viewModel.startOperation(it) },
        onSetSearchQuery = { viewModel.setSearchQuery(it) },
        onSetSearchActive = { viewModel.setSearchActive(it) },
        onToggleDisplayMode = { viewModel.toggleDisplayMode() },
        onSetSortType = { type, forPictures -> viewModel.setSortType(type, forPictures) },
        onSetSortOrder = { order, forPictures -> viewModel.setSortOrder(order, forPictures) },
        onSetGroupBy = { viewModel.setGroupBy(it) },
        onSetGroupOrder = { viewModel.setGroupOrder(it) },
        onSetColumnsCount = { count, forPictures -> viewModel.setColumnsCount(count, forPictures) },
        onSetViewType = { type, forPictures -> viewModel.setViewType(type, forPictures) },
        onSetSelectedMediaTypes = { viewModel.setSelectedMediaTypes(it) },
        onSetShowExcludedTemporarily = { viewModel.setShowExcludedTemporarily(it) },
        onSetSettingsMode = { viewModel.setSettingsMode(it) },
        onUpdateBrowsingPath = { viewModel.updateBrowsingPath(it) },
        onPerformOperationWithPath = { viewModel.performOperationWithPath(it) },
        onCancelOperation = { viewModel.cancelOperation() },
        onSelectFolder = { viewModel.selectFolder(it) },
        onEnterSelectionMode = { viewModel.enterSelectionMode(it) },
        onDecreaseColumns = { viewModel.decreaseColumns(it) },
        onIncreaseColumns = { viewModel.increaseColumns(it) },
        onToggleInfo = { viewModel.toggleInfo() },
        getSelectedMediaData = { viewModel.getSelectedMediaData() },
        getSelectedFoldersData = { viewModel.getSelectedFoldersData() },
        onRotateSelectedMedia = { viewModel.rotateSelectedMedia(it) },
        onSelectMedia = { viewModel.selectMedia(it) },
        onEnterMediaSelectionMode = { viewModel.enterMediaSelectionMode(it) },
        calendarContent = {
            CalendarGrid(
                viewModel = viewModel,
                groupedPhotos = groupedAllMedia,
                columns = pictureColumns,
                showInfo = showInfo
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListContent(
    uiState: GalleryUiState,
    folderColumns: Int,
    pictureColumns: Int,
    searchQuery: String,
    isSearchActive: Boolean,
    isSelectionMode: Boolean,
    isMediaSelectionMode: Boolean,
    selectedMediaItems: Set<android.net.Uri>,
    selectedFolders: Set<String>,
    displayMode: DisplayMode,
    groupedAllMedia: Map<String, List<com.davide.seddio.easygallery.data.MediaItem>>,
    showInfo: Boolean,
    folderSortType: SortType,
    pictureSortType: SortType,
    folderSortOrder: SortOrder,
    pictureSortOrder: SortOrder,
    pictureGroupBy: GroupByType,
    pictureGroupOrder: SortOrder,
    folderViewType: ViewType,
    pictureViewType: ViewType,
    selectedMediaTypes: Set<MediaType>,
    isDestinationPickerActive: Boolean,
    pendingOperation: OperationType?,
    browsingPath: String,
    browsingFolders: List<Folder>,
    onExitMediaSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onDeleteSelectedMedia: () -> Unit,
    onDeleteSelectedFolders: () -> Unit,
    onPinSelected: () -> Unit,
    onSelectAllMedia: () -> Unit,
    onSelectAllFolders: () -> Unit,
    onExcludeSelected: () -> Unit,
    onStartOperation: (OperationType) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onToggleDisplayMode: () -> Unit,
    onSetSortType: (SortType, Boolean) -> Unit,
    onSetSortOrder: (SortOrder, Boolean) -> Unit,
    onSetGroupBy: (GroupByType) -> Unit,
    onSetGroupOrder: (SortOrder) -> Unit,
    onSetColumnsCount: (Int, Boolean) -> Unit,
    onSetViewType: (ViewType, Boolean) -> Unit,
    onSetSelectedMediaTypes: (Set<MediaType>) -> Unit,
    onSetShowExcludedTemporarily: (Boolean) -> Unit,
    onSetSettingsMode: (Boolean) -> Unit,
    onUpdateBrowsingPath: (String) -> Unit,
    onPerformOperationWithPath: (String) -> Unit,
    onCancelOperation: () -> Unit,
    onSelectFolder: (Folder) -> Unit,
    onEnterSelectionMode: (String) -> Unit,
    onDecreaseColumns: (Boolean) -> Unit,
    onIncreaseColumns: (Boolean) -> Unit,
    onToggleInfo: () -> Unit,
    getSelectedMediaData: () -> List<com.davide.seddio.easygallery.data.MediaItem>,
    getSelectedFoldersData: () -> List<Folder>,
    onRotateSelectedMedia: (Int) -> Unit,
    onSelectMedia: (com.davide.seddio.easygallery.data.MediaItem) -> Unit,
    onEnterMediaSelectionMode: (com.davide.seddio.easygallery.data.MediaItem) -> Unit,
    calendarContent: @Composable () -> Unit
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    if (isMediaSelectionMode) {
        BackHandler { onExitMediaSelectionMode() }
    } else if (isSelectionMode) {
        BackHandler { onExitSelectionMode() }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupByDialog by remember { mutableStateOf(false) }
    var showColumnCountDialog by remember { mutableStateOf(false) }
    var showViewTypeDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showExcludeDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showRotateDialog by remember { mutableStateOf(false) }
    
    val totalFolders = if (uiState is GalleryUiState.Success) uiState.folders.size else 0

    Scaffold(
        topBar = {
            if (isMediaSelectionMode) {
                MediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = if (displayMode == DisplayMode.CALENDAR) groupedAllMedia.values.flatten().size else 0,
                    onClose = { onExitMediaSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onInfoClick = { showPropertiesDialog = true },
                    onRename = { /* Placeholder */ },
                    onRotate = { showRotateDialog = true },
                    onCopyTo = { onStartOperation(OperationType.COPY) },
                    onMoveTo = { onStartOperation(OperationType.MOVE) },
                    onSelectAll = { onSelectAllMedia() }
                )
            } else if (isSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectedFolders.size,
                    totalCount = totalFolders,
                    onClose = { onExitSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onPin = { onPinSelected() },
                    onInfoClick = { showPropertiesDialog = true },
                    onSelectAll = { onSelectAllFolders() },
                    onExclude = { showExcludeDialog = true },
                    onRename = { /* Placeholder */ },
                    onCopyTo = { onStartOperation(OperationType.COPY) },
                    onMoveTo = { onStartOperation(OperationType.MOVE) }
                )
            } else {
                SearchTopBar(
                    title = if (displayMode == DisplayMode.GALLERY) "Easy Gallery" else "Timeline",
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    displayMode = displayMode,
                    onSearchQueryChange = { onSetSearchQuery(it) },
                    onSearchActiveChange = { onSetSearchActive(it) },
                    onToggleDisplayMode = { onToggleDisplayMode() },
                    onSortClick = { showSortDialog = true },
                    onColumnCountClick = { showColumnCountDialog = true },
                    onGroupByClick = if (displayMode == DisplayMode.CALENDAR) { { showGroupByDialog = true } } else null,
                    onViewTypeClick = { showViewTypeDialog = true },
                    onFilterMediaClick = { showFilterDialog = true },
                    onShowExcludedClick = { onSetShowExcludedTemporarily(true) },
                    onSettingsClick = { onSetSettingsMode(true) }
                )
            }
        },
        containerColor = BottomGrey
    ) { padding ->
        if (showDeleteDialog) {
            val title = if (isMediaSelectionMode) "Delete Media" else "Delete Folders"
            val text = if (isMediaSelectionMode) {
                "Are you sure you want to delete the selected items? This action cannot be undone."
            } else {
                "Are you sure you want to delete the selected folders? This action might be irreversible."
            }

            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(title) },
                text = { Text(text) },
                confirmButton = {
                    TextButton(onClick = {
                        if (isMediaSelectionMode) {
                            onDeleteSelectedMedia()
                        } else {
                            onDeleteSelectedFolders()
                        }
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

        if (showExcludeDialog) {
            AlertDialog(
                onDismissRequest = { showExcludeDialog = false },
                title = { Text("Exclude Folders") },
                text = { Text("The selected folders will be excluded from the gallery. If you want to include them again, you must visit Settings > Manage Excluded.") },
                confirmButton = {
                    TextButton(onClick = {
                        onExcludeSelected()
                        showExcludeDialog = false
                    }) {
                        Text("Exclude")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExcludeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showSortDialog) {
            SortDialog(
                currentSort = if (displayMode == DisplayMode.GALLERY) folderSortType else pictureSortType,
                currentOrder = if (displayMode == DisplayMode.GALLERY) folderSortOrder else pictureSortOrder,
                onSortSelected = {
                    onSetSortType(it, displayMode != DisplayMode.GALLERY)
                },
                onOrderSelected = {
                    onSetSortOrder(it, displayMode != DisplayMode.GALLERY)
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

        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = if (displayMode == DisplayMode.GALLERY) folderColumns else pictureColumns,
                onCountSelected = {
                    onSetColumnsCount(it, displayMode != DisplayMode.GALLERY)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = if (displayMode == DisplayMode.GALLERY) folderViewType else pictureViewType,
                onViewTypeSelected = {
                    onSetViewType(it, displayMode != DisplayMode.GALLERY)
                    showViewTypeDialog = false
                },
                onDismiss = { showViewTypeDialog = false }
            )
        }

        if (showPropertiesDialog) {
            if (isMediaSelectionMode) {
                MediaPropertiesDialog(
                    media = getSelectedMediaData(),
                    onDismiss = { showPropertiesDialog = false }
                )
            } else {
                PropertiesDialog(
                    folders = getSelectedFoldersData(),
                    onDismiss = { showPropertiesDialog = false }
                )
            }
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
            if (displayMode == DisplayMode.CALENDAR) {
                calendarContent()
            } else {
                when (val state = uiState) {
                    is GalleryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is GalleryUiState.Success -> {
                        if (folderViewType == ViewType.GRID) {
                            FolderGrid(
                                folders = state.folders,
                                columns = folderColumns,
                                state = gridState,
                                selectedFolders = selectedFolders,
                                onFolderClick = { onSelectFolder(it) },
                                onFolderLongClick = { onEnterSelectionMode(it.path) },
                                onZoomIn = { onDecreaseColumns(false) },
                                onZoomOut = { onIncreaseColumns(false) }
                            )
                        } else {
                            FolderList(
                                folders = state.folders,
                                state = listState,
                                selectedFolders = selectedFolders,
                                onFolderClick = { onSelectFolder(it) },
                                onFolderLongClick = { onEnterSelectionMode(it.path) }
                            )
                        }
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
}

@Composable
fun DestinationFolderPickerDialog(
    title: String,
    currentPath: String,
    folders: List<Folder>,
    onFolderSelected: (Folder) -> Unit,
    onBreadcrumbClick: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Breadcrumb(
                    path = currentPath,
                    onBreadcrumbClick = onBreadcrumbClick
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                if (folders.isEmpty()) {
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No subfolders here.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(folders) { folder ->
                            FolderPickerItem(
                                folder = folder,
                                onClick = { onFolderSelected(folder) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun Breadcrumb(path: String, onBreadcrumbClick: (String) -> Unit) {
    val segments = remember(path) {
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        val relative = path.removePrefix(root).trimStart('/')
        val list = mutableListOf("Internal Storage" to root)
        if (relative.isNotEmpty()) {
            var current = root
            relative.split('/').forEach { segment ->
                current = "$current/$segment"
                list.add(segment to current)
            }
        }
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (label, fullPath) ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (index == segments.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onBreadcrumbClick(fullPath) }
            )
            if (index < segments.lastIndex) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun FolderPickerItem(folder: Folder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = folder.name,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PropertiesDialog(folders: List<Folder>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Items selected: ${folders.size}", color = MaterialTheme.colorScheme.onSurface)
                val totalSizeMb = folders.sumOf { it.size } / (1024 * 1024)
                Text("Content size: $totalSizeMb MB", color = MaterialTheme.colorScheme.onSurface)
                val totalFiles = folders.sumOf { it.imageCount }
                Text("Total files count: $totalFiles", color = MaterialTheme.colorScheme.onSurface)

                if (folders.size == 1) {
                    val folder = folders[0]
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Name: ${folder.name}", fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                    Text("Path: ${folder.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ViewTypeDialog(
    currentViewType: ViewType,
    onViewTypeSelected: (ViewType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change view type") },
        text = {
            Column(Modifier.selectableGroup()) {
                ViewTypeOption("Grid", ViewType.GRID, currentViewType == ViewType.GRID, onViewTypeSelected)
                ViewTypeOption("List", ViewType.LIST, currentViewType == ViewType.LIST, onViewTypeSelected)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ViewTypeOption(
    label: String,
    type: ViewType,
    selected: Boolean,
    onViewTypeSelected: (ViewType) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = selected,
                onClick = { onViewTypeSelected(type) },
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SortDialog(
    currentSort: SortType,
    currentOrder: SortOrder,
    onSortSelected: (SortType) -> Unit,
    onOrderSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column(Modifier.selectableGroup()) {
                SortOption("Name", SortType.NAME, currentSort == SortType.NAME, onSortSelected)
                SortOption("Path", SortType.PATH, currentSort == SortType.PATH, onSortSelected)
                SortOption("Size", SortType.SIZE, currentSort == SortType.SIZE, onSortSelected)
                SortOption("Last Modified", SortType.LAST_MODIFIED, currentSort == SortType.LAST_MODIFIED, onSortSelected)
                SortOption("Date Taken", SortType.DATE_TAKEN, currentSort == SortType.DATE_TAKEN, onSortSelected)
                SortOption("Random", SortType.RANDOM, currentSort == SortType.RANDOM, onSortSelected)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val isOrderEnabled = currentSort != SortType.RANDOM
                
                OrderOption(
                    label = "Ascending",
                    order = SortOrder.ASCENDING,
                    selected = currentOrder == SortOrder.ASCENDING,
                    enabled = isOrderEnabled,
                    onOrderSelected = onOrderSelected
                )
                OrderOption(
                    label = "Descending",
                    order = SortOrder.DESCENDING,
                    selected = currentOrder == SortOrder.DESCENDING,
                    enabled = isOrderEnabled,
                    onOrderSelected = onOrderSelected
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
fun FolderGrid(
    folders: List<Folder>,
    columns: Int,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    selectedFolders: Set<String>,
    onFolderClick: (Folder) -> Unit,
    onFolderLongClick: (Folder) -> Unit,
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
            .testTag("folder_grid")
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
        items(folders, key = { it.path }) { folder ->
            FolderGridItem(
                folder = folder,
                isSelected = selectedFolders.contains(folder.path),
                onClick = { onFolderClick(folder) },
                onLongClick = { onFolderLongClick(folder) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun androidx.compose.foundation.lazy.grid.LazyGridItemScope.FolderGridItem(
    folder: Folder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .animateItem()
            .testTag("folder_tile_${folder.path}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = androidx.compose.ui.graphics.RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = BottomGrey)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = folder.thumbnailUri,
                contentDescription = folder.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Selection overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .testTag("selected_checkmark")
                        .background(Color.White, CircleShape)
                )
            }
            
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

            // Pin icon for pinned folders
            if (folder.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(20.dp)
                        .testTag("pin_icon")
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .padding(2.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = folder.name,
                    color = Color.White,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.imageCount}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun FolderList(
    folders: List<Folder>,
    state: androidx.compose.foundation.lazy.LazyListState,
    selectedFolders: Set<String>,
    onFolderClick: (Folder) -> Unit,
    onFolderLongClick: (Folder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BottomGrey).testTag("folder_list"),
        state = state,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(folders) { folder ->
            FolderListItem(
                folder = folder,
                isSelected = selectedFolders.contains(folder.path),
                onClick = { onFolderClick(folder) },
                onLongClick = { onFolderLongClick(folder) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListItem(
    folder: Folder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("folder_tile_${folder.path}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.05f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = folder.thumbnailUri,
                    contentDescription = folder.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .testTag("selected_checkmark")
                            .background(Color.White, CircleShape)
                    )
                }
                if (folder.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(16.dp)
                            .testTag("pin_icon")
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .padding(1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        color = Color.White
                    )
                    Text(
                        text = "${folder.imageCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
