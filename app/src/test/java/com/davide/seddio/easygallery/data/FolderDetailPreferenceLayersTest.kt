package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderDetailPreferenceLayersTest {

    private val base = ViewPreferences.defaultFor(PreferenceScope.FOLDER_DETAIL)

    @Test
    fun `resolver keeps global preferences when no overrides and folder is not camera`() {
        val layers = FolderDetailPreferenceLayers(
            globalPreferences = base,
            folderOverrides = emptyMap(),
            isSortUserDefined = false
        )

        val resolved = resolveFolderDetailPreferences(
            folderPath = "/storage/emulated/0/Pictures/Screenshots",
            layers = layers
        )

        assertEquals(base, resolved)
    }

    @Test
    fun `resolver applies camera implicit default when no explicit override exists`() {
        val layers = FolderDetailPreferenceLayers(
            globalPreferences = base,
            folderOverrides = emptyMap(),
            isSortUserDefined = false
        )

        val resolved = resolveFolderDetailPreferences(
            folderPath = "/storage/emulated/0/DCIM/Camera",
            layers = layers
        )

        assertEquals(SortType.DATE_TAKEN, resolved.sortType)
        assertEquals(SortOrder.DESCENDING, resolved.sortOrder)
        assertEquals(base.viewType, resolved.viewType)
    }

    @Test
    fun `resolver lets explicit override beat camera implicit default`() {
        val folderPath = "/storage/emulated/0/DCIM/Camera"
        val layers = FolderDetailPreferenceLayers(
            globalPreferences = base,
            folderOverrides = mapOf(
                folderPath to FolderViewOverrides(
                    sortType = SortType.NAME,
                    sortOrder = SortOrder.ASCENDING,
                    columns = 5
                )
            ),
            isSortUserDefined = false
        )

        val resolved = resolveFolderDetailPreferences(folderPath = folderPath, layers = layers)

        assertEquals(SortType.NAME, resolved.sortType)
        assertEquals(SortOrder.ASCENDING, resolved.sortOrder)
        assertEquals(5, resolved.columns)
    }

    @Test
    fun `resolver suppresses camera implicit default after user sets global sort`() {
        val layers = FolderDetailPreferenceLayers(
            globalPreferences = base.copy(
                sortType = SortType.LAST_MODIFIED,
                sortOrder = SortOrder.ASCENDING
            ),
            folderOverrides = emptyMap(),
            isSortUserDefined = true
        )

        val resolved = resolveFolderDetailPreferences(
            folderPath = "/storage/emulated/0/DCIM/Camera",
            layers = layers
        )

        assertEquals(SortType.LAST_MODIFIED, resolved.sortType)
        assertEquals(SortOrder.ASCENDING, resolved.sortOrder)
    }
}
