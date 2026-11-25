package com.davide.seddio.easygallery.ui.components

import android.net.Uri
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShortcutsTest {

    private val folderPath = "/storage/emulated/0/Pictures/Trips"

    private fun media(path: String, type: MediaType = MediaType.IMAGE) = MediaItem(
        uri = mockk<Uri>(),
        name = "item.jpg",
        dateAdded = 1_000L,
        dateModified = 1_000L,
        size = 10L,
        type = type,
        bucketName = path.substringAfterLast("/"),
        folderPath = path
    )

    private fun resolve(
        target: ShortcutTarget,
        hasPermission: Boolean = true,
        isMediaLoaded: Boolean = true,
        isAlbumListLoaded: Boolean = true,
        allMedia: List<MediaItem> = listOf(media(folderPath)),
        albumIds: Collection<Long> = listOf(7L),
        isFavouritesAvailable: Boolean = true
    ) = resolveShortcutTarget(
        target = target,
        hasPermission = hasPermission,
        isMediaLoaded = isMediaLoaded,
        isAlbumListLoaded = isAlbumListLoaded,
        allMedia = allMedia,
        albumIds = albumIds,
        isFavouritesAvailable = isFavouritesAvailable
    )

    // ---- identity ----

    @Test
    fun `shortcut ids are stable strings unique per target`() {
        val ids = listOf(
            ShortcutTarget.FolderTarget(folderPath).shortcutId,
            ShortcutTarget.FolderTarget("$folderPath/Other").shortcutId,
            ShortcutTarget.AlbumTarget(7L).shortcutId,
            ShortcutTarget.AlbumTarget(8L).shortcutId,
            ShortcutTarget.FavouritesTarget.shortcutId
        )

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ShortcutTarget.AlbumTarget(7L).shortcutId, ShortcutTarget.AlbumTarget(7L).shortcutId)
        assertEquals("favourites", ShortcutTarget.FavouritesTarget.shortcutId)
    }

    @Test
    fun `an album id and a folder path that looks like it never share an id`() {
        assertFalse(ShortcutTarget.AlbumTarget(7L).shortcutId == ShortcutTarget.FolderTarget("7").shortcutId)
    }

    // ---- encode and decode ----

    @Test
    fun `each target kind round-trips through its extras`() {
        val targets = listOf(
            ShortcutTarget.FolderTarget(folderPath),
            ShortcutTarget.AlbumTarget(42L),
            ShortcutTarget.FavouritesTarget
        )

        for (target in targets) {
            assertEquals(target, decodeShortcutTarget(SHORTCUT_ACTION, target.toExtras()))
        }
    }

    @Test
    fun `a foreign or missing action decodes to no target`() {
        val extras = ShortcutTarget.FavouritesTarget.toExtras()

        assertNull(decodeShortcutTarget("android.intent.action.VIEW", extras))
        assertNull(decodeShortcutTarget(null, extras))
    }

    @Test
    fun `a missing or unknown kind decodes to no target`() {
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = null)))
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "")))
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "FOLDER", folderPath = folderPath)))
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "timeline")))
    }

    @Test
    fun `an album id of zero or less decodes to no target`() {
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "album")))
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "album", albumId = 0L)))
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "album", albumId = -3L)))
    }

    @Test
    fun `a missing or blank folder path decodes to no target`() {
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "folder")))
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "folder", folderPath = "")))
        assertNull(decodeShortcutTarget(SHORTCUT_ACTION, ShortcutExtras(kind = "folder", folderPath = "  \t")))
    }

    @Test
    fun `stray extras for another kind are ignored`() {
        val target = decodeShortcutTarget(
            SHORTCUT_ACTION,
            ShortcutExtras(kind = "favourites", albumId = 5L, folderPath = folderPath)
        )

        assertEquals(ShortcutTarget.FavouritesTarget, target)
    }

    @Test
    fun `extras are not read for a foreign or missing action`() {
        var reads = 0
        val read = { reads++; ShortcutTarget.FavouritesTarget.toExtras() }

        assertNull(decodeShortcutIntent("android.intent.action.MAIN", read))
        assertNull(decodeShortcutIntent(null, read))
        assertEquals(0, reads)
    }

    @Test
    fun `extras that throw while being read decode to no target`() {
        assertNull(decodeShortcutIntent(SHORTCUT_ACTION) { throw IllegalStateException("unparcel failed") })
    }

    @Test
    fun `a valid action with valid extras decodes through the guarded read`() {
        val target = ShortcutTarget.AlbumTarget(7L)

        assertEquals(target, decodeShortcutIntent(SHORTCUT_ACTION) { target.toExtras() })
    }

    // ---- resolution ----

    @Test
    fun `resolution is pending until permission, media and the album list have all loaded`() {
        val targets = listOf(
            ShortcutTarget.FolderTarget(folderPath),
            ShortcutTarget.AlbumTarget(7L),
            ShortcutTarget.FavouritesTarget
        )

        for (target in targets) {
            assertEquals(ShortcutResolution.Pending, resolve(target, hasPermission = false))
            assertEquals(ShortcutResolution.Pending, resolve(target, isMediaLoaded = false))
            assertEquals(ShortcutResolution.Pending, resolve(target, isAlbumListLoaded = false))
            assertFalse(resolve(target) is ShortcutResolution.Pending)
        }
    }

    @Test
    fun `a pending album is not reported unavailable before the album list loads`() {
        val result = resolve(ShortcutTarget.AlbumTarget(7L), isAlbumListLoaded = false, albumIds = emptyList())

        assertEquals(ShortcutResolution.Pending, result)
    }

    @Test
    fun `a folder is found by path and built from all its media`() {
        val media = listOf(
            media(folderPath, MediaType.IMAGE),
            media(folderPath, MediaType.VIDEO),
            media(folderPath, MediaType.GIF),
            media("/storage/emulated/0/Pictures/Other")
        )

        val result = resolve(ShortcutTarget.FolderTarget(folderPath), allMedia = media)

        val folder = (result as ShortcutResolution.OpenFolder).folder
        assertEquals(folderPath, folder.path)
        assertEquals("Trips", folder.name)
        assertEquals(3, folder.imageCount)
    }

    @Test
    fun `a folder holding only videos still opens`() {
        val media = listOf(media(folderPath, MediaType.VIDEO))

        val result = resolve(ShortcutTarget.FolderTarget(folderPath), allMedia = media)

        assertEquals(1, (result as ShortcutResolution.OpenFolder).folder.imageCount)
    }

    @Test
    fun `a folder is matched by exact path only`() {
        val result = resolve(ShortcutTarget.FolderTarget("$folderPath/"), allMedia = listOf(media(folderPath)))

        assertEquals(ShortcutResolution.Unavailable(ShortcutKind.FOLDER), result)
    }

    @Test
    fun `a folder with no media is unavailable`() {
        assertEquals(
            ShortcutResolution.Unavailable(ShortcutKind.FOLDER),
            resolve(ShortcutTarget.FolderTarget(folderPath), allMedia = emptyList())
        )
        assertEquals(
            ShortcutResolution.Unavailable(ShortcutKind.FOLDER),
            resolve(ShortcutTarget.FolderTarget("/no/such/folder"))
        )
    }

    @Test
    fun `an album is found by id`() {
        assertEquals(ShortcutResolution.OpenAlbum(7L), resolve(ShortcutTarget.AlbumTarget(7L)))
    }

    @Test
    fun `a deleted album is unavailable`() {
        assertEquals(
            ShortcutResolution.Unavailable(ShortcutKind.ALBUM),
            resolve(ShortcutTarget.AlbumTarget(99L))
        )
    }

    @Test
    fun `favourites opens where available and is unavailable otherwise`() {
        assertEquals(
            ShortcutResolution.OpenFavourites,
            resolve(ShortcutTarget.FavouritesTarget, isFavouritesAvailable = true)
        )
        assertEquals(
            ShortcutResolution.Unavailable(ShortcutKind.FAVOURITES),
            resolve(ShortcutTarget.FavouritesTarget, isFavouritesAvailable = false)
        )
    }

    @Test
    fun `favourites does not depend on the media or albums being non-empty`() {
        val result = resolve(ShortcutTarget.FavouritesTarget, allMedia = emptyList(), albumIds = emptyList())

        assertTrue(result is ShortcutResolution.OpenFavourites)
    }
}
