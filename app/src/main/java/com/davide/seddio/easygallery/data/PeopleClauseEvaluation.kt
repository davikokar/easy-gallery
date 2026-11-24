package com.davide.seddio.easygallery.data

/** Whether a people album can be evaluated on this device now; derived outside the evaluator (plan 3.5). */
enum class PeopleAvailability {
    AVAILABLE,
    FACE_DATA_OFF,
    DEVICE_INCAPABLE
}

/** What a `people` clause is evaluated against. The evaluator reads nothing else of face data and never an embedding. */
data class PeopleEvaluationInput(
    val availability: PeopleAvailability,
    val faces: FaceEvaluationSnapshot
)

/** The anchor resolves when the best face overlaps the clause's box by at least this much (decision 46). */
internal const val PEOPLE_ANCHOR_MINIMUM_IOU = 0.5

internal sealed interface AnchorResolution {
    /** The anchor is the person whose faces carry this group id at the current grouping. */
    data class Group(val groupId: Long) : AnchorResolution

    /** Face indexing or grouping has not reached the anchor yet; waiting will settle it. */
    data object Waiting : AnchorResolution

    /** Waiting will not settle it: the photo is gone, not an image, failed, or has no face at the box. */
    data object NotFound : AnchorResolution
}

internal enum class PeopleItemAnswer { MATCH, SETTLED_NO_MATCH, UNSETTLED }

/**
 * ADR-0022 §3, in order. [faces] null is not loaded yet, which is waiting. The anchor image's Tier C data is trusted
 * only while the live item, the winning index row and the face snapshot agree on the healing triple.
 */
internal fun resolveAnchor(
    clause: AlbumRuleClause.People,
    join: MediaIndexLiveJoin,
    faces: FaceEvaluationSnapshot?
): AnchorResolution {
    // Not loaded yet is waiting, whatever the live media says: it is never unevaluatable.
    if (faces == null) return AnchorResolution.Waiting

    val live = join.lookup.find(clause.anchor)?.item ?: return AnchorResolution.NotFound
    if (live.type != MediaType.IMAGE) return AnchorResolution.NotFound

    val image = imageForLiveItem(join.joinedItemOf(live)?.indexedIdentity, live, faces)
        ?: return AnchorResolution.Waiting
    if (image.outcome == FaceIndexOutcome.FAILED) return AnchorResolution.NotFound

    val face = bestFace(image.faces, clause.box) ?: return AnchorResolution.NotFound
    val groupId = face.groupId
    if (groupId == null || face.groupingVersion != FACE_GROUPING_VERSION) return AnchorResolution.Waiting
    return AnchorResolution.Group(groupId)
}

/** The answer of a clause resolved to [groupId] for one live item (plan 3.5's table). */
internal fun peopleItemAnswer(
    groupId: Long,
    joined: JoinedMedia,
    join: MediaIndexLiveJoin,
    faces: FaceEvaluationSnapshot
): PeopleItemAnswer {
    if (joined.item.type != MediaType.IMAGE) return PeopleItemAnswer.SETTLED_NO_MATCH

    val image = imageForLiveItem(joined.indexedIdentity, joined.item, faces) ?: return PeopleItemAnswer.UNSETTLED
    if (image.outcome == FaceIndexOutcome.FAILED) return PeopleItemAnswer.SETTLED_NO_MATCH

    if (image.faces.any { it.groupId == groupId && it.groupingVersion == FACE_GROUPING_VERSION }) {
        return PeopleItemAnswer.MATCH
    }
    if (image.faces.any { it.groupId == null || it.groupingVersion != FACE_GROUPING_VERSION }) {
        return PeopleItemAnswer.UNSETTLED
    }
    return PeopleItemAnswer.SETTLED_NO_MATCH
}

private fun imageForLiveItem(
    indexed: MediaIdentity?,
    live: MediaItem,
    faces: FaceEvaluationSnapshot
): FaceEvaluationImage? {
    if (indexed == null) return null
    val image = faces.imageOf(indexed.mediaUri) ?: return null
    val liveKey = live.toMediaIdentity().healingKey
    if (indexed.healingKey != liveKey || image.identity.healingKey != liveKey) return null
    return image
}

// Best overlap wins; on exactly equal overlap the lower face id (ADR-0022 §3 step 4), whatever order the faces come in.
private fun bestFace(faces: List<FaceEvaluationFace>, anchorBox: FaceBox): FaceEvaluationFace? {
    var best: FaceEvaluationFace? = null
    var bestIou = 0.0
    for (face in faces) {
        val iou = faceBoxIou(face.box, anchorBox)
        if (iou < PEOPLE_ANCHOR_MINIMUM_IOU) continue
        val current = best
        if (current == null || iou > bestIou || (iou == bestIou && face.id < current.id)) {
            best = face
            bestIou = iou
        }
    }
    return best
}

/** Intersection over union of two boxes, computed in Double from the stored Floats. */
internal fun faceBoxIou(a: FaceBox, b: FaceBox): Double {
    val width = minOf(a.right, b.right).toDouble() - maxOf(a.left, b.left).toDouble()
    val height = minOf(a.bottom, b.bottom).toDouble() - maxOf(a.top, b.top).toDouble()
    if (width <= 0.0 || height <= 0.0) return 0.0
    val intersection = width * height
    val union = area(a) + area(b) - intersection
    return intersection / union
}

private fun area(box: FaceBox): Double {
    return (box.right.toDouble() - box.left.toDouble()) * (box.bottom.toDouble() - box.top.toDouble())
}
