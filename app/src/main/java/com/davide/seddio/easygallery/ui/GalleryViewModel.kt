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

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val dataSource = MediaStoreDataSource(application)

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    val filteredFolders: StateFlow<GalleryUiState> = combine(_uiState, _searchQuery) { state, query ->
        if (state is GalleryUiState.Success && query.isNotEmpty()) {
            val filtered = state.folders.filter { it.name.contains(query, ignoreCase = true) }
            GalleryUiState.Success(filtered)
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState.Loading)

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

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            try {
                val folders = dataSource.getFolders()
                _uiState.value = GalleryUiState.Success(folders)
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun increaseColumns() {
        if (_columnsCount.value < 5) {
            _columnsCount.value += 1
        }
    }

    fun decreaseColumns() {
        if (_columnsCount.value > 1) {
            _columnsCount.value -= 1
        }
    }

    fun selectFolder(folder: Folder) {
        _selectedFolder.value = folder
        viewModelScope.launch {
            _photosInFolder.value = dataSource.getPhotosInFolder(folder.name)
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(val folders: List<com.davide.seddio.easygallery.data.Folder>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}
