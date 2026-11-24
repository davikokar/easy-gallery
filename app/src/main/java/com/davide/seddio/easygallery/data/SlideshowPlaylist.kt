package com.davide.seddio.easygallery.data

import android.net.Uri
import kotlin.random.Random

object SlideshowPlaylist {

    /** Images always play; a square item or one of unknown size plays only under ALL. */
    fun isPlayable(item: MediaItem, options: SlideshowOptions): Boolean {
        val typeAllowed = when (item.type) {
            MediaType.IMAGE -> true
            MediaType.VIDEO -> options.includeVideos
            MediaType.GIF -> options.includeGifs
        }
        if (!typeAllowed) return false

        val width = item.displayWidth
        val height = item.displayHeight
        return when (options.orientation) {
            SlideshowOrientation.ALL -> true
            SlideshowOrientation.LANDSCAPE_ONLY -> width != null && height != null && width > height
            SlideshowOrientation.PORTRAIT_ONLY -> width != null && height != null && height > width
        }
    }

    /**
     * The flat order the view sorts by, over the whole membership. Under the Random sort there is
     * nothing to recompute, so the list the view shows is kept and the items it lacks are
     * inserted at random positions (ADR-0025 section 3).
     */
    fun baseOrder(
        membership: List<MediaItem>,
        sortType: SortType,
        sortOrder: SortOrder,
        indexedDateTakenByCanonicalMediaUri: Map<String, Long?>,
        shownWhenRandom: List<MediaItem>,
        random: Random
    ): List<MediaItem> {
        if (sortType != SortType.RANDOM) {
            return GalleryTransformations.sortMedia(
                membership, sortType, sortOrder, indexedDateTakenByCanonicalMediaUri
            )
        }

        val membershipByUri = membership.associateBy { it.uri }
        val placed = HashSet<Uri>()
        val ordered = mutableListOf<MediaItem>()
        for (shown in shownWhenRandom) {
            val current = membershipByUri[shown.uri] ?: continue
            if (placed.add(current.uri)) ordered.add(current)
        }
        for (item in membership) {
            if (placed.add(item.uri)) ordered.add(random.nextInt(ordered.size + 1), item)
        }
        return ordered
    }

    fun build(baseOrder: List<MediaItem>, options: SlideshowOptions, random: Random): List<MediaItem> {
        val playable = baseOrder.filter { isPlayable(it, options) }
        return when (options.order) {
            SlideshowOrder.ASCENDING -> playable
            SlideshowOrder.DESCENDING -> playable.reversed()
            SlideshowOrder.RANDOM -> playable.shuffled(random)
        }
    }

    /** A new random pass that never opens on [lastShown], unless there is only one item. */
    fun reshuffle(playlist: List<MediaItem>, lastShown: MediaItem?, random: Random): List<MediaItem> {
        if (playlist.size <= 1) return playlist
        val shuffled = playlist.shuffled(random).toMutableList()
        if (lastShown == null || shuffled.first().uri != lastShown.uri) return shuffled

        val replacement = shuffled.indexOfFirst { it.uri != lastShown.uri }
        if (replacement > 0) {
            val first = shuffled[0]
            shuffled[0] = shuffled[replacement]
            shuffled[replacement] = first
        }
        return shuffled
    }
}
