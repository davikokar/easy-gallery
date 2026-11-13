package com.davide.seddio.contentspike

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import kotlin.math.floor
import kotlin.math.sqrt

/** Milliseconds spent on one image, split by stage. */
class ImageTiming(val embedding: FloatArray, val decodePreprocessMs: Double, val visionMs: Double, val lmMs: Double)

interface Pipeline {
    val dim: Int
    val loadTimesMs: Map<String, Double>
    fun embedImage(resolver: ContentResolver, uri: Uri): ImageTiming
    fun embedParityFile(file: File): FloatArray
    fun embedQuery(ids: IntArray): FloatArray
    fun close()
}

private fun directFloats(n: Int): FloatBuffer =
    ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

private fun directLongs(n: Int): LongBuffer =
    ByteBuffer.allocateDirect(n * 8).order(ByteOrder.nativeOrder()).asLongBuffer()

private fun sessionOptions(threads: Int) = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(threads)
    setInterOpNumThreads(1)
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    setMemoryPatternOptimization(false)
}

private fun floatsOf(tensor: OnnxTensor): FloatArray {
    val fb = tensor.floatBuffer
    return FloatArray(fb.remaining()).also { fb.get(it) }
}

/** Token embedding table read through a memory map, so only touched rows become resident. */
class TokenTable(dir: File, meta: JSONObject, private val dim: Int) {
    private val isInt8 = meta.optString("table") == "i8"
    private val data: ByteBuffer
    private val scales: FloatBuffer?

    init {
        val name = if (isInt8) "embed_table_i8.bin" else "embed_table_f32.bin"
        RandomAccessFile(File(dir, name), "r").use { f ->
            data = f.channel.map(FileChannel.MapMode.READ_ONLY, 0, f.length()).order(ByteOrder.LITTLE_ENDIAN)
        }
        scales = if (isInt8) {
            RandomAccessFile(File(dir, "embed_scales_f32.bin"), "r").use { f ->
                f.channel.map(FileChannel.MapMode.READ_ONLY, 0, f.length()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            }
        } else null
    }

    fun rowInto(id: Int, dst: FloatBuffer) {
        if (isInt8) {
            val s = scales!!.get(id)
            val base = id * dim
            for (i in 0 until dim) dst.put(data.get(base + i) * s)
        } else {
            val d = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            d.position(id * dim * 4)
            val src = FloatArray(dim)
            d.asFloatBuffer().get(src)
            dst.put(src)
        }
    }
}

private fun loadMeta(dir: File) = JSONObject(File(dir, "meta.json").readText())

private fun elapsedMs(startNs: Long) = (System.nanoTime() - startNs) / 1e6

/** EmbeddingGemma 2, text + vision only: vision.onnx -> soft tokens, lm.onnx -> embedding, host-side token table. */
class GemmaPipeline(dir: File, private val budget: Int, threads: Int) : Pipeline {
    override val dim = 768
    private val env = OrtEnvironment.getEnvironment()
    private val meta = loadMeta(dir)
    private val hidden = meta.getInt("hidden")
    private val special: FloatArray
    private val table: TokenTable
    private val vision: OrtSession
    private val lm: OrtSession
    override val loadTimesMs: Map<String, Double>

    init {
        var t = System.nanoTime()
        vision = env.createSession(File(dir, "vision.onnx").path, sessionOptions(threads))
        val vMs = elapsedMs(t)
        t = System.nanoTime()
        lm = env.createSession(File(dir, "lm.onnx").path, sessionOptions(threads))
        val lMs = elapsedMs(t)
        t = System.nanoTime()
        table = TokenTable(dir, meta, hidden)
        val sp = File(dir, "special_f32.bin").readBytes()
        special = FloatArray(4 * hidden).also { ByteBuffer.wrap(sp).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
        val tMs = elapsedMs(t)
        loadTimesMs = mapOf("vision_session" to vMs, "lm_session" to lMs, "table_and_special" to tMs)
    }

    private fun runVision(pixels: FloatBuffer, positions: LongBuffer, p: Int): Pair<FloatArray, Double> {
        val t = System.nanoTime()
        OnnxTensor.createTensor(env, pixels, longArrayOf(1, p.toLong(), 768)).use { pv ->
            OnnxTensor.createTensor(env, positions, longArrayOf(1, p.toLong(), 2)).use { pos ->
                vision.run(mapOf("pixel_values" to pv, "position_ids" to pos)).use { r ->
                    return floatsOf(r.get(0) as OnnxTensor) to elapsedMs(t)
                }
            }
        }
    }

    private fun runLm(soft: FloatArray): Pair<FloatArray, Double> {
        val t = System.nanoTime()
        val n = soft.size / hidden
        val seq = directFloats((n + 4) * hidden)
        seq.put(special, 0, hidden)
        seq.put(special, hidden, hidden)
        seq.put(soft)
        seq.put(special, 2 * hidden, hidden)
        seq.put(special, 3 * hidden, hidden)
        seq.rewind()
        OnnxTensor.createTensor(env, seq, longArrayOf(1, (n + 4).toLong(), hidden.toLong())).use { e ->
            lm.run(mapOf("inputs_embeds" to e)).use { r -> return floatsOf(r.get(0) as OnnxTensor) to elapsedMs(t) }
        }
    }

    override fun embedImage(resolver: ContentResolver, uri: Uri): ImageTiming {
        val t0 = System.nanoTime()
        val bmp = decodeForGemma(resolver, uri, budget)
        val (pixels, positions, p) = patchify(bmp)
        bmp.recycle()
        val pre = elapsedMs(t0)
        val (soft, vMs) = runVision(pixels, positions, p)
        val (emb, lMs) = runLm(soft)
        return ImageTiming(emb, pre, vMs, lMs)
    }

    /** Parity input: int32 P, float32 [P,768], int32 [P,2] as written by make_reference_gemma.py. */
    override fun embedParityFile(file: File): FloatArray {
        val bb = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val p = bb.getInt()
        val pixels = directFloats(p * 768)
        for (i in 0 until p * 768) pixels.put(bb.getFloat())
        pixels.rewind()
        val positions = directLongs(p * 2)
        for (i in 0 until p * 2) positions.put(bb.getInt().toLong())
        positions.rewind()
        val (soft, _) = runVision(pixels, positions, p)
        return runLm(soft).first
    }

    override fun embedQuery(ids: IntArray): FloatArray {
        val seq = directFloats(ids.size * hidden)
        for (id in ids) table.rowInto(id, seq)
        seq.rewind()
        OnnxTensor.createTensor(env, seq, longArrayOf(1, ids.size.toLong(), hidden.toLong())).use { e ->
            lm.run(mapOf("inputs_embeds" to e)).use { r -> return floatsOf(r.get(0) as OnnxTensor) }
        }
    }

    override fun close() {
        vision.close(); lm.close()
    }

    private fun decodeForGemma(resolver: ContentResolver, uri: Uri, budget: Int): Bitmap {
        val src = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (h, w) = aspectRatioPreservingSize(info.size.height, info.size.width, 16, budget * 9, 3)
            decoder.setTargetSize(w, h)
        }
    }

    /** Port of get_aspect_ratio_preserving_size from transformers' Gemma4 image processor. */
    private fun aspectRatioPreservingSize(height: Int, width: Int, patch: Int, maxPatches: Int, pool: Int): Pair<Int, Int> {
        val targetPx = maxPatches * patch * patch
        val factor = sqrt(targetPx.toDouble() / (height.toDouble() * width))
        val side = pool * patch
        var th = floor(factor * height / side).toInt() * side
        var tw = floor(factor * width / side).toInt() * side
        val maxSide = (maxPatches / (pool * pool)) * side
        if (th == 0 && tw == 0) error("0x0 target for ${height}x$width")
        if (th == 0) {
            th = side; tw = minOf(floor(width.toDouble() / height).toInt() * side, maxSide)
        } else if (tw == 0) {
            tw = side; th = minOf(floor(height.toDouble() / width).toInt() * side, maxSide)
        }
        return th to tw
    }

    /** (C,H,W)/255 -> [P, 16*16*3] with feature order (y, x, c), positions (x, y), row-major over patches. */
    private fun patchify(bmp: Bitmap): Triple<FloatBuffer, LongBuffer, Int> {
        val w = bmp.width
        val h = bmp.height
        val pw = w / 16
        val ph = h / 16
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        val p = pw * ph
        val pixels = directFloats(p * 768)
        val positions = directLongs(p * 2)
        val buf = FloatArray(768)
        for (py in 0 until ph) for (px in 0 until pw) {
            var k = 0
            for (y in 0 until 16) {
                val row = (py * 16 + y) * w + px * 16
                for (x in 0 until 16) {
                    val c = argb[row + x]
                    buf[k++] = ((c shr 16) and 0xFF) / 255f
                    buf[k++] = ((c shr 8) and 0xFF) / 255f
                    buf[k++] = (c and 0xFF) / 255f
                }
            }
            pixels.put(buf)
            positions.put(px.toLong()).put(py.toLong())
        }
        pixels.rewind(); positions.rewind()
        return Triple(pixels, positions, p)
    }
}

/** SigLIP 2 Base 224: vision.onnx (pixels -> embedding), text.onnx (embedded tokens -> embedding), host-side table. */
class SiglipPipeline(dir: File, threads: Int) : Pipeline {
    override val dim = 768
    private val env = OrtEnvironment.getEnvironment()
    private val meta = loadMeta(dir)
    private val hidden = meta.getInt("hidden")
    private val table: TokenTable
    private val vision: OrtSession
    private val text: OrtSession
    override val loadTimesMs: Map<String, Double>

    init {
        var t = System.nanoTime()
        vision = env.createSession(File(dir, "vision.onnx").path, sessionOptions(threads))
        val vMs = elapsedMs(t)
        t = System.nanoTime()
        text = env.createSession(File(dir, "text.onnx").path, sessionOptions(threads))
        val tMs = elapsedMs(t)
        t = System.nanoTime()
        table = TokenTable(dir, meta, hidden)
        loadTimesMs = mapOf("vision_session" to vMs, "text_session" to tMs, "table" to elapsedMs(t))
    }

    private fun runVision(pixels: FloatBuffer): Pair<FloatArray, Double> {
        val t = System.nanoTime()
        OnnxTensor.createTensor(env, pixels, longArrayOf(1, 3, 224, 224)).use { pv ->
            vision.run(mapOf("pixel_values" to pv)).use { r -> return floatsOf(r.get(0) as OnnxTensor) to elapsedMs(t) }
        }
    }

    override fun embedImage(resolver: ContentResolver, uri: Uri): ImageTiming {
        val t0 = System.nanoTime()
        val src = ImageDecoder.createSource(resolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(224, 224)
        }
        val argb = IntArray(224 * 224)
        bmp.getPixels(argb, 0, 224, 0, 0, 224, 224)
        bmp.recycle()
        val pixels = directFloats(3 * 224 * 224)
        for (c in 0 until 3) {
            val shift = 16 - 8 * c
            for (i in argb.indices) pixels.put(c * 224 * 224 + i, (((argb[i] shr shift) and 0xFF) / 255f - 0.5f) / 0.5f)
        }
        pixels.rewind()
        val pre = elapsedMs(t0)
        val (emb, vMs) = runVision(pixels)
        return ImageTiming(emb, pre, vMs, 0.0)
    }

    /** Parity input: float32 [3,224,224] as written by make_reference_siglip.py. */
    override fun embedParityFile(file: File): FloatArray {
        val bb = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val pixels = directFloats(3 * 224 * 224)
        for (i in 0 until 3 * 224 * 224) pixels.put(bb.getFloat())
        pixels.rewind()
        return runVision(pixels).first
    }

    override fun embedQuery(ids: IntArray): FloatArray {
        val seq = directFloats(ids.size * hidden)
        for (id in ids) table.rowInto(id, seq)
        seq.rewind()
        OnnxTensor.createTensor(env, seq, longArrayOf(1, ids.size.toLong(), hidden.toLong())).use { e ->
            text.run(mapOf("inputs_embeds" to e)).use { r -> return floatsOf(r.get(0) as OnnxTensor) }
        }
    }

    override fun close() {
        vision.close(); text.close()
    }
}
