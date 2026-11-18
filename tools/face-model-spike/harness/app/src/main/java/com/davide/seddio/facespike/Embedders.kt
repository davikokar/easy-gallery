package com.davide.seddio.facespike

import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File

/**
 * One recognition model. embed() takes the BGR working image and one detector row
 * (x, y, w, h, 5 landmark pairs, score; 15 floats) in working-image coordinates.
 */
abstract class Embedder(val name: String, val file: File) {
    var lastAlignNs = 0L
    var lastEmbedNs = 0L
    abstract fun load()
    abstract fun embed(work: Mat, face: FloatArray): FloatArray
    abstract fun close()
}

/** OpenCV's reference FaceRecognizerSF: its own 5-point ArcFace alignment to 112x112, 128-d output. */
class SFaceEmbedder(name: String, file: File) : Embedder(name, file) {
    private var rec: FaceRecognizerSF? = null
    override fun load() { rec = FaceRecognizerSF.create(file.absolutePath, "") }
    override fun embed(work: Mat, face: FloatArray): FloatArray {
        val faceMat = Mat(1, 15, CvType.CV_32F)
        faceMat.put(0, 0, face)
        val aligned = Mat()
        val feat = Mat()
        try {
            val t0 = System.nanoTime()
            rec!!.alignCrop(work, faceMat, aligned)
            val t1 = System.nanoTime()
            rec!!.feature(aligned, feat)
            val t2 = System.nanoTime()
            lastAlignNs = t1 - t0
            lastEmbedNs = t2 - t1
            val out = FloatArray(feat.cols())
            feat.get(0, 0, out)
            return out
        } finally {
            faceMat.release(); aligned.release(); feat.release()
        }
    }
    override fun close() { rec = null }
}

/**
 * Warps the working image into a chip: a least-squares similarity transform of the detector's five landmarks
 * (eye, eye, nose tip, mouth corner, mouth corner) onto [template], given in chip pixels.
 */
fun alignChip(work: Mat, face: FloatArray, template: DoubleArray, chip: Int): Mat {
    val src = MatOfPoint2f(
        Point(face[4].toDouble(), face[5].toDouble()), Point(face[6].toDouble(), face[7].toDouble()),
        Point(face[8].toDouble(), face[9].toDouble()), Point(face[10].toDouble(), face[11].toDouble()),
        Point(face[12].toDouble(), face[13].toDouble()),
    )
    val dst = MatOfPoint2f(
        Point(template[0], template[1]), Point(template[2], template[3]), Point(template[4], template[5]),
        Point(template[6], template[7]), Point(template[8], template[9]),
    )
    val m = Calib3d.estimateAffinePartial2D(src, dst)
    val aligned = Mat()
    try {
        Imgproc.warpAffine(
            work, aligned, m, Size(chip.toDouble(), chip.toDouble()), Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT, Scalar(127.0, 127.0, 127.0),
        )
    } finally {
        src.release(); dst.release(); m.release()
    }
    return aligned
}

/** A model that wants its own aligned chip, run through an OpenCV DNN net. */
class TemplateNetEmbedder(
    name: String,
    file: File,
    private val chip: Int,
    private val template: DoubleArray,
    private val blobScale: Double,
    private val swapRB: Boolean,
    private val readNet: (String) -> Net,
) : Embedder(name, file) {
    private var net: Net? = null
    override fun load() { net = readNet(file.absolutePath) }
    override fun embed(work: Mat, face: FloatArray): FloatArray {
        val t0 = System.nanoTime()
        val aligned = alignChip(work, face, template, chip)
        val blob = Dnn.blobFromImage(aligned, blobScale, Size(chip.toDouble(), chip.toDouble()), Scalar(0.0, 0.0, 0.0), swapRB, false)
        val t1 = System.nanoTime()
        try {
            net!!.setInput(blob)
            val out = net!!.forward()
            val t2 = System.nanoTime()
            lastAlignNs = t1 - t0
            lastEmbedNs = t2 - t1
            val v = FloatArray(out.total().toInt())
            out.reshape(1, 1).get(0, 0, v)
            out.release()
            return v
        } finally {
            aligned.release(); blob.release()
        }
    }
    override fun close() { net = null }
}

/** dlib's ResNet-34-style recogniser, run by dlib's own native code (libdlibrec.so) on a 150x150 RGB chip. */
class DlibEmbedder(name: String, file: File, private val template: DoubleArray) : Embedder(name, file) {
    private var handle = 0L
    override fun load() { handle = DlibNative.load(file.absolutePath) }
    override fun embed(work: Mat, face: FloatArray): FloatArray {
        val t0 = System.nanoTime()
        val aligned = alignChip(work, face, template, 150)
        val rgb = Mat()
        val bytes = ByteArray(150 * 150 * 3)
        try {
            Imgproc.cvtColor(aligned, rgb, Imgproc.COLOR_BGR2RGB)
            rgb.get(0, 0, bytes)
        } finally {
            aligned.release(); rgb.release()
        }
        val t1 = System.nanoTime()
        val v = DlibNative.embed(handle, bytes)
        lastAlignNs = t1 - t0
        lastEmbedNs = System.nanoTime() - t1
        return v
    }
    override fun close() { if (handle != 0L) DlibNative.release(handle); handle = 0L }
}

object DlibNative {
    init { System.loadLibrary("dlibrec") }
    external fun load(path: String): Long
    external fun embed(handle: Long, rgb: ByteArray): FloatArray
    external fun release(handle: Long)
}

object Embedders {
    // OpenFace's own template (align_dlib.py, MINMAX-normalised 68 points x 96): eye centres, nose tip (30), mouth corners (48, 54).
    private val OPENFACE_96 = doubleArrayOf(26.63, 16.06, 67.79, 15.26, 47.24, 40.52, 29.48, 61.62, 66.76, 60.88)

    // dlib's 68-point chip (size 150, padding 0.25) reduced to the five YuNet points: eye centres (mean of 36-41, 42-47), nose tip 30, mouth corners 48, 54.
    private val DLIB_150 = doubleArrayOf(47.55, 46.60, 100.48, 46.60, 74.01, 76.56, 50.41, 103.02, 97.61, 103.02)

    fun create(kind: String, models: File): Embedder = when (kind) {
        "dlib" -> DlibEmbedder(kind, File(models, "dlib_face_recognition_resnet_model_v1.dat"), DLIB_150)
        "sface" -> SFaceEmbedder(kind, File(models, "sface_2021dec.onnx"))
        "sface_int8" -> SFaceEmbedder(kind, File(models, "sface_2021dec_int8.onnx"))
        "openface" -> TemplateNetEmbedder(kind, File(models, "openface_nn4.small2.v1.t7"), 96, OPENFACE_96, 1.0 / 255.0, true) {
            Dnn.readNetFromTorch(it)
        }
        else -> throw IllegalArgumentException("unknown recogniser '$kind'")
    }
}
