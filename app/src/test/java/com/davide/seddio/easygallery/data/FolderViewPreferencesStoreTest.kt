package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderViewPreferencesStoreTest {

    @Test
    fun `save then loadAll returns stored overrides for each folder`() {
        val store = InMemoryFolderViewPreferencesStore()

        val cameraOverrides = FolderViewOverrides(
            sortType = SortType.DATE_TAKEN,
            sortOrder = SortOrder.DESCENDING,
            columns = 5
        )
        val screenshotsOverrides = FolderViewOverrides(
            viewType = ViewType.LIST,
            groupBy = GroupByType.FILE_TYPE,
            groupOrder = SortOrder.ASCENDING,
            mediaTypes = setOf(MediaType.IMAGE)
        )

        store.save("/storage/emulated/0/DCIM/Camera", cameraOverrides)
        store.save("/storage/emulated/0/Pictures/Screenshots", screenshotsOverrides)

        assertEquals(
            mapOf(
                "/storage/emulated/0/DCIM/Camera" to cameraOverrides,
                "/storage/emulated/0/Pictures/Screenshots" to screenshotsOverrides
            ),
            store.loadAll()
        )
    }

    @Test
    fun `saving empty overrides removes the folder entry`() {
        val store = InMemoryFolderViewPreferencesStore()
        val folderPath = "/storage/emulated/0/DCIM/Camera"

        store.save(folderPath, FolderViewOverrides(columns = 4))
        store.save(folderPath, FolderViewOverrides())

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `clear removes selected group across all folders and keeps other groups`() {
        val store = InMemoryFolderViewPreferencesStore()

        val folderA = "/storage/emulated/0/DCIM/Camera"
        val folderB = "/storage/emulated/0/Pictures/Screenshots"

        val overridesA = FolderViewOverrides(
            sortType = SortType.NAME,
            sortOrder = SortOrder.ASCENDING,
            columns = 6,
            viewType = ViewType.LIST
        )
        val overridesB = FolderViewOverrides(
            sortType = SortType.SIZE,
            sortOrder = SortOrder.DESCENDING,
            groupBy = GroupByType.DATE_TAKEN_DAILY,
            groupOrder = SortOrder.DESCENDING,
            mediaTypes = setOf(MediaType.VIDEO)
        )

        store.save(folderA, overridesA)
        store.save(folderB, overridesB)

        store.clear(OverridablePreference.SORT)

        assertEquals(
            mapOf(
                folderA to FolderViewOverrides(columns = 6, viewType = ViewType.LIST),
                folderB to FolderViewOverrides(
                    groupBy = GroupByType.DATE_TAKEN_DAILY,
                    groupOrder = SortOrder.DESCENDING,
                    mediaTypes = setOf(MediaType.VIDEO)
                )
            ),
            store.loadAll()
        )
    }

    @Test
    fun `clear drops folders that become empty after selected group removal`() {
        val store = InMemoryFolderViewPreferencesStore()

        val folderOnlySort = "/storage/emulated/0/DCIM/Camera"
        val folderWithOther = "/storage/emulated/0/Pictures/Screenshots"

        store.save(
            folderOnlySort,
            FolderViewOverrides(sortType = SortType.NAME, sortOrder = SortOrder.ASCENDING)
        )
        store.save(
            folderWithOther,
            FolderViewOverrides(sortType = SortType.SIZE, sortOrder = SortOrder.DESCENDING, columns = 3)
        )

        store.clear(OverridablePreference.SORT)

        assertEquals(
            mapOf(folderWithOther to FolderViewOverrides(columns = 3)),
            store.loadAll()
        )
    }

    @Test
    fun `clear on an empty store is a no-op`() {
        val store = InMemoryFolderViewPreferencesStore()

        store.clear(OverridablePreference.COLUMNS)

        assertTrue(store.loadAll().isEmpty())
    }
}
