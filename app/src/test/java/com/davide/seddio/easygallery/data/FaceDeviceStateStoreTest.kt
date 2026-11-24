package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The record of this installation's face state (plan 3.3, Task 1, criterion 11). The `SharedPreferences*` half needs a
 * real file; the contract is asserted on both stores, as the Tier C store tests do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class FaceDeviceStateStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = "12|1|fp/1"

    @Before
    fun setUp() {
        listOf(FACE_DEVICE_STATE_FILE, "app_settings").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun `the file is face_device_state`() {
        assertEquals("face_device_state", FACE_DEVICE_STATE_FILE)
    }

    @Test
    fun `a new store has not shown the disclosure and holds no record`() {
        forEachStore { store ->
            assertFalse(store.isDisclosureShown())
            assertNull(store.capabilityState())
        }
    }

    @Test
    fun `marking the disclosure shown is kept`() {
        forEachStore { store ->
            store.markDisclosureShown()

            assertTrue(store.isDisclosureShown())
        }
    }

    @Test
    fun `starts count up for one key and a result ends them`() {
        forEachStore { store ->
            assertEquals(1, store.recordCapabilityStart(key))
            assertEquals(2, store.recordCapabilityStart(key))
            assertEquals(FaceCapabilityState(key, capable = null, unfinishedStarts = 2), store.capabilityState())

            store.saveCapabilityResult(key, capable = true)

            assertEquals(FaceCapabilityState(key, capable = true, unfinishedStarts = 0), store.capabilityState())
        }
    }

    @Test
    fun `a start for another key replaces the record and its result`() {
        forEachStore { store ->
            store.saveCapabilityResult(key, capable = true)

            assertEquals(1, store.recordCapabilityStart("13|1|fp/1"))

            assertEquals(FaceCapabilityState("13|1|fp/1", capable = null, unfinishedStarts = 1), store.capabilityState())
        }
    }

    @Test
    fun `a start for the key that has a result keeps the result and counts the start`() {
        forEachStore { store ->
            store.saveCapabilityResult(key, capable = false)

            assertEquals(1, store.recordCapabilityStart(key))

            assertEquals(FaceCapabilityState(key, capable = false, unfinishedStarts = 1), store.capabilityState())
        }
    }

    @Test
    fun `taking back a start lowers the count for that key only and never below zero`() {
        forEachStore { store ->
            store.recordCapabilityStart(key)
            store.recordCapabilityStart(key)

            store.clearCapabilityStart("another")
            store.clearCapabilityStart(key)
            assertEquals(1, store.capabilityState()!!.unfinishedStarts)
            store.clearCapabilityStart(key)
            store.clearCapabilityStart(key)

            assertEquals(FaceCapabilityState(key, capable = null, unfinishedStarts = 0), store.capabilityState())
        }
    }

    @Test
    fun `an incapable result is told apart from no result`() {
        forEachStore { store ->
            store.saveCapabilityResult(key, capable = false)

            assertEquals(false, store.capabilityState()!!.capable)
        }
    }

    @Test
    fun `the shared preferences store writes face_device_state and nothing in app_settings`() {
        val store = SharedPreferencesFaceDeviceStateStore(context)

        store.markDisclosureShown()
        store.recordCapabilityStart(key)
        store.saveCapabilityResult(key, capable = true)

        val file = context.getSharedPreferences(FACE_DEVICE_STATE_FILE, Context.MODE_PRIVATE)
        assertEquals(
            setOf("disclosure_shown", "capability_key", "capability_result", "capability_starts"),
            file.all.keys
        )
        assertTrue(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun `the shared preferences state survives a new instance`() {
        SharedPreferencesFaceDeviceStateStore(context).apply {
            markDisclosureShown()
            recordCapabilityStart(key)
            recordCapabilityStart(key)
        }

        val reopened = SharedPreferencesFaceDeviceStateStore(context)

        assertTrue(reopened.isDisclosureShown())
        assertEquals(FaceCapabilityState(key, capable = null, unfinishedStarts = 2), reopened.capabilityState())
    }

    @Test
    fun `a result survives a new instance`() {
        SharedPreferencesFaceDeviceStateStore(context).saveCapabilityResult(key, capable = false)

        val reopened = SharedPreferencesFaceDeviceStateStore(context)

        assertEquals(FaceCapabilityState(key, capable = false, unfinishedStarts = 0), reopened.capabilityState())
    }

    @Test
    fun `the face data setting store does not write the file of the device state`() {
        SharedPreferencesFaceDataSettingStore(context).setEnabled(false)

        assertTrue(context.getSharedPreferences(FACE_DEVICE_STATE_FILE, Context.MODE_PRIVATE).all.isEmpty())
    }

    private fun forEachStore(block: (FaceDeviceStateStore) -> Unit) {
        listOf(
            "in-memory" to InMemoryFaceDeviceStateStore(),
            "shared preferences" to SharedPreferencesFaceDeviceStateStore(context)
        ).forEach { (name, store) ->
            setUp()
            try {
                block(store)
            } catch (e: AssertionError) {
                throw AssertionError("[$name store] ${e.message}", e)
            }
        }
    }
}
