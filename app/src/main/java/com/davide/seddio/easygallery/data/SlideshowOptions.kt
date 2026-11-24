package com.davide.seddio.easygallery.data

const val SLIDESHOW_MIN_INTERVAL_SECONDS = 1
const val SLIDESHOW_MAX_INTERVAL_SECONDS = 60

enum class SlideshowTransition { FADE, NONE }

// ASCENDING is the list as the view shows it; DESCENDING is that list reversed.
enum class SlideshowOrder { ASCENDING, DESCENDING, RANDOM }

enum class SlideshowOrientation { ALL, LANDSCAPE_ONLY, PORTRAIT_ONLY }

data class SlideshowOptions(
    val intervalSeconds: Int = 3,
    val transition: SlideshowTransition = SlideshowTransition.FADE,
    val includeVideos: Boolean = true,
    val includeGifs: Boolean = false,
    val order: SlideshowOrder = SlideshowOrder.ASCENDING,
    val loop: Boolean = false,
    val orientation: SlideshowOrientation = SlideshowOrientation.ALL
)
