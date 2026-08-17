package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.data.Folder
import com.davide.seddio.easygallery.data.SortOrder
import com.davide.seddio.easygallery.data.SortType
import com.davide.seddio.easygallery.data.ViewType
import com.davide.seddio.easygallery.ui.components.*

@Composable
fun PropertiesDialog(folders: List<Folder>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Items selected: ${folders.size}", color = MaterialTheme.colorScheme.onSurface)
                val totalSizeMb = folders.sumOf { it.size } / (1024 * 1024)
                Text("Content size: $totalSizeMb MB", color = MaterialTheme.colorScheme.onSurface)
                val totalFiles = folders.sumOf { it.imageCount }
                Text("Total files count: $totalFiles", color = MaterialTheme.colorScheme.onSurface)

                if (folders.size == 1) {
                    val folder = folders[0]
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Name: ${folder.name}", fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                    Text("Path: ${folder.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun ViewTypeDialog(
    currentViewType: ViewType,
    onViewTypeSelected: (ViewType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change view type") },
        text = {
            Column(Modifier.selectableGroup()) {
                ViewTypeOption("Grid", ViewType.GRID, currentViewType == ViewType.GRID, onViewTypeSelected)
                ViewTypeOption("List", ViewType.LIST, currentViewType == ViewType.LIST, onViewTypeSelected)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        title = { Text("Sort by") },
        text = {
            Column(Modifier.selectableGroup()) {
                SortOption("Name", SortType.NAME, currentSort == SortType.NAME, onSortSelected)
                SortOption("Path", SortType.PATH, currentSort == SortType.PATH, onSortSelected)
                SortOption("Size", SortType.SIZE, currentSort == SortType.SIZE, onSortSelected)
                SortOption("Last Modified", SortType.LAST_MODIFIED, currentSort == SortType.LAST_MODIFIED, onSortSelected)
                SortOption("Date Taken", SortType.DATE_TAKEN, currentSort == SortType.DATE_TAKEN, onSortSelected)
                SortOption("Random", SortType.RANDOM, currentSort == SortType.RANDOM, onSortSelected)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val isOrderEnabled = currentSort != SortType.RANDOM
                
                OrderOption(
                    label = "Ascending",
                    order = SortOrder.ASCENDING,
                    selected = currentOrder == SortOrder.ASCENDING,
                    enabled = isOrderEnabled,
                    onOrderSelected = onOrderSelected
                )
                OrderOption(
                    label = "Descending",
                    order = SortOrder.DESCENDING,
                    selected = currentOrder == SortOrder.DESCENDING,
                    enabled = isOrderEnabled,
                    onOrderSelected = onOrderSelected
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
