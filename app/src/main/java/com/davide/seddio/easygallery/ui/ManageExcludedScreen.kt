package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageExcludedScreen(viewModel: GalleryViewModel) {
    val excludedFolders by viewModel.excludedFolders.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Excluded", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setManageExcludedMode(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Folder", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = AppBackground
    ) { padding ->
        if (showAddDialog) {
            AddExcludedFolderDialog(
                folders = viewModel.getNonExcludedFolders(),
                onFolderSelected = {
                    viewModel.excludeFolder(it.path)
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false }
            )
        }

        if (excludedFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No excluded folders", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppBackground)
            ) {
                items(excludedFolders.toList().sorted()) { folderPath ->
                    val folderName = folderPath.substringAfterLast("/", folderPath)
                    ExcludedFolderItem(
                        name = folderName,
                        path = folderPath,
                        onRemove = { viewModel.unexcludeFolder(folderPath) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExcludedFolderItem(name: String, path: String, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = path,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun AddExcludedFolderDialog(
    folders: List<Folder>,
    onFolderSelected: (Folder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exclude Folder") },
        text = {
            if (folders.isEmpty()) {
                Text("No more folders to exclude.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(folders) { folder ->
                        TextButton(
                            onClick = { onFolderSelected(folder) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = folder.name, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
