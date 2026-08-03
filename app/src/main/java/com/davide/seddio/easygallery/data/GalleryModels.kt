package com.davide.seddio.easygallery.data

enum class SortType {
    NAME, PATH, SIZE, LAST_MODIFIED, DATE_TAKEN, RANDOM
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

enum class GroupByType {
    NONE, LAST_MODIFIED_DAILY, LAST_MODIFIED_MONTHLY, DATE_TAKEN_DAILY, DATE_TAKEN_MONTHLY, FILE_TYPE
}

enum class DisplayMode {
    GALLERY, CALENDAR
}

enum class OperationType {
    COPY, MOVE
}

enum class ViewType {
    GRID, LIST
}

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(val folders: List<Folder>) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}
