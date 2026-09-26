package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.data.DisplayPreferencesStore
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.InMemoryDisplayPreferencesStore
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.PreferenceScope
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewPreferences
import com.davide.seddio.easygallery.data.ViewType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds user-facing display preferences (filtering, sorting, grouping, view type, column counts and
 * the info toggle). Each [PreferenceScope] — Folders View, Timeline View and Folder Detail View —
 * owns an independent [ViewPreferences] bundle, persisted through [DisplayPreferencesStore].
 *
 * Search state is scoped the same way but deliberately kept in memory only.
 */
class DisplayPreferencesState(
    private val store: DisplayPreferencesStore = InMemoryDisplayPreferencesStore()
) {

    private val _displayMode = MutableStateFlow(DisplayMode.GALLERY)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val preferenceFlows: Map<PreferenceScope, MutableStateFlow<ViewPreferences>> =
        PreferenceScope.entries.associateWith { MutableStateFlow(store.load(it)) }

    private val searchQueries: Map<PreferenceScope, MutableStateFlow<String>> =
        PreferenceScope.entries.associateWith { MutableStateFlow("") }

    private val searchActive: Map<PreferenceScope, MutableStateFlow<Boolean>> =
        PreferenceScope.entries.associateWith { MutableStateFlow(false) }

    private val sortUserDefined: Map<PreferenceScope, MutableStateFlow<Boolean>> =
        PreferenceScope.entries.associateWith { MutableStateFlow(store.loadSortUserDefined(it)) }

    fun preferences(scope: PreferenceScope): StateFlow<ViewPreferences> =
        preferenceFlows.getValue(scope)

    fun searchQuery(scope: PreferenceScope): StateFlow<String> = searchQueries.getValue(scope)

    fun isSearchActive(scope: PreferenceScope): StateFlow<Boolean> = searchActive.getValue(scope)

    fun isSortUserDefined(scope: PreferenceScope): StateFlow<Boolean> = sortUserDefined.getValue(scope)

    private fun update(scope: PreferenceScope, transform: (ViewPreferences) -> ViewPreferences) {
        val flow = preferenceFlows.getValue(scope)
        val updated = transform(flow.value)
        if (updated == flow.value) return
        flow.value = updated
        store.save(scope, updated)
    }

    fun increaseColumns(scope: PreferenceScope) = update(scope) {
        it.copy(columns = (it.columns + 1).coerceAtMost(ViewPreferences.MAX_COLUMNS))
    }

    fun decreaseColumns(scope: PreferenceScope) = update(scope) {
        it.copy(columns = (it.columns - 1).coerceAtLeast(ViewPreferences.MIN_COLUMNS))
    }

    fun setColumnsCount(count: Int, scope: PreferenceScope) = update(scope) {
        it.copy(columns = count.coerceIn(ViewPreferences.MIN_COLUMNS, ViewPreferences.MAX_COLUMNS))
    }

    fun toggleInfo(scope: PreferenceScope) = update(scope) { it.copy(showInfo = !it.showInfo) }

    fun toggleDisplayMode() {
        _displayMode.value =
            if (_displayMode.value == DisplayMode.GALLERY) DisplayMode.CALENDAR else DisplayMode.GALLERY
    }

    fun setSortType(sortType: SortType, scope: PreferenceScope) = update(scope) {
        it.copy(sortType = sortType)
    }

    fun setSortOrder(order: SortOrder, scope: PreferenceScope) = update(scope) {
        it.copy(sortOrder = order)
    }

    fun setGroupBy(type: GroupByType, scope: PreferenceScope) = update(scope) {
        it.copy(groupBy = type)
    }

    fun setGroupOrder(order: SortOrder, scope: PreferenceScope) = update(scope) {
        it.copy(groupOrder = order)
    }

    fun setViewType(viewType: ViewType, scope: PreferenceScope) = update(scope) {
        it.copy(viewType = viewType)
    }

    fun setSelectedMediaTypes(types: Set<MediaType>, scope: PreferenceScope) = update(scope) {
        it.copy(mediaTypes = types)
    }

    fun setSearchQuery(query: String, scope: PreferenceScope) {
        searchQueries.getValue(scope).value = query
    }

    fun setSearchActive(active: Boolean, scope: PreferenceScope) {
        searchActive.getValue(scope).value = active
        if (!active) {
            searchQueries.getValue(scope).value = ""
        }
    }

    fun markSortUserDefined(scope: PreferenceScope) {
        val flow = sortUserDefined.getValue(scope)
        if (flow.value) return
        flow.value = true
        store.saveSortUserDefined(scope, true)
    }
}
