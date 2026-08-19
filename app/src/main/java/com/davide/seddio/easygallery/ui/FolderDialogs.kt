package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.ui.components.*

@Composable
fun PropertiesDialog(folders: List<Folder>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.properties_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.properties_items_selected, folders.size), color = MaterialTheme.colorScheme.onSurface)
                val totalSizeMb = folders.sumOf { it.size } / (1024 * 1024)
                Text(stringResource(R.string.properties_content_size, totalSizeMb.toString()), color = MaterialTheme.colorScheme.onSurface)
                val totalFiles = folders.sumOf { it.imageCount }
                Text(stringResource(R.string.properties_total_files, totalFiles), color = MaterialTheme.colorScheme.onSurface)

                if (folders.size == 1) {
                    val folder = folders[0]
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.properties_name, folder.name), fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.properties_path, folder.path), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun ViewTypeDialog(
    currentViewType: ViewType,
    onViewTypeSelected: (ViewType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_view_type_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ViewTypeOption(stringResource(R.string.view_type_grid), ViewType.GRID, currentViewType == ViewType.GRID, onViewTypeSelected)
                ViewTypeOption(stringResource(R.string.view_type_list), ViewType.LIST, currentViewType == ViewType.LIST, onViewTypeSelected)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun ViewTypeOption(
    label: String,
    type: ViewType,
    selected: Boolean,
    onViewTypeSelected: (ViewType) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = selected,
                onClick = { onViewTypeSelected(type) },
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SortDialog(
    currentSort: SortType,
    currentOrder: SortOrder,
    onSortSelected: (SortType) -> Unit,
    onOrderSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_by_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                SortOption(stringResource(R.string.sort_name), SortType.NAME, currentSort == SortType.NAME, onSortSelected)
                SortOption(stringResource(R.string.sort_path), SortType.PATH, currentSort == SortType.PATH, onSortSelected)
                SortOption(stringResource(R.string.sort_size), SortType.SIZE, currentSort == SortType.SIZE, onSortSelected)
                SortOption(stringResource(R.string.sort_last_modified), SortType.LAST_MODIFIED, currentSort == SortType.LAST_MODIFIED, onSortSelected)
                SortOption(stringResource(R.string.sort_date_taken), SortType.DATE_TAKEN, currentSort == SortType.DATE_TAKEN, onSortSelected)
                SortOption(stringResource(R.string.sort_random), SortType.RANDOM, currentSort == SortType.RANDOM, onSortSelected)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val isOrderEnabled = currentSort != SortType.RANDOM
                
                OrderOption(
                    label = stringResource(R.string.sort_ascending),
                    order = SortOrder.ASCENDING,
                    selected = currentOrder == SortOrder.ASCENDING,
                    enabled = isOrderEnabled,
                    onOrderSelected = onOrderSelected
                )
                OrderOption(
                    label = stringResource(R.string.sort_descending),
                    order = SortOrder.DESCENDING,
                    selected = currentOrder == SortOrder.DESCENDING,
                    enabled = isOrderEnabled,
                    onOrderSelected = onOrderSelected
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    )
}
