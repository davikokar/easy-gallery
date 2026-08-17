package com.davide.seddio.easygallery.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.MediaRepository
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the state and logic for the "create folder" dialog. Split out of [GalleryViewModel] so the
 * feature is self-contained and independently testable. It emits [folderCreated] on success; the
 * hosting screen listens to that event to refresh the folder list.
 */
class CreateFolderViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MediaRepository = MediaStoreDataSource(application)
) : AndroidViewModel(application) {

    private val _isDialogOpen = MutableStateFlow(false)
    val isDialogOpen: StateFlow<Boolean> = _isDialogOpen.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _browsingPath = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    val browsingPath: StateFlow<String> = _browsingPath.asStateFlow()

    val browsingFolders: StateFlow<List<Folder>> = _browsingPath.map { path ->
        repository.getSubdirectories(path)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _folderCreated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val folderCreated: SharedFlow<Unit> = _folderCreated.asSharedFlow()

    fun setDialogOpen(open: Boolean) {
        _isDialogOpen.value = open
        if (open) {
            _browsingPath.value = Environment.getExternalStorageDirectory().absolutePath
        } else {
            _error.value = null
        }
    }

    fun updateBrowsingPath(path: String) {
        _browsingPath.value = path
    }

    fun createFolder(name: String) {
        if (name.isBlank()) {
            _error.value = "Folder name cannot be empty"
            return
        }

        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (name.any { it in invalidChars }) {
            _error.value = "Invalid characters in folder name"
            return
        }

        val parentPath = _browsingPath.value
        val fullPath = File(parentPath, name).absolutePath

        if (repository.folderExists(fullPath)) {
            _error.value = "Folder already exists"
            return
        }

        viewModelScope.launch {
            val result = repository.createFolder(parentPath, name)
            if (result.isSuccess) {
                setDialogOpen(false)
                _folderCreated.tryEmit(Unit)
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create folder"
            }
        }
    }
}
