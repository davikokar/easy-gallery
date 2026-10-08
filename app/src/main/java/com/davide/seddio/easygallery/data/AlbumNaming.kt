package com.davide.seddio.easygallery.data

import java.util.Locale

/**
 * Pure, Android-free default naming for a new album.
 */
object AlbumNaming {

    /**
     * The first name produced by [format] whose ordinal does not collide with [existingNames],
     * compared the same way album names are validated: trimmed and case-insensitive.
     */
    fun suggestName(existingNames: Collection<String>, format: (Int) -> String): String {
        val taken = existingNames.mapTo(HashSet()) { canonical(it) }
        var ordinal = 1
        while (canonical(format(ordinal)) in taken) {
            ordinal++
        }
        return format(ordinal)
    }

    private fun canonical(name: String): String = name.trim().lowercase(Locale.ROOT)
}
