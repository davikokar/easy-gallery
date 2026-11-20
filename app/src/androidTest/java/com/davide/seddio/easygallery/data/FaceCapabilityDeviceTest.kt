package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core

/**
 * ADR-0023 §2, §3: OpenCV ships for arm64-v8a only, so the pipeline is capable exactly where the app runs as arm64, and
 * incapable everywhere else. Each test asserts the arm64 outcome on an arm64 device and the opposite on any other, so
 * neither passes vacuously whichever device is attached. Emulators are expected to land on the second branch.
 */
@RunWith(AndroidJUnit4::class)
class FaceCapabilityDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun capabilityIsReportedExactlyWhereTheAppRunsAsArm64() {
        val runsArm64 = appRunsAsArm64()
        val checker = FaceCapabilityChecker(
            store = InMemoryFaceDeviceStateStore(),
            key = FaceCapabilityKey(appVersionCode = 1L, pipelineVersion = FACE_PIPELINE_VERSION, buildFingerprint = Build.FINGERPRINT),
            pipelineLoader = { OpenCvFacePipeline.load(context) }
        )

        val capable = checker.isCapable()

        Log.i(TAG, "abi=${nativeAbi()} supported=${Build.SUPPORTED_ABIS.joinToString(",")} runsArm64=$runsArm64 capable=$capable")
        assertEquals(
            "Capable must equal whether the app process runs as arm64 (abi=${nativeAbi()}, 64-bit=${Process.is64Bit()})",
            runsArm64,
            capable
        )
    }

    @Test
    fun theLibraryAndModelsLoadOnArm64AndAreUnavailableElsewhere() {
        if (!appRunsAsArm64()) {
            try {
                OpenCvFacePipeline.load(context).close()
                fail("The pipeline loaded on abi=${nativeAbi()}, where OpenCV is not packaged")
            } catch (_: FacePipelineUnavailableException) {
                Log.i(TAG, "load: unavailable on abi=${nativeAbi()}, as expected")
            }
            return
        }

        OpenCvFacePipeline.load(context).use { pipeline ->
            val settings = pipeline.detectorSettings()
            val selfTest = pipeline.selfTest()

            // The values FACE_PIPELINE_VERSION's KDoc promises: OpenCV's defaults for NMS and top-K.
            assertEquals(FACE_SCORE_THRESHOLD, settings.scoreThreshold, 1e-6f)
            assertEquals(0.3f, settings.nmsThreshold, 1e-6f)
            assertEquals(5000, settings.topK)
            assertTrue("detection output of the synthetic input is not a well-formed table", selfTest.detectionOutputValid)
            val embedding = checkNotNull(selfTest.embedding) { "no embedding came out of the self-test" }
            assertEquals(FACE_EMBEDDING_SIZE, embedding.size)
            assertTrue("embedding has a non-finite value", embedding.all { it.isFinite() })
            assertTrue("embedding norm is zero", embedding.sumOf { it.toDouble() * it.toDouble() } > 0.0)
            // Core.VERSION is compiled into the AAR's Java classes; getVersionString() asks the native library that was loaded.
            assertEquals(FACE_PIPELINE_OPENCV_VERSION, Core.VERSION)
            assertEquals(FACE_PIPELINE_OPENCV_VERSION, Core.getVersionString())
            Log.i(TAG, "load: capable on abi=${nativeAbi()} embeddingSize=${embedding.size}")
            Core.getBuildInformation().lines().forEach { Log.i(BUILD_INFO_TAG, it) }
        }
    }

    // The folder Android extracts or maps the app's libraries from is named for the ABI the process runs as.
    private fun nativeAbi(): String = File(context.applicationInfo.nativeLibraryDir).name

    private fun appRunsAsArm64(): Boolean = nativeAbi() == "arm64" && Process.is64Bit()

    private companion object {
        const val TAG = "FaceCapabilityDeviceTest"
        const val BUILD_INFO_TAG = "OpenCvBuildInfo"
    }
}
