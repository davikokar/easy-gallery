package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleAvailabilityTest {

    @Test
    fun `face data off is unavailable whatever the capability record says`() {
        assertEquals(PeopleAvailability.FACE_DATA_OFF, peopleAvailabilityOf(false, null, KEY))
        assertEquals(PeopleAvailability.FACE_DATA_OFF, peopleAvailabilityOf(false, state(KEY, true), KEY))
    }

    @Test
    fun `face data off beats a device recorded incapable`() {
        assertEquals(PeopleAvailability.FACE_DATA_OFF, peopleAvailabilityOf(false, state(KEY, false), KEY))
    }

    @Test
    fun `incapable under the current key is device incapable`() {
        assertEquals(PeopleAvailability.DEVICE_INCAPABLE, peopleAvailabilityOf(true, state(KEY, false), KEY))
    }

    @Test
    fun `incapable under another key is available`() {
        assertEquals(PeopleAvailability.AVAILABLE, peopleAvailabilityOf(true, state(OTHER_KEY, false), KEY))
    }

    @Test
    fun `no record, a capable record and an unfinished check are available`() {
        assertEquals(PeopleAvailability.AVAILABLE, peopleAvailabilityOf(true, null, KEY))
        assertEquals(PeopleAvailability.AVAILABLE, peopleAvailabilityOf(true, state(KEY, true), KEY))
        assertEquals(PeopleAvailability.AVAILABLE, peopleAvailabilityOf(true, state(KEY, null, unfinishedStarts = 5), KEY))
    }

    @Test
    fun `the stored source reads the setting and the record fresh at every call`() {
        val setting = InMemoryFaceDataSettingStore(initiallyEnabled = true)
        val device = InMemoryFaceDeviceStateStore()
        val source = StoredPeopleAvailabilitySource(setting, device, KEY)

        assertEquals(PeopleAvailability.AVAILABLE, source.read())

        device.saveCapabilityResult(KEY, capable = false)
        assertEquals(PeopleAvailability.DEVICE_INCAPABLE, source.read())

        setting.setEnabled(false)
        assertEquals(PeopleAvailability.FACE_DATA_OFF, source.read())

        setting.setEnabled(true)
        device.saveCapabilityResult(OTHER_KEY, capable = false)
        assertEquals(PeopleAvailability.AVAILABLE, source.read())
    }

    @Test
    fun `the stored source never reads the disclosure and never writes`() {
        val device = ReadOnlyDeviceStore(InMemoryFaceDeviceStateStore().also { it.saveCapabilityResult(KEY, false) })
        val source = StoredPeopleAvailabilitySource(InMemoryFaceDataSettingStore(), device, KEY)

        assertEquals(PeopleAvailability.DEVICE_INCAPABLE, source.read())
    }

    @Test
    fun `the in-memory source answers what it was set to`() {
        val source = InMemoryPeopleAvailabilitySource()
        assertEquals(PeopleAvailability.AVAILABLE, source.read())

        source.availability = PeopleAvailability.FACE_DATA_OFF
        assertEquals(PeopleAvailability.FACE_DATA_OFF, source.read())
    }

    private fun state(key: String, capable: Boolean?, unfinishedStarts: Int = 0) =
        FaceCapabilityState(key, capable, unfinishedStarts)

    // Only capabilityState may be called: the disclosure is not an input and a read must not record a start.
    private class ReadOnlyDeviceStore(private val delegate: FaceDeviceStateStore) : FaceDeviceStateStore {
        override fun isDisclosureShown(): Boolean = error("the disclosure must not be read")
        override fun markDisclosureShown() = error("must not write")
        override fun capabilityState(): FaceCapabilityState? = delegate.capabilityState()
        override fun recordCapabilityStart(key: String): Int = error("must not write")
        override fun clearCapabilityStart(key: String) = error("must not write")
        override fun saveCapabilityResult(key: String, capable: Boolean) = error("must not write")
    }

    private companion object {
        const val KEY = "7|1|fingerprint-a"
        const val OTHER_KEY = "8|1|fingerprint-a"
    }
}
