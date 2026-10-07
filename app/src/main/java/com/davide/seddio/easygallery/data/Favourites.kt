package com.davide.seddio.easygallery.data

private const val MARK_IS_FAVORITE_R_EXTENSION = 16

enum class FavouriteTier {
    DIRECT_MARK,
    MANAGE_MEDIA_WRITE,
    FAVORITE_REQUEST,
    UNSUPPORTED
}

fun resolveFavouriteTier(
    sdkInt: Int,
    hasManageMedia: Boolean,
    rExtensionVersion: Int = 0
): FavouriteTier {
    val hasDirectMarkApi = sdkInt >= 36 || (sdkInt >= 30 && rExtensionVersion >= MARK_IS_FAVORITE_R_EXTENSION)
    if (hasDirectMarkApi) return FavouriteTier.DIRECT_MARK
    if (sdkInt >= 31 && hasManageMedia) return FavouriteTier.MANAGE_MEDIA_WRITE
    if (sdkInt >= 30) return FavouriteTier.FAVORITE_REQUEST
    return FavouriteTier.UNSUPPORTED
}

fun isFavouritesSupported(sdkInt: Int): Boolean = sdkInt >= 30
