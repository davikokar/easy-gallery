package com.davide.seddio.easygallery.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.PreferenceApplyTarget

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnCountDialog(
    currentCount: Int,
    @StringRes settingLabel: Int? = null,
    onConfirm: (Int, PreferenceApplyTarget) -> Unit,
    onDismiss: () -> Unit
) {
    var draftCount by remember(currentCount) { mutableIntStateOf(currentCount) }
    val scopeSelector = rememberPreferenceScopeSelector(settingLabel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.column_count_title)) },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(250.dp)
                ) {
                    items(20) { index ->
                        val count = index + 1
                        val isSelected = count == draftCount
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .combinedClickable(
                                    onClick = { draftCount = count }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = count.toString(),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Normal else FontWeight.Normal
                            )
                        }
                    }
                }
                scopeSelector.CheckboxRow()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scopeSelector.onOk(draftCount) { count, target ->
                        onConfirm(count, target)
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
