package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumNamingTest {

    private val format: (Int) -> String = { "My album $it" }

    @Test
    fun `first suggestion is used when nothing exists`() {
        val suggestion = AlbumNaming.suggestName(emptyList(), format)

        assertEquals("My album 1", suggestion)
    }

    @Test
    fun `collisions are skipped in order`() {
        val suggestion = AlbumNaming.suggestName(listOf("My album 1", "My album 2"), format)

        assertEquals("My album 3", suggestion)
    }

    @Test
    fun `a gap is filled rather than appended past it`() {
        val suggestion = AlbumNaming.suggestName(listOf("My album 1", "My album 3"), format)

        assertEquals("My album 2", suggestion)
    }

    @Test
    fun `collision matching ignores case and surrounding space`() {
        val suggestion = AlbumNaming.suggestName(listOf("  MY ALBUM 1 "), format)

        assertEquals("My album 2", suggestion)
    }

    @Test
    fun `unrelated album names never collide`() {
        val suggestion = AlbumNaming.suggestName(listOf("Trips", "Castles"), format)

        assertEquals("My album 1", suggestion)
    }
}
