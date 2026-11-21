package com.davide.seddio.easygallery.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Pure Kotlin, no android.* imports, so it runs on a plain JVM (ADR-0023 §4). Everything the pass touches that is face
// data (a URI, a box, an embedding) stays out of logs, exception messages and toString (ADR-0024 §6).

/**
 * Names the whole pipeline (ADR-0023 §1) and stamps every Tier C state and face. All inside this one version: the
 * decode to a long side of at most [FACE_DECODE_LONG_SIDE] px, upright and never upscaled; YuNet on a
 * [FACE_DETECT_LONG_SIDE] px long-side copy at score [FACE_SCORE_THRESHOLD], its NMS threshold and top-K left at
 * OpenCV's `FaceDetectorYN` defaults (0.3 and 5000, checked on a device against the pinned build); SFace `alignCrop`
 * and `feature` on the decoded image; the [FACE_MIN_SIDE_PIXELS] px cut-off; the OpenCV build
 * ([FACE_PIPELINE_OPENCV_VERSION]); and the two model files. Any change to any of these bumps it.
 */
const val FACE_PIPELINE_VERSION = 1

/**
 * The OpenCV build the pipeline was measured and is stamped with: another build moves boxes and would mix embeddings.
 * `FacePipelineVersionTest` fails unless `opencv` in `gradle/libs.versions.toml` equals it.
 */
const val FACE_PIPELINE_OPENCV_VERSION = "4.13.0"

const val FACE_DETECTOR_ASSET = "face/face_detection_yunet_2023mar.onnx"
const val FACE_RECOGNISER_ASSET = "face/face_recognition_sface_2021dec.onnx"

const val FACE_DECODE_LONG_SIDE = 1280
const val FACE_DETECT_LONG_SIDE = 640
const val FACE_SCORE_THRESHOLD = 0.7f

/** Faces narrower or shorter than this in the decoded image are dropped, not stored (decision 46). */
const val FACE_MIN_SIDE_PIXELS = 40

/** An image is decoded at most this many times under one pipeline version (ADR-0018 Amendment 7 §1). */
const val FACE_ATTEMPT_BOUND = 3

/** No image starts later than this after the worker began; WorkManager stops a worker at about ten minutes. */
const val FACE_PASS_BUDGET_MILLIS = 7L * 60L * 1000L

const val FACE_PASS_PAGE_SIZE = 50

/** The only message that leaves the face path when a run fails (ADR-0024 §6). */
const val FACE_INDEX_FAILURE_MESSAGE = "Face indexing failed"

/** A face as the recogniser found it: pixel edges in the decoded, upright image. */
class RawFace(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val embedding: FaceEmbedding
) {
    override fun toString(): String = "RawFace(redacted)"
}

/** The decoded image's size, which the boxes are in, and what was found in it. */
class FacePipelineOutput(
    val imageWidth: Int,
    val imageHeight: Int,
    val faces: List<RawFace>
) {
    override fun toString(): String = "FacePipelineOutput(redacted)"
}

/** Detection and embedding ran on a synthetic input; [embedding] is null when no embedding came out. */
class FaceSelfTestResult(
    val detectionOutputValid: Boolean,
    val embedding: FloatArray?
) {
    override fun toString(): String = "FaceSelfTestResult(redacted)"
}

enum class FacePipelineFailure { DECODE, INFERENCE }

/**
 * A failure of this image, not of the file's availability: it decoded to nothing, or OpenCV refused it. The pass writes
 * it as failed at once and never reads [message], which may name the image.
 */
class FacePipelineException(val failure: FacePipelineFailure, message: String? = null) : Exception(message)

/** This build carries no pipeline: incapable, and not a fact about the device, so it is never persisted. */
class FacePipelineUnavailableException : Exception("No face pipeline in this build")

/**
 * Upright decode, detection and embedding of one image, with the models held in memory. [analyse] throws
 * [FacePipelineException] for a decode or inference failure, `IOException` for a file that cannot be opened and
 * `SecurityException` for a refused read; every other throwable is the caller's to propagate. Blocking.
 */
interface FacePipeline : AutoCloseable {
    fun analyse(mediaUri: String): FacePipelineOutput

    fun selfTest(): FaceSelfTestResult
}

/** One line at a time, from [FaceIndexLogLines] only, so a test can hold every line to a fixed set. */
fun interface FaceIndexLog {
    fun line(text: String)
}

enum class FaceIndexPassStop { COMPLETED, SETTING_OFF, BUDGET, PERMISSION }

enum class FaceIndexNotRunReason { SETTING_OFF, INCAPABLE, DISCLOSURE_NOT_SHOWN, BUSY }

data class FaceIndexPassResult(
    val indexedImages: Int,
    val failedImages: Int,
    val skippedImages: Int,
    val stop: FaceIndexPassStop
)

/** Counts of images and fixed words, nothing else (ADR-0024 §6). */
object FaceIndexLogLines {
    const val PASS_FAILED = "face pass failed"
    const val DELETION_FAILED = "face data deletion failed"

    fun passFinished(result: FaceIndexPassResult): String =
        "face pass finished images=${result.indexedImages + result.failedImages} indexed=${result.indexedImages} " +
            "failed=${result.failedImages} skipped=${result.skippedImages} stop=${result.stop.name}"

    fun passNotRun(reason: FaceIndexNotRunReason): String = "face pass not run reason=${reason.name}"

    fun capabilityChecked(capable: Boolean): String = "face capability checked capable=$capable"

    fun deletionRan(scrubbed: Boolean): String = "face data deletion ran scrubbed=$scrubbed"

    /** A fixed stop name only: no count of faces or groups, no id, no time (plan 3.4, *Reporting*). */
    fun groupingFinished(stop: FaceGroupingStop): String = "face grouping finished stop=${stop.name}"
}

/**
 * The box as fractions of the decoded image, clamped to it, or null for a face that is not stored: narrower or shorter
 * than [FACE_MIN_SIDE_PIXELS] as detected (decision 46), or empty once clamped. The frame is the upright decode's, the
 * one ADR-0022 §1 stores anchors in.
 */
internal fun faceDetectionOf(face: RawFace, imageWidth: Int, imageHeight: Int): FaceDetection? {
    if (imageWidth <= 0 || imageHeight <= 0) return null
    if (face.right - face.left < FACE_MIN_SIDE_PIXELS || face.bottom - face.top < FACE_MIN_SIDE_PIXELS) return null

    val width = imageWidth.toFloat()
    val height = imageHeight.toFloat()
    val left = face.left.coerceIn(0f, width) / width
    val right = face.right.coerceIn(0f, width) / width
    val top = face.top.coerceIn(0f, height) / height
    val bottom = face.bottom.coerceIn(0f, height) / height
    // Written as a negated test so that NaN, which compares false, is dropped too.
    if (!(left < right && top < bottom)) return null
    return FaceDetection(FaceBox(left, top, right, bottom), face.embedding)
}

/**
 * The Tier C pass (ADR-0023 §4): detects and embeds the faces of images that have no state at
 * [FACE_PIPELINE_VERSION], one image at a time. It records an attempt before each decode, writes once per image, checks
 * cancellation, the setting and the budget between images, and resumes from the index at the next run. The caller holds
 * the Tier C lock and has checked capability and the disclosure.
 */
class FaceIndexPass(
    private val store: MediaIndexStore,
    private val mediaRepository: MediaRepository,
    private val settingStore: FaceDataSettingStore,
    private val pipelineLoader: () -> FacePipeline,
    private val log: FaceIndexLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val budgetMillis: Long = FACE_PASS_BUDGET_MILLIS,
    private val pageSize: Int = FACE_PASS_PAGE_SIZE
) {

    init {
        require(pageSize > 0) { "pageSize must be > 0" }
    }

    /** [workStartedAtMillis] is the worker's start, on [clock]'s time base: the budget counts from it. */
    suspend fun run(workStartedAtMillis: Long): FaceIndexPassResult {
        var indexed = 0
        var failed = 0
        var skipped = 0
        var stop = FaceIndexPassStop.COMPLETED
        var pipeline: FacePipeline? = null
        var live: Map<String, MediaItem>? = null
        var cursor: String? = null

        try {
            pages@ while (true) {
                currentCoroutineContext().ensureActive()
                stopReason(workStartedAtMillis)?.let { stop = it; break@pages }

                val page = store.listPendingFaceImages(FACE_PIPELINE_VERSION, cursor, pageSize)
                if (page.isEmpty()) break

                if (live == null) {
                    live = try {
                        liveImages()
                    } catch (_: SecurityException) {
                        stop = FaceIndexPassStop.PERMISSION
                        break@pages
                    }
                }

                for (candidate in page) {
                    currentCoroutineContext().ensureActive()
                    stopReason(workStartedAtMillis)?.let { stop = it; break@pages }
                    cursor = candidate.identity.mediaUri

                    val outcome = processCandidate(candidate, live) {
                        pipeline ?: pipelineLoader().also { pipeline = it }
                    }
                    when (outcome) {
                        CandidateOutcome.INDEXED -> indexed += 1
                        CandidateOutcome.FAILED -> failed += 1
                        CandidateOutcome.SKIPPED -> skipped += 1
                        CandidateOutcome.STOP_SETTING_OFF -> { stop = FaceIndexPassStop.SETTING_OFF; break@pages }
                        CandidateOutcome.STOP_PERMISSION -> { stop = FaceIndexPassStop.PERMISSION; break@pages }
                    }
                }

                // A short page means nothing further was pending when it was read.
                if (page.size < pageSize) break
            }
        } finally {
            // A close that fails must not turn a finished pass into a failed run.
            pipeline?.let { runCatching { it.close() } }
        }

        val result = FaceIndexPassResult(indexed, failed, skipped, stop)
        log.line(FaceIndexLogLines.passFinished(result))
        return result
    }

    private fun stopReason(workStartedAtMillis: Long): FaceIndexPassStop? {
        if (!settingStore.isEnabled()) return FaceIndexPassStop.SETTING_OFF
        if (clock() - workStartedAtMillis >= budgetMillis) return FaceIndexPassStop.BUDGET
        return null
    }

    private suspend fun liveImages(): Map<String, MediaItem> {
        val byUri = HashMap<String, MediaItem>()
        for (item in mediaRepository.getAllMedia()) {
            byUri.putIfAbsent(canonicalMediaStoreUriForJoin(item.uri.toString()), item)
        }
        return byUri
    }

    private suspend fun processCandidate(
        candidate: PendingFaceImage,
        live: Map<String, MediaItem>,
        pipeline: () -> FacePipeline
    ): CandidateOutcome {
        val identity = candidate.identity
        val item = live[canonicalMediaStoreUriForJoin(identity.mediaUri)]
        // Reconciliation or Tier A will move a gone or changed row first; a video or GIF never gets a state.
        if (item == null || item.type != MediaType.IMAGE || item.toMediaIdentity().healingKey != identity.healingKey) {
            return CandidateOutcome.SKIPPED
        }

        if (candidate.attempts >= FACE_ATTEMPT_BOUND) return writeFailed(identity)

        val loaded = pipeline()
        try {
            val attemptNumber = store.recordFaceIndexAttempt(identity, FACE_PIPELINE_VERSION, settingStore::isEnabled)
            if (attemptNumber == null) {
                return if (settingStore.isEnabled()) CandidateOutcome.SKIPPED else CandidateOutcome.STOP_SETTING_OFF
            }

            val output = try {
                loaded.analyse(identity.mediaUri)
            } catch (_: FacePipelineException) {
                return writeFailed(identity)
            } catch (_: SecurityException) {
                store.clearFaceIndexAttempt(identity.mediaUri)
                return CandidateOutcome.STOP_PERMISSION
            } catch (_: IOException) {
                store.clearFaceIndexAttempt(identity.mediaUri)
                return CandidateOutcome.SKIPPED
            }

            // A cancellation that arrived during the blocking call must not be followed by a write.
            currentCoroutineContext().ensureActive()
            if (output.imageWidth <= 0 || output.imageHeight <= 0) return writeFailed(identity)
            val faces = output.faces.mapNotNull { faceDetectionOf(it, output.imageWidth, output.imageHeight) }
            return write(identity, FaceIndexOutcome.INDEXED, faces)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { store.clearFaceIndexAttempt(identity.mediaUri) }
            throw cancelled
        }
    }

    private suspend fun writeFailed(identity: MediaIdentity): CandidateOutcome =
        write(identity, FaceIndexOutcome.FAILED, emptyList())

    // A result write clears the image's attempt in its own transaction; a write that did not apply leaves it to be cleared here.
    private suspend fun write(
        identity: MediaIdentity,
        outcome: FaceIndexOutcome,
        faces: List<FaceDetection>
    ): CandidateOutcome {
        val applied = store.writeFaceIndexResults(
            listOf(FaceIndexWrite(identity, FACE_PIPELINE_VERSION, outcome, faces)),
            settingStore::isEnabled
        )
        if (applied == 0) {
            store.clearFaceIndexAttempt(identity.mediaUri)
            return if (settingStore.isEnabled()) CandidateOutcome.SKIPPED else CandidateOutcome.STOP_SETTING_OFF
        }
        return if (outcome == FaceIndexOutcome.INDEXED) CandidateOutcome.INDEXED else CandidateOutcome.FAILED
    }

    private enum class CandidateOutcome { INDEXED, FAILED, SKIPPED, STOP_SETTING_OFF, STOP_PERMISSION }
}

/**
 * Deletes face data at a process start while the setting reads off, whatever [MediaIndexStore.hasFaceData] says: a
 * deletion can commit every row and still report its scrub incomplete (ADR-0018 Amendments 6 and 7 §5). Not scrubbed is
 * a result, not an error, and is not retried here. A thrown error is caught and left to the next start. It logs at most
 * that it ran and whether it scrubbed.
 */
class FaceDataStartupDeletion(
    private val store: MediaIndexStore,
    private val settingStore: FaceDataSettingStore,
    private val log: FaceIndexLog
) {
    /** Null when it did not run, because face data is on, or did not finish, because it threw. */
    suspend fun run(): FaceDataDeletion? {
        if (settingStore.isEnabled()) return null
        return try {
            store.deleteAllFaceData().also { log.line(FaceIndexLogLines.deletionRan(it.scrubbed)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log.line(FaceIndexLogLines.DELETION_FAILED)
            null
        }
    }
}
