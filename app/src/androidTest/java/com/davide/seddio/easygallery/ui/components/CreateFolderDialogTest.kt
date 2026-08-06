package com.davide.seddio.easygallery.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class CreateFolderDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialogDisplaysCorrectElements() {
        composeTestRule.setContent {
            CreateFolderDialog(
                currentPath = "/storage/emulated/0",
                folders = emptyList(),
                error = null,
                onPathChange = {},
                onDismiss = {},
                onCreate = {}
            )
        }

        composeTestRule.onNodeWithText("Create New Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
        composeTestRule.onNodeWithTag("folder_name_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_create_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dismiss_create_button").assertIsDisplayed()
    }

    @Test
    fun typingUpdatesFolderNameField() {
        composeTestRule.setContent {
            CreateFolderDialog(
                currentPath = "/storage/emulated/0",
                folders = emptyList(),
                error = null,
                onPathChange = {},
                onDismiss = {},
                onCreate = {}
            )
        }

        composeTestRule.onNodeWithTag("folder_name_field").performTextInput("New Gallery")
        composeTestRule.onNodeWithText("New Gallery").assertIsDisplayed()
    }

    @Test
    fun errorIsDisplayedCorrectly() {
        composeTestRule.setContent {
            CreateFolderDialog(
                currentPath = "/storage/emulated/0",
                folders = emptyList(),
                error = "Invalid name",
                onPathChange = {},
                onDismiss = {},
                onCreate = {}
            )
        }

        composeTestRule.onNodeWithTag("folder_name_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid name").assertIsDisplayed()
    }

    @Test
    fun confirmButtonTriggersOnCreate() {
        var createdName = ""
        composeTestRule.setContent {
            CreateFolderDialog(
                currentPath = "/storage/emulated/0",
                folders = emptyList(),
                error = null,
                onPathChange = {},
                onDismiss = {},
                onCreate = { createdName = it }
            )
        }

        composeTestRule.onNodeWithTag("folder_name_field").performTextInput("MyPhotos")
        composeTestRule.onNodeWithTag("confirm_create_button").performClick()

        assert(createdName == "MyPhotos")
    }

    @Test
    fun dismissButtonTriggersOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            CreateFolderDialog(
                currentPath = "/storage/emulated/0",
                folders = emptyList(),
                error = null,
                onPathChange = {},
                onDismiss = { dismissed = true },
                onCreate = {}
            )
        }

        composeTestRule.onNodeWithTag("dismiss_create_button").performClick()

        assert(dismissed)
    }
}
