package com.davide.seddio.easygallery.ui

import android.os.Build
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    albumsViewModel: AlbumsViewModel,
    createFolderViewModel: CreateFolderViewModel,
    favouritesAvailable: Boolean,
    favouriteUris: Set<android.net.Uri>,
    isFavouritePending: Boolean,
    onToggleFavourites: (Collection<android.net.Uri>) -> Unit,
    onAddToAlbum: (List<com.davide.seddio.easygallery.data.MediaItem>) -> Unit,
    onStartManualAlbum: () -> Unit = {},
    onSelectFavourites: () -> Unit = {}
) {
    val uiState by viewModel.filteredFolders.collectAsState()
    val allMedia by viewModel.allMedia.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val activeScope = when (displayMode) {
        DisplayMode.FOLDERS -> PreferenceScope.FOLDERS
        DisplayMode.TIMELINE -> PreferenceScope.TIMELINE
        DisplayMode.ALBUMS -> PreferenceScope.ALBUMS
    }
    val folderPreferences by viewModel.preferences(PreferenceScope.FOLDERS).collectAsState()
    val timelinePreferences by viewModel.preferences(PreferenceScope.TIMELINE).collectAsState()
    val albumsPreferences by viewModel.preferences(PreferenceScope.ALBUMS).collectAsState()
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
    val albums by albumsViewModel.albums.collectAsState()

    val isCreateFolderDialogOpen by createFolderViewModel.isDialogOpen.collectAsState()
    val createFolderError by createFolderViewModel.error.collectAsState()
    val createFolderBrowsingPath by createFolderViewModel.browsingPath.collectAsState()
    val createFolderBrowsingFolders by createFolderViewModel.browsingFolders.collectAsState()

    LaunchedEffect(Unit) {
        createFolderViewModel.folderCreated.collect { viewModel.loadFolders() }
    }
    LaunchedEffect(allMedia) {
        albumsViewModel.setAllMedia(allMedia)
    }

    val favouriteMedia = remember(allMedia, favouriteUris) {
        allMedia.filter { it.uri in favouriteUris }
    }

    FolderListContent(
        uiState = uiState,
        folderPreferences = folderPreferences,
        timelinePreferences = timelinePreferences,
        albumsPreferences = albumsPreferences,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        isSelectionMode = isSelectionMode,
        isMediaSelectionMode = isMediaSelectionMode,
        selectedMediaItems = selectedMediaItems,
        selectedFolders = selectedFolders,
        displayMode = displayMode,
        albums = albums,
        favouritesAvailable = favouritesAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        favouriteMedia = favouriteMedia,
        favouriteUris = favouriteUris,
        isFavouritePending = isFavouritePending,
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
        onToggleFavourites = onToggleFavourites,
        onAddToAlbum = onAddToAlbum,
        onSelectAllFolders = { viewModel.selectAll() },
        onExcludeSelected = { viewModel.excludeSelected() },
        onStartOperation = { viewModel.startOperation(it) },
        onSetSearchQuery = { viewModel.setSearchQuery(it, activeScope) },
        onSetSearchActive = { viewModel.setSearchActive(it, activeScope) },
        onSetDisplayMode = { viewModel.setDisplayMode(it) },
        onBackToFolders = { viewModel.setDisplayMode(DisplayMode.FOLDERS) },
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
        onStartManualAlbum = onStartManualAlbum,
        onRenameAlbum = { albumId, name -> albumsViewModel.renameAlbum(albumId, name) },
        onDeleteAlbum = { albumsViewModel.deleteAlbum(it) },
        onSelectAlbum = { albumsViewModel.selectAlbum(it) },
        onSelectFavourites = onSelectFavourites,
        onCreateFolder = { createFolderViewModel.createFolder(it) },
        onUpdateCreateFolderBrowsingPath = { createFolderViewModel.updateBrowsingPath(it) },
        onUpdateBrowsingPath = { viewModel.updateBrowsingPath(it) },
        onPerformOperationWithPath = { viewModel.performOperationWithPath(it) },
        onCancelOperation = { viewModel.cancelOperation() },
        onSelectFolder = { viewModel.selectFolder(it) },
        onEnterSelectionMode = { viewModel.enterSelectionMode(it) },
        onDecreaseColumns = { viewModel.decreaseColumns(activeScope) },
        onIncreaseColumns = { viewModel.increaseColumns(activeScope) },
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
    albumsPreferences: ViewPreferences,
    searchQuery: String,
    isSearchActive: Boolean,
    isSelectionMode: Boolean,
    isMediaSelectionMode: Boolean,
    selectedMediaItems: Set<android.net.Uri>,
    selectedFolders: Set<String>,
    displayMode: DisplayMode,
    albums: List<AlbumListItem>,
    favouritesAvailable: Boolean,
    favouriteMedia: List<com.davide.seddio.easygallery.data.MediaItem>,
    favouriteUris: Set<android.net.Uri>,
    isFavouritePending: Boolean,
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
    onToggleFavourites: (Collection<android.net.Uri>) -> Unit,
    onAddToAlbum: (List<com.davide.seddio.easygallery.data.MediaItem>) -> Unit,
    onSelectAllFolders: () -> Unit,
    onExcludeSelected: () -> Unit,
    onStartOperation: (OperationType) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onSetDisplayMode: (DisplayMode) -> Unit,
    onBackToFolders: () -> Unit,
    onSetSort: (SortType, SortOrder) -> Unit,
    onSetGroupByAndOrder: (GroupByType, SortOrder) -> Unit,
    onSetColumnsCount: (Int) -> Unit,
    onSetViewType: (ViewType) -> Unit,
    onSetSelectedMediaTypes: (Set<MediaType>) -> Unit,
    onSetShowExcludedTemporarily: (Boolean) -> Unit,
    onSetSettingsMode: (Boolean) -> Unit,
    onSetCreateFolderDialogOpen: (Boolean) -> Unit,
    onStartManualAlbum: () -> Unit,
    onRenameAlbum: (Long, String) -> Unit,
    onDeleteAlbum: (Long) -> Unit,
    onSelectAlbum: (Long) -> Unit,
    onSelectFavourites: () -> Unit,
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
    val activePreferences = when (displayMode) {
        DisplayMode.FOLDERS -> folderPreferences
        DisplayMode.TIMELINE -> timelinePreferences
        DisplayMode.ALBUMS -> albumsPreferences
    }
    val context = LocalContext.current
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selectedMediaForActions = remember(selectedMediaItems) { getSelectedMediaData() }
    val selectedMediaForWallpaper = selectedMediaForActions.singleOrNull()
    val canUseAsBackground = selectedMediaForWallpaper?.let { supportsWallpaper(it.type) } == true
    val allSelectedAreFavourite = selectedMediaForActions.isNotEmpty() &&
        selectedMediaForActions.all { it.uri in favouriteUris }

    if (isMediaSelectionMode) {
        BackHandler { onExitMediaSelectionMode() }
    } else if (isSelectionMode) {
        BackHandler { onExitSelectionMode() }
    } else if (displayMode != DisplayMode.FOLDERS) {
        BackHandler { onBackToFolders() }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupByDialog by remember { mutableStateOf(false) }
    var showColumnCountDialog by remember { mutableStateOf(false) }
    var showViewTypeDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showExcludeDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showAlbumTypeDialog by remember { mutableStateOf(false) }
    var albumForActions by remember { mutableStateOf<AlbumListItem?>(null) }
    var albumToRename by remember { mutableStateOf<AlbumListItem?>(null) }
    var albumToDelete by remember { mutableStateOf<AlbumListItem?>(null) }
    var albumNameDraft by remember { mutableStateOf("") }
    val showAddAlbumButton =
        displayMode == DisplayMode.ALBUMS &&
            !isSelectionMode &&
            !isMediaSelectionMode &&
            albumForActions == null
    val addAlbumDescription = stringResource(R.string.menu_new_album)

    BackHandler(enabled = albumForActions != null) {
        albumForActions = null
    }
    
    val totalFolders = if (uiState is GalleryUiState.Success) uiState.folders.size else 0

    Scaffold(
        topBar = {
            if (albumForActions != null && displayMode == DisplayMode.ALBUMS) {
                AlbumSelectionTopBar(
                    albumName = albumForActions!!.name,
                    onClose = { albumForActions = null },
                    onRename = {
                        albumToRename = albumForActions
                        albumNameDraft = albumForActions!!.name
                        albumForActions = null
                    },
                    onDelete = {
                        albumToDelete = albumForActions
                        albumForActions = null
                    }
                )
            } else if (isMediaSelectionMode) {
                MediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = if (displayMode == DisplayMode.TIMELINE) groupedAllMedia.values.flatten().size else 0,
                    onClose = { onExitMediaSelectionMode() },
                    onDelete = { showDeleteDialog = true },
                    onShare = { shareMedia(context, selectedMediaForActions) },
                    onInfoClick = { showPropertiesDialog = true },
                    canUseAsBackground = canUseAsBackground,
                    onUseAsBackground = {
                        selectedMediaForWallpaper?.let { setImageAsWallpaper(context, it.uri) }
                    },
                    favouritesAvailable = favouritesAvailable,
                    allSelectedAreFavourite = allSelectedAreFavourite,
                    isFavouritePending = isFavouritePending,
                    onToggleFavourite = {
                        onToggleFavourites(selectedMediaForActions.map { it.uri })
                    },
                    onAddToAlbum = { onAddToAlbum(selectedMediaForActions) },
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
                    title = when (displayMode) {
                        DisplayMode.FOLDERS -> stringResource(R.string.folders_title)
                        DisplayMode.TIMELINE -> stringResource(R.string.timeline_title)
                        DisplayMode.ALBUMS -> stringResource(R.string.albums_title)
                    },
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    displayMode = displayMode,
                    onSearchQueryChange = { onSetSearchQuery(it) },
                    onSearchActiveChange = { onSetSearchActive(it) },
                    onDisplayModeChange = { onSetDisplayMode(it) },
                    onSortClick = { showSortDialog = true },
                    onColumnCountClick = { showColumnCountDialog = true },
                    onGroupByClick = if (displayMode == DisplayMode.TIMELINE) { { showGroupByDialog = true } } else null,
                    onViewTypeClick = { showViewTypeDialog = true },
                    onFilterMediaClick = if (displayMode == DisplayMode.ALBUMS) null else { { showFilterDialog = true } },
                    onShowExcludedClick = if (displayMode == DisplayMode.ALBUMS) null else { { onSetShowExcludedTemporarily(true) } },
                    onCreateFolderClick = if (displayMode == DisplayMode.ALBUMS) null else { { onSetCreateFolderDialogOpen(true) } },
                    onCreateAlbumClick = if (displayMode == DisplayMode.ALBUMS) { { showAlbumTypeDialog = true } } else null,
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

        if (showAlbumTypeDialog) {
            AlbumTypeDialog(
                onManualSelected = {
                    showAlbumTypeDialog = false
                    onStartManualAlbum()
                },
                onDismiss = { showAlbumTypeDialog = false }
            )
        }

        albumToRename?.let { album ->
            val duplicate = albums.any {
                it.id != album.id && it.name.trim().equals(albumNameDraft.trim(), ignoreCase = true)
            }
            RenameAlbumDialog(
                name = albumNameDraft,
                isNameDuplicate = duplicate,
                onNameChange = { albumNameDraft = it },
                onRename = {
                    onRenameAlbum(album.id, it)
                    albumNameDraft = ""
                    albumToRename = null
                },
                onDismiss = {
                    albumNameDraft = ""
                    albumToRename = null
                }
            )
        }

        albumToDelete?.let { album ->
            DeleteAlbumDialog(
                onDelete = {
                    onDeleteAlbum(album.id)
                    albumToDelete = null
                },
                onDismiss = { albumToDelete = null }
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
            when (displayMode) {
                DisplayMode.TIMELINE -> calendarContent()
                DisplayMode.ALBUMS -> AlbumsViewBody(
                    albums = albums,
                    favouritesAvailable = favouritesAvailable,
                    favouriteMedia = favouriteMedia,
                    preferences = albumsPreferences,
                    searchQuery = searchQuery,
                    selectedAlbumId = albumForActions?.id,
                    gridState = gridState,
                    listState = listState,
                    contentPadding = if (showAddAlbumButton) {
                        PaddingValues(bottom = 88.dp)
                    } else {
                        PaddingValues(0.dp)
                    },
                    onSelectAlbum = onSelectAlbum,
                    onSelectFavourites = onSelectFavourites,
                    onAlbumLongClick = { albumForActions = it },
                    onZoomIn = onDecreaseColumns,
                    onZoomOut = onIncreaseColumns
                )
                DisplayMode.FOLDERS -> {
                when (val state = uiState) {
                    is GalleryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is GalleryUiState.Success -> {
                        if (activePreferences.viewType == ViewType.GRID) {
                            FolderGrid(
                                folders = state.folders,
                                columns = activePreferences.columns,
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

            if (showAddAlbumButton) {
                FloatingActionButton(
                    onClick = { showAlbumTypeDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag("add_album_button")
                        .semantics {
                            contentDescription = addAlbumDescription
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsViewBody(
    albums: List<AlbumListItem>,
    favouritesAvailable: Boolean,
    favouriteMedia: List<com.davide.seddio.easygallery.data.MediaItem>,
    preferences: ViewPreferences,
    searchQuery: String,
    selectedAlbumId: Long?,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
    onSelectAlbum: (Long) -> Unit,
    onSelectFavourites: () -> Unit,
    onAlbumLongClick: (AlbumListItem) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    var cumulativeScale by remember { mutableFloatStateOf(1f) }
    val visibleAlbums = remember(albums, searchQuery, preferences.sortType, preferences.sortOrder) {
        val filtered = albums.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val sorted = when (preferences.sortType) {
            SortType.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortType.SIZE -> filtered.sortedBy { it.resolvedMemberCount }
            SortType.LAST_MODIFIED, SortType.DATE_TAKEN -> filtered.sortedBy { it.createdAt }
            SortType.RANDOM -> filtered.shuffled()
            SortType.PATH -> filtered.sortedBy { it.name.lowercase() }
        }
        if (preferences.sortType != SortType.RANDOM && preferences.sortOrder == SortOrder.DESCENDING) {
            sorted.reversed()
        } else {
            sorted
        }
    }
    val showFavourites = favouritesAvailable && stringResource(R.string.favourites_title)
        .contains(searchQuery, ignoreCase = true)

    if (!showFavourites && visibleAlbums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.albums_empty), color = Color.White)
        }
        return
    }

    if (preferences.viewType == ViewType.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(preferences.columns),
            state = gridState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .testTag("album_grid")
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
            if (showFavourites) {
                item(key = "favourites") {
                    FavouritesAlbumGridItem(
                        itemCount = favouriteMedia.size,
                        coverUri = favouriteMedia.firstOrNull()?.uri,
                        isSelected = false,
                        onClick = onSelectFavourites
                    )
                }
            }
            items(visibleAlbums, key = { it.id }) { album ->
                AlbumGridItem(
                    albumId = album.id,
                    name = album.name,
                    itemCount = album.resolvedMemberCount,
                    hiddenMemberCount = album.hiddenMemberCount,
                    coverUri = album.cover?.uri,
                    isSelected = selectedAlbumId == album.id,
                    onClick = { onSelectAlbum(album.id) },
                    onLongClick = { onAlbumLongClick(album) }
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .testTag("album_list")
        ) {
            if (showFavourites) {
                item(key = "favourites") {
                    FavouritesAlbumListItem(
                        itemCount = favouriteMedia.size,
                        coverUri = favouriteMedia.firstOrNull()?.uri,
                        isSelected = false,
                        onClick = onSelectFavourites
                    )
                }
            }
            items(visibleAlbums, key = { it.id }) { album ->
                AlbumListItem(
                    albumId = album.id,
                    name = album.name,
                    itemCount = album.resolvedMemberCount,
                    hiddenMemberCount = album.hiddenMemberCount,
                    coverUri = album.cover?.uri,
                    isSelected = selectedAlbumId == album.id,
                    onClick = { onSelectAlbum(album.id) },
                    onLongClick = { onAlbumLongClick(album) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSelectionTopBar(
    albumName: String,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val closeDescription = stringResource(R.string.cd_exit_selection)
    val renameDescription = stringResource(R.string.album_rename_title)
    val deleteDescription = stringResource(R.string.album_delete_title)

    TopAppBar(
        title = {
            Text(
                text = albumName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onClose,
                modifier = Modifier.semantics { contentDescription = closeDescription }
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        },
        actions = {
            IconButton(
                onClick = onRename,
                modifier = Modifier.semantics { contentDescription = renameDescription }
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteDescription }
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = com.davide.seddio.easygallery.ui.theme.BrandBlue,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

