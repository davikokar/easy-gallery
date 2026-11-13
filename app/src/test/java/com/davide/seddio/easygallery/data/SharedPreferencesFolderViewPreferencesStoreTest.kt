package com.davide.seddio.easygallery.data

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class SharedPreferencesFolderViewPreferencesStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folderPath = "/storage/emulated/0/DCIM/Camera"

    @Before
    fun setUp() {
        listOf(FOLDER_DISPLAY_PREFERENCES_FILE, ALBUM_DISPLAY_PREFERENCES_FILE).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun `file names are the documented ones`() {
        assertEquals("folder_display_preferences", FOLDER_DISPLAY_PREFERENCES_FILE)
        assertEquals("album_display_preferences", ALBUM_DISPLAY_PREFERENCES_FILE)
    }

    @Test
    fun `the default store keeps writing to the folder file`() {
        SharedPreferencesFolderViewPreferencesStore(context)
            .save(folderPath, FolderViewOverrides(columns = 4))

        val folderFile = context.getSharedPreferences(FOLDER_DISPLAY_PREFERENCES_FILE, Context.MODE_PRIVATE)
        val albumFile = context.getSharedPreferences(ALBUM_DISPLAY_PREFERENCES_FILE, Context.MODE_PRIVATE)
        assertTrue(folderFile.contains("$folderPath/columns"))
        assertTrue(albumFile.all.isEmpty())
    }

    @Test
    fun `album keys and the favourites key round trip through a new store instance`() {
        val overrides = mapOf(
            albumOverrideKey(12L) to FolderViewOverrides(
                sortType = SortType.SIZE,
                sortOrder = SortOrder.DESCENDING,
                columns = 7,
                mediaTypes = setOf(MediaType.VIDEO)
            ),
            FAVOURITES_OVERRIDE_KEY to FolderViewOverrides(
                viewType = ViewType.LIST,
                groupBy = GroupByType.FILE_TYPE,
                groupOrder = SortOrder.ASCENDING
            )
        )
        val writer = albumStore()
        overrides.forEach { (key, value) -> writer.save(key, value) }

        val loaded = albumStore().loadAll()

        assertEquals(overrides, loaded)
    }

    @Test
    fun `saving an empty override removes a key from the album file`() {
        val store = albumStore()
        store.save("12", FolderViewOverrides(columns = 5))
        store.save(FAVOURITES_OVERRIDE_KEY, FolderViewOverrides(columns = 6))

        store.save("12", FolderViewOverrides())

        assertEquals(mapOf(FAVOURITES_OVERRIDE_KEY to FolderViewOverrides(columns = 6)), albumStore().loadAll())
    }

    @Test
    fun `clearing a group in the album file leaves the folder file alone`() {
        val folders = folderStore()
        val albums = albumStore()
        folders.save(folderPath, FolderViewOverrides(columns = 4, viewType = ViewType.LIST))
        albums.save("12", FolderViewOverrides(columns = 7, viewType = ViewType.LIST))

        albums.clear(OverridablePreference.COLUMNS)

        assertEquals(
            mapOf(folderPath to FolderViewOverrides(columns = 4, viewType = ViewType.LIST)),
            folderStore().loadAll()
        )
        assertEquals(mapOf("12" to FolderViewOverrides(viewType = ViewType.LIST)), albumStore().loadAll())
    }

    @Test
    fun `clearing a group in the folder file leaves the album file alone`() {
        val folders = folderStore()
        val albums = albumStore()
        folders.save(folderPath, FolderViewOverrides(columns = 4, viewType = ViewType.LIST))
        albums.save("12", FolderViewOverrides(columns = 7))
        albums.save(FAVOURITES_OVERRIDE_KEY, FolderViewOverrides(columns = 8))

        folders.clear(OverridablePreference.COLUMNS)

        assertEquals(mapOf(folderPath to FolderViewOverrides(viewType = ViewType.LIST)), folderStore().loadAll())
        assertEquals(
            mapOf(
                "12" to FolderViewOverrides(columns = 7),
                FAVOURITES_OVERRIDE_KEY to FolderViewOverrides(columns = 8)
            ),
            albumStore().loadAll()
        )
    }

    private fun folderStore() = SharedPreferencesFolderViewPreferencesStore(context)

    private fun albumStore() =
        SharedPreferencesFolderViewPreferencesStore(context, ALBUM_DISPLAY_PREFERENCES_FILE)
}
