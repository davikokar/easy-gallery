package com.davide.seddio.easygallery.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
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

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val dataSource = MediaStoreDataSource(application)

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

    private val _pendingWriteRequest = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingWriteRequest: StateFlow<android.content.IntentSender?> = _pendingWriteRequest.asStateFlow()

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
        dataSource.getSubdirectories(path).filter { !selected.contains(it.path) }
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
            val foldersMap = mutableMapOf<String, Folder>()
            allMedia.forEach { item ->
                val isExcluded = excluded.contains(item.folderPath)
                if ((showExcluded || !isExcluded) && types.contains(item.type)) {
                    val existing = foldersMap[item.folderPath]
                    if (existing == null) {
                        foldersMap[item.folderPath] = Folder(
                            name = item.bucketName,
                            imageCount = 1,
                            thumbnailUri = item.uri,
                            isPinned = pinned.contains(item.folderPath),
                            path = item.folderPath,
                            size = item.size,
                            dateModified = item.dateModified,
                            dateTaken = item.dateAdded
                        )
                    } else {
                        foldersMap[item.folderPath] = existing.copy(
                            imageCount = existing.imageCount + 1,
                            size = existing.size + item.size,
                            dateModified = maxOf(existing.dateModified, item.dateModified),
                            dateTaken = maxOf(existing.dateTaken, item.dateAdded)
                        )
                    }
                }
            }

            val foldersList = foldersMap.values.toList()

            val sorted = when (sort) {
                SortType.NAME -> foldersList.sortedBy { it.name }
                SortType.PATH -> foldersList.sortedBy { it.path }
                SortType.SIZE -> foldersList.sortedByDescending { it.size }
                SortType.LAST_MODIFIED -> foldersList.sortedByDescending { it.dateModified }
                SortType.DATE_TAKEN -> foldersList.sortedByDescending { it.dateTaken }
                SortType.RANDOM -> foldersList.shuffled()
                else -> foldersList
            }

            val ordered = if (sort != SortType.RANDOM && order == SortOrder.DESCENDING) {
                sorted.reversed()
            } else {
                sorted
            }

            val finalSorted = ordered.sortedByDescending { it.isPinned }

            val filteredList = if (query.isNotEmpty()) {
                finalSorted.filter { it.name.contains(query, ignoreCase = true) }
            } else {
                finalSorted
            }
            
            GalleryUiState.Success(filteredList)
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

        val typeFiltered = media.filter { types.contains(it.type) }
        val searchFiltered = if (query.isNotEmpty()) {
            typeFiltered.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            typeFiltered
        }
        sortMedia(searchFiltered, sort, order)
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

        val nonExcluded = media.filter { !excluded.contains(it.folderPath) }
        val typeFiltered = nonExcluded.filter { types.contains(it.type) }
        val searchFiltered = if (query.isNotEmpty()) {
            typeFiltered.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            typeFiltered
        }
        sortMedia(searchFiltered, sort, order)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedAllMedia: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredAllMedia, _pictureGroupBy, _pictureGroupOrder
    ) { media, groupBy, order ->
        groupMedia(media, groupBy, order)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val groupedFolderMedia: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredMedia, _pictureGroupBy, _pictureGroupOrder
    ) { media, groupBy, order ->
        groupMedia(media, groupBy, order)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun loadFolders() {
        viewModelScope.launch {
            try {
                if (_uiState.value is GalleryUiState.Error) {
                    _uiState.value = GalleryUiState.Loading
                }
                val folders = dataSource.getFolders()
                _uiState.value = GalleryUiState.Success(folders)
                _allMedia.value = dataSource.getAllMedia()
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
                _mediaInFolder.value = dataSource.getMediaInFolder(folder.name)
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
            dataSource.deleteMediaItems(uris)
            onSuccess()
            loadFolders()
        } catch (e: SecurityException) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intentSender = android.provider.MediaStore.createDeleteRequest(
                    getApplication<Application>().contentResolver,
                    uris
                ).intentSender
                _pendingWriteRequest.value = intentSender
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                _pendingWriteRequest.value = e.userAction.actionIntent.intentSender
            }
        }
    }

    fun rotateSelectedMedia(degrees: Int) {
        val selectedMedia = getSelectedMediaData().filter { it.type == MediaType.IMAGE || it.type == MediaType.GIF }
        if (selectedMedia.isEmpty()) return

        viewModelScope.launch {
            try {
                selectedMedia.forEach { item ->
                    dataSource.rotateImage(item.uri, degrees)
                }
                exitMediaSelectionMode()
                loadFolders()
            } catch (e: SecurityException) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val uris = selectedMedia.map { it.uri }
                    val intentSender = android.provider.MediaStore.createWriteRequest(
                        getApplication<Application>().contentResolver,
                        uris
                    ).intentSender
                    _pendingWriteRequest.value = intentSender
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    _pendingWriteRequest.value = e.userAction.actionIntent.intentSender
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
        val relativePath = absoluteToRelativePath(path) ?: return
        
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
                        dataSource.copyFile(item.folderPath, item.name, path)
                    }
                    exitMediaSelectionMode()
                } else {
                    val selectedFoldersData = getSelectedFoldersData()
                    selectedFoldersData.forEach { folder ->
                        dataSource.copyFolderContents(folder.path, path)
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
            dataSource.updateMediaRelativePath(uris, targetRelativePath)
            exitMediaSelectionMode()
            exitSelectionMode()
            cancelOperation()
            loadFolders()
        } catch (e: SecurityException) {
            _pendingMoveOperation.value = MoveOperation(uris, targetRelativePath)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intentSender = android.provider.MediaStore.createWriteRequest(
                    getApplication<Application>().contentResolver,
                    uris
                ).intentSender
                _pendingWriteRequest.value = intentSender
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                _pendingWriteRequest.value = e.userAction.actionIntent.intentSender
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

    private fun absoluteToRelativePath(absolutePath: String): String? {
        val root = Environment.getExternalStorageDirectory().absolutePath
        if (!absolutePath.startsWith(root)) return null
        
        var relative = absolutePath.removePrefix(root).trimStart('/')
        if (relative.isNotEmpty() && !relative.endsWith('/')) {
            relative += '/'
        }
        return relative
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

    private fun sortMedia(media: List<MediaItem>, sort: SortType, order: SortOrder): List<MediaItem> {
        val baseSorted = when (sort) {
            SortType.NAME -> media.sortedBy { it.name }
            SortType.LAST_MODIFIED -> media.sortedBy { it.dateModified }
            SortType.DATE_TAKEN -> media.sortedBy { it.dateAdded }
            SortType.RANDOM -> media.shuffled()
            else -> media.sortedBy { it.dateAdded }
        }

        return if (sort != SortType.RANDOM && order == SortOrder.DESCENDING) {
            baseSorted.reversed()
        } else {
            baseSorted
        }
    }

    private fun groupMedia(items: List<MediaItem>, type: GroupByType, order: SortOrder): Map<String, List<MediaItem>> {
        if (type == GroupByType.NONE) return mapOf("" to items)

        val sortedItems = when (type) {
            GroupByType.DATE_TAKEN_DAILY, GroupByType.DATE_TAKEN_MONTHLY -> {
                if (order == SortOrder.DESCENDING) items.sortedByDescending { it.dateAdded }
                else items.sortedBy { it.dateAdded }
            }
            GroupByType.LAST_MODIFIED_DAILY, GroupByType.LAST_MODIFIED_MONTHLY -> {
                if (order == SortOrder.DESCENDING) items.sortedByDescending { it.dateModified }
                else items.sortedBy { it.dateModified }
            }
            GroupByType.FILE_TYPE -> {
                if (order == SortOrder.DESCENDING) items.sortedByDescending { it.type.name }
                else items.sortedBy { it.type.name }
            }
            GroupByType.NONE -> items
        }

        return when (type) {
            GroupByType.DATE_TAKEN_DAILY, GroupByType.LAST_MODIFIED_DAILY -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = sdf.format(Date())
                val yesterday = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
                
                sortedItems.groupBy { item ->
                    val date = if (type == GroupByType.DATE_TAKEN_DAILY) item.dateAdded else item.dateModified
                    val dateStr = sdf.format(Date(date * 1000))
                    when (dateStr) {
                        today -> "Today"
                        yesterday -> "Yesterday"
                        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(date * 1000))
                    }
                }
            }
            GroupByType.DATE_TAKEN_MONTHLY, GroupByType.LAST_MODIFIED_MONTHLY -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sortedItems.groupBy { item ->
                    val date = if (type == GroupByType.DATE_TAKEN_MONTHLY) item.dateAdded else item.dateModified
                    sdf.format(Date(date * 1000))
                }
            }
            GroupByType.FILE_TYPE -> {
                sortedItems.groupBy {
                    it.type.name.lowercase().replaceFirstChar { char -> char.uppercase() } + "s"
                }
            }
            GroupByType.NONE -> mapOf("" to sortedItems)
        }
    }
}

enum class DisplayMode {
    GALLERY, CALENDAR
}

enum class OperationType {
    COPY, MOVE
}

data class MoveOperation(
    val uris: List<android.net.Uri>,
    val targetRelativePath: String
)

enum class GroupByType {
    NONE, LAST_MODIFIED_DAILY, LAST_MODIFIED_MONTHLY, DATE_TAKEN_DAILY, DATE_TAKEN_MONTHLY, FILE_TYPE
}

enum class SortType {
    NAME, PATH, SIZE, LAST_MODIFIED, DATE_TAKEN, RANDOM
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

enum class ViewType {
    GRID, LIST
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(val folders: List<com.davide.seddio.easygallery.data.Folder>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}
