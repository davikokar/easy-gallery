package com.davide.seddio.facespike

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageDecoder
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import java.io.BufferedWriter
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class JobParams(
    val tag: String,
    val det: String,
    val recs: List<String>,
    val limit: Int,
    val offset: Int,
    val detSide: Int,
    val workSide: Int,
    val threads: Int,
    val score: Float,
    val dump: Boolean,
) {
    companion object {
        fun from(i: Intent) = JobParams(
            tag = i.getStringExtra("tag") ?: "run",
            det = i.getStringExtra("det") ?: "yunet",
            recs = (i.getStringExtra("recs") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            limit = i.getIntExtra("limit", 0),
            offset = i.getIntExtra("offset", 0),
            detSide = i.getIntExtra("detside", 640),
            workSide = i.getIntExtra("workside", 1280),
            threads = i.getIntExtra("threads", 4),
            score = i.getFloatExtra("score", 0.7f),
            dump = i.getBooleanExtra("dump", false),
        )
    }
}

/** One measurement run over the phone's still images. Logs and the result JSON carry aggregates only. */
class Job(private val ctx: Context, private val p: JobParams) {
    private val base = ctx.filesDir
    private val out = File(base, "results").also { it.mkdirs() }
    private val models = File(base, "models")

    private fun procStatusKb(key: String): Long =
        File("/proc/self/status").readLines().firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")?.trim()?.substringBefore(" ")?.toLong() ?: -1L

    private fun mem(): JSONObject = JSONObject()
        .put("VmHWM_kB", procStatusKb("VmHWM")).put("VmRSS_kB", procStatusKb("VmRSS"))
        .put("RssAnon_kB", procStatusKb("RssAnon")).put("VmSwap_kB", procStatusKb("VmSwap"))

    private fun env(): JSONObject {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return JSONObject()
            .put("battery_saver", pm.isPowerSaveMode).put("thermal_status", pm.currentThermalStatus)
            .put("battery_temp_c", (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0)
            .put("plugged", bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
    }

    private fun stats(v: List<Double>): JSONObject {
        val o = JSONObject().put("n", v.size)
        if (v.isEmpty()) return o
        val s = v.sorted()
        fun q(f: Double) = s[min(s.size - 1, (f * (s.size - 1)).roundToInt())]
        return o.put("mean", v.average()).put("p50", q(0.5)).put("p90", q(0.9)).put("p99", q(0.99)).put("max", s.last())
    }

    private fun ms(ns: Long) = ns / 1e6

    private class Row(val id: Long)

    /** All images by type (aggregates) and the still-image rows the run processes. */
    private fun queryImages(result: JSONObject): List<Row> {
        val cr = ctx.contentResolver
        val counts = JSONObject()
        cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media.MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) counts.put(c.getString(0), counts.optInt(c.getString(0), 0) + 1)
        }
        result.put("mediastore_images_by_mime", counts)
        val rows = ArrayList<Row>()
        cr.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "mime_type IN (?,?,?)", arrayOf("image/jpeg", "image/png", "image/webp"), "_id ASC",
        )?.use { c -> while (c.moveToNext()) rows.add(Row(c.getLong(0))) }
        result.put("still_images_total", rows.size)
        val from = min(p.offset, rows.size)
        val to = if (p.limit > 0) min(rows.size, from + p.limit) else rows.size
        return rows.subList(from, to)
    }

    private fun decode(id: Long): Mat {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            val longSide = max(info.size.width, info.size.height)
            if (longSide > p.workSide) {
                val s = p.workSide.toDouble() / longSide
                dec.setTargetSize((info.size.width * s).roundToInt().coerceAtLeast(1), (info.size.height * s).roundToInt().coerceAtLeast(1))
            }
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bmp, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        } finally {
            rgba.release(); bmp.recycle()
        }
        return bgr
    }

    private fun writeCrop(work: Mat, f: FloatArray, path: String) {
        val side = max(f[2], f[3]) * 1.6f
        val s = 128.0 / side
        val m = Mat(2, 3, CvType.CV_64F)
        m.put(0, 0, s, 0.0, 64.0 - s * (f[0] + f[2] / 2), 0.0, s, 64.0 - s * (f[1] + f[3] / 2))
        val crop = Mat()
        try {
            Imgproc.warpAffine(work, crop, m, Size(128.0, 128.0), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(127.0, 127.0, 127.0))
            Imgcodecs.imwrite(path, crop, MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 88))
        } finally {
            m.release(); crop.release()
        }
    }

    private fun floats(f: FloatArray): String {
        val sb = StringBuilder("[")
        for (i in f.indices) { if (i > 0) sb.append(','); sb.append(String.format(Locale.US, "%.5f", f[i])) }
        return sb.append(']').toString()
    }

    fun run() {
        val result = JSONObject().put("tag", p.tag).put("device", "${Build.MODEL} api${Build.VERSION.SDK_INT}")
            .put("det", p.det).put("recs", JSONArray(p.recs)).put("det_side", p.detSide).put("work_side", p.workSide)
            .put("threads", p.threads).put("score_threshold", p.score)
        File(out, "${p.tag}.done").delete()
        try {
            execute(result)
            result.put("status", "ok")
        } catch (t: Throwable) {
            Log.e(TAG, "run ${p.tag} failed", t)
            result.put("status", "failed").put("error", t.javaClass.name + ": " + t.message)
                .put("stack", Log.getStackTraceString(t).take(3000))
        }
        result.put("mem_end", mem()).put("env_end", env())
        File(out, "${p.tag}.json").writeText(result.toString(1))
        File(out, "${p.tag}.done").writeText("done")
        Log.i(TAG, "run ${p.tag} status=${result.getString("status")}")
    }

    private fun execute(result: JSONObject) {
        result.put("env_start", env())
        System.loadLibrary("opencv_java4")
        Core.setNumThreads(p.threads)
        result.put("opencv_version", Core.VERSION).put("opencv_threads", Core.getNumThreads())
        result.put("mem_after_opencv_load", mem())

        val detFile = File(models, when (p.det) { "yunet" -> "yunet_2023mar.onnx"; "yunet_int8" -> "yunet_2023mar_int8.onnx"; else -> error("unknown detector ${p.det}") })
        result.put("det_model_bytes", detFile.length())
        var t0 = System.nanoTime()
        val detector = FaceDetectorYN.create(detFile.absolutePath, "", Size(640.0, 480.0), p.score, 0.3f, 5000, Dnn.DNN_BACKEND_OPENCV, Dnn.DNN_TARGET_CPU)
        result.put("det_load_ms", ms(System.nanoTime() - t0))

        val embedders = p.recs.map { Embedders.create(it, models) }
        val recInfo = JSONObject()
        for (e in embedders) {
            t0 = System.nanoTime()
            e.load()
            recInfo.put(e.name, JSONObject().put("model_bytes", e.file.length()).put("load_ms", ms(System.nanoTime() - t0)))
        }
        result.put("recognisers", recInfo).put("mem_after_load", mem())

        val rows = queryImages(result)
        result.put("images_processed_target", rows.size)

        val faceDir = File(out, p.tag)
        var jsonl: BufferedWriter? = null
        var photosCsv: BufferedWriter? = null
        if (p.dump) {
            faceDir.deleteRecursively(); faceDir.mkdirs()
            jsonl = File(out, "${p.tag}.faces.jsonl").bufferedWriter()
            photosCsv = File(out, "${p.tag}.photos.csv").bufferedWriter()
            photosCsv.write("id,w,h,decode_ms,detect_ms,faces\n")
        }

        val decodeMs = ArrayList<Double>(); val detectMs = ArrayList<Double>(); val totalPhotoMs = ArrayList<Double>()
        val alignMs = HashMap<String, ArrayList<Double>>(); val embedMs = HashMap<String, ArrayList<Double>>()
        embedders.forEach { alignMs[it.name] = ArrayList(); embedMs[it.name] = ArrayList() }
        val perPhotoEmbedMs = ArrayList<Double>()
        val facesPerImage = JSONObject()
        var facesTotal = 0; var failed = 0; var done = 0
        var firstPhotoMs = -1.0
        val wallStart = System.nanoTime()
        var smallFaceCount = 0

        for ((index, row) in rows.withIndex()) {
            try {
                val tPhoto = System.nanoTime()
                val work = decode(row.id)
                val tDecoded = System.nanoTime()
                val wW = work.cols(); val wH = work.rows()
                val scale = min(1.0, p.detSide.toDouble() / max(wW, wH))
                val small = Mat()
                val faces = Mat()
                val tDet0 = System.nanoTime()
                if (scale < 1.0) Imgproc.resize(work, small, Size(wW * scale, wH * scale), 0.0, 0.0, Imgproc.INTER_AREA) else work.copyTo(small)
                detector.setInputSize(Size(small.cols().toDouble(), small.rows().toDouble()))
                detector.detect(small, faces)
                val tDet1 = System.nanoTime()

                val found = ArrayList<FloatArray>()
                for (i in 0 until faces.rows()) {
                    val f = FloatArray(15)
                    faces.get(i, 0, f)
                    for (j in 0 until 14) f[j] = (f[j] / scale).toFloat() // box and landmarks back to working coordinates
                    found.add(f)
                }
                found.sortByDescending { it[14] }

                var embedNsThisPhoto = 0L
                for ((k, f) in found.withIndex()) {
                    if (min(f[2], f[3]) < 40f) smallFaceCount++
                    val embs = LinkedHashMap<String, FloatArray>()
                    for (e in embedders) {
                        embs[e.name] = e.embed(work, f)
                        if (index > 0) { alignMs[e.name]!!.add(ms(e.lastAlignNs)); embedMs[e.name]!!.add(ms(e.lastEmbedNs)) }
                        embedNsThisPhoto += e.lastAlignNs + e.lastEmbedNs
                    }
                    if (p.dump) {
                        writeCrop(work, f, File(faceDir, "${row.id}_$k.jpg").absolutePath)
                        val sb = StringBuilder()
                        sb.append("{\"id\":").append(row.id).append(",\"k\":").append(k).append(",\"w\":").append(wW).append(",\"h\":").append(wH)
                        sb.append(",\"box\":").append(floats(f.copyOfRange(0, 4))).append(",\"lm\":").append(floats(f.copyOfRange(4, 14)))
                        sb.append(",\"score\":").append(String.format(Locale.US, "%.4f", f[14])).append(",\"emb\":{")
                        var first = true
                        for ((n, v) in embs) { if (!first) sb.append(','); first = false; sb.append('"').append(n).append("\":").append(floats(v)) }
                        sb.append("}}\n")
                        jsonl!!.write(sb.toString())
                    }
                }
                val tEnd = System.nanoTime()
                if (index == 0) {
                    firstPhotoMs = ms(tEnd - tPhoto) // includes lazy initialisation; excluded from the statistics
                } else {
                    decodeMs.add(ms(tDecoded - tPhoto)); detectMs.add(ms(tDet1 - tDet0)); totalPhotoMs.add(ms(tEnd - tPhoto))
                    perPhotoEmbedMs.add(ms(embedNsThisPhoto))
                }
                val bucket = when { found.size >= 6 -> "6+"; found.size >= 3 -> "3-5"; else -> found.size.toString() }
                facesPerImage.put(bucket, facesPerImage.optInt(bucket, 0) + 1)
                facesTotal += found.size
                if (p.dump) photosCsv!!.write("${row.id},$wW,$wH,${"%.2f".format(Locale.US, ms(tDecoded - tPhoto))},${"%.2f".format(Locale.US, ms(tDet1 - tDet0))},${found.size}\n")
                work.release(); small.release(); faces.release()
                done++
            } catch (t: Throwable) {
                failed++
                if (failed <= 3) Log.w(TAG, "image failed: ${t.javaClass.simpleName}")
            }
            if ((index + 1) % 100 == 0) Log.i(TAG, "${p.tag}: ${index + 1}/${rows.size} faces=$facesTotal")
        }
        val wallS = (System.nanoTime() - wallStart) / 1e9
        jsonl?.close(); photosCsv?.close()

        val recStats = JSONObject()
        for (e in embedders) recStats.put(e.name, JSONObject().put("align_ms_per_face", stats(alignMs[e.name]!!)).put("embed_ms_per_face", stats(embedMs[e.name]!!)))
        result.put("images_ok", done).put("images_failed", failed).put("faces_found", facesTotal).put("faces_min_side_lt_40px_working", smallFaceCount)
            .put("faces_per_image_buckets", facesPerImage).put("wall_s", wallS).put("first_photo_ms_excluded_from_stats", firstPhotoMs)
            .put("decode_ms_per_photo", stats(decodeMs)).put("detect_ms_per_photo", stats(detectMs))
            .put("embed_ms_per_photo_all_faces_align_plus_embed", stats(perPhotoEmbedMs))
            .put("total_ms_per_photo_decode_detect_embed", stats(totalPhotoMs)).put("recogniser_timings", recStats)
        embedders.forEach { it.close() }
    }
}
