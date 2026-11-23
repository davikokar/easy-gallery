package com.davide.seddio.easygallery.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import android.app.UiAutomation
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private companion object {
        const val ROTATION_WAIT_MILLIS = 10_000L
    }

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

    // Plan 3.7, item 22: the dialog at the largest font scale, its height bounded by the window and its OK on screen once
    // the longest notice has loaded. The rotation is set before the content, and frozen only for the test's length.
    @Test
    fun licencesDialogKeepsOkOnScreenAtLargestFontScaleInPortrait() {
        assertLicencesDialogFitsTheScreenAtLargestFontScale(landscape = false)
    }

    @Test
    fun licencesDialogKeepsOkOnScreenAtLargestFontScaleInLandscape() {
        assertLicencesDialogFitsTheScreenAtLargestFontScale(landscape = true)
    }

    private fun assertLicencesDialogFitsTheScreenAtLargestFontScale(landscape: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectedOrientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        // Freezing a rotation writes user_rotation and turns auto-rotate off, and unfreezing neither puts user_rotation back nor leaves auto-rotate off.
        val resolver = instrumentation.targetContext.contentResolver
        val userRotationBefore = Settings.System.getInt(resolver, "user_rotation", 0)
        val autoRotateBefore = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        try {
            instrumentation.uiAutomation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
            val deadline = SystemClock.elapsedRealtime() + ROTATION_WAIT_MILLIS
            while (instrumentation.targetContext.resources.configuration.orientation != expectedOrientation) {
                if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("ENVIRONMENT: the phone did not rotate to orientation $expectedOrientation")
                SystemClock.sleep(100)
            }
            instrumentation.waitForIdleSync()
            composeTestRule.waitForIdle()

            var orientation = Configuration.ORIENTATION_UNDEFINED
            composeTestRule.setContent {
                orientation = LocalConfiguration.current.orientation
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    FaceLicencesDialog(onDismiss = { closes++ })
                }
            }
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithTag("face_licences_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.waitForIdle()

            assertEquals("The dialog must be drawn in the orientation under test", expectedOrientation, orientation)
            composeTestRule.onNodeWithText("Face recognition licences").assertIsDisplayed()
            composeTestRule.onNodeWithText("OK").assertIsDisplayed()
            val screen = instrumentation.targetContext.resources.displayMetrics
            val dialog = composeTestRule.onNode(isDialog()).fetchSemanticsNode()
            val ok = composeTestRule.onNodeWithText("OK").fetchSemanticsNode()
            listOf("dialog" to dialog, "OK button" to ok).forEach { (name, node) ->
                val top = node.positionOnScreen.y
                val bottom = top + node.size.height
                assertTrue("The $name must lie inside the ${screen.heightPixels} px high screen: top=$top bottom=$bottom", top >= 0f && bottom <= screen.heightPixels)
                val left = node.positionOnScreen.x
                assertTrue("The $name must lie inside the ${screen.widthPixels} px wide screen", left >= 0f && left + node.size.width <= screen.widthPixels)
            }
            assertEquals("OK must not have been pressed", 0, closes)
        } finally {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            listOf("user_rotation" to userRotationBefore, "accelerometer_rotation" to autoRotateBefore).forEach { (setting, value) ->
                instrumentation.uiAutomation.executeShellCommand("settings put system $setting $value").use {
                    FileInputStream(it.fileDescriptor).readBytes()
                }
            }
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
