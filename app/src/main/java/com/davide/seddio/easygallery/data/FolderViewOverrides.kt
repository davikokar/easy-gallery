package com.davide.seddio.easygallery.data

enum class PreferenceApplyTarget {
    CURRENT_FOLDER,
    ALL_FOLDERS
}

enum class OverridablePreference {
    SORT,
    COLUMNS,
    GROUP_BY,
    MEDIA_TYPES,
    VIEW_TYPE
}

data class FolderViewOverrides(
    val sortType: SortType? = null,
    val sortOrder: SortOrder? = null,
    val viewType: ViewType? = null,
    val columns: Int? = null,
    val groupBy: GroupByType? = null,
    val groupOrder: SortOrder? = null,
    val mediaTypes: Set<MediaType>? = null
) {
    fun applyTo(base: ViewPreferences): ViewPreferences = base.copy(
        sortType = sortType ?: base.sortType,
        sortOrder = sortOrder ?: base.sortOrder,
        viewType = viewType ?: base.viewType,
        columns = (columns ?: base.columns)
            .coerceIn(ViewPreferences.MIN_COLUMNS, ViewPreferences.MAX_COLUMNS),
        groupBy = groupBy ?: base.groupBy,
        groupOrder = groupOrder ?: base.groupOrder,
        mediaTypes = normalizedMediaTypes() ?: base.mediaTypes
    )

    fun isEmpty(): Boolean =
        sortType == null &&
            sortOrder == null &&
            viewType == null &&
            columns == null &&
            groupBy == null &&
            groupOrder == null &&
            normalizedMediaTypes() == null

    fun cleared(group: OverridablePreference): FolderViewOverrides = when (group) {
        OverridablePreference.SORT -> copy(sortType = null, sortOrder = null)
        OverridablePreference.COLUMNS -> copy(columns = null)
        OverridablePreference.GROUP_BY -> copy(groupBy = null, groupOrder = null)
        OverridablePreference.MEDIA_TYPES -> copy(mediaTypes = null)
        OverridablePreference.VIEW_TYPE -> copy(viewType = null)
    }

    fun withSort(sortType: SortType, sortOrder: SortOrder): FolderViewOverrides =
        copy(sortType = sortType, sortOrder = sortOrder)

    fun withColumns(columns: Int): FolderViewOverrides =
        copy(columns = columns.coerceIn(ViewPreferences.MIN_COLUMNS, ViewPreferences.MAX_COLUMNS))

    fun withGroupBy(groupBy: GroupByType, groupOrder: SortOrder): FolderViewOverrides =
        copy(groupBy = groupBy, groupOrder = groupOrder)

    fun withMediaTypes(mediaTypes: Set<MediaType>): FolderViewOverrides =
        copy(mediaTypes = mediaTypes.ifEmpty { null })

    fun withViewType(viewType: ViewType): FolderViewOverrides =
        copy(viewType = viewType)

    private fun normalizedMediaTypes(): Set<MediaType>? = mediaTypes?.ifEmpty { null }
}
