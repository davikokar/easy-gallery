package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
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

    @Test
    fun `an attempt is recorded only while the row has the identity it names`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A, name = "now.jpg", size = 10L, dateModified = 20L)
            assertNull(store.recordAttempt(row.toMediaIdentity(), 1))
            store.upsert(listOf(row))

            val stale = listOf(
                row.copy(displayName = "before.jpg"),
                row.copy(size = 9L),
                row.copy(dateModified = 19L),
                row.copy(mediaUri = URI_B)
            )
            stale.forEach { assertNull(store.recordAttempt(it.toMediaIdentity(), 1)) }

            assertFalse(store.hasFaceData())
            assertEquals(1, store.recordAttempt(row.toMediaIdentity(), 1))
            assertTrue(store.hasFaceData())
        }
    }

    @Test
    fun `an attempt increments, and restarts at 1 under another pipeline version`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))

            assertEquals(1, store.recordAttempt(row.toMediaIdentity(), 1))
            assertEquals(2, store.recordAttempt(row.toMediaIdentity(), 1))
            assertEquals(3, store.recordAttempt(row.toMediaIdentity(), 1))
            assertEquals(3, store.attemptsOf(URI_A, version = 1))
            assertEquals(0, store.attemptsOf(URI_A, version = 2))

            assertEquals(1, store.recordAttempt(row.toMediaIdentity(), 2))
            assertEquals(2, store.recordAttempt(row.toMediaIdentity(), 2))
            assertEquals(2, store.attemptsOf(URI_A, version = 2))
        }
    }

    @Test
    fun `a result write clears the attempt and so does clearing it`() = runTest {
        forEachStore { store ->
            val written = entry(URI_A)
            val cleared = entry(URI_B)
            store.upsert(listOf(written, cleared))
            store.recordAttempt(written.toMediaIdentity(), 1)
            store.recordAttempt(cleared.toMediaIdentity(), 1)

            store.writeFaceIndexResults(listOf(write(written, 1, listOf(detection(1)))))
            store.clearFaceIndexAttempt(URI_B)
            store.clearFaceIndexAttempt(URI_C)

            assertEquals(0, store.attemptsOf(URI_B, version = 1))
            // The written image's attempt left with the write: the state and the face are all that a deletion finds.
            assertEquals(2, store.deleteAllFaceData().rowsDeleted)
        }
    }

    @Test
    fun `a failed result write also clears the attempt`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.recordAttempt(row.toMediaIdentity(), 1)

            store.writeFaceIndexResults(listOf(write(row, 1, emptyList(), FaceIndexOutcome.FAILED)))

            assertEquals(1, store.deleteAllFaceData().rowsDeleted)
        }
    }

    @Test
    fun `a row write keeps the attempt while the healing triple is unchanged and drops it when it changes`() = runTest {
        forEachStore { store ->
            val kept = entry(URI_A)
            val changed = entry(URI_B)
            store.upsert(listOf(kept, changed))
            store.recordAttempt(kept.toMediaIdentity(), 1)
            store.recordAttempt(changed.toMediaIdentity(), 1)
            store.recordAttempt(changed.toMediaIdentity(), 1)

            store.upsert(listOf(kept.copy(generationModified = 50L), changed.copy(size = changed.size + 1)))

            assertEquals(1, store.attemptsOf(URI_A, version = 1))
            assertEquals(0, store.attemptsOf(URI_B, version = 1))
        }
    }

    @Test
    fun `a heal re-keys the attempt with the tier c rows and a heal that changes the triple drops it`() = runTest {
        forEachStore { store ->
            val moved = entry(URI_A)
            val renamed = entry(URI_B)
            store.upsert(listOf(moved, renamed))
            store.recordAttempt(moved.toMediaIdentity(), 1)
            store.recordAttempt(moved.toMediaIdentity(), 1)
            store.recordAttempt(renamed.toMediaIdentity(), 1)

            store.healMediaUri(URI_A, moved.copy(mediaUri = URI_C))
            store.healMediaUri(URI_B, renamed.copy(displayName = "renamed.jpg"))

            assertEquals(0, store.attemptsOf(URI_A, version = 1))
            assertEquals(2, store.attemptsOf(URI_C, version = 1))
            assertEquals(0, store.attemptsOf(URI_B, version = 1))
        }
    }

    @Test
    fun `a heal onto a row that had an attempt of its own leaves it only the moved one`() = runTest {
        forEachStore { store ->
            val from = entry(URI_A)
            val target = entry(URI_B)
            store.upsert(listOf(from, target))
            store.recordAttempt(from.toMediaIdentity(), 1)
            store.recordAttempt(target.toMediaIdentity(), 1)
            store.recordAttempt(target.toMediaIdentity(), 1)
            store.recordAttempt(target.toMediaIdentity(), 1)

            store.healMediaUri(URI_A, from.copy(mediaUri = URI_B))

            assertEquals(1, store.attemptsOf(URI_B, version = 1))
        }
    }

    // The attempts are read through deleteAllFaceData's count and hasFaceData, which see every attempt row, not only
    // those that still join an index row: an orphan left by a delete would be invisible to attemptsOf.
    @Test
    fun `deleting an image row takes its attempt and no other image's`() = runTest {
        forEachStore { store ->
            val gone = entry(URI_A)
            val kept = entry(URI_C)
            store.upsert(listOf(gone, kept))
            store.recordAttempt(gone.toMediaIdentity(), 1)
            store.recordAttempt(kept.toMediaIdentity(), 1)

            store.deleteByMediaUri(URI_A)

            assertEquals(1, store.attemptsOf(URI_C, version = 1))
            assertEquals(1, store.deleteAllFaceData().rowsDeleted)
        }
    }

    @Test
    fun `deleting a volume takes the attempts of that volume only`() = runTest {
        forEachStore { store ->
            val onCard = entry(URI_A, volume = "0123-4567")
            val kept = entry(URI_C)
            store.upsert(listOf(onCard, kept))
            store.recordAttempt(onCard.toMediaIdentity(), 1)
            store.recordAttempt(kept.toMediaIdentity(), 1)

            store.deleteByVolume("0123-4567")

            assertEquals(1, store.attemptsOf(URI_C, version = 1))
            assertEquals(1, store.deleteAllFaceData().rowsDeleted)
        }
    }

    @Test
    fun `deleting every row takes every attempt`() = runTest {
        forEachStore { store ->
            val first = entry(URI_A)
            val second = entry(URI_B, volume = "0123-4567")
            store.upsert(listOf(first, second))
            store.recordAttempt(first.toMediaIdentity(), 1)
            store.recordAttempt(second.toMediaIdentity(), 1)
            assertTrue(store.hasFaceData())

            store.deleteAll()

            assertFalse(store.hasFaceData())
            assertEquals(0, store.deleteAllFaceData().rowsDeleted)
        }
    }

    @Test
    fun `deleting face data empties the attempts, counts them and leaves the index rows`() = runTest {
        forEachStore { store ->
            val first = entry(URI_A)
            val second = entry(URI_B)
            store.upsert(listOf(first, second))
            store.recordAttempt(first.toMediaIdentity(), 1)
            store.recordAttempt(second.toMediaIdentity(), 1)
            assertTrue(store.hasFaceData())

            val deletion = store.deleteAllFaceData()

            assertEquals(FaceDataDeletion(rowsDeleted = 2, scrubbed = true), deletion)
            assertFalse(store.hasFaceData())
            assertEquals(0, store.attemptsOf(URI_A, version = 1))
            assertEquals(first, store.getByMediaUri(URI_A))
            assertEquals(second, store.getByMediaUri(URI_B))
        }
    }

    @Test
    fun `a result write with the setting off writes nothing and returns 0`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))

            val applied = store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))), faceDataEnabled = { false })

            assertEquals(0, applied)
            assertNull(store.getFaceIndexState(URI_A))
            assertTrue(store.listFaces(URI_A).isEmpty())
            assertFalse(store.hasFaceData())
        }
    }

    @Test
    fun `a result write with the setting off leaves an earlier state, faces and attempt as they were`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))))
            store.recordAttempt(row.toMediaIdentity(), 2)
            val state = store.getFaceIndexState(URI_A)
            val faces = store.listFaces(URI_A)

            val applied = store.writeFaceIndexResults(listOf(write(row, 2, listOf(detection(2)))), faceDataEnabled = { false })

            assertEquals(0, applied)
            assertEquals(state, store.getFaceIndexState(URI_A))
            assertEquals(faces, store.listFaces(URI_A))
            assertEquals(1, store.attemptsOf(URI_A, version = 2))
        }
    }

    @Test
    fun `recording an attempt with the setting off writes nothing and returns null`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))

            val number = store.recordFaceIndexAttempt(row.toMediaIdentity(), 1, faceDataEnabled = { false })

            assertNull(number)
            assertFalse(store.hasFaceData())
        }
    }

    @Test
    fun `the setting is read once per call and inside the write transaction of the room store`() = runTest {
        val row = entry(URI_A)
        val store = RoomMediaIndexStore(database)
        store.upsert(listOf(row))
        val inTransaction = mutableListOf<Boolean>()
        val setting = {
            inTransaction += database.openHelper.writableDatabase.inTransaction()
            true
        }

        store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1)))), setting)
        store.recordFaceIndexAttempt(row.toMediaIdentity(), 1, setting)

        assertEquals(listOf(true, true), inTransaction)
    }

    @Test
    fun `pending images are the images collection rows with no state at the version, in uri order`() = runTest {
        forEachStore { store ->
            val image = entry("content://media/external/images/media/20")
            val perVolumeImage = entry("content://media/0123-4567/images/media/10", volume = "0123-4567")
            val video = entry("content://media/external/video/media/5")
            val done = entry("content://media/external/images/media/30")
            val doneOther = entry("content://media/external/images/media/40")
            store.upsert(listOf(image, perVolumeImage, video, done, doneOther))
            store.writeFaceIndexResults(listOf(write(done, 1, emptyList()), write(doneOther, 2, emptyList())))

            val pending = store.listPendingFaceImages(pipelineVersion = 1, afterMediaUri = null, limit = 100)

            assertEquals(
                listOf(perVolumeImage.mediaUri, image.mediaUri, doneOther.mediaUri),
                pending.map { it.identity.mediaUri }
            )
            assertEquals(image.toMediaIdentity(), pending[1].identity)
            assertEquals(listOf(0, 0, 0), pending.map { it.attempts })
        }
    }

    @Test
    fun `pending images start strictly after the cursor and stop at the limit`() = runTest {
        forEachStore { store ->
            val uris = (1..5).map { "content://media/external/images/media/$it" }
            store.upsert(uris.map { entry(it) })

            val first = store.listPendingFaceImages(1, null, limit = 2)
            val second = store.listPendingFaceImages(1, first.last().identity.mediaUri, limit = 2)
            val third = store.listPendingFaceImages(1, second.last().identity.mediaUri, limit = 2)
            val beyond = store.listPendingFaceImages(1, uris.last(), limit = 2)

            assertEquals(uris.take(2), first.map { it.identity.mediaUri })
            assertEquals(uris.slice(2..3), second.map { it.identity.mediaUri })
            assertEquals(uris.takeLast(1), third.map { it.identity.mediaUri })
            assertTrue(beyond.isEmpty())
        }
    }

    @Test
    fun `a pending image carries the attempts recorded under that version only`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.recordAttempt(row.toMediaIdentity(), 1)
            store.recordAttempt(row.toMediaIdentity(), 1)

            assertEquals(2, store.listPendingFaceImages(1, null, 10).single().attempts)
            assertEquals(0, store.listPendingFaceImages(2, null, 10).single().attempts)
        }
    }

    @Test
    fun `a pending image keeps its state at another version out of the way and the limit must be positive`() = runTest {
        forEachStore { store ->
            val row = entry(URI_A)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, emptyList(), FaceIndexOutcome.FAILED)))

            assertTrue(store.listPendingFaceImages(1, null, 10).isEmpty())
            assertEquals(1, store.listPendingFaceImages(2, null, 10).size)
            assertTrue(runCatching { store.listPendingFaceImages(1, null, 0) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    // ---- grouping and the person record (plan 3.4, Task 3) ----

    @Test
    fun `the faces for grouping are the current pipeline version's only, ids and embeddings in id order`() = runTest {
        forEachStore { store ->
            val first = store.addImage(uri(1), faceCount = 2)
            store.addImage(uri(2), pipelineVersion = 2)
            val third = store.addImage(uri(3))

            val listed = store.listFacesForGrouping(pipelineVersion = 1, afterFaceId = null, limit = 100)

            assertEquals(first + third, listed.map { it.faceId })
            assertEquals(
                listOf(detection(1).embedding, detection(2).embedding, detection(1).embedding),
                listed.map { it.embedding }
            )
        }
    }

    @Test
    fun `the faces for grouping start strictly after the cursor, stop at the limit, and the limit must be positive`() = runTest {
        forEachStore { store ->
            val ids = (1..5).flatMap { store.addImage(uri(it)) }

            val first = store.listFacesForGrouping(1, null, limit = 2)
            val second = store.listFacesForGrouping(1, first.last().faceId, limit = 2)
            val third = store.listFacesForGrouping(1, second.last().faceId, limit = 2)
            val beyond = store.listFacesForGrouping(1, ids.last(), limit = 2)

            assertEquals(ids.take(2), first.map { it.faceId })
            assertEquals(ids.slice(2..3), second.map { it.faceId })
            assertEquals(ids.takeLast(1), third.map { it.faceId })
            assertTrue(beyond.isEmpty())
            assertTrue(runCatching { store.listFacesForGrouping(1, null, 0) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `grouping is not needed without faces, is needed for ungrouped ones, and not once every face carries the version`() = runTest {
        forEachStore { store ->
            assertFalse(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
            val ids = store.addImage(uri(1)) + store.addImage(uri(2))

            assertTrue(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
            store.group(ids.first(), ids)

            assertFalse(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
        }
    }

    @Test
    fun `grouping is needed again for a grouping version other than the current one, older or newer`() = runTest {
        forEachStore { store ->
            val ids = store.addImage(uri(1)) + store.addImage(uri(2))
            store.group(ids.first(), ids, groupingVersion = GROUPING_VERSION + 1)

            assertTrue(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
            assertFalse(store.isFaceGroupingNeeded(1, GROUPING_VERSION + 1))
            assertTrue(store.isFaceGroupingNeeded(1, GROUPING_VERSION + 2))
        }
    }

    @Test
    fun `grouping is needed when one face is added after the others were grouped`() = runTest {
        forEachStore { store ->
            val ids = store.addImage(uri(1)) + store.addImage(uri(2))
            store.group(ids.first(), ids)

            store.addImage(uri(3))

            assertTrue(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
        }
    }

    @Test
    fun `grouping ignores a face at another pipeline version`() = runTest {
        forEachStore { store ->
            val ids = store.addImage(uri(1)) + store.addImage(uri(2))
            store.group(ids.first(), ids)

            store.addImage(uri(3), pipelineVersion = 2)

            assertFalse(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
            assertTrue(store.isFaceGroupingNeeded(2, GROUPING_VERSION))
        }
    }

    @Test
    fun `grouping is needed when the representative's image is deleted and not when another member's is`() = runTest {
        forEachStore { store ->
            val ids = (1..3).flatMap { store.addImage(uri(it)) }
            store.group(ids[0], ids)

            store.deleteByMediaUri(uri(3))
            assertFalse("a deleted face that was not the representative does not regroup", store.isFaceGroupingNeeded(1, GROUPING_VERSION))

            store.deleteByMediaUri(uri(1))
            assertTrue("a deleted representative leaves its members pending", store.isFaceGroupingNeeded(1, GROUPING_VERSION))
        }
    }

    @Test
    fun `a changed healing triple drops the faces, which makes grouping needed only if one was the representative`() = runTest {
        forEachStore { store ->
            val ids = (1..3).flatMap { store.addImage(uri(it)) }
            store.group(ids[0], ids)

            store.upsert(listOf(entry(uri(3), size = 11L)))
            assertTrue(store.listFaces(uri(3)).isEmpty())
            assertFalse(store.isFaceGroupingNeeded(1, GROUPING_VERSION))

            store.upsert(listOf(entry(uri(1), size = 11L)))
            assertTrue(store.listFaces(uri(1)).isEmpty())
            assertTrue(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
        }
    }

    @Test
    fun `a heal keeps the group id and version of a face and so does a row write with an unchanged triple`() = runTest {
        forEachStore { store ->
            val ids = store.addImage(uri(1)) + store.addImage(uri(2))
            store.group(ids[0], ids)

            store.healMediaUri(uri(1), entry(uri(9)))
            store.upsert(listOf(entry(uri(2)).copy(generationModified = 50L)))

            val healed = store.listFaces(uri(9)).single()
            assertEquals(ids[0], healed.id)
            assertEquals(ids[0], healed.groupId)
            assertEquals(GROUPING_VERSION, healed.groupingVersion)
            assertEquals(ids[0], store.listFaces(uri(2)).single().groupId)
            assertFalse(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
        }
    }

    @Test
    fun `a group write stores the group id and the version on each listed face and counts them`() = runTest {
        forEachStore { store ->
            val a = store.addImage(uri(1)).single()
            val b = store.addImage(uri(2)).single()
            val c = store.addImage(uri(3)).single()
            val untouched = store.addImage(uri(4)).single()

            val written = store.writeFaceGroups(
                listOf(FaceGroup(a, listOf(a, b)), FaceGroup(c, listOf(c))),
                pipelineVersion = 1,
                groupingVersion = 7,
                faceDataEnabled = { true }
            )

            assertEquals(3, written)
            assertEquals(listOf(a, a, c), listOf(uri(1), uri(2), uri(3)).map { store.listFaces(it).single().groupId })
            assertEquals(listOf(7, 7, 7), listOf(uri(1), uri(2), uri(3)).map { store.listFaces(it).single().groupingVersion })
            assertNull(store.listFaces(uri(4)).single().groupId)
            assertNull(store.listFaces(uri(4)).single().groupingVersion)
            assertEquals(untouched, store.listFaces(uri(4)).single().id)
        }
    }

    @Test
    fun `a group write skips an id that is gone, leaves a face at another version, and touches nothing else`() = runTest {
        forEachStore { store ->
            val a = store.addImage(uri(1)).single()
            val old = store.addImage(uri(2), pipelineVersion = 2).single()
            val attempted = entry(uri(3))
            store.upsert(listOf(attempted))
            store.recordAttempt(attempted.toMediaIdentity(), 1)
            val rowBefore = store.getByMediaUri(uri(1))
            val stateBefore = store.getFaceIndexState(uri(1))
            val oldBefore = store.listFaces(uri(2))

            val written = store.writeFaceGroups(
                listOf(FaceGroup(a, listOf(a, 987_654L, old))),
                pipelineVersion = 1,
                groupingVersion = GROUPING_VERSION,
                faceDataEnabled = { true }
            )

            assertEquals(1, written)
            assertEquals(oldBefore, store.listFaces(uri(2)))
            assertEquals(a, store.listFaces(uri(1)).single().groupId)
            assertEquals(rowBefore, store.getByMediaUri(uri(1)))
            assertEquals(stateBefore, store.getFaceIndexState(uri(1)))
            assertEquals(1, store.attemptsOf(uri(3), version = 1))
        }
    }

    @Test
    fun `a group write with the setting off writes nothing and returns 0`() = runTest {
        forEachStore { store ->
            val ids = store.addImage(uri(1)) + store.addImage(uri(2))

            val written = store.writeFaceGroups(
                listOf(FaceGroup(ids[0], ids)),
                pipelineVersion = 1,
                groupingVersion = GROUPING_VERSION,
                faceDataEnabled = { false }
            )

            assertEquals(0, written)
            assertNull(store.listFaces(uri(1)).single().groupId)
            assertNull(store.listFaces(uri(2)).single().groupingVersion)
            assertTrue(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
        }
    }

    @Test
    fun `the setting is read once per group write and inside the write transaction of the room store`() = runTest {
        val store = RoomMediaIndexStore(database)
        val ids = store.addImage(uri(1)) + store.addImage(uri(2))
        val inTransaction = mutableListOf<Boolean>()

        store.writeFaceGroups(listOf(FaceGroup(ids[0], ids)), 1, GROUPING_VERSION) {
            inTransaction += database.openHelper.writableDatabase.inTransaction()
            true
        }

        assertEquals(listOf(true), inTransaction)
    }

    @Test
    fun `a group of more faces than one update takes is written in chunks that stay under the variable limit`() = runTest {
        val real = database.mediaIndexDao()
        val idsPerUpdate = mutableListOf<Int>()
        val recordingDao = object : MediaIndexDao by real {
            override suspend fun updateFaceGroup(
                groupId: Long,
                groupingVersion: Int,
                pipelineVersion: Int,
                faceIds: List<Long>
            ): Int {
                idsPerUpdate += faceIds.size
                return real.updateFaceGroup(groupId, groupingVersion, pipelineVersion, faceIds)
            }
        }
        val store = RoomMediaIndexStore(database, recordingDao)
        val row = entry(uri(1))
        store.upsert(listOf(row))
        val detections = (1..1_200).map { FaceDetection(FaceBox(0.1f, 0.1f, 0.5f, 0.5f), FaceEmbedding(floatArrayOf(it.toFloat(), 1f))) }
        store.writeFaceIndexResults(listOf(write(row, 1, detections)))
        val ids = store.listFaces(uri(1)).map { it.id }

        val written = store.writeFaceGroups(listOf(FaceGroup(ids.first(), ids)), 1, GROUPING_VERSION) { true }

        assertEquals(1_200, written)
        assertEquals(1_200, idsPerUpdate.sum())
        // Three more variables ride with each list: the group id, the grouping version and the pipeline version.
        assertTrue("an update carried ${idsPerUpdate.max()} ids", idsPerUpdate.max() + 3 <= 999)
        assertTrue(store.listFaces(uri(1)).all { it.groupId == ids.first() })
    }

    @Test
    fun `the person record counts distinct images, so two faces in one image count once`() = runTest {
        forEachStore { store ->
            val shared = store.addImage(uri(1), faceCount = 2)
            val other = store.addImage(uri(2))
            val lone = store.addImage(uri(3), faceCount = 2)
            store.group(shared[0], shared + other)
            store.group(lone[0], lone)

            val offered = store.listFaceGroups(1, GROUPING_VERSION, minimumImages = 2)
            val everything = store.listFaceGroups(1, GROUPING_VERSION, minimumImages = 1)

            assertEquals(listOf(shared[0]), offered.map { it.groupId })
            assertEquals(2, offered.single().imageCount)
            assertEquals(mapOf(shared[0] to 2, lone[0] to 1), everything.associate { it.groupId to it.imageCount })
        }
    }

    @Test
    fun `the person record applies the minimum and the minimum must be positive`() = runTest {
        forEachStore { store ->
            val two = store.makeGroup(listOf(1L, 2L))
            val three = store.makeGroup(listOf(3L, 4L, 5L))

            assertEquals(setOf(two, three), store.listFaceGroups(1, GROUPING_VERSION, 2).map { it.groupId }.toSet())
            assertEquals(listOf(three), store.listFaceGroups(1, GROUPING_VERSION, 3).map { it.groupId })
            assertTrue(store.listFaceGroups(1, GROUPING_VERSION, 4).isEmpty())
            assertTrue(runCatching { store.listFaceGroups(1, GROUPING_VERSION, 0) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `the person record sorts by image count, then newest date with no date last, then group id`() = runTest {
        forEachStore { store ->
            val old = store.makeGroup(listOf(500L, null))
            val noDateLate = store.makeGroup(listOf(null, null))
            val tieEarly = store.makeGroup(listOf(700L, 100L))
            val big = store.makeGroup(listOf(100L, 100L, 100L))
            val newest = store.makeGroup(listOf(100L, 900L))
            val noDateEarly = store.makeGroup(listOf(null, null))
            val tieLate = store.makeGroup(listOf(100L, 700L))

            val listed = store.listFaceGroups(1, GROUPING_VERSION, 2)

            assertEquals(listOf(big, newest, tieEarly, tieLate, old, noDateLate, noDateEarly), listed.map { it.groupId })
            assertEquals(listOf(100L, 900L, 700L, 700L, 500L, null, null), listed.map { it.newestDateTakenEpochMillis })
        }
    }

    @Test
    fun `the person record carries the representative's identity, box and pipeline version, the healed uri after a heal`() = runTest {
        forEachStore { store ->
            val box = FaceBox(0.125f, 0.25f, 0.5f, 0.75f)
            val row = entry(uri(1), name = "representative.jpg", size = 77L, dateModified = 88L)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(FaceDetection(box, detection(1).embedding)))))
            val representative = store.listFaces(uri(1)).single().id
            val other = store.addImage(uri(2)).single()
            store.group(representative, listOf(representative, other))

            val before = store.listFaceGroups(1, GROUPING_VERSION, 2).single()
            store.healMediaUri(uri(1), row.copy(mediaUri = uri(9)))
            val after = store.listFaceGroups(1, GROUPING_VERSION, 2).single()

            assertEquals(representative, before.groupId)
            assertEquals(FaceGroupRepresentative(row.toMediaIdentity(), box, 1), before.representative)
            assertEquals(uri(9), after.representative.identity.mediaUri)
            assertEquals(row.toMediaIdentity().healingKey, after.representative.identity.healingKey)
            assertEquals(box, after.representative.box)
        }
    }

    @Test
    fun `the person record leaves out a stale grouping version, a missing representative and another pipeline version`() = runTest {
        forEachStore { store ->
            val offered = store.makeGroup(listOf(1L, 2L))
            val staleVersion = store.makeGroup(listOf(1L, 2L), groupingVersion = GROUPING_VERSION + 1)
            val otherPipeline = store.makeGroup(listOf(1L, 2L), pipelineVersion = 2)
            val orphaned = store.makeGroup(listOf(1L, 2L, 3L))
            store.deleteByMediaUri(imageOf(orphaned))

            val listed = store.listFaceGroups(1, GROUPING_VERSION, 2)

            assertEquals(listOf(offered), listed.map { it.groupId })
            assertEquals(listOf(staleVersion), store.listFaceGroups(1, GROUPING_VERSION + 1, 2).map { it.groupId })
            assertEquals(listOf(otherPipeline), store.listFaceGroups(2, GROUPING_VERSION, 2).map { it.groupId })
        }
    }

    @Test
    fun `a group whose images are partly gone is judged on the images still indexed`() = runTest {
        forEachStore { store ->
            val group = store.makeGroup(listOf(1L, 2L, 3L))
            assertEquals(3, store.listFaceGroups(1, GROUPING_VERSION, 2).single().imageCount)

            store.deleteByMediaUri(imageOf(group, 2))
            assertEquals(2, store.listFaceGroups(1, GROUPING_VERSION, 2).single().imageCount)

            store.deleteByMediaUri(imageOf(group, 1))
            assertTrue(store.listFaceGroups(1, GROUPING_VERSION, 2).isEmpty())
            assertEquals(1, store.listFaceGroups(1, GROUPING_VERSION, 1).single().imageCount)
        }
    }

    @Test
    fun `deleting face data after grouping leaves no face, no group and no pending work`() = runTest {
        forEachStore { store ->
            store.makeGroup(listOf(1L, 2L))
            assertTrue(store.hasFaceData())

            val deletion = store.deleteAllFaceData()

            assertEquals(4, deletion.rowsDeleted)
            assertFalse(store.hasFaceData())
            assertTrue(store.listFacesForGrouping(1, null, 100).isEmpty())
            assertTrue(store.listFaceGroups(1, GROUPING_VERSION, 1).isEmpty())
            assertFalse(store.isFaceGroupingNeeded(1, GROUPING_VERSION))
        }
    }

    @Test
    fun `the face projection holds only the images and faces at the asked pipeline version`() = runTest {
        forEachStore { store ->
            val old = store.addImage(uri(1), faceCount = 2, pipelineVersion = 1)
            store.addImage(uri(2), faceCount = 1, pipelineVersion = 2)
            store.addImage(uri(3), faceCount = 0, pipelineVersion = 1)
            val failed = entry(uri(4))
            store.upsert(listOf(failed))
            store.writeFaceIndexResults(listOf(write(failed, 1, emptyList(), FaceIndexOutcome.FAILED)))
            store.upsert(listOf(entry(uri(5))))

            val version1 = store.observeFaceEvaluationProjection(1).first()
            val version2 = store.observeFaceEvaluationProjection(2).first()

            assertEquals(listOf(uri(1), uri(3), uri(4)), version1.images.map { it.identity.mediaUri })
            assertEquals(old, version1.images[0].faces.map { it.id })
            assertEquals(listOf(FaceIndexOutcome.INDEXED, FaceIndexOutcome.INDEXED, FaceIndexOutcome.FAILED), version1.images.map { it.outcome })
            assertTrue(version1.images[1].faces.isEmpty())
            assertTrue(version1.images[2].faces.isEmpty())
            assertEquals(listOf(uri(2)), version2.images.map { it.identity.mediaUri })
            assertTrue(store.observeFaceEvaluationProjection(3).first().images.isEmpty())
        }
    }

    @Test
    fun `the face projection carries the index row's identity, each face's box and group, and nothing of the embedding`() = runTest {
        forEachStore { store ->
            val row = entry(uri(1), name = "anchor.jpg", size = 77L, dateModified = 88L)
            store.upsert(listOf(row))
            store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1), detection(2)))))
            val ids = store.listFaces(uri(1)).map { it.id }
            store.group(ids[0], listOf(ids[0]))

            val image = store.observeFaceEvaluationProjection(1).first().images.single()

            assertEquals(row.toMediaIdentity(), image.identity)
            assertEquals(
                listOf(
                    FaceEvaluationFace(ids[0], detection(1).box, ids[0], GROUPING_VERSION),
                    FaceEvaluationFace(ids[1], detection(2).box, null, null)
                ),
                image.faces
            )
            assertTrue(FaceEvaluationFace::class.java.declaredFields.none { it.name.contains("embedding", ignoreCase = true) })
            assertFalse(image.toString().contains("anchor"))
            assertFalse(image.faces.first().toString().contains(ids[0].toString()))
        }
    }

    @Test
    fun `the face projection emits again after a face-pass write`() = runTest {
        forEachStore { store ->
            val row = entry(uri(1))
            store.upsert(listOf(row))

            store.awaitFaceProjection(
                change = { store.writeFaceIndexResults(listOf(write(row, 1, listOf(detection(1))))) },
                until = { it.images.size == 1 }
            )
        }
    }

    @Test
    fun `the face projection emits again after a grouping write alone`() = runTest {
        forEachStore { store ->
            val ids = store.addImage(uri(1), faceCount = 1)

            store.awaitFaceProjection(
                change = { store.group(ids[0], ids) },
                until = { it.images.single().faces.single().groupId == ids[0] }
            )
        }
    }

    @Test
    fun `the face projection emits again after a heal, an upsert that drops Tier C and the three deletes`() = runTest {
        forEachStore { store ->
            store.addImage(uri(1))
            store.awaitFaceProjection(
                change = { store.healMediaUri(uri(1), entry(uri(2))) },
                until = { it.images.map { image -> image.identity.mediaUri } == listOf(uri(2)) }
            )

            store.awaitFaceProjection(
                change = { store.upsert(listOf(entry(uri(2), size = 11L))) },
                until = { it.images.isEmpty() }
            )

            store.addImage(uri(3))
            store.awaitFaceProjection(change = { store.deleteByMediaUri(uri(3)) }, until = { it.images.isEmpty() })

            store.addImage(uri(4))
            store.awaitFaceProjection(change = { store.deleteByVolume("external_primary") }, until = { it.images.isEmpty() })

            store.addImage(uri(5))
            store.awaitFaceProjection(change = { store.deleteAllFaceData() }, until = { it.images.isEmpty() })
        }
    }

    // Waits for the first emission, makes the change, then for the emission [until] accepts: a predicate, never the next item.
    private suspend fun MediaIndexStore.awaitFaceProjection(
        change: suspend () -> Unit,
        until: (FaceEvaluationSnapshot) -> Boolean
    ) {
        observeFaceEvaluationProjection(1).test {
            awaitItem()
            change()
            while (!until(awaitItem())) Unit
            cancelAndIgnoreRemainingEvents()
        }
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

    // The older tests write with the setting on; the setting-off rule has its own tests.
    private suspend fun MediaIndexStore.writeFaceIndexResults(writes: List<FaceIndexWrite>): Int =
        writeFaceIndexResults(writes, faceDataEnabled = { true })

    private suspend fun MediaIndexStore.recordAttempt(identity: MediaIdentity, pipelineVersion: Int): Int? =
        recordFaceIndexAttempt(identity, pipelineVersion, faceDataEnabled = { true })

    // Attempts are observed through the pending list, where they are reported for the version asked.
    private suspend fun MediaIndexStore.attemptsOf(mediaUri: String, version: Int): Int =
        listPendingFaceImages(version, null, 1_000).firstOrNull { it.identity.mediaUri == mediaUri }?.attempts ?: 0

    private fun uri(n: Int) = "content://media/external_primary/images/media/$n"

    // One index row and its faces, at the pipeline version given; the faces' ids ascend.
    private suspend fun MediaIndexStore.addImage(
        mediaUri: String,
        faceCount: Int = 1,
        pipelineVersion: Int = 1,
        dateTaken: Long? = 1_700_000_000_000L
    ): List<Long> {
        val row = entry(mediaUri, dateTaken = dateTaken)
        upsert(listOf(row))
        writeFaceIndexResults(listOf(write(row, pipelineVersion, List(faceCount) { detection(it + 1) })))
        return listFaces(mediaUri).map { it.id }
    }

    private suspend fun MediaIndexStore.group(
        representative: Long,
        members: List<Long>,
        groupingVersion: Int = GROUPING_VERSION,
        pipelineVersion: Int = 1
    ): Int = writeFaceGroups(listOf(FaceGroup(representative, members.sorted())), pipelineVersion, groupingVersion) { true }

    private val madeGroups = HashMap<Long, List<String>>()

    // A group of one face in each of as many images as there are dates, the first face the representative; returns its id.
    private suspend fun MediaIndexStore.makeGroup(
        dates: List<Long?>,
        groupingVersion: Int = GROUPING_VERSION,
        pipelineVersion: Int = 1
    ): Long {
        val uris = dates.map { uri(nextImage++) }
        val ids = dates.mapIndexed { index, date -> addImage(uris[index], dateTaken = date, pipelineVersion = pipelineVersion).single() }
        group(ids.first(), ids, groupingVersion, pipelineVersion)
        madeGroups[ids.first()] = uris
        return ids.first()
    }

    private var nextImage = 1_000

    private fun imageOf(groupId: Long, index: Int = 0): String = madeGroups.getValue(groupId)[index]

    private fun entry(
        mediaUri: String,
        volume: String = "external_primary",
        name: String = "photo.jpg",
        size: Long = 10L,
        dateModified: Long = 20L,
        dateTaken: Long? = 1_700_000_000_000L
    ): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = mediaUri,
            displayName = name,
            size = size,
            dateModified = dateModified,
            volumeName = volume,
            generationAdded = 1L,
            generationModified = 2L,
            capturedDateTakenEpochMillis = dateTaken,
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
        const val GROUPING_VERSION = FACE_GROUPING_VERSION
    }
}
