package com.davide.seddio.easygallery.ui.components

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.davide.seddio.easygallery.MainActivity
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.GalleryTransformations
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Home-screen shortcut core, ADR-0027. Everything above the "Android boundary" marker is pure.

private const val LOG_TAG = "HomeShortcuts"

const val SHORTCUT_ACTION = "com.davide.seddio.easygallery.action.OPEN_SHORTCUT_TARGET"
const val SHORTCUT_EXTRA_KIND = "com.davide.seddio.easygallery.extra.SHORTCUT_KIND"
const val SHORTCUT_EXTRA_ALBUM_ID = "com.davide.seddio.easygallery.extra.SHORTCUT_ALBUM_ID"
const val SHORTCUT_EXTRA_FOLDER_PATH = "com.davide.seddio.easygallery.extra.SHORTCUT_FOLDER_PATH"

const val FAVOURITES_SHORTCUT_ID = "favourites"

enum class ShortcutKind(val wireValue: String) {
    FOLDER("folder"),
    ALBUM("album"),
    FAVOURITES("favourites")
}

sealed interface ShortcutTarget {
    val kind: ShortcutKind

    /** Stable across devices and restores, and the same for the same target every time it is pinned. */
    val shortcutId: String

    data class FolderTarget(val path: String) : ShortcutTarget {
        override val kind get() = ShortcutKind.FOLDER
        override val shortcutId get() = "folder:$path"
    }

    data class AlbumTarget(val albumId: Long) : ShortcutTarget {
        override val kind get() = ShortcutKind.ALBUM
        override val shortcutId get() = "album:$albumId"
    }

    data object FavouritesTarget : ShortcutTarget {
        override val kind get() = ShortcutKind.FAVOURITES
        override val shortcutId get() = FAVOURITES_SHORTCUT_ID
    }
}

/** The intent extras as primitives; an absent long reads as 0, which is never a valid album id. */
data class ShortcutExtras(
    val kind: String?,
    val albumId: Long = 0L,
    val folderPath: String? = null
)

fun ShortcutTarget.toExtras(): ShortcutExtras = when (this) {
    is ShortcutTarget.FolderTarget -> ShortcutExtras(kind.wireValue, folderPath = path)
    is ShortcutTarget.AlbumTarget -> ShortcutExtras(kind.wireValue, albumId = albumId)
    ShortcutTarget.FavouritesTarget -> ShortcutExtras(kind.wireValue)
}

/** The extras are untrusted: any app can send them to the exported MainActivity (ADR-0027 section 8). */
fun decodeShortcutTarget(action: String?, extras: ShortcutExtras): ShortcutTarget? {
    if (action != SHORTCUT_ACTION) return null
    return when (extras.kind) {
        ShortcutKind.FOLDER.wireValue ->
            extras.folderPath?.takeIf { it.isNotBlank() }?.let { ShortcutTarget.FolderTarget(it) }
        ShortcutKind.ALBUM.wireValue ->
            extras.albumId.takeIf { it > 0L }?.let { ShortcutTarget.AlbumTarget(it) }
        ShortcutKind.FAVOURITES.wireValue -> ShortcutTarget.FavouritesTarget
        else -> null
    }
}

/** Reading extras can throw on an exported activity (a foreign Parcelable class); that is a plain launch, not a crash. */
fun decodeShortcutIntent(action: String?, readExtras: () -> ShortcutExtras): ShortcutTarget? {
    if (action != SHORTCUT_ACTION) return null
    val extras = try {
        readExtras()
    } catch (error: RuntimeException) {
        return null
    }
    return decodeShortcutTarget(action, extras)
}

sealed interface ShortcutResolution {
    /** Permission, media or the album list has not loaded yet. */
    data object Pending : ShortcutResolution

    data class OpenFolder(val folder: Folder) : ShortcutResolution

    data class OpenAlbum(val albumId: Long) : ShortcutResolution

    data object OpenFavourites : ShortcutResolution

    data class Unavailable(val kind: ShortcutKind) : ShortcutResolution
}

/**
 * A folder is matched by path among all loaded media, so exclusion and the media-type filter do not
 * hide it; the path is only ever compared as a string.
 */
fun resolveShortcutTarget(
    target: ShortcutTarget,
    hasPermission: Boolean,
    isMediaLoaded: Boolean,
    isAlbumListLoaded: Boolean,
    allMedia: List<MediaItem>,
    albumIds: Collection<Long>,
    isFavouritesAvailable: Boolean
): ShortcutResolution {
    if (!hasPermission || !isMediaLoaded || !isAlbumListLoaded) return ShortcutResolution.Pending

    return when (target) {
        is ShortcutTarget.FolderTarget -> {
            val folder = buildUnfilteredFolder(target.path, allMedia)
            if (folder != null) ShortcutResolution.OpenFolder(folder) else ShortcutResolution.Unavailable(target.kind)
        }
        is ShortcutTarget.AlbumTarget ->
            if (target.albumId in albumIds) ShortcutResolution.OpenAlbum(target.albumId)
            else ShortcutResolution.Unavailable(target.kind)
        ShortcutTarget.FavouritesTarget ->
            if (isFavouritesAvailable) ShortcutResolution.OpenFavourites
            else ShortcutResolution.Unavailable(target.kind)
    }
}

private fun buildUnfilteredFolder(path: String, allMedia: List<MediaItem>): Folder? {
    val inFolder = allMedia.filter { it.folderPath == path }
    if (inFolder.isEmpty()) return null
    return GalleryTransformations.filterAndSortFolders(
        allMedia = inFolder,
        query = "",
        pinned = emptySet(),
        sort = SortType.NAME,
        order = SortOrder.ASCENDING,
        excluded = emptySet(),
        showExcluded = true,
        types = MediaType.entries.toSet(),
        indexedDateTakenByCanonicalMediaUri = emptyMap()
    ).firstOrNull()
}

/** Keeps a pinned Album shortcut true after the Album's own write has succeeded (ADR-0027 section 7); a call on an id with no pinned shortcut does nothing. */
interface AlbumShortcutPublisher {
    suspend fun albumRenamed(albumId: Long, newName: String)

    suspend fun albumDeleted(albumId: Long, disabledMessage: String)
}

class InMemoryAlbumShortcutPublisher : AlbumShortcutPublisher {
    private val _renamed = mutableListOf<Pair<Long, String>>()
    private val _deleted = mutableListOf<Pair<Long, String>>()

    val renamed: List<Pair<Long, String>> get() = _renamed.toList()
    val deleted: List<Pair<Long, String>> get() = _deleted.toList()

    override suspend fun albumRenamed(albumId: Long, newName: String) {
        _renamed += albumId to newName
    }

    override suspend fun albumDeleted(albumId: Long, disabledMessage: String) {
        _deleted += albumId to disabledMessage
    }
}

// ---- Android boundary ----

enum class ShortcutPinResult { Requested, NotSupported, Failed }

fun buildShortcutIntent(context: Context, target: ShortcutTarget): Intent {
    val extras = target.toExtras()
    return Intent(context, MainActivity::class.java).apply {
        action = SHORTCUT_ACTION
        putExtra(SHORTCUT_EXTRA_KIND, extras.kind)
        when (target) {
            is ShortcutTarget.FolderTarget -> putExtra(SHORTCUT_EXTRA_FOLDER_PATH, extras.folderPath)
            is ShortcutTarget.AlbumTarget -> putExtra(SHORTCUT_EXTRA_ALBUM_ID, extras.albumId)
            ShortcutTarget.FavouritesTarget -> Unit
        }
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}

fun readShortcutTarget(intent: Intent): ShortcutTarget? = decodeShortcutIntent(intent.action) {
    ShortcutExtras(
        kind = intent.getStringExtra(SHORTCUT_EXTRA_KIND),
        albumId = intent.getLongExtra(SHORTCUT_EXTRA_ALBUM_ID, 0L),
        folderPath = intent.getStringExtra(SHORTCUT_EXTRA_FOLDER_PATH)
    )
}

private fun shortcutIconResource(kind: ShortcutKind): Int = when (kind) {
    ShortcutKind.FOLDER -> R.drawable.ic_shortcut_folder
    ShortcutKind.ALBUM -> R.drawable.ic_shortcut_album
    ShortcutKind.FAVOURITES -> R.drawable.ic_shortcut_favourites
}

// Shared by pinning and by any later label update, which must publish the same id, intent and icon.
internal fun buildShortcutInfo(context: Context, target: ShortcutTarget, label: String): ShortcutInfoCompat =
    ShortcutInfoCompat.Builder(context, target.shortcutId)
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(IconCompat.createWithResource(context, shortcutIconResource(target.kind)))
        .setIntent(buildShortcutIntent(context, target))
        .build()

/** Runs after the Album's write, so a platform failure is logged and never thrown: it must not undo or crash a rename or delete. */
class AndroidAlbumShortcutPublisher(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AlbumShortcutPublisher {
    private val appContext = context.applicationContext ?: context

    override suspend fun albumRenamed(albumId: Long, newName: String) {
        withContext(dispatcher) {
            try {
                val info = buildShortcutInfo(appContext, ShortcutTarget.AlbumTarget(albumId), newName)
                val accepted = ShortcutManagerCompat.updateShortcuts(appContext, listOf(info))
                Log.i(LOG_TAG, "album shortcut label update accepted=$accepted")
            } catch (error: RuntimeException) {
                Log.w(LOG_TAG, "album shortcut label update failed error=${error.javaClass.simpleName}")
            }
        }
    }

    override suspend fun albumDeleted(albumId: Long, disabledMessage: String) {
        withContext(dispatcher) {
            try {
                ShortcutManagerCompat.disableShortcuts(
                    appContext,
                    listOf(ShortcutTarget.AlbumTarget(albumId).shortcutId),
                    disabledMessage
                )
                Log.i(LOG_TAG, "album shortcut disabled")
            } catch (error: RuntimeException) {
                Log.w(LOG_TAG, "album shortcut disable failed error=${error.javaClass.simpleName}")
            }
        }
    }
}

/** Off the main thread: the platform call may take time. Logs only the kind, never a path or a name. */
suspend fun pinHomeShortcut(
    context: Context,
    target: ShortcutTarget,
    label: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): ShortcutPinResult = withContext(dispatcher) {
    val appContext = context.applicationContext
    try {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) {
            Log.i(LOG_TAG, "pin not supported kind=${target.kind}")
            return@withContext ShortcutPinResult.NotSupported
        }
        val requested = ShortcutManagerCompat.requestPinShortcut(
            appContext,
            buildShortcutInfo(appContext, target, label),
            null
        )
        if (requested) {
            Log.i(LOG_TAG, "pin requested kind=${target.kind}")
            ShortcutPinResult.Requested
        } else {
            Log.i(LOG_TAG, "pin refused by launcher kind=${target.kind}")
            ShortcutPinResult.NotSupported
        }
    } catch (error: IllegalStateException) {
        Log.w(LOG_TAG, "pin failed kind=${target.kind} error=${error.javaClass.simpleName}")
        ShortcutPinResult.Failed
    } catch (error: IllegalArgumentException) {
        Log.w(LOG_TAG, "pin failed kind=${target.kind} error=${error.javaClass.simpleName}")
        ShortcutPinResult.Failed
    }
}
