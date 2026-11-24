package com.davide.seddio.easygallery.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * What no backup, device transfer or cross-platform transfer may carry (ADR-0018 Amendments 5 and 7; plan 3.3, Task 1,
 * criterion 10). The rules are read as files from the module directory, Gradle's working directory for unit tests.
 * Only `<exclude>` is ever written: the first `<include>` anywhere would flip that mode to include-only and drop the
 * album database from a backup that is meant to carry it (ADR-0014).
 */
class BackupRulesParityTest {

    private val rulesDir = File("src/main/res/xml")

    // Written out, not derived: renaming a file in production must fail here until its rule is renamed too.
    private val expectedExcludes = setOf(
        Exclude("database", "media_index.db"),
        Exclude("database", "media_index.db-wal"),
        Exclude("database", "media_index.db-shm"),
        Exclude("database", "media_index.db-journal"),
        Exclude("sharedpref", "media_index_run_state.xml"),
        Exclude("sharedpref", "face_device_state.xml")
    )

    @Test
    fun `no rules file has an include anywhere`() {
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            assertEquals("$name has an <include>", 0, parse(name).getElementsByTagName("include").length)
        }
    }

    @Test
    fun `full backup rules exclude exactly the expected files`() {
        val root = parse("backup_rules.xml").documentElement

        assertEquals("full-backup-content", root.tagName)
        assertEquals(expectedExcludes, excludesIn(root))
    }

    @Test
    fun `cloud backup excludes exactly the expected files`() {
        assertEquals(expectedExcludes, excludesIn(section("cloud-backup")))
    }

    @Test
    fun `device transfer excludes exactly the expected files`() {
        assertEquals(expectedExcludes, excludesIn(section("device-transfer")))
    }

    @Test
    fun `cross platform transfer excludes exactly the expected files`() {
        val element = section("cross-platform-transfer")

        assertEquals("ios", element.getAttribute("platform"))
        assertEquals(expectedExcludes, excludesIn(element))
    }

    @Test
    fun `the extraction rules hold these three sections and no other`() {
        val sections = children(parse("data_extraction_rules.xml").documentElement).map { it.tagName }

        assertEquals(listOf("cloud-backup", "device-transfer", "cross-platform-transfer"), sections)
    }

    @Test
    fun `no section lists an exclusion twice and every exclusion names a domain and a path`() {
        val sections = listOf(parse("backup_rules.xml").documentElement) +
            children(parse("data_extraction_rules.xml").documentElement)

        sections.forEach { element ->
            val all = children(element).filter { it.tagName == "exclude" }.map { excludeOf(it) }
            assertEquals("duplicate in <${element.tagName}>", all.size, all.toSet().size)
            all.forEach { assertTrue(it.domain.isNotEmpty() && it.path.isNotEmpty()) }
        }
    }

    @Test
    fun `the album database and app_settings, which carry the setting and the albums, stay backed up`() {
        val everything = listOf("backup_rules.xml", "data_extraction_rules.xml").flatMap { name ->
            excludesIn(parse(name).documentElement, deep = true)
        }

        assertFalse(everything.any { it.path.startsWith("easy_gallery.db") || it.path == "app_settings.xml" })
    }

    @Test
    fun `the face device state file is the one the store writes`() {
        assertTrue(expectedExcludes.contains(Exclude("sharedpref", "$FACE_DEVICE_STATE_FILE.xml")))
    }

    private fun parse(name: String): Document {
        val file = File(rulesDir, name)
        assertTrue("missing ${file.absolutePath}", file.exists())
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun section(tag: String): Element {
        val matching = children(parse("data_extraction_rules.xml").documentElement).filter { it.tagName == tag }
        assertEquals("expected exactly one <$tag>", 1, matching.size)
        return matching.single()
    }

    private fun children(parent: Element): List<Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun excludesIn(element: Element, deep: Boolean = false): Set<Exclude> {
        val direct = children(element).filter { it.tagName == "exclude" }.map { excludeOf(it) }
        if (!deep) {
            val others = children(element).filter { it.tagName != "exclude" }.map { it.tagName }
            assertEquals("<${element.tagName}> holds a child that is not an <exclude>", emptyList<String>(), others)
            return direct.toSet()
        }
        return (direct + children(element).flatMap { excludesIn(it, deep = true) }).toSet()
    }

    private fun excludeOf(element: Element) = Exclude(element.getAttribute("domain"), element.getAttribute("path"))

    private data class Exclude(val domain: String, val path: String)
}
