package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.GalleryTransformations
import com.davide.seddio.easygallery.data.GalleryUiState
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.ViewPreferences
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.data.resolveFolderDetailPreferences
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue

/**
 * The manual branch of album creation: name the album and pick its members before it exists.
 * Nothing is written until Create, so backing out leaves no empty album behind.
 */
@Composable
fun NewAlbumScreen(
    galleryViewModel: GalleryViewModel,
    albumsViewModel: AlbumsViewModel,
    initialName: String,
    onFinished: () -> Unit
) {
    val allMedia by galleryViewModel.allMedia.collectAsState()
    val foldersState by galleryViewModel.newAlbumFolders.collectAsState()
    val foldersPreferences by galleryViewModel.preferences(PreferenceScope.FOLDERS).collectAsState()
    val folderDetailPreferenceLayers by galleryViewModel.folderDetailPreferenceLayers.collectAsState()
    val albums by albumsViewModel.albums.collectAsState()

    var name by rememberSaveable { mutableStateOf(initialName) }
    var openFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selection = rememberSaveable(saver = selectedUriSaver) { mutableStateOf(emptySet<Uri>()) }
    var selectedUris by selection

    val isNameDuplicate = remember(albums, name) {
        albums.any { it.name.trim().equals(name.trim(), ignoreCase = true) }
    }

    val selectedFolderPreferences = remember(openFolderPath, folderDetailPreferenceLayers) {
        resolveFolderDetailPreferences(openFolderPath, folderDetailPreferenceLayers)
    }
    val selectedFolderMedia = remember(allMedia, openFolderPath) {
        val folderPath = openFolderPath ?: return@remember emptyList()
        allMedia.filter { it.folderPath == folderPath }
    }
    val sortedFolderMedia = remember(
        selectedFolderMedia,
        selectedFolderPreferences.sortType,
        selectedFolderPreferences.sortOrder
    ) {
        GalleryTransformations.sortMedia(
            selectedFolderMedia,
            selectedFolderPreferences.sortType,
            selectedFolderPreferences.sortOrder
        )
    }

    val todayLabel = stringResource(R.string.date_today)
    val yesterdayLabel = stringResource(R.string.date_yesterday)
    val fileTypeLabels = mapOf(
        MediaType.IMAGE to stringResource(R.string.filter_images),
        MediaType.VIDEO to stringResource(R.string.filter_videos),
        MediaType.GIF to stringResource(R.string.filter_gifs)
    )
    val groupedFolderMedia = remember(
        sortedFolderMedia,
        selectedFolderPreferences.groupBy,
        selectedFolderPreferences.groupOrder,
        todayLabel,
        yesterdayLabel,
        fileTypeLabels
    ) {
        GalleryTransformations.groupMedia(
            items = sortedFolderMedia,
            type = selectedFolderPreferences.groupBy,
            order = selectedFolderPreferences.groupOrder,
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
            fileTypeLabels = fileTypeLabels
        )
    }

    NewAlbumContent(
        name = name,
        isNameDuplicate = isNameDuplicate,
        foldersState = foldersState,
        foldersPreferences = foldersPreferences,
        openFolderPath = openFolderPath,
        selectedFolderPreferences = selectedFolderPreferences,
        groupedFolderMedia = groupedFolderMedia,
        selectedFolderMedia = sortedFolderMedia,
        selectedUris = selectedUris,
        onNameChange = { name = it },
        onOpenFolder = { folder -> openFolderPath = folder.path },
        onBackToFolders = { openFolderPath = null },
        onToggleMedia = { item ->
            selectedUris = if (item.uri in selectedUris) {
                selectedUris - item.uri
            } else {
                selectedUris + item.uri
            }
        },
        onCreate = {
            if (albumsViewModel.createAlbum(name, allMedia.filter { it.uri in selectedUris })) {
                onFinished()
            }
        },
        onCancel = onFinished
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAlbumContent(
    name: String,
    isNameDuplicate: Boolean,
    foldersState: GalleryUiState,
    foldersPreferences: ViewPreferences,
    openFolderPath: String?,
    selectedFolderPreferences: ViewPreferences,
    groupedFolderMedia: Map<String, List<MediaItem>>,
    selectedFolderMedia: List<MediaItem>,
    selectedUris: Set<Uri>,
    onNameChange: (String) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onBackToFolders: () -> Unit,
    onToggleMedia: (MediaItem) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit
) {
    val isFolderLevel = openFolderPath == null
    BackHandler {
        if (isFolderLevel) {
            onCancel()
        } else {
            onBackToFolders()
        }
    }

    val nameError = when {
        name.isBlank() -> stringResource(R.string.album_name_blank)
        isNameDuplicate -> stringResource(R.string.album_name_duplicate)
        else -> null
    }
    val createLabel = stringResource(R.string.action_create)
    val closeLabel = stringResource(R.string.action_close)
    val backLabel = stringResource(R.string.cd_back)
    val canCreate = nameError == null
    val foldersGridState = rememberLazyGridState()
    val foldersListState = rememberLazyListState()
    val floatingContentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
    val floatingListContentPadding = androidx.compose.foundation.layout.PaddingValues(
        top = 8.dp,
        bottom = 96.dp
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = name,
                        onValueChange = onNameChange,
                        singleLine = true,
                        isError = nameError != null,
                        label = { Text(text = stringResource(R.string.album_name_hint), color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_album_name_field"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.White,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isFolderLevel) {
                                onCancel()
                            } else {
                                onBackToFolders()
                            }
                        },
                        modifier = Modifier.testTag(
                            if (isFolderLevel) "new_album_close_button" else "new_album_back_button"
                        )
                    ) {
                        Icon(
                            imageVector = if (isFolderLevel) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (isFolderLevel) closeLabel else backLabel,
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBlue,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier
                        .testTag("new_album_create_button")
                        .semantics { contentDescription = createLabel }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("new_album_create_button")
                        .semantics {
                            contentDescription = createLabel
                            disabled()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        containerColor = AppBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppBackground)
        ) {
            Text(
                text = nameError ?: if (selectedUris.isEmpty()) {
                    stringResource(R.string.new_album_select_media)
                } else {
                    pluralStringResource(
                        R.plurals.album_item_count,
                        selectedUris.size,
                        selectedUris.size
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (nameError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.White
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("new_album_subtitle")
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isFolderLevel) {
                    when (foldersState) {
                        is GalleryUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("new_album_folder_level"),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is GalleryUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("new_album_folder_level"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.error_prefix,
                                        foldersState.message
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        is GalleryUiState.Success -> {
                            if (foldersPreferences.viewType == ViewType.GRID) {
                                Box(modifier = Modifier.fillMaxSize().testTag("new_album_folder_grid")) {
                                    FolderGrid(
                                        folders = foldersState.folders,
                                        columns = foldersPreferences.columns,
                                        state = foldersGridState,
                                        contentPadding = floatingContentPadding,
                                        selectedFolders = emptySet(),
                                        onFolderClick = onOpenFolder,
                                        onFolderLongClick = onOpenFolder,
                                        onZoomIn = {},
                                        onZoomOut = {}
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize().testTag("new_album_folder_list")) {
                                    FolderList(
                                        folders = foldersState.folders,
                                        state = foldersListState,
                                        contentPadding = floatingListContentPadding,
                                        selectedFolders = emptySet(),
                                        onFolderClick = onOpenFolder,
                                        onFolderLongClick = onOpenFolder
                                    )
                                }
                            }
                        }
                    }
                } else {
                    key(openFolderPath) {
                        val mediaGridState = rememberLazyGridState()
                        val mediaListState = rememberLazyListState()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("new_album_media_level")
                        ) {
                            if (selectedFolderPreferences.groupBy == GroupByType.NONE) {
                                if (selectedFolderPreferences.viewType == ViewType.GRID) {
                                    Box(modifier = Modifier.fillMaxSize().testTag("new_album_media_grid")) {
                                        MediaGrid(
                                            media = selectedFolderMedia,
                                            columns = selectedFolderPreferences.columns,
                                            state = mediaGridState,
                                            contentPadding = floatingContentPadding,
                                            showInfo = selectedFolderPreferences.showInfo,
                                            selectedItems = selectedUris,
                                            onItemClick = onToggleMedia,
                                            onItemLongClick = onToggleMedia,
                                            onZoomIn = {},
                                            onZoomOut = {}
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize().testTag("new_album_media_list")) {
                                        MediaList(
                                            media = selectedFolderMedia,
                                            showInfo = selectedFolderPreferences.showInfo,
                                            state = mediaListState,
                                            contentPadding = floatingListContentPadding,
                                            selectedItems = selectedUris,
                                            onItemClick = onToggleMedia,
                                            onItemLongClick = onToggleMedia
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize().testTag("new_album_media_grouped")) {
                                    GroupedMediaContent(
                                        groupedMedia = groupedFolderMedia,
                                        viewType = selectedFolderPreferences.viewType,
                                        columns = selectedFolderPreferences.columns,
                                        gridState = mediaGridState,
                                        listState = mediaListState,
                                        contentPadding = floatingContentPadding,
                                        showInfo = selectedFolderPreferences.showInfo,
                                        selectedItems = selectedUris,
                                        onItemClick = onToggleMedia,
                                        onItemLongClick = onToggleMedia,
                                        onZoomIn = {},
                                        onZoomOut = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val selectedUriSaver = listSaver<MutableState<Set<Uri>>, String>(
    save = { state -> state.value.map { it.toString() } },
    restore = { saved -> mutableStateOf(saved.map { Uri.parse(it) }.toSet()) }
)
