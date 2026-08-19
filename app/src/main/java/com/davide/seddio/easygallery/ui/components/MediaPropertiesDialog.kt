package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MediaPropertiesDialog(media: List<MediaItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.properties_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.properties_items_selected, media.size), color = MaterialTheme.colorScheme.onSurface)
                val totalSizeMb = media.sumOf { it.size } / (1024 * 1024)
                Text(stringResource(R.string.properties_content_size, totalSizeMb.toString()), color = MaterialTheme.colorScheme.onSurface)

                if (media.size == 1) {
                    val item = media[0]
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.properties_name, item.name), fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.properties_path, "${item.folderPath}/${item.name}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val dateStr = remember(item.dateAdded) {
                        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(item.dateAdded * 1000))
                    }
                    Text(stringResource(R.string.properties_date, dateStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
