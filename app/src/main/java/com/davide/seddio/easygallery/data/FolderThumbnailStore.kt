package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences

interface FolderThumbnailStore {
    fun loadAll(): Map<String, String>
    fun save(folderPath: String, uri: String)
    fun clear(folderPath: String)
}

class InMemoryFolderThumbnailStore : FolderThumbnailStore {
    private val values = mutableMapOf<String, String>()

    override fun loadAll(): Map<String, String> = values.toMap()

    override fun save(folderPath: String, uri: String) {
        if (folderPath.isBlank() || uri.isBlank()) return
        values[folderPath] = uri
    }

    override fun clear(folderPath: String) {
        values.remove(folderPath)
    }
}

class SharedPreferencesFolderThumbnailStore(context: Context) : FolderThumbnailStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadAll(): Map<String, String> =
        prefs.all.mapNotNull { (folderPath, storedUri) ->
            val uri = storedUri as? String
            if (folderPath.isBlank() || uri.isNullOrBlank()) null else folderPath to uri
        }.toMap()

    override fun save(folderPath: String, uri: String) {
        if (folderPath.isBlank() || uri.isBlank()) return
        prefs.edit().putString(folderPath, uri).apply()
    }

    override fun clear(folderPath: String) {
        prefs.edit().remove(folderPath).apply()
    }

    private companion object {
        const val PREFS_NAME = "folder_thumbnails"
    }
}
