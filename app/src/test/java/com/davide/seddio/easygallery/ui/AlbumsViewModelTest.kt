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
import com.davide.seddio.easygallery.data.InMemoryAlbumStore
import com.davide.seddio.easygallery.data.InMemoryMediaIndexStore
import com.davide.seddio.easygallery.data.InMemoryMediaStoreVersionStore
import com.davide.seddio.easygallery.data.MediaIndexEntry
import com.davide.seddio.easygallery.data.MediaIndexStore
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaStoreVersionProvider
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.MembershipOrphanStateChange
import com.davide.seddio.easygallery.data.MembershipUriRewrite
import com.davide.seddio.easygallery.data.StoredAlbum
import com.davide.seddio.easygallery.data.StoredMembership
import com.davide.seddio.easygallery.data.StoredMembershipKind
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private fun AlbumsViewModel.album(albumId: Long): AlbumListItem =
        albums.value.first { it.id == albumId }

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

    private fun createViewModel(
        albumStore: AlbumStore = InMemoryAlbumStore(),
        albumNameFormatter: (Locale, String, Int) -> String = { locale, format, ordinal ->
            String.format(locale, format, ordinal)
        },
        mediaIndexStore: MediaIndexStore = InMemoryMediaIndexStore(),
        zoneProvider: () -> ZoneId = { ZoneId.of("UTC") }
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
            evaluationDispatcher = dispatcher
        )
    }

    private fun mediaItem(
        uri: String,
        name: String,
        size: Long = 1L,
        dateModified: Long = 1L
    ): MediaItem {
        val uriMock = mockk<Uri>()
        every { uriMock.toString() } returns uri

        return MediaItem(
            uri = uriMock,
            name = name,
            dateAdded = dateModified,
            dateModified = dateModified,
            size = size,
            type = MediaType.IMAGE,
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