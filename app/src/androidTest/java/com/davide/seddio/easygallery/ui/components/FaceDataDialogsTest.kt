package com.davide.seddio.easygallery.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Plan 3.6, Task A: the Face data disclosure in both modes, the Delete face data dialog and the Face licences dialog. */
class FaceDataDialogsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var oks = 0
    private var turnOffs = 0
    private var closes = 0
    private var confirms = 0
    private var cancels = 0

    @Test
    fun disclosureModeShowsTheTitleAndFiveParagraphs() {
        setDisclosure(isInfoMode = false)

        composeTestRule.onNodeWithText("People in your photos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_disclosure").assertIsDisplayed()
        listOf(
            "Easy Gallery finds and groups the faces in your photos",
            "This happens on your phone only. What it finds is never sent anywhere and is not included in backups.",
            "Faces are never named.",
            "Finding faces takes time",
            "You can turn this off at any time in Settings, under Face data."
        ).forEach { text ->
            composeTestRule.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("face_data_disclosure")))
                .assertExists()
        }
    }

    @Test
    fun disclosureModeHasOkAndTurnOffAndNoClose() {
        setDisclosure(isInfoMode = false)

        composeTestRule.onNodeWithTag("face_data_disclosure_ok").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_disclosure_turn_off").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_disclosure_close").assertDoesNotExist()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
        composeTestRule.onNodeWithText("Turn off").assertIsDisplayed()
    }

    @Test
    fun okCallsOnlyOnOk() {
        setDisclosure(isInfoMode = false)

        composeTestRule.onNodeWithTag("face_data_disclosure_ok").performClick()

        assertEquals(listOf(1, 0, 0), counts())
    }

    @Test
    fun turnOffCallsOnlyOnTurnOff() {
        setDisclosure(isInfoMode = false)

        composeTestRule.onNodeWithTag("face_data_disclosure_turn_off").performClick()

        assertEquals(listOf(0, 1, 0), counts())
    }

    @Test
    fun backPressDoesNothingInDisclosureMode() {
        setDisclosure(isInfoMode = false)

        pressSystemBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("face_data_disclosure").assertExists()
        composeTestRule.onNodeWithTag("face_data_disclosure_ok").assertExists()
        assertEquals(listOf(0, 0, 0), counts())
    }

    @Test
    fun infoModeHasASingleCloseAndNothingElse() {
        setDisclosure(isInfoMode = true)

        composeTestRule.onNodeWithTag("face_data_disclosure").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_disclosure_close").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_disclosure_ok").assertDoesNotExist()
        composeTestRule.onNodeWithTag("face_data_disclosure_turn_off").assertDoesNotExist()
    }

    @Test
    fun infoModeCloseCallsOnlyOnClose() {
        setDisclosure(isInfoMode = true)

        composeTestRule.onNodeWithTag("face_data_disclosure_close").performClick()

        assertEquals(listOf(0, 0, 1), counts())
    }

    @Test
    fun backPressClosesTheInfoMode() {
        setDisclosure(isInfoMode = true)

        pressSystemBack()
        composeTestRule.waitForIdle()

        assertEquals(listOf(0, 0, 1), counts())
    }

    @Test
    fun disclosureButtonsStayVisibleAtTheLargestFontScale() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                FaceDataDisclosureDialog(isInfoMode = false, onOk = { oks++ }, onTurnOff = { turnOffs++ }, onClose = { closes++ })
            }
        }

        composeTestRule.onNodeWithTag("face_data_disclosure_ok").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_disclosure_turn_off").assertIsDisplayed()
    }

    @Test
    fun deleteDialogShowsTheTitleBothMessagesAndTwoButtons() {
        setDeleteDialog()

        composeTestRule.onNodeWithText("Delete face data?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_delete_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("This turns off face data and deletes the faces", substring = true).assertExists()
        composeTestRule.onNodeWithText("Albums of people stay, but show only the items you added by hand.", substring = true).assertExists()
        composeTestRule.onNodeWithTag("face_data_delete_confirm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete and turn off").assertIsDisplayed()
        composeTestRule.onNodeWithTag("face_data_delete_cancel").assertIsDisplayed()
    }

    @Test
    fun deleteDialogConfirmAndCancelCallTheirOwnCallbacks() {
        setDeleteDialog()

        composeTestRule.onNodeWithTag("face_data_delete_cancel").performClick()
        assertEquals(listOf(0, 1), listOf(confirms, cancels))

        composeTestRule.onNodeWithTag("face_data_delete_confirm").performClick()
        assertEquals(listOf(1, 1), listOf(confirms, cancels))
    }

    @Test
    fun deleteDialogBackPressCancels() {
        setDeleteDialog()

        pressSystemBack()
        composeTestRule.waitForIdle()

        assertEquals(listOf(0, 1), listOf(confirms, cancels))
    }

    @Test
    fun licencesDialogShowsProgressWhileLoading() {
        composeTestRule.setContent {
            FaceLicencesDialogContent(load = FaceLicencesLoad.Loading, onDismiss = { closes++ })
        }

        composeTestRule.onNodeWithText("Face recognition licences").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading notices").assertIsDisplayed()
    }

    @Test
    fun licencesDialogSaysSoWhenTheNoticesCouldNotBeReadAndOkCloses() {
        composeTestRule.setContent {
            FaceLicencesDialogContent(load = FaceLicencesLoad.Failed, onDismiss = { closes++ })
        }

        composeTestRule.onNodeWithText("The notices could not be read.").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(1, closes)
    }

    @Test
    fun licencesDialogShowsTheIntroTheFileNamesAndTheirTextVerbatim() {
        val files = listOf(
            FaceLicenceText("NOTICE.txt", listOf("First line of one\nsecond line of one", "Another paragraph")),
            FaceLicenceText("LICENSE-YuNet-MIT.txt", listOf("MIT text"))
        )
        composeTestRule.setContent {
            FaceLicencesDialogContent(load = FaceLicencesLoad.Loaded(files), onDismiss = { closes++ })
        }

        composeTestRule.onNodeWithText("Notices for the software and models that find faces on this phone.").assertIsDisplayed()
        composeTestRule.onNodeWithText("NOTICE.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("First line of one\nsecond line of one").assertIsDisplayed()
        composeTestRule.onNodeWithText("LICENSE-YuNet-MIT.txt").assertExists()
    }

    @Test
    fun licencesDialogReadsAndShowsTheRealAssets() {
        composeTestRule.setContent { FaceLicencesDialog(onDismiss = { closes++ }) }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("face_licences_list").fetchSemanticsNodes().isNotEmpty()
        }

        FACE_LICENCE_FILE_NAMES.forEach { name ->
            composeTestRule.onNodeWithTag("face_licences_list").performScrollToNode(hasText(name))
            composeTestRule.onNodeWithText(name).assertExists()
        }
    }

    // Espresso.pressBack waits for the picked root to take window focus, which a dialog window does not always do on a device.
    private fun pressSystemBack() {
        composeTestRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun counts() = listOf(oks, turnOffs, closes)

    private fun setDisclosure(isInfoMode: Boolean) {
        composeTestRule.setContent {
            FaceDataDisclosureDialog(
                isInfoMode = isInfoMode,
                onOk = { oks++ },
                onTurnOff = { turnOffs++ },
                onClose = { closes++ }
            )
        }
    }

    private fun setDeleteDialog() {
        composeTestRule.setContent {
            DeleteFaceDataDialog(onConfirm = { confirms++ }, onCancel = { cancels++ })
        }
    }
}
