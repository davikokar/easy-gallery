package com.davide.seddio.easygallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.davide.seddio.easygallery.ui.FullImageScreen
import com.davide.seddio.easygallery.ui.AddToAlbumDialog
import com.davide.seddio.easygallery.ui.CreateAlbumDialog
import com.davide.seddio.easygallery.ui.ManageExcludedScreen
import com.davide.seddio.easygallery.ui.SettingsScreen
import com.davide.seddio.easygallery.ui.AlbumDetailScreen
import com.davide.seddio.easygallery.ui.AlbumsViewModel
import com.davide.seddio.easygallery.ui.BillingViewModel
import com.davide.seddio.easygallery.ui.FolderDetailScreen
import com.davide.seddio.easygallery.ui.FolderListScreen
import com.davide.seddio.easygallery.ui.FavouritesViewModel
import com.davide.seddio.easygallery.ui.CreateFolderViewModel
import com.davide.seddio.easygallery.ui.GalleryViewModel
import com.davide.seddio.easygallery.ui.theme.EasyGalleryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()
    private val albumsViewModel: AlbumsViewModel by viewModels()
    private val favouritesViewModel: FavouritesViewModel by viewModels()
    private val createFolderViewModel: CreateFolderViewModel by viewModels()
    private val billingViewModel: BillingViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onWriteRequestResult(true)
            viewModel.exitSelectionMode()
            viewModel.exitMediaSelectionMode()
            viewModel.closeMedia()
        } else {
            viewModel.onWriteRequestResult(false)
        }
        viewModel.clearPendingWriteRequest()
    }

    private val favouritesIntentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        favouritesViewModel.onPendingWriteRequestResult(result.resultCode == RESULT_OK)
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasPermission = allGranted
        if (allGranted) {
            viewModel.loadFolders()
            requestMediaLocationPermissionIfNeeded()
        }
    }

    private val requestMediaLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favouritesViewModel.invalidateTransientWriteGrant()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )

        checkPermissions()

        setContent {
            val pendingWriteRequest by viewModel.pendingWriteRequest.collectAsState()
            val pendingFavouriteWriteRequest by favouritesViewModel.pendingWriteRequest.collectAsState()
            
            LaunchedEffect(pendingWriteRequest) {
                pendingWriteRequest?.let {
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                }
            }

            LaunchedEffect(pendingFavouriteWriteRequest) {
                pendingFavouriteWriteRequest?.intentSender?.let { intentSender ->
                    favouritesIntentSenderLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }

            LaunchedEffect(Unit) {
                favouritesViewModel.openManageMediaSettings.collect {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        )
                    }
                }
            }

            EasyGalleryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val selectedFolder: com.davide.seddio.easygallery.data.Folder? by viewModel.selectedFolder.collectAsState()
                    val selectedMedia: com.davide.seddio.easygallery.data.MediaItem? by viewModel.selectedMedia.collectAsState()
                    val allMedia by viewModel.allMedia.collectAsState()
                    val albums by albumsViewModel.albums.collectAsState()
                    val selectedAlbumId by albumsViewModel.selectedAlbumId.collectAsState()
                    val favouriteUris by favouritesViewModel.favourites.collectAsState()
                    val favouritesAvailable by favouritesViewModel.isAvailable.collectAsState()
                    val shouldOfferManageMediaRationale by favouritesViewModel.shouldOfferManageMediaRationale.collectAsState()
                    val isManageExcludedMode by viewModel.isManageExcludedMode.collectAsState()
                    val isSettingsMode by viewModel.isSettingsMode.collectAsState()
                    val selectedFolderPath = selectedFolder?.path
                    var isFavouritesSelected by rememberSaveable { mutableStateOf(false) }
                    var mediaPendingAddToAlbum by remember { mutableStateOf(emptyList<com.davide.seddio.easygallery.data.MediaItem>()) }
                    var showAddToAlbumDialog by remember { mutableStateOf(false) }
                    var showCreateAlbumDialog by remember { mutableStateOf(false) }
                    var albumNameDraft by remember { mutableStateOf("") }
                    var pendingCreatedAlbumName by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(allMedia) {
                        albumsViewModel.setAllMedia(allMedia)
                        if (allMedia.isNotEmpty()) {
                            favouritesViewModel.loadExistingFavourites()
                        }
                    }
                    LaunchedEffect(albums, pendingCreatedAlbumName) {
                        val pendingName = pendingCreatedAlbumName ?: return@LaunchedEffect
                        val createdAlbum = albums.firstOrNull {
                            it.name.equals(pendingName, ignoreCase = true)
                        } ?: return@LaunchedEffect
                        albumsViewModel.addMembers(createdAlbum.id, mediaPendingAddToAlbum)
                        pendingCreatedAlbumName = null
                        mediaPendingAddToAlbum = emptyList()
                    }
                    // Non-fullscreen screens switch via if/else (not a back stack), so one can leave composition.
                    // This holder keeps each screen's saveable state (e.g., lazy scroll) from being discarded.
                    // screenKey includes folder path, so a different folder still opens at the top.
                    val saveableStateHolder = rememberSaveableStateHolder()
                    val screenKey = when {
                        isManageExcludedMode -> "manage_excluded"
                        isSettingsMode -> "settings"
                        selectedFolderPath != null -> "folder_detail:$selectedFolderPath"
                        selectedAlbumId != null -> "album_detail:$selectedAlbumId"
                        isFavouritesSelected -> "album_detail:favourites"
                        hasPermission -> "folder_list"
                        else -> "permission_denied"
                    }

                    if (selectedMedia != null) {
                        BackHandler {
                            viewModel.closeMedia()
                        }
                        FullImageScreen(
                            viewModel = viewModel,
                            favouritesAvailable = favouritesAvailable,
                            favouriteUris = favouriteUris,
                            isFavouritePending = pendingFavouriteWriteRequest != null,
                            onToggleFavourite = favouritesViewModel::toggleFavourite,
                            onAddToAlbum = { media ->
                                mediaPendingAddToAlbum = media
                                showAddToAlbumDialog = true
                            }
                        )
                    } else {
                        saveableStateHolder.SaveableStateProvider(screenKey) {
                            if (isManageExcludedMode) {
                                BackHandler {
                                    viewModel.setManageExcludedMode(false)
                                }
                                ManageExcludedScreen(viewModel)
                            } else if (isSettingsMode) {
                                BackHandler {
                                    viewModel.setSettingsMode(false)
                                }
                                SettingsScreen(viewModel, billingViewModel)
                            } else if (selectedFolder != null) {
                                FolderDetailScreen(
                                    viewModel = viewModel,
                                    favouritesAvailable = favouritesAvailable,
                                    favouriteUris = favouriteUris,
                                    isFavouritePending = pendingFavouriteWriteRequest != null,
                                    onToggleFavourites = favouritesViewModel::toggleFavourites,
                                    onAddToAlbum = { media ->
                                        mediaPendingAddToAlbum = media
                                        showAddToAlbumDialog = true
                                    }
                                )
                            } else if (selectedAlbumId != null) {
                                AlbumDetailScreen(
                                    galleryViewModel = viewModel,
                                    albumsViewModel = albumsViewModel,
                                    favouritesAvailable = favouritesAvailable,
                                    favouriteUris = favouriteUris,
                                    isFavouritePending = pendingFavouriteWriteRequest != null,
                                    onToggleFavourites = favouritesViewModel::toggleFavourites,
                                    onAddToAlbum = { media ->
                                        mediaPendingAddToAlbum = media
                                        showAddToAlbumDialog = true
                                    }
                                )
                            } else if (isFavouritesSelected) {
                                AlbumDetailScreen(
                                    galleryViewModel = viewModel,
                                    albumsViewModel = albumsViewModel,
                                    favouritesAvailable = favouritesAvailable,
                                    favouriteUris = favouriteUris,
                                    isFavouritePending = pendingFavouriteWriteRequest != null,
                                    isFavouritesAlbum = true,
                                    favouriteMedia = allMedia.filter { it.uri in favouriteUris },
                                    onBackFromFavourites = { isFavouritesSelected = false },
                                    onToggleFavourites = favouritesViewModel::toggleFavourites,
                                    onAddToAlbum = { media ->
                                        mediaPendingAddToAlbum = media
                                        showAddToAlbumDialog = true
                                    }
                                )
                            } else if (hasPermission) {
                                FolderListScreen(
                                    viewModel = viewModel,
                                    albumsViewModel = albumsViewModel,
                                    createFolderViewModel = createFolderViewModel,
                                    favouritesAvailable = favouritesAvailable,
                                    favouriteUris = favouriteUris,
                                    isFavouritePending = pendingFavouriteWriteRequest != null,
                                    onToggleFavourites = favouritesViewModel::toggleFavourites,
                                    onAddToAlbum = { media ->
                                        mediaPendingAddToAlbum = media
                                        showAddToAlbumDialog = true
                                    },
                                    onSelectFavourites = { isFavouritesSelected = true }
                                )
                            } else {
                                PermissionDeniedScreen {
                                    checkPermissions()
                                }
                            }
                        }
                    }

                    if (showAddToAlbumDialog) {
                        AddToAlbumDialog(
                            albums = albums.map { album ->
                                com.davide.seddio.easygallery.data.StoredAlbum(
                                    id = album.id,
                                    name = album.name,
                                    createdAt = album.createdAt,
                                    rule = null,
                                    coverMembershipId = null
                                )
                            },
                            onAlbumSelected = { albumId ->
                                albumsViewModel.addMembers(albumId, mediaPendingAddToAlbum)
                                mediaPendingAddToAlbum = emptyList()
                                showAddToAlbumDialog = false
                            },
                            onCreateNewAlbum = {
                                albumNameDraft = ""
                                showAddToAlbumDialog = false
                                showCreateAlbumDialog = true
                            },
                            onDismiss = {
                                mediaPendingAddToAlbum = emptyList()
                                showAddToAlbumDialog = false
                            }
                        )
                    }

                    if (showCreateAlbumDialog) {
                        CreateAlbumDialog(
                            name = albumNameDraft,
                            isNameDuplicate = albums.any {
                                it.name.trim().equals(albumNameDraft.trim(), ignoreCase = true)
                            },
                            onNameChange = { albumNameDraft = it },
                            onCreate = { name ->
                                pendingCreatedAlbumName = name.trim()
                                albumsViewModel.createAlbum(name)
                                showCreateAlbumDialog = false
                            },
                            onDismiss = {
                                mediaPendingAddToAlbum = emptyList()
                                showCreateAlbumDialog = false
                            }
                        )
                    }

                    if (shouldOfferManageMediaRationale) {
                        val notNowLabel = stringResource(R.string.manage_media_not_now)
                        val openSettingsLabel = stringResource(R.string.manage_media_open_settings)
                        AlertDialog(
                            onDismissRequest = favouritesViewModel::onManageMediaRationaleDeclined,
                            title = { Text(stringResource(R.string.manage_media_rationale_title)) },
                            text = { Text(stringResource(R.string.manage_media_rationale_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = favouritesViewModel::onManageMediaRationaleAccepted,
                                    modifier = Modifier.semantics {
                                        contentDescription = openSettingsLabel
                                    }
                                ) {
                                    Text(openSettingsLabel)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = favouritesViewModel::onManageMediaRationaleDeclined,
                                    modifier = Modifier.semantics {
                                        contentDescription = notNowLabel
                                    }
                                ) {
                                    Text(notNowLabel)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        favouritesViewModel.refreshCapabilityState()
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            hasPermission = true
            viewModel.loadFolders()
            requestMediaLocationPermissionIfNeeded()
        } else {
            requestPermissionsLauncher.launch(permissions)
        }
    }

    private fun requestMediaLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        // ACCESS_MEDIA_LOCATION is intentionally non-gating for app access.
        requestMediaLocationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
}

@Composable
fun PermissionDeniedScreen(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.permission_denied_message), color = androidx.compose.ui.graphics.Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
