package com.davide.seddio.easygallery.ui.components

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Plan 3.6, Task A, criterion 6: the five notices, in order, verbatim. */
class FaceLicencesTest {

    private val assetsDirectory = File("src/main/assets/face")

    @Test
    fun `the five notices are listed in the planned order`() {
        assertEquals(
            listOf(
                "NOTICE.txt",
                "LICENSE-OpenCV-Apache-2.0.txt",
                "LICENSE-SFace-Apache-2.0.txt",
                "LICENSE-YuNet-MIT.txt",
                "THIRD_PARTY_NOTICES.txt"
            ),
            FACE_LICENCE_FILE_NAMES
        )
    }

    @Test
    fun `every listed notice is a file in the face assets`() {
        FACE_LICENCE_FILE_NAMES.forEach { name ->
            assertTrue("$name is under assets/$FACE_LICENCE_ASSET_DIRECTORY", File(assetsDirectory, name).isFile)
        }
    }

    @Test
    fun `reading the real assets keeps every non blank line in order and drops nothing`() {
        val read = readFaceLicences { name -> File(assetsDirectory, name).inputStream() }

        assertEquals(FACE_LICENCE_FILE_NAMES, read.map { it.fileName })
        read.forEach { file ->
            val original = File(assetsDirectory, file.fileName).readText(Charsets.UTF_8)
                .replace("\r\n", "\n").lines().filter { it.isNotBlank() }
            assertTrue("${file.fileName} is not empty", original.isNotEmpty())
            assertEquals(file.fileName, original, file.paragraphs.joinToString("\n").lines())
        }
    }

    @Test
    fun `a blank line ends a paragraph and nothing else does`() {
        val text = "first line\nsecond line\n\nthird\n\n\n   \nfourth"

        assertEquals(listOf("first line\nsecond line", "third", "fourth"), splitNoticeParagraphs(text))
    }

    @Test
    fun `indentation and trailing spaces inside a paragraph are kept`() {
        val text = "  indented\n    more \nflush"

        assertEquals(listOf("  indented\n    more \nflush"), splitNoticeParagraphs(text))
    }

    @Test
    fun `windows line endings split the same as unix ones`() {
        assertEquals(listOf("a\nb", "c"), splitNoticeParagraphs("a\r\nb\r\n\r\nc\r\n"))
    }

    @Test
    fun `an empty text has no paragraphs`() {
        assertEquals(emptyList<String>(), splitNoticeParagraphs(""))
        assertEquals(emptyList<String>(), splitNoticeParagraphs("\n \n"))
    }

    @Test
    fun `a notice that cannot be opened fails the whole read`() {
        try {
            readFaceLicences { name ->
                if (name == "LICENSE-YuNet-MIT.txt") throw FileNotFoundException(name)
                ByteArrayInputStream("text".toByteArray())
            }
            fail("expected the read to fail")
        } catch (expected: FileNotFoundException) {
            assertEquals("LICENSE-YuNet-MIT.txt", expected.message)
        }
    }
}
