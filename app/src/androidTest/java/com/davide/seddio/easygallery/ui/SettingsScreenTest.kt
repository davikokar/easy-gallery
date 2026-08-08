package com.davide.seddio.easygallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GalleryViewModel = mockk(relaxed = true)

    @Test
    fun settingsScreenDisplaysAllSections() {
        composeTestRule.setContent {
            SettingsScreen(viewModel)
        }

        composeTestRule.onNodeWithText("General").assertIsDisplayed()
        composeTestRule.onNodeWithText("App").assertIsDisplayed()
    }

    @Test
    fun settingsScreenDisplaysAllItems() {
        composeTestRule.setContent {
            SettingsScreen(viewModel)
        }

        composeTestRule.onNodeWithText("Manage excluded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share app").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rate app").assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy & Policy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Terms").assertIsDisplayed()
        composeTestRule.onNodeWithText("Customer Support").assertIsDisplayed()
    }

    @Test
    fun clickingManageExcludedCallsViewModel() {
        composeTestRule.setContent {
            SettingsScreen(viewModel)
        }

        composeTestRule.onNodeWithText("Manage excluded").performClick()

        verify { viewModel.setManageExcludedMode(true) }
    }

    @Test
    fun clickingCustomerSupportCallsCallback() {
        val onContactSupportClick = mockk<(android.content.Context) -> Unit>(relaxed = true)
        composeTestRule.setContent {
            SettingsScreen(viewModel, onContactSupportClick = onContactSupportClick)
        }

        composeTestRule.onNodeWithText("Customer Support").performClick()

        verify { onContactSupportClick(any()) }
    }
}
