package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexRunStateStoreTest {

    @Test
    fun `in-memory run state transitions through running progress and success`() {
        val store = InMemoryMediaIndexRunStateStore()

        store.markStarted(startedAtMillis = 100L)
        store.updateProgress(selectedRows = 10, processedRows = 4, indexedRows = 3, skippedRows = 1)
        store.markSucceeded(
            finishedAtMillis = 200L,
            result = MediaIndexTierABatchResult(
                selectedRows = 10,
                indexedRows = 8,
                skippedRows = 2,
                deletedRows = 1,
                healedRows = 1
            )
        )

        val state = store.state.value
        assertFalse(state.isRunning)
        assertEquals(10, state.selectedRows)
        assertEquals(10, state.processedRows)
        assertEquals(8, state.indexedRows)
        assertEquals(2, state.skippedRows)
        assertEquals(1, state.deletedRows)
        assertEquals(1, state.healedRows)
        assertEquals(100L, state.lastRunStartedAtMillis)
        assertEquals(200L, state.lastRunFinishedAtMillis)
        assertEquals(200L, state.lastSuccessfulRunFinishedAtMillis)
        assertEquals(MediaIndexRunOutcome.SUCCESS, state.lastRunOutcome)
    }

    @Test
    fun `in-memory run state keeps last success when a later run fails`() {
        val store = InMemoryMediaIndexRunStateStore()

        store.markStarted(startedAtMillis = 100L)
        store.markSucceeded(
            finishedAtMillis = 150L,
            result = MediaIndexTierABatchResult(
                selectedRows = 1,
                indexedRows = 1,
                skippedRows = 0,
                deletedRows = 0,
                healedRows = 0
            )
        )

        store.markStarted(startedAtMillis = 300L)
        assertTrue(store.state.value.isRunning)

        store.markFailed(finishedAtMillis = 350L)

        val state = store.state.value
        assertFalse(state.isRunning)
        assertEquals(350L, state.lastRunFinishedAtMillis)
        assertEquals(150L, state.lastSuccessfulRunFinishedAtMillis)
        assertEquals(MediaIndexRunOutcome.FAILURE, state.lastRunOutcome)
        assertNull(state.lastRunErrorMessage)
    }
}
