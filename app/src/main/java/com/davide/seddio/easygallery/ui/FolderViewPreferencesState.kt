package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.FolderViewOverrides
import com.davide.seddio.easygallery.data.FolderViewPreferencesStore
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.InMemoryFolderViewPreferencesStore
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.OverridablePreference
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FolderViewPreferencesState(
    private val store: FolderViewPreferencesStore = InMemoryFolderViewPreferencesStore()
) {

    private val _overrides = MutableStateFlow(store.loadAll())
    val overrides: StateFlow<Map<String, FolderViewOverrides>> = _overrides.asStateFlow()

    fun overridesFor(folderPath: String?): FolderViewOverrides {
        val key = folderPath?.takeIf { it.isNotBlank() } ?: return FolderViewOverrides()
        return _overrides.value[key] ?: FolderViewOverrides()
    }

    fun setSort(folderPath: String?, sortType: SortType, sortOrder: SortOrder) {
        update(folderPath) { current -> current.withSort(sortType, sortOrder) }
    }

    fun setColumns(folderPath: String?, columns: Int) {
        update(folderPath) { current -> current.withColumns(columns) }
    }

    fun setGroupBy(folderPath: String?, groupBy: GroupByType, groupOrder: SortOrder) {
        update(folderPath) { current -> current.withGroupBy(groupBy, groupOrder) }
    }

    fun setMediaTypes(folderPath: String?, mediaTypes: Set<MediaType>) {
        update(folderPath) { current -> current.withMediaTypes(mediaTypes) }
    }

    fun setViewType(folderPath: String?, viewType: ViewType) {
        update(folderPath) { current -> current.withViewType(viewType) }
    }

    fun clear(group: OverridablePreference) {
        store.clear(group)
        val updated = _overrides.value
            .mapValues { (_, overrides) -> overrides.cleared(group) }
            .filterValues { !it.isEmpty() }
        if (updated != _overrides.value) {
            _overrides.value = updated
        }
    }

    private fun update(folderPath: String?, transform: (FolderViewOverrides) -> FolderViewOverrides) {
        val key = folderPath?.takeIf { it.isNotBlank() } ?: return
        val current = _overrides.value[key] ?: FolderViewOverrides()
        val updated = transform(current)
        if (updated == current) return

        val next = _overrides.value.toMutableMap()
        if (updated.isEmpty()) {
            next.remove(key)
        } else {
            next[key] = updated
        }

        _overrides.value = next
        store.save(key, updated)
    }
}
