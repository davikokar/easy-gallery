package com.davide.seddio.easygallery.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PRIMARY_VOLUME = "external_primary"
private const val COLUMN_ID = "_id"
private const val COLUMN_DATE_MODIFIED = "date_modified"
private const val COLUMN_GENERATION_ADDED = "generation_added"
private const val COLUMN_GENERATION_MODIFIED = "generation_modified"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class AndroidMediaStoreRowDeltaProviderTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    @Test
    fun `api 30 plus projects generation columns and maps observed rows`() {
        every { context.contentResolver } returns contentResolver

        val imageCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1,
                COLUMN_GENERATION_ADDED to 2,
                COLUMN_GENERATION_MODIFIED to 3
            ),
            rows = listOf(mapOf(0 to 1L, 1 to 100L, 2 to 10L, 3 to 11L))
        )
        val videoCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1,
                COLUMN_GENERATION_ADDED to 2,
                COLUMN_GENERATION_MODIFIED to 3
            ),
            rows = listOf(mapOf(0 to 2L, 1 to 200L, 2 to null, 3 to 15L))
        )

        val projections = mutableListOf<Array<String>>()
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } answers {
            projections += secondArg<Array<String>>()
            when (projections.size) {
                1 -> imageCursor
                2 -> videoCursor
                else -> null
            }
        }

        val provider = AndroidMediaStoreRowDeltaProvider(context)
        val rows = provider.queryObservedRows(
            volumeName = PRIMARY_VOLUME,
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS
        )

        assertEquals(2, rows.size)
        assertEquals(2, projections.size)
        assertEquals(
            listOf(
                "content://media/external/images/media/1",
                "content://media/external/video/media/2"
            ),
            rows.map { it.mediaUri }
        )
        assertEquals(listOf(10L, null), rows.map { it.generationAdded })
        assertEquals(listOf(11L, 15L), rows.map { it.generationModified })
        assertEquals(listOf(100L, 200L), rows.map { it.dateModifiedSeconds })
        assertEquals(listOf(PRIMARY_VOLUME, PRIMARY_VOLUME), rows.map { it.volumeName })
        assertEquals(
            setOf(
                COLUMN_ID,
                COLUMN_DATE_MODIFIED,
                COLUMN_GENERATION_ADDED,
                COLUMN_GENERATION_MODIFIED
            ),
            projections.first().toSet()
        )
    }

    @Test
    fun `api 29 query omits generation columns and returns null generation values`() {
        every { context.contentResolver } returns contentResolver

        val imageCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1
            ),
            rows = listOf(mapOf(0 to 7L, 1 to 321L))
        )
        val videoCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1
            ),
            rows = emptyList()
        )

        val projections = mutableListOf<Array<String>>()
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } answers {
            projections += secondArg<Array<String>>()
            when (projections.size) {
                1 -> imageCursor
                2 -> videoCursor
                else -> null
            }
        }

        val provider = AndroidMediaStoreRowDeltaProvider(context)
        val rows = provider.queryObservedRows(
            volumeName = "external",
            apiBranch = MediaStoreDeltaApiBranch.API_29
        )

        assertEquals(1, rows.size)
        assertNull(rows.single().generationAdded)
        assertNull(rows.single().generationModified)
        assertEquals(
            setOf(COLUMN_ID, COLUMN_DATE_MODIFIED),
            projections.first().toSet()
        )
    }

    @Test
    fun `api 30 plus canonicalizes uuid volume rows to external namespace`() {
        every { context.contentResolver } returns contentResolver

        val imageCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1,
                COLUMN_GENERATION_ADDED to 2,
                COLUMN_GENERATION_MODIFIED to 3
            ),
            rows = listOf(mapOf(0 to 7L, 1 to 321L, 2 to 20L, 3 to 21L))
        )
        val videoCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1,
                COLUMN_GENERATION_ADDED to 2,
                COLUMN_GENERATION_MODIFIED to 3
            ),
            rows = emptyList()
        )

        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returnsMany listOf(imageCursor, videoCursor)

        val provider = AndroidMediaStoreRowDeltaProvider(context)
        val rows = provider.queryObservedRows(
            volumeName = "0123-4567",
            apiBranch = MediaStoreDeltaApiBranch.API_30_PLUS
        )

        assertEquals(1, rows.size)
        assertEquals("content://media/external/images/media/7", rows.single().mediaUri)
        assertEquals("0123-4567", rows.single().volumeName)
    }

    @Test
    fun `api 28 query skips non-primary volume entirely`() {
        every { context.contentResolver } returns contentResolver

        val provider = AndroidMediaStoreRowDeltaProvider(context)
        val rows = provider.queryObservedRows(
            volumeName = "external",
            apiBranch = MediaStoreDeltaApiBranch.API_28
        )

        assertEquals(emptyList<MediaStoreObservedRowDelta>(), rows)
        verify(exactly = 0) {
            contentResolver.query(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `api 28 query uses legacy external collection uris`() {
        every { context.contentResolver } returns contentResolver

        val imageCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1
            ),
            rows = emptyList()
        )
        val videoCursor = fakeCursor(
            columns = mapOf(
                COLUMN_ID to 0,
                COLUMN_DATE_MODIFIED to 1
            ),
            rows = emptyList()
        )

        val queriedUris = mutableListOf<Uri>()
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } answers {
            queriedUris += firstArg<Uri>()
            when (queriedUris.size) {
                1 -> imageCursor
                2 -> videoCursor
                else -> null
            }
        }

        val provider = AndroidMediaStoreRowDeltaProvider(context)
        provider.queryObservedRows(
            volumeName = PRIMARY_VOLUME,
            apiBranch = MediaStoreDeltaApiBranch.API_28
        )

        assertEquals(
            listOf(
                Uri.parse("content://media/external/images/media"),
                Uri.parse("content://media/external/video/media")
            ),
            queriedUris
        )
    }

    private fun fakeCursor(
        columns: Map<String, Int>,
        rows: List<Map<Int, Any?>>
    ): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        columns.forEach { (name, index) ->
            every { cursor.getColumnIndexOrThrow(name) } returns index
            every { cursor.getColumnIndex(name) } returns index
        }

        val absent = listOf(
            COLUMN_GENERATION_ADDED,
            COLUMN_GENERATION_MODIFIED
        ) - columns.keys
        absent.forEach { name ->
            every { cursor.getColumnIndex(name) } returns -1
        }

        var position = -1
        every { cursor.moveToNext() } answers {
            position += 1
            position < rows.size
        }
        every { cursor.isNull(any()) } answers {
            rows.getOrNull(position)?.get(firstArg<Int>()) == null
        }
        every { cursor.getLong(any()) } answers {
            (rows.getOrNull(position)?.get(firstArg<Int>()) as? Long) ?: 0L
        }

        return cursor
    }
}
