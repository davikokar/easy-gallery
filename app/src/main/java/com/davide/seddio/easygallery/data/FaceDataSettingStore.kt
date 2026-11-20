package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Whether face data is on (ADR-0024 §1): the user's intent, on by default. A flat scalar in `app_settings`, which is
 * backed up, so a user who switched it off finds it off on a restored device. The disclosure's own flag is not here.
 * [setEnabled] writes synchronously: the switch persists OFF before it deletes (ADR-0024 §3), so a kill between the two cannot revert it.
 */
interface FaceDataSettingStore {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}

class InMemoryFaceDataSettingStore(initiallyEnabled: Boolean = true) : FaceDataSettingStore {
    @Volatile
    private var enabled: Boolean = initiallyEnabled

    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

class SharedPreferencesFaceDataSettingStore(context: Context) : FaceDataSettingStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_FACE_DATA_ENABLED, true)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FACE_DATA_ENABLED, enabled).commit()
    }

    internal companion object {
        const val PREFS_FILE = "app_settings"
        const val KEY_FACE_DATA_ENABLED = "face_data_enabled"
    }
}
