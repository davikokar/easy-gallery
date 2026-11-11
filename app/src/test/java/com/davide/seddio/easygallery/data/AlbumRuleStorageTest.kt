package com.davide.seddio.easygallery.data

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Real Room + the real RoomAlbumStore: the rule must come back from the albums.rule column untouched.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class AlbumRuleStorageTest {

    private lateinit var database: EasyGalleryDatabase
    private lateinit var store: RoomAlbumStore

    // Non-ASCII, odd whitespace and a future version: any re-encode or trim would change it.
    private val unevaluatableRaw =
        " {\"version\":2,\"all\":[{\"type\":\"people\",\"ids\":[1,2]}],\"\u00e9\":\"\u2713\"}\n"

    // Evaluatable but not in canonical form, so a decode-then-encode rewrite would be visible.
    private val nonCanonicalRaw =
        "{ \"all\": [ { \"to\": \"2025-12-31\", \"type\": \"time\" } ], \"version\": 1 }\n"

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyGalleryDatabase::class.java
        ).allowMainThreadQueries().build()
        store = RoomAlbumStore(database = database, random = Random(90))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun rawRuleColumn(albumId: Long): String? {
        return database.openHelper.readableDatabase
            .query("SELECT rule FROM albums WHERE id = ?", arrayOf<Any>(albumId))
            .use { cursor ->
                check(cursor.moveToFirst()) { "No album row $albumId" }
                if (cursor.isNull(0)) null else cursor.getString(0)
            }
    }

    private suspend fun storedAlbum(albumId: Long): StoredAlbum {
        return store.observeAlbums().first().single { it.id == albumId }
    }

    private suspend fun assertRuleUntouched(albumId: Long, expectedRaw: String) {
        assertEquals(expectedRaw, rawRuleColumn(albumId))
        assertEquals(expectedRaw, storedAlbum(albumId).rule)
    }

    private suspend fun addMemberTo(albumId: Long, n: Int): Long {
        return store.addMember(
            albumId = albumId,
            mediaUri = "content://media/external/images/media/$n",
            displayName = "$n.jpg",
            size = n.toLong(),
            dateModified = n * 1_000L
        )
    }

    @Test
    fun `a rule round trips through the real rule column`() = runTest {
        val rule = AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Time(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
        )
        val encoded = AlbumRuleJson.encode(rule)

        val albumId = store.createAlbum(name = "Twenty twenty five", createdAt = 1L, rule = encoded)

        assertEquals(encoded, rawRuleColumn(albumId))
        assertEquals(rule, AlbumRuleJson.parse(storedAlbum(albumId).rule))
    }

    @Test
    fun `a null rule is still a manual album`() = runTest {
        val albumId = store.createAlbum(name = "Manual", createdAt = 2L)

        assertNull(rawRuleColumn(albumId))
        assertNull(storedAlbum(albumId).rule)
        assertNull(AlbumRuleJson.parse(storedAlbum(albumId).rule))
    }

    @Test
    fun `a null rule stays null after members are added and an exclusion is recorded`() = runTest {
        val albumId = store.createAlbum(name = "Manual", createdAt = 3L)

        addMemberTo(albumId, 1)
        store.recordExclusion(albumId, "content://media/external/images/media/2", "2.jpg", 2L, 2_000L)

        assertNull(rawRuleColumn(albumId))
        assertNull(AlbumRuleJson.parse(storedAlbum(albumId).rule))
    }

    @Test
    fun `an unevaluatable rule is read back as unevaluatable with the stored text`() = runTest {
        val albumId = store.createAlbum(name = "Future", createdAt = 4L, rule = unevaluatableRaw)

        val parsed = AlbumRuleJson.parse(storedAlbum(albumId).rule)

        assertNotNull(parsed)
        parsed as AlbumRule.Unevaluatable
        assertEquals(unevaluatableRaw, parsed.raw)
        assertEquals(UnevaluatableReason.UNKNOWN_VERSION, parsed.reason)
    }

    @Test
    fun `an unevaluatable rule is byte identical after rename`() = runTest {
        val albumId = store.createAlbum(name = "Future", createdAt = 5L, rule = unevaluatableRaw)

        store.renameAlbum(albumId, "Renamed")

        assertEquals("Renamed", storedAlbum(albumId).name)
        assertRuleUntouched(albumId, unevaluatableRaw)
    }

    @Test
    fun `an unevaluatable rule is byte identical after addMember assigns a cover`() = runTest {
        val albumId = store.createAlbum(name = "Future", createdAt = 6L, rule = unevaluatableRaw)

        val membershipId = addMemberTo(albumId, 1)

        assertEquals(membershipId, storedAlbum(albumId).coverMembershipId)
        assertRuleUntouched(albumId, unevaluatableRaw)
    }

    @Test
    fun `an unevaluatable rule is byte identical after removeMembership rerolls the cover`() = runTest {
        val albumId = store.createAlbum(name = "Future", createdAt = 7L, rule = unevaluatableRaw)
        val first = addMemberTo(albumId, 1)
        val second = addMemberTo(albumId, 2)
        val coverBefore = storedAlbum(albumId).coverMembershipId
        val survivor = if (coverBefore == first) second else first

        store.removeMembership(albumId, requireNotNull(coverBefore))

        assertNotEquals(coverBefore, storedAlbum(albumId).coverMembershipId)
        assertEquals(survivor, storedAlbum(albumId).coverMembershipId)
        assertRuleUntouched(albumId, unevaluatableRaw)
    }

    @Test
    fun `an unevaluatable rule is byte identical after recordExclusion`() = runTest {
        val albumId = store.createAlbum(name = "Future", createdAt = 8L, rule = unevaluatableRaw)

        store.recordExclusion(albumId, "content://media/external/images/media/9", "9.jpg", 9L, 9_000L)

        assertEquals(1, store.observeMembershipsForAlbum(albumId).first().size)
        assertRuleUntouched(albumId, unevaluatableRaw)
    }

    @Test
    fun `an evaluatable rule in non canonical form is not rewritten by any store operation`() = runTest {
        val albumId = store.createAlbum(name = "Until", createdAt = 9L, rule = nonCanonicalRaw)
        assertEquals(
            listOf(AlbumRuleClause.Time(null, LocalDate.of(2025, 12, 31))),
            (AlbumRuleJson.parse(nonCanonicalRaw) as AlbumRule.Evaluatable).clauses
        )

        store.renameAlbum(albumId, "Renamed")
        val first = addMemberTo(albumId, 1)
        addMemberTo(albumId, 2)
        store.removeMembership(albumId, first)
        store.recordExclusion(albumId, "content://media/external/images/media/3", "3.jpg", 3L, 3_000L)

        assertRuleUntouched(albumId, nonCanonicalRaw)
    }

    private suspend fun exerciseStore(albumId: Long) {
        store.renameAlbum(albumId, "Renamed")
        val first = addMemberTo(albumId, 1)
        addMemberTo(albumId, 2)
        store.removeMembership(albumId, first)
        store.recordExclusion(albumId, "content://media/external/images/media/3", "3.jpg", 3L, 3_000L)
    }

    @Test
    fun `a region rule is stored byte for byte in the real rule column`() = runTest {
        val rule = AlbumRule.Evaluatable(
            listOf(
                AlbumRuleClause.Time(LocalDate.of(2025, 1, 1), null),
                AlbumRuleClause.Region("IT", "16", "Tuscany \"Toscana\" \\ \u00e8 \uD83D\uDE00", "Florence")
            )
        )
        val encoded = AlbumRuleJson.encode(rule)

        val albumId = store.createAlbum(name = "Tuscany", createdAt = 10L, rule = encoded)

        assertEquals(encoded, rawRuleColumn(albumId))
        assertEquals(rule, AlbumRuleJson.parse(storedAlbum(albumId).rule))
    }

    @Test
    fun `region rules with hostile names are stored byte for byte`() = runTest {
        val names = listOf(
            "Quote\"Town",
            "back\\slash",
            "ctrl\u0000\u001f\n\t\u007f",
            "\u05E9\u05DC\u05D5\u05DD \u202E",
            "\uD83D\uDE00",
            "lone\uD800surrogate"
        )

        names.forEachIndexed { index, name ->
            val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT", "16", name, name)))
            val encoded = AlbumRuleJson.encode(rule)

            val albumId = store.createAlbum(name = "Hostile $index", createdAt = 20L + index, rule = encoded)

            assertEquals(name, encoded, rawRuleColumn(albumId))
            assertEquals(name, rule, AlbumRuleJson.parse(storedAlbum(albumId).rule))
        }
    }

    @Test
    fun `a region rule in non canonical form is not rewritten by any store operation`() = runTest {
        val raw = "{ \"all\": [ { \"place\": \"Singapore\", \"country\": \"SG\", \"type\": \"region\", " +
            "\"dataset\": 1 } ], \"version\": 1 }\n"
        val albumId = store.createAlbum(name = "Singapore", createdAt = 11L, rule = raw)
        assertEquals(
            listOf(AlbumRuleClause.Region(country = "SG", place = "Singapore")),
            (AlbumRuleJson.parse(raw) as AlbumRule.Evaluatable).clauses
        )

        exerciseStore(albumId)

        assertRuleUntouched(albumId, raw)
    }

    @Test
    fun `a region rule from another dataset is unevaluatable and byte identical after any store operation`() = runTest {
        val raw = "{\"version\":1,\"all\":[{\"type\":\"region\",\"dataset\":${PLACE_RESOLVER_VERSION + 1}," +
            "\"country\":\"IT\",\"admin1\":\"16\",\"admin1Name\":\"Tuscany\"}]}"
        val albumId = store.createAlbum(name = "Other dataset", createdAt = 12L, rule = raw)

        val parsed = AlbumRuleJson.parse(storedAlbum(albumId).rule)
        exerciseStore(albumId)

        parsed as AlbumRule.Unevaluatable
        assertEquals(raw, parsed.raw)
        assertEquals(UnevaluatableReason.UNKNOWN_DATASET, parsed.reason)
        assertRuleUntouched(albumId, raw)
    }
}
