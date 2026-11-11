package com.davide.seddio.easygallery.ui

import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.DateTakenSortState
import com.davide.seddio.easygallery.data.GazetteerNames
import com.davide.seddio.easygallery.data.JoinedMedia
import com.davide.seddio.easygallery.data.LocationState
import com.davide.seddio.easygallery.data.MediaIndexCoverage
import com.davide.seddio.easygallery.data.MediaIndexLiveJoin
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.MonthFacet
import com.davide.seddio.easygallery.data.PLACE_RESOLVER_VERSION
import com.davide.seddio.easygallery.data.PlaceFacet
import com.davide.seddio.easygallery.data.PlaceValues
import com.davide.seddio.easygallery.data.YearFacet
import io.mockk.mockk
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticAlbumPickerStateTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val join = MutableStateFlow<MediaIndexLiveJoin?>(null)
    private val existingNames = MutableStateFlow(listOf("Trips"))
    private var locale: Locale = Locale.US
    private var zone: ZoneId = ZoneId.of("UTC")
    private var locationGranted = true
    private var namesLoads = 0
    private var namesFailure: Exception? = null

    private val state = AutomaticAlbumPickerState(
        scope = scope,
        liveJoin = join,
        existingAlbumNames = existingNames,
        zoneProvider = { zone },
        localeProvider = { locale },
        locationAccess = { locationGranted },
        namesLoader = {
            namesLoads++
            namesFailure?.let { throw it }
            names()
        },
        workDispatcher = dispatcher
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    // --- selection and rule (pure) ----------------------------------------------------------------

    @Test
    fun `a year is the whole calendar year and a month ends on its last day`() {
        assertEquals(
            AlbumRuleClause.Time(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
            TimeSelection.Year(2025).toClause()
        )
        assertEquals(
            AlbumRuleClause.Time(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
            TimeSelection.Month(2024, 2).toClause()
        )
    }

    @Test
    fun `no clause is no rule and each choice adds its clause`() {
        val none = AutomaticAlbumSelection(browsedCountry = "IT", browsedYear = 2025)
        assertNull(none.toRule())

        val region = AlbumRuleClause.Region("IT")
        val both = none.copy(region = region, time = TimeSelection.Year(2025))
        assertEquals(
            AlbumRule.Evaluatable(listOf(region, TimeSelection.Year(2025).toClause())),
            both.toRule()
        )
        assertEquals(AlbumRule.Evaluatable(listOf(region)), none.copy(region = region).toRule())
    }

    // --- facets over the shared join --------------------------------------------------------------

    @Test
    fun `nothing is listed until the names are read and the live media is known`() = runTest {
        state.uiState.test {
            assertEquals(AutomaticAlbumPickerStatus.LOADING_NAMES, awaitItem().status)

            state.open("Album")
            awaitState { it.status == AutomaticAlbumPickerStatus.WAITING_FOR_MEDIA }

            join.value = libraryJoin()
            val ready = awaitState { it.status == AutomaticAlbumPickerStatus.READY }
            assertEquals(listOf("France", "Italy"), ready.content.countries.map { it.displayName })
            assertEquals(MediaIndexCoverage(withoutLocation = 1, withoutDate = 1), ready.coverage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `countries and years are counted over the join and sorted for display`() = runTest {
        val ui = readyState()

        assertEquals(
            listOf(CountryEntry("FR", "France", 1), CountryEntry("IT", "Italy", 4)),
            ui.content.countries
        )
        assertEquals(listOf(YearFacet(2025, 4), YearFacet(2024, 1)), ui.content.years)
        assertEquals(0, ui.content.matchCount)
    }

    @Test
    fun `a time choice filters the countries and a region choice filters the years`() = runTest {
        state.uiState.test {
            openWith(this)

            state.selectTime(TimeSelection.Year(2025))
            val timed = awaitState { it.selection.time != null }
            assertEquals(
                listOf(CountryEntry("FR", "France", 1), CountryEntry("IT", "Italy", 2)),
                timed.content.countries
            )

            state.selectTime(null)
            state.selectRegion(AlbumRuleClause.Region("IT"))
            val placed = awaitState { it.selection.region != null && it.selection.time == null }
            assertEquals(listOf(YearFacet(2025, 2), YearFacet(2024, 1)), placed.content.years)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `browsing a country lists its regions and a region lists its places`() = runTest {
        state.uiState.test {
            openWith(this)

            state.browseCountry("IT")
            val country = awaitState { it.content.browsedCountry != null }.content.browsedCountry!!
            assertEquals(4, country.allCount)
            assertEquals(listOf("Lombardy" to 1, "Tuscany" to 3), country.regions.map { it.admin1Name to it.count })

            state.browseRegion("16")
            val places = awaitState { it.content.browsedRegionPlaces.isNotEmpty() }.content.browsedRegionPlaces
            assertEquals(listOf(PlaceFacet("Florence", 2), PlaceFacet("Pisa", 1)), places)

            // Switching country closes the region list, which would otherwise name another country's region.
            state.browseCountry("FR")
            val switched = awaitState { it.selection.browsedCountry == "FR" }
            assertNull(switched.selection.browsedAdmin1)
            assertTrue(switched.content.browsedRegionPlaces.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `browsing a year lists its months`() = runTest {
        state.uiState.test {
            openWith(this)

            state.browseYear(2025)
            val months = awaitState { it.content.browsedYearMonths.isNotEmpty() }.content.browsedYearMonths
            assertEquals(listOf(MonthFacet(6, 4)), months)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the match count is what the combined choice would make members`() = runTest {
        state.uiState.test {
            openWith(this)

            state.selectRegion(AlbumRuleClause.Region("IT"))
            assertEquals(4, awaitState { it.selection.region != null }.content.matchCount)

            state.selectTime(TimeSelection.Month(2025, 6))
            assertEquals(2, awaitState { it.selection.time != null }.content.matchCount)

            state.selectRegion(AlbumRuleClause.Region("IT", "09", "Lombardy"))
            assertEquals(0, awaitState { it.selection.region?.admin1 != null }.content.matchCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the zone is read when the lists are computed`() = runTest {
        join.value = libraryJoin(lateOnJuneThirtieth = true)

        state.uiState.test {
            state.open("Album")
            state.selectTime(TimeSelection.Month(2025, 6))
            val utc = awaitState { it.selection.time != null && it.status == AutomaticAlbumPickerStatus.READY }
            assertEquals(2, utc.content.countries.single { it.countryCode == "IT" }.count)

            // Tokyo is nine hours ahead, which moves the 20:00Z item on 30 June into July.
            zone = ZoneId.of("Asia/Tokyo")
            join.value = libraryJoin(lateOnJuneThirtieth = true)
            val tokyo = awaitState { ui -> ui.content.countries.single { it.countryCode == "IT" }.count == 1 }
            assertEquals(1, tokyo.content.countries.single { it.countryCode == "IT" }.count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- creation ---------------------------------------------------------------------------------

    @Test
    fun `a draft can be created only with a clause and a free name`() = runTest {
        state.uiState.test {
            state.open("Italy")
            assertFalse(awaitState { it.name == "Italy" }.canCreate)

            state.selectRegion(AlbumRuleClause.Region("IT"))
            val named = awaitState { it.rule != null }
            assertTrue(named.canCreate)
            assertEquals(AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT"))), named.rule)

            state.setName("   ")
            assertFalse(awaitState { it.name.isBlank() }.canCreate)

            state.setName(" trips ")
            assertFalse(awaitState { it.name == " trips " }.canCreate)

            existingNames.value = emptyList()
            assertTrue(awaitState { it.canCreate }.canCreate)

            state.selectRegion(null)
            assertFalse(awaitState { it.rule == null }.canCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening again starts a fresh draft`() = runTest {
        state.uiState.test {
            state.open("First")
            state.selectRegion(AlbumRuleClause.Region("IT"))
            state.browseCountry("IT")
            awaitState { it.selection.region != null && it.selection.browsedCountry == "IT" }

            state.close()
            state.open("Second")
            val reopened = awaitState { it.name == "Second" && it.selection == AutomaticAlbumSelection() }
            assertNull(reopened.rule)
            assertFalse(reopened.canCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- names, locale, permission ---------------------------------------------------------------

    @Test
    fun `the names are read once, and only when the picker opens`() = runTest {
        state.uiState.test {
            awaitItem()
            assertEquals(0, namesLoads)

            state.open("One")
            awaitState { it.name == "One" }
            state.close()
            state.open("Two")
            awaitState { it.name == "Two" }

            assertEquals(1, namesLoads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a names failure is an error state and not an empty list, and retrying recovers`() = runTest {
        namesFailure = IOException("asset missing")
        join.value = libraryJoin()

        state.uiState.test {
            state.open("Album")
            val failed = awaitState { it.status == AutomaticAlbumPickerStatus.NAMES_FAILED }
            assertTrue(failed.content.countries.isEmpty())
            assertNull(failed.coverage)

            // A retry that fails again stays an error.
            state.retryLoadingNames()
            assertEquals(2, namesLoads)

            namesFailure = null
            state.retryLoadingNames()
            val ready = awaitState { it.status == AutomaticAlbumPickerStatus.READY }
            assertEquals(3, namesLoads)
            assertEquals(2, ready.content.countries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `country names follow the locale read at each open`() = runTest {
        join.value = libraryJoin()

        state.uiState.test {
            state.open("Album")
            assertEquals(
                listOf("France", "Italy"),
                awaitState { it.status == AutomaticAlbumPickerStatus.READY }.content.countries.map { it.displayName }
            )

            // A language change recreates the Activity; the next open must not reuse the old names.
            locale = Locale.ITALY
            state.close()
            state.open("Album")
            assertEquals(
                listOf("Francia", "Italia"),
                awaitState { ui -> ui.content.countries.any { it.displayName == "Italia" } }
                    .content.countries.map { it.displayName }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh re-resolves the country names and their order for a changed locale and keeps the draft`() = runTest {
        join.value = MediaIndexLiveJoin(
            listOf(
                joined(1, epoch("2025-06-10T12:00:00Z"), place("AT", null, "Vienna")),
                joined(2, epoch("2025-06-11T12:00:00Z"), place("DE", null, "Berlin"))
            )
        )

        state.uiState.test {
            state.open("Album")
            state.selectRegion(AlbumRuleClause.Region("AT"))
            val english = awaitState { it.status == AutomaticAlbumPickerStatus.READY && it.selection.region != null }
            assertEquals(listOf("Austria", "Germany"), english.content.countries.map { it.displayName })

            // A language change recreates the Activity, which leaves the draft alone and refreshes the lists.
            locale = Locale.GERMANY
            state.refresh()
            val german = awaitState { ui -> ui.content.countries.any { it.displayName == "Deutschland" } }

            assertEquals(listOf("Deutschland", "\u00D6sterreich"), german.content.countries.map { it.displayName })
            assertEquals(listOf("DE", "AT"), german.content.countries.map { it.countryCode })
            assertEquals(AlbumRuleClause.Region("AT"), german.selection.region)
            assertEquals("Album", german.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a draft name exists after open and not before it or after close`() {
        assertFalse(state.hasDraftName)

        state.open("Album")
        assertTrue(state.hasDraftName)

        state.close()
        assertFalse(state.hasDraftName)
    }

    @Test
    fun `location access is read when the picker opens`() = runTest {
        join.value = libraryJoin()
        locationGranted = false

        state.uiState.test {
            state.open("Album")
            assertFalse(awaitState { it.status == AutomaticAlbumPickerStatus.READY }.locationAccessGranted)

            locationGranted = true
            state.close()
            state.open("Album")
            assertTrue(awaitState { it.locationAccessGranted && it.status == AutomaticAlbumPickerStatus.READY }.locationAccessGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private suspend fun readyState(): AutomaticAlbumPickerUi {
        join.value = libraryJoin()
        var ui: AutomaticAlbumPickerUi? = null
        state.uiState.test {
            state.open("Album")
            ui = awaitState { it.status == AutomaticAlbumPickerStatus.READY }
            cancelAndIgnoreRemainingEvents()
        }
        return ui!!
    }

    private suspend fun openWith(turbine: ReceiveTurbine<AutomaticAlbumPickerUi>) {
        join.value = libraryJoin()
        state.open("Album")
        turbine.awaitState { it.status == AutomaticAlbumPickerStatus.READY }
    }

    // The flow can emit intermediate states, so a test awaits the one it means.
    private suspend fun ReceiveTurbine<AutomaticAlbumPickerUi>.awaitState(
        predicate: (AutomaticAlbumPickerUi) -> Boolean
    ): AutomaticAlbumPickerUi {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun names(): GazetteerNames {
        val header = "# dataset-version: $PLACE_RESOLVER_VERSION\n"
        return GazetteerNames.load(
            StringReader(header + "# rows-written: 3\nIT.16\tTuscany\nIT.09\tLombardy\nFR.11\tIle-de-France\n"),
            StringReader(header + "# rows-written: 2\nIT\tItaly\nFR\tFrance\n")
        )
    }

    private fun epoch(text: String): Long = Instant.parse(text).toEpochMilli()

    // Four dated Italian items in 2025 (one undated), one French, one unlocated, one out of 2025.
    private fun libraryJoin(lateOnJuneThirtieth: Boolean = false): MediaIndexLiveJoin {
        val lastJuneInstant = if (lateOnJuneThirtieth) "2025-06-30T20:00:00Z" else "2025-06-20T12:00:00Z"
        return MediaIndexLiveJoin(
            listOf(
                joined(1, epoch("2025-06-10T12:00:00Z"), place("IT", "16", "Florence")),
                joined(2, epoch(lastJuneInstant), place("IT", "16", "Pisa")),
                joined(3, epoch("2024-03-05T12:00:00Z"), place("IT", "09", "Milan")),
                joined(4, epoch("2025-06-12T12:00:00Z"), place("FR", "11", "Paris")),
                joined(5, null, place("IT", "16", "Florence")),
                joined(6, epoch("2025-06-01T12:00:00Z"), LocationState.NoCoordinates)
            )
        )
    }

    private fun place(country: String, region: String?, place: String?) =
        LocationState.Resolved(PlaceValues(country, region, place))

    private fun joined(id: Int, dateTaken: Long?, location: LocationState): JoinedMedia {
        val item = MediaItem(
            uri = mockk<Uri>(relaxed = true),
            name = "$id.jpg",
            dateAdded = 1L,
            dateModified = 1L,
            size = 1L,
            type = MediaType.IMAGE,
            bucketName = "Camera",
            folderPath = "/storage/emulated/0/DCIM/Camera"
        )
        val date = if (dateTaken == null) {
            DateTakenSortState.IndexedWithoutDate
        } else {
            DateTakenSortState.IndexedWithDate(dateTaken)
        }
        return JoinedMedia(item, date, location)
    }
}
