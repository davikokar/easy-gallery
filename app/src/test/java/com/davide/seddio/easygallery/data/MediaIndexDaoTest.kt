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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class MediaIndexDaoTest {

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
    fun `upsert emits row and preserves null derived columns`() = runTest {
        dao.observeAll().test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(listOf(sampleEntity(mediaUri = "content://media/external/images/media/1")))

            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertNull(rows.single().derivedCountryCode)
            assertNull(rows.single().derivedRegionCode)
            assertNull(rows.single().derivedPlaceName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert replaces existing row by media uri`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/2",
                    displayName = "before.jpg",
                    capturedWidth = 800,
                    capturedHeight = 600
                )
            )
        )

        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/2",
                    displayName = "after.jpg",
                    capturedWidth = 1920,
                    capturedHeight = 1080,
                    derivedCountryCode = "IT",
                    derivedRegionCode = "52",
                    derivedPlaceName = "Firenze"
                )
            )
        )

        val stored = dao.getByMediaUri("content://media/external/images/media/2")
        assertNotNull(stored)
        assertEquals("after.jpg", stored?.displayName)
        assertEquals(1920, stored?.capturedWidth)
        assertEquals("IT", stored?.derivedCountryCode)
    }

    @Test
    fun `delete by volume removes only targeted volume`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/11",
                    volumeName = "external_primary"
                ),
                sampleEntity(
                    mediaUri = "content://media/external/images/media/12",
                    volumeName = "external"
                )
            )
        )

        dao.deleteByVolume("external")

        assertNotNull(dao.getByMediaUri("content://media/external/images/media/11"))
        assertNull(dao.getByMediaUri("content://media/external/images/media/12"))
    }

    @Test
    fun `observe by volume emits only matching rows`() = runTest {
        dao.observeByVolume("external_primary").test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(
                listOf(
                    sampleEntity(
                        mediaUri = "content://media/external/images/media/21",
                        volumeName = "external_primary"
                    ),
                    sampleEntity(
                        mediaUri = "content://media/external/images/media/22",
                        volumeName = "external"
                    )
                )
            )

            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("content://media/external/images/media/21", rows.single().mediaUri)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `evaluation projection keeps a null date and still lists the row`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/61",
                    capturedDateTakenEpochMillis = null
                ),
                sampleEntity(mediaUri = "content://media/external/images/media/62")
            )
        )

        val byUri = dao.observeEvaluationProjection().first().associateBy { it.mediaUri }

        assertEquals(setOf("content://media/external/images/media/61", "content://media/external/images/media/62"), byUri.keys)
        assertNull(byUri.getValue("content://media/external/images/media/61").capturedDateTakenEpochMillis)
        assertNotNull(byUri.getValue("content://media/external/images/media/62").capturedDateTakenEpochMillis)
    }

    @Test
    fun `evaluation projection returns each column unshuffled`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/141",
                    displayName = "evaluated.jpg",
                    size = 1_414L,
                    dateModified = 2_525L,
                    capturedDateTakenEpochMillis = 1_729_000_000_321L,
                    derivedCountryCode = "AA",
                    derivedRegionCode = "BB-77",
                    derivedPlaceName = "Distinct Place",
                    derivedPlaceResolverVersion = 9
                )
            )
        )

        val rows = dao.observeEvaluationProjection().first()

        assertEquals(
            listOf(
                MediaIndexEvaluationProjectionEntity(
                    mediaUri = "content://media/external/images/media/141",
                    displayName = "evaluated.jpg",
                    size = 1_414L,
                    dateModified = 2_525L,
                    capturedDateTakenEpochMillis = 1_729_000_000_321L,
                    hasCoordinates = true,
                    derivedCountryCode = "AA",
                    derivedRegionCode = "BB-77",
                    derivedPlaceName = "Distinct Place",
                    derivedPlaceResolverVersion = 9
                )
            ),
            rows
        )
    }

    @Test
    fun `evaluation projection reports coordinates present only when both are stored`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(mediaUri = "content://media/external/images/media/151"),
                sampleEntity(
                    mediaUri = "content://media/external/images/media/152",
                    capturedLatitude = null,
                    capturedLongitude = null
                ),
                sampleEntity(mediaUri = "content://media/external/images/media/153", capturedLongitude = null),
                sampleEntity(mediaUri = "content://media/external/images/media/154", capturedLatitude = null)
            )
        )

        val byUri = dao.observeEvaluationProjection().first().associate { it.mediaUri to it.hasCoordinates }

        assertEquals(
            mapOf(
                "content://media/external/images/media/151" to true,
                "content://media/external/images/media/152" to false,
                "content://media/external/images/media/153" to false,
                "content://media/external/images/media/154" to false
            ),
            byUri
        )
    }

    @Test
    fun `evaluation projection returns a pending row with its old derived values and a null marker`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/161",
                    derivedCountryCode = "IT",
                    derivedRegionCode = "52",
                    derivedPlaceName = "Firenze",
                    derivedPlaceResolverVersion = null
                )
            )
        )

        val row = dao.observeEvaluationProjection().first().single()

        assertEquals("IT", row.derivedCountryCode)
        assertEquals("52", row.derivedRegionCode)
        assertEquals("Firenze", row.derivedPlaceName)
        assertNull(row.derivedPlaceResolverVersion)
    }

    @Test
    fun `evaluation projection tells an out-of-cap row from a row without coordinates`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(
                    mediaUri = "content://media/external/images/media/171",
                    derivedPlaceResolverVersion = 2
                ),
                sampleEntity(
                    mediaUri = "content://media/external/images/media/172",
                    capturedLatitude = null,
                    capturedLongitude = null,
                    derivedPlaceResolverVersion = null
                )
            )
        )

        val byUri = dao.observeEvaluationProjection().first().associateBy { it.mediaUri }
        val outOfCap = byUri.getValue("content://media/external/images/media/171")
        val noCoordinates = byUri.getValue("content://media/external/images/media/172")

        assertTrue(outOfCap.hasCoordinates)
        assertEquals(2, outOfCap.derivedPlaceResolverVersion)
        assertNull(outOfCap.derivedCountryCode)
        assertTrue(!noCoordinates.hasCoordinates)
        assertNull(noCoordinates.derivedPlaceResolverVersion)
    }

    @Test
    fun `evaluation projection is ordered by uri and does not filter by volume`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(mediaUri = "content://media/external/images/media/182", volumeName = "unmounted_volume"),
                sampleEntity(mediaUri = "content://media/external/images/media/181")
            )
        )

        val uris = dao.observeEvaluationProjection().first().map { it.mediaUri }

        assertEquals(
            listOf("content://media/external/images/media/181", "content://media/external/images/media/182"),
            uris
        )
    }

    @Test
    fun `evaluation projection re-emits on upsert marker change and delete`() = runTest {
        val uri = "content://media/external/images/media/191"
        dao.observeEvaluationProjection().test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(listOf(sampleEntity(mediaUri = uri)))
            assertNull(awaitItem().single().derivedPlaceResolverVersion)

            dao.updateResolvedPlace(uri, 43.7696, 11.2558, "IT", "52", "Firenze", 2)
            val resolved = awaitItem().single()
            assertEquals(2, resolved.derivedPlaceResolverVersion)
            assertEquals("Firenze", resolved.derivedPlaceName)

            dao.deleteByMediaUri(uri)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pending place query orders by uri and applies marker coordinates and cursor filters`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(mediaUri = "content://media/external/images/media/3", derivedPlaceResolverVersion = 2),
                sampleEntity(mediaUri = "content://media/external/images/media/2", derivedPlaceResolverVersion = 1),
                sampleEntity(mediaUri = "content://media/external/images/media/1"),
                sampleEntity(
                    mediaUri = "content://media/external/images/media/4",
                    capturedLatitude = null,
                    capturedLongitude = null
                ),
                sampleEntity(mediaUri = "content://media/external/images/media/5")
            )
        )

        val all = dao.listPendingPlaceResolution(currentVersion = 2, afterMediaUri = null, limit = 10)
        val afterFirst = dao.listPendingPlaceResolution(
            currentVersion = 2,
            afterMediaUri = "content://media/external/images/media/1",
            limit = 1
        )

        assertEquals(
            listOf(
                "content://media/external/images/media/1",
                "content://media/external/images/media/2",
                "content://media/external/images/media/5"
            ),
            all.map { it.mediaUri }
        )
        assertEquals(listOf("content://media/external/images/media/2"), afterFirst.map { it.mediaUri })
    }

    @Test
    fun `resolved place update matches uri and exact coordinates and reports rows written`() = runTest {
        val uri = "content://media/external/images/media/81"
        dao.upsert(listOf(sampleEntity(mediaUri = uri, derivedCountryCode = "XX")))

        val wrongLatitude = dao.updateResolvedPlace(uri, 43.7697, 11.2558, "IT", "52", "Firenze", 2)
        val wrongLongitude = dao.updateResolvedPlace(uri, 43.7696, 11.2559, "IT", "52", "Firenze", 2)
        val wrongUri = dao.updateResolvedPlace("$uri-other", 43.7696, 11.2558, "IT", "52", "Firenze", 2)
        val untouched = dao.getByMediaUri(uri)
        val matched = dao.updateResolvedPlace(uri, 43.7696, 11.2558, null, null, null, 2)
        val stored = dao.getByMediaUri(uri)

        assertEquals(listOf(0, 0, 0, 1), listOf(wrongLatitude, wrongLongitude, wrongUri, matched))
        assertEquals("XX", untouched?.derivedCountryCode)
        assertNull(untouched?.derivedPlaceResolverVersion)
        assertNull(stored?.derivedCountryCode)
        assertEquals(2, stored?.derivedPlaceResolverVersion)
    }

    @Test
    fun `watermark table upsert list and delete work by volume`() {
        dao.upsertWatermark(
            MediaIndexWatermarkEntity(
                volumeName = "external_primary",
                version = "v3",
                generation = 11L,
                dateModifiedWatermarkSeconds = 900L
            )
        )
        dao.upsertWatermark(
            MediaIndexWatermarkEntity(
                volumeName = "external",
                version = "v4",
                generation = null,
                dateModifiedWatermarkSeconds = 700L
            )
        )

        val all = dao.listWatermarks().associateBy { it.volumeName }
        assertEquals(setOf("external_primary", "external"), all.keys)
        assertEquals("v3", all.getValue("external_primary").version)

        dao.deleteWatermark("external")
        assertNull(dao.getWatermark("external"))
        assertNotNull(dao.getWatermark("external_primary"))
    }

    @Test
    fun `face state upsert replaces by media uri and stores the outcome by name`() = runTest {
        val uri = "content://media/external/images/media/301"

        dao.upsertFaceState(MediaIndexFaceStateEntity(uri, pipelineVersion = 1, outcome = FaceIndexOutcome.FAILED))
        dao.upsertFaceState(MediaIndexFaceStateEntity(uri, pipelineVersion = 2, outcome = FaceIndexOutcome.INDEXED))

        assertEquals(
            MediaIndexFaceStateEntity(uri, 2, FaceIndexOutcome.INDEXED),
            dao.getFaceState(uri)
        )
        val stored = database.openHelper.readableDatabase
            .query("SELECT outcome FROM media_index_face_state").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        assertEquals("INDEXED", stored)
        assertNull(dao.getFaceState("$uri-other"))
    }

    @Test
    fun `faces get ascending ids and list by uri with every column unshuffled`() = runTest {
        val uri = "content://media/external/images/media/311"
        dao.insertFaces(
            listOf(
                faceEntity(uri, boxLeft = 0.1f, embedding = byteArrayOf(1, 2, 3, 4), groupId = 7L, groupingVersion = 3),
                faceEntity(uri, boxLeft = 0.2f),
                faceEntity("$uri-other")
            )
        )

        val listed = dao.listFaces(uri)

        assertEquals(2, listed.size)
        assertTrue(listed[0].id < listed[1].id)
        assertEquals(listOf(0.1f, 0.2f), listed.map { it.boxLeft })
        assertEquals(listOf(0.2f, 0.2f), listed.map { it.boxTop })
        assertEquals(listOf(0.5f, 0.5f), listed.map { it.boxRight })
        assertEquals(listOf(0.6f, 0.6f), listed.map { it.boxBottom })
        assertEquals(listOf(5, 5), listed.map { it.pipelineVersion })
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), listed[0].embedding)
        assertEquals(7L, listed[0].groupId)
        assertEquals(3, listed[0].groupingVersion)
        assertNull(listed[1].groupId)
        assertNull(listed[1].groupingVersion)
    }

    @Test
    fun `a face id is not reused after the highest row is deleted`() = runTest {
        val uri = "content://media/external/images/media/321"
        dao.insertFaces(listOf(faceEntity(uri)))
        val firstId = dao.listFaces(uri).single().id
        dao.deleteFaces(uri)

        dao.insertFaces(listOf(faceEntity(uri)))

        assertTrue(dao.listFaces(uri).single().id > firstId)
    }

    @Test
    fun `has face data is true for a state alone or a face alone and false for neither`() = runTest {
        val uri = "content://media/external/images/media/331"
        assertFalse(dao.hasFaceData())

        dao.upsertFaceState(MediaIndexFaceStateEntity(uri, 1, FaceIndexOutcome.INDEXED))
        assertTrue(dao.hasFaceData())
        dao.deleteFaceState(uri)
        assertFalse(dao.hasFaceData())

        dao.insertFaces(listOf(faceEntity(uri)))
        assertTrue(dao.hasFaceData())
        dao.deleteFaces(uri)
        assertFalse(dao.hasFaceData())
    }

    @Test
    fun `delete by uri removes that image's face rows only`() = runTest {
        val kept = "content://media/external/images/media/341"
        val removed = "content://media/external/images/media/342"
        dao.insertFaces(listOf(faceEntity(kept), faceEntity(removed), faceEntity(removed)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(kept, 1, FaceIndexOutcome.INDEXED))
        dao.upsertFaceState(MediaIndexFaceStateEntity(removed, 1, FaceIndexOutcome.INDEXED))

        dao.deleteFaces(removed)
        dao.deleteFaceState(removed)

        assertEquals(1, dao.listFaces(kept).size)
        assertTrue(dao.listFaces(removed).isEmpty())
        assertNotNull(dao.getFaceState(kept))
        assertNull(dao.getFaceState(removed))
    }

    @Test
    fun `re-keying moves one image's face rows and state and keeps their ids`() = runTest {
        val from = "content://media/external/images/media/351"
        val to = "content://media/0123-4567/images/media/9"
        val other = "content://media/external/images/media/352"
        dao.insertFaces(listOf(faceEntity(from), faceEntity(from), faceEntity(other)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(from, 4, FaceIndexOutcome.INDEXED))
        val idsBefore = dao.listFaces(from).map { it.id }

        dao.rekeyFaces(from, to)
        dao.rekeyFaceState(from, to)

        assertTrue(dao.listFaces(from).isEmpty())
        assertNull(dao.getFaceState(from))
        assertEquals(idsBefore, dao.listFaces(to).map { it.id })
        assertEquals(4, dao.getFaceState(to)?.pipelineVersion)
        assertEquals(1, dao.listFaces(other).size)
    }

    @Test
    fun `delete by volume removes the tier c rows of that volume's index rows only`() = runTest {
        val onCard = "content://media/0123-4567/images/media/361"
        val onPhone = "content://media/external_primary/images/media/362"
        dao.upsert(
            listOf(
                sampleEntity(onCard, volumeName = "0123-4567"),
                sampleEntity(onPhone, volumeName = "external_primary")
            )
        )
        dao.insertFaces(listOf(faceEntity(onCard), faceEntity(onPhone)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(onCard, 1, FaceIndexOutcome.INDEXED))
        dao.upsertFaceState(MediaIndexFaceStateEntity(onPhone, 1, FaceIndexOutcome.INDEXED))

        dao.deleteFacesByVolume("0123-4567")
        dao.deleteFaceStatesByVolume("0123-4567")

        assertTrue(dao.listFaces(onCard).isEmpty())
        assertNull(dao.getFaceState(onCard))
        assertEquals(1, dao.listFaces(onPhone).size)
        assertNotNull(dao.getFaceState(onPhone))
        assertNotNull(dao.getByMediaUri(onCard))
    }

    @Test
    fun `delete all reports how many rows each tier c table lost`() = runTest {
        val uri = "content://media/external/images/media/371"
        dao.insertFaces(listOf(faceEntity(uri), faceEntity(uri), faceEntity(uri)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(uri, 1, FaceIndexOutcome.INDEXED))

        assertEquals(3, dao.deleteAllFaces())
        assertEquals(1, dao.deleteAllFaceStates())
        assertEquals(0, dao.deleteAllFaces())
        assertEquals(0, dao.deleteAllFaceStates())
    }

    @Test
    fun `replacing an index row leaves its tier c rows because nothing cascades`() = runTest {
        val uri = "content://media/external/images/media/381"
        dao.upsert(listOf(sampleEntity(uri)))
        dao.insertFaces(listOf(faceEntity(uri)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(uri, 1, FaceIndexOutcome.INDEXED))

        dao.upsert(listOf(sampleEntity(uri, displayName = "replaced.jpg")))
        dao.deleteByMediaUri(uri)

        assertEquals(1, dao.listFaces(uri).size)
        assertNotNull(dao.getFaceState(uri))
    }

    @Test
    fun `faces for grouping are one pipeline version's, in id order, after the cursor, with their embeddings`() = runTest {
        dao.insertFaces(
            listOf(
                faceEntity(URI_1, embedding = byteArrayOf(1, 2, 3, 4)),
                faceEntity(URI_2, pipelineVersion = 6),
                faceEntity(URI_3, embedding = byteArrayOf(5, 6, 7, 8)),
                faceEntity(URI_4)
            )
        )
        val ids = listOf(URI_1, URI_3, URI_4).map { dao.listFaces(it).single().id }

        val all = dao.listFacesForGrouping(pipelineVersion = 5, afterFaceId = null, limit = 10)
        val afterFirst = dao.listFacesForGrouping(5, ids[0], limit = 1)

        assertEquals(ids, all.map { it.id })
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), all[0].embedding)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), all[1].embedding)
        assertEquals(listOf(ids[1]), afterFirst.map { it.id })
        assertEquals(1, dao.listFacesForGrouping(6, null, 10).size)
    }

    @Test
    fun `grouping is pending for a null or another grouping version, and for no faces it is not`() = runTest {
        assertFalse(dao.hasFacesPendingGrouping(5, 3))
        dao.insertFaces(listOf(faceEntity(URI_1), faceEntity(URI_2)))
        val ids = listOf(URI_1, URI_2).map { dao.listFaces(it).single().id }
        assertTrue(dao.hasFacesPendingGrouping(5, 3))

        dao.updateFaceGroup(ids[0], 3, 5, ids)

        assertFalse(dao.hasFacesPendingGrouping(5, 3))
        assertTrue(dao.hasFacesPendingGrouping(5, 4))
        assertTrue(dao.hasFacesPendingGrouping(5, 2))
    }

    @Test
    fun `grouping is pending when a group id names no face at the version, whether deleted or at another version`() = runTest {
        dao.insertFaces(listOf(faceEntity(URI_1), faceEntity(URI_2), faceEntity(URI_3), faceEntity(URI_4, pipelineVersion = 6)))
        val ids = listOf(URI_1, URI_2, URI_3).map { dao.listFaces(it).single().id }
        val elsewhere = dao.listFaces(URI_4).single().id
        dao.updateFaceGroup(ids[0], 3, 5, ids)
        assertFalse(dao.hasFacesPendingGrouping(5, 3))

        dao.deleteFaces(URI_3)
        assertFalse("a deleted member that was not the representative", dao.hasFacesPendingGrouping(5, 3))
        dao.deleteFaces(URI_1)
        assertTrue("the representative is gone", dao.hasFacesPendingGrouping(5, 3))

        dao.updateFaceGroup(elsewhere, 3, 5, listOf(ids[1]))
        assertTrue("the representative exists, but at another pipeline version", dao.hasFacesPendingGrouping(5, 3))
    }

    @Test
    fun `grouping pending ignores faces at another pipeline version`() = runTest {
        dao.insertFaces(listOf(faceEntity(URI_1), faceEntity(URI_2), faceEntity(URI_3, pipelineVersion = 6)))
        val ids = listOf(URI_1, URI_2).map { dao.listFaces(it).single().id }
        dao.updateFaceGroup(ids[0], 3, 5, ids)

        assertFalse(dao.hasFacesPendingGrouping(5, 3))
        assertTrue(dao.hasFacesPendingGrouping(6, 3))
    }

    @Test
    fun `the group update writes the listed faces at the version only and reports the rows written`() = runTest {
        dao.insertFaces(listOf(faceEntity(URI_1), faceEntity(URI_2), faceEntity(URI_3), faceEntity(URI_4, pipelineVersion = 6)))
        val ids = listOf(URI_1, URI_2, URI_3, URI_4).map { dao.listFaces(it).single().id }

        val written = dao.updateFaceGroup(ids[0], 3, 5, listOf(ids[0], ids[1], ids[3], 987_654L))

        assertEquals(2, written)
        assertEquals(listOf(ids[0], ids[0], null, null), listOf(URI_1, URI_2, URI_3, URI_4).map { dao.listFaces(it).single().groupId })
        assertEquals(listOf(3, 3, null, null), listOf(URI_1, URI_2, URI_3, URI_4).map { dao.listFaces(it).single().groupingVersion })
    }

    @Test
    fun `the group query counts distinct images among index rows that still exist and joins the representative`() = runTest {
        dao.upsert(
            listOf(
                sampleEntity(URI_1, displayName = "rep.jpg", size = 11L, dateModified = 22L, capturedDateTakenEpochMillis = 100L),
                sampleEntity(URI_2, capturedDateTakenEpochMillis = 300L),
                sampleEntity(URI_5, capturedDateTakenEpochMillis = null)
            )
        )
        // URI_3 has faces and no index row; URI_1 holds two faces.
        dao.insertFaces(
            listOf(faceEntity(URI_1, boxLeft = 0.125f), faceEntity(URI_1), faceEntity(URI_2), faceEntity(URI_3), faceEntity(URI_5))
        )
        val ids = dao.listFaces(URI_1).map { it.id } + listOf(URI_2, URI_3, URI_5).map { dao.listFaces(it).single().id }
        dao.updateFaceGroup(ids[0], 3, 5, ids)

        val groups = dao.listFaceGroups(pipelineVersion = 5, groupingVersion = 3, minimumImages = 1)

        val group = groups.single()
        assertEquals(ids[0], group.groupId)
        assertEquals(3, group.imageCount)
        assertEquals(300L, group.newestDateTakenEpochMillis)
        assertEquals(URI_1, group.representativeMediaUri)
        assertEquals("rep.jpg", group.representativeDisplayName)
        assertEquals(11L, group.representativeSize)
        assertEquals(22L, group.representativeDateModified)
        assertEquals(listOf(0.125f, 0.2f, 0.5f, 0.6f), listOf(group.boxLeft, group.boxTop, group.boxRight, group.boxBottom))
        assertEquals(5, group.pipelineVersion)
    }

    @Test
    fun `the group query applies the minimum, both versions and the representative, and sorts as the picker does`() = runTest {
        val uris = listOf(URI_1, URI_2, URI_3, URI_4, URI_5, URI_6, URI_7)
        val dates = listOf(null, null, 200L, 300L, 500L, null, 900L)
        dao.upsert(uris.mapIndexed { index, uri -> sampleEntity(uri, capturedDateTakenEpochMillis = dates[index]) })
        dao.insertFaces(uris.map { faceEntity(it) })
        val ids = uris.map { dao.listFaces(it).single().id }
        // Two images with no date, two with 200 and 300, two with 500 and none, and a lone image no minimum of 2 keeps.
        dao.updateFaceGroup(ids[1], 3, 5, listOf(ids[0], ids[1]))
        dao.updateFaceGroup(ids[2], 3, 5, listOf(ids[2], ids[3]))
        dao.updateFaceGroup(ids[4], 3, 5, listOf(ids[4], ids[5]))
        dao.updateFaceGroup(ids[6], 3, 5, listOf(ids[6]))

        val listed = dao.listFaceGroups(5, 3, minimumImages = 2)

        assertEquals(listOf(ids[4], ids[2], ids[1]), listed.map { it.groupId })
        assertEquals(listOf(500L, 300L, null), listed.map { it.newestDateTakenEpochMillis })
        assertEquals(listOf(2, 2, 2), listed.map { it.imageCount })
        assertEquals(4, dao.listFaceGroups(5, 3, 1).size)
        assertTrue(dao.listFaceGroups(5, 4, 1).isEmpty())
        assertTrue(dao.listFaceGroups(6, 3, 1).isEmpty())
    }

    @Test
    fun `the face evaluation query reads an image once per face at the version, an empty one once, and no other version`() = runTest {
        dao.upsert(listOf(sampleEntity(URI_1), sampleEntity(URI_2), sampleEntity(URI_3), sampleEntity(URI_4)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(URI_1, 5, FaceIndexOutcome.INDEXED))
        dao.upsertFaceState(MediaIndexFaceStateEntity(URI_2, 5, FaceIndexOutcome.FAILED))
        dao.upsertFaceState(MediaIndexFaceStateEntity(URI_3, 4, FaceIndexOutcome.INDEXED))
        // A state whose index row is gone is not an image of the index.
        dao.upsertFaceState(MediaIndexFaceStateEntity("content://media/external/images/media/999", 5, FaceIndexOutcome.INDEXED))
        dao.insertFaces(listOf(faceEntity(URI_1, boxLeft = 0.125f), faceEntity(URI_1), faceEntity(URI_3, pipelineVersion = 4)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(URI_4, 5, FaceIndexOutcome.INDEXED))
        dao.insertFaces(listOf(faceEntity(URI_4, pipelineVersion = 4)))

        val rows = dao.observeFaceEvaluationRows(5).first()

        assertEquals(listOf(URI_1, URI_1, URI_2, URI_4), rows.map { it.mediaUri })
        assertEquals(listOf(FaceIndexOutcome.INDEXED, FaceIndexOutcome.INDEXED, FaceIndexOutcome.FAILED, FaceIndexOutcome.INDEXED), rows.map { it.outcome })
        assertEquals(dao.listFaces(URI_1).map { it.id }, rows.take(2).map { it.faceId })
        assertEquals(listOf(0.125f, 0.1f), rows.take(2).map { it.boxLeft })
        assertEquals(listOf(null, null), rows.drop(2).map { it.faceId })
        assertEquals("photo.jpg", rows.first().displayName)
        assertEquals(123L, rows.first().size)
        assertEquals(456L, rows.first().dateModified)
    }

    @Test
    fun `the face evaluation flow emits again on a group write alone`() = runTest {
        dao.upsert(listOf(sampleEntity(URI_1)))
        dao.upsertFaceState(MediaIndexFaceStateEntity(URI_1, 5, FaceIndexOutcome.INDEXED))
        dao.insertFaces(listOf(faceEntity(URI_1)))
        val faceId = dao.listFaces(URI_1).single().id

        dao.observeFaceEvaluationRows(5).test {
            assertNull(awaitItem().single().groupId)

            dao.updateFaceGroup(faceId, 3, 5, listOf(faceId))

            val grouped = awaitItem().single()
            assertEquals(faceId, grouped.groupId)
            assertEquals(3, grouped.groupingVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun faceEntity(
        mediaUri: String,
        pipelineVersion: Int = 5,
        boxLeft: Float = 0.1f,
        embedding: ByteArray = byteArrayOf(9, 8, 7, 6),
        groupId: Long? = null,
        groupingVersion: Int? = null
    ): MediaIndexFaceEntity {
        return MediaIndexFaceEntity(
            mediaUri = mediaUri,
            pipelineVersion = pipelineVersion,
            boxLeft = boxLeft,
            boxTop = 0.2f,
            boxRight = 0.5f,
            boxBottom = 0.6f,
            embedding = embedding,
            groupId = groupId,
            groupingVersion = groupingVersion
        )
    }

    private fun sampleEntity(
        mediaUri: String,
        displayName: String = "photo.jpg",
        volumeName: String = "external_primary",
        size: Long = 123L,
        dateModified: Long = 456L,
        capturedDateTakenEpochMillis: Long? = 1_725_000_000_000L,
        capturedWidth: Int? = null,
        capturedHeight: Int? = null,
        derivedCountryCode: String? = null,
        derivedRegionCode: String? = null,
        derivedPlaceName: String? = null,
        capturedLatitude: Double? = 43.7696,
        capturedLongitude: Double? = 11.2558,
        derivedPlaceResolverVersion: Int? = null
    ): MediaIndexEntity {
        return MediaIndexEntity(
            mediaUri = mediaUri,
            displayName = displayName,
            size = size,
            dateModified = dateModified,
            volumeName = volumeName,
            generationAdded = 10L,
            generationModified = 11L,
            capturedDateTakenEpochMillis = capturedDateTakenEpochMillis,
            capturedLatitude = capturedLatitude,
            capturedLongitude = capturedLongitude,
            capturedWidth = capturedWidth,
            capturedHeight = capturedHeight,
            derivedCountryCode = derivedCountryCode,
            derivedRegionCode = derivedRegionCode,
            derivedPlaceName = derivedPlaceName,
            derivedPlaceResolverVersion = derivedPlaceResolverVersion
        )
    }

    private companion object {
        const val URI_1 = "content://media/external/images/media/401"
        const val URI_2 = "content://media/external/images/media/402"
        const val URI_3 = "content://media/external/images/media/403"
        const val URI_4 = "content://media/external/images/media/404"
        const val URI_5 = "content://media/external/images/media/405"
        const val URI_6 = "content://media/external/images/media/406"
        const val URI_7 = "content://media/external/images/media/407"
    }
}
