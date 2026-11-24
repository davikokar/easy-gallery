package com.davide.seddio.easygallery.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexTierCTest {

    private val identity = mediaIdentityOf("content://media/external/images/media/77", "secret-name.jpg", 1_234L, 5_678L)
    private val box = FaceBox(0.125f, 0.25f, 0.5f, 0.75f)
    private val embedding = FaceEmbedding(floatArrayOf(0.15625f, -2.5f, 3.0e-5f, 1.0e9f))

    @Test
    fun `an embedding encodes as float32 little endian`() {
        val blob = encodeFaceEmbedding(FaceEmbedding(floatArrayOf(1.0f, -2.0f)))

        // 1.0f is 0x3F800000 and -2.0f is 0xC0000000; little endian puts the low byte first.
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F, 0x00, 0x00, 0x00, 0xC0.toByte()),
            blob
        )
    }

    @Test
    fun `an embedding survives a round trip of any length`() {
        listOf(1, 2, 128, 512).forEach { length ->
            val values = FloatArray(length) { (it - length / 2) * 0.0123f }
            val decoded = decodeFaceEmbedding(encodeFaceEmbedding(FaceEmbedding(values)))

            assertEquals(length, decoded.size)
            assertArrayEquals(values, decoded.toFloatArray(), 0f)
        }
    }

    @Test
    fun `decoding rejects an empty blob and a length that is not whole floats`() {
        assertThrows(IllegalArgumentException::class.java) { decodeFaceEmbedding(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { decodeFaceEmbedding(ByteArray(7)) }
    }

    @Test
    fun `an embedding rejects an empty vector and non finite values`() {
        assertThrows(IllegalArgumentException::class.java) { FaceEmbedding(FloatArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { FaceEmbedding(floatArrayOf(1f, Float.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { FaceEmbedding(floatArrayOf(Float.POSITIVE_INFINITY)) }
    }

    @Test
    fun `embeddings compare by content and are not changed through the source or the copy`() {
        val source = floatArrayOf(1f, 2f, 3f)
        val first = FaceEmbedding(source)
        val second = FaceEmbedding(floatArrayOf(1f, 2f, 3f))

        source[0] = 99f
        first.toFloatArray()[1] = 99f

        assertEquals(second, first)
        assertEquals(second.hashCode(), first.hashCode())
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), first.toFloatArray(), 0f)
        assertNotEquals(FaceEmbedding(floatArrayOf(1f, 2f, 4f)), first)
    }

    @Test
    fun `a box must lie inside the photo and have a positive size`() {
        assertEquals(FaceBox(0f, 0f, 1f, 1f), FaceBox(0f, 0f, 1f, 1f))
        listOf(
            { FaceBox(-0.01f, 0.1f, 0.5f, 0.5f) },
            { FaceBox(0.1f, -0.01f, 0.5f, 0.5f) },
            { FaceBox(0.1f, 0.1f, 1.01f, 0.5f) },
            { FaceBox(0.1f, 0.1f, 0.5f, 1.01f) },
            { FaceBox(0.5f, 0.1f, 0.5f, 0.5f) },
            { FaceBox(0.6f, 0.1f, 0.5f, 0.5f) },
            { FaceBox(0.1f, 0.5f, 0.5f, 0.5f) },
            { FaceBox(0.1f, 0.6f, 0.5f, 0.5f) },
            { FaceBox(Float.NaN, 0.1f, 0.5f, 0.5f) }
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }

    @Test
    fun `a failed write carries no face and an indexed write may carry none`() {
        val detection = FaceDetection(box, embedding)

        assertThrows(IllegalArgumentException::class.java) {
            FaceIndexWrite(identity, 1, FaceIndexOutcome.FAILED, listOf(detection))
        }
        assertEquals(0, FaceIndexWrite(identity, 1, FaceIndexOutcome.FAILED, emptyList()).faces.size)
        assertEquals(0, FaceIndexWrite(identity, 1, FaceIndexOutcome.INDEXED, emptyList()).faces.size)
        assertEquals(1, FaceIndexWrite(identity, 1, FaceIndexOutcome.INDEXED, listOf(detection)).faces.size)
    }

    @Test
    fun `no toString carries a box an embedding or a uri`() {
        val detection = FaceDetection(box, embedding)
        val stored = StoredFace(
            id = 9_001L,
            mediaUri = identity.mediaUri,
            pipelineVersion = 31_337,
            box = box,
            embedding = embedding,
            groupId = 4_242L,
            groupingVersion = 8_675
        )
        val texts = listOf(
            box.toString(),
            embedding.toString(),
            detection.toString(),
            FaceIndexWrite(identity, 31_337, FaceIndexOutcome.INDEXED, listOf(detection)).toString(),
            FaceIndexState(identity.mediaUri, 31_337, FaceIndexOutcome.FAILED).toString(),
            stored.toString(),
            MediaIndexFaceStateEntity(identity.mediaUri, 31_337, FaceIndexOutcome.INDEXED).toString(),
            MediaIndexFaceEntity(
                id = 9_001L,
                mediaUri = identity.mediaUri,
                pipelineVersion = 31_337,
                boxLeft = 0.125f,
                boxTop = 0.25f,
                boxRight = 0.5f,
                boxBottom = 0.75f,
                embedding = encodeFaceEmbedding(embedding),
                groupId = 4_242L,
                groupingVersion = 8_675
            ).toString()
        )
        val forbidden = listOf(
            "content://", "secret-name", "77", "0.125", "0.25", "0.5", "0.75", "0.15625", "-2.5", "3.0E-5", "1.0E9",
            "9001", "31337", "4242", "8675"
        )

        texts.forEach { text ->
            forbidden.forEach { value -> assertFalse("$text contains $value", text.contains(value)) }
            assertTrue(text.contains("redacted"))
        }
    }

    @Test
    fun `a check message names the rule and never the value`() {
        val messages = listOf(
            assertThrows(IllegalArgumentException::class.java) { FaceBox(0.987654f, 0.1f, 0.5f, 0.5f) },
            assertThrows(IllegalArgumentException::class.java) { FaceEmbedding(floatArrayOf(1.2345f, Float.NaN)) },
            assertThrows(IllegalArgumentException::class.java) { decodeFaceEmbedding(ByteArray(7)) },
            assertThrows(IllegalArgumentException::class.java) {
                FaceIndexWrite(identity, 1, FaceIndexOutcome.FAILED, listOf(FaceDetection(box, embedding)))
            }
        ).map { it.message.orEmpty() }

        messages.forEach { message ->
            listOf("0.9876", "1.2345", "content://", "secret-name", "0.125", "-2.5").forEach { value ->
                assertFalse("$message contains $value", message.contains(value))
            }
        }
    }
}
