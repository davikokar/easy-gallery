package com.davide.seddio.easygallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-0018 Amendment 6, the table of what a deletion may report as scrubbed, one row per test. */
class MediaIndexScrubModeTest {

    @Test
    fun `wal reports what the checkpoint found`() {
        assertTrue(deletionIsScrubbed("wal", "normal") { true })
        assertFalse(deletionIsScrubbed("wal", "normal") { false })
    }

    @Test
    fun `delete and truncate are scrubbed under normal locking`() {
        assertTrue(deletionIsScrubbed("delete", "normal") { false })
        assertTrue(deletionIsScrubbed("truncate", "normal") { false })
    }

    @Test
    fun `delete and truncate are not scrubbed under exclusive locking`() {
        assertFalse(deletionIsScrubbed("delete", "exclusive") { true })
        assertFalse(deletionIsScrubbed("truncate", "exclusive") { true })
    }

    @Test
    fun `memory and off are scrubbed whatever the locking mode`() {
        listOf("memory", "off").forEach { mode ->
            listOf("normal", "exclusive").forEach { locking ->
                assertTrue("$mode $locking", deletionIsScrubbed(mode, locking) { false })
            }
        }
    }

    @Test
    fun `persist is never scrubbed`() {
        listOf("normal", "exclusive").forEach { locking ->
            assertFalse(locking, deletionIsScrubbed("persist", locking) { true })
        }
    }

    @Test
    fun `a journal mode that is not recognised is not scrubbed`() {
        listOf("", "something-new", "wal2").forEach { mode ->
            assertFalse(mode, deletionIsScrubbed(mode, "normal") { true })
        }
    }

    @Test
    fun `a locking mode that is not recognised is not scrubbed under a rollback journal`() {
        assertFalse(deletionIsScrubbed("truncate", "") { true })
        assertFalse(deletionIsScrubbed("delete", "something-new") { true })
    }

    @Test
    fun `the modes compare without depending on case`() {
        assertTrue(deletionIsScrubbed("WAL", "NORMAL") { true })
        assertTrue(deletionIsScrubbed("Truncate", "Normal") { false })
        assertTrue(deletionIsScrubbed("MEMORY", "EXCLUSIVE") { false })
        assertFalse(deletionIsScrubbed("PERSIST", "NORMAL") { true })
        assertFalse(deletionIsScrubbed("Delete", "EXCLUSIVE") { true })
    }

    @Test
    fun `the checkpoint is asked only for wal`() {
        listOf("delete", "truncate", "memory", "off", "persist", "unknown").forEach { mode ->
            deletionIsScrubbed(mode, "normal") { throw AssertionError("the checkpoint was asked under $mode") }
        }
    }
}
