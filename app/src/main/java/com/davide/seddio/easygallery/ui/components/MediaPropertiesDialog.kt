package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.data.MediaItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MediaPropertiesDialog(media: List<MediaItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Items selected: ${media.size}", color = MaterialTheme.colorScheme.onSurface)
                val totalSizeMb = media.sumOf { it.size } / (1024 * 1024)
                Text("Content size: $totalSizeMb MB", color = MaterialTheme.colorScheme.onSurface)

                if (media.size == 1) {
                    val item = media[0]
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Name: ${item.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Path: ${item.folderPath}/${item.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val dateStr = remember(item.dateAdded) {
                        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(item.dateAdded * 1000))
                    }
                    Text("Date: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
