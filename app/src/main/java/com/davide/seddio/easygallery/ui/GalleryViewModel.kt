package com.davide.seddio.easygallery.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GalleryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MediaRepository = MediaStoreDataSource(application),
    private val permissionHandler: MediaPermissionHandler = DefaultMediaPermissionHandler()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _displayMode = MutableStateFlow(DisplayMode.GALLERY)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMedia: StateFlow<List<MediaItem>> = _allMedia.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet()) // Stores folder paths
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _selectedMediaTypes = MutableStateFlow(MediaType.entries.toSet())
    val selectedMediaTypes: StateFlow<Set<MediaType>> = _selectedMediaTypes.asStateFlow()

    private val _folderSortType = MutableStateFlow(SortType.NAME)
    val folderSortType: StateFlow<SortType> = _folderSortType.asStateFlow()

    private val _pictureSortType = MutableStateFlow(SortType.DATE_TAKEN)
    val pictureSortType: StateFlow<SortType> = _pictureSortType.asStateFlow()

    private val _folderSortOrder = MutableStateFlow(SortOrder.ASCENDING)
    val folderSortOrder: StateFlow<SortOrder> = _folderSortOrder.asStateFlow()

    private val _pictureSortOrder = MutableStateFlow(SortOrder.DESCENDING)
    val pictureSortOrder: StateFlow<SortOrder> = _pictureSortOrder.asStateFlow()

    private val _folderViewType = MutableStateFlow(ViewType.GRID)
    val folderViewType: StateFlow<ViewType> = _folderViewType.asStateFlow()

    private val _pictureViewType = MutableStateFlow(ViewType.GRID)
    val pictureViewType: StateFlow<ViewType> = _pictureViewType.asStateFlow()

    private val _pictureGroupBy = MutableStateFlow(GroupByType.DATE_TAKEN_DAILY)
    val pictureGroupBy: StateFlow<GroupByType> = _pictureGroupBy.asStateFlow()

    private val _pictureGroupOrder = MutableStateFlow(SortOrder.DESCENDING)
    val pictureGroupOrder: StateFlow<SortOrder> = _pictureGroupOrder.asStateFlow()

    private val _pinnedFolders = MutableStateFlow<Set<String>>(emptySet()) // Stores folder paths
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

    private val _folderColumns = MutableStateFlow(2)
    val folderColumns: StateFlow<Int> = _folderColumns.asStateFlow()

    private val _pictureColumns = MutableStateFlow(3)
    val pictureColumns: StateFlow<Int> = _pictureColumns.asStateFlow()

    private val _showInfo = MutableStateFlow(false)
    val showInfo: StateFlow<Boolean> = _showInfo.asStateFlow()

    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia.asStateFlow()

    private val _isImmersiveMode = MutableStateFlow(false)
    val isImmersiveMode: StateFlow<Boolean> = _isImmersiveMode.asStateFlow()

    private val _currentRotation = MutableStateFlow(0f)
    val currentRotation: StateFlow<Float> = _currentRotation.asStateFlow()

    private val _currentMediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    val currentMediaList: StateFlow<List<MediaItem>> = _currentMediaList.asStateFlow()

    val browsingFolders: StateFlow<List<Folder>> = combine(_browsingPath, _selectedFolders) { path, selected ->
        repository.getSubdirectories(path).filter { !selected.contains(it.path) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredFolders: StateFlow<GalleryUiState> = combine(
        _allMedia, _searchQuery, _pinnedFolders, _folderSortType, _folderSortOrder, _excludedFolders, _showExcludedTemporarily, _selectedMediaTypes
    ) { args ->
        val allMedia = args[0] as List<MediaItem>
        val query = args[1] as String
        val pinned = args[2] as Set<String>
        val sort = args[3] as SortType
        val order = args[4] as SortOrder
        val excluded = args[5] as Set<String>
        val showExcluded = args[6] as Boolean
        val types = args[7] as Set<MediaType>

        if (allMedia.isEmpty() && _uiState.value is GalleryUiState.Loading) {
            GalleryUiState.Loading
        } else {
            val folders = GalleryTransformations.filterAndSortFolders(
                allMedia, query, pinned, sort, order, excluded, showExcluded, types
            )
            GalleryUiState.Success(folders)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState.Loading)

    val filteredMedia: StateFlow<List<MediaItem>> = combine(
        _mediaInFolder, _searchQuery, _selectedMediaTypes, _pictureSortType, _pictureSortOrder
    ) { args ->
        val media = args[0] as List<MediaItem>
        val query = args[1] as String
        val types = args[2] as Set<MediaType>
        val sort = args[3] as SortType
        val order = args[4] as SortOrder

        val filtered = GalleryTransformations.filterMedia(media, query, types)
        GalleryTransformations.sortMedia(filtered, sort, order)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredAllMedia: StateFlow<List<MediaItem>> = combine(
        _allMedia, _searchQuery, _excludedFolders, _selectedMediaTypes, _pictureSortType, _pictureSortOrder
    ) { args ->
        val media = args[0] as List<MediaItem>
        val query = args[1] as String
        val excluded = args[2] as Set<String>
        val types = args[3] as Set<MediaType>
        val sort = args[4] as SortType
        val order = args[5] as SortOrder

        val filtered = GalleryTransformations.filterMedia(media, query, types, excluded)
        GalleryTransformations.sortMedia(filtered, sort, order)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedAllMedia: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredAllMedia, _pictureGroupBy, _pictureGroupOrder
    ) { media, groupBy, order ->
        GalleryTransformations.groupMedia(media, groupBy, order)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val groupedFolderMedia: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredMedia, _pictureGroupBy, _pictureGroupOrder
    ) { media, groupBy, order ->
        GalleryTransformations.groupMedia(media, groupBy, order)
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
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun increaseColumns(forPictures: Boolean) {
        if (forPictures) {
            if (_pictureColumns.value < 20) _pictureColumns.value += 1
        } else {
            if (_folderColumns.value < 20) _folderColumns.value += 1
        }
    }

    fun decreaseColumns(forPictures: Boolean) {
        if (forPictures) {
            if (_pictureColumns.value > 1) _pictureColumns.value -= 1
        } else {
            if (_folderColumns.value > 1) _folderColumns.value -= 1
        }
    }

    fun setColumnsCount(count: Int, forPictures: Boolean) {
        val safeCount = count.coerceIn(1, 20)
        if (forPictures) {
            _pictureColumns.value = safeCount
        } else {
            _folderColumns.value = safeCount
        }
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
        setSearchActive(false)
    }

    fun toggleInfo() {
        _showInfo.value = !_showInfo.value
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
            _currentMediaList.value = list
            _selectedMedia.value = item
            _isImmersiveMode.value = false
            _currentRotation.value = 0f
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

    fun rotateSelectedMedia(degrees: Int) {
        val selectedMedia = getSelectedMediaData().filter { it.type == MediaType.IMAGE || it.type == MediaType.GIF }
        if (selectedMedia.isEmpty()) return

        viewModelScope.launch {
            try {
                selectedMedia.forEach { item ->
                    repository.rotateImage(item.uri, degrees)
                }
                exitMediaSelectionMode()
                loadFolders()
            } catch (e: SecurityException) {
                val uris = selectedMedia.map { it.uri }
                permissionHandler.createWriteRequest(getApplication<Application>().contentResolver, uris)?.let {
                    _pendingWriteRequest.value = PendingMediaPermissionRequest(it)
                } ?: permissionHandler.getIntentSenderFromException(e)?.let {
                    _pendingWriteRequest.value = PendingMediaPermissionRequest(it)
                }
            }
        }
    }

    fun clearPendingWriteRequest() {
        _pendingWriteRequest.value = null
    }

    fun setCurrentMedia(item: MediaItem) {
        _selectedMedia.value = item
        _currentRotation.value = 0f
    }

    fun closeMedia() {
        _selectedMedia.value = null
    }

    fun toggleImmersiveMode() {
        _isImmersiveMode.value = !_isImmersiveMode.value
    }

    fun rotatePhoto() {
        _currentRotation.value = (_currentRotation.value + 90f) % 360f
    }

    fun toggleDisplayMode() {
        _showExcludedTemporarily.value = false
        _displayMode.value = if (_displayMode.value == DisplayMode.GALLERY) DisplayMode.CALENDAR else DisplayMode.GALLERY
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

    fun performOperation(destination: Folder) {
        performOperationWithPath(destination.path)
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

    fun unexcludeFolder(folderName: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.remove(folderName)
        _excludedFolders.value = current
    }

    fun excludeFolder(folderName: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.add(folderName)
        _excludedFolders.value = current
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

    fun setSortType(sortType: SortType, forPictures: Boolean) {
        if (forPictures) {
            _pictureSortType.value = sortType
        } else {
            _folderSortType.value = sortType
        }
    }

    fun setSortOrder(order: SortOrder, forPictures: Boolean) {
        if (forPictures) {
            _pictureSortOrder.value = order
        } else {
            _folderSortOrder.value = order
        }
    }

    fun setGroupBy(type: GroupByType) {
        _pictureGroupBy.value = type
    }

    fun setGroupOrder(order: SortOrder) {
        _pictureGroupOrder.value = order
    }

    fun setViewType(viewType: ViewType, forPictures: Boolean) {
        if (forPictures) {
            _pictureViewType.value = viewType
        } else {
            _folderViewType.value = viewType
        }
    }

    fun toggleMediaType(type: MediaType) {
        val current = _selectedMediaTypes.value.toMutableSet()
        if (current.contains(type)) {
            current.remove(type)
        } else {
            current.add(type)
        }
        _selectedMediaTypes.value = current
    }

    fun setSelectedMediaTypes(types: Set<MediaType>) {
        _selectedMediaTypes.value = types
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
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
        setSearchActive(false)
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
        exitSelectionMode()
    }

    fun excludeSelected() {
        val selectedPaths = _selectedFolders.value
        val currentExcluded = _excludedFolders.value.toMutableSet()
        currentExcluded.addAll(selectedPaths)
        _excludedFolders.value = currentExcluded
        exitSelectionMode()
    }

}

data class MoveOperation(
    val uris: List<android.net.Uri>,
    val targetRelativePath: String
)
