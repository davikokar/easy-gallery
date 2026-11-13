package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.MediaIndexingWorkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticAlbumIndexBandTest {

    private fun state(
        workStatus: MediaIndexingWorkStatus = MediaIndexingWorkStatus.IDLE,
        indexed: Int = 0,
        notYetIndexed: Int = 0
    ) = MediaIndexingUiState(
        workStatus = workStatus,
        isRunning = workStatus == MediaIndexingWorkStatus.RUNNING,
        selectedRows = 0,
        processedRows = 0,
        indexedRows = 0,
        skippedRows = 0,
        deletedRows = 0,
        healedRows = 0,
        indexedItemsCount = indexed,
        indexedWithDateCount = 0,
        indexedWithoutDateCount = 0,
        indexedWithoutLocationCount = 0,
        notYetIndexedCount = notYetIndexed,
        lastRunStartedAtMillis = null,
        lastRunFinishedAtMillis = null,
        lastSuccessfulRunFinishedAtMillis = null,
        lastRunOutcome = null,
        lastRunErrorMessage = null,
        locationAccessGranted = true
    )

    @Test
    fun `nothing waiting and nothing running is hidden`() {
        assertEquals(AutomaticAlbumIndexBand.Hidden, automaticAlbumIndexBand(state(indexed = 40, notYetIndexed = 0)))
    }

    @Test
    fun `an empty library is hidden rather than offering Index now`() {
        assertEquals(AutomaticAlbumIndexBand.Hidden, automaticAlbumIndexBand(state(indexed = 0, notYetIndexed = 0)))
    }

    @Test
    fun `the all-zero initial state is hidden`() {
        assertEquals(AutomaticAlbumIndexBand.Hidden, automaticAlbumIndexBand(state()))
    }

    @Test
    fun `a running pass is Running whatever the counts say`() {
        val running = MediaIndexingWorkStatus.RUNNING

        assertEquals(AutomaticAlbumIndexBand.Running, automaticAlbumIndexBand(state(running, indexed = 0, notYetIndexed = 10)))
        assertEquals(AutomaticAlbumIndexBand.Running, automaticAlbumIndexBand(state(running, indexed = 5, notYetIndexed = 5)))
        assertEquals(AutomaticAlbumIndexBand.Running, automaticAlbumIndexBand(state(running, indexed = 10, notYetIndexed = 0)))
    }

    @Test
    fun `media waiting carries its count`() {
        assertEquals(
            AutomaticAlbumIndexBand.NotIndexed(count = 1, nothingIndexed = false),
            automaticAlbumIndexBand(state(indexed = 7, notYetIndexed = 1))
        )
        assertEquals(
            AutomaticAlbumIndexBand.NotIndexed(count = 1234, nothingIndexed = false),
            automaticAlbumIndexBand(state(indexed = 2, notYetIndexed = 1234))
        )
    }

    @Test
    fun `nothing indexed with media waiting says so`() {
        assertEquals(
            AutomaticAlbumIndexBand.NotIndexed(count = 12, nothingIndexed = true),
            automaticAlbumIndexBand(state(indexed = 0, notYetIndexed = 12))
        )
    }

    @Test
    fun `WAITING between periodic runs counts as idle`() {
        val waiting = MediaIndexingWorkStatus.WAITING

        assertEquals(
            AutomaticAlbumIndexBand.NotIndexed(count = 3, nothingIndexed = true),
            automaticAlbumIndexBand(state(waiting, indexed = 0, notYetIndexed = 3))
        )
        assertEquals(
            AutomaticAlbumIndexBand.NotIndexed(count = 3, nothingIndexed = false),
            automaticAlbumIndexBand(state(waiting, indexed = 2, notYetIndexed = 3))
        )
        assertEquals(AutomaticAlbumIndexBand.Hidden, automaticAlbumIndexBand(state(waiting, indexed = 5, notYetIndexed = 0)))
    }

    @Test
    fun `an empty list may still fill in only while running or with nothing indexed`() {
        val mayFill = listOf(
            AutomaticAlbumIndexBand.Running to true,
            AutomaticAlbumIndexBand.NotIndexed(count = 4, nothingIndexed = true) to true,
            AutomaticAlbumIndexBand.NotIndexed(count = 4, nothingIndexed = false) to false,
            AutomaticAlbumIndexBand.Hidden to false
        )

        mayFill.forEach { (band, expected) ->
            assertEquals(band.toString(), expected, band.emptyListMayStillFill())
        }
    }

    @Test
    fun `the first pass over an unindexed library still may not claim an empty list is final`() {
        val running = MediaIndexingWorkStatus.RUNNING

        assertTrue(automaticAlbumIndexBand(state(running, indexed = 0, notYetIndexed = 10)).emptyListMayStillFill())
        assertFalse(automaticAlbumIndexBand(state(indexed = 10, notYetIndexed = 0)).emptyListMayStillFill())
    }
}
