package com.davide.seddio.easygallery.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Plain JVM. Gradle runs unit tests with the module directory as the working directory, so the files are read from disk.
class FacePipelineVersionTest {

    private val versionCatalog = File("../gradle/libs.versions.toml")
    private val modelChecksums = File("src/main/assets/face/SHA256SUMS")

    @Test
    fun `the OpenCV version the pipeline is stamped with is the one the version catalog pins`() {
        assertTrue("missing ${versionCatalog.absolutePath}", versionCatalog.isFile)
        val declared = Regex("""^opencv\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .find(versionCatalog.readText())?.groupValues?.get(1)

        assertEquals(
            "gradle/libs.versions.toml pins a different OpenCV build than the one FACE_PIPELINE_VERSION $FACE_PIPELINE_VERSION " +
                "is measured on. Another build moves boxes and would mix embeddings: bump FACE_PIPELINE_VERSION, update " +
                "FACE_PIPELINE_OPENCV_VERSION and the fingerprint below together.",
            FACE_PIPELINE_OPENCV_VERSION,
            declared
        )
    }

    @Test
    fun `every input to the pipeline is recorded against the version that names it`() {
        val recorded = RECORDED_FINGERPRINTS[FACE_PIPELINE_VERSION]

        assertEquals(
            "FACE_PIPELINE_VERSION is $FACE_PIPELINE_VERSION, and what it names changed or was never recorded. A change " +
                "to the OpenCV build, a model file, a decode or detection parameter or the size cut-off must bump the " +
                "version, so that stored faces and their embeddings are never mixed across pipelines; then add the new " +
                "fingerprint to RECORDED_FINGERPRINTS and keep the old one.",
            recorded,
            currentFingerprint()
        )
    }

    @Test
    fun `no two recorded versions share a fingerprint`() {
        val fingerprints = RECORDED_FINGERPRINTS.values

        assertEquals(fingerprints.size, fingerprints.toSet().size)
    }

    @Test
    fun `the capability key carries the pipeline version so a bump asks the question again`() {
        val current = FaceCapabilityKey(appVersionCode = 9, pipelineVersion = FACE_PIPELINE_VERSION, buildFingerprint = "fp")
        val bumped = current.copy(pipelineVersion = FACE_PIPELINE_VERSION + 1)

        assertNotEquals(current.asStoredKey(), bumped.asStoredKey())
    }

    @Test
    fun `the model checksums list both models and nothing else`() {
        assertTrue("missing ${modelChecksums.absolutePath}", modelChecksums.isFile)

        assertEquals(
            setOf(FACE_DETECTOR_ASSET.removePrefix("face/"), FACE_RECOGNISER_ASSET.removePrefix("face/")),
            modelChecksumsByName().keys
        )
    }

    private fun currentFingerprint(): String {
        val checksums = modelChecksumsByName()
        return listOf(
            "opencv=$FACE_PIPELINE_OPENCV_VERSION",
            "decode=$FACE_DECODE_LONG_SIDE",
            "detect=$FACE_DETECT_LONG_SIDE",
            "score=$FACE_SCORE_THRESHOLD",
            "minSide=$FACE_MIN_SIDE_PIXELS",
            "yunet=${checksums.getValue(FACE_DETECTOR_ASSET.removePrefix("face/"))}",
            "sface=${checksums.getValue(FACE_RECOGNISER_ASSET.removePrefix("face/"))}"
        ).joinToString(";")
    }

    private fun modelChecksumsByName(): Map<String, String> =
        modelChecksums.readLines().filter { it.isNotBlank() }.associate { line ->
            val (hash, name) = line.trim().split(Regex("\\s+"), limit = 2)
            name.trimStart('*') to hash.lowercase()
        }

    private companion object {
        val RECORDED_FINGERPRINTS = mapOf(
            1 to "opencv=4.13.0;decode=1280;detect=640;score=0.7;minSide=40;" +
                "yunet=8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4;" +
                "sface=0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79"
        )
    }
}
