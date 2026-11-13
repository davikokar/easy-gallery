package com.davide.seddio.easygallery.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.*
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewAutomaticAlbumContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("UTC")
    private val locale = Locale.US

    private val names = GazetteerNames.load(
        StringReader(
            "# dataset-version: $PLACE_RESOLVER_VERSION\n# rows-written: 3\n" +
                "IT.16\tTuscany\nIT.09\tLombardy\nFR.11\tIle-de-France\n"
        ),
        StringReader(
            "# dataset-version: $PLACE_RESOLVER_VERSION\n# rows-written: 3\n" +
                "IT\tItaly\nFR\tFrance\nSG\tSingapore\n"
        )
    )

    // Italy 5 (Tuscany 3, Lombardy 1, Vatican City without a region 1), France 1, Singapore 2 (no region);
    // 2025 has 7 dated items (June 4), 2024 has 1; two items have no usable location and two no date.
    private fun libraryJoin() = MediaIndexLiveJoin(
        listOf(
            joined(1, "2025-06-10T12:00:00Z", place("IT", "16", "Florence")),
            joined(2, "2025-06-20T12:00:00Z", place("IT", "16", "Pisa")),
            joined(3, "2024-03-05T12:00:00Z", place("IT", "09", "Milan")),
            joined(4, "2025-06-12T12:00:00Z", place("FR", "11", "Paris")),
            joined(5, "2025-07-01T12:00:00Z", place("SG", null, "Singapore")),
            joined(6, null, place("IT", "16", "Florence")),
            joined(7, "2025-06-01T12:00:00Z", LocationState.NoCoordinates),
            joined(8, "2025-08-01T12:00:00Z", place("SG", null, "Singapore")),
            joined(9, "2025-01-01T12:00:00Z", place("IT", null, "Vatican City")),
            joined(10, null, LocationState.ResolvedOutOfCap)
        )
    )

    @Test
    fun placeSectionOpensOnCountriesWithLocalisedNamesAndCounts() {
        composeTestRule.setContent { PickerHarness() }

        composeTestRule.onNodeWithTag("new_automatic_album_place_tab").assertIsSelected()
        composeTestRule.onNodeWithTag("new_automatic_album_place_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_automatic_album_country_IT").assertTextContains("Italy")
        composeTestRule.onNodeWithTag("new_automatic_album_country_IT").assertTextContains("5")
        composeTestRule.onNodeWithTag("new_automatic_album_country_FR").assertTextContains("France")
        composeTestRule.onNodeWithTag("new_automatic_album_country_FR").assertTextContains("1")
        composeTestRule.onNodeWithTag("new_automatic_album_country_SG").assertTextContains("Singapore")
        composeTestRule.onNodeWithTag("new_automatic_album_country_SG").assertTextContains("2")
        assertTrue(rowTop("new_automatic_album_country_FR") < rowTop("new_automatic_album_country_IT"))
        assertTrue(rowTop("new_automatic_album_country_IT") < rowTop("new_automatic_album_country_SG"))
    }

    @Test
    fun drillingIntoACountryShowsAllOfRowRegionsAndAnUnselectableGroupOfPlacesWithoutARegion() {
        composeTestRule.setContent { PickerHarness() }

        openCountry("IT")

        composeTestRule.onNodeWithTag("new_automatic_album_place_back").assertTextContains("Italy")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertTextContains("All of Italy")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertTextContains("5")
        composeTestRule.onNodeWithTag("new_automatic_album_region_16").assertTextContains("Tuscany")
        composeTestRule.onNodeWithTag("new_automatic_album_region_16").assertTextContains("3")
        composeTestRule.onNodeWithTag("new_automatic_album_region_09").assertTextContains("Lombardy")
        composeTestRule.onNodeWithTag("new_automatic_album_region_09").assertTextContains("1")
        composeTestRule.onNodeWithText(appContext.getString(R.string.automatic_album_places_without_region))
            .assertExists()
            .assertHasNoClickAction()
        composeTestRule.onNodeWithTag("new_automatic_album_place_Vatican City").assertTextContains("1")
        assertTrue(rowTop("new_automatic_album_all_of_country") < rowTop("new_automatic_album_region_16"))
    }

    @Test
    fun countryWithOnlyRegionlessPlacesListsThosePlacesAndNoRegions() {
        composeTestRule.setContent { PickerHarness() }

        openCountry("SG")

        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertTextContains("All of Singapore")
        composeTestRule.onNodeWithTag("new_automatic_album_place_Singapore").assertTextContains("2")
        composeTestRule.onAllNodesWithTag("new_automatic_album_region_16").assertCountEquals(0)
    }

    @Test
    fun backFromACountryReturnsToTheCountriesFromTheListRowAndFromTheTopBar() {
        composeTestRule.setContent { PickerHarness() }

        composeTestRule.onNodeWithTag("new_automatic_album_close_button").assertExists()
        composeTestRule.onAllNodesWithTag("new_automatic_album_back_button").assertCountEquals(0)

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_back_button").assertExists()
        composeTestRule.onAllNodesWithTag("new_automatic_album_close_button").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_place_back").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_country_IT").assertExists()
        composeTestRule.onAllNodesWithTag("new_automatic_album_all_of_country").assertCountEquals(0)

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_back_button").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_country_IT").assertExists()
        composeTestRule.onAllNodesWithTag("new_automatic_album_all_of_country").assertCountEquals(0)
    }

    @Test
    fun drillingIntoARegionShowsAllOfRegionAndItsPlacesAndBackReturnsToTheCountry() {
        composeTestRule.setContent { PickerHarness() }

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_region_16").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_region_back").assertTextContains("Tuscany")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_region").assertTextContains("All of Tuscany")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_region").assertTextContains("3")
        composeTestRule.onNodeWithTag("new_automatic_album_place_Florence").assertTextContains("2")
        composeTestRule.onNodeWithTag("new_automatic_album_place_Pisa").assertTextContains("1")
        composeTestRule.onAllNodesWithTag("new_automatic_album_all_of_country").assertCountEquals(0)

        composeTestRule.onNodeWithTag("new_automatic_album_region_back").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertExists()
        composeTestRule.onNodeWithTag("new_automatic_album_region_16").assertExists()

        composeTestRule.onNodeWithTag("new_automatic_album_region_16").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_back_button").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertExists()
    }

    @Test
    fun choosingAllOfACountrySelectsItShowsAChipAndStatesTheMatchCount() {
        composeTestRule.setContent { PickerHarness() }

        composeTestRule.onNodeWithTag("new_automatic_album_hint")
            .assertTextEquals(appContext.getString(R.string.automatic_album_hint_choose))

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertIsNotSelected()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertIsSelected()
        composeTestRule.onNodeWithTag("new_automatic_album_place_chip").assertTextContains("Italy")
        composeTestRule.onNodeWithContentDescription("Clear place Italy").assertExists()
        composeTestRule.onNodeWithTag("new_automatic_album_match_count")
            .assertTextEquals("5 photos and videos will be in this album")
        composeTestRule.onAllNodesWithTag("new_automatic_album_hint").assertCountEquals(0)
    }

    @Test
    fun matchCountIsSingularForOneItem() {
        composeTestRule.setContent { PickerHarness() }

        openCountry("FR")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_match_count")
            .assertTextEquals("1 photo or video will be in this album")
    }

    @Test
    fun choosingARegionlessPlaceWritesARuleWithoutTheRegionPair() {
        var createdRule: AlbumRule.Evaluatable? = null
        composeTestRule.setContent { PickerHarness(onCreate = { _, rule -> createdRule = rule }) }

        openCountry("SG")
        composeTestRule.onNodeWithTag("new_automatic_album_place_Singapore").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_create_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = "SG", place = "Singapore"))),
                createdRule
            )
        }
    }

    @Test
    fun choosingAPlaceInARegionWritesTheRegionPairAndThePlace() {
        var createdRule: AlbumRule.Evaluatable? = null
        composeTestRule.setContent { PickerHarness(onCreate = { _, rule -> createdRule = rule }) }

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_region_16").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_place_Florence").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_place_chip").assertTextContains("Florence")
        composeTestRule.onNodeWithTag("new_automatic_album_create_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                AlbumRule.Evaluatable(
                    listOf(
                        AlbumRuleClause.Region(
                            country = "IT",
                            admin1 = "16",
                            admin1Name = "Tuscany",
                            place = "Florence"
                        )
                    )
                ),
                createdRule
            )
        }
    }

    @Test
    fun choosingAllOfARegionWritesTheRegionPairWithoutAPlace() {
        var createdRule: AlbumRule.Evaluatable? = null
        composeTestRule.setContent { PickerHarness(onCreate = { _, rule -> createdRule = rule }) }

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_region_09").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_region").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_create_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                AlbumRule.Evaluatable(
                    listOf(AlbumRuleClause.Region(country = "IT", admin1 = "09", admin1Name = "Lombardy"))
                ),
                createdRule
            )
        }
    }

    @Test
    fun dateSectionListsYearsNewestFirstWithCountsAndDrillsIntoMonths() {
        composeTestRule.setContent { PickerHarness() }

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").assertIsSelected()
        composeTestRule.onAllNodesWithTag("new_automatic_album_place_list").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").assertTextContains("2025")
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").assertTextContains("7")
        composeTestRule.onNodeWithTag("new_automatic_album_year_2024").assertTextContains("1")
        assertTrue(rowTop("new_automatic_album_year_2025") < rowTop("new_automatic_album_year_2024"))

        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_date_back").assertTextContains("2025")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_year").assertTextContains("All of 2025")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_year").assertTextContains("7")
        composeTestRule.onNodeWithTag("new_automatic_album_month_1").assertTextContains("January")
        composeTestRule.onNodeWithTag("new_automatic_album_month_6").assertTextContains("June")
        composeTestRule.onNodeWithTag("new_automatic_album_month_6").assertTextContains("4")
        composeTestRule.onNodeWithTag("new_automatic_album_month_7").assertTextContains("July")
        composeTestRule.onNodeWithTag("new_automatic_album_month_8").assertTextContains("August")
        composeTestRule.onAllNodesWithTag("new_automatic_album_month_3").assertCountEquals(0)

        composeTestRule.onNodeWithTag("new_automatic_album_date_back").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").assertExists()
        composeTestRule.onAllNodesWithTag("new_automatic_album_all_of_year").assertCountEquals(0)
    }

    @Test
    fun backInTheTopBarStepsOutOfTheOpenSectionOnly() {
        composeTestRule.setContent { PickerHarness() }

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_close_button").assertExists()
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_back_button").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").assertExists()

        composeTestRule.onNodeWithTag("new_automatic_album_place_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").assertExists()
    }

    @Test
    fun systemBackOnTheDateTabStepsOutOfThePlaceDrillBeforeClosing() {
        var cancelled = false
        composeTestRule.setContent { PickerHarness(onCancel = { cancelled = true }) }

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").assertExists()

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("new_automatic_album_place_tab").assertIsSelected()
        composeTestRule.onNodeWithTag("new_automatic_album_country_IT").assertExists()
        composeTestRule.onAllNodesWithTag("new_automatic_album_all_of_country").assertCountEquals(0)
        composeTestRule.runOnIdle { assertFalse(cancelled) }

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun choosingAMonthWritesAMonthRangeAndShowsAChipWithTheMonthAndYear() {
        var createdRule: AlbumRule.Evaluatable? = null
        composeTestRule.setContent { PickerHarness(onCreate = { _, rule -> createdRule = rule }) }

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_month_6").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_month_6").assertIsSelected()
        composeTestRule.onNodeWithTag("new_automatic_album_date_chip").assertTextContains("June 2025")
        composeTestRule.onNodeWithTag("new_automatic_album_match_count")
            .assertTextEquals("4 photos and videos will be in this album")
        composeTestRule.onNodeWithTag("new_automatic_album_create_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                AlbumRule.Evaluatable(
                    listOf(AlbumRuleClause.Time(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)))
                ),
                createdRule
            )
        }
    }

    @Test
    fun choosingAllOfAYearWritesAWholeYearRange() {
        var createdRule: AlbumRule.Evaluatable? = null
        composeTestRule.setContent { PickerHarness(onCreate = { _, rule -> createdRule = rule }) }

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_year_2024").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_year").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_create_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                AlbumRule.Evaluatable(
                    listOf(AlbumRuleClause.Time(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                ),
                createdRule
            )
        }
    }

    @Test
    fun choosingAPlaceChangesTheYearCountsAndChoosingAYearChangesTheCountryCounts() {
        composeTestRule.setContent { PickerHarness() }

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").assertTextContains("3")
        composeTestRule.onNodeWithTag("new_automatic_album_year_2024").assertTextContains("1")

        composeTestRule.onNodeWithTag("new_automatic_album_year_2024").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_year").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_place_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_place_back").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_country_IT").assertTextContains("1")
        composeTestRule.onAllNodesWithTag("new_automatic_album_country_FR").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_country_SG").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_match_count")
            .assertTextEquals("1 photo or video will be in this album")
    }

    @Test
    fun clearingEachChipRemovesOnlyThatChoiceAndTheMatchCountFollows() {
        composeTestRule.setContent {
            PickerHarness(
                initialSelection = AutomaticAlbumSelection(
                    region = AlbumRuleClause.Region(country = "IT"),
                    time = TimeSelection.Year(2025)
                )
            )
        }

        composeTestRule.onNodeWithTag("new_automatic_album_place_chip").assertExists()
        composeTestRule.onNodeWithTag("new_automatic_album_date_chip").assertTextContains("2025")
        composeTestRule.onNodeWithTag("new_automatic_album_match_count")
            .assertTextEquals("3 photos and videos will be in this album")

        composeTestRule.onNodeWithTag("new_automatic_album_place_chip").performClick()

        composeTestRule.onAllNodesWithTag("new_automatic_album_place_chip").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_date_chip").assertExists()
        composeTestRule.onNodeWithTag("new_automatic_album_match_count")
            .assertTextEquals("7 photos and videos will be in this album")

        composeTestRule.onNodeWithTag("new_automatic_album_date_chip").performClick()

        composeTestRule.onAllNodesWithTag("new_automatic_album_date_chip").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_match_count").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_hint")
            .assertTextEquals(appContext.getString(R.string.automatic_album_hint_choose))
    }

    @Test
    fun createIsDisabledUntilThereIsAValidNameAndAClause() {
        composeTestRule.setContent { PickerHarness(existingNames = listOf("Taken")) }

        composeTestRule.onNodeWithTag("new_automatic_album_create_button")
            .assertIsNotEnabled()
            .assertHasNoClickAction()

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_create_button")
            .assertIsEnabled()
            .assertHasClickAction()

        composeTestRule.onNodeWithTag("new_automatic_album_name_field").performTextReplacement("   ")
        composeTestRule.onNodeWithTag("new_automatic_album_hint")
            .assertTextEquals(appContext.getString(R.string.album_name_blank))
        composeTestRule.onNodeWithTag("new_automatic_album_create_button")
            .assertIsNotEnabled()
            .assertHasNoClickAction()

        composeTestRule.onNodeWithTag("new_automatic_album_name_field").performTextReplacement("taken")
        composeTestRule.onNodeWithTag("new_automatic_album_hint")
            .assertTextEquals(appContext.getString(R.string.album_name_duplicate))
        composeTestRule.onNodeWithTag("new_automatic_album_create_button")
            .assertIsNotEnabled()
            .assertHasNoClickAction()

        composeTestRule.onNodeWithTag("new_automatic_album_name_field").performTextReplacement("Fresh")
        composeTestRule.onNodeWithTag("new_automatic_album_create_button")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun disabledCreateCannotCreateAnything() {
        var created = false
        composeTestRule.setContent { PickerHarness(onCreate = { _, _ -> created = true }) }

        composeTestRule.onNodeWithTag("new_automatic_album_create_button")
            .performTouchInput { click() }

        composeTestRule.runOnIdle { assertFalse(created) }
    }

    @Test
    fun createReportsTheNameAndTheCombinedRuleInPlaceThenDateOrder() {
        var createdName: String? = null
        var createdRule: AlbumRule.Evaluatable? = null
        composeTestRule.setContent {
            PickerHarness(
                initialName = "My album 7",
                onCreate = { name, rule ->
                    createdName = name
                    createdRule = rule
                }
            )
        }

        openCountry("IT")
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_country").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_all_of_year").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_create_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals("My album 7", createdName)
            assertEquals(
                AlbumRule.Evaluatable(
                    listOf(
                        AlbumRuleClause.Region(country = "IT"),
                        AlbumRuleClause.Time(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))
                    )
                ),
                createdRule
            )
        }
    }

    @Test
    fun closeCancelsWithoutCreating() {
        var created = false
        var cancelled = false
        composeTestRule.setContent {
            PickerHarness(onCreate = { _, _ -> created = true }, onCancel = { cancelled = true })
        }

        composeTestRule.onNodeWithTag("new_automatic_album_close_button").performClick()

        composeTestRule.runOnIdle {
            assertTrue(cancelled)
            assertFalse(created)
        }
    }

    @Test
    fun loadingNamesShowsProgressAndNoLists() {
        composeTestRule.setContent {
            PickerHarness(status = AutomaticAlbumPickerStatus.LOADING_NAMES)
        }

        composeTestRule.onNodeWithTag("new_automatic_album_loading").assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.automatic_album_loading)).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("new_automatic_album_place_list").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_place_tab").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_date_tab").assertCountEquals(0)
    }

    @Test
    fun failedNamesShowAnErrorWithRetryAndNoLists() {
        var retries = 0
        composeTestRule.setContent {
            PickerHarness(status = AutomaticAlbumPickerStatus.NAMES_FAILED, onRetry = { retries++ })
        }

        composeTestRule.onNodeWithTag("new_automatic_album_names_failed").assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.automatic_album_names_failed)).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("new_automatic_album_place_list").assertCountEquals(0)

        composeTestRule.onNodeWithTag("new_automatic_album_retry").performClick()

        composeTestRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun waitingForMediaExplainsItAndNeverShowsAnEmptyList() {
        composeTestRule.setContent {
            PickerHarness(status = AutomaticAlbumPickerStatus.WAITING_FOR_MEDIA, join = null)
        }

        composeTestRule.onNodeWithTag("new_automatic_album_waiting").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(appContext.getString(R.string.automatic_album_waiting_for_media))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("new_automatic_album_place_list").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_date_list").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_place_empty").assertCountEquals(0)
    }

    @Test
    fun emptyLibraryShowsAnExplicitMessageInEachSection() {
        composeTestRule.setContent { PickerHarness(join = MediaIndexLiveJoin(emptyList())) }

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_places_empty))
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_dates_empty))
    }

    @Test
    fun libraryWithNoLocatedOrNoDatedItemsExplainsTheEmptySectionOnly() {
        val onlyDated = MediaIndexLiveJoin(listOf(joined(1, "2025-06-10T12:00:00Z", LocationState.NoCoordinates)))
        composeTestRule.setContent { PickerHarness(join = onlyDated) }

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty").assertExists()
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onAllNodesWithTag("new_automatic_album_date_empty").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").assertExists()
    }

    @Test
    fun anEmptySectionBecauseOfTheOtherSectionsChoiceSaysSo() {
        val join = MediaIndexLiveJoin(
            listOf(
                joined(1, "2024-06-10T12:00:00Z", place("IT", "16", "Florence")),
                joined(2, "2025-06-10T12:00:00Z", LocationState.NoCoordinates)
            )
        )
        composeTestRule.setContent {
            PickerHarness(join = join, initialSelection = AutomaticAlbumSelection(time = TimeSelection.Year(2025)))
        }

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_places_empty_for_date))
    }

    @Test
    fun createButtonClearsTheLastCountryRowAfterScrolling() {
        val manyCountries = (1..60).map { CountryEntry("C" + it.toString().padStart(2, '0'), "Country $it", it) }
        composeTestRule.setContent {
            StaticPicker(contentWith = AutomaticAlbumPickerContent(countries = manyCountries))
        }

        assertCanScrollLastItemAboveCreateButton("new_automatic_album_place_list", "new_automatic_album_country_C60")
    }

    @Test
    fun createButtonClearsTheLastYearRowAfterScrolling() {
        val manyYears = (1960..2025).map { YearFacet(it, 3) }.sortedByDescending { it.year }
        composeTestRule.setContent {
            StaticPicker(contentWith = AutomaticAlbumPickerContent(years = manyYears))
        }

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        assertCanScrollLastItemAboveCreateButton("new_automatic_album_date_list", "new_automatic_album_year_1960")
    }

    @Test
    fun largeFontCountryListScrollsItsLastRowClearOfTheCreateButton() {
        composeTestRule.setContent {
            AtFontScale(2f) { StaticPicker(contentWith = AutomaticAlbumPickerContent(countries = countryEntries(60))) }
        }

        assertEndOfListClearsCreateButton("new_automatic_album_place_list", "new_automatic_album_country_C60")
    }

    @Test
    fun largeFontRegionListScrollsItsLastRowClearOfTheCreateButton() {
        val regions = (1..40).map { RegionFacet("R" + it.toString().padStart(2, '0'), "Region $it", LAST_ROW_COUNT) }
        composeTestRule.setContent {
            AtFontScale(2f) {
                StaticPicker(
                    contentWith = AutomaticAlbumPickerContent(
                        countries = listOf(CountryEntry("IT", "Italy", 400)),
                        browsedCountry = CountryBreakdown(400, regions, PlaceGroup(0, emptyList()))
                    ),
                    selection = AutomaticAlbumSelection(time = TimeSelection.Year(2025), browsedCountry = "IT")
                )
            }
        }

        assertEndOfListClearsCreateButton("new_automatic_album_place_list", "new_automatic_album_region_R40")
    }

    @Test
    fun largeFontYearListScrollsItsLastRowClearOfTheCreateButton() {
        composeTestRule.setContent {
            AtFontScale(2f) { StaticPicker(contentWith = AutomaticAlbumPickerContent(years = yearFacets())) }
        }

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        assertEndOfListClearsCreateButton("new_automatic_album_date_list", "new_automatic_album_year_1960")
    }

    // Rows that fit on their own but would sit under the button: only the list's bottom padding makes them scrollable.
    @Test
    fun largeFontCountryListThatFitsButSitsUnderTheCreateButtonStillScrollsClear() {
        var rowCount by mutableIntStateOf(1)
        composeTestRule.setContent {
            AtFontScale(2f) {
                StaticPicker(contentWith = AutomaticAlbumPickerContent(countries = countryEntries(rowCount)))
            }
        }

        val lastRowTag = growUntilLastRowSitsUnderCreateButton(
            listTag = "new_automatic_album_place_list",
            maxRows = 60,
            setRowCount = { rowCount = it },
            rowTag = { "new_automatic_album_country_C" + it.toString().padStart(2, '0') }
        )
        assertEndOfListClearsCreateButton("new_automatic_album_place_list", lastRowTag)
    }

    @Test
    fun largeFontYearListThatFitsButSitsUnderTheCreateButtonStillScrollsClear() {
        var rowCount by mutableIntStateOf(1)
        composeTestRule.setContent {
            AtFontScale(2f) {
                StaticPicker(contentWith = AutomaticAlbumPickerContent(years = yearFacets(rowCount)))
            }
        }

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        val lastRowTag = growUntilLastRowSitsUnderCreateButton(
            listTag = "new_automatic_album_date_list",
            maxRows = 60,
            setRowCount = { rowCount = it },
            rowTag = { "new_automatic_album_year_${2025 - it + 1}" }
        )
        assertEndOfListClearsCreateButton("new_automatic_album_date_list", lastRowTag)
    }

    @Test
    fun layoutHoldsAtLargeFontScaleInRightToLeftWithLongNames() {
        val longName = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch and the surrounding villages"
        val countries = listOf(
            CountryEntry("GB", "The United Kingdom of Great Britain and Northern Ireland, overseas territories", 1234)
        )
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                StaticPicker(
                    contentWith = AutomaticAlbumPickerContent(countries = countries, matchCount = 1234),
                    selection = AutomaticAlbumSelection(
                        region = AlbumRuleClause.Region(country = "GB", place = longName),
                        time = TimeSelection.Month(2025, 6)
                    )
                )
            }
        }

        val root = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        listOf(
            "new_automatic_album_match_count",
            "new_automatic_album_place_chip",
            "new_automatic_album_date_chip",
            "new_automatic_album_place_tab",
            "new_automatic_album_date_tab",
            "new_automatic_album_country_GB",
            "new_automatic_album_create_button"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = boundsOf(tag)
            assertTrue(
                "$tag must fit inside the screen: $bounds in $root",
                bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom
            )
        }
    }

    @Test
    fun drilledLevelAndIndexBandHoldAtLargeFontScaleInRightToLeft() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                PickerHarness(
                    indexBand = notIndexedBand(count = 1234),
                    initialSelection = AutomaticAlbumSelection(
                        region = AlbumRuleClause.Region(country = "IT"),
                        browsedCountry = "IT"
                    )
                )
            }
        }

        val root = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        // The Status area can push the first rows below the fold at this scale, so list items are scrolled to.
        listOf(
            "new_automatic_album_back_button",
            "new_automatic_album_status_toggle",
            "new_automatic_album_index_band",
            "new_automatic_album_index_now",
            "new_automatic_album_place_back",
            "new_automatic_album_all_of_country"
        ).forEach { tag ->
            if (tag != "new_automatic_album_back_button") {
                composeTestRule.onNodeWithTag("new_automatic_album_place_list").performScrollToNode(hasTestTag(tag))
            }
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = boundsOf(tag)
            assertTrue(
                "$tag must fit inside the screen: $bounds in $root",
                bounds.left >= root.left && bounds.right <= root.right && bounds.bottom <= root.bottom
            )
        }
    }

    @Test
    fun nameFieldIsPrefilledAndEditable() {
        composeTestRule.setContent { PickerHarness(initialName = "My album 3") }

        composeTestRule.onNodeWithTag("new_automatic_album_name_field").assertTextContains("My album 3")
        composeTestRule.onNodeWithTag("new_automatic_album_name_field").performTextReplacement("Trips")
        composeTestRule.onNodeWithTag("new_automatic_album_name_field").assertTextContains("Trips")
    }

    @Test
    fun noChipsAndNoMatchCountBeforeAnythingIsChosen() {
        composeTestRule.setContent { PickerHarness() }

        composeTestRule.onAllNodesWithTag("new_automatic_album_place_chip").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_date_chip").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_match_count").assertCountEquals(0)
    }

    // The Status area is the first item of every picker list: the header, then the Index band.
    @Test
    fun statusAreaIsFirstOnBothTabsWithTheBandAboveTheList() {
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand()) }

        composeTestRule.onNodeWithTag("new_automatic_album_status_area").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_automatic_album_index_band").assertIsDisplayed()
        assertTrue(rowTop("new_automatic_album_status_toggle") < rowTop("new_automatic_album_index_band"))
        assertTrue(rowTop("new_automatic_album_index_band") < rowTop("new_automatic_album_country_IT"))

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_automatic_album_index_band").assertIsDisplayed()
        assertTrue(rowTop("new_automatic_album_status_toggle") < rowTop("new_automatic_album_index_band"))
        assertTrue(rowTop("new_automatic_album_index_band") < rowTop("new_automatic_album_year_2025"))
    }

    @Test
    fun statusAreaStaysFirstWhenAPlaceListIsOpened() {
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand()) }

        openCountry("IT")

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_automatic_album_index_band").assertIsDisplayed()
        assertTrue(rowTop("new_automatic_album_index_band") < rowTop("new_automatic_album_place_back"))
    }

    @Test
    fun notIndexedBandShowsItsCountInTheRowText() {
        var band by mutableStateOf(notIndexedBand(count = 5))
        composeTestRule.setContent { PickerHarness(indexBand = band) }

        composeTestRule.onNodeWithText(notIndexedText(5)).assertIsDisplayed()
        assertEquals("5 media files are not indexed yet. Click to index", notIndexedText(5))

        composeTestRule.runOnIdle { band = notIndexedBand(count = 1) }

        composeTestRule.onNodeWithText(notIndexedText(1)).assertIsDisplayed()
        assertEquals("1 media file is not indexed yet. Click to index", notIndexedText(1))
    }

    @Test
    fun bandAndItsRowTextAreAbsentWhenNothingWaitsAndNothingRuns() {
        composeTestRule.setContent { PickerHarness(indexBand = AutomaticAlbumIndexBand.Hidden) }

        composeTestRule.onAllNodesWithTag("new_automatic_album_index_band").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_now").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("not indexed yet", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.settings_index_now)).assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_now").assertCountEquals(0)
    }

    @Test
    fun statusAreaAppearsAndDisappearsWithTheIndexBand() {
        var band by mutableStateOf<AutomaticAlbumIndexBand>(AutomaticAlbumIndexBand.Hidden)
        composeTestRule.setContent { PickerHarness(join = fullyKnownJoin(), indexBand = band) }

        composeTestRule.onAllNodesWithTag("new_automatic_album_status_area").assertCountEquals(0)

        composeTestRule.runOnIdle { band = notIndexedBand(count = 3) }

        composeTestRule.onNodeWithTag("new_automatic_album_status_area").assertIsDisplayed()
        composeTestRule.onNodeWithText(notIndexedText(3)).assertIsDisplayed()

        composeTestRule.runOnIdle { band = AutomaticAlbumIndexBand.Hidden }

        composeTestRule.onAllNodesWithTag("new_automatic_album_status_area").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_now").assertCountEquals(0)
    }

    @Test
    fun indexBandIsAbsentUntilThePickerIsReady() {
        composeTestRule.setContent {
            PickerHarness(status = AutomaticAlbumPickerStatus.LOADING_NAMES, indexBand = notIndexedBand())
        }

        composeTestRule.onAllNodesWithTag("new_automatic_album_status_area").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_band").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_now").assertCountEquals(0)
    }

    @Test
    fun indexNowRowIsOneButtonLabelledIndexNowWithAnIconOnlyStartSlot() {
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand()) }

        val config = composeTestRule.onNodeWithTag("new_automatic_album_index_now")
            .assertHasClickAction()
            .fetchSemanticsNode()
            .config

        assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role))
        assertEquals(appContext.getString(R.string.settings_index_now), config[SemanticsActions.OnClick].label)
        // The icon is decorative: the row text and the click label already say everything.
        assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription))
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.settings_index_now)).assertCountEquals(0)
    }

    @Test
    fun indexNowCallsBackWhenNothingIsIndexed() {
        assertIndexNowTapIsReported(notIndexedBand(count = 12, nothingIndexed = true))
    }

    @Test
    fun indexNowCallsBackWhenPartlyIndexed() {
        assertIndexNowTapIsReported(notIndexedBand(count = 1))
    }

    // The text is part of the target: a tap on the icon slot and a tap on the words both start the scan.
    @Test
    fun tappingTheIconSideOrTheTextSideOfTheRowReportsIndexNow() {
        var taps = 0
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand(), onIndexNow = { taps++ }) }

        composeTestRule.onNodeWithTag("new_automatic_album_index_now")
            .performTouchInput { click(percentOffset(0.03f, 0.5f)) }
        composeTestRule.onNodeWithTag("new_automatic_album_index_now")
            .performTouchInput { click(percentOffset(0.6f, 0.5f)) }

        composeTestRule.runOnIdle { assertEquals(2, taps) }
    }

    @Test
    fun indexNowIsAlsoOfferedOnTheDateTab() {
        var taps = 0
        composeTestRule.setContent {
            PickerHarness(indexBand = notIndexedBand(), onIndexNow = { taps++ })
        }

        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_index_now").performClick()

        composeTestRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun runningBandHasNoClickActionAndShowsIndeterminateProgressWithoutACount() {
        var taps = 0
        composeTestRule.setContent {
            PickerHarness(indexBand = AutomaticAlbumIndexBand.Running, onIndexNow = { taps++ })
        }

        composeTestRule.onNodeWithTag("new_automatic_album_index_now")
            .assertHasNoClickAction()
            .assertTextEquals(appContext.getString(R.string.automatic_album_index_running))
        assertTrue("The running text carries no count", noticeText("new_automatic_album_index_now").none { it.isDigit() })
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.settings_index_now)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("not indexed yet", substring = true).assertCountEquals(0)

        composeTestRule.onNodeWithTag("new_automatic_album_index_now").performClick()

        composeTestRule.runOnIdle { assertEquals(0, taps) }
    }

    // A pass over a library whose count has just reached zero still has something to say.
    @Test
    fun runningBandShowsEvenWhenNothingElseInTheAreaDoes() {
        composeTestRule.setContent { PickerHarness(join = fullyKnownJoin(), indexBand = AutomaticAlbumIndexBand.Running) }

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_automatic_album_index_band").assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun idleBandShowsNoProgressIndicator() {
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand()) }

        composeTestRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
    }

    @Test
    fun notIndexedRowPutsTheIconAtTheStartSideInLeftToRight() {
        assertTextSitsBesideAStartSlot(LayoutDirection.Ltr, notIndexedBand())
    }

    @Test
    fun notIndexedRowPutsTheIconAtTheStartSideInRightToLeft() {
        assertTextSitsBesideAStartSlot(LayoutDirection.Rtl, notIndexedBand())
    }

    @Test
    fun runningProgressIndicatorSitsAtTheStartSideInLeftToRight() {
        assertTextSitsBesideAStartSlot(LayoutDirection.Ltr, AutomaticAlbumIndexBand.Running)
    }

    @Test
    fun runningProgressIndicatorSitsAtTheStartSideInRightToLeft() {
        assertTextSitsBesideAStartSlot(LayoutDirection.Rtl, AutomaticAlbumIndexBand.Running)
    }

    @Test
    fun unlocatedAndUndatedMediaAloneDoNotOpenTheStatusArea() {
        composeTestRule.setContent { PickerHarness(join = libraryJoin(), indexBand = AutomaticAlbumIndexBand.Hidden) }

        composeTestRule.onAllNodesWithTag("new_automatic_album_status_toggle").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_status_area").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onAllNodesWithTag("new_automatic_album_status_toggle").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_status_area").assertCountEquals(0)
    }

    @Test
    fun collapsingLeavesOnlyTheHeaderAndExpandingRestoresTheBand() {
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand()) }

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_band").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_now").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_country_IT").assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_index_band").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_automatic_album_index_now").assertIsDisplayed()
    }

    @Test
    fun headerIsOneButtonThatSaysExpandedThenCollapsed() {
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand()) }

        val expanded = composeTestRule.onNodeWithTag("new_automatic_album_status_toggle")
            .assertHasClickAction()
            .fetchSemanticsNode()
            .config
        assertEquals(Role.Button, expanded.getOrNull(SemanticsProperties.Role))
        assertEquals(appContext.getString(R.string.state_expanded), expanded.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(appContext.getString(R.string.action_collapse), expanded[SemanticsActions.OnClick].label)
        composeTestRule.onNodeWithText(appContext.getString(R.string.automatic_album_status_area_title)).assertIsDisplayed()

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").performClick()

        val collapsed = composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").fetchSemanticsNode().config
        assertEquals(appContext.getString(R.string.state_collapsed), collapsed.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(appContext.getString(R.string.action_expand), collapsed[SemanticsActions.OnClick].label)
    }

    @Test
    fun collapsedStateSurvivesATabSwitchAndAPickerDrillInAndOut() {
        composeTestRule.setContent { PickerHarness(indexBand = notIndexedBand()) }

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()

        assertStatusToggleSays(R.string.state_collapsed)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_band").assertCountEquals(0)

        composeTestRule.onNodeWithTag("new_automatic_album_year_2025").performClick()

        assertStatusToggleSays(R.string.state_collapsed)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_band").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_date_back").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_place_tab").performClick()
        openCountry("IT")

        assertStatusToggleSays(R.string.state_collapsed)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_band").assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_place_back").performClick()

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()

        assertStatusToggleSays(R.string.state_expanded)
        composeTestRule.onNodeWithTag("new_automatic_album_index_band").assertIsDisplayed()
    }

    @Test
    fun collapsedStateSurvivesRotation() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent { PickerHarness(indexBand = notIndexedBand()) }

        composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").performClick()
        restoration.emulateSavedInstanceStateRestore()

        assertStatusToggleSays(R.string.state_collapsed)
        composeTestRule.onAllNodesWithTag("new_automatic_album_index_band").assertCountEquals(0)
    }

    @Test
    fun nothingIndexedReplacesTheEmptyMessagesOnBothTabs() {
        composeTestRule.setContent {
            PickerHarness(join = MediaIndexLiveJoin(emptyList()), indexBand = notIndexedBand(count = 3, nothingIndexed = true))
        }
        val notIndexed = appContext.getString(R.string.automatic_album_not_indexed_empty)

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty").assertTextEquals(notIndexed)
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_empty").assertTextEquals(notIndexed)
    }

    // First install: a running pass over an empty index must not claim that no places or dates exist.
    @Test
    fun runningPassKeepsTheNotIndexedEmptyMessageOnBothTabs() {
        composeTestRule.setContent {
            PickerHarness(join = MediaIndexLiveJoin(emptyList()), indexBand = AutomaticAlbumIndexBand.Running)
        }
        val notIndexed = appContext.getString(R.string.automatic_album_not_indexed_empty)

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty").assertTextEquals(notIndexed)
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.automatic_album_places_empty)).assertCountEquals(0)
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_empty").assertTextEquals(notIndexed)
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.automatic_album_dates_empty)).assertCountEquals(0)
    }

    @Test
    fun emptyListMessageIsHonestAgainOnceTheRunEnds() {
        var band by mutableStateOf<AutomaticAlbumIndexBand>(AutomaticAlbumIndexBand.Running)
        composeTestRule.setContent { PickerHarness(join = MediaIndexLiveJoin(emptyList()), indexBand = band) }

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_not_indexed_empty))

        composeTestRule.runOnIdle { band = AutomaticAlbumIndexBand.Hidden }

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_places_empty))
    }

    // Only the status text announces: the whole row is a button, and its tap replaces the row with the running one.
    @Test
    fun indexBandAnnouncesItsStatusTextPolitelyAndNotTheBandItself() {
        var band by mutableStateOf<AutomaticAlbumIndexBand>(AutomaticAlbumIndexBand.Running)
        composeTestRule.setContent { PickerHarness(indexBand = band) }

        val runningText = composeTestRule.onNodeWithText(
            appContext.getString(R.string.automatic_album_index_running),
            useUnmergedTree = true
        )
        assertEquals(LiveRegionMode.Polite, runningText.fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(null, indexRowLiveRegion())

        composeTestRule.runOnIdle { band = notIndexedBand() }

        val idleText = composeTestRule.onNodeWithText(notIndexedText(5), useUnmergedTree = true)
        assertEquals(LiveRegionMode.Polite, idleText.fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(null, indexRowLiveRegion())
    }

    private fun indexRowLiveRegion(): LiveRegionMode? =
        composeTestRule.onNodeWithTag("new_automatic_album_index_now", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.LiveRegion)

    @Test
    fun otherBandsKeepTheCurrentEmptyMessages() {
        composeTestRule.setContent {
            PickerHarness(join = MediaIndexLiveJoin(emptyList()), indexBand = AutomaticAlbumIndexBand.Hidden)
        }

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_places_empty))
        composeTestRule.onNodeWithTag("new_automatic_album_date_tab").performClick()
        composeTestRule.onNodeWithTag("new_automatic_album_date_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_dates_empty))
    }

    @Test
    fun somethingIndexedWithMediaWaitingKeepsTheCurrentEmptyMessages() {
        composeTestRule.setContent {
            PickerHarness(join = MediaIndexLiveJoin(emptyList()), indexBand = notIndexedBand(count = 4, nothingIndexed = false))
        }

        composeTestRule.onNodeWithTag("new_automatic_album_place_empty")
            .assertTextEquals(appContext.getString(R.string.automatic_album_places_empty))
    }

    private fun notIndexedBand(count: Int = 5, nothingIndexed: Boolean = false): AutomaticAlbumIndexBand =
        AutomaticAlbumIndexBand.NotIndexed(count = count, nothingIndexed = nothingIndexed)

    private fun notIndexedText(count: Int): String =
        appContext.resources.getQuantityString(R.plurals.automatic_album_not_indexed_count, count, count)

    private fun fullyKnownJoin() =
        MediaIndexLiveJoin(listOf(joined(1, "2025-06-10T12:00:00Z", place("IT", "16", "Florence"))))

    private fun assertStatusToggleSays(stateRes: Int) {
        val config = composeTestRule.onNodeWithTag("new_automatic_album_status_toggle").fetchSemanticsNode().config
        assertEquals(appContext.getString(stateRes), config.getOrNull(SemanticsProperties.StateDescription))
    }

    // The start slot is not findable on its own (the icon is decorative), so the row's free space is measured instead.
    private fun assertTextSitsBesideAStartSlot(direction: LayoutDirection, band: AutomaticAlbumIndexBand) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) { PickerHarness(indexBand = band) }
        }

        val row = boundsOf("new_automatic_album_index_now")
        val text = composeTestRule
            .onNode(hasAnyAncestor(hasTestTag("new_automatic_album_index_now")) and hasText("", substring = true), useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val slotPx = with(composeTestRule.density) { 48.dp.toPx() }

        if (direction == LayoutDirection.Ltr) {
            assertTrue("Text must start after the 48dp icon slot: $text in $row", text.left - row.left >= slotPx - 1f)
            assertTrue("Text must run to the row's end: $text in $row", row.right - text.right < 1f)
        } else {
            assertTrue("Text must start after the 48dp icon slot: $text in $row", row.right - text.right >= slotPx - 1f)
            assertTrue("Text must run to the row's end: $text in $row", text.left - row.left < 1f)
        }
        if (band == AutomaticAlbumIndexBand.Running) {
            val progress = composeTestRule
                .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            if (direction == LayoutDirection.Ltr) {
                assertTrue("Progress must sit before the text: $progress vs $text", progress.right <= text.left)
            } else {
                assertTrue("Progress must sit before the text: $progress vs $text", progress.left >= text.right)
            }
        }
    }

    private fun assertIndexNowTapIsReported(band: AutomaticAlbumIndexBand) {
        var taps = 0
        composeTestRule.setContent { PickerHarness(indexBand = band, onIndexNow = { taps++ }) }

        composeTestRule.onNodeWithTag("new_automatic_album_index_now")
            .assertHasClickAction()
            .performClick()

        composeTestRule.runOnIdle { assertEquals(1, taps) }
    }

    // The real content builder over a real join, driven by the picker's own actions, so counts really cross-filter.
    @Composable
    private fun PickerHarness(
        initialName: String = "My album 1",
        existingNames: List<String> = emptyList(),
        status: AutomaticAlbumPickerStatus = AutomaticAlbumPickerStatus.READY,
        join: MediaIndexLiveJoin? = libraryJoin(),
        initialSelection: AutomaticAlbumSelection = AutomaticAlbumSelection(),
        indexBand: AutomaticAlbumIndexBand = AutomaticAlbumIndexBand.Hidden,
        onIndexNow: () -> Unit = {},
        onCreate: (String, AlbumRule.Evaluatable) -> Unit = { _, _ -> },
        onCancel: () -> Unit = {},
        onRetry: () -> Unit = {}
    ) {
        var name by remember { mutableStateOf(initialName) }
        var selection by remember { mutableStateOf(initialSelection) }

        val rule = selection.toRule()
        val content = if (status == AutomaticAlbumPickerStatus.READY && join != null) {
            AutomaticAlbumPickerContent.build(join, zone, names, locale, selection)
        } else {
            AutomaticAlbumPickerContent()
        }
        val ui = AutomaticAlbumPickerUi(
            status = status,
            name = name,
            selection = selection,
            content = content,
            rule = rule,
            canCreate = rule != null && AlbumNaming.isAvailable(name.trim(), existingNames)
        )

        NewAutomaticAlbumContent(
            ui = ui,
            indexBand = indexBand,
            isNameDuplicate = existingNames.any { it.trim().equals(name.trim(), ignoreCase = true) },
            onNameChange = { name = it },
            onSelectRegion = { selection = selection.copy(region = it) },
            onSelectTime = { selection = selection.copy(time = it) },
            onBrowseCountry = { selection = selection.copy(browsedCountry = it, browsedAdmin1 = null) },
            onBrowseRegion = { selection = selection.copy(browsedAdmin1 = it) },
            onBrowseYear = { selection = selection.copy(browsedYear = it) },
            onRetryLoadingNames = onRetry,
            onIndexNow = onIndexNow,
            onCreate = { rule?.let { onCreate(name, it) } },
            onCancel = onCancel
        )
    }

    // A ready screen over lists built by hand, for the layout tests that need more rows than a library gives.
    @Composable
    private fun StaticPicker(
        contentWith: AutomaticAlbumPickerContent,
        selection: AutomaticAlbumSelection = AutomaticAlbumSelection(time = TimeSelection.Year(2025))
    ) {
        NewAutomaticAlbumContent(
            ui = AutomaticAlbumPickerUi(
                status = AutomaticAlbumPickerStatus.READY,
                name = "My album 1",
                selection = selection,
                content = contentWith,
                rule = selection.toRule(),
                canCreate = true
            ),
            indexBand = AutomaticAlbumIndexBand.Hidden,
            isNameDuplicate = false,
            onNameChange = {},
            onSelectRegion = {},
            onSelectTime = {},
            onBrowseCountry = {},
            onBrowseRegion = {},
            onBrowseYear = {},
            onRetryLoadingNames = {},
            onIndexNow = {},
            onCreate = {},
            onCancel = {}
        )
    }

    private fun openCountry(code: String) {
        composeTestRule.onNodeWithTag("new_automatic_album_country_$code").performClick()
    }

    private fun boundsOf(tag: String): Rect = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun rowTop(tag: String): Float = boundsOf(tag).top

    private fun noticeText(tag: String): String = composeTestRule
        .onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
        .joinToString(" ") { it.text }

    private fun place(country: String, region: String?, place: String?) =
        LocationState.Resolved(PlaceValues(country, region, place))

    private fun joined(id: Int, dateTaken: String?, location: LocationState): JoinedMedia {
        val item = MediaItem(
            uri = Uri.parse("content://media/external/images/media/$id"),
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
            DateTakenSortState.IndexedWithDate(Instant.parse(dateTaken).toEpochMilli())
        }
        return JoinedMedia(item, date, location)
    }

    private fun assertCanScrollLastItemAboveCreateButton(containerTag: String, targetTag: String) {
        var lastTargetBounds: Rect? = null
        var createButtonBounds: Rect? = null
        var scrolledClear = false

        for (attempt in 0 until 30) {
            val targetNodes = composeTestRule
                .onAllNodesWithTag(targetTag, useUnmergedTree = true)
                .fetchSemanticsNodes()

            if (targetNodes.isEmpty()) {
                composeTestRule.onNodeWithTag(containerTag).performTouchInput { swipeUp() }
                continue
            }

            val targetBounds = targetNodes.first().boundsInRoot
            val buttonBounds = boundsOf("new_automatic_album_create_button")
            lastTargetBounds = targetBounds
            createButtonBounds = buttonBounds

            if (targetBounds.bottom <= buttonBounds.top) {
                scrolledClear = true
                break
            }

            composeTestRule.onNodeWithTag(containerTag).performTouchInput { swipeUp() }
        }

        assertTrue(
            "Expected to be able to scroll the last row fully above the Create button",
            scrolledClear
        )
        assertFalse(
            "Expected the Create button not to overlap the last row after scrolling",
            boundsOverlap(lastTargetBounds!!, createButtonBounds!!)
        )
    }

    @Composable
    private fun AtFontScale(scale: Float, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, fontScale = scale),
            content = content
        )
    }

    private fun countryEntries(rows: Int) =
        (1..rows).map { CountryEntry("C" + it.toString().padStart(2, '0'), "Country $it", LAST_ROW_COUNT) }

    // Newest first, like the real list: the last row is the oldest year.
    private fun yearFacets(rows: Int = 66) = (0 until rows).map { YearFacet(2025 - it, LAST_ROW_COUNT) }

    // Adds one row at a time until the last one first reaches the Create button, while the rows still fit unscrolled.
    private fun growUntilLastRowSitsUnderCreateButton(
        listTag: String,
        maxRows: Int,
        setRowCount: (Int) -> Unit,
        rowTag: (Int) -> String
    ): String {
        for (rows in 1..maxRows) {
            composeTestRule.runOnIdle { setRowCount(rows) }
            composeTestRule.waitForIdle()

            val lastRow = boundsOf(rowTag(rows))
            if (lastRow.bottom > boundsOf("new_automatic_album_create_button").top) {
                assertTrue(
                    "The rows should still fit in the list without scrolling: $lastRow in ${boundsOf(listTag)}",
                    lastRow.bottom <= boundsOf(listTag).bottom
                )
                return rowTag(rows)
            }
        }
        throw AssertionError("No row count up to $maxRows put the last row under the Create button")
    }

    // Swipes until the last row stops moving, then checks the row and its count are clear of the button.
    private fun assertEndOfListClearsCreateButton(containerTag: String, lastRowTag: String) {
        var previousTop: Float? = null
        for (attempt in 0 until 40) {
            val nodes = composeTestRule.onAllNodesWithTag(lastRowTag, useUnmergedTree = true).fetchSemanticsNodes()
            val top = nodes.firstOrNull()?.boundsInRoot?.top
            if (top != null && top == previousTop) break
            previousTop = top
            composeTestRule.onNodeWithTag(containerTag).performTouchInput { swipeUp() }
            composeTestRule.waitForIdle()
        }

        val rowBounds = boundsOf(lastRowTag)
        val buttonBounds = boundsOf("new_automatic_album_create_button")
        val countBounds = composeTestRule
            .onNode(
                hasText(LAST_ROW_COUNT.toString()) and hasAnyAncestor(hasTestTag(lastRowTag)),
                useUnmergedTree = true
            )
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "The last row must end above the Create button at the end of the list: $rowBounds vs $buttonBounds",
            rowBounds.bottom <= buttonBounds.top
        )
        assertFalse(
            "The Create button must not cover the last row's count: $countBounds vs $buttonBounds",
            boundsOverlap(countBounds, buttonBounds)
        )
    }

    private fun boundsOverlap(first: Rect, second: Rect): Boolean {
        return first.left < second.right &&
            first.right > second.left &&
            first.top < second.bottom &&
            first.bottom > second.top
    }

    private companion object {
        // One value for every row, so the count node is findable under the last row whatever the list.
        const val LAST_ROW_COUNT = 7
    }
}
