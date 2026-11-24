package com.davide.seddio.easygallery.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The grouping pass on the in-memory store and the real [groupFaces] (plan 3.4, Task 3, criteria 6 and 7). */
class FaceGroupingPassTest {

    private val store = InMemoryMediaIndexStore()
    private val setting = InMemoryFaceDataSettingStore(initiallyEnabled = true)
    private val log = mutableListOf<String>()
    private var now = 0L
    private var nextImage = 1

    // ---- the pass groups what it reads, and writes once ----

    @Test
    fun `images of one person form one group whose id is the medoid's face id, not the lowest id`() = runTest {
        val edge = addImage(PERSON_A_RIGHT)
        val center = addImage(PERSON_A)
        val other = addImage(PERSON_A_LEFT)

        val stop = pass().run(now)

        assertEquals(FaceGroupingStop.GROUPED, stop)
        val centerFace = faceOf(center)
        assertTrue(centerFace.id > faceOf(edge).id)
        listOf(edge, center, other).forEach {
            assertEquals(centerFace.id, faceOf(it).groupId)
            assertEquals(FACE_GROUPING_VERSION, faceOf(it).groupingVersion)
        }
        val offered = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2).single()
        assertEquals(centerFace.id, offered.groupId)
        assertEquals(3, offered.imageCount)
        assertEquals(center, offered.representative.identity.mediaUri)
        assertEquals(centerFace.box, offered.representative.box)
    }

    @Test
    fun `two people stay in two groups`() = runTest {
        val a = listOf(addImage(PERSON_A), addImage(PERSON_A_RIGHT))
        val b = listOf(addImage(PERSON_B), addImage(PERSON_B_UP))

        pass().run(now)

        assertEquals(1, a.map { faceOf(it).groupId }.distinct().size)
        assertEquals(1, b.map { faceOf(it).groupId }.distinct().size)
        assertTrue(faceOf(a[0]).groupId != faceOf(b[0]).groupId)
        assertEquals(2, store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2).size)
    }

    @Test
    fun `a person seen in one image is grouped but not offered, nor are two faces of one image`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        val single = addImage(PERSON_B)
        val twoFaces = addImage(PERSON_C, PERSON_C_NEAR)

        pass().run(now)

        assertEquals(faceOf(single).id, faceOf(single).groupId)
        assertEquals(FACE_GROUPING_VERSION, faceOf(single).groupingVersion)
        assertEquals(1, store.listFaces(twoFaces).map { it.groupId }.distinct().size)
        val offered = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2)
        assertEquals(1, offered.size)
        assertEquals(3, store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 1).size)
    }

    @Test
    fun `the picker orders groups by how many images show the person`() = runTest {
        val fewer = listOf(addImage(PERSON_A), addImage(PERSON_A_RIGHT))
        val more = listOf(addImage(PERSON_B), addImage(PERSON_B_UP), addImage(PERSON_B))

        pass().run(now)

        val offered = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2)
        assertEquals(listOf(3, 2), offered.map { it.imageCount })
        assertEquals(faceOf(more[0]).groupId, offered[0].groupId)
        assertEquals(faceOf(fewer[0]).groupId, offered[1].groupId)
    }

    @Test
    fun `the pass reads every page of faces`() = runTest {
        val a = (1..3).map { addImage(PERSON_A) }
        val b = (1..2).map { addImage(PERSON_B) }

        val stop = pass(pageSize = 2).run(now)

        assertEquals(FaceGroupingStop.GROUPED, stop)
        assertEquals(1, a.map { faceOf(it).groupId }.distinct().size)
        assertEquals(1, b.map { faceOf(it).groupId }.distinct().size)
        assertEquals(listOf(3, 2), store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2).map { it.imageCount })
    }

    // ---- whether it runs ----

    @Test
    fun `a second run finds nothing pending and writes nothing`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        val spy = SpyStore(store)

        val first = pass(spy).run(now)
        val second = pass(spy).run(now)

        assertEquals(FaceGroupingStop.GROUPED, first)
        assertEquals(FaceGroupingStop.NOT_NEEDED, second)
        assertEquals(1, spy.writes)
        assertEquals(1, spy.reads)
    }

    @Test
    fun `no faces at all is not needed and writes nothing`() = runTest {
        val spy = SpyStore(store)

        assertEquals(FaceGroupingStop.NOT_NEEDED, pass(spy).run(now))

        assertEquals(0, spy.reads)
        assertEquals(0, spy.writes)
    }

    @Test
    fun `an old or a newer grouping version is regrouped from scratch and stamped with the current one`() = runTest {
        val images = listOf(addImage(PERSON_A), addImage(PERSON_A_RIGHT))
        listOf(FACE_GROUPING_VERSION - 1, FACE_GROUPING_VERSION + 1).forEach { stale ->
            // Each face in a group of its own, under a version that is not the current one.
            val faces = images.map { faceOf(it).id }
            store.writeFaceGroups(faces.map { FaceGroup(it, listOf(it)) }, FACE_PIPELINE_VERSION, stale) { true }
            assertTrue(store.isFaceGroupingNeeded(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION))

            val stop = pass().run(now)

            assertEquals(FaceGroupingStop.GROUPED, stop)
            assertEquals(1, images.map { faceOf(it).groupId }.distinct().size)
            assertEquals(listOf(FACE_GROUPING_VERSION, FACE_GROUPING_VERSION), images.map { faceOf(it).groupingVersion })
        }
    }

    @Test
    fun `a deleted representative leaves its group pending, and the rest regroup around a new representative`() = runTest {
        val center = addImage(PERSON_A)
        val first = addImage(PERSON_A_RIGHT)
        val second = addImage(PERSON_A_LEFT)
        pass().run(now)
        val oldRepresentative = faceOf(center).id
        assertEquals(oldRepresentative, faceOf(first).groupId)

        store.deleteByMediaUri(center)
        val stop = pass().run(now)

        // The two left are equally central, so the tie goes to the lower face id.
        assertEquals(FaceGroupingStop.GROUPED, stop)
        assertEquals(listOf(faceOf(first).id, faceOf(first).id), listOf(first, second).map { faceOf(it).groupId })
        assertEquals(faceOf(first).id, store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2).single().groupId)
    }

    @Test
    fun `a deleted face that was not the representative does not regroup`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        val third = addImage(PERSON_A_LEFT)
        pass().run(now)

        store.deleteByMediaUri(third)

        assertEquals(FaceGroupingStop.NOT_NEEDED, pass().run(now))
    }

    // ---- stops: the setting, the deadline, cancellation ----

    @Test
    fun `the setting off at the start reads nothing and writes nothing`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        setting.setEnabled(false)
        val spy = SpyStore(store)

        val stop = pass(spy).run(now)

        assertEquals(FaceGroupingStop.SETTING_OFF, stop)
        assertEquals(0, spy.reads)
        assertEquals(0, spy.writes)
        assertNull(store.listFaces(uriOf(1)).single().groupId)
    }

    @Test
    fun `the setting turning off while the faces are read writes nothing`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        val spy = SpyStore(store)
        spy.beforeRead = { setting.setEnabled(false) }

        val stop = pass(spy).run(now)

        assertEquals(FaceGroupingStop.SETTING_OFF, stop)
        assertEquals(0, spy.writes)
        assertTrue(store.isFaceGroupingNeeded(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION))
    }

    @Test
    fun `the setting turning off just before the write is stopped by the store's own check and reported`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        val spy = SpyStore(store)
        spy.beforeWrite = { setting.setEnabled(false) }

        val stop = pass(spy).run(now)

        assertEquals(1, spy.writes)
        assertEquals(FaceGroupingStop.SETTING_OFF, stop)
        assertNull(store.listFaces(uriOf(1)).single().groupId)
        assertNull(store.listFaces(uriOf(2)).single().groupingVersion)
    }

    @Test
    fun `a start past the deadline reads nothing and writes nothing, and one just before it runs`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        val spy = SpyStore(store)
        val started = 1_000L

        now = started + FACE_GROUPING_START_DEADLINE_MILLIS
        val late = pass(spy).run(started)

        assertEquals(FaceGroupingStop.PAST_DEADLINE, late)
        assertEquals(0, spy.reads)
        assertEquals(0, spy.writes)

        now = started + FACE_GROUPING_START_DEADLINE_MILLIS - 1
        assertEquals(FaceGroupingStop.GROUPED, pass(spy).run(started))
    }

    @Test
    fun `a cancellation stops the pass with nothing written and no line`() = runTest {
        addImage(PERSON_A)
        addImage(PERSON_A_RIGHT)
        val spy = SpyStore(store)
        lateinit var job: Job
        spy.beforeRead = { job.cancel() }
        job = launch { pass(spy).run(now) }

        job.join()

        assertTrue(job.isCancelled)
        assertEquals(0, spy.writes)
        assertTrue(log.isEmpty())
        assertTrue(store.isFaceGroupingNeeded(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION))
    }

    @Test
    fun `a cancellation reaches the caller as a cancellation`() = runTest {
        addImage(PERSON_A)
        val spy = SpyStore(store)
        spy.beforeRead = { throw CancellationException("cancelled") }

        val failure = runCatching { pass(spy).run(now) }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(log.isEmpty())
    }

    // ---- failures ----

    @Test
    fun `a thrown error propagates untouched, with nothing written and nothing logged`() = runTest {
        addImage(PERSON_A)
        val spy = SpyStore(store)
        spy.beforeRead = { throw IllegalStateException("boom $LEAK_URI $LEAK_NAME") }

        val failure = runCatching { pass(spy).run(now) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(0, spy.writes)
        assertTrue("a run that threw says nothing", log.isEmpty())
    }

    @Test
    fun `embeddings of unequal length fail the pass with the fixed message and write nothing`() = runTest {
        addImage(PERSON_A)
        addImage(floatArrayOf(1f, 0f, 0f))
        val spy = SpyStore(store)

        val failure = runCatching { pass(spy).run(now) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(FACE_GROUPING_UNEQUAL_LENGTH_MESSAGE, failure!!.message)
        assertEquals(0, spy.writes)
        assertTrue(log.isEmpty())
    }

    // ---- the logger rule (ADR-0024 §6) ----

    @Test
    fun `every grouping line is a fixed stop name with no digit, and none holds a uri, a name, an id or an embedding value`() = runTest {
        val first = addImage(LEAK_EMBEDDING, number = 48151623, name = LEAK_NAME)
        val second = addImage(floatArrayOf(0.1358f, 0.2469f, 0.9753f, 0f), number = 48151624, name = LEAK_NAME)

        val grouped = pass().run(now)
        val notNeeded = pass().run(now)
        store.deleteByMediaUri(first)
        addImage(LEAK_EMBEDDING, number = 48151625, name = LEAK_NAME)
        setting.setEnabled(false)
        val settingOff = pass().run(now)
        setting.setEnabled(true)
        now = FACE_GROUPING_START_DEADLINE_MILLIS
        val late = pass().run(0L)

        assertEquals(
            listOf(FaceGroupingStop.GROUPED, FaceGroupingStop.NOT_NEEDED, FaceGroupingStop.SETTING_OFF, FaceGroupingStop.PAST_DEADLINE),
            listOf(grouped, notNeeded, settingOff, late)
        )
        assertEquals(4, log.size)
        FaceLogRules.assertFixedSet(log)
        log.forEach { assertFalse("a grouping line carries a count, an id or a time: $it", Regex("\\d").containsMatchIn(it)) }
        FaceLogRules.assertNoneOf(log, listOf(first, second, LEAK_NAME, "48151623") + LEAK_VALUES)
    }

    @Test
    fun `the person record's types print nothing of what they hold`() = runTest {
        val image = addImage(LEAK_EMBEDDING, number = 48151623, name = LEAK_NAME)
        addImage(LEAK_EMBEDDING, number = 48151624, name = LEAK_NAME)
        pass().run(now)

        val summary = store.listFaceGroups(FACE_PIPELINE_VERSION, FACE_GROUPING_VERSION, 2).single()

        val printed = listOf(
            summary.toString(),
            summary.representative.toString(),
            FaceGroupingInput(1L, FaceEmbedding(LEAK_EMBEDDING)).toString()
        )
        FaceLogRules.assertNoneOf(printed, listOf(image, LEAK_NAME, "48151623") + LEAK_VALUES)
        assertEquals("FaceGroupSummary(redacted)", summary.toString())
        assertEquals("FaceGroupRepresentative(redacted)", summary.representative.toString())
    }

    // ---- fixtures ----

    private fun pass(
        over: MediaIndexStore = store,
        pageSize: Int = FACE_GROUPING_PAGE_SIZE
    ) = FaceGroupingPass(
        store = over,
        settingStore = setting,
        log = { log += it },
        clock = { now },
        pageSize = pageSize
    )

    private fun uriOf(number: Int) = "content://media/external/images/media/$number"

    // One image holding one face per embedding, indexed at the current pipeline version; returns the image's uri.
    private suspend fun addImage(vararg embeddings: FloatArray, number: Int = nextImage++, name: String = "photo$number.jpg"): String {
        val uri = uriOf(number)
        val row = MediaIndexEntry(
            mediaUri = uri,
            displayName = name,
            size = 10L,
            dateModified = 20L,
            volumeName = "external_primary",
            generationAdded = 1L,
            generationModified = 2L,
            capturedDateTakenEpochMillis = 1_700_000_000_000L + number,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )
        store.upsert(listOf(row))
        val faces = embeddings.map { FaceDetection(FaceBox(0.1f, 0.1f, 0.5f, 0.5f), FaceEmbedding(it)) }
        store.writeFaceIndexResults(
            listOf(FaceIndexWrite(row.toMediaIdentity(), FACE_PIPELINE_VERSION, FaceIndexOutcome.INDEXED, faces)),
            faceDataEnabled = { true }
        )
        return uri
    }

    private suspend fun faceOf(uri: String): StoredFace = store.listFaces(uri).first()

    // Lets a test act at the moment the pass reads or writes, and counts both.
    private class SpyStore(private val inner: InMemoryMediaIndexStore) : MediaIndexStore by inner {
        var reads = 0
        var writes = 0
        var beforeRead: () -> Unit = {}
        var beforeWrite: () -> Unit = {}

        override suspend fun listFacesForGrouping(pipelineVersion: Int, afterFaceId: Long?, limit: Int): List<FaceGroupingInput> {
            reads += 1
            beforeRead()
            return inner.listFacesForGrouping(pipelineVersion, afterFaceId, limit)
        }

        override suspend fun writeFaceGroups(
            groups: List<FaceGroup>,
            pipelineVersion: Int,
            groupingVersion: Int,
            faceDataEnabled: () -> Boolean
        ): Int {
            writes += 1
            beforeWrite()
            return inner.writeFaceGroups(groups, pipelineVersion, groupingVersion, faceDataEnabled)
        }
    }

    private companion object {
        // Cosine distance within a person is under 0.03; between the people it is over 0.9.
        val PERSON_A = floatArrayOf(1f, 0f, 0f, 0f)
        val PERSON_A_RIGHT = floatArrayOf(1f, 0.1f, 0f, 0f)
        val PERSON_A_LEFT = floatArrayOf(1f, -0.1f, 0f, 0f)
        val PERSON_B = floatArrayOf(0f, 1f, 0f, 0f)
        val PERSON_B_UP = floatArrayOf(0.1f, 1f, 0f, 0f)
        val PERSON_C = floatArrayOf(0f, 0f, 1f, 0f)
        val PERSON_C_NEAR = floatArrayOf(0f, 0f, 1f, 0.1f)

        const val LEAK_NAME = "LEAKCHECK-Maria_Rossi-4242.jpg"
        const val LEAK_URI = "content://media/external/images/media/48151623"
        val LEAK_EMBEDDING = floatArrayOf(0.13579f, 0.2468f, 0.97531f, 0f)

        // The rendered forms of the embedding values.
        val LEAK_VALUES = listOf("0.13579", "0.2468", "0.97531", "0.1358", "0.2469", "0.9753")
    }
}
