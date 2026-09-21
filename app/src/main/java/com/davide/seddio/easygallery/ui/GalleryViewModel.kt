package com.davide.seddio.easygallery.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.LocaleHelper
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class GalleryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MediaRepository = MediaStoreDataSource(application),
    private val permissionHandler: MediaPermissionHandler = DefaultMediaPermissionHandler()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _localeTrigger = MutableStateFlow(System.currentTimeMillis())

    private var todayLabel: String = LocaleHelper.wrap(application).getString(R.string.date_today)
    private var yesterdayLabel: String = LocaleHelper.wrap(application).getString(R.string.date_yesterday)
    private var fileTypeLabels: Map<MediaType, String> = localizedFileTypeLabels(application)

    fun onLocaleChanged() {
        val localizedContext = LocaleHelper.wrap(getApplication<Application>())
        todayLabel = localizedContext.getString(R.string.date_today)
        yesterdayLabel = localizedContext.getString(R.string.date_yesterday)
        fileTypeLabels = localizedFileTypeLabels(getApplication())
        _localeTrigger.value = System.currentTimeMillis()
    }

    private fun localizedFileTypeLabels(application: Application): Map<MediaType, String> {
        val localizedContext = LocaleHelper.wrap(application)
        return mapOf(
            MediaType.IMAGE to localizedContext.getString(R.string.filter_images),
            MediaType.VIDEO to localizedContext.getString(R.string.filter_videos),
            MediaType.GIF to localizedContext.getString(R.string.filter_gifs)
        )
    }

    private val prefs = DisplayPreferencesState(SharedPreferencesDisplayStore(application))
    private val folderStore: FolderPreferencesStore = SharedPreferencesFolderStore(application)
    val displayMode: StateFlow<DisplayMode> = prefs.displayMode

    fun preferences(scope: PreferenceScope): StateFlow<ViewPreferences> = prefs.preferences(scope)
    fun searchQuery(scope: PreferenceScope): StateFlow<String> = prefs.searchQuery(scope)
    fun isSearchActive(scope: PreferenceScope): StateFlow<Boolean> = prefs.isSearchActive(scope)

    private val foldersPrefs = prefs.preferences(PreferenceScope.FOLDERS)
    private val timelinePrefs = prefs.preferences(PreferenceScope.TIMELINE)
    private val folderDetailPrefs = prefs.preferences(PreferenceScope.FOLDER_DETAIL)

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMedia: StateFlow<List<MediaItem>> = _allMedia.asStateFlow()

    private val _excludedFolders = MutableStateFlow(folderStore.loadExcluded()) // Stores folder paths
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _pinnedFolders = MutableStateFlow(folderStore.loadPinned()) // Stores folder paths
    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet()) // Stores folder paths
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedMediaItems = MutableStateFlow<Set<android.net.Uri>>(emptySet())
    val selectedMediaItems: StateFlow<Set<android.net.Uri>> = _selectedMediaItems.asStateFlow()

    private val _isMediaSelectionMode = MutableStateFlow(false)
    val isMediaSelectionMode: StateFlow<Boolean> = _isMediaSelectionMode.asStateFlow()

    private val _isManageExcludedMode = MutableStateFlow(false)
    val isManageExcludedMode: StateFlow<Boolean> = _isManageExcludedMode.asStateFlow()

    private val _isSettingsMode = MutableStateFlow(false)
    val isSettingsMode: StateFlow<Boolean> = _isSettingsMode.asStateFlow()

    private val _showExcludedTemporarily = MutableStateFlow(false)
    val showExcludedTemporarily: StateFlow<Boolean> = _showExcludedTemporarily.asStateFlow()

    private val _isDestinationPickerActive = MutableStateFlow(false)
    val isDestinationPickerActive: StateFlow<Boolean> = _isDestinationPickerActive.asStateFlow()

    private val _pendingOperation = MutableStateFlow<OperationType?>(null)
    val pendingOperation: StateFlow<OperationType?> = _pendingOperation.asStateFlow()

    private val _browsingPath = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    val browsingPath: StateFlow<String> = _browsingPath.asStateFlow()

    private val _pendingWriteRequest = MutableStateFlow<PendingMediaPermissionRequest?>(null)
    val pendingWriteRequest: StateFlow<PendingMediaPermissionRequest?> = _pendingWriteRequest.asStateFlow()

    private val _pendingMoveOperation = MutableStateFlow<MoveOperation?>(null)
    val pendingMoveOperation: StateFlow<MoveOperation?> = _pendingMoveOperation.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _mediaInFolder = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaInFolder: StateFlow<List<MediaItem>> = _mediaInFolder.asStateFlow()

    private val mediaViewer = MediaViewerState()
    val selectedMedia: StateFlow<MediaItem?> = mediaViewer.selectedMedia
    val currentMediaList: StateFlow<List<MediaItem>> = mediaViewer.currentMediaList
    val isImmersiveMode: StateFlow<Boolean> = mediaViewer.isImmersiveMode
    val currentRotation: StateFlow<Float> = mediaViewer.currentRotation

    val browsingFolders: StateFlow<List<Folder>> = combine(_browsingPath, _selectedFolders) { path, selected ->
        repository.getSubdirectories(path).filter { !selected.contains(it.path) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredFolders: StateFlow<GalleryUiState> = combine(
        _allMedia,
        foldersPrefs,
        prefs.searchQuery(PreferenceScope.FOLDERS),
        _pinnedFolders,
        combine(_excludedFolders, _showExcludedTemporarily) { excluded, showExcluded -> excluded to showExcluded }
    ) { allMedia, viewPrefs, query, pinned, exclusion ->
        val (excluded, showExcluded) = exclusion
        if (allMedia.isEmpty() && _uiState.value is GalleryUiState.Loading) {
            GalleryUiState.Loading
        } else {
            val folders = GalleryTransformations.filterAndSortFolders(
                allMedia,
                query,
                pinned,
                viewPrefs.sortType,
                viewPrefs.sortOrder,
                excluded,
                showExcluded,
                viewPrefs.mediaTypes
            )
            GalleryUiState.Success(folders)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState.Loading)

    val filteredMedia: StateFlow<List<MediaItem>> = combine(
        _mediaInFolder, folderDetailPrefs, prefs.searchQuery(PreferenceScope.FOLDER_DETAIL)
    ) { media, viewPrefs, query ->
        val filtered = GalleryTransformations.filterMedia(media, query, viewPrefs.mediaTypes)
        GalleryTransformations.sortMedia(filtered, viewPrefs.sortType, viewPrefs.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredAllMedia: StateFlow<List<MediaItem>> = combine(
        _allMedia, timelinePrefs, prefs.searchQuery(PreferenceScope.TIMELINE), _excludedFolders
    ) { media, viewPrefs, query, excluded ->
        val filtered = GalleryTransformations.filterMedia(media, query, viewPrefs.mediaTypes, excluded)
        GalleryTransformations.sortMedia(filtered, viewPrefs.sortType, viewPrefs.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedAllMedia: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredAllMedia, timelinePrefs, _localeTrigger
    ) { media, viewPrefs, _ ->
        GalleryTransformations.groupMedia(
            media, viewPrefs.groupBy, viewPrefs.groupOrder, todayLabel, yesterdayLabel, fileTypeLabels
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val groupedFolderMedia: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredMedia, folderDetailPrefs, _localeTrigger
    ) { media, viewPrefs, _ ->
        GalleryTransformations.groupMedia(
            media, viewPrefs.groupBy, viewPrefs.groupOrder, todayLabel, yesterdayLabel, fileTypeLabels
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun loadFolders() {
        viewModelScope.launch {
            try {
                if (_uiState.value is GalleryUiState.Error) {
                    _uiState.value = GalleryUiState.Loading
                }
                val folders = repository.getFolders()
                _uiState.value = GalleryUiState.Success(folders)
                _allMedia.value = repository.getAllMedia()

                // Refresh current folder media if one is selected
                _selectedFolder.value?.let { currentFolder ->
                    _mediaInFolder.value = repository.getMediaInFolder(currentFolder.name)
                }
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(e.message ?: LocaleHelper.wrap(getApplication<Application>()).getString(R.string.error_unknown))
            }
        }
    }

    fun increaseColumns(scope: PreferenceScope) {
        prefs.increaseColumns(scope)
    }

    fun decreaseColumns(scope: PreferenceScope) {
        prefs.decreaseColumns(scope)
    }

    fun setColumnsCount(count: Int, scope: PreferenceScope) {
        prefs.setColumnsCount(count, scope)
    }

    fun selectFolder(folder: Folder) {
        _showExcludedTemporarily.value = false
        if (_isSelectionMode.value) {
            toggleSelection(folder.path)
        } else {
            _selectedFolder.value = folder
            viewModelScope.launch {
                _mediaInFolder.value = repository.getMediaInFolder(folder.name)
            }
        }
    }

    fun backToFolders() {
        _selectedFolder.value = null
        _mediaInFolder.value = emptyList()
        setSearchActive(false, PreferenceScope.FOLDER_DETAIL)
    }

    fun toggleInfo(scope: PreferenceScope) {
        prefs.toggleInfo(scope)
    }

    fun selectMedia(item: MediaItem) {
        if (_isMediaSelectionMode.value) {
            toggleMediaSelection(item)
        } else {
            val list = if (_selectedFolder.value != null) {
                filteredMedia.value
            } else {
                filteredAllMedia.value
            }
            mediaViewer.open(item, list)
        }
    }

    fun toggleMediaSelection(item: MediaItem) {
        val current = _selectedMediaItems.value.toMutableSet()
        if (current.contains(item.uri)) {
            current.remove(item.uri)
        } else {
            current.add(item.uri)
        }
        _selectedMediaItems.value = current
        if (current.isEmpty()) {
            _isMediaSelectionMode.value = false
        }
    }

    fun enterMediaSelectionMode(item: MediaItem) {
        exitSelectionMode()
        _isMediaSelectionMode.value = true
        _selectedMediaItems.value = setOf(item.uri)
    }

    fun exitMediaSelectionMode() {
        _isMediaSelectionMode.value = false
        _selectedMediaItems.value = emptySet()
    }

    fun selectAllMedia() {
        val list = if (_selectedFolder.value != null) {
            filteredMedia.value
        } else {
            filteredAllMedia.value
        }
        _selectedMediaItems.value = list.map { it.uri }.toSet()
    }

    fun getSelectedMediaData(): List<MediaItem> {
        val selectedUris = _selectedMediaItems.value
        val allVisible = if (_selectedFolder.value != null) {
            _mediaInFolder.value
        } else {
            _allMedia.value
        }
        return allVisible.filter { selectedUris.contains(it.uri) }
    }

    fun deleteMedia(item: MediaItem) {
        viewModelScope.launch {
            performDeletion(listOf(item.uri)) {
                closeMedia()
            }
        }
    }

    fun deleteSelectedMedia() {
        val selectedUris = _selectedMediaItems.value.toList()
        viewModelScope.launch {
            performDeletion(selectedUris) {
                exitMediaSelectionMode()
            }
        }
    }

    fun deleteSelected() {
        val selectedPaths = _selectedFolders.value
        val urisToDelete = _allMedia.value
            .filter { selectedPaths.contains(it.folderPath) }
            .map { it.uri }
            
        if (urisToDelete.isEmpty()) {
            exitSelectionMode()
            return
        }

        viewModelScope.launch {
            performDeletion(urisToDelete) {
                exitSelectionMode()
            }
        }
    }

    private suspend fun performDeletion(uris: List<android.net.Uri>, onSuccess: () -> Unit) {
        try {
            repository.deleteMediaItems(uris)
            onSuccess()
            loadFolders()
        } catch (e: SecurityException) {
            permissionHandler.createDeleteRequest(getApplication<Application>().contentResolver, uris)?.let {
                _pendingWriteRequest.value = PendingMediaPermissionRequest(it)
            } ?: permissionHandler.getIntentSenderFromException(e)?.let {
                _pendingWriteRequest.value = PendingMediaPermissionRequest(it)
            }
        }
    }

    fun clearPendingWriteRequest() {
        _pendingWriteRequest.value = null
    }

    fun setCurrentMedia(item: MediaItem) {
        mediaViewer.setCurrent(item)
    }

    fun closeMedia() {
        mediaViewer.close()
    }

    fun toggleImmersiveMode() {
        mediaViewer.toggleImmersive()
    }

    fun rotatePhoto() {
        mediaViewer.rotate()
    }

    fun toggleDisplayMode() {
        _showExcludedTemporarily.value = false
        prefs.toggleDisplayMode()
    }

    fun setShowExcludedTemporarily(show: Boolean) {
        _showExcludedTemporarily.value = show
    }

    fun setSettingsMode(active: Boolean) {
        _isSettingsMode.value = active
    }

    fun setManageExcludedMode(active: Boolean) {
        _isManageExcludedMode.value = active
    }

    fun startOperation(type: OperationType) {
        _pendingOperation.value = type
        _isDestinationPickerActive.value = true
    }

    fun cancelOperation() {
        _pendingOperation.value = null
        _isDestinationPickerActive.value = false
        _browsingPath.value = Environment.getExternalStorageDirectory().absolutePath
    }

    fun updateBrowsingPath(path: String) {
        _browsingPath.value = path
    }

    fun navigateToParent() {
        val current = File(_browsingPath.value)
        val parent = current.parentFile
        if (parent != null && parent.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)) {
            _browsingPath.value = parent.absolutePath
        }
    }


    fun performOperationWithPath(path: String) {
        val operation = _pendingOperation.value ?: return
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val relativePath = GalleryTransformations.absoluteToRelativePath(path, rootPath) ?: return
        
        viewModelScope.launch {
            val urisToMove = if (_isMediaSelectionMode.value) {
                _selectedMediaItems.value.toList()
            } else {
                val selectedPaths = _selectedFolders.value
                _allMedia.value
                    .filter { selectedPaths.contains(it.folderPath) }
                    .map { it.uri }
            }

            if (urisToMove.isEmpty()) {
                cancelOperation()
                return@launch
            }

            if (operation == OperationType.MOVE) {
                tryMoveMedia(urisToMove, relativePath)
            } else {
                if (_isMediaSelectionMode.value) {
                    val selectedMedia = getSelectedMediaData()
                    selectedMedia.forEach { item ->
                        repository.copyFile(item.folderPath, item.name, path)
                    }
                    exitMediaSelectionMode()
                } else {
                    val selectedFoldersData = getSelectedFoldersData()
                    selectedFoldersData.forEach { folder ->
                        repository.copyFolderContents(folder.path, path)
                    }
                    exitSelectionMode()
                }
                cancelOperation()
                loadFolders()
            }
        }
    }

    private suspend fun tryMoveMedia(uris: List<android.net.Uri>, targetRelativePath: String) {
        try {
            repository.updateMediaRelativePath(uris, targetRelativePath)
            _pendingMoveOperation.value = null
            exitMediaSelectionMode()
            exitSelectionMode()
            cancelOperation()
            loadFolders()
        } catch (e: SecurityException) {
            _pendingMoveOperation.value = MoveOperation(uris, targetRelativePath)
            permissionHandler.createWriteRequest(getApplication<Application>().contentResolver, uris)?.let {
                _pendingWriteRequest.value = PendingMediaPermissionRequest(it)
            } ?: permissionHandler.getIntentSenderFromException(e)?.let {
                _pendingWriteRequest.value = PendingMediaPermissionRequest(it)
            }
        }
    }

    fun onWriteRequestResult(granted: Boolean) {
        if (granted) {
            val pending = _pendingMoveOperation.value
            if (pending != null) {
                viewModelScope.launch {
                    tryMoveMedia(pending.uris, pending.targetRelativePath)
                }
            } else {
                loadFolders()
            }
        } else {
            _pendingMoveOperation.value = null
        }
    }

    fun unexcludeFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.remove(folderPath)
        _excludedFolders.value = current
        folderStore.saveExcluded(current)
    }

    fun excludeFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.add(folderPath)
        _excludedFolders.value = current
        folderStore.saveExcluded(current)
    }

    fun getNonExcludedFolders(): List<Folder> {
        val currentState = _uiState.value
        val excluded = _excludedFolders.value
        return if (currentState is GalleryUiState.Success) {
            currentState.folders.filter { !excluded.contains(it.path) }
        } else {
            emptyList()
        }
    }

    fun setSortType(sortType: SortType, scope: PreferenceScope) {
        prefs.setSortType(sortType, scope)
    }

    fun setSortOrder(order: SortOrder, scope: PreferenceScope) {
        prefs.setSortOrder(order, scope)
    }

    fun setGroupBy(type: GroupByType, scope: PreferenceScope) {
        prefs.setGroupBy(type, scope)
    }

    fun setGroupOrder(order: SortOrder, scope: PreferenceScope) {
        prefs.setGroupOrder(order, scope)
    }

    fun setViewType(viewType: ViewType, scope: PreferenceScope) {
        prefs.setViewType(viewType, scope)
    }

    fun setSelectedMediaTypes(types: Set<MediaType>, scope: PreferenceScope) {
        prefs.setSelectedMediaTypes(types, scope)
    }

    fun setSearchQuery(query: String, scope: PreferenceScope) {
        prefs.setSearchQuery(query, scope)
    }

    fun setSearchActive(active: Boolean, scope: PreferenceScope) {
        prefs.setSearchActive(active, scope)
    }

    fun toggleSelection(folderPath: String) {
        val current = _selectedFolders.value.toMutableSet()
        if (current.contains(folderPath)) {
            current.remove(folderPath)
        } else {
            current.add(folderPath)
        }
        _selectedFolders.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun enterSelectionMode(folderPath: String) {
        _isSelectionMode.value = true
        _selectedFolders.value = setOf(folderPath)
        setSearchActive(false, PreferenceScope.FOLDERS)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedFolders.value = emptySet()
    }

    fun selectAll() {
        val currentState = filteredFolders.value
        if (currentState is GalleryUiState.Success) {
            _selectedFolders.value = currentState.folders.map { it.path }.toSet()
        }
    }

    fun getSelectedFoldersData(): List<Folder> {
        val selectedPaths = _selectedFolders.value
        val currentState = filteredFolders.value
        return if (currentState is GalleryUiState.Success) {
            currentState.folders.filter { selectedPaths.contains(it.path) }
        } else {
            emptyList()
        }
    }

    fun pinSelected() {
        val selectedPaths = _selectedFolders.value
        val currentPinned = _pinnedFolders.value.toMutableSet()
        val allSelectedPinned = selectedPaths.all { currentPinned.contains(it) }
        if (allSelectedPinned) {
            currentPinned.removeAll(selectedPaths)
        } else {
            currentPinned.addAll(selectedPaths)
        }
        _pinnedFolders.value = currentPinned
        folderStore.savePinned(currentPinned)
        exitSelectionMode()
    }

    fun excludeSelected() {
        val selectedPaths = _selectedFolders.value
        val currentExcluded = _excludedFolders.value.toMutableSet()
        currentExcluded.addAll(selectedPaths)
        _excludedFolders.value = currentExcluded
        folderStore.saveExcluded(currentExcluded)
        exitSelectionMode()
    }

}

data class MoveOperation(
    val uris: List<android.net.Uri>,
    val targetRelativePath: String
)
