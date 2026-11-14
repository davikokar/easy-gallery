package com.davide.seddio.easygallery.ui

import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType

/** An album as Albums View lists it: the album, and how many of its items pass the media-type filter. */
data class VisibleAlbum(val album: AlbumListItem, val itemCount: Int)

/** Albums View's search, filter and sort; the filter hides an album it empties, not one that was already empty. */
fun filterAndSortAlbums(
    albums: List<AlbumListItem>,
    query: String,
    sortType: SortType,
    sortOrder: SortOrder,
    mediaTypes: Set<MediaType>
): List<VisibleAlbum> {
    val filtered = albums
        .filter { it.name.contains(query, ignoreCase = true) }
        .map { VisibleAlbum(it, it.mediaCountsByType.countOf(mediaTypes)) }
        .filterNot { it.itemCount == 0 && it.album.mediaCountsByType.values.sum() > 0 }
    val sorted = when (sortType) {
        SortType.NAME, SortType.PATH -> filtered.sortedBy { it.album.name.lowercase() }
        SortType.SIZE -> filtered.sortedBy { it.itemCount }
        SortType.LAST_MODIFIED, SortType.DATE_TAKEN -> filtered.sortedBy { it.album.createdAt }
        SortType.RANDOM -> filtered.shuffled()
    }
    return if (sortType != SortType.RANDOM && sortOrder == SortOrder.DESCENDING) sorted.reversed() else sorted
}

private fun Map<MediaType, Int>.countOf(types: Set<MediaType>): Int = types.sumOf { this[it] ?: 0 }
