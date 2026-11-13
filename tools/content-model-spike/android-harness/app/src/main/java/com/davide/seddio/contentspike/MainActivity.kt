package com.davide.seddio.contentspike

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.provider.MediaStore
import android.content.ContentUris
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val TAG = "SPIKE"

class JobParams(
    val kind: String,
    val variant: String,
    val budget: Int,
    val limit: Int,
    val threads: Int,
    val parityLimit: Int,
    val tag: String,
)

/** One measurement run: load a model, embed queries and photos, write results next to the models (adb pull). */
class Job(private val ctx: Context, private val p: JobParams) {
    private val base = ctx.getExternalFilesDir(null)!!
    private val out = File(base, "results").also { it.mkdirs() }

    private val peakPss = Peak()
    @Volatile private var sampling = true

    private class Peak { @Volatile var kb = 0L }

    private fun pssKb(): Long {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        return mi.totalPss.toLong()
    }

    private fun procStatus(): JSONObject {
        val o = JSONObject()
        File("/proc/self/status").readLines().forEach { l ->
            for (k in listOf("VmHWM", "VmRSS", "RssAnon", "RssFile", "RssShmem")) {
                if (l.startsWith("$k:")) o.put(k + "_kB", l.substringAfter(":").trim().substringBefore(" ").toLong())
            }
        }
        return o
    }

    private fun env(): JSONObject {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return JSONObject()
            .put("battery_saver", pm.isPowerSaveMode)
            .put("thermal_status", pm.currentThermalStatus)
            .put("battery_temp_c", (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0)
            .put("battery_level", bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1)
            .put("plugged", bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
    }

    private fun readIds(name: String): List<Long> =
        File(base, "inputs/$name").readLines().map { it.trim() }.filter { it.isNotEmpty() }.map { it.toLong() }

    private fun readQueryIds(name: String): List<IntArray> {
        val arr = JSONArray(File(base, "inputs/$name").readText())
        return (0 until arr.length()).map { i ->
            val a = arr.getJSONArray(i)
            IntArray(a.length()) { a.getInt(it) }
        }
    }

    private fun writeFloats(name: String, rows: List<FloatArray>) {
        val bb = ByteBuffer.allocate(rows.sumOf { it.size } * 4).order(ByteOrder.LITTLE_ENDIAN)
        rows.forEach { r -> r.forEach { bb.putFloat(it) } }
        File(out, name).writeBytes(bb.array())
    }

    fun run() {
        val result = JSONObject().put("tag", p.tag).put("kind", p.kind).put("variant", p.variant)
            .put("budget", p.budget).put("threads", p.threads).put("device", "${Build.MODEL} api${Build.VERSION.SDK_INT}")
        File(out, "${p.tag}.done").delete()
        try {
            execute(result)
            result.put("status", "ok")
        } catch (t: Throwable) {
            Log.e(TAG, "job ${p.tag} failed", t)
            result.put("status", "failed").put("error", t.javaClass.name + ": " + t.message)
                .put("stack", Log.getStackTraceString(t).take(4000))
        } finally {
            sampling = false
        }
        result.put("peak_pss_kB_sampled", peakPss.kb).put("proc_status_end", procStatus()).put("env_end", env())
        File(out, "${p.tag}.json").writeText(result.toString(1))
        File(out, "${p.tag}.done").writeText("done")
        Log.i(TAG, "job ${p.tag} status=${result.getString("status")}")
    }

    private fun execute(result: JSONObject) {
        val dir = File(base, "models/${p.kind}/${p.variant}")
        val sizes = JSONObject()
        dir.listFiles()!!.sortedBy { it.name }.forEach { sizes.put(it.name, it.length()) }
        result.put("file_sizes_bytes", sizes)
        result.put("env_start", env())
        result.put("pss_before_load_kB", pssKb())

        Thread {
            while (sampling) {
                peakPss.kb = maxOf(peakPss.kb, pssKb())
                Thread.sleep(40)
            }
        }.apply { isDaemon = true; start() }

        val t0 = System.nanoTime()
        val pipeline: Pipeline = if (p.kind == "gemma") GemmaPipeline(dir, p.budget, p.threads) else SiglipPipeline(dir, p.threads)
        result.put("load_total_ms", (System.nanoTime() - t0) / 1e6)
        result.put("load_parts_ms", JSONObject(pipeline.loadTimesMs as Map<*, *>))
        result.put("pss_after_load_kB", pssKb()).put("proc_status_after_load", procStatus())
        try {
            // Queries: first call timed apart (lazy init), the rest are the steady-state sample.
            val queries = readQueryIds(if (p.kind == "gemma") "${p.kind}_queries.json" else "siglip_queries.json")
            val qMs = ArrayList<Double>()
            val qEmb = ArrayList<FloatArray>()
            for (q in queries) {
                val t = System.nanoTime()
                qEmb.add(pipeline.embedQuery(q))
                qMs.add((System.nanoTime() - t) / 1e6)
            }
            result.put("query_ms", JSONArray(qMs)).put("query_count", queries.size)
            writeFloats("${p.tag}_qry.f32", qEmb)
            result.put("pss_after_queries_kB", pssKb())

            // Parity: the desktop's own preprocessed tensors, so only the runtime/quantisation differs.
            val parityDir = File(base, if (p.kind == "gemma") "inputs/gemma_parity_b${p.budget}" else "inputs/siglip_pixels")
            val parityFiles = (parityDir.listFiles() ?: emptyArray()).sortedBy { it.name }.take(p.parityLimit)
            val parityEmb = ArrayList<FloatArray>()
            val parityMs = ArrayList<Double>()
            for (f in parityFiles) {
                val t = System.nanoTime()
                parityEmb.add(pipeline.embedParityFile(f))
                parityMs.add((System.nanoTime() - t) / 1e6)
            }
            result.put("parity_ids", JSONArray(parityFiles.map { it.name.substringBefore('.') }))
            result.put("parity_ms", JSONArray(parityMs))
            writeFloats("${p.tag}_parity.f32", parityEmb)
            result.put("pss_after_parity_kB", pssKb())

            // Photos through the real on-device path: MediaStore -> decode -> preprocess -> model.
            val ids = readIds("photo_ids.txt").take(p.limit)
            val pre = ArrayList<Double>(); val vis = ArrayList<Double>(); val lm = ArrayList<Double>()
            val iEmb = ArrayList<FloatArray>()
            val done = ArrayList<Long>()
            val resolver = ctx.contentResolver
            val tStart = System.nanoTime()
            for ((n, id) in ids.withIndex()) {
                val uri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                try {
                    val r = pipeline.embedImage(resolver, uri)
                    iEmb.add(r.embedding); pre.add(r.decodePreprocessMs); vis.add(r.visionMs); lm.add(r.lmMs); done.add(id)
                } catch (e: Exception) {
                    Log.w(TAG, "image $id failed: ${e.javaClass.simpleName}: ${e.message}")
                }
                if (n % 25 == 0) Log.i(TAG, "${p.tag}: ${n + 1}/${ids.size} ${(System.nanoTime() - tStart) / 1e9}s pss=${pssKb()}")
            }
            result.put("image_wall_s", (System.nanoTime() - tStart) / 1e9)
            result.put("image_ids", JSONArray(done))
            result.put("image_decode_preprocess_ms", JSONArray(pre))
            result.put("image_vision_ms", JSONArray(vis))
            result.put("image_lm_ms", JSONArray(lm))
            result.put("images_requested", ids.size).put("images_embedded", done.size)
            result.put("embedding_dim", pipeline.dim)
            writeFloats("${p.tag}_img.f32", iEmb)
        } finally {
            pipeline.close()
        }
    }
}

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val tv = android.widget.TextView(this).apply { text = "content-spike harness (adb-driven)"; setPadding(32, 96, 32, 32) }
        setContentView(tv)
        val i = intent
        val kind = i.getStringExtra("kind")
        if (kind == null) {
            tv.text = "idle: start with --es kind gemma|siglip --es variant fp32|dyn8|nbits4 [--ei budget 280 --ei limit 300 --ei threads 4]"
            return
        }
        val variant = i.getStringExtra("variant") ?: "fp32"
        val budget = i.getIntExtra("budget", 280)
        val tag = i.getStringExtra("tag") ?: "$kind-$variant" + (if (kind == "gemma") "-b$budget" else "")
        val params = JobParams(kind, variant, budget, i.getIntExtra("limit", 300), i.getIntExtra("threads", 4),
            i.getIntExtra("parityLimit", 50), tag)
        tv.text = "running $tag"
        Thread { Job(applicationContext, params).run(); runOnUiThread { tv.text = "finished $tag" } }.start()
    }
}
