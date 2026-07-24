package com.davide.seddio.easygallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val dataSource = MediaStoreDataSource(application)

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _columnsCount = MutableStateFlow(2)
    val columnsCount: StateFlow<Int> = _columnsCount.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _photosInFolder = MutableStateFlow<List<Photo>>(emptyList())
    val photosInFolder: StateFlow<List<Photo>> = _photosInFolder.asStateFlow()

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
    }

    fun toggleInfo() {
        _showInfo.value = !_showInfo.value
    }
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(val folders: List<Folder>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}
