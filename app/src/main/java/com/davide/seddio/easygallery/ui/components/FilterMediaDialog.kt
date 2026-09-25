package com.davide.seddio.easygallery.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaType
import com.davide.seddio.easygallery.data.PreferenceApplyTarget

@Composable
fun FilterMediaDialog(
    initialSelectedTypes: Set<MediaType>,
    @StringRes settingLabel: Int? = null,
    onConfirm: (Set<MediaType>, PreferenceApplyTarget) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelectedTypes by remember { mutableStateOf(initialSelectedTypes) }
    val scopeSelector = rememberPreferenceScopeSelector(settingLabel)

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
                if (settingLabel != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    scopeSelector.CheckboxRow()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scopeSelector.onOk(tempSelectedTypes) { selectedTypes, target ->
                        onConfirm(selectedTypes, target)
                    }
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    scopeSelector.ConfirmationDialog()
}

@Composable
fun MediaTypeFilterItem(label: String, type: MediaType, checked: Boolean, onToggle: (MediaType) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onToggle(type) }
            )
            .padding(vertical = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
