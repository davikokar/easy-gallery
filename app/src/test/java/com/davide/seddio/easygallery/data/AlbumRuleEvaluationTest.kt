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

        val result = evaluateOver(
            rule = null,
            rows = listOf(added.row, excluded.row),
            live = live,
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

        val result = evaluateOver(
            rule = timeRule("2025-06-10", "2025-06-10"),
            rows = index,
            live = listOf(kept.live),
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

        val result = evaluateOver(
            rule = timeRule("2025-06-10", "2025-06-10"),
            rows = listOf(staleRow),
            live = listOf(moved.live),
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

        val result = evaluateOver(
            rule = timeRule("2025-06-10", "2025-06-10"),
            rows = listOf(volumeRow),
            live = listOf(item.live),
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
            val result = evaluateOver(rule, index, listOf(live), emptyList(), UTC)
            assertEquals(listOf(live), result.members)
        }

        val directOutside = row(live.uri.toString(), live, dateTaken = 0L)
        val healedInside = row("content://media/external/images/media/8888", live, dateTaken = JUNE_10_NOON_UTC)
        for (index in listOf(listOf(healedInside, directOutside), listOf(directOutside, healedInside))) {
            val result = evaluateOver(rule, index, listOf(live), emptyList(), UTC)
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

        val result = evaluateOver(
            rule = timeRule("2025-06-10", "2025-06-10"),
            rows = listOf(indexed.row),
            live = listOf(indexed.live, notIndexed.live),
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

        val result = evaluateOver(
            rule = timeRule("2025-06-10", "2025-06-10"),
            rows = emptyList(),
            live = listOf(added.live, excluded.live),
            memberships = listOf(addition(100, added), exclusion(101, excluded)),
            zone = UTC
        )

        assertEquals(listOf(added.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `live media not yet supplied is waiting on the index and not an empty complete`() {
        val item = IndexedItem(1, JUNE_10_NOON_UTC)

        val result = evaluateOver(
            rule = timeRule("2025-06-10", "2025-06-10"),
            rows = listOf(item.row),
            live = null,
            memberships = listOf(addition(100, item)),
            zone = UTC
        )

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    @Test
    fun `an empty live list is a complete empty album`() {
        val result = evaluateOver(
            rule = timeRule("2025-06-10", "2025-06-10"),
            rows = emptyList(),
            live = emptyList(),
            memberships = emptyList(),
            zone = UTC
        )

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    // --- the join -----------------------------------------------------------------------------

    @Test
    fun `the join gives every live item one location state`() {
        val resolved = located(1, country = "IT", region = "16", place = "Florence", marker = CURRENT)
        val outOfCap = located(2, marker = CURRENT)
        val noCoordinates = located(3, hasCoordinates = false, marker = CURRENT)
        val noCoordinatesWithOldValues = located(4, hasCoordinates = false, country = "IT", marker = null)
        val pendingWithValues = located(5, country = "IT", place = "Rome", marker = null)
        val staleMarker = located(6, country = "FR", place = "Paris", marker = CURRENT + 1)
        val pendingWithout = located(7, marker = null)
        val unreached = notIndexed(8)

        val join = joinOf(
            resolved, outOfCap, noCoordinates, noCoordinatesWithOldValues,
            pendingWithValues, staleMarker, pendingWithout, unreached
        )

        assertEquals(
            listOf(
                LocationState.Resolved(PlaceValues("IT", "16", "Florence")),
                LocationState.ResolvedOutOfCap,
                LocationState.NoCoordinates,
                LocationState.NoCoordinates,
                LocationState.PendingWithOldValues(PlaceValues("IT", null, "Rome")),
                LocationState.PendingWithOldValues(PlaceValues("FR", null, "Paris")),
                LocationState.PendingWithoutValues,
                LocationState.NotIndexed
            ),
            join.items.map { it.location }
        )
    }

    @Test
    fun `the join gives every live item one date state`() {
        val dated = located(1, dateTaken = JUNE_10_NOON_UTC)
        val undated = located(2, dateTaken = null)
        val unreached = notIndexed(3)

        val join = joinOf(dated, undated, unreached)

        assertEquals(
            listOf(
                DateTakenSortState.IndexedWithDate(JUNE_10_NOON_UTC),
                DateTakenSortState.IndexedWithoutDate,
                DateTakenSortState.NotIndexed
            ),
            join.items.map { it.date }
        )
        assertEquals(join.items.map { it.item }, join.liveMedia)
    }

    @Test
    fun `an index row whose item is no longer live joins nothing`() {
        val kept = located(1, country = "IT", marker = CURRENT)
        val deleted = located(2, country = "IT", marker = CURRENT)

        val join = MediaIndexLiveJoin.build(listOf(kept.row!!, deleted.row!!), listOf(kept.live))

        assertEquals(listOf(kept.live), join.liveMedia)
    }

    @Test
    fun `a row spelled with a volume uuid joins the external live item with its place`() {
        val live = liveItem(7)
        val volumeRow = projection(
            live,
            dateTaken = JUNE_10_NOON_UTC,
            country = "IT",
            marker = CURRENT,
            uri = "content://media/1234-ABCD/images/media/7",
            // A different healing triple, so only the canonical uri join can match it.
            displayName = "other.jpg",
            size = -1L,
            dateModified = -1L
        )

        val join = MediaIndexLiveJoin.build(listOf(volumeRow), listOf(live))

        assertEquals(LocationState.Resolved(PlaceValues("IT", null, null)), join.items.single().location)
    }

    @Test
    fun `when a healed row and a direct row reach one live item the direct row supplies date and place`() {
        val live = liveItem(1)
        val direct = projection(live, dateTaken = JUNE_10_NOON_UTC, country = "IT", marker = CURRENT)
        val healed = projection(
            live,
            dateTaken = 0L,
            country = "FR",
            marker = CURRENT,
            uri = "content://media/external/images/media/8888"
        )

        for (rows in listOf(listOf(healed, direct), listOf(direct, healed))) {
            val joined = MediaIndexLiveJoin.build(rows, listOf(live)).items.single()

            assertEquals(DateTakenSortState.IndexedWithDate(JUNE_10_NOON_UTC), joined.date)
            assertEquals(LocationState.Resolved(PlaceValues("IT", null, null)), joined.location)
        }
    }

    @Test
    fun `a moved item joins through the healing key`() {
        val live = liveItem(1)
        val stale = projection(
            live,
            dateTaken = JUNE_10_NOON_UTC,
            country = "IT",
            marker = CURRENT,
            uri = "content://media/external/images/media/5001"
        )

        val joined = MediaIndexLiveJoin.build(listOf(stale), listOf(live)).items.single()

        assertEquals(LocationState.Resolved(PlaceValues("IT", null, null)), joined.location)
    }

    // --- region clause ------------------------------------------------------------------------

    @Test
    fun `a resolved item in the clause country is a member and the album is complete`() {
        val item = located(1, country = "IT", region = "16", place = "Florence", marker = CURRENT)

        val result = evaluateRows(regionRule("IT"), item)

        assertEquals(listOf(item.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `a resolved item in another country is not a member and nothing waits`() {
        val item = located(1, country = "FR", place = "Paris", marker = CURRENT)

        val result = evaluateRows(regionRule("IT"), item)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `an item with no coordinates is a settled no-match`() {
        val item = located(1, hasCoordinates = false, marker = null)

        val result = evaluateRows(regionRule("IT"), item)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `an item resolved beyond the cap is a settled no-match`() {
        val item = located(1, marker = CURRENT)

        val result = evaluateRows(regionRule("IT"), item)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `a pending item whose old values match is a provisional member and the album waits`() {
        val item = located(1, country = "IT", region = "16", place = "Florence", marker = null)

        val result = evaluateRows(regionRule("IT"), item)

        assertEquals(listOf(item.live), result.members)
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    @Test
    fun `a pending item whose old values differ is not a member and the album waits`() {
        val item = located(1, country = "FR", place = "Paris", marker = null)

        val result = evaluateRows(regionRule("IT"), item)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    @Test
    fun `a pending item with no values and a not-indexed item are not members and the album waits`() {
        for (item in listOf(located(1, marker = null), notIndexed(2))) {
            val result = evaluateRows(regionRule("IT"), item)

            assertTrue(result.members.isEmpty())
            assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
        }
    }

    @Test
    fun `a marker that is not the current version is pending even with values`() {
        for (marker in listOf(0, CURRENT + 1)) {
            val item = located(1, country = "IT", place = "Rome", marker = marker)

            val result = evaluateRows(regionRule("IT"), item)

            assertEquals(listOf(item.live), result.members)
            assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
        }
    }

    @Test
    fun `a country clause matches every region and a region clause only its own`() {
        val tuscany = located(1, country = "IT", region = "16", place = "Florence", marker = CURRENT)
        val lazio = located(2, country = "IT", region = "07", place = "Rome", marker = CURRENT)
        val noRegion = located(3, country = "IT", place = "Vatican", marker = CURRENT)

        val country = evaluateRows(regionRule("IT"), tuscany, lazio, noRegion)
        val region = evaluateRows(regionRule("IT", admin1 = "16", admin1Name = "Tuscany"), tuscany, lazio, noRegion)

        assertEquals(listOf(tuscany.live, lazio.live, noRegion.live), country.members)
        assertEquals(listOf(tuscany.live), region.members)
    }

    @Test
    fun `a place with its region pair matches only that region`() {
        val florence = located(1, country = "IT", region = "16", place = "Florence", marker = CURRENT)
        val otherRegion = located(2, country = "IT", region = "07", place = "Florence", marker = CURRENT)
        val rome = located(3, country = "IT", region = "16", place = "Rome", marker = CURRENT)

        val result = evaluateRows(
            regionRule("IT", admin1 = "16", admin1Name = "Tuscany", place = "Florence"),
            florence, otherRegion, rome
        )

        assertEquals(listOf(florence.live), result.members)
    }

    @Test
    fun `a place without a region pair matches only rows whose region is null`() {
        val singapore = located(1, country = "SG", place = "Singapore", marker = CURRENT)
        val sameNameWithRegion = located(2, country = "SG", region = "01", place = "Singapore", marker = CURRENT)
        val otherPlace = located(3, country = "SG", place = "Jurong", marker = CURRENT)

        val result = evaluateRows(regionRule("SG", place = "Singapore"), singapore, sameNameWithRegion, otherPlace)

        assertEquals(listOf(singapore.live), result.members)
    }

    @Test
    fun `two region clauses naming different countries match nothing`() {
        val item = located(1, country = "IT", marker = CURRENT)
        val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT"), AlbumRuleClause.Region("FR")))

        val result = evaluateRows(rule, item)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    // --- combined time and region -------------------------------------------------------------

    @Test
    fun `a combined rule needs the date and the place to match`() {
        val both = located(1, dateTaken = JUNE_10_NOON_UTC, country = "IT", marker = CURRENT)
        val wrongPlace = located(2, dateTaken = JUNE_10_NOON_UTC, country = "FR", marker = CURRENT)
        val wrongDate = located(3, dateTaken = epoch("2020-01-01T00:00:00Z"), country = "IT", marker = CURRENT)

        val result = evaluateRows(combinedRule("IT"), both, wrongPlace, wrongDate)

        assertEquals(listOf(both.live), result.members)
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `a date outside the range with a pending location is not a member and does not make the album wait`() {
        val outOfRange = located(1, dateTaken = epoch("2020-01-01T00:00:00Z"), marker = null)

        val result = evaluateRows(combinedRule("IT"), outOfRange)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `a settled date no-match also hides a pending location with matching old values`() {
        val outOfRange = located(1, dateTaken = epoch("2020-01-01T00:00:00Z"), country = "IT", marker = null)

        val result = evaluateRows(combinedRule("IT"), outOfRange)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `an item with no date and a pending location does not make the album wait`() {
        val undated = located(1, dateTaken = null, marker = null)

        val result = evaluateRows(combinedRule("IT"), undated)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `a date in range with a pending location waits`() {
        val inRange = located(1, dateTaken = JUNE_10_NOON_UTC, marker = null)

        val result = evaluateRows(combinedRule("IT"), inRange)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    @Test
    fun `a date in range with pending old values that match is a member and the album waits`() {
        val inRange = located(1, dateTaken = JUNE_10_NOON_UTC, country = "IT", marker = null)

        val result = evaluateRows(combinedRule("IT"), inRange)

        assertEquals(listOf(inRange.live), result.members)
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    @Test
    fun `a not-indexed item waits under a combined rule`() {
        val result = evaluateRows(combinedRule("IT"), notIndexed(1))

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    // --- ADR-0012 with places -----------------------------------------------------------------

    @Test
    fun `an exclusion beats a region match and an excluded pending item does not make the album wait`() {
        val resolved = located(1, country = "IT", marker = CURRENT)
        val pending = located(2, marker = null)

        val result = evaluateRows(
            rule = regionRule("IT"),
            items = arrayOf(resolved, pending),
            memberships = listOf(exclusionOf(100, resolved.live), exclusionOf(101, pending.live))
        )

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `an addition is a member and never makes the album wait`() {
        val pending = located(1, marker = null)
        val unreached = notIndexed(2)
        val noCoordinates = located(3, hasCoordinates = false, marker = null)

        val result = evaluateRows(
            rule = regionRule("IT"),
            items = arrayOf(pending, unreached, noCoordinates),
            memberships = listOf(
                additionOf(100, pending.live),
                additionOf(101, unreached.live),
                additionOf(102, noCoordinates.live)
            )
        )

        assertEquals(setOf(pending.live, unreached.live, noCoordinates.live), result.members.toSet())
        assertEquals(AlbumEvaluationStatus.COMPLETE, result.status)
    }

    @Test
    fun `an item that is both added and excluded stays out`() {
        val item = located(1, country = "IT", marker = CURRENT)

        val result = evaluateRows(
            rule = regionRule("IT"),
            items = arrayOf(item),
            memberships = listOf(additionOf(100, item.live), exclusionOf(101, item.live))
        )

        assertTrue(result.members.isEmpty())
    }

    // --- the evaluator and the join -----------------------------------------------------------

    @Test
    fun `live media not yet supplied is waiting for a region album and not an empty complete`() {
        val result = AlbumRuleEvaluator.evaluate(regionRule("IT"), null, emptyList(), UTC)

        assertTrue(result.members.isEmpty())
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, result.status)
    }

    // --- fixtures -----------------------------------------------------------------------------

    private class Located(val live: MediaItem, val row: MediaIndexEvaluationProjection?)

    private fun located(
        id: Int,
        dateTaken: Long? = JUNE_10_NOON_UTC,
        hasCoordinates: Boolean = true,
        country: String? = null,
        region: String? = null,
        place: String? = null,
        marker: Int? = null
    ): Located {
        val live = liveItem(id)
        return Located(live, projection(live, dateTaken, hasCoordinates, country, region, place, marker))
    }

    private fun notIndexed(id: Int) = Located(liveItem(id), row = null)

    private fun joinOf(vararg items: Located): MediaIndexLiveJoin {
        return MediaIndexLiveJoin.build(items.mapNotNull { it.row }, items.map { it.live })
    }

    private fun evaluateRows(
        rule: AlbumRule,
        vararg items: Located
    ): AlbumRuleEvaluation = evaluateRows(rule, arrayOf(*items), emptyList())

    private fun evaluateRows(
        rule: AlbumRule,
        items: Array<out Located>,
        memberships: List<StoredMembership>
    ): AlbumRuleEvaluation {
        return AlbumRuleEvaluator.evaluate(rule, joinOf(*items), memberships, UTC)
    }

    private fun regionRule(
        country: String,
        admin1: String? = null,
        admin1Name: String? = null,
        place: String? = null
    ) = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country, admin1, admin1Name, place)))

    private fun combinedRule(country: String) = AlbumRule.Evaluatable(
        listOf(
            AlbumRuleClause.Time(LocalDate.parse("2025-06-10"), LocalDate.parse("2025-06-10")),
            AlbumRuleClause.Region(country)
        )
    )

    private fun additionOf(id: Long, live: MediaItem) = membershipOf(id, live, StoredMembershipKind.ADDITION)

    private fun exclusionOf(id: Long, live: MediaItem) = membershipOf(id, live, StoredMembershipKind.EXCLUSION)

    private fun membershipOf(id: Long, live: MediaItem, kind: StoredMembershipKind) = StoredMembership(
        id = id,
        mediaUri = live.uri.toString(),
        displayName = live.name,
        size = live.size,
        dateModified = live.dateModified,
        kind = kind,
        isOrphaned = false
    )

    private class IndexedItem(val id: Int, val dateTaken: Long?) {
        val live: MediaItem = liveItem(id)
        val row: MediaIndexEvaluationProjection = row(live.uri.toString(), live, dateTaken)
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
        return evaluateOver(
            rule = rule,
            rows = items.map { it.row },
            live = items.map { it.live },
            memberships = memberships,
            zone = zone
        )
    }

    // Live null is MediaStore not read yet, so the join is null too.
    private fun evaluateOver(
        rule: AlbumRule?,
        rows: List<MediaIndexEvaluationProjection>,
        live: List<MediaItem>?,
        memberships: List<StoredMembership>,
        zone: ZoneId
    ): AlbumRuleEvaluation {
        val join = live?.let { MediaIndexLiveJoin.build(rows, it) }
        return AlbumRuleEvaluator.evaluate(rule, join, memberships, zone)
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

        val CURRENT = PLACE_RESOLVER_VERSION

        fun projection(
            live: MediaItem,
            dateTaken: Long?,
            hasCoordinates: Boolean = true,
            country: String? = null,
            region: String? = null,
            place: String? = null,
            marker: Int? = null,
            uri: String = live.uri.toString(),
            displayName: String = live.name,
            size: Long = live.size,
            dateModified: Long = live.dateModified
        ) = MediaIndexEvaluationProjection(
            identity = mediaIdentityOf(
                mediaUri = uri,
                displayName = displayName,
                size = size,
                dateModified = dateModified
            ),
            capturedDateTakenEpochMillis = dateTaken,
            hasCoordinates = hasCoordinates,
            derivedCountryCode = country,
            derivedRegionCode = region,
            derivedPlaceName = place,
            derivedPlaceResolverVersion = marker
        )

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

        fun row(uri: String, live: MediaItem, dateTaken: Long?) = projection(live, dateTaken, uri = uri)
    }
}
