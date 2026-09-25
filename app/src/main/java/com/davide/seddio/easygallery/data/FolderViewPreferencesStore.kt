package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences

interface FolderViewPreferencesStore {
    fun loadAll(): Map<String, FolderViewOverrides>
    fun save(folderPath: String, overrides: FolderViewOverrides)
    fun clear(group: OverridablePreference)
}

class InMemoryFolderViewPreferencesStore : FolderViewPreferencesStore {
    private val values = mutableMapOf<String, FolderViewOverrides>()

    override fun loadAll(): Map<String, FolderViewOverrides> = values.toMap()

    override fun save(folderPath: String, overrides: FolderViewOverrides) {
        if (overrides.isEmpty()) {
            values.remove(folderPath)
            return
        }
        values[folderPath] = overrides
    }

    override fun clear(group: OverridablePreference) {
        val updated = values.mapValues { (_, overrides) -> overrides.cleared(group) }
            .filterValues { !it.isEmpty() }
        values.clear()
        values.putAll(updated)
    }
}

class SharedPreferencesFolderViewPreferencesStore(context: Context) : FolderViewPreferencesStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadAll(): Map<String, FolderViewOverrides> {
        val folderPaths = prefs.all.keys.mapNotNull { parseStoredKey(it)?.first }.toSet()
        return folderPaths.mapNotNull { folderPath ->
            val overrides = FolderViewOverrides(
                sortType = readEnum(folderPath, KEY_SORT_TYPE, SortType::valueOf),
                sortOrder = readEnum(folderPath, KEY_SORT_ORDER, SortOrder::valueOf),
                viewType = readEnum(folderPath, KEY_VIEW_TYPE, ViewType::valueOf),
                columns = readColumns(folderPath),
                groupBy = readEnum(folderPath, KEY_GROUP_BY, GroupByType::valueOf),
                groupOrder = readEnum(folderPath, KEY_GROUP_ORDER, SortOrder::valueOf),
                mediaTypes = readMediaTypes(folderPath)
            )
            if (overrides.isEmpty()) null else folderPath to overrides
        }.toMap()
    }

    override fun save(folderPath: String, overrides: FolderViewOverrides) {
        if (overrides.isEmpty()) {
            removeFolder(folderPath)
            return
        }

        prefs.edit()
            .putOrRemove(folderPath, KEY_SORT_TYPE, overrides.sortType?.name)
            .putOrRemove(folderPath, KEY_SORT_ORDER, overrides.sortOrder?.name)
            .putOrRemove(folderPath, KEY_VIEW_TYPE, overrides.viewType?.name)
            .putOrRemove(folderPath, KEY_COLUMNS, overrides.columns)
            .putOrRemove(folderPath, KEY_GROUP_BY, overrides.groupBy?.name)
            .putOrRemove(folderPath, KEY_GROUP_ORDER, overrides.groupOrder?.name)
            .putOrRemove(
                folderPath,
                KEY_MEDIA_TYPES,
                overrides.mediaTypes?.ifEmpty { null }?.map { it.name }?.toSet()
            )
            .apply()
    }

    override fun clear(group: OverridablePreference) {
        val fields = fieldsForGroup(group)
        prefs.edit().apply {
            prefs.all.keys.forEach { storedKey ->
                val parsed = parseStoredKey(storedKey) ?: return@forEach
                if (parsed.second in fields) {
                    remove(storedKey)
                }
            }
        }.apply()
    }

    private fun readColumns(folderPath: String): Int? {
        val storedKey = key(folderPath, KEY_COLUMNS)
        if (!prefs.contains(storedKey)) return null
        return runCatching { prefs.getInt(storedKey, ViewPreferences.MIN_COLUMNS) }
            .getOrNull()
            ?.coerceIn(ViewPreferences.MIN_COLUMNS, ViewPreferences.MAX_COLUMNS)
    }

    private fun readMediaTypes(folderPath: String): Set<MediaType>? {
        val storedKey = key(folderPath, KEY_MEDIA_TYPES)
        if (!prefs.contains(storedKey)) return null
        val stored = runCatching { prefs.getStringSet(storedKey, null) }.getOrNull() ?: return null
        return stored
            .mapNotNull { name -> runCatching { MediaType.valueOf(name) }.getOrNull() }
            .toSet()
            .ifEmpty { null }
    }

    private fun <T> readEnum(folderPath: String, field: String, parse: (String) -> T): T? {
        val stored = prefs.getString(key(folderPath, field), null) ?: return null
        return runCatching { parse(stored) }.getOrNull()
    }

    private fun removeFolder(folderPath: String) {
        prefs.edit().apply {
            ALL_FIELDS.forEach { field ->
                remove(key(folderPath, field))
            }
        }.apply()
    }

    private fun fieldsForGroup(group: OverridablePreference): Set<String> = when (group) {
        OverridablePreference.SORT -> setOf(KEY_SORT_TYPE, KEY_SORT_ORDER)
        OverridablePreference.COLUMNS -> setOf(KEY_COLUMNS)
        OverridablePreference.GROUP_BY -> setOf(KEY_GROUP_BY, KEY_GROUP_ORDER)
        OverridablePreference.MEDIA_TYPES -> setOf(KEY_MEDIA_TYPES)
        OverridablePreference.VIEW_TYPE -> setOf(KEY_VIEW_TYPE)
    }

    private fun parseStoredKey(storedKey: String): Pair<String, String>? {
        val separatorIndex = storedKey.lastIndexOf(KEY_SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex == storedKey.lastIndex) return null

        val folderPath = storedKey.substring(0, separatorIndex)
        val field = storedKey.substring(separatorIndex + 1)
        if (field !in ALL_FIELDS) return null

        return folderPath to field
    }

    private fun key(folderPath: String, field: String): String = "$folderPath$KEY_SEPARATOR$field"

    private fun SharedPreferences.Editor.putOrRemove(folderPath: String, field: String, value: String?) =
        apply {
            val storedKey = key(folderPath, field)
            if (value == null) remove(storedKey) else putString(storedKey, value)
        }

    private fun SharedPreferences.Editor.putOrRemove(folderPath: String, field: String, value: Int?) =
        apply {
            val storedKey = key(folderPath, field)
            if (value == null) remove(storedKey) else putInt(storedKey, value)
        }

    private fun SharedPreferences.Editor.putOrRemove(folderPath: String, field: String, value: Set<String>?) =
        apply {
            val storedKey = key(folderPath, field)
            if (value == null) remove(storedKey) else putStringSet(storedKey, value)
        }

    private companion object {
        const val PREFS_NAME = "folder_display_preferences"
        // '/' cannot appear inside a single path segment because it is the path separator.
        const val KEY_SEPARATOR = "/"
        const val KEY_SORT_TYPE = "sort_type"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_VIEW_TYPE = "view_type"
        const val KEY_COLUMNS = "columns"
        const val KEY_GROUP_BY = "group_by"
        const val KEY_GROUP_ORDER = "group_order"
        const val KEY_MEDIA_TYPES = "media_types"

        val ALL_FIELDS = setOf(
            KEY_SORT_TYPE,
            KEY_SORT_ORDER,
            KEY_VIEW_TYPE,
            KEY_COLUMNS,
            KEY_GROUP_BY,
            KEY_GROUP_ORDER,
            KEY_MEDIA_TYPES
        )
    }
}
