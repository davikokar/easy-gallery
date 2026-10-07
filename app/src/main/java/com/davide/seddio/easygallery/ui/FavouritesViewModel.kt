package com.davide.seddio.easygallery.ui

import android.app.Application
import android.content.ContentUris
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.seddio.easygallery.data.FavouriteTier
import com.davide.seddio.easygallery.data.FavouriteWriteResult
import com.davide.seddio.easygallery.data.FavouritesDataSource
import com.davide.seddio.easygallery.data.MediaStoreFavouritesDataSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingFavouriteMutation(
    val uris: Set<android.net.Uri>,
    val isFavourite: Boolean,
    val tierAtRequest: FavouriteTier,
    val previousFavourites: Set<android.net.Uri>
)

data class PendingFavouriteWriteRequest(
    val intentSender: IntentSender?
)

class FavouritesViewModel @JvmOverloads constructor(
    application: Application,
    private val dataSource: FavouritesDataSource = MediaStoreFavouritesDataSource(application),
    private val supportsManageMediaRationale: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
) : AndroidViewModel(application) {

    private val _favourites = MutableStateFlow<Set<android.net.Uri>>(emptySet())
    val favourites: StateFlow<Set<android.net.Uri>> = _favourites.asStateFlow()

    private val _pendingWriteRequest = MutableStateFlow<PendingFavouriteWriteRequest?>(null)
    val pendingWriteRequest: StateFlow<PendingFavouriteWriteRequest?> = _pendingWriteRequest.asStateFlow()

    private val _currentTier = MutableStateFlow(dataSource.currentTier())
    val currentTier: StateFlow<FavouriteTier> = _currentTier.asStateFlow()

    private val _isAvailable = MutableStateFlow(dataSource.isSupported())
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _shouldOfferManageMediaRationale = MutableStateFlow(false)
    val shouldOfferManageMediaRationale: StateFlow<Boolean> = _shouldOfferManageMediaRationale.asStateFlow()

    private val _openManageMediaSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openManageMediaSettings: SharedFlow<Unit> = _openManageMediaSettings.asSharedFlow()

    private var pendingMutation: PendingFavouriteMutation? = null
    private var hasMetSystemPrompt = false
    private var hasDeclinedManageMediaRationale = false
    private var hasLoadedExistingFavourites = false
    private var mutationVersion = 0L

    fun isFavourite(uri: android.net.Uri): Boolean = _favourites.value.contains(uri)

    fun loadExistingFavourites() {
        if (!_isAvailable.value || hasLoadedExistingFavourites) return

        val versionAtStart = mutationVersion
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                queryExistingFavouriteUris()
            } ?: return@launch

            hasLoadedExistingFavourites = true
            if (mutationVersion == versionAtStart && pendingMutation == null) {
                _favourites.value = loaded
            }
        }
    }

    fun toggleFavourite(uri: android.net.Uri) {
        toggleFavourites(setOf(uri))
    }

    fun toggleFavourites(uris: Collection<android.net.Uri>) {
        val uniqueUris = uris.toSet()
        if (!_isAvailable.value || uniqueUris.isEmpty()) return
        if (pendingMutation != null) return

        mutationVersion += 1

        val previous = _favourites.value
        val allAlreadyFavourite = uniqueUris.all(previous::contains)
        val targetFavourite = !allAlreadyFavourite
        val optimistic = previous.toMutableSet().apply {
            if (targetFavourite) addAll(uniqueUris) else removeAll(uniqueUris)
        }.toSet()

        _favourites.value = optimistic

        val tier = dataSource.currentTier()
        _currentTier.value = tier

        when (val result = dataSource.setFavourite(uniqueUris, targetFavourite)) {
            FavouriteWriteResult.Applied -> {
                pendingMutation = null
            }

            is FavouriteWriteResult.PendingWriteGrant -> {
                pendingMutation = PendingFavouriteMutation(
                    uris = uniqueUris,
                    isFavourite = targetFavourite,
                    tierAtRequest = tier,
                    previousFavourites = previous
                )
                _pendingWriteRequest.value = PendingFavouriteWriteRequest(result.intentSender)
                hasMetSystemPrompt = true
                refreshManageMediaRationaleOfferState()
            }

            FavouriteWriteResult.Unsupported -> {
                // API 28-29 writes must be safe no-ops.
                _favourites.value = previous
                pendingMutation = null
            }
        }
    }

    fun onPendingWriteRequestResult(granted: Boolean) {
        val pending = pendingMutation ?: run {
            _pendingWriteRequest.value = null
            return
        }

        _pendingWriteRequest.value = null

        if (!granted) {
            dataSource.onWriteGrantResult(false)
            _favourites.value = pending.previousFavourites
            pendingMutation = null
            refreshManageMediaRationaleOfferState()
            return
        }

        when (pending.tierAtRequest) {
            FavouriteTier.MANAGE_MEDIA_WRITE -> {
                dataSource.onWriteGrantResult(true)
                when (dataSource.setFavourite(pending.uris, pending.isFavourite)) {
                    FavouriteWriteResult.Applied -> {
                        pendingMutation = null
                    }

                    is FavouriteWriteResult.PendingWriteGrant,
                    FavouriteWriteResult.Unsupported -> {
                        dataSource.onWriteGrantResult(false)
                        _favourites.value = pending.previousFavourites
                        pendingMutation = null
                    }
                }
            }

            FavouriteTier.FAVORITE_REQUEST,
            FavouriteTier.DIRECT_MARK -> {
                // The system request applies this write directly for this tier.
                pendingMutation = null
            }

            FavouriteTier.UNSUPPORTED -> {
                _favourites.value = pending.previousFavourites
                pendingMutation = null
            }
        }

        refreshManageMediaRationaleOfferState()
    }

    fun clearPendingWriteRequest() {
        _pendingWriteRequest.value = null
        pendingMutation = null
    }

    fun invalidateTransientWriteGrant() {
        dataSource.invalidateWriteGrant()
    }

    fun onManageMediaRationaleAccepted() {
        _shouldOfferManageMediaRationale.value = false
        _openManageMediaSettings.tryEmit(Unit)
    }

    fun onManageMediaRationaleDeclined() {
        hasDeclinedManageMediaRationale = true
        _shouldOfferManageMediaRationale.value = false
    }

    fun refreshCapabilityState() {
        _isAvailable.value = dataSource.isSupported()
        _currentTier.value = dataSource.currentTier()
        refreshManageMediaRationaleOfferState()
    }

    private fun refreshManageMediaRationaleOfferState() {
        if (!supportsManageMediaRationale) {
            _shouldOfferManageMediaRationale.value = false
            return
        }

        val shouldOffer = hasMetSystemPrompt &&
            !hasDeclinedManageMediaRationale &&
            !dataSource.hasManageMediaPermission()

        _shouldOfferManageMediaRationale.value = shouldOffer
    }

    private fun queryExistingFavouriteUris(): Set<android.net.Uri>? {
        val queryArgs = dataSource.favouritesOnlyQueryArgs() ?: return emptySet()
        val contentResolver = getApplication<Application>().contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val favourites = mutableSetOf<android.net.Uri>()

        return try {
            listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).forEach { collectionUri ->
                contentResolver.query(collectionUri, projection, queryArgs, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        favourites += ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn))
                    }
                }
            }
            favourites
        } catch (_: SecurityException) {
            null
        }
    }
}
