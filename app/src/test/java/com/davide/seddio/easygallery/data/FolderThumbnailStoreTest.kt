package com.davide.seddio.easygallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderThumbnailStoreTest {

    @Test
    fun `save then loadAll returns stored thumbnail for one folder`() {
        val store = InMemoryFolderThumbnailStore()
        val folderPath = "/storage/emulated/0/DCIM/Camera"
        val uri = "content://media/external/images/media/101"

        store.save(folderPath, uri)

        assertEquals(mapOf(folderPath to uri), store.loadAll())
    }

    @Test
    fun `save then loadAll returns stored thumbnails for multiple folders`() {
        val store = InMemoryFolderThumbnailStore()

        val cameraFolder = "/storage/emulated/0/DCIM/Camera"
        val screenshotsFolder = "/storage/emulated/0/Pictures/Screenshots"
        val cameraUri = "content://media/external/images/media/101"
        val screenshotsUri = "content://media/external/images/media/202"

        store.save(cameraFolder, cameraUri)
        store.save(screenshotsFolder, screenshotsUri)

        assertEquals(
            mapOf(
                cameraFolder to cameraUri,
                screenshotsFolder to screenshotsUri
            ),
            store.loadAll()
        )
    }

    @Test
    fun `clear removes only targeted folder and keeps others`() {
        val store = InMemoryFolderThumbnailStore()

        val cameraFolder = "/storage/emulated/0/DCIM/Camera"
        val screenshotsFolder = "/storage/emulated/0/Pictures/Screenshots"
        val cameraUri = "content://media/external/images/media/101"
        val screenshotsUri = "content://media/external/images/media/202"

        store.save(cameraFolder, cameraUri)
        store.save(screenshotsFolder, screenshotsUri)

        store.clear(cameraFolder)

        assertEquals(mapOf(screenshotsFolder to screenshotsUri), store.loadAll())
    }

    @Test
    fun `save with blank folder path is a no-op`() {
        val store = InMemoryFolderThumbnailStore()

        store.save("", "content://media/external/images/media/101")

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `save with blank uri is a no-op`() {
        val store = InMemoryFolderThumbnailStore()

        store.save("/storage/emulated/0/DCIM/Camera", "")

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `loadAll on an empty store returns an empty map`() {
        val store = InMemoryFolderThumbnailStore()

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `folder with no stored entry is absent from loadAll`() {
        val store = InMemoryFolderThumbnailStore()

        val existingFolder = "/storage/emulated/0/DCIM/Camera"
        val missingFolder = "/storage/emulated/0/Download"
        val existingUri = "content://media/external/images/media/101"

        store.save(existingFolder, existingUri)

        val all = store.loadAll()

        assertEquals(1, all.size)
        assertTrue(all.containsKey(existingFolder))
        assertFalse(all.containsKey(missingFolder))
    }
}
