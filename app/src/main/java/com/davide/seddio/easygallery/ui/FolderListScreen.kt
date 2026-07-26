package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.davide.seddio.easygallery.ui.components.SearchTopBar
import com.davide.seddio.easygallery.ui.components.SelectionTopBar
import com.davide.seddio.easygallery.ui.components.ColumnCountDialog
import com.davide.seddio.easygallery.ui.components.FilterMediaDialog
import com.davide.seddio.easygallery.ui.theme.BottomGrey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(viewModel: GalleryViewModel) {
    val uiState by viewModel.filteredFolders.collectAsState()
    val folderColumns by viewModel.folderColumns.collectAsState()
    val pictureColumns by viewModel.pictureColumns.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedFolders by viewModel.selectedFolders.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val groupedPhotos by viewModel.groupedPhotosByDate.collectAsState()
    val showInfo by viewModel.showInfo.collectAsState()
    val folderSortType by viewModel.folderSortType.collectAsState()
    val pictureSortType by viewModel.pictureSortType.collectAsState()
    val folderSortOrder by viewModel.folderSortOrder.collectAsState()
    val pictureSortOrder by viewModel.pictureSortOrder.collectAsState()
    val folderViewType by viewModel.folderViewType.collectAsState()
    val pictureViewType by viewModel.pictureViewType.collectAsState()
    val selectedMediaTypes by viewModel.selectedMediaTypes.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showColumnCountDialog by remember { mutableStateOf(false) }
    var showViewTypeDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showExcludeDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val totalFolders = if (uiState is GalleryUiState.Success) (uiState as GalleryUiState.Success).folders.size else 0

    Scaffold(
        topBar = {
            if (isSelectionMode) {
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
                    onCopyTo = { /* Placeholder */ },
                    onMoveTo = { /* Placeholder */ }
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
                    onViewTypeClick = { showViewTypeDialog = true },
                    onFilterMediaClick = { showFilterDialog = true },
                    onSettingsClick = { viewModel.setSettingsMode(true) }
                )
            }
        },
        containerColor = BottomGrey
    ) { padding ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Folders") },
                text = { Text("Are you sure you want to delete the selected folders? This action might be irreversible.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSelected()
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
            PropertiesDialog(
                folders = viewModel.getSelectedFoldersData(),
                onDismiss = { showPropertiesDialog = false }
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

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BottomGrey)
        ) {
            if (displayMode == DisplayMode.CALENDAR) {
                CalendarGrid(
                    viewModel = viewModel,
                    groupedPhotos = groupedPhotos,
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
                                selectedFolders = selectedFolders,
                                onFolderClick = { viewModel.selectFolder(it) },
                                onFolderLongClick = { viewModel.enterSelectionMode(it.name) },
                                onZoomIn = { viewModel.decreaseColumns(forPictures = false) },
                                onZoomOut = { viewModel.increaseColumns(forPictures = false) }
                            )
                        } else {
                            FolderList(
                                folders = state.folders,
                                selectedFolders = selectedFolders,
                                onFolderClick = { viewModel.selectFolder(it) },
                                onFolderLongClick = { viewModel.enterSelectionMode(it.name) }
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
fun PropertiesDialog(folders: List<Folder>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Items selected: ${folders.size}")
                val totalSizeMb = folders.sumOf { it.size } / (1024 * 1024)
                Text("Content size: $totalSizeMb MB")
                val totalFiles = folders.sumOf { it.imageCount }
                Text("Total files count: $totalFiles")

                if (folders.size == 1) {
                    val folder = folders[0]
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Name: ${folder.name}", fontWeight = FontWeight.Bold)
                    Text("Path: ${folder.path}", style = MaterialTheme.typography.bodySmall)
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
            modifier = Modifier.padding(start = 16.dp)
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
fun SortOption(
    label: String,
    type: SortType,
    selected: Boolean,
    onSortSelected: (SortType) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = { onSortSelected(type) },
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun OrderOption(
    label: String,
    order: SortOrder,
    selected: Boolean,
    enabled: Boolean,
    onOrderSelected: (SortOrder) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = { if (enabled) onOrderSelected(order) },
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun FolderGrid(
    folders: List<Folder>,
    columns: Int,
    selectedFolders: Set<String>,
    onFolderClick: (Folder) -> Unit,
    onFolderLongClick: (Folder) -> Unit,
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
        items(folders) { folder ->
            FolderGridItem(
                folder = folder,
                isSelected = selectedFolders.contains(folder.name),
                onClick = { onFolderClick(folder) },
                onLongClick = { onFolderLongClick(folder) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridItem(
    folder: Folder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
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
            
            // Pin icon for pinned folders
            if (folder.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(20.dp)
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
    selectedFolders: Set<String>,
    onFolderClick: (Folder) -> Unit,
    onFolderLongClick: (Folder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BottomGrey),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(folders) { folder ->
            FolderListItem(
                folder = folder,
                isSelected = selectedFolders.contains(folder.name),
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else BottomGrey
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(androidx.compose.ui.graphics.RectangleShape)
            ) {
                AsyncImage(
                    model = folder.thumbnailUri,
                    contentDescription = folder.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (folder.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
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
            }

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = Color.White
                    )
                    Text(
                        text = "${folder.imageCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
