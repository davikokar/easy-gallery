package com.davide.seddio.easygallery.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Tier C is face data (ADR-0018 Amendment 5). ADR-0024 §6: nothing here may print a box, an embedding or a
// URI, so every toString is redacted and every check message names the rule, never the value.

enum class FaceIndexOutcome {
    INDEXED,
    FAILED
}

/** A face's box as fractions of the photo's width and height, in the upright frame (ADR-0022 §1). */
data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) {
            "a face box must lie inside the photo"
        }
        require(left < right && top < bottom) {
            "a face box must have a positive width and height"
        }
    }

    override fun toString(): String = "FaceBox(redacted)"
}

/** The recogniser's output for one face. Immutable: the values are copied in and out. */
class FaceEmbedding(source: FloatArray) {
    private val values: FloatArray = source.copyOf()

    init {
        require(values.isNotEmpty()) { "a face embedding must not be empty" }
        require(values.all { it.isFinite() }) { "a face embedding must hold finite values" }
    }

    val size: Int get() = values.size

    fun toFloatArray(): FloatArray = values.copyOf()

    override fun equals(other: Any?): Boolean = other is FaceEmbedding && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = "FaceEmbedding(redacted)"
}

/** float32 little-endian, the stored form; the schema does not fix the length. */
fun encodeFaceEmbedding(embedding: FaceEmbedding): ByteArray {
    val values = embedding.toFloatArray()
    val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(values)
    return buffer.array()
}

fun decodeFaceEmbedding(blob: ByteArray): FaceEmbedding {
    require(blob.isNotEmpty() && blob.size % Float.SIZE_BYTES == 0) {
        "a stored face embedding must be a whole number of float32 values"
    }
    val values = FloatArray(blob.size / Float.SIZE_BYTES)
    ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values)
    return FaceEmbedding(values)
}

/** One face the pipeline found: what a write carries, before the store gives it an id. */
data class FaceDetection(
    val box: FaceBox,
    val embedding: FaceEmbedding
) {
    override fun toString(): String = "FaceDetection(redacted)"
}

/**
 * One image's result. [identity] is the index row's identity the result was computed from; the store applies the
 * write only while the row still has it. A [FaceIndexOutcome.FAILED] write carries no face.
 */
data class FaceIndexWrite(
    val identity: MediaIdentity,
    val pipelineVersion: Int,
    val outcome: FaceIndexOutcome,
    val faces: List<FaceDetection>
) {
    init {
        require(outcome == FaceIndexOutcome.INDEXED || faces.isEmpty()) {
            "a failed face-index write carries no face"
        }
    }

    override fun toString(): String = "FaceIndexWrite(redacted)"
}

/** Done, faces and done, none are told apart by the face rows, not by a second outcome. */
data class FaceIndexState(
    val mediaUri: String,
    val pipelineVersion: Int,
    val outcome: FaceIndexOutcome
) {
    override fun toString(): String = "FaceIndexState(redacted)"
}

data class StoredFace(
    val id: Long,
    val mediaUri: String,
    val pipelineVersion: Int,
    val box: FaceBox,
    val embedding: FaceEmbedding,
    val groupId: Long?,
    val groupingVersion: Int?
) {
    override fun toString(): String = "StoredFace(redacted)"
}

/**
 * [scrubbed] is false when no deleted byte can be shown to be gone from the files: a reader held the old snapshot of
 * a write-ahead log past the busy timeout, or the journal mode keeps page copies (`PERSIST`, `DELETE` or `TRUNCATE`
 * under `exclusive` locking, or a mode not recognised; ADR-0018 Amendment 6). The rows are deleted either way. The
 * caller repeats the deletion at each start while face data is off; under `PERSIST` a repeat reports false again.
 */
data class FaceDataDeletion(
    val rowsDeleted: Int,
    val scrubbed: Boolean
)
