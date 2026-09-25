package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderViewOverridesTest {

    private val base = ViewPreferences.defaultFor(PreferenceScope.FOLDER_DETAIL)

    @Test
    fun `applyTo with empty overrides returns the base preferences unchanged`() {
        val overrides = FolderViewOverrides()

        assertEquals(base, overrides.applyTo(base))
    }

    @Test
    fun `applyTo with partial override inherits untouched fields from base`() {
        val overrides = FolderViewOverrides(sortType = SortType.SIZE, sortOrder = SortOrder.DESCENDING)

        val resolved = overrides.applyTo(base)

        assertEquals(SortType.SIZE, resolved.sortType)
        assertEquals(SortOrder.DESCENDING, resolved.sortOrder)
        assertEquals(base.viewType, resolved.viewType)
        assertEquals(base.columns, resolved.columns)
        assertEquals(base.groupBy, resolved.groupBy)
        assertEquals(base.groupOrder, resolved.groupOrder)
        assertEquals(base.mediaTypes, resolved.mediaTypes)
        assertEquals(base.showInfo, resolved.showInfo)
    }

    @Test
    fun `each overridable preference group overrides only its own fields`() {
        val sortResolved = FolderViewOverrides(
            sortType = SortType.RANDOM,
            sortOrder = SortOrder.DESCENDING
        ).applyTo(base)
        assertEquals(SortType.RANDOM, sortResolved.sortType)
        assertEquals(SortOrder.DESCENDING, sortResolved.sortOrder)
        assertEquals(base.viewType, sortResolved.viewType)
        assertEquals(base.columns, sortResolved.columns)
        assertEquals(base.groupBy, sortResolved.groupBy)
        assertEquals(base.groupOrder, sortResolved.groupOrder)
        assertEquals(base.mediaTypes, sortResolved.mediaTypes)

        val columnsResolved = FolderViewOverrides(columns = ViewPreferences.MIN_COLUMNS + 1).applyTo(base)
        assertEquals(ViewPreferences.MIN_COLUMNS + 1, columnsResolved.columns)
        assertEquals(base.sortType, columnsResolved.sortType)
        assertEquals(base.sortOrder, columnsResolved.sortOrder)
        assertEquals(base.groupBy, columnsResolved.groupBy)
        assertEquals(base.groupOrder, columnsResolved.groupOrder)
        assertEquals(base.viewType, columnsResolved.viewType)
        assertEquals(base.mediaTypes, columnsResolved.mediaTypes)

        val groupResolved = FolderViewOverrides(
            groupBy = GroupByType.FILE_TYPE,
            groupOrder = SortOrder.ASCENDING
        ).applyTo(base)
        assertEquals(GroupByType.FILE_TYPE, groupResolved.groupBy)
        assertEquals(SortOrder.ASCENDING, groupResolved.groupOrder)
        assertEquals(base.sortType, groupResolved.sortType)
        assertEquals(base.sortOrder, groupResolved.sortOrder)
        assertEquals(base.columns, groupResolved.columns)
        assertEquals(base.viewType, groupResolved.viewType)
        assertEquals(base.mediaTypes, groupResolved.mediaTypes)

        val mediaResolved = FolderViewOverrides(mediaTypes = setOf(MediaType.VIDEO)).applyTo(base)
        assertEquals(setOf(MediaType.VIDEO), mediaResolved.mediaTypes)
        assertEquals(base.sortType, mediaResolved.sortType)
        assertEquals(base.sortOrder, mediaResolved.sortOrder)
        assertEquals(base.columns, mediaResolved.columns)
        assertEquals(base.groupBy, mediaResolved.groupBy)
        assertEquals(base.groupOrder, mediaResolved.groupOrder)
        assertEquals(base.viewType, mediaResolved.viewType)

        val viewTypeResolved = FolderViewOverrides(viewType = ViewType.LIST).applyTo(base)
        assertEquals(ViewType.LIST, viewTypeResolved.viewType)
        assertEquals(base.sortType, viewTypeResolved.sortType)
        assertEquals(base.sortOrder, viewTypeResolved.sortOrder)
        assertEquals(base.columns, viewTypeResolved.columns)
        assertEquals(base.groupBy, viewTypeResolved.groupBy)
        assertEquals(base.groupOrder, viewTypeResolved.groupOrder)
        assertEquals(base.mediaTypes, viewTypeResolved.mediaTypes)
    }

    @Test
    fun `applyTo coerces out of range columns into valid bounds`() {
        val belowMinResolved = FolderViewOverrides(columns = ViewPreferences.MIN_COLUMNS - 5).applyTo(base)
        val aboveMaxResolved = FolderViewOverrides(columns = ViewPreferences.MAX_COLUMNS + 5).applyTo(base)

        assertEquals(ViewPreferences.MIN_COLUMNS, belowMinResolved.columns)
        assertEquals(ViewPreferences.MAX_COLUMNS, aboveMaxResolved.columns)
    }

    @Test
    fun `cleared nulls exactly the selected group fields and leaves others intact for every group`() {
        val full = FolderViewOverrides(
            sortType = SortType.SIZE,
            sortOrder = SortOrder.DESCENDING,
            viewType = ViewType.LIST,
            columns = 7,
            groupBy = GroupByType.DATE_TAKEN_MONTHLY,
            groupOrder = SortOrder.ASCENDING,
            mediaTypes = setOf(MediaType.IMAGE)
        )

        for (group in OverridablePreference.entries) {
            val cleared = full.cleared(group)
            val expected = when (group) {
                OverridablePreference.SORT -> full.copy(sortType = null, sortOrder = null)
                OverridablePreference.COLUMNS -> full.copy(columns = null)
                OverridablePreference.GROUP_BY -> full.copy(groupBy = null, groupOrder = null)
                OverridablePreference.MEDIA_TYPES -> full.copy(mediaTypes = null)
                OverridablePreference.VIEW_TYPE -> full.copy(viewType = null)
            }
            assertEquals(expected, cleared)
        }
    }

    @Test
    fun `isEmpty is true only when every field is effectively null`() {
        val nonEmptyCases = listOf(
            FolderViewOverrides(sortType = SortType.NAME),
            FolderViewOverrides(sortOrder = SortOrder.DESCENDING),
            FolderViewOverrides(viewType = ViewType.LIST),
            FolderViewOverrides(columns = 3),
            FolderViewOverrides(groupBy = GroupByType.FILE_TYPE),
            FolderViewOverrides(groupOrder = SortOrder.ASCENDING),
            FolderViewOverrides(mediaTypes = setOf(MediaType.GIF))
        )

        assertTrue(FolderViewOverrides().isEmpty())
        nonEmptyCases.forEach { overrides ->
            assertFalse(overrides.isEmpty())
        }
    }

    @Test
    fun `empty media type set is treated as no override`() {
        val overrides = FolderViewOverrides(mediaTypes = emptySet())

        assertTrue(overrides.isEmpty())
        assertEquals(base.mediaTypes, overrides.applyTo(base).mediaTypes)

        val withMediaTypes = FolderViewOverrides().withMediaTypes(emptySet())
        assertTrue(withMediaTypes.isEmpty())
        assertEquals(base.mediaTypes, withMediaTypes.applyTo(base).mediaTypes)
    }
}
