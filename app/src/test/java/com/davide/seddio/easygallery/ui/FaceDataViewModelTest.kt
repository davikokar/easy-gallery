package com.davide.seddio.easygallery.ui

import android.app.Application
import com.davide.seddio.easygallery.data.FACE_PIPELINE_ABI
import com.davide.seddio.easygallery.data.FaceCapabilityChecker
import com.davide.seddio.easygallery.data.FaceCapabilityKey
import com.davide.seddio.easygallery.data.FaceDataDeletion
import com.davide.seddio.easygallery.data.FaceDataSettingStore
import com.davide.seddio.easygallery.data.FaceDataStartupDeletion
import com.davide.seddio.easygallery.data.FaceDeviceStateStore
import com.davide.seddio.easygallery.data.FaceIndexLog
import com.davide.seddio.easygallery.data.FacePipelineUnavailableException
import com.davide.seddio.easygallery.data.FakeFacePipeline
import com.davide.seddio.easygallery.data.FaceSelfTestResult
import com.davide.seddio.easygallery.data.InMemoryFaceDataSettingStore
import com.davide.seddio.easygallery.data.InMemoryFaceDeviceStateStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexStore
import com.davide.seddio.easygallery.data.MediaIndexStore
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Plan 3.6, Task A: the face-data switch, the disclosure and the app-open work, on recording fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class FaceDataViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val events = mutableListOf<String>()
    private val lock = Mutex()
    private val key = FaceCapabilityKey(appVersionCode = 12L, pipelineVersion = 1, buildFingerprint = "fp/1")
    private val setting = RecordingSettingStore(events, InMemoryFaceDataSettingStore(initiallyEnabled = true))
    private val device = RecordingDeviceStore(events, InMemoryFaceDeviceStateStore())
    private val index = RecordingIndexStore(events, lock, InMemoryMediaIndexStore())
    private var memoryDrops = 0
    private var checkerLoads = 0
    private var loaderLockedWhenCalled: Boolean? = null
    private var abis = listOf(FACE_PIPELINE_ABI)
    private var loader: () -> com.davide.seddio.easygallery.data.FacePipeline = {
        checkerLoads += 1
        loaderLockedWhenCalled = lock.isLocked
        FakeFacePipeline()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- the disclosure's condition, one test per term ----

    @Test
    fun `the disclosure is due when every condition holds`() {
        assertTrue(faceDataDisclosureDue(due(), mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = false))
    }

    @Test
    fun `the disclosure is not due without the media permission`() {
        assertFalse(faceDataDisclosureDue(due(), mediaPermissionGranted = false, viewerOpen = false, slideshowOpen = false))
    }

    @Test
    fun `the disclosure waits while the full screen viewer is open`() {
        assertFalse(faceDataDisclosureDue(due(), mediaPermissionGranted = true, viewerOpen = true, slideshowOpen = false))
    }

    @Test
    fun `the disclosure waits while the slideshow is open`() {
        assertFalse(faceDataDisclosureDue(due(), mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = true))
    }

    @Test
    fun `the disclosure is due again once the slideshow has closed`() {
        val state = due()

        assertFalse(faceDataDisclosureDue(state, mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = true))
        assertTrue(faceDataDisclosureDue(state, mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = false))
    }

    @Test
    fun `the disclosure is not due while face data is off`() {
        assertFalse(faceDataDisclosureDue(due().copy(enabled = false), mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = false))
    }

    @Test
    fun `the disclosure is not due on an unknown capability`() {
        val state = due().copy(capability = FaceCapabilityStatus.UNKNOWN)

        assertFalse(faceDataDisclosureDue(state, mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = false))
    }

    @Test
    fun `the disclosure is not due on an incapable device`() {
        val state = due().copy(capability = FaceCapabilityStatus.INCAPABLE)

        assertFalse(faceDataDisclosureDue(state, mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = false))
    }

    @Test
    fun `the disclosure is not due once it has been shown`() {
        assertFalse(faceDataDisclosureDue(due().copy(disclosureShown = true), mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = false))
    }

    @Test
    fun `nothing is due before the stores have been read`() {
        assertFalse(faceDataDisclosureDue(FaceDataUiState(), mediaPermissionGranted = true, viewerOpen = false, slideshowOpen = false))
    }

    // ---- the state is derived from the stores ----

    @Test
    fun `the state reads the setting the capability for the current key and the disclosure from the stores`() {
        recordCapability(capable = true)
        setting.setEnabled(false)
        events.clear()

        val state = createViewModel().uiState.value

        assertEquals(FaceCapabilityStatus.CAPABLE, state.capability)
        assertFalse(state.enabled)
        assertFalse(state.disclosureShown)
    }

    @Test
    fun `an answer recorded for another key is unknown here`() {
        device.saveCapabilityResult(key.copy(appVersionCode = 11L).asStoredKey(), capable = true)

        val viewModel = createViewModel(supportedAbis = listOf(FACE_PIPELINE_ABI), blockCheck = true)

        assertEquals(FaceCapabilityStatus.UNKNOWN, viewModel.uiState.value.capability)
    }

    @Test
    fun `an incapable record reads as incapable`() {
        recordCapability(capable = false)

        assertEquals(FaceCapabilityStatus.INCAPABLE, createViewModel().uiState.value.capability)
    }

    @Test
    fun `a recreated screen derives the disclosure again from the stores`() {
        recordCapability(capable = true)

        val first = createViewModel().uiState.value
        val second = createViewModel().uiState.value

        assertTrue(faceDataDisclosureDue(first, true, false, false))
        assertTrue(faceDataDisclosureDue(second, true, false, false))
    }

    // ---- the disclosure's buttons ----

    @Test
    fun `OK marks the disclosure shown and does nothing else`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        val epochBefore = viewModel.peopleAvailabilityEpoch.value
        events.clear()

        viewModel.acknowledgeDisclosure()

        assertEquals(listOf("mark shown"), events)
        assertTrue(viewModel.uiState.value.disclosureShown)
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals(epochBefore, viewModel.peopleAvailabilityEpoch.value)
        assertFalse(faceDataDisclosureDue(viewModel.uiState.value, true, false, false))
    }

    @Test
    fun `Turn off marks the disclosure shown then switches face data off and refreshes`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        val epochBefore = viewModel.peopleAvailabilityEpoch.value
        events.clear()

        viewModel.turnOffFromDisclosure()

        assertEquals("mark shown", events.first())
        assertTrue(events.contains("persist off"))
        assertTrue(viewModel.uiState.value.disclosureShown)
        assertFalse(viewModel.uiState.value.enabled)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(epochBefore + 1, viewModel.peopleAvailabilityEpoch.value)
        assertFalse(faceDataDisclosureDue(viewModel.uiState.value, true, false, false))
    }

    @Test
    fun `Turn off runs the same ordered switch off without a confirmation`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        events.clear()

        viewModel.turnOffFromDisclosure()

        assertEquals(
            listOf("mark shown", "persist off", "delete locked=true", "drop locked=true"),
            events
        )
    }

    // ---- switching on ----

    @Test
    fun `switching on persists on and refreshes and deletes nothing`() {
        recordCapability(capable = true)
        device.markDisclosureShown()
        setting.setEnabled(false)
        val viewModel = createViewModel()
        val epochBefore = viewModel.peopleAvailabilityEpoch.value
        index.deletions = 0 // the app-open deletion ran, the setting being off
        events.clear()

        viewModel.switchOn()

        assertEquals(listOf("persist on"), events)
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals(epochBefore + 1, viewModel.peopleAvailabilityEpoch.value)
        assertEquals(0, index.deletions)
    }

    @Test
    fun `switching on never marks the disclosure shown`() {
        recordCapability(capable = true)
        setting.setEnabled(false)
        val viewModel = createViewModel()
        events.clear()

        viewModel.switchOn()

        assertFalse("switching on must not use up the disclosure", events.contains("mark shown"))
        assertFalse(device.isDisclosureShown())
        assertFalse(viewModel.uiState.value.disclosureShown)
    }

    @Test
    fun `switching on with the disclosure never shown makes the disclosure due`() {
        recordCapability(capable = true)
        setting.setEnabled(false)
        val viewModel = createViewModel()
        assertFalse(faceDataDisclosureDue(viewModel.uiState.value, true, false, false))

        viewModel.switchOn()

        assertTrue(faceDataDisclosureDue(viewModel.uiState.value, true, false, false))
    }

    @Test
    fun `switching on after the disclosure was shown shows nothing`() {
        recordCapability(capable = true)
        device.markDisclosureShown()
        setting.setEnabled(false)
        val viewModel = createViewModel()

        viewModel.switchOn()

        assertFalse(faceDataDisclosureDue(viewModel.uiState.value, true, false, false))
    }

    // ---- switching off ----

    @Test
    fun `switching off persists then takes the lock then deletes then drops memory then releases then refreshes`() = runTest {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        val refreshes = collectRefreshes(viewModel)
        events.clear()

        viewModel.switchOff()

        assertEquals(
            listOf("persist off", "delete locked=true", "drop locked=true", "refresh locked=false"),
            events
        )
        assertFalse(lock.isLocked)
        refreshes.cancel()
    }

    @Test
    fun `switching off waits for a running pass and has already persisted off while it waits`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        events.clear()
        assertTrue(lock.tryLock())

        viewModel.switchOff()

        assertEquals("persisted before the wait", listOf("persist off"), events)
        assertTrue(viewModel.uiState.value.isDeleting)
        assertEquals(0, index.deletions)

        lock.unlock()

        assertEquals(1, index.deletions)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(lock.isLocked)
    }

    @Test
    fun `a second switch off while one is running is ignored`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        assertTrue(lock.tryLock())

        viewModel.switchOff()
        viewModel.switchOff()
        viewModel.turnOffFromDisclosure()
        lock.unlock()

        assertEquals(1, index.deletions)
        assertEquals(1, events.count { it == "persist off" })
    }

    @Test
    fun `a scrubbed deletion says nothing`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()

        viewModel.switchOff()

        assertFalse(viewModel.uiState.value.cleanupPending)
        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun `a deletion not scrubbed shows the cleanup sentence until it is dismissed`() {
        recordCapability(capable = true)
        index.scrubbed = false
        val viewModel = createViewModel()

        viewModel.switchOff()

        assertTrue(viewModel.uiState.value.cleanupPending)
        assertFalse(viewModel.uiState.value.isDeleting)

        viewModel.dismissCleanupNotice()

        assertFalse(viewModel.uiState.value.cleanupPending)
    }

    @Test
    fun `switching on again clears the cleanup sentence`() {
        recordCapability(capable = true)
        index.scrubbed = false
        val viewModel = createViewModel()
        viewModel.switchOff()

        viewModel.switchOn()

        assertFalse(viewModel.uiState.value.cleanupPending)
    }

    @Test
    fun `a deletion that throws leaves face data off releases the lock and does not claim the data is deleted`() {
        recordCapability(capable = true)
        index.failure = IllegalStateException("disk full")
        val viewModel = createViewModel()
        val epochBefore = viewModel.peopleAvailabilityEpoch.value

        viewModel.switchOff()

        assertFalse(viewModel.uiState.value.enabled)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse("the rows were not deleted, so the cleanup sentence would be false", viewModel.uiState.value.cleanupPending)
        assertFalse(lock.isLocked)
        assertEquals(1, memoryDrops)
        assertEquals(epochBefore + 1, viewModel.peopleAvailabilityEpoch.value)
    }

    @Test
    fun `after a deletion that throws the switch is usable again`() {
        recordCapability(capable = true)
        index.failure = IllegalStateException("disk full")
        val viewModel = createViewModel()
        viewModel.switchOff()
        index.failure = null

        viewModel.switchOff()
        viewModel.switchOn()

        assertEquals(2, index.deletions)
        assertTrue(viewModel.uiState.value.enabled)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.cleanupPending)
    }

    @Test
    fun `a cancelled deletion is not swallowed and still releases the lock`() {
        recordCapability(capable = true)
        index.failure = CancellationException("cancelled")
        val viewModel = createViewModel()

        viewModel.switchOff()

        assertFalse(lock.isLocked)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun `refresh follows each direction of the switch`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        val start = viewModel.peopleAvailabilityEpoch.value

        viewModel.switchOff()
        assertEquals(start + 1, viewModel.peopleAvailabilityEpoch.value)

        viewModel.switchOn()
        assertEquals(start + 2, viewModel.peopleAvailabilityEpoch.value)
    }

    @Test
    fun `resuming reads the stores again and refreshes`() {
        recordCapability(capable = true)
        val viewModel = createViewModel()
        val start = viewModel.peopleAvailabilityEpoch.value
        setting.setEnabled(false)
        assertTrue(viewModel.uiState.value.enabled)

        viewModel.onResume()

        assertFalse(viewModel.uiState.value.enabled)
        assertEquals(start + 1, viewModel.peopleAvailabilityEpoch.value)
    }

    // ---- app open: the capability check ----

    @Test
    fun `a stored answer is read without the lock and without the checker`() {
        recordCapability(capable = true)
        assertTrue(lock.tryLock())

        val viewModel = createViewModel()

        assertEquals(FaceCapabilityStatus.CAPABLE, viewModel.uiState.value.capability)
        assertEquals(0, checkerLoads)
        assertEquals(1L, viewModel.peopleAvailabilityEpoch.value)
        lock.unlock()
    }

    @Test
    fun `with no answer the check waits for the lock and runs under it`() {
        assertTrue(lock.tryLock())

        val viewModel = createViewModel()

        assertEquals(0, checkerLoads)
        assertEquals(FaceCapabilityStatus.UNKNOWN, viewModel.uiState.value.capability)
        assertEquals(0L, viewModel.peopleAvailabilityEpoch.value)

        lock.unlock()

        assertEquals(1, checkerLoads)
        assertEquals("the models load while the lock is held", true, loaderLockedWhenCalled)
        assertEquals(FaceCapabilityStatus.CAPABLE, viewModel.uiState.value.capability)
        assertEquals(1L, viewModel.peopleAvailabilityEpoch.value)
        assertFalse(lock.isLocked)
    }

    @Test
    fun `with no arm64 ABI incapable is recorded durably without the lock and without loading the models`() {
        abis = listOf("armeabi-v7a", "x86_64")
        assertTrue(lock.tryLock())

        val viewModel = createViewModel()

        assertEquals(0, checkerLoads)
        assertEquals(FaceCapabilityStatus.INCAPABLE, viewModel.uiState.value.capability)
        assertEquals(false, device.capabilityState()?.capable)
        assertEquals(key.asStoredKey(), device.capabilityState()?.key)
        assertEquals(1L, viewModel.peopleAvailabilityEpoch.value)
        lock.unlock()
    }

    @Test
    fun `running out of memory records nothing and leaves the capability unknown`() {
        loader = { checkerLoads += 1; throw OutOfMemoryError("short of memory") }

        val viewModel = createViewModel()

        assertEquals(1, checkerLoads)
        assertNull(device.capabilityState()?.capable)
        assertEquals(FaceCapabilityStatus.UNKNOWN, viewModel.uiState.value.capability)
        assertFalse(lock.isLocked)
    }

    @Test
    fun `an error the checker does not catch does not crash the app and the refresh still happens`() {
        loader = { checkerLoads += 1; throw StackOverflowError() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val uncaught = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }

        val viewModel = try {
            createViewModel()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }

        assertEquals(1, checkerLoads)
        assertEquals("nothing reaches the thread's handler", emptyList<Throwable>(), uncaught)
        assertEquals(FaceCapabilityStatus.UNKNOWN, viewModel.uiState.value.capability)
        assertEquals(1L, viewModel.peopleAvailabilityEpoch.value)
        assertFalse(lock.isLocked)
    }

    @Test
    fun `a build without the pipeline on a device that lists arm64 stays unknown`() {
        loader = { checkerLoads += 1; throw FacePipelineUnavailableException() }

        val viewModel = createViewModel()

        assertNull(device.capabilityState()?.capable)
        assertEquals(FaceCapabilityStatus.UNKNOWN, viewModel.uiState.value.capability)
    }

    @Test
    fun `a failed self test records incapable`() {
        loader = {
            checkerLoads += 1
            FakeFacePipeline().also { it.onSelfTest = { FaceSelfTestResult(detectionOutputValid = false, embedding = null) } }
        }

        val viewModel = createViewModel()

        assertEquals(FaceCapabilityStatus.INCAPABLE, viewModel.uiState.value.capability)
    }

    @Test
    fun `the check runs whatever the setting says`() {
        setting.setEnabled(false)

        val viewModel = createViewModel()

        assertEquals(1, checkerLoads)
        assertEquals(FaceCapabilityStatus.CAPABLE, viewModel.uiState.value.capability)
    }

    // ---- app open: the start-up deletion ----

    @Test
    fun `the start up deletion runs under the lock while the setting is off`() {
        recordCapability(capable = true)
        setting.setEnabled(false)
        events.clear()

        createViewModel()

        assertEquals(listOf("delete locked=true"), events)
    }

    @Test
    fun `the start up deletion does not run and does not wait for the lock while the setting is on`() {
        recordCapability(capable = true)
        assertTrue(lock.tryLock())

        val viewModel = createViewModel()

        assertEquals(0, index.deletions)
        assertEquals(1L, viewModel.peopleAvailabilityEpoch.value)
        lock.unlock()
    }

    @Test
    fun `the start up deletion waits for a running pass and the refresh follows it`() {
        recordCapability(capable = true)
        setting.setEnabled(false)
        assertTrue(lock.tryLock())

        val viewModel = createViewModel()

        assertEquals(0, index.deletions)
        assertEquals(0L, viewModel.peopleAvailabilityEpoch.value)

        lock.unlock()

        assertEquals(1, index.deletions)
        assertEquals(1L, viewModel.peopleAvailabilityEpoch.value)
    }

    @Test
    fun `an unfinished check does not stop the start up deletion from running`() {
        setting.setEnabled(false)
        loader = { checkerLoads += 1; throw OutOfMemoryError() }

        createViewModel()

        assertEquals(1, index.deletions)
    }

    // ---- fixtures ----

    private fun due() = FaceDataUiState(
        capability = FaceCapabilityStatus.CAPABLE,
        enabled = true,
        disclosureShown = false
    )

    private fun recordCapability(capable: Boolean) {
        device.saveCapabilityResult(key.asStoredKey(), capable)
    }

    // blockCheck holds the lock so an unanswered check stays unfinished and the state it leaves can be read.
    private fun createViewModel(supportedAbis: List<String> = abis, blockCheck: Boolean = false): FaceDataViewModel {
        if (blockCheck) assertTrue(lock.tryLock())
        return FaceDataViewModel(
            application = mockk<Application>(relaxed = true),
            settingStore = setting,
            deviceStateStore = device,
            mediaIndexStore = index,
            capabilityKey = { key.asStoredKey() },
            supportedAbis = supportedAbis,
            createCapabilityChecker = { FaceCapabilityChecker(device, key, supportedAbis, { loader() }) },
            createStartupDeletion = { FaceDataStartupDeletion(index, setting, FaceIndexLog { }) },
            faceIndexLock = lock,
            dropMemory = { events += "drop locked=${lock.isLocked}"; memoryDrops += 1 },
            ioDispatcher = dispatcher
        )
    }

    private fun kotlinx.coroutines.test.TestScope.collectRefreshes(viewModel: FaceDataViewModel) =
        backgroundScope.launch(dispatcher) {
            viewModel.peopleAvailabilityEpoch.drop(1).collect { events += "refresh locked=${lock.isLocked}" }
        }

    private class RecordingSettingStore(
        private val events: MutableList<String>,
        private val inner: FaceDataSettingStore
    ) : FaceDataSettingStore {
        override fun isEnabled(): Boolean = inner.isEnabled()

        override fun setEnabled(enabled: Boolean) {
            events += if (enabled) "persist on" else "persist off"
            inner.setEnabled(enabled)
        }
    }

    private class RecordingDeviceStore(
        private val events: MutableList<String>,
        private val inner: FaceDeviceStateStore
    ) : FaceDeviceStateStore by inner {
        override fun markDisclosureShown() {
            events += "mark shown"
            inner.markDisclosureShown()
        }
    }

    private class RecordingIndexStore(
        private val events: MutableList<String>,
        private val lock: Mutex,
        private val inner: InMemoryMediaIndexStore
    ) : MediaIndexStore by inner {
        var deletions = 0
        var scrubbed = true
        var failure: Throwable? = null

        override suspend fun deleteAllFaceData(): FaceDataDeletion {
            deletions += 1
            events += "delete locked=${lock.isLocked}"
            failure?.let { throw it }
            return inner.deleteAllFaceData().copy(scrubbed = scrubbed)
        }
    }
}
