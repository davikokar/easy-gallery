package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences

interface DisplayPreferencesStore {
    fun load(scope: PreferenceScope): ViewPreferences
    fun save(scope: PreferenceScope, preferences: ViewPreferences)
    fun loadSortUserDefined(scope: PreferenceScope): Boolean
    fun saveSortUserDefined(scope: PreferenceScope, isUserDefined: Boolean)
}

/** Used by unit tests and previews so preferences never touch disk. */
class InMemoryDisplayPreferencesStore : DisplayPreferencesStore {
    private val values = mutableMapOf<PreferenceScope, ViewPreferences>()
    private val sortUserDefined = mutableMapOf<PreferenceScope, Boolean>()

    override fun load(scope: PreferenceScope): ViewPreferences =
        values[scope] ?: ViewPreferences.defaultFor(scope)

    override fun save(scope: PreferenceScope, preferences: ViewPreferences) {
        values[scope] = preferences
    }

    override fun loadSortUserDefined(scope: PreferenceScope): Boolean =
        sortUserDefined[scope] ?: false

    override fun saveSortUserDefined(scope: PreferenceScope, isUserDefined: Boolean) {
        sortUserDefined[scope] = isUserDefined
    }
}

class SharedPreferencesDisplayStore(context: Context) : DisplayPreferencesStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(scope: PreferenceScope): ViewPreferences {
        val defaults = ViewPreferences.defaultFor(scope)
        return ViewPreferences(
            sortType = readEnum(scope, KEY_SORT_TYPE, defaults.sortType, SortType::valueOf),
            sortOrder = readEnum(scope, KEY_SORT_ORDER, defaults.sortOrder, SortOrder::valueOf),
            viewType = readEnum(scope, KEY_VIEW_TYPE, defaults.viewType, ViewType::valueOf),
            columns = prefs.getInt(key(scope, KEY_COLUMNS), defaults.columns)
                .coerceIn(ViewPreferences.MIN_COLUMNS, ViewPreferences.MAX_COLUMNS),
            groupBy = readEnum(scope, KEY_GROUP_BY, defaults.groupBy, GroupByType::valueOf),
            groupOrder = readEnum(scope, KEY_GROUP_ORDER, defaults.groupOrder, SortOrder::valueOf),
            mediaTypes = readMediaTypes(scope, defaults.mediaTypes),
            showInfo = prefs.getBoolean(key(scope, KEY_SHOW_INFO), defaults.showInfo)
        )
    }

    override fun save(scope: PreferenceScope, preferences: ViewPreferences) {
        prefs.edit()
            .putString(key(scope, KEY_SORT_TYPE), preferences.sortType.name)
            .putString(key(scope, KEY_SORT_ORDER), preferences.sortOrder.name)
            .putString(key(scope, KEY_VIEW_TYPE), preferences.viewType.name)
            .putInt(key(scope, KEY_COLUMNS), preferences.columns)
            .putString(key(scope, KEY_GROUP_BY), preferences.groupBy.name)
            .putString(key(scope, KEY_GROUP_ORDER), preferences.groupOrder.name)
            .putStringSet(key(scope, KEY_MEDIA_TYPES), preferences.mediaTypes.map { it.name }.toSet())
            .putBoolean(key(scope, KEY_SHOW_INFO), preferences.showInfo)
            .apply()
    }

    override fun loadSortUserDefined(scope: PreferenceScope): Boolean {
        return prefs.getBoolean(
            key(scope, KEY_SORT_USER_DEFINED),
            defaultSortUserDefined(scope)
        )
    }

    override fun saveSortUserDefined(scope: PreferenceScope, isUserDefined: Boolean) {
        prefs.edit()
            .putBoolean(key(scope, KEY_SORT_USER_DEFINED), isUserDefined)
            .apply()
    }

    // Stored values may predate an enum rename, so fall back instead of throwing.
    private fun <T> readEnum(scope: PreferenceScope, field: String, default: T, parse: (String) -> T): T {
        val stored = prefs.getString(key(scope, field), null) ?: return default
        return runCatching { parse(stored) }.getOrDefault(default)
    }

    private fun readMediaTypes(scope: PreferenceScope, default: Set<MediaType>): Set<MediaType> {
        val stored = prefs.getStringSet(key(scope, KEY_MEDIA_TYPES), null) ?: return default
        val parsed = stored.mapNotNull { name -> runCatching { MediaType.valueOf(name) }.getOrNull() }.toSet()
        return parsed.ifEmpty { default }
    }

    private fun defaultSortUserDefined(scope: PreferenceScope): Boolean {
        val defaults = ViewPreferences.defaultFor(scope)
        val storedSortType = readEnum(scope, KEY_SORT_TYPE, defaults.sortType, SortType::valueOf)
        val storedSortOrder = readEnum(scope, KEY_SORT_ORDER, defaults.sortOrder, SortOrder::valueOf)
        return storedSortType != defaults.sortType || storedSortOrder != defaults.sortOrder
    }

    private fun key(scope: PreferenceScope, field: String) = "${scope.name}_$field"

    private companion object {
        const val PREFS_NAME = "display_preferences"
        const val KEY_SORT_TYPE = "sort_type"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_VIEW_TYPE = "view_type"
        const val KEY_COLUMNS = "columns"
        const val KEY_GROUP_BY = "group_by"
        const val KEY_GROUP_ORDER = "group_order"
        const val KEY_MEDIA_TYPES = "media_types"
        const val KEY_SHOW_INFO = "show_info"
        const val KEY_SORT_USER_DEFINED = "sort_user_defined"
    }
}
