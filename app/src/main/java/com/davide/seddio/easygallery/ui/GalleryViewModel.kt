package com.davide.seddio.easygallery.ui

import android.app.Application
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
import java.text.SimpleDateFormat
import java.util.*

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val dataSource = MediaStoreDataSource(application)

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _displayMode = MutableStateFlow(DisplayMode.GALLERY)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
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

    val filteredAllMedia: StateFlow<List<MediaItem>> = combine(
        _allMedia, _searchQuery, _excludedFolders, _selectedMediaTypes, _pictureSortType, _pictureSortOrder
    ) { args ->
        val media = args[0] as List<MediaItem>
        val query = args[1] as String
        val excluded = args[2] as Set<String>
        val types = args[3] as Set<MediaType>
        val sort = args[4] as SortType
        val order = args[5] as SortOrder

        val filtered = media.filter { 
            !excluded.contains(it.bucketName) && types.contains(it.type)
        }
        
        val searchFiltered = if (query.isNotEmpty()) {
            filtered.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            filtered
        }

        sortMedia(searchFiltered, sort, order)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _pinnedFolders = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _isManageExcludedMode = MutableStateFlow(false)
    val isManageExcludedMode: StateFlow<Boolean> = _isManageExcludedMode.asStateFlow()

    private val _isSettingsMode = MutableStateFlow(false)
    val isSettingsMode: StateFlow<Boolean> = _isSettingsMode.asStateFlow()

    val filteredFolders: StateFlow<GalleryUiState> = combine(
        _allMedia, _searchQuery, _pinnedFolders, _folderSortType, _excludedFolders, _selectedMediaTypes, _folderSortOrder
    ) { args ->
        val media = args[0] as List<MediaItem>
        val query = args[1] as String
        val pinned = args[2] as Set<String>
        val sort = args[3] as SortType
        val excluded = args[4] as Set<String>
        val types = args[5] as Set<MediaType>
        val order = args[6] as SortOrder

        if (media.isEmpty() && _uiState.value is GalleryUiState.Loading) {
            GalleryUiState.Loading
        } else {
            val foldersMap = mutableMapOf<String, Folder>()
            media.forEach { item ->
                if (!excluded.contains(item.bucketName) && types.contains(item.type)) {
                    val existing = foldersMap[item.bucketName]
                    if (existing == null) {
                        foldersMap[item.bucketName] = Folder(
                            name = item.bucketName,
                            imageCount = 1,
                            thumbnailUri = item.uri,
                            isPinned = pinned.contains(item.bucketName),
                            path = "",
                            size = 0L,
                            dateModified = item.dateAdded,
                            dateTaken = item.dateAdded
                        )
                    } else {
                        foldersMap[item.bucketName] = existing.copy(
                            imageCount = existing.imageCount + 1,
                            dateModified = maxOf(existing.dateModified, item.dateAdded),
                            dateTaken = maxOf(existing.dateTaken, item.dateAdded)
                        )
                    }
                }
            }

            val foldersList = foldersMap.values.toList()

            val baseSorted = when (sort) {
                SortType.NAME -> foldersList.sortedBy { it.name }
                SortType.PATH -> foldersList.sortedBy { it.path }
                SortType.SIZE -> foldersList.sortedBy { it.size }
                SortType.LAST_MODIFIED -> foldersList.sortedBy { it.dateModified }
                SortType.DATE_TAKEN -> foldersList.sortedBy { it.dateTaken }
                SortType.RANDOM -> foldersList.shuffled()
            }

            val orderedFolders = if (sort != SortType.RANDOM && order == SortOrder.DESCENDING) {
                baseSorted.reversed()
            } else {
                baseSorted
            }

            val finalSorted = orderedFolders.sortedByDescending { it.isPinned }

            val filteredList = if (query.isNotEmpty()) {
                finalSorted.filter { it.name.contains(query, ignoreCase = true) }
            } else {
                finalSorted
            }
            
            GalleryUiState.Success(filteredList)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState.Loading)

    val groupedPhotosByDate: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredAllMedia, _searchQuery
    ) { media, _ ->
        groupMediaByDate(media)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _folderColumns = MutableStateFlow(2)
    val folderColumns: StateFlow<Int> = _folderColumns.asStateFlow()

    private val _pictureColumns = MutableStateFlow(3)
    val pictureColumns: StateFlow<Int> = _pictureColumns.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _mediaInFolder = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaInFolder: StateFlow<List<MediaItem>> = _mediaInFolder.asStateFlow()

    val filteredMedia: StateFlow<List<MediaItem>> = combine(_mediaInFolder, _searchQuery, _selectedMediaTypes, _pictureSortType, _pictureSortOrder) { args ->
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

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            try {
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
        if (_isSelectionMode.value) {
            toggleSelection(folder.name)
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

    fun deleteMedia(item: MediaItem) {
        viewModelScope.launch {
            _mediaInFolder.value = _mediaInFolder.value.filter { it != item }
            _allMedia.value = _allMedia.value.filter { it != item }
            closeMedia()
        }
    }

    fun toggleDisplayMode() {
        _displayMode.value = if (_displayMode.value == DisplayMode.GALLERY) DisplayMode.CALENDAR else DisplayMode.GALLERY
    }

    fun setSettingsMode(active: Boolean) {
        _isSettingsMode.value = active
    }

    fun setManageExcludedMode(active: Boolean) {
        _isManageExcludedMode.value = active
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
            currentState.folders.filter { !excluded.contains(it.name) }
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

    fun toggleSelection(folderName: String) {
        val current = _selectedFolders.value.toMutableSet()
        if (current.contains(folderName)) {
            current.remove(folderName)
        } else {
            current.add(folderName)
        }
        _selectedFolders.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun enterSelectionMode(folderName: String) {
        _isSelectionMode.value = true
        _selectedFolders.value = setOf(folderName)
        setSearchActive(false)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedFolders.value = emptySet()
    }

    fun selectAll() {
        val currentState = filteredFolders.value
        if (currentState is GalleryUiState.Success) {
            _selectedFolders.value = currentState.folders.map { it.name }.toSet()
        }
    }

    fun getSelectedFoldersData(): List<Folder> {
        val selected = _selectedFolders.value
        val currentState = filteredFolders.value // Use filteredFolders to get correct counts
        return if (currentState is GalleryUiState.Success) {
            currentState.folders.filter { selected.contains(it.name) }
        } else {
            emptyList()
        }
    }

    fun deleteSelected() {
        val toDelete = _selectedFolders.value
        val currentState = _uiState.value
        if (currentState is GalleryUiState.Success) {
            val remaining = currentState.folders.filter { !toDelete.contains(it.name) }
            _uiState.value = GalleryUiState.Success(remaining)
        }
        exitSelectionMode()
    }

    fun pinSelected() {
        val selected = _selectedFolders.value
        val currentPinned = _pinnedFolders.value.toMutableSet()
        val allSelectedPinned = selected.all { currentPinned.contains(it) }
        if (allSelectedPinned) {
            currentPinned.removeAll(selected)
        } else {
            currentPinned.addAll(selected)
        }
        _pinnedFolders.value = currentPinned
        exitSelectionMode()
    }

    fun excludeSelected() {
        val selected = _selectedFolders.value
        val currentExcluded = _excludedFolders.value.toMutableSet()
        currentExcluded.addAll(selected)
        _excludedFolders.value = currentExcluded
        exitSelectionMode()
    }

    private fun sortMedia(media: List<MediaItem>, sort: SortType, order: SortOrder): List<MediaItem> {
        val baseSorted = when (sort) {
            SortType.NAME -> media.sortedBy { it.name }
            SortType.LAST_MODIFIED -> media.sortedBy { it.dateAdded }
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

    private fun groupMediaByDate(items: List<MediaItem>): Map<String, List<MediaItem>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        val yesterday = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

        return items.groupBy {
            val dateStr = sdf.format(Date(it.dateAdded * 1000))
            when (dateStr) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> {
                    val prettySdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    prettySdf.format(Date(it.dateAdded * 1000))
                }
            }
        }
    }
}

enum class DisplayMode {
    GALLERY, CALENDAR
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
