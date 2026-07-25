package com.davide.seddio.easygallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.Photo
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

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

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

    val groupedPhotosByDate: StateFlow<Map<String, List<Photo>>> = combine(
        _allPhotos, _searchQuery
    ) { photos, query ->
        val filtered = if (query.isNotEmpty()) {
            photos.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            photos
        }
        groupPhotosByDate(filtered)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _columnsCount = MutableStateFlow(2)
    val columnsCount: StateFlow<Int> = _columnsCount.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _photosInFolder = MutableStateFlow<List<Photo>>(emptyList())
    val photosInFolder: StateFlow<List<Photo>> = _photosInFolder.asStateFlow()

    val filteredPhotos: StateFlow<List<Photo>> = combine(_photosInFolder, _searchQuery) { photos, query ->
        if (query.isNotEmpty()) {
            photos.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            photos
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _showInfo = MutableStateFlow(false)
    val showInfo: StateFlow<Boolean> = _showInfo.asStateFlow()

    private val _selectedPhoto = MutableStateFlow<Photo?>(null)
    val selectedPhoto: StateFlow<Photo?> = _selectedPhoto.asStateFlow()

    private val _isImmersiveMode = MutableStateFlow(false)
    val isImmersiveMode: StateFlow<Boolean> = _isImmersiveMode.asStateFlow()

    private val _currentRotation = MutableStateFlow(0f)
    val currentRotation: StateFlow<Float> = _currentRotation.asStateFlow()

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            try {
                val folders = dataSource.getFolders()
                _uiState.value = GalleryUiState.Success(folders)
                _allPhotos.value = dataSource.getAllPhotos()
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
                _photosInFolder.value = dataSource.getPhotosInFolder(folder.name)
            }
        }
    }

    fun backToFolders() {
        _selectedFolder.value = null
        _photosInFolder.value = emptyList()
        setSearchActive(false)
    }

    fun toggleInfo() {
        _showInfo.value = !_showInfo.value
    }

    fun selectPhoto(photo: Photo) {
        _selectedPhoto.value = photo
        _isImmersiveMode.value = false
        _currentRotation.value = 0f
    }

    fun closePhoto() {
        _selectedPhoto.value = null
    }

    fun toggleImmersiveMode() {
        _isImmersiveMode.value = !_isImmersiveMode.value
    }

    fun rotatePhoto() {
        _currentRotation.value = (_currentRotation.value + 90f) % 360f
    }

    fun deletePhoto(photo: Photo) {
        viewModelScope.launch {
            // Simulate deletion in UI state
            _photosInFolder.value = _photosInFolder.value.filter { it != photo }
            _allPhotos.value = _allPhotos.value.filter { it != photo }
            closePhoto()
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

    private fun groupPhotosByDate(photos: List<Photo>): Map<String, List<Photo>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        val yesterday = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

        return photos.groupBy {
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
