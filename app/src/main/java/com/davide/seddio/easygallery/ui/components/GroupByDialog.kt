package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.SortOrder

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
        title = { Text(stringResource(R.string.group_by_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                GroupByOption(stringResource(R.string.group_by_none), GroupByType.NONE, currentGroupBy == GroupByType.NONE, onGroupBySelected)
                GroupByOption(stringResource(R.string.group_by_date_taken_daily), GroupByType.DATE_TAKEN_DAILY, currentGroupBy == GroupByType.DATE_TAKEN_DAILY, onGroupBySelected)
                GroupByOption(stringResource(R.string.group_by_date_taken_monthly), GroupByType.DATE_TAKEN_MONTHLY, currentGroupBy == GroupByType.DATE_TAKEN_MONTHLY, onGroupBySelected)
                GroupByOption(stringResource(R.string.group_by_last_modified_daily), GroupByType.LAST_MODIFIED_DAILY, currentGroupBy == GroupByType.LAST_MODIFIED_DAILY, onGroupBySelected)
                GroupByOption(stringResource(R.string.group_by_last_modified_monthly), GroupByType.LAST_MODIFIED_MONTHLY, currentGroupBy == GroupByType.LAST_MODIFIED_MONTHLY, onGroupBySelected)
                GroupByOption(stringResource(R.string.group_by_file_type), GroupByType.FILE_TYPE, currentGroupBy == GroupByType.FILE_TYPE, onGroupBySelected)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val isOrderEnabled = currentGroupBy != GroupByType.NONE
                
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
