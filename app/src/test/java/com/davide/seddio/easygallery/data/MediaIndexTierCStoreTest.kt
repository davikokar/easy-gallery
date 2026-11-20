package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every Tier C rule, against the in-memory store and the Room store through the same assertions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexTierCStoreTest {

    private lateinit var database: MediaIndexDatabase
    private lateinit var dao: MediaIndexDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaIndexDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.mediaIndexDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a write for the row it was computed from stores its state and faces in order`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            val detections = listOf(detection(1), detection(2))

            val applied = store.writeFaceIndexResults(listOf(write(row, pipelineVersion = 3, detections = detections)))

            assertEquals(1, applied)
            assertEquals(FaceIndexState(URI_A, 3, FaceIndexOutcome.INDEXED), store.getFaceIndexState(URI_A))
            val stored = store.listFaces(URI_A)
            assertEquals(detections, stored.map { FaceDetection(it.box, it.embedding) })
            assertEquals(stored.map { it.id }.sorted(), stored.map { it.id })
            assertEquals(2, stored.map { it.id }.distinct().size)
            stored.forEach {
                assertEquals(URI_A, it.mediaUri)
                assertEquals(3, it.pipelineVersion)
                assertNull(it.groupId)
                assertNull(it.groupingVersion)
            }
            assertTrue(store.hasFaceData())
        }
    }

    @Test
    fun `a write replaces the image's earlier state and faces and an id is never reused`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, detections = listOf(detection(1), detection(2)))))
            val firstIds = store.listFaces(URI_A).map { it.id }

            store.writeFaceIndexResults(listOf(write(row, 2, detections = listOf(detection(3)))))

            assertEquals(FaceIndexState(URI_A, 2, FaceIndexOutcome.INDEXED), store.getFaceIndexState(URI_A))
            val stored = store.listFaces(URI_A)
            assertEquals(listOf(detection(3)), stored.map { FaceDetection(it.box, it.embedding) })
            assertEquals(2, stored.single().pipelineVersion)
            assertTrue(stored.single().id > firstIds.max())
        }
    }

    @Test
    fun `a write is skipped when the index row is gone`() = runTest {
        forEachStore { store ->
            val applied = store.writeFaceIndexResults(listOf(write(entry(URI_A), 1, listOf(detection(1)))))

            assertEquals(0, applied)
            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertFalse(store.hasFaceData())
        }
    }

    @Test
    fun `a write is skipped when the row's uri or any part of its healing triple changed`() = runTest {
        forEachStore { store ->
            val stored = entry(URI_A, name = "now.jpg", size = 10L, dateModified = 20L)
            store.upsert(listOf(stored))
            val computedFrom = listOf(
                stored.copy(displayName = "before.jpg"),
                stored.copy(size = 9L),
                stored.copy(dateModified = 19L),
                stored.copy(mediaUri = URI_B)
            )

            computedFrom.forEach { stale ->
                assertEquals(0, store.writeFaceIndexResults(listOf(write(stale, 1, listOf(detection(1))))))
            }

            assertNull(store.getFaceIndexState(URI_A))
            assertNull(store.getFaceIndexState(URI_B))
            assertFalse(store.hasFaceData())
        }
    }

    @Test
    fun `a batch applies the writes that still match and counts them`() = runTest {
        forEachStore { store ->
            val kept = entry(URI_A)
            val changed = entry(URI_B)
            store.upsert(listOf(kept, changed))

            val applied = store.writeFaceIndexResults(
                listOf(
                    write(kept, 1, listOf(detection(1))),
                    write(changed.copy(size = 999L), 1, listOf(detection(2))),
                    write(entry(URI_C), 1, listOf(detection(3)))
                )
            )

            assertEquals(1, applied)
            assertNotNull(store.getFaceIndexState(URI_A))
            assertNull(store.getFaceIndexState(URI_B))
            assertNull(store.getFaceIndexState(URI_C))
        }
    }

    @Test
    fun `a failed image and an image with no face both store a state and no face`() = runTest {
        forEachStore { store ->
            val failed = entry(URI_A)
            val noFace = entry(URI_B)
            store.upsert(listOf(failed, noFace))

            store.writeFaceIndexResults(
                listOf(
                    write(failed, 1, emptyList(), FaceIndexOutcome.FAILED),
                    write(noFace, 1, emptyList(), FaceIndexOutcome.INDEXED)
                )
            )

            assertEquals(FaceIndexOutcome.FAILED, store.getFaceIndexState(URI_A)?.outcome)
            assertEquals(FaceIndexOutcome.INDEXED, store.getFaceIndexState(URI_B)?.outcome)
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertTrue(store.listFaces(URI_B).isEmpty())
        }
    }

    @Test
    fun `a stored embedding is a copy of the one written and of the one read`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            val source = floatArrayOf(0.5f, -1.5f, 2.25f)
            store.writeFaceIndexResults(
                listOf(write(row, 1, listOf(FaceDetection(FaceBox(0.1f, 0.1f, 0.5f, 0.5f), FaceEmbedding(source)))))
            )

            source[0] = 99f
            store.listFaces(URI_A).single().embedding.toFloatArray()[1] = 99f

            assertArrayEquals(
                floatArrayOf(0.5f, -1.5f, 2.25f),
                store.listFaces(URI_A).single().embedding.toFloatArray(),
                0f
            )
        }
    }

    @Test
    fun `re-extracting a changed file makes it pending and leaves other images alone`() = runTest {
        forEachStore { store ->
            val reextracted = entry(URI_A)
            val untouched = entry(URI_B)
            store.upsert(listOf(reextracted, untouched))
            store.writeFaceIndexResults(
                listOf(write(reextracted, 1, listOf(detection(1))), write(untouched, 1, listOf(detection(2))))
            )

            store.upsert(listOf(reextracted.copy(size = 11L), entry(URI_C)))

            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertEquals(11L, store.getByMediaUri(URI_A)?.size)
            assertNotNull(store.getFaceIndexState(URI_B))
            assertEquals(1, store.listFaces(URI_B).size)
        }
    }

    @Test
    fun `a row write with an unchanged healing triple keeps the state and faces and updates the tier a columns`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 3, listOf(detection(1), detection(2)))))
            val stateBefore = store.getFaceIndexState(URI_A)
            val facesBefore = store.listFaces(URI_A)

            store.upsert(listOf(row.copy(generationModified = 99L, capturedWidth = 200)))

            assertEquals(99L, store.getByMediaUri(URI_A)?.generationModified)
            assertEquals(200, store.getByMediaUri(URI_A)?.capturedWidth)
            assertEquals(stateBefore, store.getFaceIndexState(URI_A))
            assertEquals(facesBefore, store.listFaces(URI_A))
            assertEquals(2, store.listFaces(URI_A).map { it.id }.distinct().size)
        }
    }

    @Test
    fun `a row write that changes the size drops the tier c rows`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))))

            store.upsert(listOf(row.copy(size = row.size + 1)))

            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
        }
    }

    @Test
    fun `a row write that changes the modification date drops the tier c rows`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))))

            store.upsert(listOf(row.copy(dateModified = row.dateModified + 1)))

            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
        }
    }

    @Test
    fun `a row write that changes the display name drops the tier c rows`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))))

            store.upsert(listOf(row.copy(displayName = "renamed.jpg")))

            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
        }
    }

    // Room only: the in-memory store's API cannot create Tier C rows for a URI that has no index row.
    @Test
    fun `a row write for a uri with no stored row drops the tier c rows left behind for it`() = runTest {
        dao.upsertFaceState(MediaIndexFaceStateEntity(URI_A, 1, FaceIndexOutcome.INDEXED))
        dao.insertFaces(
            listOf(
                MediaIndexFaceEntity(
                    mediaUri = URI_A,
                    pipelineVersion = 1,
                    boxLeft = 0.1f,
                    boxTop = 0.2f,
                    boxRight = 0.4f,
                    boxBottom = 0.6f,
                    embedding = ByteArray(12),
                    groupId = null,
                    groupingVersion = null
                )
            )
        )
        val store = RoomMediaIndexStore(database)
        assertNull(store.getByMediaUri(URI_A))
        assertNotNull(dao.getFaceState(URI_A))

        store.upsert(listOf(entry(URI_A)))

        assertNotNull(store.getByMediaUri(URI_A))
        assertNull(dao.getFaceState(URI_A))
        assertTrue(dao.listFaces(URI_A).isEmpty())
        assertFalse(store.hasFaceData())
    }

    @Test
    fun `a row write neither clears nor restamps the pipeline version of a kept state`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))))

            store.upsert(listOf(row.copy(generationModified = 50L)))

            assertEquals(FaceIndexState(URI_A, 1, FaceIndexOutcome.INDEXED), store.getFaceIndexState(URI_A))
            assertEquals(1, store.listFaces(URI_A).single().pipelineVersion)

            store.upsert(listOf(row.copy(size = row.size + 1)))

            assertNull(store.getFaceIndexState(URI_A))
        }
    }

    @Test
    fun `one row write holding an unchanged row and a changed row drops only the changed row's tier c rows`() = runTest {
        forEachStore { store ->
            val unchanged = entry(URI_A)
            val changed = entry(URI_B)
            store.upsert(listOf(unchanged, changed))
            store.writeFaceIndexResults(
                listOf(write(unchanged, 1, listOf(detection(1))), write(changed, 1, listOf(detection(2))))
            )

            store.upsert(listOf(unchanged.copy(generationModified = 50L), changed.copy(size = changed.size + 1)))

            assertNotNull(store.getFaceIndexState(URI_A))
            assertEquals(1, store.listFaces(URI_A).size)
            assertNull(store.getFaceIndexState(URI_B))
            assertTrue(store.listFaces(URI_B).isEmpty())
        }
    }

    @Test
    fun `a heal moves the row and re-keys its tier c rows keeping their ids`() = runTest {
        forEachStore { store ->
            val from = entry(URI_A)
            val healed = from.copy(mediaUri = URI_B, volumeName = "0123-4567")
            store.upsert(listOf(from))
            store.writeFaceIndexResults(listOf(write(from, 4, listOf(detection(1), detection(2)))))
            val before = store.listFaces(URI_A)

            store.healMediaUri(URI_A, healed)

            assertNull(store.getByMediaUri(URI_A))
            assertEquals(healed, store.getByMediaUri(URI_B))
            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertEquals(FaceIndexState(URI_B, 4, FaceIndexOutcome.INDEXED), store.getFaceIndexState(URI_B))
            assertEquals(before.map { it.copy(mediaUri = URI_B) }, store.listFaces(URI_B))
        }
    }

    @Test
    fun `a heal onto a row that had tier c rows of its own leaves it only the moved ones`() = runTest {
        forEachStore { store ->
            val from = entry(URI_A)
            val target = entry(URI_B)
            store.upsert(listOf(from, target))
            store.writeFaceIndexResults(
                listOf(write(from, 4, listOf(detection(1))), write(target, 7, listOf(detection(2), detection(3))))
            )
            val moved = store.listFaces(URI_A)

            store.healMediaUri(URI_A, from.copy(mediaUri = URI_B))

            assertEquals(FaceIndexState(URI_B, 4, FaceIndexOutcome.INDEXED), store.getFaceIndexState(URI_B))
            assertEquals(moved.map { it.copy(mediaUri = URI_B) }, store.listFaces(URI_B))
            assertTrue(store.listFaces(URI_A).isEmpty())
        }
    }

    @Test
    fun `a heal re-keys a failed state that has no faces`() = runTest {
        forEachStore { store ->
            val from = entry(URI_A)
            store.upsert(listOf(from, entry(URI_B)))
            store.writeFaceIndexResults(listOf(write(from, 4, emptyList(), FaceIndexOutcome.FAILED)))

            store.healMediaUri(URI_A, from.copy(mediaUri = URI_B))

            assertEquals(FaceIndexState(URI_B, 4, FaceIndexOutcome.FAILED), store.getFaceIndexState(URI_B))
            assertNull(store.getFaceIndexState(URI_A))
        }
    }

    @Test
    fun `a heal that keeps the uri and the healing triple keeps the tier c rows`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 4, listOf(detection(1)))))
            val before = store.listFaces(URI_A)

            store.healMediaUri(URI_A, row.copy(volumeName = "0123-4567", capturedWidth = 999))

            assertEquals("0123-4567", store.getByMediaUri(URI_A)?.volumeName)
            assertEquals(999, store.getByMediaUri(URI_A)?.capturedWidth)
            assertEquals(before, store.listFaces(URI_A))
            assertEquals(FaceIndexState(URI_A, 4, FaceIndexOutcome.INDEXED), store.getFaceIndexState(URI_A))
        }
    }

    @Test
    fun `a heal that keeps the uri but changes the healing triple leaves no tier c rows`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 4, listOf(detection(1)))))

            store.healMediaUri(URI_A, row.copy(displayName = "renamed.jpg"))

            assertEquals("renamed.jpg", store.getByMediaUri(URI_A)?.displayName)
            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertFalse(store.hasFaceData())
        }
    }

    @Test
    fun `a heal that moves the row and changes the healing triple leaves no tier c rows at the source or the target`() = runTest {
        forEachStore { store ->
            val from = entry(URI_A)
            val target = entry(URI_B)
            store.upsert(listOf(from, target))
            store.writeFaceIndexResults(
                listOf(write(from, 4, listOf(detection(1))), write(target, 7, listOf(detection(2), detection(3))))
            )

            store.healMediaUri(URI_A, from.copy(mediaUri = URI_B, size = from.size + 1))

            assertNull(store.getByMediaUri(URI_A))
            assertEquals(from.size + 1, store.getByMediaUri(URI_B)?.size)
            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertNull(store.getFaceIndexState(URI_B))
            assertTrue(store.listFaces(URI_B).isEmpty())
            assertFalse(store.hasFaceData())
        }
    }

    @Test
    fun `deleting an image row takes its tier c rows and no other image's`() = runTest {
        forEachStore { store ->
            val gone = entry(URI_A)
            val kept = entry(URI_B)
            store.upsert(listOf(gone, kept))
            store.writeFaceIndexResults(listOf(write(gone, 1, listOf(detection(1))), write(kept, 1, listOf(detection(2)))))

            store.deleteByMediaUri(URI_A)

            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertNotNull(store.getFaceIndexState(URI_B))
            assertEquals(1, store.listFaces(URI_B).size)
        }
    }

    @Test
    fun `deleting a volume takes the tier c rows of that volume only`() = runTest {
        forEachStore { store ->
            val removed = entry(URI_A, volume = "0123-4567")
            val kept = entry(URI_B, volume = "external_primary")
            store.upsert(listOf(removed, kept))
            store.writeFaceIndexResults(listOf(write(removed, 1, listOf(detection(1))), write(kept, 1, listOf(detection(2)))))

            store.deleteByVolume("0123-4567")

            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertNotNull(store.getFaceIndexState(URI_B))
            assertEquals(1, store.listFaces(URI_B).size)
        }
    }

    @Test
    fun `deleting every row takes every tier c row`() = runTest {
        forEachStore { store ->
            val first = entry(URI_A)
            val second = entry(URI_B, volume = "0123-4567")
            store.upsert(listOf(first, second))
            store.writeFaceIndexResults(listOf(write(first, 1, listOf(detection(1))), write(second, 1, listOf(detection(2)))))

            store.deleteAll()

            assertFalse(store.hasFaceData())
            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_B).isEmpty())
        }
    }

    @Test
    fun `resolving a place leaves the tier c rows alone`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 5, listOf(detection(1), detection(2)))))
            val state = store.getFaceIndexState(URI_A)
            val faces = store.listFaces(URI_A)

            val written = store.writeResolvedPlaces(
                listOf(ResolvedPlaceWrite(URI_A, 45.5, 9.25, "IT", "25", "Milan", 2))
            )

            assertEquals(1, written)
            assertEquals("Milan", store.getByMediaUri(URI_A)?.derivedPlaceName)
            assertEquals(state, store.getFaceIndexState(URI_A))
            assertEquals(faces, store.listFaces(URI_A))
        }
    }

    @Test
    fun `deleting face data removes every tier c row and nothing else`() = runTest {
        forEachStore { store ->
            val first = entry(URI_A)
            val second = entry(URI_B)
            store.upsert(listOf(first, second))
            store.writeResolvedPlaces(listOf(ResolvedPlaceWrite(URI_A, 45.5, 9.25, "IT", "25", "Milan", 2)))
            store.writeFaceIndexResults(
                listOf(
                    write(first, 1, listOf(detection(1), detection(2))),
                    write(second, 1, emptyList(), FaceIndexOutcome.FAILED)
                )
            )
            val rowsBefore = store.listByVolume("external_primary")

            val deletion = store.deleteAllFaceData()

            assertEquals(FaceDataDeletion(rowsDeleted = 4, scrubbed = true), deletion)
            assertFalse(store.hasFaceData())
            assertNull(store.getFaceIndexState(URI_A))
            assertNull(store.getFaceIndexState(URI_B))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertEquals(rowsBefore, store.listByVolume("external_primary"))
            assertEquals("Milan", store.getByMediaUri(URI_A)?.derivedPlaceName)
        }
    }

    @Test
    fun `deleting face data a second time does nothing`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))))
            store.deleteAllFaceData()

            val second = store.deleteAllFaceData()

            assertEquals(FaceDataDeletion(rowsDeleted = 0, scrubbed = true), second)
            assertEquals(row, store.getByMediaUri(URI_A))
        }
    }

    @Test
    fun `a face id is not reused after face data is deleted`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1), detection(2)))))
            val lastId = store.listFaces(URI_A).maxOf { it.id }
            store.deleteAllFaceData()

            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(3)))))

            assertTrue(store.listFaces(URI_A).single().id > lastId)
        }
    }

    @Test
    fun `an unknown image has no state and no faces`() = runTest {
        forEachStore { store ->
            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertFalse(store.hasFaceData())
        }
    }

    @Test
    fun `deleting face data leaves the delta watermarks alone`() = runTest {
        val watermark = MediaIndexWatermarkEntity("external_primary", "v1", 77L, 1_700_000_000L)
        dao.upsertWatermark(watermark)
        val store = RoomMediaIndexStore(database)
        store.upsert(listOf(entry(URI_A)))

        store.deleteAllFaceData()

        assertEquals(listOf(watermark), dao.listWatermarks())
    }

    private suspend fun forEachStore(block: suspend (MediaIndexStore) -> Unit) {
        listOf(
            "in-memory" to InMemoryMediaIndexStore(),
            "room" to RoomMediaIndexStore(database)
        ).forEach { (name, store) ->
            try {
                block(store)
            } catch (e: AssertionError) {
                throw AssertionError("[$name store] ${e.message}", e)
            }
        }
    }

    private fun entry(
        mediaUri: String,
        volume: String = "external_primary",
        name: String = "photo.jpg",
        size: Long = 10L,
        dateModified: Long = 20L
    ): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = name,
            size = size,
            dateModified = dateModified,
            volumeName = volume,
            generationAdded = 1L,
            generationModified = 2L,
            capturedDateTakenEpochMillis = 1_700_000_000_000L,
            capturedLatitude = 45.5,
            capturedLongitude = 9.25,
            capturedWidth = 100,
            capturedHeight = 50,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )
    }

    private fun write(
        row: MediaIndexEntry,
        pipelineVersion: Int,
        detections: List<FaceDetection>,
        outcome: FaceIndexOutcome = FaceIndexOutcome.INDEXED
    ): FaceIndexWrite {
        return FaceIndexWrite(row.toMediaIdentity(), pipelineVersion, outcome, detections)
    }

    private fun detection(seed: Int): FaceDetection {
        val left = 0.1f + seed * 0.01f
        return FaceDetection(
            box = FaceBox(left, 0.2f, left + 0.3f, 0.6f),
            embedding = FaceEmbedding(floatArrayOf(seed.toFloat(), -seed * 0.5f, 0.25f))
        )
    }

    private companion object {
        const val URI_A = "content://media/external_primary/images/media/1"
        const val URI_B = "content://media/external_primary/images/media/2"
        const val URI_C = "content://media/external_primary/images/media/3"
    }
}
