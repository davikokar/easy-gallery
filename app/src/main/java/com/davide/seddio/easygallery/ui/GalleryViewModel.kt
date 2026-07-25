package com.davide.seddio.easygallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.MediaItem
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

    val filteredAllMedia: StateFlow<List<MediaItem>> = combine(_allMedia, _searchQuery) { media, query ->
        if (query.isNotEmpty()) {
            media.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            media
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _pinnedFolders = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _sortType = MutableStateFlow(SortType.NAME)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _viewType = MutableStateFlow(ViewType.GRID)
    val viewType: StateFlow<ViewType> = _viewType.asStateFlow()

    val filteredFolders: StateFlow<GalleryUiState> = combine(
        _uiState, _searchQuery, _pinnedFolders, _sortType
    ) { state, query, pinned, sort ->
        if (state is GalleryUiState.Success) {
            val foldersWithPinned = state.folders.map { 
                it.copy(isPinned = pinned.contains(it.name)) 
            }

            val sortedFolders = when (sort) {
                SortType.NAME -> foldersWithPinned.sortedBy { it.name }
                SortType.PATH -> foldersWithPinned.sortedBy { it.path }
                SortType.SIZE -> foldersWithPinned.sortedByDescending { it.size }
                SortType.LAST_MODIFIED -> foldersWithPinned.sortedByDescending { it.dateModified }
                SortType.DATE_TAKEN -> foldersWithPinned.sortedByDescending { it.dateTaken }
                SortType.RANDOM -> foldersWithPinned.shuffled()
            }.sortedByDescending { it.isPinned }

            if (query.isNotEmpty()) {
                GalleryUiState.Success(sortedFolders.filter { it.name.contains(query, ignoreCase = true) })
            } else {
                GalleryUiState.Success(sortedFolders)
            }
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState.Loading)

    val groupedPhotosByDate: StateFlow<Map<String, List<MediaItem>>> = combine(
        filteredAllMedia, _searchQuery
    ) { media, _ ->
        groupMediaByDate(media)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _columnsCount = MutableStateFlow(2)
    val columnsCount: StateFlow<Int> = _columnsCount.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _mediaInFolder = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaInFolder: StateFlow<List<MediaItem>> = _mediaInFolder.asStateFlow()

    val filteredMedia: StateFlow<List<MediaItem>> = combine(_mediaInFolder, _searchQuery) { media, query ->
        if (query.isNotEmpty()) {
            media.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            media
        }
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

    fun increaseColumns() {
        if (_columnsCount.value < 20) {
            _columnsCount.value += 1
        }
    }

    fun decreaseColumns() {
        if (_columnsCount.value > 1) {
            _columnsCount.value -= 1
        }
    }

    fun setColumnsCount(count: Int) {
        _columnsCount.value = count.coerceIn(1, 20)
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

    fun setSortType(sortType: SortType) {
        _sortType.value = sortType
    }

    fun setViewType(viewType: ViewType) {
        _viewType.value = viewType
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
        val currentState = _uiState.value
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

enum class ViewType {
    GRID, LIST
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(val folders: List<com.davide.seddio.easygallery.data.Folder>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}
