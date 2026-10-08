package com.davide.seddio.easygallery.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.StoredAlbum

@Composable
fun CreateAlbumDialog(
    name: String,
    isNameDuplicate: Boolean,
    onNameChange: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlbumNameDialog(
        title = R.string.album_create_title,
        confirmLabel = R.string.action_create,
        name = name,
        isNameDuplicate = isNameDuplicate,
        onNameChange = onNameChange,
        onConfirm = onCreate,
        onDismiss = onDismiss
    )
}

@Composable
fun AlbumTypeDialog(
    onManualSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelLabel = stringResource(R.string.action_cancel)
    val manualTitle = stringResource(R.string.album_type_manual_title)
    val automaticTitle = stringResource(R.string.album_type_automatic_title)
    val notAvailable = stringResource(R.string.album_type_not_available)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_type_title)) },
        text = {
            Column {
                AlbumTypeOption(
                    icon = Icons.Default.PhotoAlbum,
                    title = manualTitle,
                    description = stringResource(R.string.album_type_manual_description),
                    enabled = true,
                    testTag = "album_type_manual",
                    onClick = onManualSelected
                )
                Spacer(modifier = Modifier.height(8.dp))
                AlbumTypeOption(
                    icon = Icons.Default.AutoAwesome,
                    title = automaticTitle,
                    description = stringResource(R.string.album_type_automatic_description),
                    enabled = false,
                    badge = notAvailable,
                    testTag = "album_type_automatic",
                    onClick = {}
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = cancelLabel }
            ) {
                Text(cancelLabel)
            }
        }
    )
}

@Composable
private fun AlbumTypeOption(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag(testTag)
            .semantics {
                contentDescription = if (badge == null) title else "$title, $badge"
                if (!enabled) disabled()
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RenameAlbumDialog(
    name: String,
    isNameDuplicate: Boolean,
    onNameChange: (String) -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlbumNameDialog(
        title = R.string.album_rename_title,
        confirmLabel = R.string.album_rename_title,
        name = name,
        isNameDuplicate = isNameDuplicate,
        onNameChange = onNameChange,
        onConfirm = onRename,
        onDismiss = onDismiss
    )
}

@Composable
fun DeleteAlbumDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val deleteLabel = stringResource(R.string.action_delete)
    val cancelLabel = stringResource(R.string.action_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_delete_title)) },
        text = { Text(stringResource(R.string.album_delete_message)) },
        confirmButton = {
            Button(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteLabel }
            ) {
                Text(deleteLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = cancelLabel }
            ) {
                Text(cancelLabel)
            }
        }
    )
}

@Composable
fun AddToAlbumDialog(
    albums: List<StoredAlbum>,
    onAlbumSelected: (Long) -> Unit,
    onCreateNewAlbum: () -> Unit,
    onDismiss: () -> Unit
) {
    val newAlbumLabel = stringResource(R.string.menu_new_album)
    val cancelLabel = stringResource(R.string.action_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_add_to_title)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                item {
                    DropdownMenuItem(
                        text = { Text(newAlbumLabel) },
                        onClick = onCreateNewAlbum,
                        modifier = Modifier.semantics {
                            contentDescription = newAlbumLabel
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                        }
                    )
                    HorizontalDivider()
                }
                items(albums, key = { it.id }) { album ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = album.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = { onAlbumSelected(album.id) },
                        modifier = Modifier.semantics {
                            contentDescription = album.name
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PhotoAlbum,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = cancelLabel }
            ) {
                Text(cancelLabel)
            }
        }
    )
}

@Composable
private fun AlbumNameDialog(
    @StringRes title: Int,
    @StringRes confirmLabel: Int,
    name: String,
    isNameDuplicate: Boolean,
    onNameChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val error = when {
        name.isBlank() -> R.string.album_name_blank
        isNameDuplicate -> R.string.album_name_duplicate
        else -> null
    }
    val fieldLabel = stringResource(R.string.album_name_hint)
    val confirmText = stringResource(confirmLabel)
    val cancelLabel = stringResource(R.string.action_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(fieldLabel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = fieldLabel },
                    isError = error != null,
                    singleLine = true
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = error == null,
                modifier = Modifier.semantics { contentDescription = confirmText }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = cancelLabel }
            ) {
                Text(cancelLabel)
            }
        }
    )
}