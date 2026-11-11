package com.davide.seddio.easygallery.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore

sealed interface FavouriteWriteResult {
    data object Applied : FavouriteWriteResult
    data class PendingWriteGrant(val intentSender: IntentSender?) : FavouriteWriteResult
    data object Unsupported : FavouriteWriteResult
}

interface FavouritesDataSource {
    fun currentTier(): FavouriteTier
    fun isSupported(): Boolean
    fun hasManageMediaPermission(): Boolean
    fun favouritesOnlyQueryArgs(): Bundle?
    fun setFavourite(uris: Collection<Uri>, isFavourite: Boolean): FavouriteWriteResult

    // Tier 2 write access is transient and tied to the Activity lifecycle.
    fun onWriteGrantResult(granted: Boolean)
    fun invalidateWriteGrant()
}

class MediaStoreFavouritesDataSource(
    private val context: Context,
    private val contentResolver: ContentResolver = context.contentResolver
) : FavouritesDataSource {

    private var hasTransientWriteGrant = false

    override fun currentTier(): FavouriteTier =
        resolveFavouriteTier(
            sdkInt = Build.VERSION.SDK_INT,
            hasManageMedia = hasManageMediaPermission(),
            rExtensionVersion = currentRExtensionVersion()
        )

    override fun isSupported(): Boolean = isFavouritesSupported(Build.VERSION.SDK_INT)

    override fun hasManageMediaPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return MediaStore.canManageMedia(context)
    }

    override fun favouritesOnlyQueryArgs(): Bundle? {
        if (!isSupported()) return null
        return Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_FAVORITE, MediaStore.MATCH_ONLY)
        }
    }

    override fun setFavourite(uris: Collection<Uri>, isFavourite: Boolean): FavouriteWriteResult {
        if (uris.isEmpty()) return FavouriteWriteResult.Applied

        return when (currentTier()) {
            FavouriteTier.DIRECT_MARK -> {
                MediaStore.markIsFavoriteStatus(contentResolver, uris, isFavourite)
                FavouriteWriteResult.Applied
            }

            FavouriteTier.MANAGE_MEDIA_WRITE -> {
                if (!hasTransientWriteGrant) {
                    val intentSender = MediaStore.createWriteRequest(contentResolver, uris.toList()).intentSender
                    return FavouriteWriteResult.PendingWriteGrant(intentSender)
                }
                updateFavoriteColumn(uris, isFavourite)
                FavouriteWriteResult.Applied
            }

            FavouriteTier.FAVORITE_REQUEST -> {
                val intentSender = MediaStore.createFavoriteRequest(contentResolver, uris.toList(), isFavourite).intentSender
                FavouriteWriteResult.PendingWriteGrant(intentSender)
            }

            FavouriteTier.UNSUPPORTED -> FavouriteWriteResult.Unsupported
        }
    }

    override fun onWriteGrantResult(granted: Boolean) {
        hasTransientWriteGrant = granted
    }

    override fun invalidateWriteGrant() {
        hasTransientWriteGrant = false
    }

    private fun currentRExtensionVersion(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R)
    }

    private fun updateFavoriteColumn(uris: Collection<Uri>, isFavourite: Boolean) {
        uris.forEach { uri ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_FAVORITE, if (isFavourite) 1 else 0)
            }
            contentResolver.update(uri, values, null, null)
        }
    }
}

class InMemoryFavouritesDataSource(
    private var sdkInt: Int = 30,
    private var hasManageMedia: Boolean = false,
    private var rExtensionVersion: Int = 0
) : FavouritesDataSource {

    private val favouriteUris = mutableSetOf<String>()
    private var hasTransientWriteGrant = false

    override fun currentTier(): FavouriteTier =
        resolveFavouriteTier(
            sdkInt = sdkInt,
            hasManageMedia = hasManageMedia,
            rExtensionVersion = rExtensionVersion
        )

    override fun isSupported(): Boolean = isFavouritesSupported(sdkInt)

    override fun hasManageMediaPermission(): Boolean = hasManageMedia

    override fun favouritesOnlyQueryArgs(): Bundle? {
        if (!isSupported()) return null
        return Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_FAVORITE, MediaStore.MATCH_ONLY)
        }
    }

    override fun setFavourite(uris: Collection<Uri>, isFavourite: Boolean): FavouriteWriteResult {
        if (uris.isEmpty()) return FavouriteWriteResult.Applied

        return when (currentTier()) {
            FavouriteTier.DIRECT_MARK -> {
                applyFavoriteMutation(uris, isFavourite)
                FavouriteWriteResult.Applied
            }

            FavouriteTier.MANAGE_MEDIA_WRITE -> {
                if (!hasTransientWriteGrant) return FavouriteWriteResult.PendingWriteGrant(null)
                applyFavoriteMutation(uris, isFavourite)
                FavouriteWriteResult.Applied
            }

            FavouriteTier.FAVORITE_REQUEST -> FavouriteWriteResult.PendingWriteGrant(null)
            FavouriteTier.UNSUPPORTED -> FavouriteWriteResult.Unsupported
        }
    }

    override fun onWriteGrantResult(granted: Boolean) {
        hasTransientWriteGrant = granted
    }

    override fun invalidateWriteGrant() {
        hasTransientWriteGrant = false
    }

    fun setEnvironment(sdkInt: Int, hasManageMedia: Boolean, rExtensionVersion: Int = 0) {
        this.sdkInt = sdkInt
        this.hasManageMedia = hasManageMedia
        this.rExtensionVersion = rExtensionVersion
        if (sdkInt < 31 || !hasManageMedia) {
            hasTransientWriteGrant = false
        }
    }

    fun isFavourite(uri: Uri): Boolean = favouriteUris.contains(uri.toString())

    private fun applyFavoriteMutation(uris: Collection<Uri>, isFavourite: Boolean) {
        uris.forEach { uri ->
            if (isFavourite) {
                favouriteUris.add(uri.toString())
            } else {
                favouriteUris.remove(uri.toString())
            }
        }
    }
}
