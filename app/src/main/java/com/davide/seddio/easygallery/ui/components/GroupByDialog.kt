package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.ui.GroupByType
import com.davide.seddio.easygallery.ui.SortOrder

@Composable
fun GroupByDialog(
    currentGroupBy: GroupByType,
    currentOrder: SortOrder,
    onGroupBySelected: (GroupByType) -> Unit,
    onOrderSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group by") },
        text = {
            Column(Modifier.selectableGroup()) {
                GroupByOption("Do not group", GroupByType.NONE, currentGroupBy == GroupByType.NONE, onGroupBySelected)
                GroupByOption("Date taken (daily)", GroupByType.DATE_TAKEN_DAILY, currentGroupBy == GroupByType.DATE_TAKEN_DAILY, onGroupBySelected)
                GroupByOption("Date taken (monthly)", GroupByType.DATE_TAKEN_MONTHLY, currentGroupBy == GroupByType.DATE_TAKEN_MONTHLY, onGroupBySelected)
                GroupByOption("Last modified (daily)", GroupByType.LAST_MODIFIED_DAILY, currentGroupBy == GroupByType.LAST_MODIFIED_DAILY, onGroupBySelected)
                GroupByOption("Last modified (monthly)", GroupByType.LAST_MODIFIED_MONTHLY, currentGroupBy == GroupByType.LAST_MODIFIED_MONTHLY, onGroupBySelected)
                GroupByOption("File type", GroupByType.FILE_TYPE, currentGroupBy == GroupByType.FILE_TYPE, onGroupBySelected)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val isOrderEnabled = currentGroupBy != GroupByType.NONE
                
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

@Composable
fun GroupByOption(
    label: String,
    type: GroupByType,
    selected: Boolean,
    onGroupBySelected: (GroupByType) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = { onGroupBySelected(type) },
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
