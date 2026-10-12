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
        composeTestRule.onNodeWithText("2 media items are indexed without a date").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 media items are not yet indexed").assertIsDisplayed()
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

    companion object {
        private fun indexingState(
            workStatus: MediaIndexingWorkStatus = MediaIndexingWorkStatus.IDLE,
            selectedRows: Int = 0,
            processedRows: Int = 0,
            indexedWithDateCount: Int = 0,
            indexedWithoutDateCount: Int = 0,
            notYetIndexedCount: Int = 0
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
            lastRunErrorMessage = null
        )
    }
}
