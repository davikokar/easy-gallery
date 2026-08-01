package com.davide.seddio.easygallery.ui

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
    
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    if (isMediaSelectionMode) {
        BackHandler { viewModel.exitMediaSelectionMode() }
    } else if (isSelectionMode) {
        BackHandler { viewModel.exitSelectionMode() }
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
    
    val isDestinationPickerActive by viewModel.isDestinationPickerActive.collectAsState()
    val pendingOperation by viewModel.pendingOperation.collectAsState()

    val totalFolders = if (uiState is GalleryUiState.Success) (uiState as GalleryUiState.Success).folders.size else 0

    Scaffold(
        topBar = {
            if (isMediaSelectionMode) {
                val mediaList = if (displayMode == DisplayMode.CALENDAR) {
                    viewModel.filteredAllMedia.collectAsState().value
                } else {
                    emptyList()
                }
                MediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = mediaList.size,
                    onClose = { viewModel.exitMediaSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onInfoClick = { showPropertiesDialog = true },
                    onRename = { /* Placeholder */ },
                    onRotate = { showRotateDialog = true },
                    onCopyTo = { viewModel.startOperation(OperationType.COPY) },
                    onMoveTo = { viewModel.startOperation(OperationType.MOVE) },
                    onSelectAll = { viewModel.selectAllMedia() }
                )
            } else if (isSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectedFolders.size,
                    totalCount = totalFolders,
                    onClose = { viewModel.exitSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onPin = { viewModel.pinSelected() },
                    onInfoClick = { showPropertiesDialog = true },
                    onSelectAll = { viewModel.selectAll() },
                    onExclude = { showExcludeDialog = true },
                    onRename = { /* Placeholder */ },
                    onCopyTo = { viewModel.startOperation(OperationType.COPY) },
                    onMoveTo = { viewModel.startOperation(OperationType.MOVE) }
                )
            } else {
                SearchTopBar(
                    title = if (displayMode == DisplayMode.GALLERY) "Easy Gallery" else "Timeline",
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    displayMode = displayMode,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSearchActiveChange = { viewModel.setSearchActive(it) },
                    onToggleDisplayMode = { viewModel.toggleDisplayMode() },
                    onSortClick = { showSortDialog = true },
                    onColumnCountClick = { showColumnCountDialog = true },
                    onGroupByClick = if (displayMode == DisplayMode.CALENDAR) { { showGroupByDialog = true } } else null,
                    onViewTypeClick = { showViewTypeDialog = true },
                    onFilterMediaClick = { showFilterDialog = true },
                    onShowExcludedClick = { viewModel.setShowExcludedTemporarily(true) },
                    onSettingsClick = { viewModel.setSettingsMode(true) }
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
                            viewModel.deleteSelectedMedia()
                        } else {
                            viewModel.deleteSelected()
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
                        viewModel.excludeSelected()
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
                    viewModel.setSortType(it, forPictures = displayMode != DisplayMode.GALLERY)
                },
                onOrderSelected = {
                    viewModel.setSortOrder(it, forPictures = displayMode != DisplayMode.GALLERY)
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

        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = if (displayMode == DisplayMode.GALLERY) folderColumns else pictureColumns,
                onCountSelected = {
                    viewModel.setColumnsCount(it, forPictures = displayMode != DisplayMode.GALLERY)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = if (displayMode == DisplayMode.GALLERY) folderViewType else pictureViewType,
                onViewTypeSelected = {
                    viewModel.setViewType(it, forPictures = displayMode != DisplayMode.GALLERY)
                    showViewTypeDialog = false
                },
                onDismiss = { showViewTypeDialog = false }
            )
        }

        if (showPropertiesDialog) {
            if (isMediaSelectionMode) {
                MediaPropertiesDialog(
                    media = viewModel.getSelectedMediaData(),
                    onDismiss = { showPropertiesDialog = false }
                )
            } else {
                PropertiesDialog(
                    folders = viewModel.getSelectedFoldersData(),
                    onDismiss = { showPropertiesDialog = false }
                )
            }
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
            if (displayMode == DisplayMode.CALENDAR) {
                CalendarGrid(
                    viewModel = viewModel,
                    groupedPhotos = groupedAllMedia,
                    columns = pictureColumns,
                    showInfo = showInfo
                )
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
                                onFolderClick = { viewModel.selectFolder(it) },
                                onFolderLongClick = { viewModel.enterSelectionMode(it.path) },
                                onZoomIn = { viewModel.decreaseColumns(forPictures = false) },
                                onZoomOut = { viewModel.increaseColumns(forPictures = false) }
                            )
                        } else {
                            FolderList(
                                folders = state.folders,
                                state = listState,
                                selectedFolders = selectedFolders,
                                onFolderClick = { viewModel.selectFolder(it) },
                                onFolderLongClick = { viewModel.enterSelectionMode(it.path) }
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
        modifier = Modifier.fillMaxSize().background(BottomGrey),
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
