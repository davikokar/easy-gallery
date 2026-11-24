package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The face-data setting (ADR-0024 §1; plan 3.3, Task 1, criterion 11): a scalar in `app_settings`, on by default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class FaceDataSettingStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        listOf("app_settings", FACE_DEVICE_STATE_FILE).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun `face data is on by default in both stores`() {
        assertTrue(InMemoryFaceDataSettingStore().isEnabled())
        assertTrue(SharedPreferencesFaceDataSettingStore(context).isEnabled())
    }

    @Test
    fun `the in memory store can start off`() {
        assertFalse(InMemoryFaceDataSettingStore(initiallyEnabled = false).isEnabled())
    }

    @Test
    fun `switching off and on again is read back by both stores`() {
        listOf(InMemoryFaceDataSettingStore(), SharedPreferencesFaceDataSettingStore(context)).forEach { store ->
            store.setEnabled(false)
            assertFalse(store.isEnabled())

            store.setEnabled(true)
            assertTrue(store.isEnabled())
        }
    }

    @Test
    fun `the shared preferences store writes the flag under face_data_enabled in app_settings`() {
        SharedPreferencesFaceDataSettingStore(context).setEnabled(false)

        val file = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        assertEquals(setOf("face_data_enabled"), file.all.keys)
        assertEquals(false, file.getBoolean("face_data_enabled", true))
    }

    @Test
    fun `an off setting survives a new instance and does not touch the device state file`() {
        SharedPreferencesFaceDataSettingStore(context).setEnabled(false)

        assertFalse(SharedPreferencesFaceDataSettingStore(context).isEnabled())
        assertTrue(context.getSharedPreferences(FACE_DEVICE_STATE_FILE, Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun `an app_settings file holding other keys still reads the setting as on`() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString("app_language", "it").commit()

        assertTrue(SharedPreferencesFaceDataSettingStore(context).isEnabled())
    }
}
