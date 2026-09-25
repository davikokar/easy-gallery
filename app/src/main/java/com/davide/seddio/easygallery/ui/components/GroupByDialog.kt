package com.davide.seddio.easygallery.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.GroupByType
import com.davide.seddio.easygallery.data.PreferenceApplyTarget
import com.davide.seddio.easygallery.data.SortOrder

@Composable
fun GroupByDialog(
    currentGroupBy: GroupByType,
    currentOrder: SortOrder,
    @StringRes settingLabel: Int? = null,
    onConfirm: (GroupByType, SortOrder, PreferenceApplyTarget) -> Unit,
    onDismiss: () -> Unit
) {
    var draftGroupBy by remember(currentGroupBy) { mutableStateOf(currentGroupBy) }
    var draftOrder by remember(currentOrder) { mutableStateOf(currentOrder) }
    val scopeSelector = rememberPreferenceScopeSelector(settingLabel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_by_title)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    GroupByOption(stringResource(R.string.group_by_none), GroupByType.NONE, draftGroupBy == GroupByType.NONE) { draftGroupBy = it }
                    GroupByOption(stringResource(R.string.group_by_date_taken_daily), GroupByType.DATE_TAKEN_DAILY, draftGroupBy == GroupByType.DATE_TAKEN_DAILY) { draftGroupBy = it }
                    GroupByOption(stringResource(R.string.group_by_date_taken_monthly), GroupByType.DATE_TAKEN_MONTHLY, draftGroupBy == GroupByType.DATE_TAKEN_MONTHLY) { draftGroupBy = it }
                    GroupByOption(stringResource(R.string.group_by_last_modified_daily), GroupByType.LAST_MODIFIED_DAILY, draftGroupBy == GroupByType.LAST_MODIFIED_DAILY) { draftGroupBy = it }
                    GroupByOption(stringResource(R.string.group_by_last_modified_monthly), GroupByType.LAST_MODIFIED_MONTHLY, draftGroupBy == GroupByType.LAST_MODIFIED_MONTHLY) { draftGroupBy = it }
                    GroupByOption(stringResource(R.string.group_by_file_type), GroupByType.FILE_TYPE, draftGroupBy == GroupByType.FILE_TYPE) { draftGroupBy = it }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val isOrderEnabled = draftGroupBy != GroupByType.NONE

                    OrderOption(
                        label = stringResource(R.string.sort_ascending),
                        order = SortOrder.ASCENDING,
                        selected = draftOrder == SortOrder.ASCENDING,
                        enabled = isOrderEnabled,
                        onOrderSelected = { draftOrder = it }
                    )
                    OrderOption(
                        label = stringResource(R.string.sort_descending),
                        order = SortOrder.DESCENDING,
                        selected = draftOrder == SortOrder.DESCENDING,
                        enabled = isOrderEnabled,
                        onOrderSelected = { draftOrder = it }
                    )
                }
                scopeSelector.CheckboxRow()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scopeSelector.onOk(draftGroupBy to draftOrder) { (groupBy, order), target ->
                        onConfirm(groupBy, order, target)
                    }
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
    scopeSelector.ConfirmationDialog()
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
