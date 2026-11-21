package com.davide.seddio.easygallery.data

/**
 * Plan 3.5, *Availability*. Face data off first, because it is the one the user can fix; then an answer recorded for
 * [currentKey] saying incapable. No record, a record for another key, or a check begun and never finished is unknown,
 * and unknown is available: the check loads the pipeline and evaluation never runs it. The disclosure is not an input.
 */
fun peopleAvailabilityOf(
    faceDataEnabled: Boolean,
    capability: FaceCapabilityState?,
    currentKey: String
): PeopleAvailability {
    if (!faceDataEnabled) return PeopleAvailability.FACE_DATA_OFF
    if (capability != null && capability.key == currentKey && capability.capable == false) {
        return PeopleAvailability.DEVICE_INCAPABLE
    }
    return PeopleAvailability.AVAILABLE
}

/** Reads the two persisted facts; cheap, never loads the pipeline. */
interface PeopleAvailabilitySource {
    fun read(): PeopleAvailability
}

class StoredPeopleAvailabilitySource(
    private val settingStore: FaceDataSettingStore,
    private val deviceStateStore: FaceDeviceStateStore,
    private val currentKey: String
) : PeopleAvailabilitySource {
    override fun read(): PeopleAvailability {
        return peopleAvailabilityOf(settingStore.isEnabled(), deviceStateStore.capabilityState(), currentKey)
    }
}

class InMemoryPeopleAvailabilitySource(
    initial: PeopleAvailability = PeopleAvailability.AVAILABLE
) : PeopleAvailabilitySource {
    @Volatile
    var availability: PeopleAvailability = initial

    override fun read(): PeopleAvailability = availability
}
