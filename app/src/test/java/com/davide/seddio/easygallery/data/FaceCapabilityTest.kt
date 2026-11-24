package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The capability check's persistence and its three outcomes (plan 3.3, Task 1, criterion 7). */
class FaceCapabilityTest {

    private val store = InMemoryFaceDeviceStateStore()
    private val key = FaceCapabilityKey(appVersionCode = 12L, pipelineVersion = FACE_PIPELINE_VERSION, buildFingerprint = "fp/1")
    private var loads = 0
    private val pipeline = FakeFacePipeline()
    private val arm64Abis = listOf(FACE_PIPELINE_ABI, "armeabi-v7a")

    @Test
    fun `a capable device is recorded and the next call reuses the record without loading`() {
        val checker = checker()

        assertTrue(checker.isCapable())
        assertTrue(checker.isCapable())
        assertTrue(checker().isCapable())

        assertEquals(1, loads)
        assertEquals(FaceCapabilityState(key.asStoredKey(), capable = true, unfinishedStarts = 0), store.capabilityState())
    }

    @Test
    fun `an incapable result is recorded and reused as well`() {
        pipeline.onSelfTest = { FaceSelfTestResult(detectionOutputValid = false, embedding = floatArrayOf(1f)) }

        assertFalse(checker().isCapable())
        assertFalse(checker().isCapable())

        assertEquals(1, loads)
        assertEquals(false, store.capabilityState()!!.capable)
    }

    @Test
    fun `a new app version pipeline version or build fingerprint is asked again`() {
        assertTrue(checker().isCapable())

        assertTrue(checker(key.copy(appVersionCode = 13L)).isCapable())
        assertTrue(checker(key.copy(pipelineVersion = key.pipelineVersion + 1)).isCapable())
        assertTrue(checker(key.copy(buildFingerprint = "fp/2")).isCapable())

        assertEquals(4, loads)
    }

    @Test
    fun `the three parts of the key each make a different stored key`() {
        val keys = listOf(
            key,
            key.copy(appVersionCode = 13L),
            key.copy(pipelineVersion = 2),
            key.copy(buildFingerprint = "fp/2")
        ).map { it.asStoredKey() }

        assertEquals(4, keys.toSet().size)
    }

    @Test
    fun `a start is recorded before the check runs`() {
        var startsWhileChecking: Int? = null
        pipeline.onSelfTest = {
            startsWhileChecking = store.capabilityState()?.unfinishedStarts
            validSelfTest()
        }

        checker().isCapable()

        assertEquals(1, startsWhileChecking)
    }

    @Test
    fun `two unfinished starts for one key make it incapable without a third check`() {
        val stored = key.asStoredKey()
        store.recordCapabilityStart(stored)
        store.recordCapabilityStart(stored)

        assertFalse(checker().isCapable())
        assertFalse(checker().isCapable())

        assertEquals(0, loads)
        assertEquals(FaceCapabilityState(stored, capable = false, unfinishedStarts = 0), store.capabilityState())
    }

    @Test
    fun `one unfinished start is not yet enough to give up`() {
        store.recordCapabilityStart(key.asStoredKey())

        assertTrue(checker().isCapable())

        assertEquals(1, loads)
        assertEquals(true, store.capabilityState()!!.capable)
    }

    @Test
    fun `unfinished starts under another key do not count against this one`() {
        val other = key.copy(buildFingerprint = "fp/other").asStoredKey()
        store.recordCapabilityStart(other)
        store.recordCapabilityStart(other)

        assertTrue(checker().isCapable())

        assertEquals(1, loads)
    }

    @Test
    fun `a library or a model that does not load is recorded incapable and not asked again`() {
        val failing = FaceCapabilityChecker(store, key, arm64Abis, { loads += 1; throw IllegalStateException("model missing") })

        assertFalse(failing.isCapable())
        assertFalse(failing.isCapable())

        assertEquals(1, loads)
        assertEquals(FaceCapabilityState(key.asStoredKey(), capable = false, unfinishedStarts = 0), store.capabilityState())
    }

    @Test
    fun `a native library that fails to link is recorded incapable`() {
        val failing = FaceCapabilityChecker(store, key, arm64Abis, { loads += 1; throw UnsatisfiedLinkError("libopencv_java4.so") })

        assertFalse(failing.isCapable())
        assertFalse(failing.isCapable())

        assertEquals(1, loads)
        assertEquals(false, store.capabilityState()!!.capable)
    }

    @Test
    fun `a self test that throws is recorded incapable`() {
        pipeline.onSelfTest = { throw IllegalArgumentException("bad shape") }

        assertFalse(checker().isCapable())
        assertFalse(checker().isCapable())

        assertEquals(1, loads)
        assertEquals(false, store.capabilityState()!!.capable)
    }

    @Test
    fun `a self test with a wrong shape a bad value or a zero norm is incapable`() {
        val outcomes = listOf(
            FaceSelfTestResult(detectionOutputValid = false, embedding = floatArrayOf(1f, 2f)),
            FaceSelfTestResult(detectionOutputValid = true, embedding = null),
            FaceSelfTestResult(detectionOutputValid = true, embedding = floatArrayOf()),
            FaceSelfTestResult(detectionOutputValid = true, embedding = floatArrayOf(1f, Float.NaN)),
            FaceSelfTestResult(detectionOutputValid = true, embedding = floatArrayOf(Float.POSITIVE_INFINITY)),
            FaceSelfTestResult(detectionOutputValid = true, embedding = floatArrayOf(0f, 0f, 0f))
        )

        outcomes.forEachIndexed { index, outcome ->
            val fresh = InMemoryFaceDeviceStateStore()
            val result = FaceCapabilityChecker(fresh, key, arm64Abis, { pipeline.also { it.onSelfTest = { outcome } } }).isCapable()
            assertFalse("outcome #$index", result)
            assertEquals("outcome #$index is recorded", false, fresh.capabilityState()!!.capable)
        }
    }

    @Test
    fun `running out of memory is not recorded and the check is asked again`() {
        var attempt = 0
        val flaky = FaceCapabilityChecker(store, key, arm64Abis, {
            attempt += 1
            if (attempt == 1) throw OutOfMemoryError("short of memory just now")
            pipeline
        })

        assertFalse(flaky.isCapable())
        val afterFirst = store.capabilityState()
        assertNull("no result is recorded", afterFirst?.capable)
        assertEquals("the start is taken back", 0, afterFirst?.unfinishedStarts)

        assertTrue(flaky.isCapable())
        assertEquals(2, attempt)
    }

    @Test
    fun `an out of memory check repeated many times never turns into incapable`() {
        val failing = FaceCapabilityChecker(store, key, arm64Abis, { throw OutOfMemoryError() })

        repeat(FACE_CAPABILITY_MAX_UNFINISHED_STARTS + 3) { assertFalse(failing.isCapable()) }

        assertNull(store.capabilityState()?.capable)
        assertTrue(checker().isCapable())
    }

    @Test
    fun `a build with no pipeline is incapable and records nothing a later build could inherit`() {
        val unavailable = FaceCapabilityChecker(store, key, arm64Abis, { throw FacePipelineUnavailableException() })

        assertFalse(unavailable.isCapable())
        assertFalse(unavailable.isCapable())

        assertNull(store.capabilityState()?.capable)
        assertEquals(0, store.capabilityState()?.unfinishedStarts)
        assertTrue(checker().isCapable())
    }

    @Test
    fun `a device that lists no arm64 ABI is recorded incapable under the current key and nothing is loaded`() {
        val noArm64 = FaceCapabilityChecker(store, key, listOf("armeabi-v7a", "x86_64"), { loads += 1; pipeline })

        assertFalse(noArm64.isCapable())
        assertFalse(noArm64.isCapable())

        assertEquals(0, loads)
        assertEquals(FaceCapabilityState(key.asStoredKey(), capable = false, unfinishedStarts = 0), store.capabilityState())
    }

    @Test
    fun `an empty ABI list is incapable too`() {
        val none = FaceCapabilityChecker(store, key, emptyList(), { loads += 1; pipeline })

        assertFalse(none.isCapable())

        assertEquals(0, loads)
        assertEquals(false, store.capabilityState()!!.capable)
    }

    @Test
    fun `the no arm64 answer belongs to its key so a build that ships other ABIs is asked afresh`() {
        FaceCapabilityChecker(store, key, listOf("x86_64"), { loads += 1; pipeline }).isCapable()

        val laterBuild = key.copy(appVersionCode = key.appVersionCode + 1)

        assertTrue(checker(laterBuild).isCapable())
        assertEquals(1, loads)
    }

    @Test
    fun `an ABI list with arm64 anywhere in it runs the self test as before`() {
        val checker = FaceCapabilityChecker(store, key, listOf("armeabi-v7a", FACE_PIPELINE_ABI), { loads += 1; pipeline })

        assertTrue(checker.isCapable())

        assertEquals(1, loads)
    }

    @Test
    fun `only the pipeline's own ABI counts as supported`() {
        assertTrue(supportsFacePipelineAbi(listOf(FACE_PIPELINE_ABI)))
        assertFalse(supportsFacePipelineAbi(listOf("armeabi-v7a", "x86", "x86_64")))
        assertFalse(supportsFacePipelineAbi(emptyList()))
    }

    @Test
    fun `the pipeline is closed after the self test whatever it found`() {
        checker().isCapable()
        assertEquals(1, pipeline.closeCount)

        val failing = FakeFacePipeline().apply { onSelfTest = { throw IllegalStateException("no") } }
        FaceCapabilityChecker(InMemoryFaceDeviceStateStore(), key, arm64Abis, { failing }).isCapable()
        assertEquals(1, failing.closeCount)
    }

    private fun checker(forKey: FaceCapabilityKey = key) =
        FaceCapabilityChecker(store, forKey, arm64Abis, { loads += 1; pipeline })
}
