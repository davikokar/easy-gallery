package com.davide.seddio.easygallery.ui.components

import java.util.Locale

internal fun formatMediaDuration(durationMs: Long): String {
    if (durationMs <= 0L) {
        return "0:00"
    }

    val totalSeconds = durationMs / 1000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L

    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun playbackProgressFraction(positionMs: Long, totalDurationMs: Long): Float {
    if (totalDurationMs <= 0L) {
        return 0f
    }

    val fraction = positionMs.toDouble() / totalDurationMs.toDouble()
    if (!fraction.isFinite()) {
        return 0f
    }

    return fraction.toFloat().coerceIn(0f, 1f)
}