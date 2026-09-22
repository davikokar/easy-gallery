package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onInfoClick: () -> Unit,
    canUseAsBackground: Boolean,
    onUseAsBackground: () -> Unit,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onSelectAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.selection_count, selectedCount, totalCount), color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_exit_selection), tint = Color.White)
            }
        },
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options), tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        modifier = Modifier.testTag("delete_button"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_share)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.properties_title)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onInfoClick()
                        }
                    )
                    if (canUseAsBackground) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_use_as_wallpaper)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onUseAsBackground()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_to)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FolderCopy,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onCopyTo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_move_to)) },
                        modifier = Modifier.testTag("move_to_button"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onMoveTo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_select_all)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onSelectAll()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BrandBlue,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}
