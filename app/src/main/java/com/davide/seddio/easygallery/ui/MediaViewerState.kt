package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the transient state of the full-screen media viewer (the currently open item, the list it
 * pages through, immersive mode, and rotation). Extracted from [GalleryViewModel] so this concern
 * is self-contained; the ViewModel delegates to it and re-exposes its flows unchanged.
 */
class MediaViewerState {

    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia.asStateFlow()

    private val _currentMediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    val currentMediaList: StateFlow<List<MediaItem>> = _currentMediaList.asStateFlow()

    private val _isImmersiveMode = MutableStateFlow(false)
    val isImmersiveMode: StateFlow<Boolean> = _isImmersiveMode.asStateFlow()

    private val _currentRotation = MutableStateFlow(0f)
    val currentRotation: StateFlow<Float> = _currentRotation.asStateFlow()

    /** Opens [item] within [list], resetting immersive mode and rotation. */
    fun open(item: MediaItem, list: List<MediaItem>) {
        _currentMediaList.value = list
        _selectedMedia.value = item
        _isImmersiveMode.value = false
        _currentRotation.value = 0f
    }

    /** Switches the current item (e.g. when paging) and resets rotation. */
    fun setCurrent(item: MediaItem) {
        _selectedMedia.value = item
        _currentRotation.value = 0f
    }

    fun close() {
        _selectedMedia.value = null
    }

    fun toggleImmersive() {
        _isImmersiveMode.value = !_isImmersiveMode.value
    }

    fun rotate() {
        _currentRotation.value = (_currentRotation.value + 90f) % 360f
    }
}
