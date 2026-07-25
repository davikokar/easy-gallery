package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.davide.seddio.easygallery.ui.theme.TopBarBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onInfoClick: () -> Unit,
    onSelectAll: () -> Unit,
    onExclude: () -> Unit,
    onRename: () -> Unit,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("$selectedCount / $totalCount", color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Exit Selection", tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Properties", tint = Color.White)
            }
            IconButton(onClick = onPin) {
                Icon(Icons.Default.PushPin, contentDescription = "Pin/Unpin", tint = Color.White)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy to") },
                        onClick = {
                            showMenu = false
                            onCopyTo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to") },
                        onClick = {
                            showMenu = false
                            onMoveTo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Exclude") },
                        onClick = {
                            showMenu = false
                            onExclude()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = {
                            showMenu = false
                            onSelectAll()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TopBarBlue,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}
