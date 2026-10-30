package com.davide.seddio.easygallery.data

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class AlbumRuleEvaluationTest {

    private lateinit var originalDefaultZone: TimeZone

    @Before
    fun pinJvmDefaultZoneAwayFromTheTestZones() {
        originalDefaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
    }

    @After
    fun restoreJvmDefaultZone() {
        TimeZone.setDefault(originalDefaultZone)
    }

    // --- time clause bounds -------------------------------------------------------------------

    @Test
    fun `first millisecond of from is in and one millisecond earlier is out`() {
        val start = epoch("2025-06-09T15:00:00Z") // 2025-06-10T00:00 in Tokyo
        val item = IndexedItem(1, start)
        val earlier = IndexedItem(2, start - 1)

        val result = evaluate(timeRule(from = "2025-06-10", to = "2025-06-12"), TOKYO, item, earlier)

        assertEquals(listOf(item.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `last millisecond of to is in and the next days first millisecond is out`() {
        val nextDayStart = epoch("2025-06-12T15:00:00Z") // 2025-06-13T00:00 in Tokyo
        val last = IndexedItem(1, nextDayStart - 1)
        val next = IndexedItem(2, nextDayStart)

        val result = evaluate(timeRule(from = "2025-06-10", to = "2025-06-12"), TOKYO, last, next)

        assertEquals(listOf(last.live), result.members)
    }

    @Test
    fun `from only is open towards the future`() {
        val before = IndexedItem(1, epoch("2025-06-09T14:59:59Z"))
        val onFrom = IndexedItem(2, epoch("2025-06-09T15:00:00Z"))
        val farFuture = IndexedItem(3, epoch("2090-01-01T00:00:00Z"))

        val result = evaluate(timeRule(from = "2025-06-10", to = null), TOKYO, before, onFrom, farFuture)

        assertEquals(listOf(onFrom.live, farFuture.live), result.members)
    }

    @Test
    fun `to only is open towards the past`() {
        val farPast = IndexedItem(1, epoch("1971-01-01T00:00:00Z"))
        val onLastDay = IndexedItem(2, epoch("2025-06-12T14:59:59Z"))
        val after = IndexedItem(3, epoch("2025-06-12T15:00:00Z"))

        val result = evaluate(timeRule(from = null, to = "2025-06-12"), TOKYO, farPast, onLastDay, after)

        assertEquals(listOf(farPast.live, onLastDay.live), result.members)
    }

    @Test
    fun `a day that is 23 hours long ends at the next local midnight`() {
        // 2025-03-09 in New York: 05:00Z to 04:00Z next day.
        val lastMillis = IndexedItem(1, epoch("2025-03-10T04:00:00Z") - 1)
        val nextDay = IndexedItem(2, epoch("2025-03-10T04:00:00Z"))

        val result = evaluate(timeRule("2025-03-09", "2025-03-09"), NEW_YORK, lastMillis, nextDay)

        assertEquals(listOf(lastMillis.live), result.members)
    }

    @Test
    fun `a day that is 25 hours long keeps its last hour`() {
        // 2025-11-02 in New York: 04:00Z to 05:00Z next day. 24h after the start is still that day.
        val afterTwentyFourHours = IndexedItem(1, epoch("2025-11-03T04:00:00Z"))
        val lastMillis = IndexedItem(2, epoch("2025-11-03T05:00:00Z") - 1)
        val nextDay = IndexedItem(3, epoch("2025-11-03T05:00:00Z"))

        val result = evaluate(
            timeRule("2025-11-02", "2025-11-02"),
            NEW_YORK,
            afterTwentyFourHours,
            lastMillis,
            nextDay
        )

        assertEquals(listOf(afterTwentyFourHours.live, lastMillis.live), result.members)
    }

    @Test
    fun `the injected zone decides the day, not the jvm default`() {
        val instant = epoch("2025-06-10T20:00:00Z") // 06-11 in Tokyo, 06-10 in New York
        val item = IndexedItem(1, instant)

        val inTokyo = evaluate(timeRule("2025-06-11", "2025-06-11"), TOKYO, item)
        val inNewYork = evaluate(timeRule("2025-06-11", "2025-06-11"), NEW_YORK, item)

        assertEquals(listOf(item.live), inTokyo.members)
        assertTrue(inNewYork.members.isEmpty())
    }

    // --- ADR-0012 -----------------------------------------------------------------------------

    @Test
    fun `an exclusion beats a rule match`() {
        val item = IndexedItem(1, JUNE_10_NOON_UTC)

        val result = evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            zone = UTC,
            items = arrayOf(item),
            memberships = listOf(exclusion(100, item))
        )

        assertTrue(result.members.isEmpty())
    }

    @Test
    fun `an exclusion beats an addition`() {
        val item = IndexedItem(1, null)

        val result = evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            zone = UTC,
            items = arrayOf(item),
            memberships = listOf(addition(100, item), exclusion(101, item))
        )

        assertTrue(result.members.isEmpty())
    }

    @Test
    fun `an addition outside the rule is a member and the rule match is kept`() {
        val matching = IndexedItem(1, JUNE_10_NOON_UTC)
        val outside = IndexedItem(2, epoch("2020-01-01T00:00:00Z"))

        val result = evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            zone = UTC,
            items = arrayOf(matching, outside),
            memberships = listOf(addition(100, outside))
        )

        assertEquals(setOf(matching.live, outside.live), result.members.toSet())
        assertEquals(2, result.members.size)
    }

    @Test
    fun `an exclusion survives a re-emitted changed index`() {
        val item = IndexedItem(1, JUNE_10_NOON_UTC)
        val tombstone = exclusion(100, item)
        val rule = timeRule("2025-06-10", "2025-06-10")

        val first = evaluate(rule, UTC, arrayOf(item), listOf(tombstone))
        val reEmitted = IndexedItem(1, JUNE_10_NOON_UTC + 60_000L)
        val second = evaluate(rule, UTC, arrayOf(reEmitted), listOf(tombstone))

        assertTrue(first.members.isEmpty())
        assertTrue(second.members.isEmpty())
    }

    @Test
    fun `an unevaluatable rule resolves to additions minus exclusions`() {
        val added = IndexedItem(1, null)
        val excluded = IndexedItem(2, null)
        val indexedAndDated = IndexedItem(3, JUNE_10_NOON_UTC)
        val memberships = listOf(addition(100, added), addition(101, excluded), exclusion(102, excluded))
        val rule = AlbumRule.Unevaluatable("{\"version\":2}", UnevaluatableReason.UNKNOWN_VERSION)

        val result = evaluate(rule, UTC, arrayOf(added, excluded, indexedAndDated), memberships)

        assertEquals(listOf(added.live), result.members)
        assertEquals(AlbumEvaluationStatus.UNEVALUATABLE, result.status)
    }

    @Test
    fun `a manual album matches album membership resolve on its fixtures`() {
        val added = IndexedItem(1, JUNE_10_NOON_UTC)
        val excluded = IndexedItem(2, JUNE_10_NOON_UTC)
        val healedTarget = IndexedItem(3, JUNE_10_NOON_UTC)
        val memberships = listOf(
            addition(100, added),
            addition(101, excluded),
            exclusion(102, excluded),
            // Moved: stale uri, same healing triple as the live item.
            addition(103, healedTarget, uri = "content://media/external/images/media/9003"),
            addition(104, IndexedItem(4, null)) // not live: orphaned
        )
        val live = listOf(added.live, excluded.live, healedTarget.live)

        val result = AlbumRuleEvaluator.evaluate(
            rule = null,
            indexEntries = listOf(added.row, excluded.row),
            liveMedia = live,
            memberships = memberships,
            zone = UTC
        )

        assertEquals(AlbumMembership.resolve(memberships, live).albumItems, result.members)
        assertEquals(listOf(added.live, healedTarget.live), result.members)
        assertEquals(AlbumEvaluationStatus.MANUAL, result.status)
    }

    // --- ADR-0020 -----------------------------------------------------------------------------

    @Test
    fun `an indexed item missing from live media disappears and the index is untouched`() {
        val kept = IndexedItem(1, JUNE_10_NOON_UTC)
        val deleted = IndexedItem(2, JUNE_10_NOON_UTC)
        val index = listOf(kept.row, deleted.row)
        val indexBefore = index.toList()

        val result = AlbumRuleEvaluator.evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            indexEntries = index,
            liveMedia = listOf(kept.live),
            memberships = emptyList(),
            zone = UTC
        )

        assertEquals(listOf(kept.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
        assertEquals(indexBefore, index)
    }

    @Test
    fun `a moved item is matched through the healing key`() {
        val moved = IndexedItem(1, JUNE_10_NOON_UTC)
        val staleRow = moved.row.copy(
            identity = mediaIdentityOf(
                mediaUri = "content://media/external/images/media/5001",
                displayName = moved.live.name,
                size = moved.live.size,
                dateModified = moved.live.dateModified
            )
        )

        val result = AlbumRuleEvaluator.evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            indexEntries = listOf(staleRow),
            liveMedia = listOf(moved.live),
            memberships = emptyList(),
            zone = UTC
        )

        assertEquals(listOf(moved.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `an index row spelled with a volume uuid joins the external live item`() {
        val item = IndexedItem(7, JUNE_10_NOON_UTC)
        val volumeRow = item.row.copy(
            identity = mediaIdentityOf(
                mediaUri = "content://media/1234-ABCD/images/media/7",
                // A different healing triple, so only the canonical uri join can match it.
                displayName = "other.jpg",
                size = -1L,
                dateModified = -1L
            )
        )

        val result = AlbumRuleEvaluator.evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            indexEntries = listOf(volumeRow),
            liveMedia = listOf(item.live),
            memberships = emptyList(),
            zone = UTC
        )

        assertEquals(listOf(item.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `when a healed row and a direct row reach one live item the direct date wins`() {
        val live = IndexedItem(1, null).live
        val direct = row(live.uri.toString(), live, dateTaken = JUNE_10_NOON_UTC)
        val healedOutside = row("content://media/external/images/media/8888", live, dateTaken = 0L)
        val rule = timeRule("2025-06-10", "2025-06-10")

        for (index in listOf(listOf(healedOutside, direct), listOf(direct, healedOutside))) {
            val result = AlbumRuleEvaluator.evaluate(rule, index, listOf(live), emptyList(), UTC)
            assertEquals(listOf(live), result.members)
        }

        val directOutside = row(live.uri.toString(), live, dateTaken = 0L)
        val healedInside = row("content://media/external/images/media/8888", live, dateTaken = JUNE_10_NOON_UTC)
        for (index in listOf(listOf(healedInside, directOutside), listOf(directOutside, healedInside))) {
            val result = AlbumRuleEvaluator.evaluate(rule, index, listOf(live), emptyList(), UTC)
            assertTrue(result.members.isEmpty())
        }
    }

    // --- status -------------------------------------------------------------------------------

    @Test
    fun `an item indexed without a date neither matches nor counts as pending`() {
        val noDate = IndexedItem(1, null)

        val result = evaluate(timeRule("2025-06-10", "2025-06-10"), UTC, noDate)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `a live item that is not indexed makes the album wait on the index`() {
        val indexed = IndexedItem(1, JUNE_10_NOON_UTC)
        val notIndexed = IndexedItem(2, JUNE_10_NOON_UTC)

        val result = AlbumRuleEvaluator.evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            indexEntries = listOf(indexed.row),
            liveMedia = listOf(indexed.live, notIndexed.live),
            memberships = emptyList(),
            zone = UTC
        )

        assertEquals(listOf(indexed.live), result.members)
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    @Test
    fun `a not indexed item that is already added or excluded does not keep the album waiting`() {
        val added = IndexedItem(1, null)
        val excluded = IndexedItem(2, null)

        val result = AlbumRuleEvaluator.evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            indexEntries = emptyList(),
            liveMedia = listOf(added.live, excluded.live),
            memberships = listOf(addition(100, added), exclusion(101, excluded)),
            zone = UTC
        )

        assertEquals(listOf(added.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `live media not yet supplied is waiting on the index and not an empty complete`() {
        val item = IndexedItem(1, JUNE_10_NOON_UTC)

        val result = AlbumRuleEvaluator.evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            indexEntries = listOf(item.row),
            liveMedia = null,
            memberships = listOf(addition(100, item)),
            zone = UTC
        )

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    @Test
    fun `an empty live list is a complete empty album`() {
        val result = AlbumRuleEvaluator.evaluate(
            rule = timeRule("2025-06-10", "2025-06-10"),
            indexEntries = emptyList(),
            liveMedia = emptyList(),
            memberships = emptyList(),
            zone = UTC
        )

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    // --- fixtures -----------------------------------------------------------------------------

    private class IndexedItem(val id: Int, val dateTaken: Long?) {
        val live: MediaItem = liveItem(id)
        val row: MediaIndexIdentityDateTaken = row(live.uri.toString(), live, dateTaken)
    }

    private fun evaluate(
        rule: AlbumRule,
        zone: ZoneId,
        vararg items: IndexedItem
    ): AlbumRuleEvaluation = evaluate(rule, zone, arrayOf(*items), emptyList())

    private fun evaluate(
        rule: AlbumRule,
        zone: ZoneId,
        items: Array<out IndexedItem>,
        memberships: List<StoredMembership>
    ): AlbumRuleEvaluation {
        return AlbumRuleEvaluator.evaluate(
            rule = rule,
            indexEntries = items.map { it.row },
            liveMedia = items.map { it.live },
            memberships = memberships,
            zone = zone
        )
    }

    private fun timeRule(from: String?, to: String?): AlbumRule.Evaluatable {
        return AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Time(from?.let(LocalDate::parse), to?.let(LocalDate::parse)))
        )
    }

    private fun addition(id: Long, item: IndexedItem, uri: String = item.live.uri.toString()) =
        membership(id, item, uri, StoredMembershipKind.ADDITION)

    private fun exclusion(id: Long, item: IndexedItem) =
        membership(id, item, item.live.uri.toString(), StoredMembershipKind.EXCLUSION)

    private fun membership(
        id: Long,
        item: IndexedItem,
        uri: String,
        kind: StoredMembershipKind
    ) = StoredMembership(
        id = id,
        mediaUri = uri,
        displayName = item.live.name,
        size = item.live.size,
        dateModified = item.live.dateModified,
        kind = kind,
        isOrphaned = false
    )

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val JUNE_10_NOON_UTC = Instant.parse("2025-06-10T12:00:00Z").toEpochMilli()

        fun epoch(iso: String): Long = Instant.parse(iso).toEpochMilli()

        fun liveItem(id: Int): MediaItem {
            val uri = mockk<Uri>()
            every { uri.toString() } returns "content://media/external/images/media/$id"
            return MediaItem(
                uri = uri,
                name = "IMG_$id.jpg",
                dateAdded = 1_000L + id,
                dateModified = 2_000L + id,
                size = 10_000L + id,
                type = MediaType.IMAGE,
                bucketName = "Camera",
                folderPath = "/storage/emulated/0/DCIM/Camera"
            )
        }

        fun row(uri: String, live: MediaItem, dateTaken: Long?) = MediaIndexIdentityDateTaken(
            identity = mediaIdentityOf(
                mediaUri = uri,
                displayName = live.name,
                size = live.size,
                dateModified = live.dateModified
            ),
            capturedDateTakenEpochMillis = dateTaken
        )
    }
}
