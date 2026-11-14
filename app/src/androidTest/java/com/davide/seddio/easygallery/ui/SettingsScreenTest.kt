package com.davide.seddio.easygallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.davide.seddio.easygallery.data.MediaIndexingWorkStatus
import com.davide.seddio.easygallery.data.MediaIndexRunOutcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GalleryViewModel = mockk(relaxed = true)
    private val billingViewModel: BillingViewModel = mockk(relaxed = true)
    private val mediaIndexingViewModel: MediaIndexingViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(indexingState())
    }

    @Test
    fun settingsScreenDisplaysAllSections() {
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                billingViewModel = billingViewModel,
                mediaIndexingViewModel = mediaIndexingViewModel
            )
        }

        composeTestRule.onNodeWithText("General").assertIsDisplayed()
        composeTestRule.onNodeWithText("App").assertIsDisplayed()
    }

    @Test
    fun settingsScreenDisplaysAllItems() {
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                billingViewModel = billingViewModel,
                mediaIndexingViewModel = mediaIndexingViewModel
            )
        }

        composeTestRule.onNodeWithText("Manage excluded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share app").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rate app").assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy & Policy").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Terms").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Customer Support").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsTopBarKeepsTheTitleAndHasNoBackButton() {
        setSettingsContent()

        composeTestRule.onAllNodesWithText("Settings").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun clickingManageExcludedCallsViewModel() {
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                billingViewModel = billingViewModel,
                mediaIndexingViewModel = mediaIndexingViewModel
            )
        }

        composeTestRule.onNodeWithText("Manage excluded").performClick()

        verify { viewModel.setManageExcludedMode(true) }
    }

    @Test
    fun clickingCustomerSupportCallsCallback() {
        val onContactSupportClick = mockk<(android.content.Context) -> Unit>(relaxed = true)
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                billingViewModel = billingViewModel,
                mediaIndexingViewModel = mediaIndexingViewModel,
                onContactSupportClick = onContactSupportClick
            )
        }

        composeTestRule.onNodeWithText("Customer Support").performClick()

        verify { onContactSupportClick(any()) }
    }

    @Test
    fun mediaIndexAreaDistinguishesOutstandingWithDateAndWithoutDate() {
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(
                    workStatus = MediaIndexingWorkStatus.RUNNING,
                    selectedRows = 12,
                    processedRows = 5,
                    indexedWithDateCount = 3,
                    indexedWithoutDateCount = 2,
                    notYetIndexedCount = 4
                ),
                onIndexNow = {}
            )
        }

        composeTestRule.onNodeWithText("7 items still to scan").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 media items have an indexed date").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 items have no usable date information").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 media items are not yet indexed").assertIsDisplayed()
    }

    @Test
    fun mediaIndexAreaShowsOneItemWithoutDateInTheSingularForm() {
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(indexedWithoutDateCount = 1),
                onIndexNow = {}
            )
        }

        composeTestRule.onNodeWithText("1 item has no usable date information").assertIsDisplayed()
    }

    @Test
    fun mediaIndexAreaShowsTheNoLocationCountRightAfterTheNoDateCount() {
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(
                    indexedWithoutDateCount = 2,
                    indexedWithoutLocationCount = 5,
                    notYetIndexedCount = 4
                ),
                onIndexNow = {}
            )
        }

        val withoutDate = composeTestRule.onNodeWithText("2 items have no usable date information")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val withoutLocation = composeTestRule.onNodeWithText("5 items have no usable location information")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val notYetIndexed = composeTestRule.onNodeWithText("4 media items are not yet indexed")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertTrue(withoutDate.bottom <= withoutLocation.top)
        assertTrue(withoutLocation.bottom <= notYetIndexed.top)
    }

    @Test
    fun mediaIndexAreaShowsOneItemWithoutLocationInTheSingularForm() {
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(indexedWithoutLocationCount = 1),
                onIndexNow = {}
            )
        }

        composeTestRule.onNodeWithText("1 item has no usable location information").assertIsDisplayed()
    }

    @Test
    fun mediaIndexAreaStillShowsAZeroNoLocationCount() {
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(indexedWithoutLocationCount = 0),
                onIndexNow = {}
            )
        }

        composeTestRule.onNodeWithText("0 items have no usable location information").assertIsDisplayed()
    }

    @Test
    fun mediaIndexAreaSaysLocationAccessIsOffInPlaceOfTheNoLocationCount() {
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(indexedWithoutLocationCount = 5, locationAccessGranted = false),
                onIndexNow = {}
            )
        }

        composeTestRule.onNodeWithText("Location access is off", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("5 items have no usable location information").assertDoesNotExist()
        composeTestRule.onNodeWithText("0 items have no usable date information").assertIsDisplayed()
    }

    @Test
    fun settingsScreenAsksTheViewModelToReadLocationAccessAgain() {
        setSettingsContent()
        composeTestRule.waitForIdle()

        verify { mediaIndexingViewModel.refreshLocationAccess() }
    }

    @Test
    fun indexNowActionHasNoClickActionWhileRunning() {
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(workStatus = MediaIndexingWorkStatus.RUNNING),
                onIndexNow = {}
            )
        }

        composeTestRule.onNodeWithTag("media_index_now_action")
            .assertIsNotEnabled()
            .assertHasNoClickAction()
    }

    @Test
    fun indexNowActionRunsWhenIdle() {
        var clicks = 0
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(),
                onIndexNow = { clicks++ }
            )
        }

        composeTestRule.onNodeWithTag("media_index_now_action").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun indexNowActionRunsWhileWaiting() {
        var clicks = 0
        composeTestRule.setContent {
            MediaIndexSettingsArea(
                state = indexingState(workStatus = MediaIndexingWorkStatus.WAITING),
                onIndexNow = { clicks++ }
            )
        }

        composeTestRule.onNodeWithTag("media_index_now_action")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun placeDataRowShowsTitleAndShortCreditWithoutATap() {
        setSettingsContent()

        composeTestRule.onNodeWithTag("place_data_row")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Place data", "Place names: GeoNames, CC BY 4.0")
    }

    @Test
    fun placeDataRowIsInTheLegalSectionAfterPrivacyAndTerms() {
        setSettingsContent()

        composeTestRule.onNodeWithTag("place_data_row").performScrollTo()
        val legalTop = composeTestRule.onNodeWithText("Legal").fetchSemanticsNode().boundsInRoot.top
        val termsBottom = composeTestRule.onNodeWithText("Terms").fetchSemanticsNode().boundsInRoot.bottom
        val placeDataTop = composeTestRule.onNodeWithTag("place_data_row").fetchSemanticsNode().boundsInRoot.top

        assertTrue(legalTop < termsBottom)
        assertTrue(termsBottom <= placeDataTop)
    }

    @Test
    fun tappingPlaceDataRowOpensDialogWithTheFullNotice() {
        setSettingsContent()

        composeTestRule.onNodeWithTag("place_data_notice").assertDoesNotExist()
        composeTestRule.onNodeWithTag("place_data_row").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("place_data_notice").assertExists()
        assertNoticeContains("GeoNames")
        assertNoticeContains("CC BY 4.0")
        assertNoticeContains("https://www.geonames.org/")
        assertNoticeContains("https://creativecommons.org/licenses/by/4.0/")
        assertNoticeContains("was modified")
        assertNoticeContains("cities15000")
        assertNoticeContains("admin1CodesASCII")
        assertNoticeContains("countryInfo")
        assertNoticeContains("as is")
        assertNoticeContains("without warranty or any representation of accuracy, timeliness or completeness")
    }

    @Test
    fun placeDataDialogUrlsAreTappable() {
        setSettingsContent()
        composeTestRule.onNodeWithTag("place_data_row").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("place_data_source_link").assertHasClickAction()
        composeTestRule.onNodeWithTag("place_data_licence_link").assertHasClickAction()
    }

    @Test
    fun dismissingPlaceDataDialogClosesIt() {
        setSettingsContent()
        composeTestRule.onNodeWithTag("place_data_row").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("place_data_notice").assertExists()

        composeTestRule.onNodeWithText("OK").performClick()

        composeTestRule.onNodeWithTag("place_data_notice").assertDoesNotExist()
        composeTestRule.onNodeWithTag("place_data_row").assertExists()
    }

    private fun setSettingsContent() {
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                billingViewModel = billingViewModel,
                mediaIndexingViewModel = mediaIndexingViewModel
            )
        }
    }

    // The same text also sits behind the dialog (the row credit), so match inside the notice only.
    private fun assertNoticeContains(text: String) {
        composeTestRule
            .onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("place_data_notice")))
            .onFirst()
            .assertExists()
    }

    companion object {
        private fun indexingState(
            workStatus: MediaIndexingWorkStatus = MediaIndexingWorkStatus.IDLE,
            selectedRows: Int = 0,
            processedRows: Int = 0,
            indexedWithDateCount: Int = 0,
            indexedWithoutDateCount: Int = 0,
            notYetIndexedCount: Int = 0,
            indexedWithoutLocationCount: Int = 0,
            locationAccessGranted: Boolean = true
        ) = MediaIndexingUiState(
            workStatus = workStatus,
            isRunning = workStatus == MediaIndexingWorkStatus.RUNNING,
            selectedRows = selectedRows,
            processedRows = processedRows,
            indexedRows = processedRows,
            skippedRows = 0,
            deletedRows = 0,
            healedRows = 0,
            indexedItemsCount = indexedWithDateCount + indexedWithoutDateCount,
            indexedWithDateCount = indexedWithDateCount,
            indexedWithoutDateCount = indexedWithoutDateCount,
            notYetIndexedCount = notYetIndexedCount,
            lastRunStartedAtMillis = null,
            lastRunFinishedAtMillis = null,
            lastSuccessfulRunFinishedAtMillis = null,
            lastRunOutcome = MediaIndexRunOutcome.SUCCESS,
            lastRunErrorMessage = null,
            indexedWithoutLocationCount = indexedWithoutLocationCount,
            locationAccessGranted = locationAccessGranted
        )
    }
}
