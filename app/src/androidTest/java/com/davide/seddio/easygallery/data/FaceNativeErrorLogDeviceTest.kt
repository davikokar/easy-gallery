package com.davide.seddio.easygallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 3.7, G5a (item 6): whether OpenCV's native errors print under logcat's `cv::error()` tag, outside the pass's
 * fixed lines (ADR-0023 *Built*, ADR-0024 §6). One native error is provoked through the pipeline's own call path,
 * [OpenCvFacePipeline.analyse], on a synthetic image one pixel wide and 1,000 pixels tall: the pipeline scales it to
 * 0.64 by 640 px, an empty size, so OpenCV's `resize` refuses it with a `CvException`, which the pipeline reports as
 * [FacePipelineFailure.INFERENCE]. The image is a flat grey and holds no face; it lives in the app's cache directory
 * and is deleted afterwards.
 *
 * Opt-in: skipped unless the instrumentation argument [OPT_IN_ARGUMENT] is `true`, e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.faceNativeErrorLog=true`. It records what logcat holds and does not
 * judge it, unless [EXPECT_ARGUMENT] is `silent` (the run after a fix, which then fails on any `cv::error()` line;
 * the JNI wrapper's `org.opencv.imgproc` line stays, ADR-0024 §6, and is counted and logged only). Only this process's
 * lines between a start and an end marker are counted. What it logs, under [TAG]: a count, then the priority, the
 * process and the length of each line.
 */
@RunWith(AndroidJUnit4::class)
class FaceNativeErrorLogDeviceTest {

    @Test
    fun aNativeOpenCvErrorIsProvokedThroughThePipelineAndWhatLogcatHoldsIsRecorded() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Opt-in: pass the instrumentation argument $OPT_IN_ARGUMENT=true to run this test",
            arguments.getString(OPT_IN_ARGUMENT) == "true"
        )
        val app = ApplicationProvider.getApplicationContext<Context>()
        val image = File(app.cacheDir, "g2-native-error-${UUID.randomUUID()}.png")
        try {
            writeTallGreyPng(image)
            val pipeline = try {
                OpenCvFacePipeline.load(app)
            } catch (_: FacePipelineUnavailableException) {
                throw AssertionError("ENVIRONMENT: the OpenCV library is not in this process, so no native error can be provoked.")
            }
            val marker = UUID.randomUUID().toString().take(8)
            Log.i(TAG, "start=$marker")

            val failure = pipeline.use { runCatching { it.analyse(Uri.fromFile(image).toString()) }.exceptionOrNull() }

            val refusal = failure as? FacePipelineException
                ?: throw AssertionError("The pipeline must refuse the image with a FacePipelineException, not: $failure")
            assertEquals(
                "The refusal must be the native one, reported as an inference failure",
                FacePipelineFailure.INFERENCE,
                refusal.failure
            )
            // Lines of one process reach logd in order, so the end marker's arrival means every earlier line of this process is in.
            Log.i(TAG, "end=$marker")
            val lines = logcatBetweenMarkers(marker)
            val cvLines = lines.filter { it.tag == CV_ERROR_TAG }
            val otherOpenCvLines = lines.filter { it.tag != CV_ERROR_TAG && it.message.contains("OpenCV(") }

            Log.i(
                TAG,
                "provoked: failure=${refusal.failure} cvErrorLines=${cvLines.size} otherTagOpenCvLines=${otherOpenCvLines.size} " +
                    "linesAfterMarker=${lines.size}"
            )
            cvLines.take(MAX_LINES_LOGGED).forEachIndexed { index, line ->
                Log.i(
                    TAG,
                    "cvErrorLine index=$index priority=${line.priority} ownProcess=${line.pid == Process.myPid()} length=${line.message.length}"
                )
            }
            // The JNI wrapper's line under org.opencv.imgproc survives Core.setErrorVerbosity(false), the accepted limit L10, so it is not asserted.
            if (arguments.getString(EXPECT_ARGUMENT) == "silent") {
                assertEquals("Nothing may print under $CV_ERROR_TAG once the pipeline silences it", 0, cvLines.size)
            }
        } finally {
            image.delete()
        }
    }

    private fun writeTallGreyPng(file: File) {
        val bitmap = Bitmap.createBitmap(1, TALL_HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "The synthetic image could not be written" } }
        bitmap.recycle()
    }

    // ---- logcat ----

    private class LogLine(val pid: Int, val priority: String, val tag: String, val message: String)

    // threadtime: "11-21 10:12:13.123  1234  5678 I Tag: message". A tag may hold colons, so it is cut at the last ": " that starts the message.
    // Polls until the end marker is in logcat, then returns this process's lines between the two markers.
    private fun logcatBetweenMarkers(marker: String): List<LogLine> {
        val deadline = SystemClock.elapsedRealtime() + MARKER_WAIT_MILLIS
        while (true) {
            val all = shell("logcat -d -v threadtime").lines()
            val endIndex = all.indexOfLast { it.contains("$TAG: end=$marker") }
            val startIndex = all.indexOfLast { it.contains("$TAG: start=$marker") }
            if (endIndex >= 0 && startIndex in 0 until endIndex) {
                return all.subList(startIndex + 1, endIndex).mapNotNull { line ->
                    val head = HEAD.find(line) ?: return@mapNotNull null
                    val rest = line.substring(head.range.last + 1)
                    val split = rest.indexOf(": ")
                    if (split < 0) return@mapNotNull null
                    LogLine(head.groupValues[1].toInt(), head.groupValues[3], rest.substring(0, split).trim(), rest.substring(split + 2))
                }.filter { it.pid == Process.myPid() }
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                throw AssertionError("ENVIRONMENT: the markers of $marker did not both reach logcat within $MARKER_WAIT_MILLIS ms (start at $startIndex, end at $endIndex)")
            }
            SystemClock.sleep(MARKER_POLL_MILLIS)
        }
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }

    private companion object {
        const val TAG = "FaceNativeErrorLogTest"
        const val CV_ERROR_TAG = "cv::error()"
        const val OPT_IN_ARGUMENT = "faceNativeErrorLog"
        const val EXPECT_ARGUMENT = "faceNativeErrorLogExpect"

        // Above FACE_DETECT_LONG_SIDE, so that the pipeline scales it, and a width of one, so that the scaled width is zero.
        const val TALL_HEIGHT = 1_000
        const val MARKER_WAIT_MILLIS = 10_000L
        const val MARKER_POLL_MILLIS = 100L
        const val MAX_LINES_LOGGED = 5

        // "11-21 10:12:13.123  1234  5678 I " up to the tag.
        val HEAD = Regex("""^\S+\s+\S+\s+(\d+)\s+(\d+)\s+([A-Z])\s""")
    }
}
