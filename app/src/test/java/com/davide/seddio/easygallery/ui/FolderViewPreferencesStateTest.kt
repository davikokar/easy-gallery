package com.davide.seddio.easygallery.ui

import app.cash.turbine.test
import com.davide.seddio.easygallery.data.FolderViewOverrides
import com.davide.seddio.easygallery.data.FolderViewPreferencesStore
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.OverridablePreference
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderViewPreferencesStateTest {

    @Test
    fun `state is seeded from store at construction`() {
        val seeded = mapOf(
            "/storage/emulated/0/DCIM/Camera" to FolderViewOverrides(columns = 4),
            "/storage/emulated/0/Pictures/Screenshots" to FolderViewOverrides(viewType = ViewType.LIST)
        )
        val store = RecordingFolderViewPreferencesStore(initial = seeded)

        val state = FolderViewPreferencesState(store)

        assertEquals(seeded, state.overrides.value)
    }

    @Test
    fun `overridesFor returns empty overrides for null or blank path`() {
        val state = FolderViewPreferencesState(RecordingFolderViewPreferencesStore())

        assertEquals(FolderViewOverrides(), state.overridesFor(null))
        assertEquals(FolderViewOverrides(), state.overridesFor(""))
        assertEquals(FolderViewOverrides(), state.overridesFor("   \t\n"))
    }

    @Test
    fun `setters with null or blank path are safe no-ops`() {
        val store = RecordingFolderViewPreferencesStore()
        val state = FolderViewPreferencesState(store)

        state.setSort(null, SortType.SIZE, SortOrder.DESCENDING)
        state.setColumns("", 5)
        state.setGroupBy("   ", GroupByType.FILE_TYPE, SortOrder.ASCENDING)
        state.setMediaTypes("\n\t", setOf(MediaType.VIDEO))
        state.setViewType("  ", ViewType.LIST)

        assertTrue(state.overrides.value.isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `setSort emits updated map and writes through to store`() = runTest {
        val store = RecordingFolderViewPreferencesStore()
        val state = FolderViewPreferencesState(store)
        val path = "/storage/emulated/0/DCIM/Camera"

        state.overrides.test {
            assertEquals(emptyMap<String, FolderViewOverrides>(), awaitItem())

            state.setSort(path, SortType.SIZE, SortOrder.DESCENDING)

            val updated = awaitItem()
            val expectedOverrides = FolderViewOverrides(sortType = SortType.SIZE, sortOrder = SortOrder.DESCENDING)
            assertEquals(mapOf(path to expectedOverrides), updated)
            assertEquals(listOf(path to expectedOverrides), store.saved)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setColumns emits updated map and writes through to store`() = runTest {
        val store = RecordingFolderViewPreferencesStore()
        val state = FolderViewPreferencesState(store)
        val path = "/storage/emulated/0/DCIM/Camera"

        state.overrides.test {
            assertEquals(emptyMap<String, FolderViewOverrides>(), awaitItem())

            state.setColumns(path, 6)

            val updated = awaitItem()
            val expectedOverrides = FolderViewOverrides(columns = 6)
            assertEquals(mapOf(path to expectedOverrides), updated)
            assertEquals(listOf(path to expectedOverrides), store.saved)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setGroupBy emits updated map and writes through to store`() = runTest {
        val store = RecordingFolderViewPreferencesStore()
        val state = FolderViewPreferencesState(store)
        val path = "/storage/emulated/0/DCIM/Camera"

        state.overrides.test {
            assertEquals(emptyMap<String, FolderViewOverrides>(), awaitItem())

            state.setGroupBy(path, GroupByType.FILE_TYPE, SortOrder.ASCENDING)

            val updated = awaitItem()
            val expectedOverrides = FolderViewOverrides(
                groupBy = GroupByType.FILE_TYPE,
                groupOrder = SortOrder.ASCENDING
            )
            assertEquals(mapOf(path to expectedOverrides), updated)
            assertEquals(listOf(path to expectedOverrides), store.saved)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setMediaTypes emits updated map and writes through to store`() = runTest {
        val store = RecordingFolderViewPreferencesStore()
        val state = FolderViewPreferencesState(store)
        val path = "/storage/emulated/0/DCIM/Camera"

        state.overrides.test {
            assertEquals(emptyMap<String, FolderViewOverrides>(), awaitItem())

            state.setMediaTypes(path, setOf(MediaType.VIDEO))

            val updated = awaitItem()
            val expectedOverrides = FolderViewOverrides(mediaTypes = setOf(MediaType.VIDEO))
            assertEquals(mapOf(path to expectedOverrides), updated)
            assertEquals(listOf(path to expectedOverrides), store.saved)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setViewType emits updated map and writes through to store`() = runTest {
        val store = RecordingFolderViewPreferencesStore()
        val state = FolderViewPreferencesState(store)
        val path = "/storage/emulated/0/DCIM/Camera"

        state.overrides.test {
            assertEquals(emptyMap<String, FolderViewOverrides>(), awaitItem())

            state.setViewType(path, ViewType.LIST)

            val updated = awaitItem()
            val expectedOverrides = FolderViewOverrides(viewType = ViewType.LIST)
            assertEquals(mapOf(path to expectedOverrides), updated)
            assertEquals(listOf(path to expectedOverrides), store.saved)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear emits updated map and writes through to store`() = runTest {
        val initial = mapOf(
            "/storage/emulated/0/DCIM/Camera" to FolderViewOverrides(
                sortType = SortType.NAME,
                sortOrder = SortOrder.ASCENDING,
                columns = 4
            ),
            "/storage/emulated/0/Pictures/Screenshots" to FolderViewOverrides(
                sortType = SortType.SIZE,
                sortOrder = SortOrder.DESCENDING
            )
        )
        val store = RecordingFolderViewPreferencesStore(initial)
        val state = FolderViewPreferencesState(store)

        state.overrides.test {
            assertEquals(initial, awaitItem())

            state.clear(OverridablePreference.SORT)

            val updated = awaitItem()
            val expected = mapOf(
                "/storage/emulated/0/DCIM/Camera" to FolderViewOverrides(columns = 4)
            )
            assertEquals(expected, updated)
            assertEquals(listOf(OverridablePreference.SORT), store.cleared)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private class RecordingFolderViewPreferencesStore(
        initial: Map<String, FolderViewOverrides> = emptyMap()
    ) : FolderViewPreferencesStore {

        private val values = initial.toMutableMap()
        val saved = mutableListOf<Pair<String, FolderViewOverrides>>()
        val cleared = mutableListOf<OverridablePreference>()

        override fun loadAll(): Map<String, FolderViewOverrides> = values.toMap()

        override fun save(folderPath: String, overrides: FolderViewOverrides) {
            saved += folderPath to overrides
            if (overrides.isEmpty()) {
                values.remove(folderPath)
            } else {
                values[folderPath] = overrides
            }
        }

        override fun clear(group: OverridablePreference) {
            cleared += group
            val updated = values
                .mapValues { (_, overrides) -> overrides.cleared(group) }
                .filterValues { !it.isEmpty() }
            values.clear()
            values.putAll(updated)
        }
    }
}
