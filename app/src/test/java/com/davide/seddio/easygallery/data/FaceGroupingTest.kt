package com.davide.seddio.easygallery.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGroupingTest {

    // ---- the core, on stated distances ----

    @Test
    fun `the core equals a naive average linkage on seeded sets whose distances crowd around the cut`() = runTest {
        var compared = 0
        for (seed in 1..SEEDS) {
            val random = java.util.Random(seed.toLong())
            val size = 2 + random.nextInt(59)
            val distances = Array(size) { FloatArray(size) }
            for (first in 0 until size) {
                for (second in first + 1 until size) {
                    var value = 0.5f + (random.nextFloat() - 0.5f) * 0.4f
                    if (random.nextInt(6) == 0) value *= 0.5f
                    distances[first][second] = value
                    distances[second][first] = value
                }
            }

            val (expected, margin) = referenceClusters(distances, FACE_GROUPING_CUT)
            // A decision closer than float32 rounding can move is not a decision the two can be asked to agree on.
            if (margin < AMBIGUOUS_MARGIN) continue
            val triangle = triangleOf(size) { first, second -> distances[first][second] }

            assertEquals("seed $seed", expected, clustersOf(cluster(size, triangle)))
            compared += 1
        }
        assertTrue("only $compared of $SEEDS seeded sets were unambiguous", compared >= 50)
    }

    @Test
    fun `a pair at exactly the cut merges and a pair one float above it does not`() = runTest {
        val atCut = triangleOf(2) { _, _ -> 0.5f }
        val above = triangleOf(2) { _, _ -> Math.nextUp(0.5f) }
        val below = triangleOf(2) { _, _ -> Math.nextDown(0.5f) }

        assertEquals(setOf(setOf(0, 1)), clustersOf(cluster(2, atCut)))
        assertEquals(setOf(setOf(0), setOf(1)), clustersOf(cluster(2, above)))
        assertEquals(setOf(setOf(0, 1)), clustersOf(cluster(2, below)))
    }

    @Test
    fun `two embeddings at a cosine distance of exactly 0 point 5 are one group`() = runTest {
        val faces = listOf(input(1, 1f, 0f, 0f, 0f), input(2, 0.5f, 0.5f, 0.5f, 0.5f))

        val triangle = faceDistanceTriangle(normaliseFaces(faces)) { true }
        val result = groupFaces(faces)

        assertEquals(0.5f, triangle!![0], 0f)
        assertEquals(setOf(setOf(1L, 2L)), result!!.asSets())
    }

    @Test
    fun `two embeddings clearly above the cut are two groups`() = runTest {
        val faces = listOf(input(1, 1f, 0f, 0f, 0f), input(2, 0.49f, 0.5f, 0.5f, 0.5f))

        val triangle = faceDistanceTriangle(normaliseFaces(faces)) { true }!!

        assertTrue("the fixture is meant to sit just above 0.50", triangle[0] > 0.5f && triangle[0] < 0.52f)
        assertEquals(setOf(setOf(1L), setOf(2L)), groupFaces(faces)!!.asSets())
    }

    @Test
    fun `a chain that single linkage would join stays apart under average linkage`() = runTest {
        // A-B 0.20, B-C 0.40, A-C 0.90: single linkage reaches C through B at 0.40; the mean to C is 0.65.
        val distances = mapOf((0 to 1) to 0.20f, (1 to 2) to 0.40f, (0 to 2) to 0.90f)

        val clusters = clustersOf(cluster(3, triangleOf(3) { first, second -> distances.getValue(first to second) }))

        assertEquals(setOf(setOf(0, 1), setOf(2)), clusters)
    }

    @Test
    fun `a cluster whose mean distance is within the cut joins although its farthest member is beyond it`() = runTest {
        // A-B 0.20, A-C 0.28, B-C 0.68: the mean from {A, B} to C is 0.48; complete linkage would see 0.68.
        val distances = mapOf((0 to 1) to 0.20f, (0 to 2) to 0.28f, (1 to 2) to 0.68f)

        val clusters = clustersOf(cluster(3, triangleOf(3) { first, second -> distances.getValue(first to second) }))

        assertEquals(setOf(setOf(0, 1, 2)), clusters)
    }

    @Test
    fun `a merge weighs each member of a cluster equally`() = runTest {
        // A, B, C join at 0.05 and 0.065; D is 0.85 from A and B and 0.10 from C. The mean over the three cross pairs is
        // 0.60, above the cut; the mean of the two means, (0.85 + 0.10) / 2 = 0.475, would be within it.
        val distances = mapOf(
            (0 to 1) to 0.05f, (0 to 2) to 0.06f, (1 to 2) to 0.07f,
            (0 to 3) to 0.85f, (1 to 3) to 0.85f, (2 to 3) to 0.10f
        )

        val clusters = clustersOf(cluster(4, triangleOf(4) { first, second -> distances.getValue(first to second) }))

        assertEquals(setOf(setOf(0, 1, 2), setOf(3)), clusters)
    }

    @Test
    fun `a tie between two merges goes to the pair with the lower ranks`() = runTest {
        // A-B and B-C are both 0.40 and A-C is 0.70: merging A-B first leaves 0.55 to C; merging B-C first would leave 0.55 to A.
        val distances = mapOf((0 to 1) to 0.40f, (1 to 2) to 0.40f, (0 to 2) to 0.70f)

        val clusters = clustersOf(cluster(3, triangleOf(3) { first, second -> distances.getValue(first to second) }))

        assertEquals(setOf(setOf(0, 1), setOf(2)), clusters)
    }

    @Test
    fun `items at no distance below the cut each stay a cluster of their own`() = runTest {
        val clusters = clustersOf(cluster(5, triangleOf(5) { _, _ -> 0.9f }))

        assertEquals(5, clusters.size)
        assertTrue(clusters.all { it.size == 1 })
    }

    @Test
    fun `the core refuses a triangle of the wrong length`() = runTest {
        val failure = runCatching { averageLinkageClusters(4, FloatArray(5), FACE_GROUPING_CUT) { true } }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    // ---- the front: embeddings to distances to groups ----

    @Test
    fun `the triangle holds float32 cosine distances in id order`() = runTest {
        val faces = listOf(input(30, 0f, 1f), input(10, 1f, 0f), input(20, -1f, 0f))

        val triangle = faceDistanceTriangle(normaliseFaces(faces)) { true }!!

        // Ids 10, 20, 30: (10, 20) are opposite, (10, 30) are orthogonal, (20, 30) are orthogonal.
        assertEquals(listOf(2f, 1f, 1f), triangle.toList())
    }

    @Test
    fun `a face is its own group when no other is within the cut`() = runTest {
        val result = groupFaces(listOf(input(5, 1f, 0f, 0f), input(6, 0f, 1f, 0f), input(7, 0f, 0f, 1f)))!!

        assertEquals(listOf(FaceGroup(5, listOf(5)), FaceGroup(6, listOf(6)), FaceGroup(7, listOf(7))), result.groups)
    }

    @Test
    fun `empty input gives no group and one face gives a group of itself`() = runTest {
        assertEquals(emptyList<FaceGroup>(), groupFaces(emptyList())!!.groups)
        assertEquals(listOf(FaceGroup(42, listOf(42))), groupFaces(listOf(input(42, 0.3f, 0.4f)))!!.groups)
    }

    @Test
    fun `the group id is the medoid and not the lowest id`() = runTest {
        // Three faces on one arc, 0, 10 and 20 degrees; the middle one is nearest the other two.
        val faces = listOf(arcFace(5, 0.0), arcFace(9, 10.0), arcFace(2, 20.0))

        val result = groupFaces(faces)!!

        assertEquals(listOf(FaceGroup(9, listOf(2, 5, 9))), result.groups)
    }

    @Test
    fun `a medoid tie goes to the lowest id`() = runTest {
        val pair = groupFaces(listOf(input(7, 1f, 0f), input(3, 1f, 0f)))!!
        val four = groupFaces(listOf(input(30, 1f, 1f), input(10, 1f, 1f), input(40, 1f, 1f), input(20, 1f, 1f)))!!

        assertEquals(listOf(FaceGroup(3, listOf(3, 7))), pair.groups)
        assertEquals(listOf(FaceGroup(10, listOf(10, 20, 30, 40))), four.groups)
    }

    @Test
    fun `equal embeddings with different ids are one group whatever the order`() = runTest {
        val ids = listOf(8L, 3L, 15L, 4L, 23L)

        val orders = listOf(ids, ids.reversed(), ids.sorted(), ids.sortedDescending())
        val results = orders.map { order -> groupFaces(order.map { input(it, 0.2f, 0.9f, -0.4f) })!!.groups }

        assertTrue(results.all { it == listOf(FaceGroup(3, ids.sorted())) })
    }

    @Test
    fun `a zero embedding is a group of its own`() = runTest {
        val faces = listOf(input(1, 0f, 0f, 0f), input(2, 1f, 0f, 0f), input(3, 1f, 0f, 0f), input(4, 0f, 0f, 0f))

        val triangle = faceDistanceTriangle(normaliseFaces(faces)) { true }!!
        val result = groupFaces(faces)!!

        assertTrue("a zero embedding is at distance 1.0 from every face, another zero one included", triangle.toList().count { it == 1f } == 5)
        assertEquals(setOf(setOf(1L), setOf(2L, 3L), setOf(4L)), result.asSets())
        assertTrue(triangle.all { it.isFinite() })
    }

    @Test
    fun `unequal embedding lengths fail with a fixed message that holds no value`() = runTest {
        val failure = runCatching { groupFaces(listOf(input(11, 1f, 0f, 0f), input(12, 1f, 0f))) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(FACE_GROUPING_UNEQUAL_LENGTH_MESSAGE, failure!!.message)
        assertFalse(failure.message!!.any { it.isDigit() })
    }

    @Test
    fun `a repeated id fails with a fixed message that holds no value`() = runTest {
        val failure = runCatching { groupFaces(listOf(input(77, 1f, 0f), input(77, 0f, 1f))) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(FACE_GROUPING_DUPLICATE_ID_MESSAGE, failure!!.message)
        assertFalse(failure.message!!.any { it.isDigit() })
    }

    // ---- independence of input order ----

    @Test
    fun `a tie between merges is broken by id whatever the input order`() = runTest {
        // A (0.6, 0.8), B (1, 0) and C (0.6, -0.8): A-B and B-C are exactly 0.40, A-C is 1.28. By id A < B < C.
        val a = input(10, 0.6f, 0.8f)
        val b = input(20, 1f, 0f)
        val c = input(30, 0.6f, -0.8f)
        val faces = listOf(a, b, c)

        val results = permutations(faces).map { groupFaces(it)!!.asSets() }

        assertEquals(6, results.size)
        assertTrue(results.all { it == setOf(setOf(10L, 20L), setOf(30L)) })
    }

    @Test
    fun `a hundred shuffles of a random set give one output`() = runTest {
        val random = java.util.Random(7)
        val centres = List(5) { randomUnit(random, 6) }
        val ids = LongArray(30) { 1_000_003L * (it + 1) + random.nextInt(1000) }
        val faces = ids.mapIndexed { index, id ->
            val centre = centres[index % centres.size]
            FaceGroupingInput(id, FaceEmbedding(FloatArray(6) { centre[it] + 0.25f * random.nextGaussian().toFloat() }))
        }

        val expected = groupFaces(faces)!!
        assertTrue("the fixture should have several groups and not one per face", expected.groups.size in 2..20)

        val shuffler = java.util.Random(11)
        repeat(100) {
            assertEquals(expected, groupFaces(faces.shuffled(shuffler))!!)
        }
    }

    // ---- components first ----

    @Test
    fun `the split gives the same groups and representatives as one triangle over every face`() = runTest {
        var withSeveralComponents = 0
        var withASingleton = 0
        var pairsAtTheCut = 0
        for (seed in 1..RANDOM_SETS) {
            val faces = randomFaces(seed.toLong())
            val normalised = normaliseFaces(faces)
            val components = connectedComponents(normalised) { true }!!
            if (components.size >= 2) withSeveralComponents += 1
            if (components.any { it.size == 1 }) withASingleton += 1
            if (seed % 2 == 1) pairsAtTheCut += faceDistanceTriangle(normalised) { true }!!.count { it == 0.5f }

            assertEquals("seed $seed", groupFacesWithoutSplit(faces)!!, groupFaces(faces)!!)
        }
        assertTrue("only $withSeveralComponents sets had several components", withSeveralComponents >= 150)
        assertTrue("only $withASingleton sets had a singleton component", withASingleton >= 20)
        assertTrue("no pair sat at exactly 0.50 in float32", pairsAtTheCut > 0)
    }

    @Test
    fun `a pair at exactly the cut is one component and a pair just above it is two`() = runTest {
        val atCut = normaliseFaces(listOf(input(1, 1f, 0f, 0f, 0f), input(2, 0.5f, 0.5f, 0.5f, 0.5f)))
        val above = normaliseFaces(listOf(input(1, 1f, 0f, 0f, 0f), input(2, 0.49f, 0.5f, 0.5f, 0.5f)))

        assertEquals(listOf(listOf(0, 1)), connectedComponents(atCut) { true }!!.map { it.toList() })
        assertEquals(listOf(listOf(0), listOf(1)), connectedComponents(above) { true }!!.map { it.toList() })
    }

    @Test
    fun `two components whose closest cross pair is just above the cut stay two groups`() = runTest {
        val faces = listOf(
            input(1, 1f, 0f, 0f, 0f), input(2, 1f, 0f, 0f, 0f),
            input(3, 0.49f, 0.5f, 0.5f, 0.5f), input(4, 0.49f, 0.5f, 0.5f, 0.5f)
        )

        val triangle = faceDistanceTriangle(normaliseFaces(faces)) { true }!!

        assertTrue("the fixture is meant to sit just above 0.50", triangle[1] > 0.5f && triangle[1] < 0.52f)
        assertEquals(2, connectedComponents(normaliseFaces(faces)) { true }!!.size)
        assertEquals(setOf(setOf(1L, 2L), setOf(3L, 4L)), groupFaces(faces)!!.asSets())
        assertEquals(groupFacesWithoutSplit(faces), groupFaces(faces))
    }

    @Test
    fun `an edge is judged on the float32 distance, so a pair whose double distance is above the cut is still joined`() = runTest {
        val faces = pairAboveTheCutInDoubleButAtItInFloat()
        val normalised = normaliseFaces(faces)

        assertTrue(normalised.distance(0, 1) > FACE_GROUPING_CUT)
        assertEquals(0.5f, normalised.distance(0, 1).toFloat(), 0f)
        assertEquals(listOf(listOf(0, 1)), connectedComponents(normalised) { true }!!.map { it.toList() })
        assertEquals(1, groupFaces(faces)!!.groups.size)
        assertEquals(groupFacesWithoutSplit(faces), groupFaces(faces))
    }

    @Test
    fun `faces with no neighbour are components of one and a group of their own`() = runTest {
        val faces = listOf(input(5, 1f, 0f, 0f), input(6, 0f, 1f, 0f), input(7, 0f, 0f, 1f))

        val components = connectedComponents(normaliseFaces(faces)) { true }!!
        val result = groupFaces(faces)!!

        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), components.map { it.toList() })
        assertEquals(listOf(FaceGroup(5, listOf(5)), FaceGroup(6, listOf(6)), FaceGroup(7, listOf(7))), result.groups)
    }

    @Test
    fun `equal embeddings are one big component`() = runTest {
        val faces = (1L..50L).map { input(it * 3, 0.2f, 0.9f, -0.4f) }

        val components = connectedComponents(normaliseFaces(faces)) { true }!!

        assertEquals(listOf(50), components.map { it.size })
        assertEquals(listOf(FaceGroup(3, faces.map { it.faceId })), groupFaces(faces)!!.groups)
    }

    @Test
    fun `many small components each keep their faces in ascending id order`() = runTest {
        // 40 identities on axes of their own, so 40 components of three; ids interleave them.
        val faces = (0 until 40).flatMap { identity ->
            (0 until 3).map { member ->
                FaceGroupingInput(
                    1000L - (member * 40 + identity), FaceEmbedding(FloatArray(40) { if (it == identity) 1f else 0f })
                )
            }
        }

        val components = connectedComponents(normaliseFaces(faces)) { true }!!
        val result = groupFaces(faces)!!

        assertEquals(40, components.size)
        assertTrue(components.all { it.size == 3 && it.toList() == it.sorted() })
        assertEquals(40, result.groups.size)
        assertTrue(result.groups.all { it.memberFaceIds.size == 3 && it.memberFaceIds == it.memberFaceIds.sorted() })
        assertEquals(groupFacesWithoutSplit(faces), result)
    }

    @Test
    fun `the face cap applies to a component and not to the whole input`() = runTest {
        // Five faces in two components, of three and two; the cap is three.
        val faces = listOf(
            input(1, 1f, 0f, 0f), input(2, 1f, 0f, 0f), input(3, 1f, 0f, 0f),
            input(4, 0f, 1f, 0f), input(5, 0f, 1f, 0f)
        )
        val tooBig = faces + input(6, 1f, 0f, 0f)

        val fits = groupNormalised(faces, true, 3) { true }
        val failure = runCatching { groupNormalised(tooBig, true, 3) { true } }.exceptionOrNull()

        assertEquals(setOf(setOf(1L, 2L, 3L), setOf(4L, 5L)), fits!!.asSets())
        assertTrue(failure is IllegalArgumentException)
        assertEquals(FACE_GROUPING_TOO_MANY_MESSAGE, failure!!.message)
        assertFalse(failure.message!!.any { it.isDigit() })
    }

    // ---- stopping ----

    @Test
    fun `cancellation propagates out of the grouping`() = runTest {
        var finished = false
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            groupFaces(sixFaces()) {
                job.cancel()
                true
            }
            finished = true
        }

        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(finished)
    }

    @Test
    fun `the setting reading off before anything stops it with no result`() = runTest {
        var calls = 0

        val result = groupFaces(sixFaces()) {
            calls += 1
            false
        }

        assertNull(result)
        assertEquals(1, calls)
    }

    @Test
    fun `the setting reading off during the distances stops it with no result`() = runTest {
        var calls = 0

        val result = groupFaces(sixFaces()) { ++calls < 3 }

        assertNull(result)
        assertEquals(3, calls)
    }

    @Test
    fun `the setting reading off between merges stops the clustering with no result`() = runTest {
        // 300 equal embeddings are one component with 299 merges. Call 1 is the start, calls 2 to 300 the rows of the
        // component pass, call 301 the check before the component, calls 302 to 600 the rows of its triangle; call 601 is
        // the check after the 256th merge. Only that one call reads off, so a clustering that never looks would finish
        // with a result.
        val faces = (1L..300L).map { id -> input(id, 1f, 0f, 0f, 0f) }
        var calls = 0

        val result = groupFaces(faces) { ++calls != 601 }

        assertNull(result)
        assertEquals(601, calls)
    }

    @Test
    fun `the setting reading off before a component stops it with no result`() = runTest {
        // sixFaces is three pairs: call 1 is the start, calls 2 to 6 the rows of the component pass, call 7 the check
        // before the first component.
        var calls = 0

        val result = groupFaces(sixFaces()) { ++calls != 7 }

        assertNull(result)
        assertEquals(7, calls)
    }

    @Test
    fun `the setting reading off during a component's triangle stops it with no result`() = runTest {
        // Call 8 is the row of the first component's triangle.
        var calls = 0

        val result = groupFaces(sixFaces()) { ++calls != 8 }

        assertNull(result)
        assertEquals(8, calls)
    }

    @Test
    fun `the setting reading off while choosing a representative stops it with no result`() = runTest {
        // Two equal pairs: start 1, rows 2 to 4, check before the first component 5, its row 6, its representative's
        // row 7. Reading off at 7 is the per-group check.
        val faces = listOf(input(1, 1f, 0f), input(2, 1f, 0f), input(3, 0f, 1f), input(4, 0f, 1f))
        var calls = 0

        val result = groupFaces(faces) { ++calls != 7 }

        assertNull(result)
        assertEquals(7, calls)
    }

    @Test
    fun `the public grouping reads the setting at the start, every row of the component pass, each component and its rows`() = runTest {
        // sixFaces is three pairs: 1 at the start, 5 rows of the component pass, then for each pair 1 check before it,
        // 1 row of its triangle and 1 row of its representative. One triangle over all six would make 10.
        var calls = 0

        val result = groupFaces(sixFaces()) {
            calls += 1
            true
        }

        assertNotNull(result)
        assertEquals(1 + 5 + 3 * 3, calls)
    }

    @Test
    fun `the setting reading on throughout lets every phase run`() = runTest {
        val result = groupFaces(sixFaces()) { true }

        assertNotNull(result)
    }

    // ---- redaction ----

    @Test
    fun `no type prints an id an embedding or a group`() = runTest {
        val input = FaceGroupingInput(987_654_321L, FaceEmbedding(floatArrayOf(0.123456f, 0.654321f)))
        val group = FaceGroup(987_654_321L, listOf(987_654_321L, 123_456_789L))
        val result = FaceGroupingResult(listOf(group))

        val printed = listOf(input.toString(), group.toString(), result.toString())

        assertEquals(listOf("FaceGroupingInput(redacted)", "FaceGroup(redacted)", "FaceGroupingResult(redacted)"), printed)
        assertTrue(printed.none { text -> text.any { it.isDigit() } })
    }

    @Test
    fun `the version names the algorithm as built`() {
        assertEquals(1, FACE_GROUPING_VERSION)
        assertEquals(0.50, FACE_GROUPING_CUT, 0.0)
    }

    // ---- helpers ----

    private fun input(id: Long, vararg values: Float) = FaceGroupingInput(id, FaceEmbedding(values))

    private fun input(id: Int, vararg values: Float) = input(id.toLong(), *values)

    private fun arcFace(id: Long, degrees: Double): FaceGroupingInput {
        val radians = Math.toRadians(degrees)
        return input(id, cos(radians).toFloat(), sin(radians).toFloat())
    }

    private fun sixFaces() = listOf(
        input(1, 1f, 0f, 0f), input(2, 1f, 0.1f, 0f), input(3, 0f, 1f, 0f),
        input(4, 0f, 1f, 0.1f), input(5, 0f, 0f, 1f), input(6, 0.1f, 0f, 1f)
    )

    private fun randomUnit(random: java.util.Random, dimension: Int): FloatArray {
        val raw = FloatArray(dimension) { random.nextGaussian().toFloat() }
        val norm = sqrt(raw.sumOf { it.toDouble() * it }).toFloat()
        return FloatArray(dimension) { raw[it] / norm }
    }

    // Faces of a seeded set of 2 to 200: odd seeds put them on arcs at multiples of 15 degrees in separate planes (many
    // equal embeddings, pairs at 0.50 or a hair either side, distinct planes at distance 1), even seeds in noisy clusters
    // of a random width, so that the graph at the cut runs from many components to one.
    private fun randomFaces(seed: Long): List<FaceGroupingInput> {
        val random = java.util.Random(seed)
        val count = 2 + random.nextInt(199)
        val ids = LinkedHashSet<Long>()
        while (ids.size < count) ids.add(1L + random.nextInt(100_000))
        val embeddings = if (seed % 2 == 1L) arcEmbeddings(random, count) else clusterEmbeddings(random, count)
        return ids.toList().mapIndexed { index, id -> FaceGroupingInput(id, embeddings[index]) }
    }

    private fun arcEmbeddings(random: java.util.Random, count: Int): List<FaceEmbedding> = List(count) {
        val plane = random.nextInt(4)
        val radians = Math.toRadians(15.0 * random.nextInt(9))
        val values = FloatArray(8)
        values[2 * plane] = cos(radians).toFloat()
        values[2 * plane + 1] = sin(radians).toFloat()
        FaceEmbedding(values)
    }

    private fun clusterEmbeddings(random: java.util.Random, count: Int): List<FaceEmbedding> {
        val dimension = 8
        val centres = List(1 + random.nextInt(6)) { randomUnit(random, dimension) }
        val noise = (0.15f + random.nextFloat() * 1.1f) / sqrt(dimension.toDouble()).toFloat()
        val embeddings = ArrayList<FaceEmbedding>()
        repeat(count) {
            if (embeddings.isNotEmpty() && random.nextInt(8) == 0) {
                embeddings.add(embeddings[random.nextInt(embeddings.size)])
            } else {
                val centre = centres[random.nextInt(centres.size)]
                embeddings.add(FaceEmbedding(FloatArray(dimension) { centre[it] + noise * random.nextGaussian().toFloat() }))
            }
        }
        return embeddings
    }

    // A pair, found by a fixed scan over neighbouring float values, whose cosine distance in double is above 0.50 and
    // whose float32 value is 0.50 exactly: the case in which comparing the double and comparing the float disagree.
    private fun pairAboveTheCutInDoubleButAtItInFloat(): List<FaceGroupingInput> {
        for (xSteps in -30..30) {
            for (ySteps in -30..30) {
                var x = 0.5f
                var y = 0.8660254f
                repeat(abs(xSteps)) { x = if (xSteps < 0) Math.nextDown(x) else Math.nextUp(x) }
                repeat(abs(ySteps)) { y = if (ySteps < 0) Math.nextDown(y) else Math.nextUp(y) }
                val faces = listOf(input(1, 1f, 0f), input(2, x, y))
                val distance = normaliseFaces(faces).distance(0, 1)
                if (distance > FACE_GROUPING_CUT && distance.toFloat() == 0.5f) return faces
            }
        }
        throw AssertionError("no pair with a double distance above 0.50 and a float32 distance of 0.50 was found")
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        return items.indices.flatMap { index ->
            permutations(items.filterIndexed { other, _ -> other != index }).map { listOf(items[index]) + it }
        }
    }

    private fun FaceGroupingResult.asSets(): Set<Set<Long>> = groups.map { it.memberFaceIds.toSet() }.toSet()

    private fun triangleOf(size: Int, distance: (Int, Int) -> Float): FloatArray {
        val triangle = FloatArray(size * (size - 1) / 2)
        var offset = 0
        for (first in 0 until size - 1) {
            for (second in first + 1 until size) {
                triangle[offset] = distance(first, second)
                offset += 1
            }
        }
        return triangle
    }

    private suspend fun cluster(size: Int, triangle: FloatArray): IntArray =
        averageLinkageClusters(size, triangle.copyOf(), FACE_GROUPING_CUT) { true }!!

    private fun clustersOf(roots: IntArray): Set<Set<Int>> =
        roots.indices.groupBy { roots[it] }.values.map { it.toSet() }.toSet()

    // Average linkage as defined, not as computed: every step takes the mean over all cross pairs of the original
    // distances, merges the lowest mean (a tie to the lower pair of clusters, ordered by their lowest member), and stops
    // above the cut. Also returns how close its decisions came to the cut and to the runner-up.
    private fun referenceClusters(distances: Array<FloatArray>, cut: Double): Pair<Set<Set<Int>>, Double> {
        val clusters = MutableList(distances.size) { mutableListOf(it) }
        var margin = Double.MAX_VALUE
        while (clusters.size > 1) {
            var bestFirst = -1
            var bestOther = -1
            var best = Double.MAX_VALUE
            var second = Double.MAX_VALUE
            for (first in clusters.indices) {
                for (other in first + 1 until clusters.size) {
                    val mean = meanDistance(distances, clusters[first], clusters[other])
                    if (mean < best) {
                        second = best
                        best = mean
                        bestFirst = first
                        bestOther = other
                    } else if (mean < second) {
                        second = mean
                    }
                }
            }
            margin = minOf(margin, abs(best - cut))
            if (best > cut) break
            if (second != Double.MAX_VALUE) margin = minOf(margin, second - best)
            clusters[bestFirst].addAll(clusters[bestOther])
            clusters.removeAt(bestOther)
        }
        return clusters.map { it.toSet() }.toSet() to margin
    }

    private fun meanDistance(distances: Array<FloatArray>, first: List<Int>, second: List<Int>): Double {
        var sum = 0.0
        for (a in first) for (b in second) sum += distances[a][b].toDouble()
        return sum / (first.size * second.size)
    }

    private companion object {
        const val SEEDS = 120
        const val AMBIGUOUS_MARGIN = 1e-6
        const val RANDOM_SETS = 250
    }
}
