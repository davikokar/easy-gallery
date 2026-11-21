package com.davide.seddio.easygallery.data

import kotlin.math.sqrt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// Grouping of faces (albums.md §8 decisions 45 and 50): exact average linkage (UPGMA) on cosine distance, cut at
// FACE_GROUPING_CUT inclusive, run inside each connected component of the pairs at or below the cut. Pure and
// Android-free; FaceGroupingPass is its caller. ADR-0024 §6: no type here prints an id, a group or an embedding, and no
// failure message names a value.

/**
 * Names the algorithm: the metric, the cut and its inclusiveness, the float32 triangle, the merge tie-break, and the
 * representative, the plain medoid with ties to the lower face id. It bumps only when the grouping's result for the same
 * stored faces would change, which regroups with no inference (ADR-0018 Amendment 5); the component split computes the same
 * groups and representatives another way, so it does not bump.
 */
const val FACE_GROUPING_VERSION = 1

/** Two clusters merge while the mean distance over all their cross pairs is at most this. */
const val FACE_GROUPING_CUT = 0.50

const val FACE_GROUPING_UNEQUAL_LENGTH_MESSAGE = "Face embeddings must all have the same length"
const val FACE_GROUPING_DUPLICATE_ID_MESSAGE = "Face ids must be unique"
const val FACE_GROUPING_TOO_MANY_MESSAGE = "Too many faces to group"

// A component's condensed triangle holds n(n-1)/2 floats and an array holds at most Int.MAX_VALUE of them.
private const val MAX_GROUPING_FACES = 65_536

// Merges between two checks of cancellation and of the setting.
private const val CLUSTERING_CHECK_INTERVAL = 256

// Summed distances closer than this are a tie, which goes to the lower face id: the sums are accumulated in a
// different order per member, so exact equality would miss a tie that is only rounding.
private const val MEDOID_TIE_TOLERANCE = 1e-9

class FaceGroupingInput(val faceId: Long, val embedding: FaceEmbedding) {
    override fun toString(): String = "FaceGroupingInput(redacted)"
}

/** [groupId] is the representative's (the medoid's) face id; [memberFaceIds] ascend and include it. */
data class FaceGroup(val groupId: Long, val memberFaceIds: List<Long>) {
    override fun toString(): String = "FaceGroup(redacted)"
}

/** Every input face is in exactly one group; the groups ascend by [FaceGroup.groupId]. */
data class FaceGroupingResult(val groups: List<FaceGroup>) {
    override fun toString(): String = "FaceGroupingResult(redacted)"
}

/**
 * Groups [faces]. The result is a function of the set of (id, embedding): the input is sorted by id before
 * anything else, so the order the store returns cannot change it. The faces are split into the connected components of
 * the pairs at or below the cut, which needs every distance once and keeps none, and each component of two or more is
 * clustered on a triangle of its own, freed before the next. Null when [isSettingEnabled] reads false at one of the
 * checks (before anything, at every row of the component pass, before each component, at every row of its triangle,
 * every few hundred merges, at every row of each group of two or more when choosing representatives): the caller
 * writes nothing. Cancellation propagates. Throws [IllegalArgumentException], with a fixed message, for unequal embedding lengths, a repeated id, or a
 * component of more faces than a triangle can hold.
 */
suspend fun groupFaces(
    faces: List<FaceGroupingInput>,
    isSettingEnabled: () -> Boolean = { true }
): FaceGroupingResult? = groupNormalised(faces, true, MAX_GROUPING_FACES, isSettingEnabled)

/** The same groups through one triangle over every face: the reference the split is held to. */
internal suspend fun groupFacesWithoutSplit(
    faces: List<FaceGroupingInput>,
    isSettingEnabled: () -> Boolean = { true }
): FaceGroupingResult? = groupNormalised(faces, false, MAX_GROUPING_FACES, isSettingEnabled)

internal suspend fun groupNormalised(
    faces: List<FaceGroupingInput>,
    split: Boolean,
    maxClusterFaces: Int,
    isSettingEnabled: () -> Boolean
): FaceGroupingResult? {
    if (!mayContinue(isSettingEnabled)) return null
    val normalised = normaliseFaces(faces)
    val components = if (split) {
        connectedComponents(normalised, isSettingEnabled) ?: return null
    } else {
        listOf(IntArray(normalised.size) { it })
    }

    val groups = ArrayList<FaceGroup>()
    for (ranks in components) {
        if (ranks.size == 1) {
            groups.add(FaceGroup(normalised.faceIds[ranks[0]], listOf(normalised.faceIds[ranks[0]])))
            continue
        }
        if (!mayContinue(isSettingEnabled)) return null
        groups.addAll(groupComponent(normalised, ranks, maxClusterFaces, isSettingEnabled) ?: return null)
    }
    groups.sortBy { it.groupId }
    return FaceGroupingResult(groups)
}

// A function of its own so the component's triangle is unreachable once its groups are known.
private suspend fun groupComponent(
    faces: NormalisedFaces,
    ranks: IntArray,
    maxClusterFaces: Int,
    isSettingEnabled: () -> Boolean
): List<FaceGroup>? {
    require(ranks.size <= maxClusterFaces) { FACE_GROUPING_TOO_MANY_MESSAGE }
    val triangle = faceDistanceTriangle(faces, ranks, isSettingEnabled) ?: return null
    val roots = averageLinkageClusters(ranks.size, triangle, FACE_GROUPING_CUT, isSettingEnabled) ?: return null
    return faceGroupsOf(faces, ranks, roots, isSettingEnabled)
}

/**
 * The connected components of the graph whose edges are the pairs whose float32 distance is at most the cut, compared as
 * the clustering compares it. Each is its faces' ranks, ascending, and they ascend by lowest rank. One pass over every
 * pair, with a union-find of one int per face and no stored distance. Null when the setting reads off.
 */
internal suspend fun connectedComponents(faces: NormalisedFaces, isSettingEnabled: () -> Boolean): List<IntArray>? {
    val size = faces.size
    val parent = IntArray(size) { it }
    for (row in 0 until size - 1) {
        if (!mayContinue(isSettingEnabled)) return null
        for (column in row + 1 until size) {
            if (faces.distance(row, column).toFloat().toDouble() <= FACE_GROUPING_CUT) {
                val rowRoot = rootOf(parent, row)
                val columnRoot = rootOf(parent, column)
                if (rowRoot != columnRoot) parent[maxOf(rowRoot, columnRoot)] = minOf(rowRoot, columnRoot)
            }
        }
    }

    val componentOf = IntArray(size)
    val counts = ArrayList<Int>()
    val componentOfRoot = IntArray(size) { -1 }
    for (rank in 0 until size) {
        val root = rootOf(parent, rank)
        if (componentOfRoot[root] < 0) {
            componentOfRoot[root] = counts.size
            counts.add(0)
        }
        componentOf[rank] = componentOfRoot[root]
        counts[componentOf[rank]] = counts[componentOf[rank]] + 1
    }
    val components = List(counts.size) { IntArray(counts[it]) }
    val filled = IntArray(counts.size)
    for (rank in 0 until size) {
        val component = componentOf[rank]
        components[component][filled[component]] = rank
        filled[component] += 1
    }
    return components
}

/** Unit vectors in id order, flat. A zero-norm embedding has no direction: it is at distance 1.0 from every face. */
internal class NormalisedFaces(
    val faceIds: LongArray,
    private val dimension: Int,
    private val unit: DoubleArray,
    private val zeroNorm: BooleanArray
) {
    val size: Int get() = faceIds.size

    /** Cosine distance, computed in double; never below 0, so identical embeddings are at 0 whatever the rounding. */
    fun distance(a: Int, b: Int): Double {
        if (zeroNorm[a] || zeroNorm[b]) return 1.0
        val aOffset = a * dimension
        val bOffset = b * dimension
        var dot = 0.0
        for (k in 0 until dimension) dot += unit[aOffset + k] * unit[bOffset + k]
        return (1.0 - dot).coerceAtLeast(0.0)
    }
}

internal fun normaliseFaces(faces: List<FaceGroupingInput>): NormalisedFaces {
    val sorted = faces.sortedBy { it.faceId }
    for (index in 1 until sorted.size) {
        require(sorted[index].faceId != sorted[index - 1].faceId) { FACE_GROUPING_DUPLICATE_ID_MESSAGE }
    }
    val dimension = sorted.firstOrNull()?.embedding?.size ?: 0
    require(sorted.all { it.embedding.size == dimension }) { FACE_GROUPING_UNEQUAL_LENGTH_MESSAGE }

    val unit = DoubleArray(sorted.size * dimension)
    val zeroNorm = BooleanArray(sorted.size)
    for ((index, face) in sorted.withIndex()) {
        val values = face.embedding.toFloatArray()
        var sumOfSquares = 0.0
        for (value in values) sumOfSquares += value.toDouble() * value.toDouble()
        val norm = sqrt(sumOfSquares)
        if (norm == 0.0) {
            zeroNorm[index] = true
            continue
        }
        val offset = index * dimension
        for (k in 0 until dimension) unit[offset + k] = values[k] / norm
    }
    return NormalisedFaces(
        LongArray(sorted.size) { sorted[it].faceId },
        dimension,
        unit,
        zeroNorm
    )
}

private fun triangleSize(size: Int): Long = size.toLong() * (size - 1) / 2

// The first float of row i in the condensed triangle of pairs (i, j), i < j, read row by row.
private fun rowStart(size: Int, row: Int): Int = (row.toLong() * (2L * size - row - 1) / 2).toInt()

private fun pairIndex(size: Int, first: Int, second: Int): Int {
    val low = minOf(first, second)
    val high = maxOf(first, second)
    return rowStart(size, low) + (high - low - 1)
}

/** Condensed float32 triangle of every one of [faces]' distances, or null when the setting reads off. */
internal suspend fun faceDistanceTriangle(faces: NormalisedFaces, isSettingEnabled: () -> Boolean): FloatArray? =
    faceDistanceTriangle(faces, IntArray(faces.size) { it }, isSettingEnabled)

/** Condensed float32 triangle of the distances among the faces at [ranks], ascending, or null when the setting reads off. */
internal suspend fun faceDistanceTriangle(
    faces: NormalisedFaces,
    ranks: IntArray,
    isSettingEnabled: () -> Boolean
): FloatArray? {
    val size = ranks.size
    val triangle = FloatArray(triangleSize(size).toInt())
    var offset = 0
    for (row in 0 until size - 1) {
        if (!mayContinue(isSettingEnabled)) return null
        for (column in row + 1 until size) {
            triangle[offset] = faces.distance(ranks[row], ranks[column]).toFloat()
            offset += 1
        }
    }
    return triangle
}

/**
 * Exact average linkage over a condensed [triangle] of [size] items, overwritten as it goes. Each step merges the pair of
 * clusters with the smallest mean cross distance, and stops when that is above [cut]; a tie goes to the pair with the
 * lower (lowest member, lowest member) in rank order, so the result depends on the distances and the ranks alone. Returns,
 * for each item, the lowest rank in its cluster. Null when the setting reads off.
 */
internal suspend fun averageLinkageClusters(
    size: Int,
    triangle: FloatArray,
    cut: Double,
    isSettingEnabled: () -> Boolean
): IntArray? {
    require(triangle.size.toLong() == triangleSize(size)) { "The distance triangle does not match the number of faces" }
    val parent = IntArray(size) { it }
    if (size < 2) return parent

    val alive = BooleanArray(size) { true }
    val members = IntArray(size) { 1 }
    // For each live cluster, the nearest live cluster of higher rank, and how far.
    val nearest = IntArray(size) { -1 }
    val nearestDistance = FloatArray(size)
    for (row in 0 until size - 1) scanRow(row, size, triangle, alive, nearest, nearestDistance)

    var merges = 0
    while (true) {
        var low = -1
        var best = 0f
        for (row in 0 until size - 1) {
            if (!alive[row] || nearest[row] < 0) continue
            if (low < 0 || nearestDistance[row] < best) {
                low = row
                best = nearestDistance[row]
            }
        }
        if (low < 0 || best.toDouble() > cut) break
        val high = nearest[low]

        val lowMembers = members[low]
        val highMembers = members[high]
        val total = (lowMembers + highMembers).toDouble()
        for (other in 0 until size) {
            if (!alive[other] || other == low || other == high) continue
            val withLow = pairIndex(size, other, low)
            val withHigh = pairIndex(size, other, high)
            triangle[withLow] = ((lowMembers * triangle[withLow].toDouble() + highMembers * triangle[withHigh].toDouble()) / total).toFloat()
        }
        alive[high] = false
        members[low] = lowMembers + highMembers
        parent[high] = low

        for (row in 0 until low) {
            if (!alive[row]) continue
            if (nearest[row] == low || nearest[row] == high) {
                scanRow(row, size, triangle, alive, nearest, nearestDistance)
            } else {
                val distance = triangle[pairIndex(size, row, low)]
                if (distance < nearestDistance[row] || (distance == nearestDistance[row] && low < nearest[row])) {
                    nearest[row] = low
                    nearestDistance[row] = distance
                }
            }
        }
        for (row in low + 1 until high) {
            if (alive[row] && nearest[row] == high) scanRow(row, size, triangle, alive, nearest, nearestDistance)
        }
        scanRow(low, size, triangle, alive, nearest, nearestDistance)

        merges += 1
        if (merges % CLUSTERING_CHECK_INTERVAL == 0 && !mayContinue(isSettingEnabled)) return null
    }

    return IntArray(size) { rootOf(parent, it) }
}

private fun scanRow(
    row: Int,
    size: Int,
    triangle: FloatArray,
    alive: BooleanArray,
    nearest: IntArray,
    nearestDistance: FloatArray
) {
    val base = rowStart(size, row) - row - 1
    var best = -1
    var bestDistance = 0f
    for (column in row + 1 until size) {
        if (!alive[column]) continue
        val distance = triangle[base + column]
        if (best < 0 || distance < bestDistance) {
            best = column
            bestDistance = distance
        }
    }
    nearest[row] = best
    nearestDistance[row] = bestDistance
}

private fun rootOf(parent: IntArray, item: Int): Int {
    var root = item
    while (parent[root] != root) root = parent[root]
    var step = item
    while (step != root) {
        val next = parent[step]
        parent[step] = root
        step = next
    }
    return root
}

/**
 * The groups of [roots] — for each of the component's faces at [ranks] (ascending), the lowest index in its cluster —
 * with their representatives, ascending by group id; null when the setting reads off.
 */
internal suspend fun faceGroupsOf(
    faces: NormalisedFaces,
    ranks: IntArray,
    roots: IntArray,
    isSettingEnabled: () -> Boolean
): List<FaceGroup>? {
    val ranksByRoot = LinkedHashMap<Int, MutableList<Int>>()
    for (index in ranks.indices) ranksByRoot.getOrPut(roots[index]) { ArrayList() }.add(ranks[index])

    val groups = ArrayList<FaceGroup>(ranksByRoot.size)
    for (members in ranksByRoot.values) {
        val representative = representativeOf(faces, members, isSettingEnabled) ?: return null
        groups.add(FaceGroup(faces.faceIds[representative], members.map { faces.faceIds[it] }))
    }
    groups.sortBy { it.groupId }
    return groups
}

// The medoid: the member of smallest summed distance to the others; members ascend with face ids, so a tie is the lowest id.
private suspend fun representativeOf(faces: NormalisedFaces, members: List<Int>, isSettingEnabled: () -> Boolean): Int? {
    if (members.size == 1) return members[0]
    val sums = DoubleArray(members.size)
    for (first in 0 until members.size - 1) {
        if (!mayContinue(isSettingEnabled)) return null
        for (second in first + 1 until members.size) {
            val distance = faces.distance(members[first], members[second])
            sums[first] += distance
            sums[second] += distance
        }
    }
    var medoid = 0
    for (index in 1 until sums.size) {
        if (sums[index] < sums[medoid] - MEDOID_TIE_TOLERANCE) medoid = index
    }
    return members[medoid]
}

private suspend fun mayContinue(isSettingEnabled: () -> Boolean): Boolean {
    currentCoroutineContext().ensureActive()
    return isSettingEnabled()
}
