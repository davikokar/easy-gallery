package com.davide.seddio.easygallery.ui

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.LocaleList
import app.cash.turbine.test
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AlbumRuleJson
import com.davide.seddio.easygallery.data.AlbumStore
import com.davide.seddio.easygallery.data.FAVOURITES_OVERRIDE_KEY
import com.davide.seddio.easygallery.data.FolderViewOverrides
import com.davide.seddio.easygallery.data.FolderViewPreferencesStore
import com.davide.seddio.easygallery.data.GazetteerNames
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.InMemoryAlbumStore
import com.davide.seddio.easygallery.data.InMemoryFolderViewPreferencesStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexStore
import com.davide.seddio.easygallery.data.InMemoryMediaStoreVersionStore
import com.davide.seddio.easygallery.data.MediaIndexEntry
import com.davide.seddio.easygallery.data.MediaIndexEvaluationProjection
import com.davide.seddio.easygallery.data.MediaIndexLiveJoin
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaStoreVersionProvider
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.MembershipOrphanStateChange
import com.davide.seddio.easygallery.data.MembershipUriRewrite
import com.davide.seddio.easygallery.data.OverridablePreference
import com.davide.seddio.easygallery.data.PLACE_RESOLVER_VERSION
import com.davide.seddio.easygallery.data.PreferenceApplyTarget
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.StoredAlbum
import com.davide.seddio.easygallery.data.StoredMembership
import com.davide.seddio.easygallery.data.StoredMembershipKind
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.data.albumOverrideKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val application = mockk<Application>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { application.getString(com.davide.seddio.easygallery.R.string.album_name_blank) } returns "Album name cannot be blank"
        every { application.getString(com.davide.seddio.easygallery.R.string.album_name_duplicate) } returns "An album with this name already exists"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create rename and delete album`() = runTest {
        val viewModel = createViewModel()

        viewModel.albums.test {
            assertTrue(awaitItem().isEmpty())

            viewModel.createAlbum("Trips")
            val created = awaitItem()
            assertEquals(1, created.size)
            val albumId = created.single().id
            assertEquals("Trips", created.single().name)

            viewModel.renameAlbum(albumId, "Summer Trips")
            val renamed = awaitItem()
            assertEquals("Summer Trips", renamed.single().name)

            viewModel.deleteAlbum(albumId)
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate and blank names are rejected`() = runTest {
        val viewModel = createViewModel()

        viewModel.createAlbum("A")
        advanceUntilIdle()
        assertNull(viewModel.errorMessage.value)

        viewModel.createAlbum("   ")
        assertEquals("Album name cannot be blank", viewModel.errorMessage.value)

        viewModel.createAlbum("  a  ")
        assertEquals("An album with this name already exists", viewModel.errorMessage.value)
    }

    @Test
    fun `add and remove members`() = runTest {
        val viewModel = createViewModel()

        viewModel.createAlbum("Members")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(first, second))

        viewModel.addMembers(albumId, listOf(first, second))
        advanceUntilIdle()

        assertEquals(2, viewModel.albums.value.single().resolvedMemberCount)

        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val membershipIdToRemove = viewModel.selectedAlbumMembers.value.first().membershipId!!
        viewModel.removeMember(albumId, membershipIdToRemove)
        advanceUntilIdle()

        assertEquals(1, viewModel.albums.value.single().resolvedMemberCount)
        assertEquals(1, viewModel.selectedAlbumMembers.value.size)
    }

    @Test
    fun `an album created with members holds them without a second step`() = runTest {
        val viewModel = createViewModel()

        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(first, second))

        viewModel.createAlbum("Picked", listOf(first, second))
        advanceUntilIdle()

        assertEquals(2, viewModel.albums.value.single().resolvedMemberCount)
    }

    @Test
    fun `a rejected name creates no album and no members`() = runTest {
        val viewModel = createViewModel()

        val item = mediaItem("content://media/external/images/media/1", "1.jpg")
        viewModel.setAllMedia(listOf(item))

        val accepted = viewModel.createAlbum("   ", listOf(item))
        advanceUntilIdle()

        assertFalse(accepted)
        assertTrue(viewModel.albums.value.isEmpty())
        assertEquals("Album name cannot be blank", viewModel.errorMessage.value)
    }

    @Test
    fun `cover appears on first member and rerolls when that member is removed`() = runTest {
        val store = InMemoryAlbumStore(random = kotlin.random.Random(1))
        val viewModel = createViewModel(albumStore = store)

        viewModel.createAlbum("Cover")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val first = mediaItem("content://media/external/images/media/10", "10.jpg")
        val second = mediaItem("content://media/external/images/media/11", "11.jpg")
        viewModel.setAllMedia(listOf(first, second))

        viewModel.addMembers(albumId, listOf(first, second))
        advanceUntilIdle()

        val before = viewModel.albums.value.single()
        assertTrue(before.cover != null)

        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val coverMembershipId = viewModel.selectedAlbumMembers.value
            .first { it.mediaItem.uri.toString() == before.cover?.uri.toString() }
            .membershipId!!

        viewModel.removeMember(albumId, coverMembershipId)
        advanceUntilIdle()

        val after = viewModel.albums.value.single()
        assertTrue(after.cover != null)
        assertFalse(after.cover?.uri.toString() == before.cover?.uri.toString())
    }

    @Test
    fun `resolved members exclude orphans while stored count still includes them`() = runTest {
        val viewModel = createViewModel()

        viewModel.createAlbum("Orphans")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val existing = mediaItem("content://media/external/images/media/30", "30.jpg")
        val orphanSoon = mediaItem("content://media/external/images/media/31", "31.jpg")

        viewModel.setAllMedia(listOf(existing, orphanSoon))
        viewModel.addMembers(albumId, listOf(existing, orphanSoon))
        advanceUntilIdle()

        viewModel.setAllMedia(listOf(existing))
        advanceUntilIdle()

        val album = viewModel.albums.value.single()
        assertEquals(1, album.resolvedMemberCount)
        assertEquals(2, album.storedMemberCount)
        assertEquals(1, album.hiddenMemberCount)
    }

    @Test
    fun `healing write back runs once and does not repeat for identical second resolution`() = runTest {
        val countingStore = CountingAlbumStore()
        val viewModel = createViewModel(albumStore = countingStore)

        viewModel.createAlbum("Healing")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        val original = mediaItem("content://media/external/images/media/50", "50.jpg", size = 123L, dateModified = 999L)
        viewModel.setAllMedia(listOf(original))
        viewModel.addMembers(albumId, listOf(original))
        advanceUntilIdle()

        val healed = mediaItem("content://media/external/images/media/500", "50.jpg", size = 123L, dateModified = 999L)
        viewModel.setAllMedia(listOf(healed))
        advanceUntilIdle()

        val writesAfterFirstHeal = countingStore.writeBackCalls
        assertEquals(1, writesAfterFirstHeal)

        viewModel.setAllMedia(listOf(healed))
        advanceUntilIdle()

        assertEquals(writesAfterFirstHeal, countingStore.writeBackCalls)
    }

    @Test
    fun `suggestAlbumName uses localized format existing names and formatter wiring`() = runTest {
        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        val resources = mockk<Resources>(relaxed = true)
        val configuration = mockk<Configuration>(relaxed = true)
        val locales = mockk<LocaleList>()

        every { application.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.getString(any(), any()) } returns ""
        every { application.resources } returns resources
        every { resources.configuration } returns configuration
        every { configuration.locales } returns locales
        every { locales[0] } returns Locale.FRANCE
        every { application.getString(com.davide.seddio.easygallery.R.string.album_default_name) } returns "Album %d"

        val ordinals = mutableListOf<Int>()
        var capturedLocale: Locale? = null
        var capturedFormat: String? = null

        val viewModel = createViewModel(
            albumNameFormatter = { locale, format, ordinal ->
                capturedLocale = locale
                capturedFormat = format
                ordinals += ordinal
                String.format(locale, format, ordinal)
            }
        )

        viewModel.createAlbum("Album 1")
        viewModel.createAlbum("Album 2")
        advanceUntilIdle()

        val suggestion = viewModel.suggestAlbumName()

        assertEquals("Album 3", suggestion)
        assertEquals(Locale.FRANCE, capturedLocale)
        assertEquals("Album %d", capturedFormat)
        assertEquals(listOf(1, 2, 3, 3), ordinals)
    }

    // --- rule albums (ADR-0012, ADR-0017, ADR-0020) ---------------------------------------------

    @Test
    fun `an index store emission alone updates a rule album's members`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        val inJune = mediaItem("content://media/external/images/media/1", "1.jpg")
        val inJanuary = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(inJune, inJanuary))
        advanceUntilIdle()

        val before = viewModel.album(albumId)
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, before.status)
        assertEquals(0, before.resolvedMemberCount)

        indexStore.upsert(listOf(indexEntry(inJune, JUNE_15), indexEntry(inJanuary, JANUARY_15)))
        advanceUntilIdle()

        val after = viewModel.album(albumId)
        assertEquals(AlbumEvaluationStatus.COMPLETE, after.status)
        assertEquals(1, after.resolvedMemberCount)
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        assertEquals(listOf(inJune), viewModel.selectedAlbumMembers.value.map { it.mediaItem })
    }

    @Test
    fun `setAllMedia alone updates a rule album's members`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val inJune = mediaItem("content://media/external/images/media/1", "1.jpg")
        indexStore.upsert(listOf(indexEntry(inJune, JUNE_15)))
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)
        advanceUntilIdle()

        assertEquals(0, viewModel.album(albumId).resolvedMemberCount)

        viewModel.setAllMedia(listOf(inJune))
        advanceUntilIdle()

        val after = viewModel.album(albumId)
        assertEquals(1, after.resolvedMemberCount)
        assertEquals(AlbumEvaluationStatus.COMPLETE, after.status)
    }

    @Test
    fun `before live media is supplied a rule album is not complete`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val ruleAlbumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val manualAlbumId = albumStore.createAlbum("Manual", 1_000L, null)
        val unevaluatableAlbumId = albumStore.createAlbum("Future", 1_000L, UNKNOWN_VERSION_RULE)
        indexStore.upsert(listOf(indexEntry(mediaItem("content://media/external/images/media/1", "1.jpg"), JUNE_15)))
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)
        advanceUntilIdle()

        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, viewModel.album(ruleAlbumId).status)
        assertEquals(AlbumEvaluationStatus.MANUAL, viewModel.album(manualAlbumId).status)
        assertEquals(AlbumEvaluationStatus.UNEVALUATABLE, viewModel.album(unevaluatableAlbumId).status)
    }

    @Test
    fun `the zone is read at evaluation time and not cached`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("Last day", 1_000L, rule("2025-06-01", "2025-06-30"))
        val lateOnTheLastDay = mediaItem("content://media/external/images/media/1", "1.jpg")
        indexStore.upsert(listOf(indexEntry(lateOnTheLastDay, Instant.parse("2025-06-30T20:00:00Z").toEpochMilli())))
        var zone = ZoneId.of("UTC")
        val viewModel = createViewModel(
            albumStore = albumStore,
            mediaIndexStore = indexStore,
            zoneProvider = { zone }
        )

        viewModel.setAllMedia(listOf(lateOnTheLastDay))
        advanceUntilIdle()
        assertEquals(1, viewModel.album(albumId).resolvedMemberCount)

        zone = ZoneId.of("Asia/Tokyo")
        // An equal list would be conflated by the state flow, so the next read differs.
        val another = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(lateOnTheLastDay, another))
        advanceUntilIdle()

        assertEquals(0, viewModel.album(albumId).resolvedMemberCount)
    }

    @Test
    fun `an unevaluatable rule shows additions minus exclusions and is never rewritten`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val albumId = albumStore.createAlbum("Future", 1_000L, UNKNOWN_VERSION_RULE)
        val viewModel = createViewModel(albumStore = albumStore)

        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        val third = mediaItem("content://media/external/images/media/3", "3.jpg")
        val fourth = mediaItem("content://media/external/images/media/4", "4.jpg")
        viewModel.setAllMedia(listOf(first, second, third, fourth))
        viewModel.addMembers(albumId, listOf(first, second, third))
        advanceUntilIdle()
        albumStore.recordExclusion(albumId, third.uri.toString(), third.name, third.size, third.dateModified)
        advanceUntilIdle()

        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        assertEquals(AlbumEvaluationStatus.UNEVALUATABLE, viewModel.album(albumId).status)
        assertEquals(listOf(first, second), viewModel.selectedAlbumMembers.value.map { it.mediaItem })
        assertEquals(UNKNOWN_VERSION_RULE, albumStore.storedRule(albumId))

        viewModel.renameAlbum(albumId, "Renamed")
        viewModel.addMembers(albumId, listOf(fourth))
        viewModel.removeMembersByMedia(albumId, listOf(first))
        advanceUntilIdle()

        assertEquals(listOf(second, fourth), viewModel.selectedAlbumMembers.value.map { it.mediaItem })
        assertEquals(UNKNOWN_VERSION_RULE, albumStore.storedRule(albumId))
        assertEquals("Renamed", viewModel.album(albumId).name)
        // The removal records an exclusion and deletes the addition, as for an evaluatable rule.
        val afterRemoval = albumStore.memberships(albumId)
        assertEquals(
            setOf(third.uri.toString(), first.uri.toString()),
            afterRemoval.filter { it.kind == StoredMembershipKind.EXCLUSION }.map { it.mediaUri }.toSet()
        )
        // third's addition row was masked by a store-level exclusion above, never removed through the view model.
        assertEquals(
            setOf(second.uri.toString(), third.uri.toString(), fourth.uri.toString()),
            afterRemoval.filter { it.kind == StoredMembershipKind.ADDITION }.map { it.mediaUri }.toSet()
        )
    }

    @Test
    fun `removing an addition from an unevaluatable album by membership id records an exclusion`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val albumId = albumStore.createAlbum("Future", 1_000L, UNKNOWN_VERSION_RULE)
        val viewModel = createViewModel(albumStore = albumStore)

        val item = mediaItem("content://media/external/images/media/1", "1.jpg")
        viewModel.setAllMedia(listOf(item))
        viewModel.addMembers(albumId, listOf(item))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val additionId = viewModel.selectedAlbumMembers.value.single().membershipId!!

        viewModel.removeMember(albumId, additionId)
        advanceUntilIdle()

        assertTrue(viewModel.selectedAlbumMembers.value.isEmpty())
        val stored = albumStore.memberships(albumId)
        assertEquals(listOf(StoredMembershipKind.EXCLUSION), stored.map { it.kind })
        assertEquals(listOf(item.uri.toString()), stored.map { it.mediaUri })
        assertEquals(UNKNOWN_VERSION_RULE, albumStore.storedRule(albumId))
    }

    @Test
    fun `removing from a manual album records no exclusion`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val albumId = albumStore.createAlbum("Manual", 1_000L, null)
        val viewModel = createViewModel(albumStore = albumStore)

        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(first, second))
        viewModel.addMembers(albumId, listOf(first, second))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val firstAdditionId = viewModel.selectedAlbumMembers.value.first().membershipId!!

        viewModel.removeMember(albumId, firstAdditionId)
        viewModel.removeMembersByMedia(albumId, listOf(second))
        advanceUntilIdle()

        assertTrue(viewModel.selectedAlbumMembers.value.isEmpty())
        assertTrue(albumStore.memberships(albumId).isEmpty())
        assertNull(albumStore.storedRule(albumId))
    }

    @Test
    fun `removing a rule match records an exclusion and re-adding clears it`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        indexStore.upsert(listOf(indexEntry(first, JUNE_15), indexEntry(second, JUNE_15)))
        viewModel.setAllMedia(listOf(first, second))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        assertEquals(listOf(null, null), viewModel.selectedAlbumMembers.value.map { it.membershipId })

        viewModel.removeMembersByMedia(albumId, listOf(first))
        advanceUntilIdle()

        assertEquals(listOf(second), viewModel.selectedAlbumMembers.value.map { it.mediaItem })
        val afterRemoval = albumStore.memberships(albumId)
        assertEquals(listOf(first.uri.toString()), afterRemoval.map { it.mediaUri })
        assertEquals(listOf(StoredMembershipKind.EXCLUSION), afterRemoval.map { it.kind })

        viewModel.addMembers(albumId, listOf(first))
        advanceUntilIdle()

        assertEquals(listOf(first, second), viewModel.selectedAlbumMembers.value.map { it.mediaItem })
        assertTrue(albumStore.memberships(albumId).none { it.kind == StoredMembershipKind.EXCLUSION })
    }

    @Test
    fun `removing an added rule match records an exclusion and deletes the addition`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        val item = mediaItem("content://media/external/images/media/1", "1.jpg")
        indexStore.upsert(listOf(indexEntry(item, JUNE_15)))
        viewModel.setAllMedia(listOf(item))
        viewModel.addMembers(albumId, listOf(item))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val additionId = viewModel.selectedAlbumMembers.value.single().membershipId
        assertTrue(additionId != null)

        viewModel.removeMember(albumId, additionId!!)
        advanceUntilIdle()

        assertTrue(viewModel.selectedAlbumMembers.value.isEmpty())
        assertEquals(listOf(StoredMembershipKind.EXCLUSION), albumStore.memberships(albumId).map { it.kind })
    }

    @Test
    fun `hidden member count is additions only for a rule album and unchanged for a manual one`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val ruleAlbumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val manualAlbumId = albumStore.createAlbum("Manual", 1_000L, null)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        val matched = mediaItem("content://media/external/images/media/1", "1.jpg")
        val added = mediaItem("content://media/external/images/media/2", "2.jpg")
        val goneSoon = mediaItem("content://media/external/images/media/3", "3.jpg")
        indexStore.upsert(listOf(indexEntry(matched, JUNE_15)))
        viewModel.setAllMedia(listOf(matched, added, goneSoon))
        viewModel.addMembers(ruleAlbumId, listOf(added, goneSoon))
        viewModel.addMembers(manualAlbumId, listOf(added, goneSoon))
        advanceUntilIdle()
        viewModel.setAllMedia(listOf(matched, added))
        advanceUntilIdle()

        // Stored 2, resolved members 2: stored-minus-resolved would hide the missing one.
        val rule = viewModel.album(ruleAlbumId)
        assertEquals(2, rule.resolvedMemberCount)
        assertEquals(2, rule.storedMemberCount)
        assertEquals(1, rule.hiddenMemberCount)

        val manual = viewModel.album(manualAlbumId)
        assertEquals(1, manual.resolvedMemberCount)
        assertEquals(2, manual.storedMemberCount)
        assertEquals(1, manual.hiddenMemberCount)
    }

    @Test
    fun `a rule album without an addition cover shows its first member as a fallback cover`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        val item = mediaItem("content://media/external/images/media/1", "1.jpg")
        indexStore.upsert(listOf(indexEntry(item, JUNE_15)))
        viewModel.setAllMedia(listOf(item))
        advanceUntilIdle()

        val album = viewModel.album(albumId)
        assertEquals(item, album.cover)
        assertTrue(album.isCoverFallback)
    }

    // --- automatic albums: creation, region rules, the shared join (ADR-0016/0017 Amendment 2) -------------

    @Test
    fun `createAutomaticAlbum writes the encoded rule byte for byte and no membership rows`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = albumStore)
        val rule = AlbumRule.Evaluatable(
            listOf(
                AlbumRuleClause.Region("IT", "16", "Tuscany", "Florence"),
                AlbumRuleClause.Time(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30))
            )
        )

        val accepted = viewModel.createAutomaticAlbum("  Tuscany 2025 ", rule)
        advanceUntilIdle()

        assertTrue(accepted)
        val stored = albumStore.observeAlbums().first().single()
        assertEquals("Tuscany 2025", stored.name)
        assertEquals(
            "{\"version\":1,\"all\":[{\"type\":\"region\",\"dataset\":$PLACE_RESOLVER_VERSION,\"country\":\"IT\"," +
                "\"admin1\":\"16\",\"admin1Name\":\"Tuscany\",\"place\":\"Florence\"}," +
                "{\"type\":\"time\",\"from\":\"2025-06-01\",\"to\":\"2025-06-30\"}]}",
            stored.rule
        )
        assertTrue(albumStore.memberships(stored.id).isEmpty())
        assertEquals(AlbumRule.Evaluatable::class, AlbumRuleJson.parse(stored.rule)!!::class)
    }

    @Test
    fun `createAutomaticAlbum rejects a blank or duplicate name and writes nothing`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = albumStore)
        viewModel.createAlbum("Trips")
        advanceUntilIdle()

        val blank = viewModel.createAutomaticAlbum("   ", italyRule())
        assertEquals("Album name cannot be blank", viewModel.errorMessage.value)
        val duplicate = viewModel.createAutomaticAlbum(" trips ", italyRule())
        assertEquals("An album with this name already exists", viewModel.errorMessage.value)
        advanceUntilIdle()

        assertFalse(blank)
        assertFalse(duplicate)
        assertEquals(listOf("Trips"), albumStore.observeAlbums().first().map { it.name })
    }

    @Test
    fun `a region album moves from waiting to complete on an index store emission alone`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("Italy", 1_000L, italyRuleText())
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        val inItaly = mediaItem("content://media/external/images/media/1", "1.jpg")
        val inFrance = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(inItaly, inFrance))
        advanceUntilIdle()
        assertEquals(AlbumEvaluationStatus.WAITING_ON_INDEX, viewModel.album(albumId).status)
        assertEquals(0, viewModel.album(albumId).resolvedMemberCount)

        indexStore.upsert(
            listOf(
                placedEntry(inItaly, "IT", "16", "Florence", JUNE_15),
                placedEntry(inFrance, "FR", "11", "Paris", JUNE_15)
            )
        )
        advanceUntilIdle()

        val after = viewModel.album(albumId)
        assertEquals(AlbumEvaluationStatus.COMPLETE, after.status)
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        assertEquals(listOf(inItaly), viewModel.selectedAlbumMembers.value.map { it.mediaItem })
    }

    @Test
    fun `a time and region album holds only items matching both`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val rule = AlbumRule.Evaluatable(
            listOf(
                AlbumRuleClause.Region("IT"),
                AlbumRuleClause.Time(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30))
            )
        )
        val albumId = albumStore.createAlbum("Italy in June", 1_000L, AlbumRuleJson.encode(rule))
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        val matching = mediaItem("content://media/external/images/media/1", "1.jpg")
        val wrongMonth = mediaItem("content://media/external/images/media/2", "2.jpg")
        val wrongCountry = mediaItem("content://media/external/images/media/3", "3.jpg")
        indexStore.upsert(
            listOf(
                placedEntry(matching, "IT", "16", "Florence", JUNE_15),
                placedEntry(wrongMonth, "IT", "16", "Florence", JANUARY_15),
                placedEntry(wrongCountry, "FR", "11", "Paris", JUNE_15)
            )
        )
        viewModel.setAllMedia(listOf(matching, wrongMonth, wrongCountry))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()

        assertEquals(AlbumEvaluationStatus.COMPLETE, viewModel.album(albumId).status)
        assertEquals(listOf(matching), viewModel.selectedAlbumMembers.value.map { it.mediaItem })
    }

    @Test
    fun `the join is built once per emission whatever the number of albums`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        albumStore.createAlbum("Italy", 1_000L, italyRuleText())
        albumStore.createAlbum("France", 1_000L, AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("FR")))))
        albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val manualId = albumStore.createAlbum("Manual", 1_000L, null)
        var builds = 0
        val viewModel = createViewModel(
            albumStore = albumStore,
            mediaIndexStore = indexStore,
            joinFactory = { projection, live ->
                builds++
                MediaIndexLiveJoin.build(projection, live)
            }
        )

        val inItaly = mediaItem("content://media/external/images/media/1", "1.jpg")
        val inFrance = mediaItem("content://media/external/images/media/2", "2.jpg")
        viewModel.setAllMedia(listOf(inItaly, inFrance))
        advanceUntilIdle()
        assertEquals(1, builds)

        indexStore.upsert(
            listOf(
                placedEntry(inItaly, "IT", "16", "Florence", JUNE_15),
                placedEntry(inFrance, "FR", "11", "Paris", JANUARY_15)
            )
        )
        advanceUntilIdle()

        assertEquals(2, builds)
        assertEquals(listOf(1, 1, 1), viewModel.albums.value.filter { it.id != manualId }.map { it.resolvedMemberCount })

        viewModel.addMembers(manualId, listOf(inItaly))
        advanceUntilIdle()

        assertEquals(2, builds)
    }

    @Test
    fun `a rename does not subscribe to the index projection again or rebuild the join`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = CountingMediaIndexStore()
        val albumId = albumStore.createAlbum("Italy", 1_000L, italyRuleText())
        var builds = 0
        val viewModel = createViewModel(
            albumStore = albumStore,
            mediaIndexStore = indexStore,
            joinFactory = { projection, live ->
                builds++
                MediaIndexLiveJoin.build(projection, live)
            }
        )
        viewModel.setAllMedia(listOf(mediaItem("content://media/external/images/media/1", "1.jpg")))
        advanceUntilIdle()
        assertEquals(1, indexStore.projectionSubscriptions)
        assertEquals(1, builds)

        viewModel.renameAlbum(albumId, "Italia")
        advanceUntilIdle()
        viewModel.createAlbum("Another")
        advanceUntilIdle()

        assertEquals("Italia", viewModel.album(albumId).name)
        assertEquals(1, indexStore.projectionSubscriptions)
        assertEquals(1, builds)
    }

    @Test
    fun `the index is not read while no album has a rule`() = runTest {
        val indexStore = CountingMediaIndexStore()
        val viewModel = createViewModel(mediaIndexStore = indexStore)

        viewModel.createAlbum("Manual")
        viewModel.setAllMedia(listOf(mediaItem("content://media/external/images/media/1", "1.jpg")))
        advanceUntilIdle()

        assertEquals(0, indexStore.projectionSubscriptions)
    }

    @Test
    fun `the picker match count is the member count of the album created from it`() = runTest {
        val indexStore = InMemoryMediaIndexStore()
        val viewModel = createViewModel(mediaIndexStore = indexStore, gazetteerNamesLoader = { pickerNames() })
        val florenceJune = mediaItem("content://media/external/images/media/1", "1.jpg")
        val pisaJune = mediaItem("content://media/external/images/media/2", "2.jpg")
        val florenceJanuary = mediaItem("content://media/external/images/media/3", "3.jpg")
        val parisJune = mediaItem("content://media/external/images/media/4", "4.jpg")
        indexStore.upsert(
            listOf(
                placedEntry(florenceJune, "IT", "16", "Florence", JUNE_15),
                placedEntry(pisaJune, "IT", "16", "Pisa", JUNE_15),
                placedEntry(florenceJanuary, "IT", "16", "Florence", JANUARY_15),
                placedEntry(parisJune, "FR", "11", "Paris", JUNE_15)
            )
        )
        viewModel.setAllMedia(listOf(florenceJune, pisaJune, florenceJanuary, parisJune))
        val picker = viewModel.automaticAlbumPicker

        var created: AutomaticAlbumPickerUi? = null
        picker.uiState.test {
            picker.open("Italy in June")
            picker.selectRegion(AlbumRuleClause.Region("IT"))
            picker.selectTime(TimeSelection.Month(2025, 6))
            while (created == null) {
                val ui = awaitItem()
                if (ui.status == AutomaticAlbumPickerStatus.READY && ui.selection.time != null && ui.canCreate) created = ui
            }
            cancelAndIgnoreRemainingEvents()
        }
        val ui = created!!
        assertEquals(2, ui.content.matchCount)

        assertTrue(viewModel.createAutomaticAlbum(ui.name, ui.rule!!))
        advanceUntilIdle()

        val album = viewModel.albums.value.single()
        assertEquals(AlbumEvaluationStatus.COMPLETE, album.status)
        assertEquals(ui.content.matchCount, album.resolvedMemberCount)
        assertEquals(0, album.storedMembershipCount)
    }

    @Test
    fun `album selection starts empty and does not open Album Detail`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Trips")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id

        assertNull(viewModel.albumSelectionId.value)

        viewModel.enterAlbumSelection(albumId)

        assertEquals(albumId, viewModel.albumSelectionId.value)
        assertNull(viewModel.selectedAlbumId.value)
    }

    @Test
    fun `exitAlbumSelection clears the selected album`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Trips")
        advanceUntilIdle()
        viewModel.enterAlbumSelection(viewModel.albums.value.single().id)

        viewModel.exitAlbumSelection()

        assertNull(viewModel.albumSelectionId.value)
    }

    @Test
    fun `deleting the selected album clears album selection`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Trips")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id
        viewModel.enterAlbumSelection(albumId)

        viewModel.deleteAlbum(albumId)
        advanceUntilIdle()

        assertNull(viewModel.albumSelectionId.value)
    }

    @Test
    fun `deleting another album keeps album selection`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Trips")
        viewModel.createAlbum("Pets")
        advanceUntilIdle()
        val trips = viewModel.albums.value.first { it.name == "Trips" }.id
        val pets = viewModel.albums.value.first { it.name == "Pets" }.id
        viewModel.enterAlbumSelection(trips)

        viewModel.deleteAlbum(pets)
        advanceUntilIdle()

        assertEquals(trips, viewModel.albumSelectionId.value)
    }

    @Test
    fun `album selection clears when the album disappears from the store`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        viewModel.createAlbum("Trips")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id
        viewModel.enterAlbumSelection(albumId)

        store.deleteAlbum(albumId)
        advanceUntilIdle()

        assertNull(viewModel.albumSelectionId.value)
    }

    @Test
    fun `opening an album clears album selection`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Trips")
        viewModel.createAlbum("Pets")
        advanceUntilIdle()
        val trips = viewModel.albums.value.first { it.name == "Trips" }.id
        val pets = viewModel.albums.value.first { it.name == "Pets" }.id
        viewModel.enterAlbumSelection(trips)

        viewModel.selectAlbum(pets)

        assertNull(viewModel.albumSelectionId.value)
        assertEquals(pets, viewModel.selectedAlbumId.value)
    }

    @Test
    fun `leaving an album with null keeps album selection untouched`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Trips")
        advanceUntilIdle()
        val trips = viewModel.albums.value.single().id
        viewModel.enterAlbumSelection(trips)

        viewModel.selectAlbum(null)

        assertEquals(trips, viewModel.albumSelectionId.value)
        assertNull(viewModel.selectedAlbumId.value)
    }

    @Test
    fun `cancelAlbumSelectionAndDetail clears album selection and the open album`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Trips")
        viewModel.createAlbum("Pets")
        advanceUntilIdle()
        val trips = viewModel.albums.value.first { it.name == "Trips" }.id
        val pets = viewModel.albums.value.first { it.name == "Pets" }.id
        viewModel.selectAlbum(trips)
        viewModel.enterAlbumSelection(pets)

        viewModel.cancelAlbumSelectionAndDetail()

        assertNull(viewModel.albumSelectionId.value)
        assertNull(viewModel.selectedAlbumId.value)
    }

    @Test
    fun `a manual album counts its resolved members by media type`() = runTest {
        val viewModel = createViewModel()
        val photo = mediaItem("content://media/external/images/media/1", "1.jpg")
        val secondPhoto = mediaItem("content://media/external/images/media/2", "2.jpg")
        val clip = mediaItem("content://media/external/video/media/3", "3.mp4", type = MediaType.VIDEO)
        val animation = mediaItem("content://media/external/images/media/4", "4.gif", type = MediaType.GIF)
        viewModel.setAllMedia(listOf(photo, secondPhoto, clip, animation))

        viewModel.createAlbum("Mixed", listOf(photo, secondPhoto, clip, animation))
        advanceUntilIdle()

        val album = viewModel.albums.value.single()
        assertEquals(
            mapOf(MediaType.IMAGE to 2, MediaType.VIDEO to 1, MediaType.GIF to 1),
            album.mediaCountsByType
        )
        assertEquals(album.resolvedMemberCount, album.mediaCountsByType.values.sum())
    }

    @Test
    fun `an album with no members has a zero count for every media type`() = runTest {
        val viewModel = createViewModel()

        viewModel.createAlbum("Empty")
        advanceUntilIdle()

        assertEquals(MediaType.entries.associateWith { 0 }, viewModel.albums.value.single().mediaCountsByType)
    }

    @Test
    fun `an orphaned addition is not counted by media type`() = runTest {
        val viewModel = createViewModel()
        viewModel.createAlbum("Orphans")
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id
        val photo = mediaItem("content://media/external/images/media/1", "1.jpg")
        val clip = mediaItem("content://media/external/video/media/2", "2.mp4", type = MediaType.VIDEO)
        viewModel.setAllMedia(listOf(photo, clip))
        viewModel.addMembers(albumId, listOf(photo, clip))
        advanceUntilIdle()

        viewModel.setAllMedia(listOf(photo))
        advanceUntilIdle()

        assertEquals(1, viewModel.album(albumId).mediaCountsByType[MediaType.IMAGE])
        assertEquals(0, viewModel.album(albumId).mediaCountsByType[MediaType.VIDEO])
    }

    @Test
    fun `an automatic album counts its rule matches by media type`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val photo = mediaItem("content://media/external/images/media/1", "1.jpg")
        val clip = mediaItem("content://media/external/video/media/2", "2.mp4", type = MediaType.VIDEO)
        val januaryClip = mediaItem("content://media/external/video/media/3", "3.mp4", type = MediaType.VIDEO)
        indexStore.upsert(
            listOf(indexEntry(photo, JUNE_15), indexEntry(clip, JUNE_15), indexEntry(januaryClip, JANUARY_15))
        )
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)

        viewModel.setAllMedia(listOf(photo, clip, januaryClip))
        advanceUntilIdle()

        assertEquals(
            mapOf(MediaType.IMAGE to 1, MediaType.VIDEO to 1, MediaType.GIF to 0),
            viewModel.album(albumId).mediaCountsByType
        )
    }

    @Test
    fun `the selected album rule is null while no album is open`() = runTest {
        val albumStore = InMemoryAlbumStore()
        albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore)
        advanceUntilIdle()

        assertNull(viewModel.selectedAlbumRule.value)
    }

    @Test
    fun `the selected album rule follows the open album`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val manualId = albumStore.createAlbum("Manual", 1_000L, null)
        val juneId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val italyId = albumStore.createAlbum("Italy", 1_000L, italyRuleText())
        val futureId = albumStore.createAlbum("Future", 1_000L, UNKNOWN_VERSION_RULE)
        val viewModel = createViewModel(albumStore = albumStore)
        advanceUntilIdle()

        viewModel.selectAlbum(juneId)
        advanceUntilIdle()
        assertEquals(
            AlbumRule.Evaluatable(
                listOf(AlbumRuleClause.Time(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)))
            ),
            viewModel.selectedAlbumRule.value
        )

        viewModel.selectAlbum(italyId)
        advanceUntilIdle()
        assertEquals(italyRule(), viewModel.selectedAlbumRule.value)

        viewModel.selectAlbum(manualId)
        advanceUntilIdle()
        assertNull(viewModel.selectedAlbumRule.value)

        viewModel.selectAlbum(futureId)
        advanceUntilIdle()
        assertTrue(viewModel.selectedAlbumRule.value is AlbumRule.Unevaluatable)

        viewModel.selectAlbum(null)
        advanceUntilIdle()
        assertNull(viewModel.selectedAlbumRule.value)
    }

    @Test
    fun `the selected album rule is dropped when the open album is deleted`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val juneId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore)
        viewModel.selectAlbum(juneId)
        advanceUntilIdle()
        assertTrue(viewModel.selectedAlbumRule.value is AlbumRule.Evaluatable)

        viewModel.deleteAlbum(juneId)
        advanceUntilIdle()

        assertNull(viewModel.selectedAlbumRule.value)
    }

    private fun AlbumsViewModel.album(albumId: Long): AlbumListItem =
        albums.value.first { it.id == albumId }

    private fun pickerNames(): GazetteerNames {
        val header = "# dataset-version: $PLACE_RESOLVER_VERSION\n"
        return GazetteerNames.load(
            StringReader(header + "# rows-written: 2\nIT.16\tTuscany\nFR.11\tIle-de-France\n"),
            StringReader(header + "# rows-written: 2\nIT\tItaly\nFR\tFrance\n")
        )
    }

    private suspend fun AlbumStore.memberships(albumId: Long): List<StoredMembership> =
        observeMembershipsForAlbum(albumId).first()

    private suspend fun AlbumStore.storedRule(albumId: Long): String? =
        observeAlbums().first().first { it.id == albumId }.rule

    private fun indexEntry(item: MediaItem, dateTakenEpochMillis: Long?): MediaIndexEntry {
        return MediaIndexEntry(
            mediaUri = item.uri.toString(),
            displayName = item.name,
            size = item.size,
            dateModified = item.dateModified,
            volumeName = "external_primary",
            generationAdded = null,
            generationModified = null,
            capturedDateTakenEpochMillis = dateTakenEpochMillis,
            capturedLatitude = null,
            capturedLongitude = null,
            capturedWidth = null,
            capturedHeight = null,
            derivedCountryCode = null,
            derivedRegionCode = null,
            derivedPlaceName = null
        )
    }

    private fun rule(from: String, to: String): String {
        return AlbumRuleJson.encode(
            AlbumRule.Evaluatable(listOf(AlbumRuleClause.Time(LocalDate.parse(from), LocalDate.parse(to))))
        )
    }

    private fun italyRule() = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT")))

    private fun italyRuleText(): String = AlbumRuleJson.encode(italyRule())

    // A null country is a coordinate the current resolver found no place for: beyond the cap.
    private fun placedEntry(
        item: MediaItem,
        countryCode: String?,
        regionCode: String?,
        placeName: String?,
        dateTakenEpochMillis: Long?
    ): MediaIndexEntry {
        return indexEntry(item, dateTakenEpochMillis).copy(
            capturedLatitude = 43.7696,
            capturedLongitude = 11.2558,
            derivedCountryCode = countryCode,
            derivedRegionCode = regionCode,
            derivedPlaceName = placeName,
            derivedPlaceResolverVersion = PLACE_RESOLVER_VERSION
        )
    }

    @Test
    fun `album display overrides are seeded from the override store`() {
        val store = InMemoryFolderViewPreferencesStore().apply {
            save("12", FolderViewOverrides(columns = 5))
        }

        val viewModel = createViewModel(albumOverrideStore = store)

        assertEquals(mapOf("12" to FolderViewOverrides(columns = 5)), viewModel.albumDisplayOverrides.value)
    }

    @Test
    fun `this album commit changes only that key and does not write global`() {
        val store = InMemoryFolderViewPreferencesStore().apply {
            save("2", FolderViewOverrides(columns = 9))
            save(FAVOURITES_OVERRIDE_KEY, FolderViewOverrides(viewType = ViewType.LIST))
        }
        val viewModel = createViewModel(albumOverrideStore = store)
        var globalWrites = 0

        viewModel.commitAlbumSortPreference(
            albumKey = "1",
            sortType = SortType.SIZE,
            sortOrder = SortOrder.DESCENDING,
            applyTarget = PreferenceApplyTarget.CURRENT_FOLDER,
            writeGlobal = { globalWrites += 1 }
        )

        val expected = mapOf(
            "1" to FolderViewOverrides(sortType = SortType.SIZE, sortOrder = SortOrder.DESCENDING),
            "2" to FolderViewOverrides(columns = 9),
            FAVOURITES_OVERRIDE_KEY to FolderViewOverrides(viewType = ViewType.LIST)
        )
        assertEquals(expected, viewModel.albumDisplayOverrides.value)
        assertEquals(expected, store.loadAll())
        assertEquals(0, globalWrites)
    }

    @Test
    fun `this album commit of every group writes that groups override`() {
        val viewModel = createViewModel()
        val none = { }

        viewModel.commitAlbumSortPreference("1", SortType.NAME, SortOrder.DESCENDING, PreferenceApplyTarget.CURRENT_FOLDER, none)
        viewModel.commitAlbumColumnsPreference("1", 6, PreferenceApplyTarget.CURRENT_FOLDER, none)
        viewModel.commitAlbumGroupByPreference("1", GroupByType.FILE_TYPE, SortOrder.ASCENDING, PreferenceApplyTarget.CURRENT_FOLDER, none)
        viewModel.commitAlbumMediaTypesPreference("1", setOf(MediaType.VIDEO), PreferenceApplyTarget.CURRENT_FOLDER, none)
        viewModel.commitAlbumViewTypePreference("1", ViewType.LIST, PreferenceApplyTarget.CURRENT_FOLDER, none)

        val expected = FolderViewOverrides(
            sortType = SortType.NAME,
            sortOrder = SortOrder.DESCENDING,
            viewType = ViewType.LIST,
            columns = 6,
            groupBy = GroupByType.FILE_TYPE,
            groupOrder = SortOrder.ASCENDING,
            mediaTypes = setOf(MediaType.VIDEO)
        )
        assertEquals(mapOf("1" to expected), viewModel.albumDisplayOverrides.value)
    }

    @Test
    fun `all albums commit writes global first then clears only that group from every key`() {
        val store = InMemoryFolderViewPreferencesStore().apply {
            save("1", FolderViewOverrides(sortType = SortType.SIZE, sortOrder = SortOrder.DESCENDING, columns = 4))
            save("2", FolderViewOverrides(sortType = SortType.NAME, sortOrder = SortOrder.ASCENDING))
            save(FAVOURITES_OVERRIDE_KEY, FolderViewOverrides(sortType = SortType.RANDOM, viewType = ViewType.LIST))
        }
        val viewModel = createViewModel(albumOverrideStore = store)
        var overridesAtGlobalWrite: Map<String, FolderViewOverrides>? = null

        viewModel.commitAlbumSortPreference(
            albumKey = "1",
            sortType = SortType.DATE_TAKEN,
            sortOrder = SortOrder.ASCENDING,
            applyTarget = PreferenceApplyTarget.ALL_FOLDERS,
            writeGlobal = { overridesAtGlobalWrite = viewModel.albumDisplayOverrides.value }
        )

        // Still intact when the global write ran, so the global write happened first.
        assertEquals(3, overridesAtGlobalWrite?.size)
        assertEquals(SortType.SIZE, overridesAtGlobalWrite?.get("1")?.sortType)
        val expected = mapOf(
            "1" to FolderViewOverrides(columns = 4),
            FAVOURITES_OVERRIDE_KEY to FolderViewOverrides(viewType = ViewType.LIST)
        )
        assertEquals(expected, viewModel.albumDisplayOverrides.value)
        assertEquals(expected, store.loadAll())
    }

    @Test
    fun `all albums commit of each group clears exactly that group`() {
        val full = FolderViewOverrides(
            sortType = SortType.SIZE,
            sortOrder = SortOrder.DESCENDING,
            viewType = ViewType.LIST,
            columns = 6,
            groupBy = GroupByType.FILE_TYPE,
            groupOrder = SortOrder.ASCENDING,
            mediaTypes = setOf(MediaType.VIDEO)
        )
        val none = { }

        val commits: Map<OverridablePreference, (AlbumsViewModel) -> Unit> = mapOf(
            OverridablePreference.SORT to { vm ->
                vm.commitAlbumSortPreference("1", SortType.NAME, SortOrder.ASCENDING, PreferenceApplyTarget.ALL_FOLDERS, none)
            },
            OverridablePreference.COLUMNS to { vm ->
                vm.commitAlbumColumnsPreference("1", 3, PreferenceApplyTarget.ALL_FOLDERS, none)
            },
            OverridablePreference.GROUP_BY to { vm ->
                vm.commitAlbumGroupByPreference("1", GroupByType.NONE, SortOrder.ASCENDING, PreferenceApplyTarget.ALL_FOLDERS, none)
            },
            OverridablePreference.MEDIA_TYPES to { vm ->
                vm.commitAlbumMediaTypesPreference("1", setOf(MediaType.IMAGE), PreferenceApplyTarget.ALL_FOLDERS, none)
            },
            OverridablePreference.VIEW_TYPE to { vm ->
                vm.commitAlbumViewTypePreference("1", ViewType.GRID, PreferenceApplyTarget.ALL_FOLDERS, none)
            }
        )

        for ((group, commit) in commits) {
            val store = InMemoryFolderViewPreferencesStore().apply { save("1", full) }
            val viewModel = createViewModel(albumOverrideStore = store)

            commit(viewModel)

            assertEquals("group $group", mapOf("1" to full.cleared(group)), viewModel.albumDisplayOverrides.value)
        }
    }

    @Test
    fun `deleting an album removes only its override key`() = runTest {
        val store = InMemoryFolderViewPreferencesStore()
        val viewModel = createViewModel(albumOverrideStore = store)
        viewModel.createAlbum("Trips")
        viewModel.createAlbum("Docs")
        advanceUntilIdle()
        val trips = viewModel.albums.value.first { it.name == "Trips" }.id
        val docs = viewModel.albums.value.first { it.name == "Docs" }.id
        viewModel.setAlbumColumns(albumOverrideKey(trips), 5)
        viewModel.setAlbumColumns(albumOverrideKey(docs), 7)
        viewModel.setAlbumColumns(FAVOURITES_OVERRIDE_KEY, 8)

        viewModel.deleteAlbum(trips)
        advanceUntilIdle()

        val expected = mapOf(
            albumOverrideKey(docs) to FolderViewOverrides(columns = 7),
            FAVOURITES_OVERRIDE_KEY to FolderViewOverrides(columns = 8)
        )
        assertEquals(expected, viewModel.albumDisplayOverrides.value)
        assertEquals(expected, store.loadAll())
    }

    @Test
    fun `a pinch writes a per album columns override clamped to the valid range`() {
        val viewModel = createViewModel()

        viewModel.setAlbumColumns("1", 99)
        assertEquals(20, viewModel.albumDisplayOverrides.value["1"]?.columns)

        viewModel.setAlbumColumns("1", -3)
        assertEquals(1, viewModel.albumDisplayOverrides.value["1"]?.columns)

        viewModel.setAlbumColumns("1", 6)
        assertEquals(mapOf("1" to FolderViewOverrides(columns = 6)), viewModel.albumDisplayOverrides.value)
    }

    // --- album cover picker (ADR-0015 Amendment 1) ------------------------------------------------

    private suspend fun AlbumStore.coverMembershipId(albumId: Long): Long? =
        observeAlbums().first().first { it.id == albumId }.coverMembershipId

    // A manual album of [items] opened in Album Detail View, its cover pointed at [coverItem]'s row.
    private suspend fun TestScope.openManualAlbum(
        store: InMemoryAlbumStore,
        viewModel: AlbumsViewModel,
        items: List<MediaItem>,
        coverItem: MediaItem
    ): Long {
        viewModel.setAllMedia(items)
        viewModel.createAlbum("Cover", items)
        advanceUntilIdle()
        val albumId = viewModel.albums.value.single().id
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        val coverRow = viewModel.selectedAlbumMembers.value.first { it.mediaItem == coverItem }.membershipId!!
        store.setCover(albumId, coverRow)
        advanceUntilIdle()
        return albumId
    }

    @Test
    fun `entering the album cover picker does nothing with no album open`() = runTest {
        val viewModel = createViewModel()

        viewModel.enterAlbumCoverPicker()

        assertFalse(viewModel.isAlbumCoverPickerMode.value)
        assertNull(viewModel.albumCoverDraftUri.value)
        assertNull(viewModel.albumCoverPreselectedUri.value)
    }

    @Test
    fun `entering the album cover picker pre-selects the saved cover and drafts nothing`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        openManualAlbum(store, viewModel, listOf(first, second), coverItem = second)

        viewModel.enterAlbumCoverPicker()

        assertTrue(viewModel.isAlbumCoverPickerMode.value)
        assertEquals(second.uri, viewModel.albumCoverPreselectedUri.value)
        assertNull(viewModel.albumCoverDraftUri.value)
    }

    @Test
    fun `a rule album without a saved cover pre-selects nothing although it shows a fallback cover`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)
        val item = mediaItem("content://media/external/images/media/1", "1.jpg")
        indexStore.upsert(listOf(indexEntry(item, JUNE_15)))
        viewModel.setAllMedia(listOf(item))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        assertTrue(viewModel.album(albumId).isCoverFallback)

        viewModel.enterAlbumCoverPicker()

        assertTrue(viewModel.isAlbumCoverPickerMode.value)
        assertNull(viewModel.albumCoverPreselectedUri.value)
    }

    @Test
    fun `an orphaned saved cover is not pre-selected and a fallback never is`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        openManualAlbum(store, viewModel, listOf(first, second), coverItem = second)

        viewModel.setAllMedia(listOf(first))
        advanceUntilIdle()
        assertTrue(viewModel.album(viewModel.albums.value.single().id).isCoverFallback)

        viewModel.enterAlbumCoverPicker()

        assertNull(viewModel.albumCoverPreselectedUri.value)
    }

    @Test
    fun `a tap drafts the item and closing the album cover picker writes nothing`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        val albumId = openManualAlbum(store, viewModel, listOf(first, second), coverItem = first)
        val coverBefore = store.coverMembershipId(albumId)
        val rowsBefore = store.memberships(albumId)
        viewModel.enterAlbumCoverPicker()

        viewModel.setAlbumCoverDraft(second)
        viewModel.setAlbumCoverDraft(second)

        assertEquals(second.uri, viewModel.albumCoverDraftUri.value)
        assertTrue(viewModel.isAlbumCoverPickerMode.value)

        viewModel.exitAlbumCoverPicker()
        advanceUntilIdle()

        assertFalse(viewModel.isAlbumCoverPickerMode.value)
        assertNull(viewModel.albumCoverDraftUri.value)
        assertNull(viewModel.albumCoverPreselectedUri.value)
        assertEquals(coverBefore, store.coverMembershipId(albumId))
        assertEquals(rowsBefore, store.memberships(albumId))
    }

    @Test
    fun `a tap outside the album cover picker drafts nothing`() = runTest {
        val viewModel = createViewModel()

        viewModel.setAlbumCoverDraft(mediaItem("content://media/external/images/media/1", "1.jpg"))

        assertNull(viewModel.albumCoverDraftUri.value)
    }

    @Test
    fun `committing writes the drafted member's own row as the cover and leaves the picker`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        val albumId = openManualAlbum(store, viewModel, listOf(first, second), coverItem = first)
        val rowsBefore = store.memberships(albumId)
        val secondRow = viewModel.selectedAlbumMembers.value.first { it.mediaItem == second }.membershipId
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(second)

        viewModel.commitAlbumCoverDraft()
        advanceUntilIdle()

        assertEquals(secondRow, store.coverMembershipId(albumId))
        assertEquals(rowsBefore, store.memberships(albumId))
        assertFalse(viewModel.isAlbumCoverPickerMode.value)
        assertNull(viewModel.albumCoverDraftUri.value)
        assertNull(viewModel.albumCoverPreselectedUri.value)
        assertEquals(second, viewModel.album(albumId).cover)
        assertFalse(viewModel.album(albumId).isCoverFallback)
    }

    @Test
    fun `committing without a draft writes nothing and leaves the picker`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        val albumId = openManualAlbum(store, viewModel, listOf(first, second), coverItem = first)
        val coverBefore = store.coverMembershipId(albumId)
        viewModel.enterAlbumCoverPicker()

        viewModel.commitAlbumCoverDraft()
        advanceUntilIdle()

        assertEquals(coverBefore, store.coverMembershipId(albumId))
        assertFalse(viewModel.isAlbumCoverPickerMode.value)
    }

    @Test
    fun `committing a rule match saves one addition and the stored member count follows it`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg", size = 11L, dateModified = 21L)
        val second = mediaItem("content://media/external/images/media/2", "2.jpg", size = 12L, dateModified = 22L)
        indexStore.upsert(listOf(indexEntry(first, JUNE_15), indexEntry(second, JUNE_15)))
        viewModel.setAllMedia(listOf(first, second))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        assertEquals(0, viewModel.album(albumId).storedMemberCount)
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(second)

        viewModel.commitAlbumCoverDraft()
        advanceUntilIdle()

        val rows = albumStore.memberships(albumId)
        assertEquals(1, rows.size)
        assertEquals(StoredMembershipKind.ADDITION, rows.single().kind)
        assertEquals(second.uri.toString(), rows.single().mediaUri)
        assertEquals(listOf("2.jpg", 12L, 22L), listOf(rows.single().displayName, rows.single().size, rows.single().dateModified))
        assertEquals(rows.single().id, albumStore.coverMembershipId(albumId))

        // Still two members: the row is a no-op under the union. One stored addition, none hidden.
        val album = viewModel.album(albumId)
        assertEquals(2, album.resolvedMemberCount)
        assertEquals(1, album.storedMemberCount)
        assertEquals(0, album.hiddenMemberCount)
        assertEquals(second, album.cover)
        assertFalse(album.isCoverFallback)
        assertEquals(rows.single().id, viewModel.selectedAlbumMembers.value.first { it.mediaItem == second }.membershipId)
    }

    @Test
    fun `committing writes nothing when the drafted item is no longer shown in the album`() = runTest {
        val albumStore = InMemoryAlbumStore()
        val indexStore = InMemoryMediaIndexStore()
        val albumId = albumStore.createAlbum("June", 1_000L, JUNE_RULE)
        val viewModel = createViewModel(albumStore = albumStore, mediaIndexStore = indexStore)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        indexStore.upsert(listOf(indexEntry(first, JUNE_15), indexEntry(second, JUNE_15)))
        viewModel.setAllMedia(listOf(first, second))
        viewModel.selectAlbum(albumId)
        advanceUntilIdle()
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(second)

        viewModel.setAllMedia(listOf(first))
        advanceUntilIdle()
        viewModel.commitAlbumCoverDraft()
        advanceUntilIdle()

        assertTrue(albumStore.memberships(albumId).isEmpty())
        assertNull(albumStore.coverMembershipId(albumId))
        assertFalse(viewModel.isAlbumCoverPickerMode.value)
    }

    @Test
    fun `the album cover picker is cleared when another album is opened`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val second = mediaItem("content://media/external/images/media/2", "2.jpg")
        openManualAlbum(store, viewModel, listOf(first, second), coverItem = first)
        viewModel.createAlbum("Other")
        advanceUntilIdle()
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(second)

        viewModel.selectAlbum(viewModel.albums.value.first { it.name == "Other" }.id)

        assertCoverPickerCleared(viewModel)
    }

    @Test
    fun `the album cover picker is cleared when the open album is left`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        openManualAlbum(store, viewModel, listOf(first), coverItem = first)
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(first)

        viewModel.selectAlbum(null)

        assertCoverPickerCleared(viewModel)
    }

    @Test
    fun `the album cover picker is cleared by a main view tab tap`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        openManualAlbum(store, viewModel, listOf(first), coverItem = first)
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(first)

        viewModel.cancelAlbumSelectionAndDetail()

        assertCoverPickerCleared(viewModel)
    }

    @Test
    fun `the album cover picker is cleared when the open album is deleted`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val albumId = openManualAlbum(store, viewModel, listOf(first), coverItem = first)
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(first)

        viewModel.deleteAlbum(albumId)
        advanceUntilIdle()

        assertCoverPickerCleared(viewModel)
    }

    @Test
    fun `the album cover picker is cleared when the open album vanishes from the store`() = runTest {
        val store = InMemoryAlbumStore()
        val viewModel = createViewModel(albumStore = store)
        val first = mediaItem("content://media/external/images/media/1", "1.jpg")
        val albumId = openManualAlbum(store, viewModel, listOf(first), coverItem = first)
        viewModel.enterAlbumCoverPicker()
        viewModel.setAlbumCoverDraft(first)

        store.deleteAlbum(albumId)
        advanceUntilIdle()

        assertCoverPickerCleared(viewModel)
    }

    private fun assertCoverPickerCleared(viewModel: AlbumsViewModel) {
        assertFalse(viewModel.isAlbumCoverPickerMode.value)
        assertNull(viewModel.albumCoverDraftUri.value)
        assertNull(viewModel.albumCoverPreselectedUri.value)
    }

    private fun createViewModel(
        albumStore: AlbumStore = InMemoryAlbumStore(),
        albumNameFormatter: (Locale, String, Int) -> String = { locale, format, ordinal ->
            String.format(locale, format, ordinal)
        },
        mediaIndexStore: MediaIndexStore = InMemoryMediaIndexStore(),
        zoneProvider: () -> ZoneId = { ZoneId.of("UTC") },
        joinFactory: (List<MediaIndexEvaluationProjection>, List<MediaItem>) -> MediaIndexLiveJoin =
            { projection, live -> MediaIndexLiveJoin.build(projection, live) },
        gazetteerNamesLoader: () -> GazetteerNames = { error("this test does not open the picker") },
        albumOverrideStore: FolderViewPreferencesStore = InMemoryFolderViewPreferencesStore()
    ): AlbumsViewModel {
        return AlbumsViewModel(
            application = application,
            albumStore = albumStore,
            nowProvider = { 1_000L },
            albumNameFormatter = albumNameFormatter,
            mediaStoreVersionStore = InMemoryMediaStoreVersionStore(),
            mediaStoreVersionProvider = FixedMediaStoreVersionProvider("v1"),
            mediaIndexStore = mediaIndexStore,
            zoneProvider = zoneProvider,
            evaluationDispatcher = dispatcher,
            joinFactory = joinFactory,
            gazetteerNamesLoader = gazetteerNamesLoader,
            localeProvider = { Locale.US },
            albumOverrideStore = albumOverrideStore
        )
    }

    private fun mediaItem(
        uri: String,
        name: String,
        size: Long = 1L,
        dateModified: Long = 1L,
        type: MediaType = MediaType.IMAGE
    ): MediaItem {
        val uriMock = mockk<Uri>()
        every { uriMock.toString() } returns uri

        return MediaItem(
            uri = uriMock,
            name = name,
            dateAdded = dateModified,
            dateModified = dateModified,
            size = size,
            type = type,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
    }

    private companion object {
        val JUNE_15: Long = Instant.parse("2025-06-15T12:00:00Z").toEpochMilli()
        val JANUARY_15: Long = Instant.parse("2025-01-15T12:00:00Z").toEpochMilli()
        val JUNE_RULE: String = AlbumRuleJson.encode(
            AlbumRule.Evaluatable(
                listOf(AlbumRuleClause.Time(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)))
            )
        )

        // Written by a future build; this build must show it as unevaluatable and leave it alone.
        const val UNKNOWN_VERSION_RULE = "{\"version\":2,\"all\":[{\"type\":\"time\",\"from\":\"2025-06-01\"}]}"
    }

    private class FixedMediaStoreVersionProvider(
        private val value: String?
    ) : MediaStoreVersionProvider {
        override fun currentVersion(context: android.content.Context): String? = value
    }

    private class CountingMediaIndexStore(
        private val delegate: MediaIndexStore = InMemoryMediaIndexStore()
    ) : MediaIndexStore by delegate {
        var projectionSubscriptions: Int = 0
            private set

        override fun observeEvaluationProjection(): Flow<List<MediaIndexEvaluationProjection>> {
            return delegate.observeEvaluationProjection().onStart { projectionSubscriptions += 1 }
        }
    }

    private class CountingAlbumStore(
        private val delegate: AlbumStore = InMemoryAlbumStore()
    ) : AlbumStore by delegate {
        var writeBackCalls: Int = 0
            private set

        var orphanUpdateCalls: Int = 0
            private set

        override suspend fun writeBackHealedUris(rewrites: List<MembershipUriRewrite>) {
            if (rewrites.isNotEmpty()) {
                writeBackCalls += 1
            }
            delegate.writeBackHealedUris(rewrites)
        }

        override suspend fun updateOrphanStates(changes: List<MembershipOrphanStateChange>) {
            if (changes.isNotEmpty()) {
                orphanUpdateCalls += 1
            }
            delegate.updateOrphanStates(changes)
        }

        override fun observeAlbums(): Flow<List<StoredAlbum>> = delegate.observeAlbums()

        override fun observeMembershipsForAlbum(albumId: Long): Flow<List<StoredMembership>> {
            return delegate.observeMembershipsForAlbum(albumId)
        }
    }
}