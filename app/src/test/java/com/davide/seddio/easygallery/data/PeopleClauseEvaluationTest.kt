package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The resolution of a people clause's anchor and its per-item answers, through the pure evaluator (plan 3.5). */
class PeopleClauseEvaluationTest {

    // --- resolution: IoU and the tie rule -----------------------------------------------------

    @Test
    fun `an overlap of exactly one half resolves the anchor`() {
        val anchor = photo(1, listOf(face(1, G, box = HALF_HEIGHT_BOX)))
        val other = photo(2, listOf(face(2, G)))

        val result = evaluate(people(anchor), anchor, other)

        assertComplete(result, anchor, other)
    }

    @Test
    fun `an overlap just under one half does not resolve the anchor`() {
        val anchor = photo(1, listOf(face(1, G, box = FaceBox(0f, 0f, 0.5f, 0.24f))))

        val result = evaluate(people(anchor), anchor)

        assertNotFound(result)
    }

    @Test
    fun `the face with the best overlap is the person, not the first one that qualifies`() {
        val anchor = photo(1, listOf(face(1, G, box = FaceBox(0f, 0f, 0.5f, 0.3f)), face(2, OTHER_G)))
        val inFirst = photo(2, listOf(face(3, G)))
        val inBest = photo(3, listOf(face(4, OTHER_G)))

        val result = evaluate(people(anchor), anchor, inFirst, inBest)

        assertComplete(result, anchor, inBest)
    }

    @Test
    fun `on exactly equal overlap the lower face id is the person whatever order the faces come in`() {
        val anchor = photo(1, listOf(face(7, OTHER_G), face(3, G)))
        val inLower = photo(2, listOf(face(8, G)))
        val inHigher = photo(3, listOf(face(9, OTHER_G)))

        val result = evaluate(people(anchor), anchor, inLower, inHigher)

        assertComplete(result, anchor, inLower)
    }

    // --- resolution: the anchor image ---------------------------------------------------------

    @Test
    fun `an anchor whose uri changed is found by its healing triple`() {
        val anchor = photo(1, listOf(face(1, G)))
        val other = photo(2, listOf(face(2, G)))
        val restored = people(anchor, uri = "content://media/external/images/media/999")

        val result = evaluate(restored, anchor, other)

        assertComplete(result, anchor, other)
    }

    @Test
    fun `an anchor image that is not on the device is not found`() {
        val gone = photo(1, listOf(face(1, G)))
        val other = photo(2, listOf(face(2, G)))

        val result = evaluate(people(gone), other)

        assertNotFound(result)
    }

    @Test
    fun `an anchor image not yet processed waits and is not not found`() {
        val unprocessed = photo(1, faces = null)
        val other = photo(2, listOf(face(2, G)))

        val result = evaluate(people(unprocessed), unprocessed, other)

        assertWaiting(result)
    }

    @Test
    fun `an anchor image no index row has reached yet waits`() {
        val unreached = photo(1, faces = null, indexed = false)

        assertWaiting(evaluate(people(unreached), unreached))
    }

    @Test
    fun `an anchor image whose inference failed is not found`() {
        val failed = photo(1, emptyList(), outcome = FaceIndexOutcome.FAILED)

        assertNotFound(evaluate(people(failed), failed))
    }

    @Test
    fun `an anchor image processed with no face at all is not found`() {
        val noFace = photo(1, emptyList())

        assertNotFound(evaluate(people(noFace), noFace))
    }

    @Test
    fun `an anchor that lands on a video or a gif is not found, not waiting`() {
        val video = photo(1, faces = null, type = MediaType.VIDEO)
        val gif = photo(2, faces = null, type = MediaType.GIF)

        assertNotFound(evaluate(people(video), video))
        assertNotFound(evaluate(people(gif), gif))
    }

    @Test
    fun `an anchor face with no group, or an old grouping version, waits`() {
        val ungrouped = photo(1, listOf(face(1, group = null, version = null)))
        val oldGrouping = photo(2, listOf(face(2, G, version = FACE_GROUPING_VERSION + 1)))

        assertWaiting(evaluate(people(ungrouped), ungrouped))
        assertWaiting(evaluate(people(oldGrouping), oldGrouping))
    }

    @Test
    fun `an anchor image whose three healing triples disagree waits and is never not found`() {
        val anchor = photo(1, listOf(face(1, G)))
        val changedInSnapshot = anchor.copy(image = anchor.image!!.copy(identity = anchor.image.identity.withSize(1L)))
        val changedInRow = anchor.copy(row = anchor.row!!.copy(identity = anchor.row.identity.withSize(1L)))

        assertWaiting(evaluate(people(anchor), changedInSnapshot))
        assertWaiting(evaluate(people(anchor), changedInRow))
    }

    // --- per-item answers ---------------------------------------------------------------------

    @Test
    fun `a member is an image with any face in the group, not only the resolved face`() {
        val anchor = photo(1, listOf(face(1, G)))
        val twoPeople = photo(2, listOf(face(2, OTHER_G), face(3, G)))

        val result = evaluate(people(anchor), anchor, twoPeople)

        assertComplete(result, anchor, twoPeople)
    }

    @Test
    fun `a video and a gif in the library are settled non-members and leave the album complete`() {
        val anchor = photo(1, listOf(face(1, G)))
        val video = photo(2, faces = null, type = MediaType.VIDEO)
        val gif = photo(3, faces = null, type = MediaType.GIF, indexed = false)

        val result = evaluate(people(anchor), anchor, video, gif)

        assertComplete(result, anchor)
    }

    @Test
    fun `an image whose inference failed, or whose faces are all in other groups, is a settled non-member`() {
        val anchor = photo(1, listOf(face(1, G)))
        val failed = photo(2, emptyList(), outcome = FaceIndexOutcome.FAILED)
        val elsewhere = photo(3, listOf(face(3, OTHER_G)))
        val noFace = photo(4, emptyList())

        val result = evaluate(people(anchor), anchor, failed, elsewhere, noFace)

        assertComplete(result, anchor)
    }

    @Test
    fun `an image not yet indexed, not yet processed, or with disagreeing triples keeps the album waiting`() {
        val anchor = photo(1, listOf(face(1, G)))
        val unreached = photo(2, faces = null, indexed = false)
        val unprocessed = photo(3, faces = null)
        val skewed = photo(4, listOf(face(4, G))).let { it.copy(image = it.image!!.copy(identity = it.image.identity.withSize(1L))) }

        assertWaiting(evaluate(people(anchor), anchor, unreached))
        assertWaiting(evaluate(people(anchor), anchor, unprocessed))
        assertWaiting(evaluate(people(anchor), anchor, skewed))
    }

    @Test
    fun `an image with a face grouping has not reached waits unless a face of it is in the group`() {
        val anchor = photo(1, listOf(face(1, G)))
        val ungrouped = photo(2, listOf(face(2, group = null, version = null)))
        val oldGrouping = photo(3, listOf(face(3, G, version = FACE_GROUPING_VERSION + 1)))
        val ungroupedButMember = photo(4, listOf(face(4, G), face(5, group = null, version = null)))

        assertWaiting(evaluate(people(anchor), anchor, ungrouped))
        assertWaiting(evaluate(people(anchor), anchor, oldGrouping))
        assertComplete(evaluate(people(anchor), anchor, ungroupedButMember), anchor, ungroupedButMember)
    }

    @Test
    fun `an unsettled people answer does not keep the album waiting once another clause settles no for that item`() {
        val anchor = photo(1, listOf(face(1, G)), country = "IT")
        val ungroupedOutside = photo(2, listOf(face(2, group = null, version = null)), country = "FR")

        val result = evaluate(AlbumRule.Evaluatable(listOf(people(anchor), AlbumRuleClause.Region("IT"))), anchor, ungroupedOutside)

        assertComplete(result, anchor)
    }

    // --- several clauses ----------------------------------------------------------------------

    @Test
    fun `two people are both required in the picture`() {
        val firstAnchor = photo(1, listOf(face(1, G)))
        val secondAnchor = photo(2, listOf(face(2, OTHER_G)))
        val both = photo(3, listOf(face(3, G), face(4, OTHER_G)))
        val onlyFirst = photo(4, listOf(face(5, G)))
        val onlySecond = photo(5, listOf(face(6, OTHER_G)))

        val result = evaluate(
            AlbumRule.Evaluatable(listOf(people(firstAnchor), people(secondAnchor))),
            firstAnchor, secondAnchor, both, onlyFirst, onlySecond
        )

        assertComplete(result, both)
    }

    @Test
    fun `two anchors resolving to one group are satisfied by one face`() {
        val firstAnchor = photo(1, listOf(face(1, G)))
        val secondAnchor = photo(2, listOf(face(2, G)))
        val third = photo(3, listOf(face(3, G)))
        val elsewhere = photo(4, listOf(face(4, OTHER_G)))

        val result = evaluate(
            AlbumRule.Evaluatable(listOf(people(firstAnchor), people(secondAnchor))),
            firstAnchor, secondAnchor, third, elsewhere
        )

        assertComplete(result, firstAnchor, secondAnchor, third)
    }

    @Test
    fun `a person, a region and a time range together`() {
        val anchor = photo(1, listOf(face(1, G)), country = "IT")
        val member = photo(2, listOf(face(2, G)), country = "IT")
        val wrongPlace = photo(3, listOf(face(3, G)), country = "FR")
        val wrongDate = photo(4, listOf(face(4, G)), country = "IT", dateTaken = JUNE_10_NOON_UTC + 5 * DAY_MILLIS)
        val wrongPerson = photo(5, listOf(face(5, OTHER_G)), country = "IT")
        val video = photo(6, faces = null, type = MediaType.VIDEO, country = "IT")
        val gif = photo(7, faces = null, type = MediaType.GIF, country = "IT")
        val rule = AlbumRule.Evaluatable(
            listOf(
                people(anchor),
                AlbumRuleClause.Region("IT"),
                AlbumRuleClause.Time(LocalDate.parse("2025-06-10"), LocalDate.parse("2025-06-10"))
            )
        )

        val result = evaluate(rule, anchor, member, wrongPlace, wrongDate, wrongPerson, video, gif)

        assertComplete(result, anchor, member)
    }

    // --- status and cause ---------------------------------------------------------------------

    @Test
    fun `face data off or an incapable device make a people album unevaluatable, before any anchor is looked for`() {
        val gone = photo(1, listOf(face(1, G)))

        val off = evaluate(people(gone), availability = PeopleAvailability.FACE_DATA_OFF)
        val incapable = evaluate(people(gone), availability = PeopleAvailability.DEVICE_INCAPABLE)

        assertUnevaluatable(off, AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_FACE_DATA_OFF)
        assertUnevaluatable(incapable, AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE)
    }

    @Test
    fun `an unavailable people album keeps the additions minus the exclusions as its members`() {
        val anchor = photo(1, listOf(face(1, G)))
        val added = photo(2, listOf(face(2, G)))
        val excluded = photo(3, listOf(face(3, G)))

        val result = evaluate(
            people(anchor), anchor, added, excluded,
            availability = PeopleAvailability.FACE_DATA_OFF,
            memberships = listOf(membership(100, added, StoredMembershipKind.ADDITION), membership(101, excluded, StoredMembershipKind.EXCLUSION))
        )

        assertUnevaluatable(result, AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_FACE_DATA_OFF)
        assertEquals(listOf(added.item), result.members)
    }

    @Test
    fun `unavailability is told before the live media is read`() {
        val rule = AlbumRule.Evaluatable(listOf(people(photo(1, listOf(face(1, G))))))
        val off = PeopleEvaluationInput(PeopleAvailability.FACE_DATA_OFF, FaceEvaluationSnapshot(emptyList()))
        val on = PeopleEvaluationInput(PeopleAvailability.AVAILABLE, FaceEvaluationSnapshot(emptyList()))

        val unavailable = AlbumRuleEvaluator.evaluate(rule, null, emptyList(), UTC, off)
        val available = AlbumRuleEvaluator.evaluate(rule, null, emptyList(), UTC, on)

        assertUnevaluatable(unavailable, AlbumUnevaluatableCause.PEOPLE_UNAVAILABLE_FACE_DATA_OFF)
        assertTrue(unavailable.members.isEmpty())
        assertWaiting(available)
    }

    @Test
    fun `an anchor not found beats another anchor that is waiting`() {
        val waiting = photo(1, faces = null)
        val gone = photo(2, listOf(face(2, G)))
        val other = photo(3, listOf(face(3, G)))

        val result = evaluate(AlbumRule.Evaluatable(listOf(people(waiting), people(gone))), waiting, other)

        assertNotFound(result)
    }

    @Test
    fun `an anchor not found keeps the additions minus the exclusions`() {
        val gone = photo(1, listOf(face(1, G)))
        val added = photo(2, listOf(face(2, G)))

        val result = evaluate(
            people(gone), added,
            memberships = listOf(membership(100, added, StoredMembershipKind.ADDITION))
        )

        assertNotFound(result)
        assertEquals(listOf(added.item), result.members)
    }

    @Test
    fun `people input not loaded yet waits and is never unevaluatable`() {
        val anchor = photo(1, listOf(face(1, G)))
        val gone = photo(2, listOf(face(2, G)))

        val join = joinOf(anchor)
        val loadedNowhere = AlbumRuleEvaluator.evaluate(AlbumRule.Evaluatable(listOf(people(anchor))), join, emptyList(), UTC, people = null)
        val goneAndNotLoaded = AlbumRuleEvaluator.evaluate(AlbumRule.Evaluatable(listOf(people(gone))), join, emptyList(), UTC, people = null)

        assertWaiting(loadedNowhere)
        assertWaiting(goneAndNotLoaded)
    }

    @Test
    fun `a rule without a people clause never consults the people input`() {
        val dated = photo(1, faces = null, country = "IT")
        val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT")))

        val off = evaluate(rule, dated, availability = PeopleAvailability.FACE_DATA_OFF)
        val notLoaded = AlbumRuleEvaluator.evaluate(rule, joinOf(dated), emptyList(), UTC, people = null)

        assertComplete(off, dated)
        assertComplete(notLoaded, dated)
    }

    @Test
    fun `no rule is manual and an unparsed rule is not understood, both without a people input`() {
        val item = photo(1, listOf(face(1, G)))
        val manual = AlbumRuleEvaluator.evaluate(null, joinOf(item), emptyList(), UTC, people = null)
        val unparsed = AlbumRuleEvaluator.evaluate(
            AlbumRule.Unevaluatable("{}", UnevaluatableReason.UNKNOWN_CLAUSE_TYPE), joinOf(item), emptyList(), UTC, people = null
        )

        assertEquals(AlbumEvaluationStatus.MANUAL, manual.status)
        assertNull(manual.unevaluatableCause)
        assertUnevaluatable(unparsed, AlbumUnevaluatableCause.RULE_NOT_UNDERSTOOD)
    }

    @Test
    fun `an evaluation has a cause exactly when it is unevaluatable`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleEvaluation(emptyList(), AlbumEvaluationStatus.UNEVALUATABLE, null)
        }
        AlbumEvaluationStatus.entries.filter { it != AlbumEvaluationStatus.UNEVALUATABLE }.forEach { status ->
            assertThrows(IllegalArgumentException::class.java) {
                AlbumRuleEvaluation(emptyList(), status, AlbumUnevaluatableCause.ANCHOR_NOT_FOUND)
            }
        }
    }

    // --- the join keeps the winning row's identity --------------------------------------------

    @Test
    fun `the join keeps the identity of the direct row when a healed row also reaches the item`() {
        val item = live(1)
        val direct = rowOf(item)
        val healed = rowOf(item, storedUri = "content://media/external/images/media/77")

        val join = MediaIndexLiveJoin.build(listOf(healed, direct), listOf(item))

        assertEquals(direct.identity, join.items.single().indexedIdentity)
    }

    @Test
    fun `the join keeps the identity of a healed row, and none for an item no row reached`() {
        val healedItem = live(1)
        val unreached = live(2)
        val healed = rowOf(healedItem, storedUri = "content://media/external/images/media/77")

        val join = MediaIndexLiveJoin.build(listOf(healed), listOf(healedItem, unreached))

        assertEquals(healed.identity, join.items[0].indexedIdentity)
        assertNotNull(join.items[0].indexedIdentity)
        assertNull(join.items[1].indexedIdentity)
    }

    @Test
    fun `the face box overlap is the intersection over the union`() {
        assertEquals(1.0, faceBoxIou(ANCHOR_BOX, ANCHOR_BOX), 0.0)
        assertEquals(0.5, faceBoxIou(ANCHOR_BOX, HALF_HEIGHT_BOX), 0.0)
        assertEquals(0.0, faceBoxIou(ANCHOR_BOX, FaceBox(0.5f, 0.5f, 1f, 1f)), 0.0)
    }

    // --- fixtures -----------------------------------------------------------------------------

    private data class Photo(
        val item: MediaItem,
        val row: MediaIndexEvaluationProjection?,
        val image: FaceEvaluationImage?
    )

    private fun live(id: Int, type: MediaType = MediaType.IMAGE): MediaItem {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/external/images/media/$id"
        return MediaItem(
            uri = uri,
            name = "IMG_$id.jpg",
            dateAdded = 1_000L + id,
            dateModified = 2_000L + id,
            size = 10_000L + id,
            type = type,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
    }

    private fun rowOf(
        item: MediaItem,
        dateTaken: Long? = JUNE_10_NOON_UTC,
        country: String? = null,
        storedUri: String = item.uri.toString()
    ) = MediaIndexEvaluationProjection(
        identity = mediaIdentityOf(storedUri, item.name, item.size, item.dateModified),
        capturedDateTakenEpochMillis = dateTaken,
        hasCoordinates = country != null,
        derivedCountryCode = country,
        derivedRegionCode = null,
        derivedPlaceName = null,
        derivedPlaceResolverVersion = if (country != null) PLACE_RESOLVER_VERSION else null
    )

    /** [faces] null: no face state yet. [indexed] false: no index row either. */
    private fun photo(
        id: Int,
        faces: List<FaceEvaluationFace>?,
        type: MediaType = MediaType.IMAGE,
        outcome: FaceIndexOutcome = FaceIndexOutcome.INDEXED,
        indexed: Boolean = true,
        country: String? = null,
        dateTaken: Long? = JUNE_10_NOON_UTC
    ): Photo {
        val item = live(id, type)
        val row = if (indexed) rowOf(item, dateTaken, country) else null
        val image = if (indexed && faces != null) FaceEvaluationImage(row!!.identity, outcome, faces) else null
        return Photo(item, row, image)
    }

    private fun face(id: Long, group: Long?, box: FaceBox = ANCHOR_BOX, version: Int? = FACE_GROUPING_VERSION) =
        FaceEvaluationFace(id, box, group, if (group == null) null else version)

    private fun MediaIdentity.withSize(size: Long) = copy(healingKey = healingKey.copy(size = size))

    private fun people(
        anchor: Photo,
        box: FaceBox = ANCHOR_BOX,
        uri: String = anchor.item.uri.toString()
    ) = AlbumRuleClause.People(
        anchor = mediaIdentityOf(uri, anchor.item.name, anchor.item.size, anchor.item.dateModified),
        box = box,
        pipelineVersion = 1
    )

    private fun joinOf(vararg photos: Photo) =
        MediaIndexLiveJoin.build(photos.mapNotNull { it.row }, photos.map { it.item })

    private fun evaluate(
        clause: AlbumRuleClause,
        vararg photos: Photo,
        availability: PeopleAvailability = PeopleAvailability.AVAILABLE,
        memberships: List<StoredMembership> = emptyList()
    ): AlbumRuleEvaluation = evaluate(AlbumRule.Evaluatable(listOf(clause)), *photos, availability = availability, memberships = memberships)

    private fun evaluate(
        rule: AlbumRule,
        vararg photos: Photo,
        availability: PeopleAvailability = PeopleAvailability.AVAILABLE,
        memberships: List<StoredMembership> = emptyList()
    ): AlbumRuleEvaluation {
        val snapshot = FaceEvaluationSnapshot(photos.mapNotNull { it.image })
        return AlbumRuleEvaluator.evaluate(rule, joinOf(*photos), memberships, UTC, PeopleEvaluationInput(availability, snapshot))
    }

    private fun membership(id: Long, photo: Photo, kind: StoredMembershipKind) = StoredMembership(
        id = id,
        mediaUri = photo.item.uri.toString(),
        displayName = photo.item.name,
        size = photo.item.size,
        dateModified = photo.item.dateModified,
        kind = kind,
        isOrphaned = false
    )

    private fun assertComplete(result: AlbumRuleEvaluation, vararg members: Photo) {
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
        assertNull(result.unevaluatableCause)
        assertEquals(members.map { it.item }.toSet(), result.members.toSet())
        assertEquals(members.size, result.members.size)
    }

    private fun assertWaiting(result: AlbumRuleEvaluation) {
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
        assertNull(result.unevaluatableCause)
    }

    private fun assertNotFound(result: AlbumRuleEvaluation) = assertUnevaluatable(result, AlbumUnevaluatableCause.ANCHOR_NOT_FOUND)

    private fun assertUnevaluatable(result: AlbumRuleEvaluation, cause: AlbumUnevaluatableCause) {
        assertEquals(AlbumEvaluationStatus.UNEVALUATABLE, result.status)
        assertEquals(cause, result.unevaluatableCause)
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val JUNE_10_NOON_UTC: Long = Instant.parse("2025-06-10T12:00:00Z").toEpochMilli()
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val G = 10L
        const val OTHER_G = 20L

        val ANCHOR_BOX = FaceBox(0f, 0f, 0.5f, 0.5f)

        // Half the anchor's height: intersection 0.125, union 0.25, overlap exactly one half in binary floating point.
        val HALF_HEIGHT_BOX = FaceBox(0f, 0f, 0.5f, 0.25f)
    }
}
