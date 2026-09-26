package com.davide.seddio.easygallery.data

object CameraFolder {
    private const val DCIM_SEGMENT = "DCIM"
    private const val CAMERA_SEGMENT = "Camera"
    private val DCF_DIRECTORY_REGEX = Regex("^[0-9]{3}[0-9A-Za-z_]{5}$")
    private val REPEATED_SEPARATOR_REGEX = Regex("/+")

    private val EMPTY_OVERRIDES = FolderViewOverrides()
    private val CAMERA_DEFAULT_OVERRIDES = FolderViewOverrides(
        sortType = SortType.DATE_TAKEN,
        sortOrder = SortOrder.DESCENDING
    )

    fun isCameraFolder(absolutePath: String?): Boolean {
        val normalizedPath = normalizePath(absolutePath) ?: return false
        val segments = normalizedPath.split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return false

        val ownSegment = segments.last()
        val parentSegment = segments[segments.lastIndex - 1]

        return parentSegment.equals(DCIM_SEGMENT, ignoreCase = true) &&
            (
                ownSegment.equals(CAMERA_SEGMENT, ignoreCase = true) ||
                    DCF_DIRECTORY_REGEX.matches(ownSegment)
                )
    }

    fun implicitDefaultsForFolder(absolutePath: String?): FolderViewOverrides {
        if (!isCameraFolder(absolutePath)) return EMPTY_OVERRIDES
        return CAMERA_DEFAULT_OVERRIDES
    }

    private fun normalizePath(absolutePath: String?): String? {
        val raw = absolutePath?.trim().orEmpty()
        if (raw.isBlank()) return null

        // Keep this Android-free for JVM unit tests: do not depend on Environment.DIRECTORY_DCIM.
        return raw.replace('\\', '/')
            .replace(REPEATED_SEPARATOR_REGEX, "/")
            .trimEnd('/')
            .ifBlank { null }
    }
}
