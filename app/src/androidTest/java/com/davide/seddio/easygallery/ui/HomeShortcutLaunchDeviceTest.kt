package com.davide.seddio.easygallery.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.EasyGalleryApp
import com.davide.seddio.easygallery.MainActivity
import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.data.MediaStoreDataSource
import com.davide.seddio.easygallery.ui.components.ShortcutTarget
import com.davide.seddio.easygallery.ui.components.buildShortcutIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the real [MainActivity] with a shortcut intent (ADR-0027 section 4) and observes the
 * ViewModels it navigates through. Needs live media on the device: without it the test fails with
 * `ENVIRONMENT:` rather than passing vacuously.
 */
@RunWith(AndroidJUnit4::class)
class HomeShortcutLaunchDeviceTest {

    private class Models(val gallery: GalleryViewModel, val albums: AlbumsViewModel)

    @Test
    fun folderIntentOpensFolderDetailView() {
        val folderPath = grantPermissionsAndPickFolderPath()

        ActivityScenario.launch<MainActivity>(shortcutIntent(ShortcutTarget.FolderTarget(folderPath))).use { scenario ->
            val models = modelsOf(scenario)

            waitUntil("the shortcut's folder was never opened") {
                models.gallery.selectedFolder.value?.path == folderPath
            }

            assertEquals(folderPath, models.gallery.selectedFolder.value?.path)
            assertNull(models.albums.selectedAlbumId.value)
        }
    }

    @Test
    fun unknownAlbumIdLandsOnAlbumsView() {
        grantPermissionsAndPickFolderPath()

        ActivityScenario.launch<MainActivity>(shortcutIntent(ShortcutTarget.AlbumTarget(UNKNOWN_ALBUM_ID))).use { scenario ->
            val models = modelsOf(scenario)

            waitUntil("the missing Album did not land on Albums View") {
                models.gallery.displayMode.value == DisplayMode.ALBUMS
            }

            assertNull(models.albums.selectedAlbumId.value)
            assertNull(models.gallery.selectedFolder.value)
        }
    }

    @Test
    fun recreatingAfterBackDoesNotNavigateAgain() {
        val folderPath = grantPermissionsAndPickFolderPath()

        ActivityScenario.launch<MainActivity>(shortcutIntent(ShortcutTarget.FolderTarget(folderPath))).use { scenario ->
            val first = modelsOf(scenario)
            waitUntil("the shortcut's folder was never opened") {
                first.gallery.selectedFolder.value?.path == folderPath
            }

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitUntil("Back did not leave Folder Detail View") {
                first.gallery.selectedFolder.value == null
            }

            scenario.recreate()
            val second = modelsOf(scenario)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            // The recreated Activity still holds the same intent; a second navigation would show within a few frames.
            assertStaysTrue("the shortcut navigated a second time after recreate()") {
                second.gallery.selectedFolder.value == null
            }
        }
    }

    private fun shortcutIntent(target: ShortcutTarget): Intent {
        val context = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        // ActivityScenario starts the task itself; CLEAR_TASK would also clear the instrumentation's.
        return buildShortcutIntent(context, target).apply { flags = 0 }
    }

    private fun modelsOf(scenario: ActivityScenario<MainActivity>): Models {
        var models: Models? = null
        scenario.onActivity { activity ->
            models = Models(
                gallery = ViewModelProvider(activity)[GalleryViewModel::class.java],
                albums = ViewModelProvider(activity)[AlbumsViewModel::class.java]
            )
        }
        return models ?: throw AssertionError("The Activity was not available to read its ViewModels.")
    }

    private fun grantPermissionsAndPickFolderPath(): String {
        val app = ApplicationProvider.getApplicationContext<EasyGalleryApp>()
        grantMediaPermissions(app.packageName)

        val live = runBlocking { MediaStoreDataSource(app).getAllMedia() }
        return live.firstOrNull { it.folderPath.isNotBlank() }?.folderPath
            ?: throw AssertionError("ENVIRONMENT: MediaStore returned no media in a folder to the app process.")
    }

    private fun grantMediaPermissions(targetPackage: String) {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            // Non-gating, but a system prompt over the Activity would disturb the scenario.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        permissions.forEach { permission ->
            try {
                uiAutomation.grantRuntimePermission(targetPackage, permission)
            } catch (_: Exception) {
                // The check below reports any permission still missing.
            }
        }

        val packageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        val missing = permissions.filter {
            packageManager.checkPermission(it, targetPackage) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) throw AssertionError("ENVIRONMENT: could not grant $missing")
    }

    // Real clock under a bound: the ViewModels load on threads the test does not own.
    private fun waitUntil(message: String, timeoutMillis: Long = 30_000L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("Timed out after $timeoutMillis ms: $message")
    }

    private fun assertStaysTrue(message: String, windowMillis: Long = 3_000L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + windowMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (!condition()) throw AssertionError(message)
            Thread.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        const val POLL_MILLIS = 100L
        const val UNKNOWN_ALBUM_ID = 987_654_321L
    }
}
