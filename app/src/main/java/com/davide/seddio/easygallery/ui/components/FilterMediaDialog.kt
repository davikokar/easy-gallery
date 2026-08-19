package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaType

@Composable
fun FilterMediaDialog(
    initialSelectedTypes: Set<MediaType>,
    onConfirm: (Set<MediaType>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelectedTypes by remember { mutableStateOf(initialSelectedTypes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_media_title)) },
        text = {
            Column {
                MediaTypeFilterItem(
                    label = stringResource(R.string.filter_images),
                    type = MediaType.IMAGE,
                    checked = tempSelectedTypes.contains(MediaType.IMAGE),
                    onToggle = { type ->
                        tempSelectedTypes = if (tempSelectedTypes.contains(type)) {
                            tempSelectedTypes - type
                        } else {
                            tempSelectedTypes + type
                        }
                    }
                )
                MediaTypeFilterItem(
                    label = stringResource(R.string.filter_videos),
                    type = MediaType.VIDEO,
                    checked = tempSelectedTypes.contains(MediaType.VIDEO),
                    onToggle = { type ->
                        tempSelectedTypes = if (tempSelectedTypes.contains(type)) {
                            tempSelectedTypes - type
                        } else {
                            tempSelectedTypes + type
                        }
                    }
                )
                MediaTypeFilterItem(
                    label = stringResource(R.string.filter_gifs),
                    type = MediaType.GIF,
                    checked = tempSelectedTypes.contains(MediaType.GIF),
                    onToggle = { type ->
                        tempSelectedTypes = if (tempSelectedTypes.contains(type)) {
                            tempSelectedTypes - type
                        } else {
                            tempSelectedTypes + type
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tempSelectedTypes) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun MediaTypeFilterItem(label: String, type: MediaType, checked: Boolean, onToggle: (MediaType) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(type) }
            .padding(vertical = 8.dp)
    ) {
        Checkbox(
            checked = checked, 
            onCheckedChange = { onToggle(type) }
        )
        Text(
            text = label, 
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
