package com.davide.seddio.easygallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.davide.seddio.easygallery.ui.FullImageScreen
import com.davide.seddio.easygallery.ui.ManageExcludedScreen
import com.davide.seddio.easygallery.ui.SettingsScreen
import com.davide.seddio.easygallery.ui.BillingViewModel
import com.davide.seddio.easygallery.ui.FolderDetailScreen
import com.davide.seddio.easygallery.ui.FolderListScreen
import com.davide.seddio.easygallery.ui.CreateFolderViewModel
import com.davide.seddio.easygallery.ui.GalleryViewModel
import com.davide.seddio.easygallery.ui.theme.EasyGalleryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()
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

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasPermission = allGranted
        if (allGranted) {
            viewModel.loadFolders()
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )

        checkPermissions()

        setContent {
            val pendingWriteRequest by viewModel.pendingWriteRequest.collectAsState()
            
            LaunchedEffect(pendingWriteRequest) {
                pendingWriteRequest?.let {
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                }
            }

            EasyGalleryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val selectedFolder: com.davide.seddio.easygallery.data.Folder? by viewModel.selectedFolder.collectAsState()
                    val selectedMedia: com.davide.seddio.easygallery.data.MediaItem? by viewModel.selectedMedia.collectAsState()
                    val isManageExcludedMode by viewModel.isManageExcludedMode.collectAsState()
                    val isSettingsMode by viewModel.isSettingsMode.collectAsState()

                    if (selectedMedia != null) {
                        BackHandler {
                            viewModel.closeMedia()
                        }
                        FullImageScreen(viewModel)
                    } else if (isManageExcludedMode) {
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
                        FolderDetailScreen(viewModel)
                    } else if (hasPermission) {
                        FolderListScreen(viewModel, createFolderViewModel)
                    } else {
                        PermissionDeniedScreen {
                            checkPermissions()
                        }
                    }
                }
            }
        }
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
        } else {
            requestPermissionsLauncher.launch(permissions)
        }
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
