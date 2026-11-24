package com.davide.seddio.easygallery.ui.components

import kotlin.math.abs

/** The centred Filmstrip cell is drawn this much larger than its neighbours. */
internal const val FILMSTRIP_MAX_SCALE = 1.15f

/** Which of the two pagers a Filmstrip sync step has to move. */
internal enum class FilmstripSync { NONE, STRIP_FOLLOWS_MAIN, MAIN_FOLLOWS_STRIP }

/** 1 for the centred cell, falling linearly to 0 one page away; symmetric either side of the centre. */
internal fun filmstripSelectionFraction(distanceInPages: Float): Float =
    (1f - abs(distanceInPages)).coerceIn(0f, 1f)

internal fun filmstripItemScale(distanceInPages: Float): Float =
    1f + (FILMSTRIP_MAX_SCALE - 1f) * filmstripSelectionFraction(distanceInPages)

/** Padding either side that lets the first and last cells reach the centre; never negative. Any one unit for both arguments. */
internal fun filmstripSideInset(stripWidth: Float, itemWidth: Float): Float =
    ((stripWidth - itemWidth) / 2f).coerceAtLeast(0f)

/**
 * The main pager is the source of truth. The strip leads only while the user is driving it, so in any
 * state at most one pager is asked to move and the two cannot chase each other.
 */
internal fun filmstripSync(mainPage: Int, stripPage: Int, stripUserDriven: Boolean): FilmstripSync = when {
    mainPage == stripPage -> FilmstripSync.NONE
    stripUserDriven -> FilmstripSync.MAIN_FOLLOWS_STRIP
    else -> FilmstripSync.STRIP_FOLLOWS_MAIN
}

/** A single thumbnail gives nothing to navigate to. */
internal fun isFilmstripShown(itemCount: Int): Boolean = itemCount >= 2
