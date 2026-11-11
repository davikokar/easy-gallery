package com.davide.seddio.easygallery.ui

import android.Manifest
import android.app.UiAutomation
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.data.AlbumEvaluationStatus
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AlbumRuleEvaluator
import com.davide.seddio.easygallery.data.AlbumRuleJson
import com.davide.seddio.easygallery.data.AndroidMediaIndexTierAExtractor
import com.davide.seddio.easygallery.data.AndroidMediaStoreRowDeltaProvider
import com.davide.seddio.easygallery.data.AndroidMediaStoreVolumeSnapshotProvider
import com.davide.seddio.easygallery.data.EasyGalleryDatabase
import com.davide.seddio.easygallery.data.GazetteerAssets
import com.davide.seddio.easygallery.data.InMemoryMediaStoreVersionStore
import com.davide.seddio.easygallery.data.MediaIndexDatabase
import com.davide.seddio.easygallery.data.MediaIndexDeltaDetector
import com.davide.seddio.easygallery.data.MediaIndexEntry
import com.davide.seddio.easygallery.data.MediaIndexLiveJoin
import com.davide.seddio.easygallery.data.MediaIndexPlaceResolver
import com.davide.seddio.easygallery.data.MediaIndexReconciler
import com.davide.seddio.easygallery.data.MediaIndexTierABatchIndexer
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.data.PLACE_RESOLVER_VERSION
import com.davide.seddio.easygallery.data.RoomAlbumStore
import com.davide.seddio.easygallery.data.RoomMediaIndexStore
import com.davide.seddio.easygallery.data.RoomMediaIndexWatermarkStore
import com.davide.seddio.easygallery.data.YearFacet
import com.davide.seddio.easygallery.data.canonicalMediaStoreUriForJoin
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Stage 2 step 2.7 region album path end to end on the real library: Tier A index, the
 * real resolver and gazetteer, the picker's facets, `createAutomaticAlbum`, and the evaluator, with
 * the expected members computed from the index rows and live media alone (ADR-0017 Amendment 2,
 * ADR-0016 Amendment 2).
 *
 * Everything the test writes goes to throwaway in-memory databases; the app's real index database,
 * album database and WorkManager are never touched. Capture counts with
 * `adb logcat -s RegionAlbumDeviceTest`. Only counts and country codes are logged, never a coordinate.
 */
@RunWith(AndroidJUnit4::class)
class RegionAlbumEvaluationDeviceTest {

    private class Scenario(
        val zone: ZoneId,
        val liveMedia: List<MediaItem>,
        val indexStore: RoomMediaIndexStore,
        val albumStore: RoomAlbumStore,
        val indexer: MediaIndexTierABatchIndexer,
        val resolver: MediaIndexPlaceResolver,
        val viewModel: AlbumsViewModel
    )

    @Test
    fun countryAndYearRegionAlbumsMatchPickerAndIndependentExpectation() {
        withMediaPermissions { app ->
            runBlocking { withScenario(app) { scenario -> countryAndYearScenario(scenario) } }
        }
    }

    @Test
    fun regionAlbumWaitsForResolverThenCompletesWithNoOtherTrigger() {
        withMediaPermissions { app ->
            runBlocking { withScenario(app) { scenario -> waitsForResolverScenario(app, scenario) } }
        }
    }

    @Test
    fun everyEntryThePickerOffersCountsWhatTheEvaluatorMakesMembers() {
        withMediaPermissions { app ->
            runBlocking { withScenario(app) { scenario -> everyOfferedEntryScenario(app, scenario) } }
        }
    }

    private suspend fun countryAndYearScenario(s: Scenario) {
        val vm = s.viewModel
        indexLiveMedia(s)

        val pass = s.resolver.resolvePending()
        Log.i(TAG, "resolvePass: ${pass.toLogLine()}")

        val rows = s.indexStore.observeAll().first()
        val rowsWithCoordinates = rows.count { it.hasCoordinates() }
        val resolvedWithPlace = rows.count { it.isResolvedWithPlace() }
        Log.i(
            TAG,
            "resolved: liveItems=${s.liveMedia.size} rowsWithCoordinates=$rowsWithCoordinates " +
                "resolvedWithPlace=$resolvedWithPlace resolverVersion=$PLACE_RESOLVER_VERSION"
        )
        failIfNoLocatedRows(rowsWithCoordinates, rows.size)
        if (resolvedWithPlace == 0) {
            failTest(
                "ENVIRONMENT: none of $rowsWithCoordinates rows with coordinates resolved to a place " +
                    "within the cap, so no region album can match anything."
            )
        }
        val rowsByUri = rows.associateBy { canonicalMediaStoreUriForJoin(it.mediaUri) }

        // Live media goes in only now, so the first join the picker and the albums see is fully resolved.
        vm.setAllMedia(s.liveMedia)
        vm.openAutomaticAlbumPicker()
        val ready = awaitPicker(vm, "READY or NAMES_FAILED") {
            it.status == AutomaticAlbumPickerStatus.READY || it.status == AutomaticAlbumPickerStatus.NAMES_FAILED
        }
        if (ready.status == AutomaticAlbumPickerStatus.NAMES_FAILED) {
            failTest("The picker could not read the gazetteer names asset (status NAMES_FAILED)")
        }

        // The country with the most items, read from the picker's own facets.
        val chosen = ready.content.countries
            .maxWithOrNull(compareBy<CountryEntry>({ it.count }, { it.countryCode }))
            ?: failTest(
                "ENVIRONMENT: the picker lists no country although $resolvedWithPlace rows resolved to a " +
                    "place. The rows do not join to the live media, or the picker is broken."
            )
        val region = AlbumRuleClause.Region(country = chosen.countryCode)
        vm.automaticAlbumPicker.selectRegion(region)
        val countryUi = awaitPicker(vm, "READY with region $region") {
            it.status == AutomaticAlbumPickerStatus.READY && it.selection.region == region
        }
        val countryPickerCount = countryUi.content.matchCount
        assertEquals(
            "The country's own facet count and the picker's match count for it must agree",
            chosen.count,
            countryPickerCount
        )
        val countryRule = countryUi.rule ?: failTest("Picker has a region chosen but offers no rule")
        assertEquals("A country-only choice writes one clause", listOf<AlbumRuleClause>(region), countryRule.clauses)

        val countryExpected = expectedUris(s, rowsByUri, chosen.countryCode, year = null)
        val (countryItem, countryMembers) = createAlbumAndAwaitMembers(s, "$ALBUM_NAME country", countryRule)
        val countryMemberUris = countryMembers.map { it.mediaItem.uri.toString() }
        logAlbum(
            label = "country album",
            s = s,
            rowsWithCoordinates = rowsWithCoordinates,
            resolvedWithPlace = resolvedWithPlace,
            chosenCountry = chosen.countryCode,
            pickerMatchCount = countryPickerCount,
            expected = countryExpected.size,
            members = countryMembers.size
        )
        assertTrue("The country album must have members", countryMembers.isNotEmpty())
        assertTrue("A region album must show its region clause", countryItem.hasRegionClause)
        assertEquals("Members must be unique", countryMemberUris.size, countryMemberUris.toSet().size)
        assertEquals("Album list count and the members it lists must agree", countryItem.resolvedMemberCount, countryMembers.size)
        assertEquals("Member count must equal the picker's match count", countryPickerCount, countryMembers.size)
        assertEquals("Member count must equal the independent expectation", countryExpected.size, countryMembers.size)
        assertEquals("Members must be the independently computed set", countryExpected, countryMemberUris.toSet())

        // Compound: the most populated year among this country's items, again from the picker's facets.
        val yearFacet = countryUi.content.years
            .maxWithOrNull(compareBy<YearFacet>({ it.count }, { it.year }))
            ?: failTest(
                "ENVIRONMENT: none of the ${chosen.count} items in ${chosen.countryCode} has a date-taken " +
                    "in a writable year, so a country and year album cannot be exercised."
            )
        val timeSelection = TimeSelection.Year(yearFacet.year)
        vm.automaticAlbumPicker.selectTime(timeSelection)
        val compoundUi = awaitPicker(vm, "READY with region $region and time $timeSelection") {
            it.status == AutomaticAlbumPickerStatus.READY &&
                it.selection.region == region &&
                it.selection.time == timeSelection
        }
        val compoundPickerCount = compoundUi.content.matchCount
        assertEquals(
            "The year's own facet count and the picker's match count for country and year must agree",
            yearFacet.count,
            compoundPickerCount
        )
        val compoundRule = compoundUi.rule ?: failTest("Picker has region and year chosen but offers no rule")
        assertEquals("Country and year write two clauses", 2, compoundRule.clauses.size)

        val compoundExpected = expectedUris(s, rowsByUri, chosen.countryCode, year = yearFacet.year)
        val (compoundItem, compoundMembers) = createAlbumAndAwaitMembers(s, "$ALBUM_NAME country and year", compoundRule)
        val compoundMemberUris = compoundMembers.map { it.mediaItem.uri.toString() }
        logAlbum(
            label = "country and year ${yearFacet.year} album",
            s = s,
            rowsWithCoordinates = rowsWithCoordinates,
            resolvedWithPlace = resolvedWithPlace,
            chosenCountry = chosen.countryCode,
            pickerMatchCount = compoundPickerCount,
            expected = compoundExpected.size,
            members = compoundMembers.size
        )
        assertTrue("The country and year album must have members", compoundMembers.isNotEmpty())
        assertEquals("Members must be unique", compoundMemberUris.size, compoundMemberUris.toSet().size)
        assertEquals("Album list count and the members it lists must agree", compoundItem.resolvedMemberCount, compoundMembers.size)
        assertEquals("Member count must equal the picker's match count", compoundPickerCount, compoundMembers.size)
        assertEquals("Member count must equal the independent expectation", compoundExpected.size, compoundMembers.size)
        assertEquals("Members must be the independently computed set", compoundExpected, compoundMemberUris.toSet())
        assertTrue(
            "The country and year album cannot hold more than the country album",
            compoundMembers.size <= countryMembers.size
        )
    }

    private suspend fun everyOfferedEntryScenario(app: EasyGalleryApp, s: Scenario) {
        indexLiveMedia(s)
        val pass = s.resolver.resolvePending()
        Log.i(TAG, "resolvePass: ${pass.toLogLine()}")
        val rows = s.indexStore.observeAll().first()
        failIfNoLocatedRows(rows.count { it.hasCoordinates() }, rows.size)

        // The projection and live media the ViewModel joins, so this join is the one the picker counts over.
        val join = MediaIndexLiveJoin.build(s.indexStore.observeEvaluationProjection().first(), s.liveMedia)
        val names = withContext(Dispatchers.IO) { GazetteerAssets.loadNames(app) }
        val locale = Locale.getDefault()
        fun contentFor(selection: AutomaticAlbumSelection) =
            AutomaticAlbumPickerContent.build(join, s.zone, names, locale, selection)

        val top = contentFor(AutomaticAlbumSelection())
        if (top.countries.isEmpty() && top.years.isEmpty()) {
            failTest(
                "ENVIRONMENT: the picker offers no country and no year over ${s.liveMedia.size} live items, " +
                    "so there is no entry to compare with the evaluator."
            )
        }

        val vm = s.viewModel
        vm.setAllMedia(s.liveMedia)
        vm.openAutomaticAlbumPicker()
        val shown = awaitPicker(vm, "READY or NAMES_FAILED") {
            it.status == AutomaticAlbumPickerStatus.READY || it.status == AutomaticAlbumPickerStatus.NAMES_FAILED
        }
        if (shown.status == AutomaticAlbumPickerStatus.NAMES_FAILED) {
            failTest("The picker could not read the gazetteer names asset (status NAMES_FAILED)")
        }
        assertEquals("The ViewModel's picker must list the countries of this join", top.countries, shown.content.countries)
        assertEquals("The ViewModel's picker must list the years of this join", top.years, shown.content.years)

        val checkedByKind = LinkedHashMap<String, Int>()
        val mismatches = ArrayList<String>()
        fun check(kind: String, where: String, shownCount: Int, vararg clauses: AlbumRuleClause) {
            checkedByKind.merge(kind, 1, Int::plus)
            val evaluated = AlbumRuleEvaluator
                .evaluate(AlbumRule.Evaluatable(clauses.toList()), join, emptyList(), s.zone)
                .members.size
            if (evaluated != shownCount) mismatches += "$kind $where: shown=$shownCount evaluated=$evaluated"
        }

        for (country in top.countries) {
            val code = country.countryCode
            val countryClause = AlbumRuleClause.Region(country = code)
            check("country", code, country.count, countryClause)

            val breakdown = contentFor(AutomaticAlbumSelection(browsedCountry = code)).browsedCountry
            if (breakdown == null) {
                mismatches += "country $code: the picker offers the country but no breakdown for it"
                continue
            }
            check("country-all", code, breakdown.allCount, countryClause)
            for (region in breakdown.regions) {
                val regionClause = AlbumRuleClause.Region(code, region.admin1Code, region.admin1Name)
                check("region", "$code/${region.admin1Code}", region.count, regionClause)
                val places = contentFor(
                    AutomaticAlbumSelection(browsedCountry = code, browsedAdmin1 = region.admin1Code)
                ).browsedRegionPlaces
                for (place in places) {
                    check("place", "$code/${region.admin1Code}", place.count, regionClause.copy(place = place.placeName))
                }
            }
            for (place in breakdown.placesWithoutRegion.places) {
                check("place", "$code/none", place.count, AlbumRuleClause.Region(code, place = place.placeName))
            }
        }
        for (year in top.years) {
            check("year", "${year.year}", year.count, TimeSelection.Year(year.year).toClause())
            val months = contentFor(AutomaticAlbumSelection(browsedYear = year.year)).browsedYearMonths
            for (month in months) {
                check("month", "${year.year}-${month.month}", month.count, TimeSelection.Month(year.year, month.month).toClause())
            }
        }

        val checked = checkedByKind.values.sum()
        Log.i(
            TAG,
            "every entry: liveItems=${s.liveMedia.size} countries=${top.countries.size} years=${top.years.size} " +
                "checked=$checked byKind=$checkedByKind mismatches=${mismatches.size}"
        )
        mismatches.forEach { Log.e(TAG, "mismatch: $it") }
        assertTrue(
            "${mismatches.size} of $checked offered entries disagree with the evaluator: ${mismatches.take(10)}",
            mismatches.isEmpty()
        )
    }

    private suspend fun waitsForResolverScenario(app: EasyGalleryApp, s: Scenario) {
        val vm = s.viewModel
        val rowsBefore = indexLiveMedia(s)
        val rowsWithCoordinates = rowsBefore.count { it.hasCoordinates() }
        failIfNoLocatedRows(rowsWithCoordinates, rowsBefore.size)
        assertTrue(
            "Tier A alone must leave every resolver marker null",
            rowsBefore.all { it.derivedPlaceResolverVersion == null }
        )

        // The rule is built before the resolver runs, from the gazetteer alone, so the album can be watched waiting.
        val gazetteer = withContext(Dispatchers.IO) { GazetteerAssets.load(app) }
        val liveUris = s.liveMedia.mapTo(HashSet()) { canonicalMediaStoreUriForJoin(it.uri.toString()) }
        val directCountryByUri = HashMap<String, String>()
        for (row in rowsBefore) {
            val latitude = row.capturedLatitude ?: continue
            val longitude = row.capturedLongitude ?: continue
            val uri = canonicalMediaStoreUriForJoin(row.mediaUri)
            if (uri !in liveUris) continue
            gazetteer.nearest(latitude, longitude)?.let { directCountryByUri[uri] = it.countryCode }
        }
        if (directCountryByUri.isEmpty()) {
            failTest(
                "ENVIRONMENT: none of $rowsWithCoordinates rows with coordinates resolves to a place within " +
                    "the cap, so no region album can match anything."
            )
        }
        val chosenCountry = directCountryByUri.values
            .groupingBy { it }
            .eachCount()
            .entries
            .maxWith(compareBy({ it.value }, { it.key }))
            .key
        val directExpected = directCountryByUri.filterValues { it == chosenCountry }.keys
        val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = chosenCountry)))

        vm.setAllMedia(s.liveMedia)
        val albumName = "$ALBUM_NAME waits for resolver"
        assertTrue("createAutomaticAlbum must accept a fresh name", vm.createAutomaticAlbum(albumName, rule))
        val before = awaitAlbum(vm, albumName, "listed before the resolver") { true }
        Log.i(
            TAG,
            "before resolver: liveItems=${s.liveMedia.size} rowsWithCoordinates=$rowsWithCoordinates " +
                "resolvedWithPlace=0 chosenCountry=$chosenCountry pickerMatchCount=n/a " +
                "expected=${directExpected.size} members=${before.resolvedMemberCount} status=${before.status}"
        )
        assertNotEquals(
            "Before the resolver the album must not claim to be complete",
            AlbumEvaluationStatus.COMPLETE,
            before.status
        )
        assertEquals("No member can exist before any place is resolved", 0, before.resolvedMemberCount)

        // From here nothing touches the ViewModel or the album store: the resolver's index write alone drives it.
        val pass = s.resolver.resolvePending()
        Log.i(TAG, "resolvePass: ${pass.toLogLine()}")
        assertEquals("The resolver must select exactly the rows with coordinates", rowsWithCoordinates, pass.selectedRows)

        val rows = s.indexStore.observeAll().first()
        val resolvedWithPlace = rows.count { it.isResolvedWithPlace() }
        val rowsByUri = rows.associateBy { canonicalMediaStoreUriForJoin(it.mediaUri) }
        val expected = expectedUris(s, rowsByUri, chosenCountry, year = null)
        assertEquals(
            "The stored places must agree with resolving the same coordinates directly with the gazetteer",
            directExpected.toSet(),
            expected.mapTo(HashSet()) { canonicalMediaStoreUriForJoin(it) }
        )

        val after = awaitAlbum(vm, albumName, "COMPLETE after the resolver with no other trigger") {
            it.status == AlbumEvaluationStatus.COMPLETE
        }
        val members = awaitMembers(vm, after.id)
        val memberUris = members.map { it.mediaItem.uri.toString() }
        Log.i(
            TAG,
            "after resolver: liveItems=${s.liveMedia.size} rowsWithCoordinates=$rowsWithCoordinates " +
                "resolvedWithPlace=$resolvedWithPlace chosenCountry=$chosenCountry pickerMatchCount=n/a " +
                "expected=${expected.size} members=${members.size} status=${after.status}"
        )
        assertTrue("The album must have members once the resolver has run", members.isNotEmpty())
        assertEquals("Members must be unique", memberUris.size, memberUris.toSet().size)
        assertEquals("Album list count and the members it lists must agree", after.resolvedMemberCount, members.size)
        assertEquals("Member count must equal the independent expectation", expected.size, members.size)
        assertEquals("Members must be the independently computed set", expected, memberUris.toSet())
    }

    private suspend fun withScenario(app: EasyGalleryApp, block: suspend (Scenario) -> Unit) {
        val zone = ZoneId.systemDefault()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            failTest(
                "ENVIRONMENT: ACCESS_MEDIA_LOCATION is not granted on API ${Build.VERSION.SDK_INT}, " +
                    "so the platform redacts GPS from every image and no coordinate can be indexed."
            )
        }

        val liveMedia = MediaStoreDataSource(app).getAllMedia()
        Log.i(TAG, "start: sdk=${Build.VERSION.SDK_INT} zone=$zone liveItems=${liveMedia.size}")
        if (liveMedia.isEmpty()) {
            failTest(
                "ENVIRONMENT: MediaStore returned zero live items to the app process, so no region " +
                    "album can be proved. Media permissions were granted and asserted."
            )
        }

        val indexDatabase = Room.inMemoryDatabaseBuilder(app, MediaIndexDatabase::class.java).build()
        val albumDatabase = Room.inMemoryDatabaseBuilder(app, EasyGalleryDatabase::class.java).build()
        var viewModel: AlbumsViewModel? = null
        try {
            val indexStore = RoomMediaIndexStore(indexDatabase)
            val watermarkStore = RoomMediaIndexWatermarkStore(indexDatabase.mediaIndexDao())
            val albumStore = RoomAlbumStore(albumDatabase)

            // A first run needs an empty index and no watermark, or the batch would select nothing.
            assertTrue("Throwaway index must start empty", indexStore.observeAll().first().isEmpty())
            assertTrue("Throwaway watermark table must start empty", watermarkStore.readAll().isEmpty())

            val indexer = MediaIndexTierABatchIndexer(
                mediaRepository = MediaStoreDataSource(app),
                mediaIndexStore = indexStore,
                deltaDetector = MediaIndexDeltaDetector(
                    watermarkStore = watermarkStore,
                    snapshotProvider = AndroidMediaStoreVolumeSnapshotProvider(app)
                ),
                rowDeltaProvider = AndroidMediaStoreRowDeltaProvider(app),
                reconciler = MediaIndexReconciler(indexStore),
                extractor = AndroidMediaIndexTierAExtractor(app, zoneId = zone)
            )
            val resolver = MediaIndexPlaceResolver(
                store = indexStore,
                gazetteerLoader = { GazetteerAssets.load(app) }
            )

            // An in-memory version store means the version check can never bump the evaluation epoch.
            val vm = AlbumsViewModel(
                application = app,
                albumStore = albumStore,
                mediaStoreVersionStore = InMemoryMediaStoreVersionStore(),
                mediaIndexStore = indexStore,
                zoneProvider = { zone },
                evaluationDispatcher = Dispatchers.Default
            )
            viewModel = vm

            block(Scenario(zone, liveMedia, indexStore, albumStore, indexer, resolver, vm))
        } finally {
            // Stop the ViewModel's collectors before the databases they read are closed.
            viewModel?.let { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
            indexDatabase.close()
            albumDatabase.close()
        }
    }

    /** Runs the real Tier A batch over live media and returns the stored rows. */
    private suspend fun indexLiveMedia(s: Scenario): List<MediaIndexEntry> {
        val batch = s.indexer.indexDelta(Build.VERSION.SDK_INT)
        Log.i(
            TAG,
            "batch: selected=${batch.selectedRows} indexed=${batch.indexedRows} skipped=${batch.skippedRows} " +
                "deleted=${batch.deletedRows} healed=${batch.healedRows}"
        )
        if (batch.selectedRows == 0 || batch.indexedRows == 0) {
            failTest(
                "ENVIRONMENT: the batch selected=${batch.selectedRows} indexed=${batch.indexedRows} rows " +
                    "on a device with ${s.liveMedia.size} live items; nothing can be proved."
            )
        }
        return s.indexStore.observeAll().first()
    }

    private fun failIfNoLocatedRows(rowsWithCoordinates: Int, indexedRows: Int) {
        if (rowsWithCoordinates == 0) {
            failTest(
                "ENVIRONMENT: none of $indexedRows indexed rows has coordinates. The device's photos carry " +
                    "no GPS, or location access is withheld, so no region album can be exercised."
            )
        }
    }

    /** What an album naming [country] (and [year], when given) must hold, from index rows and live media only. */
    private fun expectedUris(
        s: Scenario,
        rowsByUri: Map<String, MediaIndexEntry>,
        country: String,
        year: Int?
    ): Set<String> {
        return s.liveMedia
            .filter { item ->
                val row = rowsByUri[canonicalMediaStoreUriForJoin(item.uri.toString())] ?: return@filter false
                if (row.derivedPlaceResolverVersion != PLACE_RESOLVER_VERSION) return@filter false
                if (row.derivedCountryCode != country) return@filter false
                year == null || row.capturedDateTakenEpochMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(s.zone).year == year } == true
            }
            .mapTo(HashSet()) { it.uri.toString() }
    }

    private suspend fun createAlbumAndAwaitMembers(
        s: Scenario,
        name: String,
        rule: AlbumRule.Evaluatable
    ): Pair<AlbumListItem, List<AlbumDetailMember>> {
        assertTrue("createAutomaticAlbum must accept the name '$name'", s.viewModel.createAutomaticAlbum(name, rule))
        val item = awaitAlbum(s.viewModel, name, "COMPLETE after creation") {
            it.status == AlbumEvaluationStatus.COMPLETE
        }

        val stored = s.albumStore.observeAlbums().first().firstOrNull { it.name == name }
            ?: failTest("Album '$name' is listed by the ViewModel but not stored")
        assertEquals("The stored rule must parse back to the picker's rule", rule, AlbumRuleJson.parse(stored.rule))

        val members = awaitMembers(s.viewModel, item.id)
        members.forEach { member ->
            assertNull("Member ${member.mediaItem.uri} is a rule match, not an addition", member.membershipId)
        }
        return item to members
    }

    private suspend fun awaitPicker(
        vm: AlbumsViewModel,
        description: String,
        predicate: (AutomaticAlbumPickerUi) -> Boolean
    ): AutomaticAlbumPickerUi {
        val state = vm.automaticAlbumPicker.uiState
        return withTimeoutOrNull(AWAIT_MILLIS) { state.first { predicate(it) } }
            ?: failTest("Picker never reached: $description. last status=${state.value.status}")
    }

    private suspend fun awaitAlbum(
        vm: AlbumsViewModel,
        name: String,
        description: String,
        predicate: (AlbumListItem) -> Boolean
    ): AlbumListItem {
        val found = withTimeoutOrNull(AWAIT_MILLIS) {
            vm.albums.first { items -> items.any { it.name == name && predicate(it) } }
        }?.firstOrNull { it.name == name }
        return found ?: failTest(
            "Album '$name' never reached: $description. last=${vm.albums.value.firstOrNull { it.name == name }}"
        )
    }

    // Deselects first so a list left over from the previously selected album cannot satisfy the wait.
    private suspend fun awaitMembers(vm: AlbumsViewModel, albumId: Long): List<AlbumDetailMember> {
        vm.selectAlbum(null)
        withTimeoutOrNull(AWAIT_MILLIS) { vm.selectedAlbumMembers.first { it.isEmpty() } }
            ?: failTest("Members of the previous selection never cleared")
        // Selecting an id the ViewModel has not emitted yet makes it clear the selection again.
        vm.selectAlbum(albumId)
        return withTimeoutOrNull(AWAIT_MILLIS) { vm.selectedAlbumMembers.first { it.isNotEmpty() } }
            ?: failTest("Album $albumId never listed a member. selectedAlbum=${vm.selectedAlbum.value}")
    }

    private fun logAlbum(
        label: String,
        s: Scenario,
        rowsWithCoordinates: Int,
        resolvedWithPlace: Int,
        chosenCountry: String,
        pickerMatchCount: Int,
        expected: Int,
        members: Int
    ) {
        Log.i(
            TAG,
            "$label: liveItems=${s.liveMedia.size} rowsWithCoordinates=$rowsWithCoordinates " +
                "resolvedWithPlace=$resolvedWithPlace chosenCountry=$chosenCountry " +
                "pickerMatchCount=$pickerMatchCount expected=$expected members=$members"
        )
    }

    private fun MediaIndexEntry.hasCoordinates(): Boolean = capturedLatitude != null && capturedLongitude != null

    private fun MediaIndexEntry.isResolvedWithPlace(): Boolean =
        derivedPlaceResolverVersion == PLACE_RESOLVER_VERSION && derivedCountryCode != null

    private fun failTest(message: String): Nothing {
        Log.e(TAG, message)
        throw AssertionError(message)
    }

    private fun withMediaPermissions(block: (EasyGalleryApp) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        try {
            allowRequiredMediaAppOps()
            grantRequiredPermissions()
            assertRequiredMediaPermissionsGranted()

            block(app)
        } finally {
            // Idempotent restore on every exit path, in the style of MediaIndexingWorkEnqueueTest. The location
            // grant is only ever added, never revoked: revoking a runtime permission can kill this process.
            allowRequiredMediaAppOps()
            grantRequiredPermissions()
            assertRequiredMediaPermissionsGranted()
        }
    }

    private fun requiredMediaPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun requiredMediaAppOps(): List<String> {
        val ops = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableListOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")
        } else {
            mutableListOf("READ_EXTERNAL_STORAGE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ops += "ACCESS_MEDIA_LOCATION"
        return ops
    }

    private fun hasPermission(permission: String): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.packageManager.checkPermission(permission, context.packageName) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun assertRequiredMediaPermissionsGranted() {
        val missing = requiredMediaPermissions().filterNot { hasPermission(it) }
        assertTrue("Missing required media runtime permissions: $missing", missing.isEmpty())
    }

    // ACCESS_MEDIA_LOCATION is checked by the scenario itself, so a withheld grant reads as an environment finding.
    private fun grantRequiredPermissions() {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val permissions = requiredMediaPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) permissions += Manifest.permission.ACCESS_MEDIA_LOCATION
        permissions.forEach { permission ->
            try {
                uiAutomation.grantRuntimePermission(targetPackage, permission)
            } catch (_: Exception) {
                // The checks that follow report any permission still missing.
            }
        }
    }

    private fun allowRequiredMediaAppOps() {
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        requiredMediaAppOps().forEach { op ->
            executeShellCommand(uiAutomation, "cmd appops set --uid $targetPackage $op allow")
        }
    }

    private fun executeShellCommand(uiAutomation: UiAutomation, command: String) {
        uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                // Drain to EOF so the shell command has completed before the test continues.
                while (reader.readLine() != null) {
                    // Output is irrelevant.
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private companion object {
        const val TAG = "RegionAlbumDeviceTest"
        const val ALBUM_NAME = "RegionAlbumDeviceTest"
        const val AWAIT_MILLIS = 60_000L
    }
}
