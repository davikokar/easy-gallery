package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.Folder

@Composable
fun FolderBrowser(
    currentPath: String,
    folders: List<Folder>,
    onFolderClick: (Folder) -> Unit,
    onBreadcrumbClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Breadcrumb(
            path = currentPath,
            onBreadcrumbClick = onBreadcrumbClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_subfolders), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(folders) { folder ->
                    FolderBrowserItem(
                        folder = folder,
                        onClick = { onFolderClick(folder) }
                    )
                }
            }
        }
    }
}

@Composable
fun DestinationFolderPickerDialog(
    title: String,
    currentPath: String,
    folders: List<Folder>,
    onFolderSelected: (Folder) -> Unit,
    onBreadcrumbClick: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FolderBrowser(
                currentPath = currentPath,
                folders = folders,
                onFolderClick = onFolderSelected,
                onBreadcrumbClick = onBreadcrumbClick
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun Breadcrumb(path: String, onBreadcrumbClick: (String) -> Unit) {
    val internalStorageLabel = stringResource(R.string.internal_storage)
    val segments = remember(path, internalStorageLabel) {
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        val relative = path.removePrefix(root).trimStart('/')
        val list = mutableListOf(internalStorageLabel to root)
        if (relative.isNotEmpty()) {
            var current = root
            relative.split('/').forEach { segment ->
                current = "$current/$segment"
                list.add(segment to current)
            }
        }
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (label, fullPath) ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (index == segments.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onBreadcrumbClick(fullPath) }
            )
            if (index < segments.lastIndex) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun FolderBrowserItem(folder: Folder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = folder.name,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
