package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.MediaStore

interface MediaStoreVersionStore {
    fun readVersion(): String?
    fun writeVersion(version: String)
}

interface MediaStoreVersionProvider {
    fun currentVersion(context: Context): String?
}

class InMemoryMediaStoreVersionStore(
    initialVersion: String? = null
) : MediaStoreVersionStore {
    private var value: String? = initialVersion

    override fun readVersion(): String? = value

    override fun writeVersion(version: String) {
        value = version
    }
}

class SharedPreferencesMediaStoreVersionStore(
    context: Context
) : MediaStoreVersionStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun readVersion(): String? {
        return prefs.getString(KEY_MEDIASTORE_VERSION, null)
    }

    override fun writeVersion(version: String) {
        prefs.edit().putString(KEY_MEDIASTORE_VERSION, version).apply()
    }

    private companion object {
        const val PREFS_FILE = "app_settings"
        const val KEY_MEDIASTORE_VERSION = "albums_mediastore_version"
    }
}

object AndroidMediaStoreVersionProvider : MediaStoreVersionProvider {
    override fun currentVersion(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.getVersion(context)
    }
}
