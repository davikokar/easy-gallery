package com.davide.seddio.easygallery.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount / $totalCount") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Exit Selection")
            }
        },
        actions = {
            IconButton(onClick = onPin) {
                Icon(Icons.Default.PushPin, contentDescription = "Pin/Unpin")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    )
}
