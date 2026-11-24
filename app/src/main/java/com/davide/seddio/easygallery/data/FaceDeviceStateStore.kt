package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.SharedPreferences

/**
 * What the capability check last found for [key], and how many starts of it never finished. [capable] is null until a
 * result is recorded.
 */
data class FaceCapabilityState(
    val key: String,
    val capable: Boolean?,
    val unfinishedStarts: Int
)

/**
 * State that belongs to this installation on this device, so no backup may carry it (ADR-0018 Amendment 7 §3): whether
 * the face-data disclosure has been shown (ADR-0024 §2) and the capability check's result (ADR-0023 §3). The face-data
 * setting is not here; it is backed up. The file is `face_device_state`, excluded in every backup rule.
 */
interface FaceDeviceStateStore {
    fun isDisclosureShown(): Boolean

    fun markDisclosureShown()

    /** The record for the key last used, or null when no check ever started. */
    fun capabilityState(): FaceCapabilityState?

    /**
     * Counts a start of the check for [key] and returns how many starts of it are unfinished, this one included. A key
     * other than the stored one replaces the record. Durable before it returns: the check may end the process.
     */
    fun recordCapabilityStart(key: String): Int

    /** Takes back one start of [key] whose outcome says nothing about the device, such as running out of memory. */
    fun clearCapabilityStart(key: String)

    /** Records the result for [key] and ends its unfinished starts. Durable before it returns. */
    fun saveCapabilityResult(key: String, capable: Boolean)
}

class InMemoryFaceDeviceStateStore : FaceDeviceStateStore {
    private var disclosureShown = false
    private var state: FaceCapabilityState? = null

    override fun isDisclosureShown(): Boolean = disclosureShown

    override fun markDisclosureShown() {
        disclosureShown = true
    }

    override fun capabilityState(): FaceCapabilityState? = state

    override fun recordCapabilityStart(key: String): Int {
        val current = state?.takeIf { it.key == key } ?: FaceCapabilityState(key, capable = null, unfinishedStarts = 0)
        val next = current.copy(unfinishedStarts = current.unfinishedStarts + 1)
        state = next
        return next.unfinishedStarts
    }

    override fun clearCapabilityStart(key: String) {
        val current = state?.takeIf { it.key == key && it.unfinishedStarts > 0 } ?: return
        state = current.copy(unfinishedStarts = current.unfinishedStarts - 1)
    }

    override fun saveCapabilityResult(key: String, capable: Boolean) {
        state = FaceCapabilityState(key, capable, unfinishedStarts = 0)
    }
}

class SharedPreferencesFaceDeviceStateStore(context: Context) : FaceDeviceStateStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FACE_DEVICE_STATE_FILE, Context.MODE_PRIVATE)

    override fun isDisclosureShown(): Boolean = prefs.getBoolean(KEY_DISCLOSURE_SHOWN, false)

    override fun markDisclosureShown() {
        prefs.edit().putBoolean(KEY_DISCLOSURE_SHOWN, true).apply()
    }

    override fun capabilityState(): FaceCapabilityState? {
        val key = prefs.getString(KEY_CAPABILITY_KEY, null) ?: return null
        val capable = if (prefs.contains(KEY_CAPABILITY_RESULT)) prefs.getBoolean(KEY_CAPABILITY_RESULT, false) else null
        return FaceCapabilityState(key, capable, prefs.getInt(KEY_CAPABILITY_STARTS, 0))
    }

    // commit, not apply: a start that the check's own crash could lose is no marker at all.
    override fun recordCapabilityStart(key: String): Int {
        val current = capabilityState()?.takeIf { it.key == key }
        val starts = (current?.unfinishedStarts ?: 0) + 1
        val editor = prefs.edit().putString(KEY_CAPABILITY_KEY, key).putInt(KEY_CAPABILITY_STARTS, starts)
        if (current == null) editor.remove(KEY_CAPABILITY_RESULT)
        editor.commit()
        return starts
    }

    override fun clearCapabilityStart(key: String) {
        val current = capabilityState()?.takeIf { it.key == key && it.unfinishedStarts > 0 } ?: return
        prefs.edit().putInt(KEY_CAPABILITY_STARTS, current.unfinishedStarts - 1).commit()
    }

    override fun saveCapabilityResult(key: String, capable: Boolean) {
        prefs.edit()
            .putString(KEY_CAPABILITY_KEY, key)
            .putBoolean(KEY_CAPABILITY_RESULT, capable)
            .putInt(KEY_CAPABILITY_STARTS, 0)
            .commit()
    }

    internal companion object {
        const val KEY_DISCLOSURE_SHOWN = "disclosure_shown"
        const val KEY_CAPABILITY_KEY = "capability_key"
        const val KEY_CAPABILITY_RESULT = "capability_result"
        const val KEY_CAPABILITY_STARTS = "capability_starts"
    }
}

const val FACE_DEVICE_STATE_FILE = "face_device_state"
