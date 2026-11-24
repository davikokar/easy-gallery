package com.davide.seddio.easygallery.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.data.FaceCapabilityChecker
import com.davide.seddio.easygallery.data.FaceDataSettingStore
import com.davide.seddio.easygallery.data.FaceDataStartupDeletion
import com.davide.seddio.easygallery.data.FaceDeviceStateStore
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.supportsFacePipelineAbi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the stored capability record says for the current key; unknown is hidden here and available to evaluation (ADR-0024 Am. 1 §5). */
enum class FaceCapabilityStatus { UNKNOWN, CAPABLE, INCAPABLE }

/**
 * Everything the face-data surfaces read, derived from the stores and never remembered here, so a recreated Activity
 * derives the disclosure again. [disclosureShown] starts true so nothing can show before the stores have been read.
 */
data class FaceDataUiState(
    val capability: FaceCapabilityStatus = FaceCapabilityStatus.UNKNOWN,
    val enabled: Boolean = true,
    val disclosureShown: Boolean = true,
    val isDeleting: Boolean = false,
    /** A deletion whose files could not be shown to be scrubbed; transient, cleared when Settings is left. */
    val cleanupPending: Boolean = false
)

/** The disclosure's whole condition (ADR-0024 Am. 1 §5, Am. 2): neither full-screen surface is covered, and nothing else may be missing. */
fun faceDataDisclosureDue(
    state: FaceDataUiState,
    mediaPermissionGranted: Boolean,
    viewerOpen: Boolean,
    slideshowOpen: Boolean
): Boolean =
    mediaPermissionGranted &&
        !viewerOpen &&
        !slideshowOpen &&
        state.enabled &&
        state.capability == FaceCapabilityStatus.CAPABLE &&
        !state.disclosureShown

/**
 * The face-data switch, the disclosure and the app-open work (ADR-0024 §3 and Amendment 1). Nothing here logs, and no
 * state names a photo, a person or a count of faces.
 */
class FaceDataViewModel @JvmOverloads constructor(
    application: Application,
    private val settingStore: FaceDataSettingStore = (application as EasyGalleryApp).faceDataSettingStore,
    private val deviceStateStore: FaceDeviceStateStore = (application as EasyGalleryApp).faceDeviceStateStore,
    private val mediaIndexStore: MediaIndexStore = (application as EasyGalleryApp).mediaIndexStore,
    private val capabilityKey: () -> String = { (application as EasyGalleryApp).faceCapabilityKey().asStoredKey() },
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val createCapabilityChecker: () -> FaceCapabilityChecker =
        { (application as EasyGalleryApp).createFaceCapabilityChecker() },
    private val createStartupDeletion: () -> FaceDataStartupDeletion =
        { (application as EasyGalleryApp).createFaceDataStartupDeletion() },
    private val faceIndexLock: Mutex = (application as EasyGalleryApp).faceIndexLock,
    private val dropMemory: () -> Unit = { SingletonImageLoader.get(application).memoryCache?.clear() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FaceDataUiState())
    val uiState: StateFlow<FaceDataUiState> = _uiState.asStateFlow()

    // The albums' people availability is re-read each time this moves; a state flow, so a late collector still sees it.
    private val _peopleAvailabilityEpoch = MutableStateFlow(0L)
    val peopleAvailabilityEpoch: StateFlow<Long> = _peopleAvailabilityEpoch.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) { runAppOpenWork() }
    }

    /** Re-reads the stores, which another surface may have changed, and asks the albums to re-read availability. */
    fun onResume() {
        viewModelScope.launch(ioDispatcher) {
            publishStores()
            refreshPeopleAvailability()
        }
    }

    /** Persists on and nothing else: the disclosure is not marked shown, so its condition decides (Am. 1 §2, option A). */
    fun switchOn() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch(ioDispatcher) {
            settingStore.setEnabled(true)
            _uiState.update { it.copy(cleanupPending = false) }
            publishStores()
            refreshPeopleAvailability()
        }
    }

    /** The confirmed switch-off; the confirmation itself belongs to the screen, where Cancel changes nothing. */
    fun switchOff() {
        if (!beginDeleting()) return
        viewModelScope.launch(ioDispatcher) { runSwitchOff() }
    }

    /** The disclosure's OK: marks it shown and starts no run. */
    fun acknowledgeDisclosure() {
        viewModelScope.launch(ioDispatcher) {
            deviceStateStore.markDisclosureShown()
            publishStores()
        }
    }

    /** The disclosure's Turn off: shown, then the switch-off below without its confirmation, since nothing can exist yet. */
    fun turnOffFromDisclosure() {
        if (!beginDeleting()) return
        viewModelScope.launch(ioDispatcher) {
            deviceStateStore.markDisclosureShown()
            runSwitchOff()
        }
    }

    fun dismissCleanupNotice() {
        _uiState.update { it.copy(cleanupPending = false) }
    }

    private suspend fun runAppOpenWork() {
        publishStores()
        try {
            ensureCapabilityAnswer()
            publishStores()
            if (!settingStore.isEnabled()) {
                faceIndexLock.withLock { createStartupDeletion().run() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Launch work in a scope with no handler: an Error would end the process, and the worker's run repeats it.
        } finally {
            refreshPeopleAvailability()
        }
    }

    // The lock only when the checker will load the models: the worker takes it before its own check, and two checks at
    // once load them twice. With no arm64 ABI nothing loads, and the answer is recorded without it.
    private suspend fun ensureCapabilityAnswer() {
        val key = capabilityKey()
        val record = deviceStateStore.capabilityState()
        if (record != null && record.key == key && record.capable != null) return

        if (!supportsFacePipelineAbi(supportedAbis)) {
            createCapabilityChecker().isCapable()
            return
        }
        faceIndexLock.withLock { createCapabilityChecker().isCapable() }
    }

    // Persist off, wait for any running pass, delete, drop what memory holds, release, then re-read (Am. 1 §4).
    private suspend fun runSwitchOff() {
        var cleanupPending = false
        try {
            settingStore.setEnabled(false)
            publishStores()
            val deletion = faceIndexLock.withLock {
                try {
                    mediaIndexStore.deleteAllFaceData()
                } finally {
                    dropMemory()
                }
            }
            cleanupPending = !deletion.scrubbed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The transaction rolled back, so no sentence: the setting is off and the start-up deletion repeats the work.
        } finally {
            _uiState.update { it.copy(isDeleting = false, cleanupPending = cleanupPending) }
            publishStores()
            refreshPeopleAvailability()
        }
    }

    private fun beginDeleting(): Boolean {
        while (true) {
            val current = _uiState.value
            if (current.isDeleting) return false
            if (_uiState.compareAndSet(current, current.copy(isDeleting = true, cleanupPending = false))) return true
        }
    }

    private fun publishStores() {
        val key = capabilityKey()
        val record = deviceStateStore.capabilityState()?.takeIf { it.key == key }
        val capability = when (record?.capable) {
            true -> FaceCapabilityStatus.CAPABLE
            false -> FaceCapabilityStatus.INCAPABLE
            null -> FaceCapabilityStatus.UNKNOWN
        }
        val enabled = settingStore.isEnabled()
        val shown = deviceStateStore.isDisclosureShown()
        _uiState.update { it.copy(capability = capability, enabled = enabled, disclosureShown = shown) }
    }

    private fun refreshPeopleAvailability() {
        _peopleAvailabilityEpoch.update { it + 1 }
    }
}
