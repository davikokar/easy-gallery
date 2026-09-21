package com.davide.seddio.easygallery.data

/** The three main views, each owning an independent set of [ViewPreferences]. */
enum class PreferenceScope {
    FOLDERS,
    TIMELINE,
    FOLDER_DETAIL
}

data class ViewPreferences(
    val sortType: SortType,
    val sortOrder: SortOrder,
    val viewType: ViewType,
    val columns: Int,
    val groupBy: GroupByType,
    val groupOrder: SortOrder,
    val mediaTypes: Set<MediaType>,
    val showInfo: Boolean
) {
    companion object {
        const val MIN_COLUMNS = 1
        const val MAX_COLUMNS = 20

        fun defaultFor(scope: PreferenceScope): ViewPreferences = when (scope) {
            PreferenceScope.FOLDERS -> ViewPreferences(
                sortType = SortType.NAME,
                sortOrder = SortOrder.ASCENDING,
                viewType = ViewType.GRID,
                columns = 2,
                groupBy = GroupByType.NONE,
                groupOrder = SortOrder.DESCENDING,
                mediaTypes = MediaType.entries.toSet(),
                showInfo = false
            )
            PreferenceScope.TIMELINE -> ViewPreferences(
                sortType = SortType.DATE_TAKEN,
                sortOrder = SortOrder.DESCENDING,
                viewType = ViewType.GRID,
                columns = 3,
                groupBy = GroupByType.DATE_TAKEN_DAILY,
                groupOrder = SortOrder.DESCENDING,
                mediaTypes = MediaType.entries.toSet(),
                showInfo = false
            )
            PreferenceScope.FOLDER_DETAIL -> ViewPreferences(
                sortType = SortType.NAME,
                sortOrder = SortOrder.ASCENDING,
                viewType = ViewType.GRID,
                columns = 3,
                groupBy = GroupByType.NONE,
                groupOrder = SortOrder.DESCENDING,
                mediaTypes = MediaType.entries.toSet(),
                showInfo = false
            )
        }
    }
}
