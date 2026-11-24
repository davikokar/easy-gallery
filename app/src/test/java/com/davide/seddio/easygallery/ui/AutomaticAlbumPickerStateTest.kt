package com.davide.seddio.easygallery.ui

import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AlbumRuleEvaluator
import com.davide.seddio.easygallery.data.DateTakenSortState
import com.davide.seddio.easygallery.data.FACE_GROUPING_VERSION
import com.davide.seddio.easygallery.data.FACE_PIPELINE_VERSION
import com.davide.seddio.easygallery.data.FaceBox
import com.davide.seddio.easygallery.data.FaceCapabilityState
import com.davide.seddio.easygallery.data.FaceEvaluationFace
import com.davide.seddio.easygallery.data.FaceEvaluationImage
import com.davide.seddio.easygallery.data.FaceEvaluationSnapshot
import com.davide.seddio.easygallery.data.FaceGroupRepresentative
import com.davide.seddio.easygallery.data.FaceGroupSummary
import com.davide.seddio.easygallery.data.FaceIndexOutcome
import com.davide.seddio.easygallery.data.GazetteerNames
import com.davide.seddio.easygallery.data.JoinedMedia
import com.davide.seddio.easygallery.data.LocationState
import com.davide.seddio.easygallery.data.MediaIndexLiveJoin
import com.davide.seddio.easygallery.data.MediaIndexEvaluationProjection
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.MonthFacet
import com.davide.seddio.easygallery.data.PLACE_RESOLVER_VERSION
import com.davide.seddio.easygallery.data.PeopleAvailability
import com.davide.seddio.easygallery.data.PeopleEvaluationInput
import com.davide.seddio.easygallery.data.PlaceFacet
import com.davide.seddio.easygallery.data.PlaceValues
import com.davide.seddio.easygallery.data.YearFacet
import com.davide.seddio.easygallery.data.toMediaIdentity
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    private var namesLoads = 0
    private var namesFailure: Exception? = null

    // The People tab's three inputs. A person is offered by default; each test that needs otherwise says so.
    private val peopleOffered = MutableStateFlow(true)
    private val faceSource = MutableStateFlow(FaceEvaluationSnapshot(emptyList()))
    private var faceSubscriptions = 0
    private var activeFaceSubscriptions = 0
    private var peopleLoads = 0
    private var peopleResult: List<FaceGroupSummary> = emptyList()
    private var peopleFailure: Exception? = null
    private var peopleGate: CompletableDeferred<Unit>? = null

    private val state = stateWith(
        faceSource
            .onStart {
                faceSubscriptions++
                activeFaceSubscriptions++
            }
            .onCompletion { activeFaceSubscriptions-- }
    )

    // [faceSnapshots] is the one input a test sometimes holds back, to look at the state before the counts catch up.
    private fun stateWith(faceSnapshots: Flow<FaceEvaluationSnapshot>) = AutomaticAlbumPickerState(
        scope = scope,
        liveJoin = join,
        existingAlbumNames = existingNames,
        zoneProvider = { zone },
        localeProvider = { locale },
        namesLoader = {
            namesLoads++
            namesFailure?.let { throw it }
            names()
        },
        workDispatcher = dispatcher,
        peopleOffered = peopleOffered,
        faceSnapshots = faceSnapshots,
        peopleLoader = {
            peopleLoads++
            peopleGate?.await()
            peopleFailure?.let { throw it }
            peopleResult
        }
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

    // --- names, locale ---------------------------------------------------------------

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

    // --- the People tab: offered, loaded, frozen, dropped (plan 3.6) ---------------------------------

    @Test
    fun `People is offered only with face data on and a capable record for the current key`() {
        val capable = FaceCapabilityState("key", capable = true, unfinishedStarts = 0)

        assertTrue(isPeoplePickerOffered(true, capable, "key"))
        assertFalse(isPeoplePickerOffered(false, capable, "key"))
        assertFalse(isPeoplePickerOffered(true, capable.copy(capable = false), "key"))
        // Unknown is available to evaluation and hidden here, on purpose (ADR-0024 Amendment 1 section 5).
        assertFalse(isPeoplePickerOffered(true, capable.copy(capable = null), "key"))
        assertFalse(isPeoplePickerOffered(true, capable, "another key"))
        assertFalse(isPeoplePickerOffered(true, null, "key"))
    }

    @Test
    fun `a restored People tab is Place while People is not offered`() {
        assertEquals(AUTOMATIC_ALBUM_TAB_PLACE, coerceAutomaticAlbumTab(AUTOMATIC_ALBUM_TAB_PEOPLE, peopleOffered = false))
        assertEquals(AUTOMATIC_ALBUM_TAB_PEOPLE, coerceAutomaticAlbumTab(AUTOMATIC_ALBUM_TAB_PEOPLE, peopleOffered = true))
        assertEquals(AUTOMATIC_ALBUM_TAB_PLACE, coerceAutomaticAlbumTab(AUTOMATIC_ALBUM_TAB_PLACE, peopleOffered = false))
        assertEquals(AUTOMATIC_ALBUM_TAB_DATE, coerceAutomaticAlbumTab(AUTOMATIC_ALBUM_TAB_DATE, peopleOffered = false))
        assertEquals(AUTOMATIC_ALBUM_TAB_DATE, coerceAutomaticAlbumTab(AUTOMATIC_ALBUM_TAB_DATE, peopleOffered = true))
    }

    @Test
    fun `the tab constants are the plan's order and a person needs two photos`() {
        assertEquals(listOf(0, 1, 2), listOf(AUTOMATIC_ALBUM_TAB_PLACE, AUTOMATIC_ALBUM_TAB_DATE, AUTOMATIC_ALBUM_TAB_PEOPLE))
        assertEquals(8, MAX_PEOPLE_PER_ALBUM)
        assertEquals(2, PEOPLE_PICKER_MINIMUM_IMAGES)
    }

    @Test
    fun `when People is not offered nothing loads and nobody can be chosen`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed
        peopleOffered.value = false

        state.uiState.test {
            openWith(this, library)
            state.revealPeople()
            state.retryLoadingPeople()

            assertFalse(state.togglePerson(library.personA))
            val ui = state.uiState.value
            assertEquals(AutomaticAlbumPickerStatus.READY, ui.status)
            assertEquals(PeoplePickerUi(), ui.people)
            assertEquals(0, peopleLoads)
            assertEquals(0, faceSubscriptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the people list loads once at the first reveal, in the query's order, and a later reveal changes nothing`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed
        peopleGate = CompletableDeferred()

        state.uiState.test {
            openWith(this, library)
            val idle = state.uiState.value
            assertTrue(idle.people.offered)
            assertEquals(PeopleListStatus.NOT_LOADED, idle.people.status)
            assertEquals(0, peopleLoads)

            state.revealPeople()
            assertEquals(PeopleListStatus.LOADING, awaitState { it.people.status == PeopleListStatus.LOADING }.people.status)
            peopleGate!!.complete(Unit)

            val ready = awaitState { it.people.status == PeopleListStatus.READY }
            // The order is the query's: the list is neither sorted by group id nor by anything else.
            assertEquals(library.listed, ready.people.groups)
            assertEquals(listOf(200L, 100L), ready.people.groups.take(2).map { it.groupId })

            state.revealPeople()
            assertEquals(1, peopleLoads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a query that returns nothing is Empty and one that throws is Failed until Retry`() = runTest {
        val library = peopleLibrary()
        peopleResult = emptyList()

        state.uiState.test {
            openWith(this, library)
            state.revealPeople()
            assertEquals(PeopleListStatus.EMPTY, awaitState { it.people.status == PeopleListStatus.EMPTY }.people.status)

            // Empty is not Failed, and Retry does nothing for it.
            state.retryLoadingPeople()
            assertEquals(1, peopleLoads)

            state.close()
            state.open("Album")
            peopleFailure = IOException("query failed")
            state.revealPeople()
            awaitState { it.people.status == PeopleListStatus.FAILED }
            assertEquals(2, peopleLoads)

            peopleFailure = null
            peopleResult = library.listed
            state.retryLoadingPeople()
            val ready = awaitState { it.people.status == PeopleListStatus.READY }
            assertEquals(3, peopleLoads)
            assertEquals(library.listed, ready.people.groups)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a person is chosen and removed, the order chosen is kept, and the chip counts them`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)

            assertTrue(state.togglePerson(library.personB))
            assertTrue(state.togglePerson(library.personA))
            val both = awaitState { it.people.chosenCount == 2 }
            // Chosen order is neither the list's order nor group-id order.
            assertEquals(listOf(library.personB, library.personA), both.people.chosen)
            assertEquals(listOf(library.personB, library.personA), both.selection.people)
            assertTrue(both.people.isChosen(library.personA))
            assertFalse(both.people.isChosen(library.others.first()))

            assertTrue(state.togglePerson(library.personB))
            val onlyA = awaitState { it.people.chosenCount == 1 }
            assertEquals(listOf(library.personA), onlyA.people.chosen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the rule holds one people clause per chosen person, each anchored at the face that was listed`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)

            state.togglePerson(library.personB)
            state.togglePerson(library.personA)
            val ui = awaitState { it.people.chosenCount == 2 }

            val clauses = ui.rule!!.clauses.filterIsInstance<AlbumRuleClause.People>()
            assertEquals(2, clauses.size)
            assertEquals(2, ui.rule!!.clauses.size)
            assertEquals(listOf(library.personB, library.personA).map { AlbumRuleClause.People.anchoredAt(it.representative) }, clauses)
            // Bit for bit: the identity, the box and the version of the representative, and nothing of the group.
            for ((clause, person) in clauses.zip(listOf(library.personB, library.personA))) {
                assertEquals(person.representative.identity, clause.anchor)
                assertEquals(person.representative.box, clause.box)
                assertEquals(person.representative.pipelineVersion, clause.pipelineVersion)
                assertTrue(person.groupId.toString() != clause.anchor.mediaUri)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a summary the list no longer shows as the tapped one is refused, whatever its group id says`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)

            val stranger = library.personA.copy(groupId = 999L)
            assertFalse(state.togglePerson(stranger))

            // The same group offered with another face is not the tile the user tapped, so nothing is chosen.
            val lookalike = library.personA.copy(representative = library.personB.representative)
            assertFalse(state.togglePerson(lookalike))
            assertTrue(state.uiState.value.people.chosen.isEmpty())

            assertTrue(state.togglePerson(library.personA))
            val ui = awaitState { it.people.chosenCount == 1 }
            assertSame(library.listed.first { it.groupId == library.personA.groupId }, ui.people.chosen.single())
            assertEquals(library.personA.representative, ui.people.chosen.single().representative)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `after a reload gives a group id another face, the old tile is refused and the new one is chosen`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)

            val regrouped = library.personA.copy(representative = library.personB.representative)
            peopleResult = library.listed.map { if (it.groupId == library.personA.groupId) regrouped else it }
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            awaitState { regrouped in it.people.groups }

            val before = state.uiState.value
            assertFalse(state.togglePerson(library.personA))
            assertEquals(before, state.uiState.value)

            assertTrue(state.togglePerson(regrouped))
            assertEquals(listOf(regrouped), awaitState { it.people.chosenCount == 1 }.people.chosen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the tiles, the cap and the chip follow a choice at once, before the counts that follow it`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed
        val faces = MutableSharedFlow<FaceEvaluationSnapshot>(replay = 1)
        val gated = stateWith(faces)

        gated.uiState.test {
            join.value = library.join
            gated.open("Album")
            awaitState { it.status == AutomaticAlbumPickerStatus.READY }
            gated.revealPeople()
            val before = awaitState { it.people.status == PeopleListStatus.READY }

            // The face projection is held back, so no snapshot that includes a person can exist yet.
            assertTrue(gated.togglePerson(library.personA))
            val one = awaitState { it.people.chosenCount == 1 }
            assertTrue(one.people.isChosen(library.personA))
            assertEquals(listOf(library.personA), one.selection.people)
            assertEquals(listOf(library.clauseA), one.rule!!.clauses)
            assertEquals(before.content, one.content)

            for (person in library.listed.take(MAX_PEOPLE_PER_ALBUM)) {
                if (person != library.personA) assertTrue(gated.togglePerson(person))
            }
            val full = awaitState { it.people.chosenCount == MAX_PEOPLE_PER_ALBUM }
            val ninth = library.listed[MAX_PEOPLE_PER_ALBUM]
            assertTrue(full.people.atCap)
            assertTrue(full.people.isDisabled(ninth))
            assertFalse(gated.togglePerson(ninth))
            assertEquals(before.content, full.content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the first person chosen keeps the previous counts and lists until the face projection arrives`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed
        val faces = MutableSharedFlow<FaceEvaluationSnapshot>(replay = 1)
        val gated = stateWith(faces)

        gated.uiState.test {
            join.value = library.join
            gated.open("Album")
            awaitState { it.status == AutomaticAlbumPickerStatus.READY }
            gated.selectTime(TimeSelection.Month(2025, 6))
            gated.revealPeople()
            val before = awaitState { it.people.status == PeopleListStatus.READY && it.content.matchCount == 3 }
            assertEquals(2, before.content.countries.size)

            assertTrue(gated.togglePerson(library.personA))
            val waiting = awaitState { it.people.chosenCount == 1 }
            // Not a count of 0 and not empty lists: building with no faces would have matched nothing.
            assertEquals(before.content, waiting.content)
            expectNoEvents()

            faces.emit(library.faces)
            val released = awaitState { it.content != before.content }
            assertEquals(2, library.evaluatorCount(listOf(TimeSelection.Month(2025, 6).toClause(), library.clauseA)))
            assertEquals(2, released.content.matchCount)
            assertEquals(listOf(CountryEntry("IT", "Italy", 2)), released.content.countries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the ninth person is refused with nothing changed, and a chosen one can still be removed at the cap`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)
            val eight = library.listed.take(MAX_PEOPLE_PER_ALBUM)
            val ninth = library.listed[MAX_PEOPLE_PER_ALBUM]

            for (person in eight) assertTrue(state.togglePerson(person))
            val full = awaitState { it.people.chosenCount == MAX_PEOPLE_PER_ALBUM }
            assertTrue(full.people.atCap)
            assertEquals(eight, full.people.chosen)

            val beforeNinth = state.uiState.value
            assertFalse(state.togglePerson(ninth))
            assertEquals(beforeNinth, state.uiState.value)

            // At the cap every unchosen tile is disabled and every chosen one is not.
            assertTrue(full.people.isDisabled(ninth))
            assertTrue(eight.none { full.people.isDisabled(it) })
            assertEquals(eight, state.uiState.value.people.chosen)
            assertEquals(MAX_PEOPLE_PER_ALBUM, state.uiState.value.rule!!.clauses.size)

            assertTrue(state.togglePerson(eight[3]))
            val seven = awaitState { it.people.chosenCount == MAX_PEOPLE_PER_ALBUM - 1 }
            assertFalse(seven.people.atCap)
            assertFalse(seven.people.isDisabled(ninth))

            assertTrue(state.togglePerson(ninth))
            assertEquals(MAX_PEOPLE_PER_ALBUM, awaitState { it.people.chosenCount == MAX_PEOPLE_PER_ALBUM }.people.chosenCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the chip clears every chosen person, keeps the list, and leaves place and date alone`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)
            state.selectRegion(AlbumRuleClause.Region("IT"))
            state.togglePerson(library.personA)
            state.togglePerson(library.personB)
            awaitState { it.people.chosenCount == 2 && it.selection.region != null }

            state.clearPeople()
            val cleared = awaitState { it.people.chosenCount == 0 }

            assertTrue(cleared.selection.people.isEmpty())
            assertEquals(library.listed, cleared.people.groups)
            assertEquals(PeopleListStatus.READY, cleared.people.status)
            assertEquals(AlbumRuleClause.Region("IT"), cleared.selection.region)
            assertEquals(AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT"))), cleared.rule)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Create needs a free name and at least one of a place, a date or a person`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed
        existingNames.value = emptyList()

        state.uiState.test {
            openWithPeople(this, library)
            assertFalse(state.uiState.value.canCreate)

            state.togglePerson(library.personA)
            assertTrue(awaitState { it.people.chosenCount == 1 }.canCreate)

            state.setName("   ")
            assertFalse(awaitState { it.name.isBlank() }.canCreate)

            state.setName("Her")
            assertTrue(awaitState { it.name == "Her" }.canCreate)

            existingNames.value = listOf("Her")
            assertFalse(awaitState { !it.canCreate }.canCreate)

            existingNames.value = emptyList()
            state.clearPeople()
            assertFalse(awaitState { it.people.chosenCount == 0 }.canCreate)

            state.selectTime(TimeSelection.Year(2025))
            assertTrue(awaitState { it.selection.time != null }.canCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no reload while a person is chosen, and a reload when indexing leaves running with none chosen`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)
            assertEquals(1, peopleLoads)

            // Starting from idle is not leaving running.
            state.onIndexingRunningChanged(false)
            assertEquals(1, peopleLoads)

            // Running, then done, with nobody chosen: the list is read again, and shows the new one.
            state.onIndexingRunningChanged(true)
            assertEquals(1, peopleLoads)
            peopleResult = library.listed.reversed()
            state.onIndexingRunningChanged(false)
            assertEquals(2, peopleLoads)
            assertEquals(library.listed.reversed(), awaitState { it.people.groups == library.listed.reversed() }.people.groups)

            // Staying idle reloads nothing.
            state.onIndexingRunningChanged(false)
            assertEquals(2, peopleLoads)

            // With a person chosen the list is frozen, so the anchors come from what the user saw.
            val chosen = state.uiState.value.people.groups.first()
            assertTrue(state.togglePerson(chosen))
            awaitState { it.people.chosenCount == 1 }
            peopleResult = library.listed
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            assertEquals(2, peopleLoads)
            assertEquals(library.listed.reversed(), state.uiState.value.people.groups)

            // The reload skipped meanwhile is made once nobody is chosen again, and the next end of a run reloads too.
            state.clearPeople()
            assertEquals(3, peopleLoads)
            assertEquals(library.listed, awaitState { it.people.groups == library.listed }.people.groups)
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            assertEquals(4, peopleLoads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reload skipped while a person was chosen is made when the last one is cleared or removed, once`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)

            // Nothing was skipped, so removing the last person reloads nothing.
            assertTrue(state.togglePerson(library.personA))
            assertTrue(state.togglePerson(library.personA))
            assertEquals(1, peopleLoads)

            // Chosen, indexing leaves RUNNING: the reload is skipped, the list stays, and clearing makes it.
            assertTrue(state.togglePerson(library.personA))
            awaitState { it.people.chosenCount == 1 }
            peopleResult = library.listed.reversed()
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            assertEquals(1, peopleLoads)
            state.clearPeople()
            assertEquals(2, peopleLoads)
            assertEquals(library.listed.reversed(), awaitState { it.people.groups == library.listed.reversed() }.people.groups)
            state.clearPeople()
            assertEquals(2, peopleLoads)

            // The same when the last person is removed by tapping them, and not before the last one goes.
            assertTrue(state.togglePerson(library.personA))
            assertTrue(state.togglePerson(library.personB))
            peopleResult = library.listed
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            assertTrue(state.togglePerson(library.personA))
            assertEquals(2, peopleLoads)
            assertTrue(state.togglePerson(library.personB))
            assertEquals(3, peopleLoads)
            assertEquals(library.listed, awaitState { it.people.groups == library.listed }.people.groups)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reload dropped because a person was chosen while it ran is made when nobody is chosen`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)
            peopleGate = CompletableDeferred()
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            assertEquals(2, peopleLoads)
            assertTrue(state.togglePerson(library.personA))
            awaitState { it.people.chosenCount == 1 }
            peopleGate!!.complete(Unit)

            peopleGate = null
            state.clearPeople()
            assertEquals(3, peopleLoads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reload shows the list it replaces rather than Loading, and a reload that was in flight when a person was chosen is dropped`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)

            peopleGate = CompletableDeferred()
            peopleResult = library.listed.reversed()
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            assertEquals(2, peopleLoads)
            val during = state.uiState.value.people
            assertEquals(PeopleListStatus.READY, during.status)
            assertEquals(library.listed, during.groups)

            // A person is chosen from the list shown while the reload is still running.
            assertTrue(state.togglePerson(library.personA))
            awaitState { it.people.chosenCount == 1 }
            peopleGate!!.complete(Unit)

            val after = state.uiState.value.people
            assertEquals(library.listed, after.groups)
            assertEquals(listOf(library.personA), after.chosen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed reload keeps nothing stale and a retry after it is allowed`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)

            peopleFailure = IOException("gone")
            state.onIndexingRunningChanged(true)
            state.onIndexingRunningChanged(false)
            assertEquals(PeopleListStatus.FAILED, awaitState { it.people.status == PeopleListStatus.FAILED }.people.status)
            assertTrue(state.uiState.value.people.groups.isEmpty())

            peopleFailure = null
            state.retryLoadingPeople()
            assertEquals(library.listed, awaitState { it.people.status == PeopleListStatus.READY }.people.groups)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `People going unoffered drops the chosen people and the groups, and offering it again reloads`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)
            state.selectRegion(AlbumRuleClause.Region("IT"))
            state.togglePerson(library.personA)
            state.togglePerson(library.personB)
            awaitState { it.people.chosenCount == 2 && it.selection.region != null }

            peopleOffered.value = false
            val dropped = awaitState { !it.people.offered }

            assertEquals(PeoplePickerUi(), dropped.people)
            assertTrue(dropped.selection.people.isEmpty())
            // The rest of the draft stays: only the people go.
            assertEquals(AlbumRuleClause.Region("IT"), dropped.selection.region)
            assertEquals(AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT"))), dropped.rule)
            assertEquals(0, activeFaceSubscriptions)

            state.revealPeople()
            assertEquals(1, peopleLoads)

            peopleOffered.value = true
            val offered = awaitState { it.people.offered }
            assertEquals(PeopleListStatus.NOT_LOADED, offered.people.status)
            state.revealPeople()
            assertEquals(library.listed, awaitState { it.people.status == PeopleListStatus.READY }.people.groups)
            assertEquals(2, peopleLoads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening or closing the picker drops the people list and the chosen people`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)
            state.togglePerson(library.personA)
            awaitState { it.people.chosenCount == 1 }

            state.close()
            val closed = awaitState { it.people.chosenCount == 0 && it.people.status == PeopleListStatus.NOT_LOADED }
            assertTrue(closed.people.groups.isEmpty())
            assertTrue(closed.people.offered)

            state.open("Again")
            state.revealPeople()
            assertEquals(2, peopleLoads)
            awaitState { it.people.status == PeopleListStatus.READY }

            state.togglePerson(library.personB)
            awaitState { it.people.chosenCount == 1 }
            state.open("Third")
            val reopened = awaitState {
                it.name == "Third" && it.people.chosenCount == 0 && it.people.status == PeopleListStatus.NOT_LOADED
            }
            assertTrue(reopened.people.groups.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- the face projection and the counts (plan 3.6, ADR-0024 Amendment 1 section 6) ---------------

    @Test
    fun `the face projection is not observed while nobody is chosen, and only while somebody is`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            openWithPeople(this, library)
            state.selectRegion(AlbumRuleClause.Region("IT"))
            state.selectTime(TimeSelection.Year(2025))
            state.browseCountry("IT")
            awaitState { it.selection.region != null && it.selection.time != null }
            assertEquals(0, faceSubscriptions)

            state.togglePerson(library.personA)
            awaitState { it.people.chosenCount == 1 }
            assertEquals(1, faceSubscriptions)
            assertEquals(1, activeFaceSubscriptions)

            // A second person is the same subscription.
            state.togglePerson(library.personB)
            awaitState { it.people.chosenCount == 2 }
            assertEquals(1, faceSubscriptions)

            state.clearPeople()
            awaitState { it.people.chosenCount == 0 }
            assertEquals(0, activeFaceSubscriptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the match count is the evaluator's member count for people alone, with a place, a date, and all three`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed
        val italy = AlbumRuleClause.Region("IT")
        val tuscany = AlbumRuleClause.Region("IT", "16", "Tuscany")
        val june = TimeSelection.Month(2025, 6)

        state.uiState.test {
            openWithPeople(this, library)
            faceSource.value = library.faces

            // The count reads 0 until the first face snapshot arrives, so each wait names the count it expects.
            assertEquals(3, library.evaluatorCount(listOf(library.clauseA)))
            state.togglePerson(library.personA)
            awaitState { it.people.chosenCount == 1 && it.content.matchCount == 3 }

            assertEquals(3, library.evaluatorCount(listOf(italy, library.clauseA)))
            state.selectRegion(italy)
            awaitState { it.selection.region == italy && it.content.matchCount == 3 }

            assertEquals(2, library.evaluatorCount(listOf(tuscany, june.toClause(), library.clauseA)))
            state.selectRegion(tuscany)
            state.selectTime(june)
            awaitState { it.selection.region == tuscany && it.selection.time == june && it.content.matchCount == 2 }

            assertEquals(2, library.evaluatorCount(listOf(june.toClause(), library.clauseA)))
            state.selectRegion(null)
            awaitState { it.selection.region == null && it.selection.time == june && it.content.matchCount == 2 }

            // Two people are one conjunction, and a place nobody was photographed in is a count of 0.
            assertEquals(2, library.evaluatorCount(listOf(library.clauseA, library.clauseB)))
            state.selectTime(null)
            state.togglePerson(library.personB)
            awaitState { it.people.chosenCount == 2 && it.selection.time == null && it.content.matchCount == 2 }

            assertEquals(0, library.evaluatorCount(listOf(AlbumRuleClause.Region("FR"), library.clauseA, library.clauseB)))
            state.selectRegion(AlbumRuleClause.Region("FR"))
            val none = awaitState { it.selection.region?.country == "FR" && it.content.matchCount == 0 }
            // An empty album is legal: indexing may be partial.
            assertTrue(none.canCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the place and date lists count the chosen people and no one else`() = runTest {
        val library = peopleLibrary()
        peopleResult = library.listed

        state.uiState.test {
            val everyone = openWithPeople(this, library)
            faceSource.value = library.faces
            assertEquals(listOf(CountryEntry("FR", "France", 1), CountryEntry("IT", "Italy", 3)), everyone.content.countries)
            assertEquals(listOf(YearFacet(2025, 4)), everyone.content.years)

            state.browseCountry("IT")
            state.browseRegion("16")
            state.browseYear(2025)
            state.togglePerson(library.personA)
            val withA = awaitState {
                it.people.chosenCount == 1 && it.content.countries.map { country -> country.count } == listOf(3) &&
                    it.content.browsedRegionPlaces.isNotEmpty() && it.content.browsedYearMonths.isNotEmpty()
            }

            assertEquals(listOf(CountryEntry("IT", "Italy", 3)), withA.content.countries)
            assertEquals(listOf(YearFacet(2025, 3)), withA.content.years)
            assertEquals(3, withA.content.browsedCountry!!.allCount)
            assertEquals(listOf(PlaceFacet("Florence", 2), PlaceFacet("Pisa", 1)), withA.content.browsedRegionPlaces)
            assertEquals(listOf(MonthFacet(1, 1), MonthFacet(6, 2)), withA.content.browsedYearMonths)
            for (entry in withA.content.countries) {
                assertEquals(entry.count, library.evaluatorCount(listOf(AlbumRuleClause.Region(entry.countryCode), library.clauseA)))
            }
            for (entry in withA.content.years) {
                assertEquals(entry.count, library.evaluatorCount(listOf(TimeSelection.Year(entry.year).toClause(), library.clauseA)))
            }

            // Nobody chosen again: every list is back to counting everything.
            state.clearPeople()
            val back = awaitState { it.people.chosenCount == 0 && it.content.countries.size == 2 }
            assertEquals(listOf(CountryEntry("FR", "France", 1), CountryEntry("IT", "Italy", 3)), back.content.countries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    // Two people photographed in Florence, Pisa and Paris, plus seven listed with no photo in the join (for the cap).
    private inner class PeopleLibrary(
        val join: MediaIndexLiveJoin,
        val faces: FaceEvaluationSnapshot,
        val personA: FaceGroupSummary,
        val personB: FaceGroupSummary,
        val others: List<FaceGroupSummary>
    ) {
        val clauseA = AlbumRuleClause.People.anchoredAt(personA.representative)
        val clauseB = AlbumRuleClause.People.anchoredAt(personB.representative)

        // B before A, then the rest: not sorted by group id, so a re-sort shows.
        val listed: List<FaceGroupSummary> = listOf(personB, personA) + others

        fun evaluatorCount(clauses: List<AlbumRuleClause>): Int {
            val input = PeopleEvaluationInput(PeopleAvailability.AVAILABLE, faces)
            return AlbumRuleEvaluator.evaluate(AlbumRule.Evaluatable(clauses), join, emptyList(), zone, input).members.size
        }
    }

    private fun peopleLibrary(): PeopleLibrary {
        class Shot(val live: MediaItem, val row: MediaIndexEvaluationProjection, val faces: List<FaceEvaluationFace>)

        fun shot(id: Int, date: Long, country: String, region: String, place: String, vararg groups: Long): Shot {
            val live = liveItem(id)
            val faces = groups.mapIndexed { index, group ->
                FaceEvaluationFace(id * 10L + index, if (group == GROUP_A) BOX_A else BOX_B, group, FACE_GROUPING_VERSION)
            }
            val row = MediaIndexEvaluationProjection(
                identity = live.toMediaIdentity(),
                capturedDateTakenEpochMillis = date,
                hasCoordinates = true,
                derivedCountryCode = country,
                derivedRegionCode = region,
                derivedPlaceName = place,
                derivedPlaceResolverVersion = PLACE_RESOLVER_VERSION
            )
            return Shot(live, row, faces)
        }

        val shots = listOf(
            shot(1, epoch("2025-06-10T12:00:00Z"), "IT", "16", "Florence", GROUP_A, GROUP_B),
            shot(2, epoch("2025-06-11T12:00:00Z"), "IT", "16", "Florence", GROUP_A),
            shot(3, epoch("2025-01-10T12:00:00Z"), "IT", "16", "Pisa", GROUP_A, GROUP_B),
            shot(4, epoch("2025-06-12T12:00:00Z"), "FR", "11", "Paris", GROUP_B)
        )
        val join = MediaIndexLiveJoin.build(shots.map { it.row }, shots.map { it.live })
        val faces = FaceEvaluationSnapshot(
            shots.map { FaceEvaluationImage(it.row.identity, FaceIndexOutcome.INDEXED, it.faces) }
        )

        fun person(groupId: Long, imageCount: Int, shot: Shot, box: FaceBox) = FaceGroupSummary(
            groupId = groupId,
            imageCount = imageCount,
            newestDateTakenEpochMillis = null,
            representative = FaceGroupRepresentative(shot.live.toMediaIdentity(), box, FACE_PIPELINE_VERSION)
        )

        val others = (0 until 7).map { index ->
            val away = liveItem(50 + index)
            FaceGroupSummary(
                groupId = 300L + index,
                imageCount = 5,
                newestDateTakenEpochMillis = null,
                representative = FaceGroupRepresentative(away.toMediaIdentity(), BOX_A, FACE_PIPELINE_VERSION)
            )
        }
        return PeopleLibrary(join, faces, person(GROUP_A, 3, shots[1], BOX_A), person(GROUP_B, 3, shots[3], BOX_B), others)
    }

    private fun liveItem(id: Int): MediaItem {
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

    // The join and the faces for [library], the draft opened and ready, and the People tab revealed and loaded.
    private suspend fun openWithPeople(
        turbine: ReceiveTurbine<AutomaticAlbumPickerUi>,
        library: PeopleLibrary
    ): AutomaticAlbumPickerUi {
        faceSource.value = library.faces
        openWith(turbine, library)
        state.revealPeople()
        return turbine.awaitState { it.people.status == PeopleListStatus.READY }
    }

    private suspend fun openWith(turbine: ReceiveTurbine<AutomaticAlbumPickerUi>, library: PeopleLibrary) {
        join.value = library.join
        state.open("Album")
        turbine.awaitState { it.status == AutomaticAlbumPickerStatus.READY }
    }

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

    private companion object {
        const val GROUP_A = 100L
        const val GROUP_B = 200L
        val BOX_A = FaceBox(0.1f, 0.1f, 0.3f, 0.3f)
        val BOX_B = FaceBox(0.6f, 0.6f, 0.8f, 0.8f)
    }

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
        return JoinedMedia(item, date, location, indexedIdentity = null)
    }
}
