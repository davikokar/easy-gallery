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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.ui.components.*
import com.davide.seddio.easygallery.ui.theme.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    viewModel: GalleryViewModel,
    createFolderViewModel: CreateFolderViewModel
) {
    val uiState by viewModel.filteredFolders.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val activeScope =
        if (displayMode == DisplayMode.GALLERY) PreferenceScope.FOLDERS else PreferenceScope.TIMELINE
    val folderPreferences by viewModel.preferences(PreferenceScope.FOLDERS).collectAsState()
    val timelinePreferences by viewModel.preferences(PreferenceScope.TIMELINE).collectAsState()
    val searchQuery by viewModel.searchQuery(activeScope).collectAsState()
    val isSearchActive by viewModel.isSearchActive(activeScope).collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isMediaSelectionMode by viewModel.isMediaSelectionMode.collectAsState()
    val selectedMediaItems by viewModel.selectedMediaItems.collectAsState()
    val selectedFolders by viewModel.selectedFolders.collectAsState()
    val groupedAllMedia by viewModel.groupedAllMedia.collectAsState()
    val isDestinationPickerActive by viewModel.isDestinationPickerActive.collectAsState()
    val pendingOperation by viewModel.pendingOperation.collectAsState()
    val browsingPath by viewModel.browsingPath.collectAsState()
    val browsingFolders by viewModel.browsingFolders.collectAsState()

    val isCreateFolderDialogOpen by createFolderViewModel.isDialogOpen.collectAsState()
    val createFolderError by createFolderViewModel.error.collectAsState()
    val createFolderBrowsingPath by createFolderViewModel.browsingPath.collectAsState()
    val createFolderBrowsingFolders by createFolderViewModel.browsingFolders.collectAsState()

    LaunchedEffect(Unit) {
        createFolderViewModel.folderCreated.collect { viewModel.loadFolders() }
    }

    FolderListContent(
        uiState = uiState,
        folderPreferences = folderPreferences,
        timelinePreferences = timelinePreferences,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        isSelectionMode = isSelectionMode,
        isMediaSelectionMode = isMediaSelectionMode,
        selectedMediaItems = selectedMediaItems,
        selectedFolders = selectedFolders,
        displayMode = displayMode,
        groupedAllMedia = groupedAllMedia,
        isDestinationPickerActive = isDestinationPickerActive,
        isCreateFolderDialogOpen = isCreateFolderDialogOpen,
        createFolderError = createFolderError,
        createFolderBrowsingPath = createFolderBrowsingPath,
        createFolderBrowsingFolders = createFolderBrowsingFolders,
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
        onSetSearchQuery = { viewModel.setSearchQuery(it, activeScope) },
        onSetSearchActive = { viewModel.setSearchActive(it, activeScope) },
        onToggleDisplayMode = { viewModel.toggleDisplayMode() },
        onSetSort = { sortType, sortOrder ->
            viewModel.setSortType(sortType, activeScope)
            viewModel.setSortOrder(sortOrder, activeScope)
        },
        onSetGroupByAndOrder = { groupBy, groupOrder ->
            viewModel.setGroupBy(groupBy, PreferenceScope.TIMELINE)
            viewModel.setGroupOrder(groupOrder, PreferenceScope.TIMELINE)
        },
        onSetColumnsCount = { viewModel.setColumnsCount(it, activeScope) },
        onSetViewType = { viewModel.setViewType(it, activeScope) },
        onSetSelectedMediaTypes = { viewModel.setSelectedMediaTypes(it, activeScope) },
        onSetShowExcludedTemporarily = { viewModel.setShowExcludedTemporarily(it) },
        onSetSettingsMode = { viewModel.setSettingsMode(it) },
        onSetCreateFolderDialogOpen = { createFolderViewModel.setDialogOpen(it) },
        onCreateFolder = { createFolderViewModel.createFolder(it) },
        onUpdateCreateFolderBrowsingPath = { createFolderViewModel.updateBrowsingPath(it) },
        onUpdateBrowsingPath = { viewModel.updateBrowsingPath(it) },
        onPerformOperationWithPath = { viewModel.performOperationWithPath(it) },
        onCancelOperation = { viewModel.cancelOperation() },
        onSelectFolder = { viewModel.selectFolder(it) },
        onEnterSelectionMode = { viewModel.enterSelectionMode(it) },
        onDecreaseColumns = { viewModel.decreaseColumns(PreferenceScope.FOLDERS) },
        onIncreaseColumns = { viewModel.increaseColumns(PreferenceScope.FOLDERS) },
        getSelectedMediaData = { viewModel.getSelectedMediaData() },
        getSelectedFoldersData = { viewModel.getSelectedFoldersData() },
        onSelectMedia = { viewModel.selectMedia(it) },
        onEnterMediaSelectionMode = { viewModel.enterMediaSelectionMode(it) },
        calendarContent = {
            CalendarGrid(
                viewModel = viewModel,
                groupedPhotos = groupedAllMedia,
                columns = timelinePreferences.columns,
                showInfo = timelinePreferences.showInfo
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListContent(
    uiState: GalleryUiState,
    folderPreferences: ViewPreferences,
    timelinePreferences: ViewPreferences,
    searchQuery: String,
    isSearchActive: Boolean,
    isSelectionMode: Boolean,
    isMediaSelectionMode: Boolean,
    selectedMediaItems: Set<android.net.Uri>,
    selectedFolders: Set<String>,
    displayMode: DisplayMode,
    groupedAllMedia: Map<String, List<com.davide.seddio.easygallery.data.MediaItem>>,
    isDestinationPickerActive: Boolean,
    isCreateFolderDialogOpen: Boolean,
    createFolderError: String?,
    createFolderBrowsingPath: String,
    createFolderBrowsingFolders: List<Folder>,
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
    onSetSort: (SortType, SortOrder) -> Unit,
    onSetGroupByAndOrder: (GroupByType, SortOrder) -> Unit,
    onSetColumnsCount: (Int) -> Unit,
    onSetViewType: (ViewType) -> Unit,
    onSetSelectedMediaTypes: (Set<MediaType>) -> Unit,
    onSetShowExcludedTemporarily: (Boolean) -> Unit,
    onSetSettingsMode: (Boolean) -> Unit,
    onSetCreateFolderDialogOpen: (Boolean) -> Unit,
    onCreateFolder: (String) -> Unit,
    onUpdateCreateFolderBrowsingPath: (String) -> Unit,
    onUpdateBrowsingPath: (String) -> Unit,
    onPerformOperationWithPath: (String) -> Unit,
    onCancelOperation: () -> Unit,
    onSelectFolder: (Folder) -> Unit,
    onEnterSelectionMode: (String) -> Unit,
    onDecreaseColumns: () -> Unit,
    onIncreaseColumns: () -> Unit,
    getSelectedMediaData: () -> List<com.davide.seddio.easygallery.data.MediaItem>,
    getSelectedFoldersData: () -> List<Folder>,
    onSelectMedia: (com.davide.seddio.easygallery.data.MediaItem) -> Unit,
    onEnterMediaSelectionMode: (com.davide.seddio.easygallery.data.MediaItem) -> Unit,
    calendarContent: @Composable () -> Unit
) {
    val activePreferences =
        if (displayMode == DisplayMode.GALLERY) folderPreferences else timelinePreferences
    val context = LocalContext.current
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selectedMediaForActions = remember(selectedMediaItems) { getSelectedMediaData() }
    val selectedMediaForWallpaper = selectedMediaForActions.singleOrNull()
    val canUseAsBackground = selectedMediaForWallpaper?.let { supportsWallpaper(it.type) } == true

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
    
    val totalFolders = if (uiState is GalleryUiState.Success) uiState.folders.size else 0

    Scaffold(
        topBar = {
            if (isMediaSelectionMode) {
                MediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = if (displayMode == DisplayMode.CALENDAR) groupedAllMedia.values.flatten().size else 0,
                    onClose = { onExitMediaSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onShare = { shareMedia(context, selectedMediaForActions) },
                    onInfoClick = { showPropertiesDialog = true },
                    canUseAsBackground = canUseAsBackground,
                    onUseAsBackground = {
                        selectedMediaForWallpaper?.let { setImageAsWallpaper(context, it.uri) }
                    },
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
                    onCopyTo = { onStartOperation(OperationType.COPY) },
                    onMoveTo = { onStartOperation(OperationType.MOVE) }
                )
            } else {
                SearchTopBar(
                    title = if (displayMode == DisplayMode.GALLERY) stringResource(R.string.app_name) else stringResource(R.string.timeline_title),
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
                    onCreateFolderClick = { onSetCreateFolderDialogOpen(true) },
                    onSettingsClick = { onSetSettingsMode(true) }
                )
            }
        },
        containerColor = AppBackground
    ) { padding ->
        if (showDeleteDialog) {
            val title = if (isMediaSelectionMode) stringResource(R.string.delete_media_title) else stringResource(R.string.delete_folders_title)
            val text = if (isMediaSelectionMode) {
                val selectedMediaCount = selectedMediaItems.size
                pluralStringResource(
                    R.plurals.delete_media_message_count,
                    selectedMediaCount,
                    selectedMediaCount
                )
            } else {
                stringResource(R.string.delete_folders_message)
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

        if (showExcludeDialog) {
            AlertDialog(
                onDismissRequest = { showExcludeDialog = false },
                title = { Text(stringResource(R.string.exclude_folders_title)) },
                text = { Text(stringResource(R.string.exclude_folders_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        onExcludeSelected()
                        showExcludeDialog = false
                    }) {
                        Text(stringResource(R.string.action_exclude))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExcludeDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showSortDialog) {
            SortDialog(
                currentSort = activePreferences.sortType,
                currentOrder = activePreferences.sortOrder,
                onConfirm = { sortType, sortOrder, _ ->
                    onSetSort(sortType, sortOrder)
                    showSortDialog = false
                },
                onDismiss = { showSortDialog = false }
            )
        }

        if (showGroupByDialog) {
            GroupByDialog(
                currentGroupBy = timelinePreferences.groupBy,
                currentOrder = timelinePreferences.groupOrder,
                onConfirm = { groupBy, groupOrder, _ ->
                    onSetGroupByAndOrder(groupBy, groupOrder)
                    showGroupByDialog = false
                },
                onDismiss = { showGroupByDialog = false }
            )
        }

        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = activePreferences.columns,
                onConfirm = { count, _ ->
                    onSetColumnsCount(count)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = activePreferences.viewType,
                onConfirm = { viewType, _ ->
                    onSetViewType(viewType)
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

        if (showFilterDialog) {
            FilterMediaDialog(
                initialSelectedTypes = activePreferences.mediaTypes,
                onConfirm = { selectedTypes, _ ->
                    onSetSelectedMediaTypes(selectedTypes)
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false }
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

        if (isCreateFolderDialogOpen) {
            CreateFolderDialog(
                currentPath = createFolderBrowsingPath,
                folders = createFolderBrowsingFolders,
                error = createFolderError,
                onPathChange = onUpdateCreateFolderBrowsingPath,
                onDismiss = { onSetCreateFolderDialogOpen(false) },
                onCreate = { onCreateFolder(it) }
            )
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppBackground)
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
                        if (folderPreferences.viewType == ViewType.GRID) {
                            FolderGrid(
                                folders = state.folders,
                                columns = folderPreferences.columns,
                                state = gridState,
                                selectedFolders = selectedFolders,
                                onFolderClick = { onSelectFolder(it) },
                                onFolderLongClick = { onEnterSelectionMode(it.path) },
                                onZoomIn = { onDecreaseColumns() },
                                onZoomOut = { onIncreaseColumns() }
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
                            Text(text = stringResource(R.string.error_prefix, state.message), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

