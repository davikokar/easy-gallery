package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.data.GalleryTransformations
import com.davide.seddio.easygallery.data.GalleryUiState
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.ViewPreferences
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.data.OperationType
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.PreferenceApplyTarget
import com.davide.seddio.easygallery.data.SlideshowPlaylist
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.davide.seddio.easygallery.LocaleHelper
import com.davide.seddio.easygallery.R
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.ui.components.*
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue
import kotlin.random.Random
import kotlinx.coroutines.launch

/** Pins [target] and tells the user only when it did not work; the launcher confirms a request itself. */
internal suspend fun pinShortcutWithFeedback(context: android.content.Context, target: ShortcutTarget, label: String) {
    val message = when (pinHomeShortcut(context, target, label)) {
        ShortcutPinResult.Requested -> return
        ShortcutPinResult.NotSupported -> R.string.shortcut_not_supported
        ShortcutPinResult.Failed -> R.string.shortcut_add_failed
    }
    val localized = LocaleHelper.wrap(context)
    android.widget.Toast.makeText(localized, localized.getString(message), android.widget.Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: GalleryViewModel,
    slideshowViewModel: SlideshowViewModel,
    favouritesAvailable: Boolean,
    favouriteUris: Set<android.net.Uri>,
    isFavouritePending: Boolean,
    onToggleFavourites: (Collection<android.net.Uri>) -> Unit,
    onAddToAlbum: (List<MediaItem>) -> Unit
) {
    val scope = PreferenceScope.FOLDER_DETAIL
    val media by viewModel.filteredMedia.collectAsState()
    val selectedFolder: com.davide.seddio.easygallery.data.Folder? by viewModel.selectedFolder.collectAsState()
    val preferences by viewModel.preferences(scope).collectAsState()
    val searchQuery by viewModel.searchQuery(scope).collectAsState()
    val isSearchActive by viewModel.isSearchActive(scope).collectAsState()
    val groupedMedia by viewModel.groupedFolderMedia.collectAsState()
    val isMediaSelectionMode by viewModel.isMediaSelectionMode.collectAsState()
    val isThumbnailPickerMode by viewModel.isThumbnailPickerMode.collectAsState()
    val folderThumbnailDraftUri by viewModel.folderThumbnailDraftUri.collectAsState()
    val folderThumbnailPreselectedUri by viewModel.folderThumbnailPreselectedUri.collectAsState()
    val selectedMediaItems by viewModel.selectedMediaItems.collectAsState()
    val isDestinationPickerActive by viewModel.isDestinationPickerActive.collectAsState()
    val pendingOperation by viewModel.pendingOperation.collectAsState()
    val browsingPath by viewModel.browsingPath.collectAsState()
    val browsingFolders by viewModel.browsingFolders.collectAsState()
    // The whole folder, before search and Filter media: the slideshow plays what the folder holds.
    val mediaInFolder by viewModel.mediaInFolder.collectAsState()
    val indexedDateTakenByMediaUri by viewModel.indexedDateTakenByMediaUri.collectAsState()
    // Saveable so rotation keeps it open; cleared on Play so returning from the slideshow does not reopen it.
    var showSlideshowDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    FolderDetailContent(
        media = media,
        selectedFolder = selectedFolder,
        preferences = preferences,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        groupedMedia = groupedMedia,
        indexedDateTakenByMediaUri = indexedDateTakenByMediaUri,
        isMediaSelectionMode = isMediaSelectionMode,
        isThumbnailPickerMode = isThumbnailPickerMode,
        draftThumbnailUri = folderThumbnailDraftUri,
        preselectedThumbnailUri = folderThumbnailPreselectedUri,
        selectedMediaItems = selectedMediaItems,
        favouritesAvailable = favouritesAvailable,
        favouriteUris = favouriteUris,
        isFavouritePending = isFavouritePending,
        isDestinationPickerActive = isDestinationPickerActive,
        pendingOperation = pendingOperation,
        browsingPath = browsingPath,
        browsingFolders = browsingFolders,
        onExitMediaSelectionMode = { viewModel.exitMediaSelectionMode() },
        onDeleteSelectedMedia = { viewModel.deleteSelectedMedia() },
        onStartOperation = { viewModel.startOperation(it) },
        onSelectAllMedia = { viewModel.selectAllMedia() },
        onToggleFavourites = onToggleFavourites,
        onAddToAlbum = onAddToAlbum,
        onSetSearchQuery = { viewModel.setSearchQuery(it, scope) },
        onSetSearchActive = { viewModel.setSearchActive(it, scope) },
        onEnterThumbnailPickerMode = { viewModel.enterThumbnailPickerMode() },
        onExitThumbnailPickerMode = { viewModel.exitThumbnailPickerMode() },
        onCommitThumbnailPickerSelection = { viewModel.commitFolderThumbnailDraft() },
        onCommitColumnsPreference = { columns, applyTarget ->
            viewModel.commitFolderColumnsPreference(columns, applyTarget)
        },
        onCommitMediaTypesPreference = { mediaTypes, applyTarget ->
            viewModel.commitFolderMediaTypesPreference(mediaTypes, applyTarget)
        },
        onCommitSortPreference = { sortType, sortOrder, applyTarget ->
            viewModel.commitFolderSortPreference(sortType, sortOrder, applyTarget)
        },
        onCommitGroupByPreference = { groupBy, groupOrder, applyTarget ->
            viewModel.commitFolderGroupByPreference(groupBy, groupOrder, applyTarget)
        },
        onCommitViewTypePreference = { viewType, applyTarget ->
            viewModel.commitFolderViewTypePreference(viewType, applyTarget)
        },
        onBackToFolders = { viewModel.backToFolders() },
        onToggleInfo = { viewModel.toggleInfo(scope) },
        onUpdateBrowsingPath = { viewModel.updateBrowsingPath(it) },
        onPerformOperationWithPath = { viewModel.performOperationWithPath(it) },
        onCancelOperation = { viewModel.cancelOperation() },
        getSelectedMediaData = { viewModel.getSelectedMediaData() },
        onSelectMedia = { viewModel.selectMedia(it) },
        onEnterMediaSelectionMode = { viewModel.enterMediaSelectionMode(it) },
        onDecreaseColumns = { viewModel.decreaseColumns(scope) },
        onIncreaseColumns = { viewModel.increaseColumns(scope) },
        hasSlideshowMedia = mediaInFolder.isNotEmpty(),
        onAddShortcutClick = {
            selectedFolder?.takeIf { it.path.isNotBlank() }?.let { folder ->
                coroutineScope.launch {
                    pinShortcutWithFeedback(context, ShortcutTarget.FolderTarget(folder.path), folder.name)
                }
            }
        },
        onSlideshowClick = { showSlideshowDialog = true }
    )

    if (showSlideshowDialog) {
        val savedOptions by slideshowViewModel.savedOptions.collectAsState()
        // Under the Random sort the shown order cannot be recomputed, so it is keyed on what is shown.
        val shownWhenRandom = if (preferences.sortType == SortType.RANDOM) media else emptyList()
        val baseOrder = remember(
            mediaInFolder,
            preferences.sortType,
            preferences.sortOrder,
            indexedDateTakenByMediaUri,
            shownWhenRandom
        ) {
            SlideshowPlaylist.baseOrder(
                membership = mediaInFolder,
                sortType = preferences.sortType,
                sortOrder = preferences.sortOrder,
                indexedDateTakenByCanonicalMediaUri = indexedDateTakenByMediaUri,
                shownWhenRandom = shownWhenRandom,
                random = Random.Default
            )
        }
        SlideshowDialogHost(
            savedOptions = savedOptions,
            baseOrder = baseOrder,
            onPlay = { options ->
                showSlideshowDialog = false
                slideshowViewModel.start(baseOrder, options)
            },
            onDismiss = { showSlideshowDialog = false }
        )
    }
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
    indexedDateTakenByMediaUri: Map<String, Long?>,
    isMediaSelectionMode: Boolean,
    isThumbnailPickerMode: Boolean,
    draftThumbnailUri: android.net.Uri?,
    preselectedThumbnailUri: android.net.Uri?,
    selectedMediaItems: Set<android.net.Uri>,
    favouritesAvailable: Boolean,
    favouriteUris: Set<android.net.Uri>,
    isFavouritePending: Boolean,
    isDestinationPickerActive: Boolean,
    pendingOperation: OperationType?,
    browsingPath: String,
    browsingFolders: List<com.davide.seddio.easygallery.data.Folder>,
    onExitMediaSelectionMode: () -> Unit,
    onDeleteSelectedMedia: () -> Unit,
    onStartOperation: (OperationType) -> Unit,
    onSelectAllMedia: () -> Unit,
    onToggleFavourites: (Collection<android.net.Uri>) -> Unit,
    onAddToAlbum: (List<MediaItem>) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onEnterThumbnailPickerMode: () -> Unit,
    onExitThumbnailPickerMode: () -> Unit,
    onCommitThumbnailPickerSelection: () -> Unit,
    onCommitColumnsPreference: (Int, PreferenceApplyTarget) -> Unit,
    onCommitMediaTypesPreference: (Set<com.davide.seddio.easygallery.data.MediaType>, PreferenceApplyTarget) -> Unit,
    onCommitSortPreference: (SortType, SortOrder, PreferenceApplyTarget) -> Unit,
    onCommitGroupByPreference: (GroupByType, SortOrder, PreferenceApplyTarget) -> Unit,
    onCommitViewTypePreference: (ViewType, PreferenceApplyTarget) -> Unit,
    onBackToFolders: () -> Unit,
    onToggleInfo: () -> Unit,
    onUpdateBrowsingPath: (String) -> Unit,
    onPerformOperationWithPath: (String) -> Unit,
    onCancelOperation: () -> Unit,
    getSelectedMediaData: () -> List<MediaItem>,
    onSelectMedia: (MediaItem) -> Unit,
    onEnterMediaSelectionMode: (MediaItem) -> Unit,
    onDecreaseColumns: () -> Unit,
    onIncreaseColumns: () -> Unit,
    hasSlideshowMedia: Boolean,
    onAddShortcutClick: () -> Unit,
    onSlideshowClick: () -> Unit
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = LocalContext.current
    val displayedSelectedItems = if (isThumbnailPickerMode) {
        setOfNotNull(draftThumbnailUri ?: preselectedThumbnailUri)
    } else {
        selectedMediaItems
    }
    val onMediaLongClick: (MediaItem) -> Unit = if (isThumbnailPickerMode) {
        {}
    } else {
        { onEnterMediaSelectionMode(it) }
    }
    val resolvedSelectedMedia = remember(selectedMediaItems) { getSelectedMediaData() }
    val wallpaperEligibleMedia = resolvedSelectedMedia.singleOrNull()
        ?.takeIf { supportsWallpaper(it.type) }
    val allSelectedAreFavourite = resolvedSelectedMedia.isNotEmpty() &&
        resolvedSelectedMedia.all { it.uri in favouriteUris }
    val visibleFavouriteUris = if (favouritesAvailable) favouriteUris else emptySet()

    if (isThumbnailPickerMode) {
        BackHandler {
            if (draftThumbnailUri != null) {
                onCommitThumbnailPickerSelection()
            } else {
                onExitThumbnailPickerMode()
            }
        }
    } else if (isMediaSelectionMode) {
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
            if (isThumbnailPickerMode) {
                ThumbnailPickerTopBar(
                    hasDraft = draftThumbnailUri != null,
                    onCancel = { onExitThumbnailPickerMode() },
                    onConfirm = { onCommitThumbnailPickerSelection() }
                )
            } else if (isMediaSelectionMode) {
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
                    favouritesAvailable = favouritesAvailable,
                    allSelectedAreFavourite = allSelectedAreFavourite,
                    isFavouritePending = isFavouritePending,
                    onToggleFavourite = {
                        onToggleFavourites(resolvedSelectedMedia.map { it.uri })
                    },
                    onAddToAlbum = { onAddToAlbum(resolvedSelectedMedia) },
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
                    onChangeThumbnailClick = { onEnterThumbnailPickerMode() },
                    onAddShortcutClick = onAddShortcutClick,
                    onSlideshowClick = if (hasSlideshowMedia) onSlideshowClick else null,
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
                media = getSelectedMediaData(),
                indexedDateTakenByMediaUri = indexedDateTakenByMediaUri,
                onDismiss = { showPropertiesDialog = false }
            )
        }

        if (showColumnCountDialog) {
            ColumnCountDialog(
                currentCount = preferences.columns,
                settingLabel = R.string.column_count_title,
                onConfirm = { count, applyTarget ->
                    onCommitColumnsPreference(count, applyTarget)
                    showColumnCountDialog = false
                },
                onDismiss = { showColumnCountDialog = false }
            )
        }

        if (showFilterDialog) {
            FilterMediaDialog(
                initialSelectedTypes = preferences.mediaTypes,
                settingLabel = R.string.filter_media_title,
                onConfirm = { selectedTypes, applyTarget ->
                    onCommitMediaTypesPreference(selectedTypes, applyTarget)
                    showFilterDialog = false
                },
                onDismiss = { showFilterDialog = false }
            )
        }

        if (showSortDialog) {
            SortDialog(
                currentSort = preferences.sortType,
                currentOrder = preferences.sortOrder,
                settingLabel = R.string.sort_by_title,
                onConfirm = { sortType, sortOrder, applyTarget ->
                    onCommitSortPreference(sortType, sortOrder, applyTarget)
                    showSortDialog = false
                },
                onDismiss = { showSortDialog = false }
            )
        }

        if (showGroupByDialog) {
            GroupByDialog(
                currentGroupBy = preferences.groupBy,
                currentOrder = preferences.groupOrder,
                settingLabel = R.string.group_by_title,
                onConfirm = { groupBy, groupOrder, applyTarget ->
                    onCommitGroupByPreference(groupBy, groupOrder, applyTarget)
                    showGroupByDialog = false
                },
                onDismiss = { showGroupByDialog = false }
            )
        }

        if (showViewTypeDialog) {
            ViewTypeDialog(
                currentViewType = preferences.viewType,
                settingLabel = R.string.change_view_type_title,
                onConfirm = { viewType, applyTarget ->
                    onCommitViewTypePreference(viewType, applyTarget)
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
                        selectedItems = displayedSelectedItems,
                        favouriteUris = visibleFavouriteUris,
                        onItemClick = { onSelectMedia(it) },
                        onItemLongClick = onMediaLongClick,
                        onZoomIn = { onDecreaseColumns() },
                        onZoomOut = { onIncreaseColumns() }
                    )
                } else {
                    MediaList(
                        media = media,
                        indexedDateTakenByMediaUri = indexedDateTakenByMediaUri,
                        state = listState,
                        showInfo = preferences.showInfo,
                        selectedItems = displayedSelectedItems,
                        favouriteUris = visibleFavouriteUris,
                        onItemClick = { onSelectMedia(it) },
                        onItemLongClick = onMediaLongClick
                    )
                }
            } else {
                GroupedMediaContent(
                    groupedMedia = groupedMedia,
                    indexedDateTakenByMediaUri = indexedDateTakenByMediaUri,
                    viewType = preferences.viewType,
                    columns = preferences.columns,
                    gridState = gridState,
                    listState = listState,
                    showInfo = preferences.showInfo,
                    selectedItems = displayedSelectedItems,
                    favouriteUris = visibleFavouriteUris,
                    onItemClick = { onSelectMedia(it) },
                    onItemLongClick = onMediaLongClick,
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
    indexedDateTakenByMediaUri: Map<String, Long?>,
    viewType: ViewType,
    columns: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showInfo: Boolean,
    selectedItems: Set<android.net.Uri>,
    favouriteUris: Set<android.net.Uri> = emptySet(),
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
            contentPadding = contentPadding,
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
                        isFavourite = favouriteUris.contains(item.uri),
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
            state = listState,
            contentPadding = contentPadding
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
                        dateTakenMillis = GalleryTransformations.resolveDateTaken(item, indexedDateTakenByMediaUri).epochMillis,
                        showInfo = showInfo,
                        isSelected = selectedItems.contains(item.uri),
                        isFavourite = favouriteUris.contains(item.uri),
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showInfo: Boolean,
    selectedItems: Set<android.net.Uri>,
    favouriteUris: Set<android.net.Uri> = emptySet(),
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    var cumulativeScale by remember { mutableFloatStateOf(1f) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
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
                isFavourite = favouriteUris.contains(item.uri),
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }
    }
}

@Composable
fun MediaList(
    media: List<MediaItem>,
    indexedDateTakenByMediaUri: Map<String, Long?>,
    showInfo: Boolean,
    state: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    selectedItems: Set<android.net.Uri>,
    favouriteUris: Set<android.net.Uri> = emptySet(),
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground),
        state = state,
        contentPadding = contentPadding
    ) {
        items(media, key = { it.uri.toString() }) { item ->
            MediaListItem(
                item = item,
                dateTakenMillis = GalleryTransformations.resolveDateTaken(item, indexedDateTakenByMediaUri).epochMillis,
                showInfo = showInfo,
                isSelected = selectedItems.contains(item.uri),
                isFavourite = favouriteUris.contains(item.uri),
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }
    }
}
