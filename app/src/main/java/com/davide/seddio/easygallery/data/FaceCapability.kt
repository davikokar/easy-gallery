package com.davide.seddio.easygallery.data

// Pure Kotlin, no android.* imports (ADR-0023 §3). The app version code and build fingerprint come in from the caller.

/** What a recorded capability answer is for: another build, pipeline or device is another question. */
data class FaceCapabilityKey(
    val appVersionCode: Long,
    val pipelineVersion: Int,
    val buildFingerprint: String
) {
    fun asStoredKey(): String = "$appVersionCode|$pipelineVersion|$buildFingerprint"
}

/** The check began this many times and never finished before it is recorded incapable (ADR-0023 §3, plan 3.3 issue 2). */
const val FACE_CAPABILITY_MAX_UNFINISHED_STARTS = 2

/** The one ABI the face pipeline ships for (ADR-0023 §2). */
const val FACE_PIPELINE_ABI = "arm64-v8a"

/** False when the device lists no ABI the pipeline ships for, so there is nothing to load or self-test (ADR-0024 Am. 1 §3). */
fun supportsFacePipelineAbi(supportedAbis: List<String>): Boolean = FACE_PIPELINE_ABI in supportedAbis

/**
 * Whether this device can run the pipeline: it loads, and a self-test on a synthetic input returns detection and an
 * embedding of the expected shape, with finite values and a non-zero norm, checked against no stored reference. The
 * answer is persisted per [key]. A start is recorded before the check runs, so one that ends the process twice is
 * recorded incapable on the third look. With no [supportedAbis] entry the pipeline ships for, incapable is recorded under
 * the key at once and nothing is loaded. Blocking: never on the main thread.
 */
class FaceCapabilityChecker(
    private val store: FaceDeviceStateStore,
    private val key: FaceCapabilityKey,
    private val supportedAbis: List<String>,
    private val pipelineLoader: () -> FacePipeline
) {

    fun isCapable(): Boolean {
        val stored = key.asStoredKey()
        val state = store.capabilityState()?.takeIf { it.key == stored }
        state?.capable?.let { return it }
        if (!supportsFacePipelineAbi(supportedAbis)) {
            store.saveCapabilityResult(stored, capable = false)
            return false
        }
        if (state != null && state.unfinishedStarts >= FACE_CAPABILITY_MAX_UNFINISHED_STARTS) {
            store.saveCapabilityResult(stored, capable = false)
            return false
        }

        store.recordCapabilityStart(stored)
        return try {
            val capable = pipelineLoader().use { selfTestPasses(it.selfTest()) }
            store.saveCapabilityResult(stored, capable)
            capable
        } catch (_: FacePipelineUnavailableException) {
            // This build has no pipeline; the next build must be asked afresh.
            store.clearCapabilityStart(stored)
            false
        } catch (_: OutOfMemoryError) {
            // The phone was short of memory just now, which says nothing about the pipeline.
            store.clearCapabilityStart(stored)
            false
        } catch (_: Exception) {
            // A library or model that does not load, or a self-test that throws, will do so again.
            store.saveCapabilityResult(stored, capable = false)
            false
        } catch (_: LinkageError) {
            store.saveCapabilityResult(stored, capable = false)
            false
        }
    }

    private fun selfTestPasses(result: FaceSelfTestResult): Boolean {
        if (!result.detectionOutputValid) return false
        val values = result.embedding ?: return false
        if (values.isEmpty() || values.any { !it.isFinite() }) return false
        return values.sumOf { it.toDouble() * it.toDouble() } > 0.0
    }
}
