package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.GalleryTransformations
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.OperationType
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewPreferences
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.ui.components.ColumnCountDialog
import com.davide.seddio.easygallery.ui.components.DestinationFolderPickerDialog
import com.davide.seddio.easygallery.ui.components.FilterMediaDialog
import com.davide.seddio.easygallery.ui.components.GroupByDialog
import com.davide.seddio.easygallery.ui.components.MediaPropertiesDialog
import com.davide.seddio.easygallery.ui.components.SearchTopBar
import com.davide.seddio.easygallery.ui.components.setImageAsWallpaper
import com.davide.seddio.easygallery.ui.components.shareMedia
import com.davide.seddio.easygallery.ui.components.supportsWallpaper
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue

@Composable
fun AlbumDetailScreen(
    galleryViewModel: GalleryViewModel,
    albumsViewModel: AlbumsViewModel,
    favouritesAvailable: Boolean,
    favouriteUris: Set<Uri>,
    isFavouritePending: Boolean,
    isFavouritesAlbum: Boolean = false,
    favouriteMedia: List<MediaItem> = emptyList(),
    onBackFromFavourites: () -> Unit = {},
    onToggleFavourites: (Collection<Uri>) -> Unit,
    onAddToAlbum: (List<MediaItem>) -> Unit
) {
    val scope = PreferenceScope.ALBUM_DETAIL
    val album by albumsViewModel.selectedAlbum.collectAsState()
    val members by albumsViewModel.selectedAlbumMembers.collectAsState()
    val preferences by galleryViewModel.preferences(scope).collectAsState()
    val searchQuery by galleryViewModel.searchQuery(scope).collectAsState()
    val isSearchActive by galleryViewModel.isSearchActive(scope).collectAsState()
    val isMediaSelectionMode by galleryViewModel.isMediaSelectionMode.collectAsState()
    val selectedMediaItems by galleryViewModel.selectedMediaItems.collectAsState()
    val isDestinationPickerActive by galleryViewModel.isDestinationPickerActive.collectAsState()
    val pendingOperation by galleryViewModel.pendingOperation.collectAsState()
    val browsingPath by galleryViewModel.browsingPath.collectAsState()
    val browsingFolders by galleryViewModel.browsingFolders.collectAsState()

    val resolvedMedia = if (isFavouritesAlbum) favouriteMedia else members.map { it.mediaItem }
    val media = remember(resolvedMedia, preferences, searchQuery) {
        val filtered = GalleryTransformations.filterMedia(
            resolvedMedia,
            searchQuery,
            preferences.mediaTypes
        )
        GalleryTransformations.sortMedia(filtered, preferences.sortType, preferences.sortOrder)
    }
    val fileTypeLabels = mapOf(
        MediaType.IMAGE to stringResource(R.string.filter_images),
        MediaType.VIDEO to stringResource(R.string.filter_videos),
        MediaType.GIF to stringResource(R.string.filter_gifs)
    )
    val todayLabel = stringResource(R.string.date_today)
    val yesterdayLabel = stringResource(R.string.date_yesterday)
    val groupedMedia = remember(media, preferences.groupBy, preferences.groupOrder, fileTypeLabels, todayLabel, yesterdayLabel) {
        GalleryTransformations.groupMedia(
            media,
            preferences.groupBy,
            preferences.groupOrder,
            todayLabel,
            yesterdayLabel,
            fileTypeLabels
        )
    }
    val selectedAlbumMedia = remember(resolvedMedia, selectedMediaItems) {
        resolvedMedia.filter { it.uri in selectedMediaItems }
    }

    AlbumDetailContent(
        albumName = if (isFavouritesAlbum) stringResource(R.string.favourites_title) else album?.name.orEmpty(),
        media = media,
        storedMemberCount = if (isFavouritesAlbum) resolvedMedia.size else album?.storedMemberCount ?: 0,
        resolvedMemberCount = if (isFavouritesAlbum) resolvedMedia.size else album?.resolvedMemberCount ?: 0,
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
        selectedMedia = selectedAlbumMedia,
        favouritesAvailable = favouritesAvailable,
        favouriteUris = favouriteUris,
        isFavouritePending = isFavouritePending,
        isFavouritesAlbum = isFavouritesAlbum,
        onBackToAlbums = {
            galleryViewModel.exitMediaSelectionMode()
            galleryViewModel.setSearchActive(false, scope)
            if (isFavouritesAlbum) {
                onBackFromFavourites()
            } else {
                albumsViewModel.selectAlbum(null)
            }
        },
        onExitMediaSelectionMode = { galleryViewModel.exitMediaSelectionMode() },
        onRemoveSelectedFromAlbum = {
            if (isFavouritesAlbum) {
                onToggleFavourites(selectedAlbumMedia.map { it.uri })
            } else {
                album?.let { albumsViewModel.removeMembersByMedia(it.id, selectedAlbumMedia) }
            }
            galleryViewModel.exitMediaSelectionMode()
        },
        onToggleFavourites = onToggleFavourites,
        onAddToAlbum = onAddToAlbum,
        onDeleteSelectedMedia = { galleryViewModel.deleteSelectedMedia() },
        onStartOperation = { galleryViewModel.startOperation(it) },
        onSelectAllMedia = {
            media.filterNot { it.uri in selectedMediaItems }
                .forEach { galleryViewModel.toggleMediaSelection(it) }
        },
        onSetSearchQuery = { galleryViewModel.setSearchQuery(it, scope) },
        onSetSearchActive = { galleryViewModel.setSearchActive(it, scope) },
        onSetColumnsCount = { galleryViewModel.setColumnsCount(it, scope) },
        onSetMediaTypes = { galleryViewModel.setSelectedMediaTypes(it, scope) },
        onSetSort = { sortType, sortOrder ->
            galleryViewModel.setSortType(sortType, scope)
            galleryViewModel.setSortOrder(sortOrder, scope)
        },
        onSetGroupBy = { groupBy, groupOrder ->
            galleryViewModel.setGroupBy(groupBy, scope)
            galleryViewModel.setGroupOrder(groupOrder, scope)
        },
        onSetViewType = { galleryViewModel.setViewType(it, scope) },
        onToggleInfo = { galleryViewModel.toggleInfo(scope) },
        onUpdateBrowsingPath = { galleryViewModel.updateBrowsingPath(it) },
        onPerformOperationWithPath = { galleryViewModel.performOperationWithPath(it) },
        onCancelOperation = { galleryViewModel.cancelOperation() },
        onSelectMedia = { item ->
            if (isMediaSelectionMode) {
                galleryViewModel.toggleMediaSelection(item)
            } else {
                galleryViewModel.openMedia(item, media)
            }
        },
        onEnterMediaSelectionMode = { galleryViewModel.enterMediaSelectionMode(it) },
        onDecreaseColumns = { galleryViewModel.decreaseColumns(scope) },
        onIncreaseColumns = { galleryViewModel.increaseColumns(scope) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailContent(
    albumName: String,
    media: List<MediaItem>,
    storedMemberCount: Int,
    resolvedMemberCount: Int,
    preferences: ViewPreferences,
    searchQuery: String,
    isSearchActive: Boolean,
    groupedMedia: Map<String, List<MediaItem>>,
    isMediaSelectionMode: Boolean,
    selectedMediaItems: Set<Uri>,
    isDestinationPickerActive: Boolean,
    pendingOperation: OperationType?,
    browsingPath: String,
    browsingFolders: List<com.davide.seddio.easygallery.data.Folder>,
    selectedMedia: List<MediaItem>,
    onBackToAlbums: () -> Unit,
    onExitMediaSelectionMode: () -> Unit,
    onRemoveSelectedFromAlbum: () -> Unit,
    onDeleteSelectedMedia: () -> Unit,
    onStartOperation: (OperationType) -> Unit,
    onSelectAllMedia: () -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onSetColumnsCount: (Int) -> Unit,
    onSetMediaTypes: (Set<MediaType>) -> Unit,
    onSetSort: (SortType, SortOrder) -> Unit,
    onSetGroupBy: (GroupByType, SortOrder) -> Unit,
    onSetViewType: (ViewType) -> Unit,
    onToggleInfo: () -> Unit,
    onUpdateBrowsingPath: (String) -> Unit,
    onPerformOperationWithPath: (String) -> Unit,
    onCancelOperation: () -> Unit,
    onSelectMedia: (MediaItem) -> Unit,
    onEnterMediaSelectionMode: (MediaItem) -> Unit,
    onDecreaseColumns: () -> Unit,
    onIncreaseColumns: () -> Unit,
    favouritesAvailable: Boolean = false,
    favouriteUris: Set<Uri> = emptySet(),
    isFavouritePending: Boolean = false,
    isFavouritesAlbum: Boolean = false,
    onToggleFavourites: (Collection<Uri>) -> Unit = {},
    onAddToAlbum: (List<MediaItem>) -> Unit = {}
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val hasHiddenMembers = resolvedMemberCount < storedMemberCount
    val wallpaperEligibleMedia = selectedMedia.singleOrNull()
        ?.takeIf { supportsWallpaper(it.type) }
    val allSelectedAreFavourite = selectedMedia.isNotEmpty() &&
        selectedMedia.all { it.uri in favouriteUris }

    if (isMediaSelectionMode) {
        BackHandler { onExitMediaSelectionMode() }
    } else {
        BackHandler { onBackToAlbums() }
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
                AlbumMediaSelectionTopBar(
                    selectedCount = selectedMediaItems.size,
                    totalCount = media.size,
                    isFavouritesAlbum = isFavouritesAlbum,
                    onClose = onExitMediaSelectionMode,
                    onRemoveFromAlbum = onRemoveSelectedFromAlbum,
                    onDelete = { showDeleteDialog = true },
                    onShare = { shareMedia(context, selectedMedia) },
                    onInfoClick = { showPropertiesDialog = true },
                    canUseAsBackground = wallpaperEligibleMedia != null,
                    onUseAsBackground = {
                        wallpaperEligibleMedia?.let { setImageAsWallpaper(context, it.uri) }
                    },
                    favouritesAvailable = favouritesAvailable,
                    allSelectedAreFavourite = allSelectedAreFavourite,
                    isFavouritePending = isFavouritePending,
                    onToggleFavourite = {
                        onToggleFavourites(selectedMedia.map { it.uri })
                    },
                    onAddToAlbum = { onAddToAlbum(selectedMedia) },
                    onCopyTo = { onStartOperation(OperationType.COPY) },
                    onMoveTo = { onStartOperation(OperationType.MOVE) },
                    onSelectAll = onSelectAllMedia
                )
            } else {
                val backDescription = stringResource(R.string.cd_back)
                SearchTopBar(
                    title = albumName,
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    onSearchQueryChange = onSetSearchQuery,
                    onSearchActiveChange = onSetSearchActive,
                    onColumnCountClick = { showColumnCountDialog = true },
                    onFilterMediaClick = { showFilterDialog = true },
                    onSortClick = { showSortDialog = true },
                    onGroupByClick = { showGroupByDialog = true },
                    onViewTypeClick = { showViewTypeDialog = true },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackToAlbums,
                            modifier = Modifier.semantics { contentDescription = backDescription }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleInfo) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.cd_toggle_info),
                                tint = Color.White
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
                text = {
                    Text(
                        pluralStringResource(
                            R.plurals.delete_media_message_count,
                            selectedMediaItems.size,
                            selectedMediaItems.size
                        )
                    )
                },
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
                media = selectedMedia,
                onDismiss = { showPropertiesDialog = false }
            )
        }

        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = preferences.columns,
                onConfirm = { count, _ ->
                    onSetColumnsCount(count)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showFilterDialog) {
            FilterMediaDialog(
                initialSelectedTypes = preferences.mediaTypes,
                onConfirm = { types, _ ->
                    onSetMediaTypes(types)
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false }
            )
        }

        if (showSortDialog) {
            SortDialog(
                currentSort = preferences.sortType,
                currentOrder = preferences.sortOrder,
                onConfirm = { sortType, sortOrder, _ ->
                    onSetSort(sortType, sortOrder)
                    showSortDialog = false
                },
                onDismiss = { showSortDialog = false }
            )
        }

        if (showGroupByDialog) {
            GroupByDialog(
                currentGroupBy = preferences.groupBy,
                currentOrder = preferences.groupOrder,
                onConfirm = { groupBy, groupOrder, _ ->
                    onSetGroupBy(groupBy, groupOrder)
                    showGroupByDialog = false
                },
                onDismiss = { showGroupByDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = preferences.viewType,
                onConfirm = { viewType, _ ->
                    onSetViewType(viewType)
                    showViewTypeDialog = false
                },
                onDismiss = { showViewTypeDialog = false }
            )
        }

        if (isDestinationPickerActive) {
            DestinationFolderPickerDialog(
                title = if (pendingOperation == OperationType.MOVE) {
                    stringResource(R.string.destination_move_title)
                } else {
                    stringResource(R.string.destination_copy_title)
                },
                currentPath = browsingPath,
                folders = browsingFolders,
                onFolderSelected = { onUpdateBrowsingPath(it.path) },
                onBreadcrumbClick = onUpdateBrowsingPath,
                onConfirm = { onPerformOperationWithPath(browsingPath) },
                onDismiss = onCancelOperation
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppBackground)
        ) {
            if (hasHiddenMembers) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("album_hidden_items_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.album_hidden_items_notice),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (media.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("album_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.album_empty),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (preferences.groupBy == GroupByType.NONE) {
                if (preferences.viewType == ViewType.GRID) {
                    MediaGrid(
                        media = media,
                        columns = preferences.columns,
                        state = gridState,
                        showInfo = preferences.showInfo,
                        selectedItems = selectedMediaItems,
                        onItemClick = onSelectMedia,
                        onItemLongClick = onEnterMediaSelectionMode,
                        onZoomIn = onDecreaseColumns,
                        onZoomOut = onIncreaseColumns
                    )
                } else {
                    MediaList(
                        media = media,
                        state = listState,
                        showInfo = preferences.showInfo,
                        selectedItems = selectedMediaItems,
                        onItemClick = onSelectMedia,
                        onItemLongClick = onEnterMediaSelectionMode
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
                    onItemClick = onSelectMedia,
                    onItemLongClick = onEnterMediaSelectionMode,
                    onZoomIn = onDecreaseColumns,
                    onZoomOut = onIncreaseColumns
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumMediaSelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    isFavouritesAlbum: Boolean,
    onClose: () -> Unit,
    onRemoveFromAlbum: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onInfoClick: () -> Unit,
    canUseAsBackground: Boolean,
    onUseAsBackground: () -> Unit,
    favouritesAvailable: Boolean,
    allSelectedAreFavourite: Boolean,
    isFavouritePending: Boolean,
    onToggleFavourite: () -> Unit,
    onAddToAlbum: () -> Unit,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onSelectAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val exitDescription = stringResource(R.string.cd_exit_selection)
    val moreDescription = stringResource(R.string.cd_more_options)

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.selection_count, selectedCount, totalCount),
                color = Color.White
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onClose,
                modifier = Modifier.semantics { contentDescription = exitDescription }
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }
        },
        actions = {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.semantics { contentDescription = moreDescription }
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    val removeDescription = stringResource(
                        if (isFavouritesAlbum) R.string.cd_unfavourite else R.string.menu_remove_from_album
                    )
                    DropdownMenuItem(
                        text = { Text(removeDescription) },
                        modifier = Modifier.semantics { contentDescription = removeDescription },
                        leadingIcon = {
                            if (isFavouritesAlbum && isFavouritePending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    if (isFavouritesAlbum) Icons.Default.Favorite else Icons.Default.RemoveCircleOutline,
                                    contentDescription = null
                                )
                            }
                        },
                        enabled = !isFavouritesAlbum || !isFavouritePending,
                        onClick = {
                            showMenu = false
                            onRemoveFromAlbum()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        modifier = Modifier.testTag("delete_button"),
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.properties_title)) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onInfoClick()
                        }
                    )
                    if (canUseAsBackground) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_use_as_wallpaper)) },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onUseAsBackground()
                            }
                        )
                    }
                    if (favouritesAvailable && !isFavouritesAlbum) {
                        val favouriteDescription = stringResource(
                            if (allSelectedAreFavourite) R.string.cd_unfavourite else R.string.menu_favourite
                        )
                        DropdownMenuItem(
                            text = { Text(favouriteDescription) },
                            modifier = Modifier.semantics {
                                contentDescription = favouriteDescription
                            },
                            leadingIcon = {
                                if (isFavouritePending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (allSelectedAreFavourite) {
                                            Icons.Default.Favorite
                                        } else {
                                            Icons.Default.FavoriteBorder
                                        },
                                        contentDescription = null
                                    )
                                }
                            },
                            enabled = !isFavouritePending,
                            onClick = {
                                showMenu = false
                                onToggleFavourite()
                            }
                        )
                    }
                    val addToAlbumDescription = stringResource(R.string.menu_add_to_album)
                    DropdownMenuItem(
                        text = { Text(addToAlbumDescription) },
                        modifier = Modifier.semantics {
                            contentDescription = addToAlbumDescription
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PhotoAlbum, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onAddToAlbum()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_to)) },
                        leadingIcon = { Icon(Icons.Default.FolderCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onCopyTo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_move_to)) },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMoveTo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_select_all)) },
                        leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onSelectAll()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BrandBlue,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}
