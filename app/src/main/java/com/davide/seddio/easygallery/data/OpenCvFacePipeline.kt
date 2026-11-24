package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF

// The Android half of the pipeline (ADR-0023 §1, §2): the decode and OpenCV live here so that FaceIndexPass stays
// Android-free. Nothing in this file logs, and no exception it throws carries a URI, a box or an embedding (ADR-0024 §6).

/** SFace's output length. */
const val FACE_EMBEDDING_SIZE = 128

private const val OPENCV_LIBRARY = "opencv_java4"

// The spike's setting. Core.setNumThreads is process-wide; nothing else in the app uses OpenCV.
private const val OPENCV_THREADS = 4

private const val ASSET_CHUNK_BYTES = 1 shl 20

// One YuNet row: x, y, width, height, five landmark (x, y) pairs, score.
private const val DETECTION_COLUMNS = 15
private const val DETECTION_COORDINATES = 14

private const val SELF_TEST_IMAGE_WIDTH = 640
private const val SELF_TEST_IMAGE_HEIGHT = 480
private const val SELF_TEST_CHIP_SIDE = 112

/**
 * The pipeline's decode: upright (ImageDecoder applies the EXIF orientation), software-backed, a long side of at most
 * [FACE_DECODE_LONG_SIDE] and never upscaled, as ARGB_8888. A separate function so a device test can drive the same code
 * without loading OpenCV. A `DecodeException` is [FacePipelineFailure.DECODE], except `SOURCE_EXCEPTION` (an I/O error
 * part-way through reading, so transient), which is rethrown as an `IOException` for the pass to skip.
 */
internal fun decodeUprightForFaces(context: Context, uri: Uri): Bitmap {
    val decoded = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longSide = max(info.size.width, info.size.height)
            if (longSide > FACE_DECODE_LONG_SIDE) {
                val ratio = FACE_DECODE_LONG_SIDE.toDouble() / longSide
                decoder.setTargetSize(
                    (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                    (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (failure: ImageDecoder.DecodeException) {
        // A DecodeException is an IOException, so the pass would skip it as a file that cannot be opened; only a read error should be.
        if (failure.error == ImageDecoder.DecodeException.SOURCE_EXCEPTION) throw IOException("A face decode could not read its source")
        throw FacePipelineException(FacePipelineFailure.DECODE)
    }
    if (decoded.config == Bitmap.Config.ARGB_8888) return decoded
    val converted = decoded.copy(Bitmap.Config.ARGB_8888, false)
    decoded.recycle()
    return converted ?: throw FacePipelineException(FacePipelineFailure.DECODE)
}

/**
 * YuNet and SFace fp32 on OpenCV's DNN, on the CPU, with both models read from the APK's assets into memory and never
 * written to a file (39 MB is over Auto Backup's quota). Decodes with [ImageDecoder], which applies the EXIF
 * orientation, so the boxes are in the upright frame ADR-0022 stores anchors in. Not thread-safe; blocking. In OpenCV
 * 4.13.0's Java wrappers (`FaceDetectorYN`, `CleanableMat`) native memory is freed only by `finalize()`, so [close] only
 * drops the references: load a pipeline once per run, and the capability check's instance is released by the GC.
 */
class OpenCvFacePipeline private constructor(
    private val context: Context,
    private val detector: FaceDetectorYN,
    private val recogniser: FaceRecognizerSF
) : FacePipeline {

    // The detector settings the pipeline version depends on, for a device test to hold against the KDoc on FACE_PIPELINE_VERSION.
    internal fun detectorSettings(): DetectorSettings =
        DetectorSettings(detector.scoreThreshold, detector.nmsThreshold, detector.topK)

    internal data class DetectorSettings(val scoreThreshold: Float, val nmsThreshold: Float, val topK: Int)

    override fun analyse(mediaUri: String): FacePipelineOutput {
        val bitmap = decodeUprightForFaces(context, Uri.parse(mediaUri))
        var working: Mat? = null
        try {
            working = toBgr(bitmap)
            return detectAndEmbed(working)
        } catch (_: CvException) {
            throw FacePipelineException(FacePipelineFailure.INFERENCE)
        } finally {
            working?.release()
        }
    }

    override fun selfTest(): FaceSelfTestResult {
        val image = syntheticImage(SELF_TEST_IMAGE_HEIGHT, SELF_TEST_IMAGE_WIDTH)
        val detections = Mat()
        val chip = syntheticImage(SELF_TEST_CHIP_SIDE, SELF_TEST_CHIP_SIDE)
        val features = Mat()
        try {
            detector.setInputSize(Size(SELF_TEST_IMAGE_WIDTH.toDouble(), SELF_TEST_IMAGE_HEIGHT.toDouble()))
            // A pattern holds no face, so at the production threshold the table is empty and proves nothing; at 0 every
            // candidate is kept, and a working detector then returns a non-empty table of finite rows.
            detector.setScoreThreshold(0f)
            try {
                detector.detect(image, detections)
            } finally {
                detector.setScoreThreshold(FACE_SCORE_THRESHOLD)
            }
            val detectionValid = !detections.empty() && isFiniteTable(detections, DETECTION_COLUMNS)

            recogniser.feature(chip, features)
            val embedding = embeddingOf(features)
            return FaceSelfTestResult(detectionValid, embedding)
        } finally {
            image.release()
            detections.release()
            chip.release()
            features.release()
        }
    }

    override fun close() {
        // Nothing to release explicitly; see the class comment.
    }

    private fun toBgr(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            return bgr
        } catch (failure: Throwable) {
            bgr.release()
            throw failure
        } finally {
            rgba.release()
            bitmap.recycle()
        }
    }

    private fun detectAndEmbed(working: Mat): FacePipelineOutput {
        val width = working.cols()
        val height = working.rows()
        val scale = min(1.0, FACE_DETECT_LONG_SIDE.toDouble() / max(width, height))
        val small = Mat()
        val detections = Mat()
        try {
            if (scale < 1.0) {
                Imgproc.resize(working, small, Size(width * scale, height * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            } else {
                working.copyTo(small)
            }
            detector.setInputSize(Size(small.cols().toDouble(), small.rows().toDouble()))
            detector.detect(small, detections)

            val rows = detectionRows(detections, scale)
            val faces = ArrayList<RawFace>(rows.size)
            for (row in rows) {
                val faceWidth = row[2]
                val faceHeight = row[3]
                // The pass drops these (decision 46); embedding one would only cost time.
                if (faceWidth < FACE_MIN_SIDE_PIXELS || faceHeight < FACE_MIN_SIDE_PIXELS) continue
                faces.add(RawFace(row[0], row[1], row[0] + faceWidth, row[1] + faceHeight, embed(working, row)))
            }
            return FacePipelineOutput(width, height, faces)
        } finally {
            small.release()
            detections.release()
        }
    }

    // Rows in decoded-image coordinates, best score first.
    private fun detectionRows(detections: Mat, scale: Double): List<FloatArray> {
        if (detections.empty()) return emptyList()
        if (detections.cols() != DETECTION_COLUMNS || detections.type() != CvType.CV_32F) {
            throw FacePipelineException(FacePipelineFailure.INFERENCE)
        }
        val rows = ArrayList<FloatArray>(detections.rows())
        for (index in 0 until detections.rows()) {
            val row = FloatArray(DETECTION_COLUMNS)
            detections.get(index, 0, row)
            for (column in 0 until DETECTION_COORDINATES) row[column] = (row[column] / scale).toFloat()
            rows.add(row)
        }
        rows.sortByDescending { it[DETECTION_COLUMNS - 1] }
        return rows
    }

    private fun embed(working: Mat, row: FloatArray): FaceEmbedding {
        val face = Mat(1, DETECTION_COLUMNS, CvType.CV_32F)
        val aligned = Mat()
        val features = Mat()
        try {
            face.put(0, 0, row)
            recogniser.alignCrop(working, face, aligned)
            recogniser.feature(aligned, features)
            val values = embeddingOf(features) ?: throw FacePipelineException(FacePipelineFailure.INFERENCE)
            if (values.any { !it.isFinite() }) throw FacePipelineException(FacePipelineFailure.INFERENCE)
            return FaceEmbedding(values)
        } finally {
            face.release()
            aligned.release()
            features.release()
        }
    }

    // Null unless the output is exactly one row of FACE_EMBEDDING_SIZE floats.
    private fun embeddingOf(features: Mat): FloatArray? {
        if (features.rows() != 1 || features.cols() != FACE_EMBEDDING_SIZE || features.type() != CvType.CV_32F) return null
        val values = FloatArray(FACE_EMBEDDING_SIZE)
        features.get(0, 0, values)
        return values
    }

    private fun isFiniteTable(table: Mat, columns: Int): Boolean {
        if (table.cols() != columns || table.type() != CvType.CV_32F) return false
        val row = FloatArray(columns)
        for (index in 0 until table.rows()) {
            table.get(index, 0, row)
            if (row.any { !it.isFinite() }) return false
        }
        return true
    }

    // A fixed pattern, so the self-test needs no stored image and no random source.
    private fun syntheticImage(height: Int, width: Int): Mat {
        val pixels = ByteArray(height * width * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = (y * width + x) * 3
                pixels[offset] = ((x * 7 + y * 13) % 256).toByte()
                pixels[offset + 1] = ((x * 11 + y * 5 + 85) % 256).toByte()
                pixels[offset + 2] = ((x * 3 + y * 17 + 170) % 256).toByte()
            }
        }
        val image = Mat(height, width, CvType.CV_8UC3)
        image.put(0, 0, pixels)
        return image
    }

    companion object {
        /**
         * Loads the native library and both models. Throws [FacePipelineUnavailableException] when the library is not
         * in this build for this device's ABI; any other throwable is a model or the runtime failing, which will fail
         * again. Blocking.
         */
        fun load(context: Context): OpenCvFacePipeline {
            try {
                System.loadLibrary(OPENCV_LIBRARY)
            } catch (_: UnsatisfiedLinkError) {
                throw FacePipelineUnavailableException()
            }
            Core.setNumThreads(OPENCV_THREADS)
            // Native error text would reach logcat as a line outside the pass's fixed ones (ADR-0024 §6).
            Core.setErrorVerbosity(false)

            val assets = context.applicationContext.assets
            val noConfig = MatOfByte()
            try {
                val detectorModel = readAsset(assets, FACE_DETECTOR_ASSET)
                val detector = try {
                    FaceDetectorYN.create(
                        "onnx",
                        detectorModel,
                        noConfig,
                        Size(FACE_DETECT_LONG_SIDE.toDouble(), FACE_DETECT_LONG_SIDE.toDouble()),
                        FACE_SCORE_THRESHOLD
                    )
                } finally {
                    detectorModel.release()
                }
                val recogniserModel = readAsset(assets, FACE_RECOGNISER_ASSET)
                val recogniser = try {
                    FaceRecognizerSF.create("onnx", recogniserModel, noConfig)
                } finally {
                    recogniserModel.release()
                }
                return OpenCvFacePipeline(context.applicationContext, detector, recogniser)
            } finally {
                noConfig.release()
            }
        }

        // The model sits on the Java heap in 1 MB chunks (never one array) and is then copied to native memory: a load peaks at about twice its 39 MB.
        private fun readAsset(assets: AssetManager, path: String): MatOfByte {
            val chunks = ArrayList<ByteArray>()
            var total = 0
            assets.open(path).use { input ->
                while (true) {
                    val buffer = ByteArray(ASSET_CHUNK_BYTES)
                    var filled = 0
                    while (filled < buffer.size) {
                        val read = input.read(buffer, filled, buffer.size - filled)
                        if (read < 0) break
                        filled += read
                    }
                    if (filled == 0) break
                    chunks.add(if (filled == buffer.size) buffer else buffer.copyOf(filled))
                    total += filled
                    if (filled < buffer.size) break
                }
            }
            check(total > 0) { "A face model asset is empty" }

            val model = MatOfByte()
            model.create(total, 1, CvType.CV_8UC1)
            var offset = 0
            for (chunk in chunks) {
                check(model.put(offset, 0, chunk) == chunk.size) { "A face model asset did not fit its buffer" }
                offset += chunk.size
            }
            return model
        }
    }
}
