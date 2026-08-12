package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds user-facing display preferences (search, filtering, sorting, grouping, view type, column
 * counts and the info toggle) shared across the folder and picture screens. Extracted from
 * [GalleryViewModel], which delegates to it and re-exposes these flows unchanged.
 */
class DisplayPreferencesState {

    private val _displayMode = MutableStateFlow(DisplayMode.GALLERY)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

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

    private val _folderColumns = MutableStateFlow(2)
    val folderColumns: StateFlow<Int> = _folderColumns.asStateFlow()

    private val _pictureColumns = MutableStateFlow(3)
    val pictureColumns: StateFlow<Int> = _pictureColumns.asStateFlow()

    private val _showInfo = MutableStateFlow(false)
    val showInfo: StateFlow<Boolean> = _showInfo.asStateFlow()

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

    fun toggleInfo() {
        _showInfo.value = !_showInfo.value
    }

    fun toggleDisplayMode() {
        _displayMode.value =
            if (_displayMode.value == DisplayMode.GALLERY) DisplayMode.CALENDAR else DisplayMode.GALLERY
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
}
