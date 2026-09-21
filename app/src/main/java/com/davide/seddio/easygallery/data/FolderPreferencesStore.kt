package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences

/** Persists folder curation that the user expects to survive a restart. */
interface FolderPreferencesStore {
    fun loadPinned(): Set<String>
    fun savePinned(folderPaths: Set<String>)
    fun loadExcluded(): Set<String>
    fun saveExcluded(folderPaths: Set<String>)
}

class InMemoryFolderPreferencesStore : FolderPreferencesStore {
    private var pinned: Set<String> = emptySet()
    private var excluded: Set<String> = emptySet()

    override fun loadPinned(): Set<String> = pinned

    override fun savePinned(folderPaths: Set<String>) {
        pinned = folderPaths
    }

    override fun loadExcluded(): Set<String> = excluded

    override fun saveExcluded(folderPaths: Set<String>) {
        excluded = folderPaths
    }
}

class SharedPreferencesFolderStore(context: Context) : FolderPreferencesStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadPinned(): Set<String> = read(KEY_PINNED)

    override fun savePinned(folderPaths: Set<String>) = write(KEY_PINNED, folderPaths)

    override fun loadExcluded(): Set<String> = read(KEY_EXCLUDED)

    override fun saveExcluded(folderPaths: Set<String>) = write(KEY_EXCLUDED, folderPaths)

    private fun read(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    // Copies the set: SharedPreferences does not defensively copy what it stores.
    private fun write(key: String, folderPaths: Set<String>) {
        prefs.edit().putStringSet(key, folderPaths.toSet()).apply()
    }

    private companion object {
        const val PREFS_NAME = "folder_preferences"
        const val KEY_PINNED = "pinned_folders"
        const val KEY_EXCLUDED = "excluded_folders"
    }
}
