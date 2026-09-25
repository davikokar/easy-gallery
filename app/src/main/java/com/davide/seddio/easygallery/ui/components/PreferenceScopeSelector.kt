package com.davide.seddio.easygallery.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import com.davide.seddio.easygallery.data.PreferenceApplyTarget

@Composable
fun rememberPreferenceScopeSelector(
    @StringRes settingLabel: Int?
): PreferenceScopeSelector = remember(settingLabel) {
    PreferenceScopeSelector(settingLabel)
}

@Stable
class PreferenceScopeSelector internal constructor(
    @StringRes private val settingLabel: Int?
) {
    private var applyOnlyToCurrentFolder by mutableStateOf(true)
    private var pendingCommit by mutableStateOf<(() -> Unit)?>(null)

    @Composable
    fun CheckboxRow(modifier: Modifier = Modifier) {
        if (settingLabel == null) return

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .toggleable(
                    value = applyOnlyToCurrentFolder,
                    role = Role.Checkbox,
                    onValueChange = { applyOnlyToCurrentFolder = it }
                )
                .padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = applyOnlyToCurrentFolder,
                onCheckedChange = null
            )
            Text(
                text = stringResource(R.string.apply_only_to_this_folder),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    @Composable
    fun ConfirmationDialog() {
        val label = settingLabel ?: return
        if (pendingCommit == null) return

        AlertDialog(
            onDismissRequest = ::cancelPendingCommit,
            title = { Text(stringResource(R.string.apply_to_all_folders_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.apply_to_all_folders_message,
                        stringResource(label)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = ::confirmPendingCommit) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = ::cancelPendingCommit) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    fun <T> onOk(
        value: T,
        commit: (T, PreferenceApplyTarget) -> Unit
    ) {
        when {
            settingLabel == null -> commit(value, PreferenceApplyTarget.ALL_FOLDERS)
            applyOnlyToCurrentFolder -> commit(value, PreferenceApplyTarget.CURRENT_FOLDER)
            else -> pendingCommit = { commit(value, PreferenceApplyTarget.ALL_FOLDERS) }
        }
    }

    private fun confirmPendingCommit() {
        val commit = pendingCommit
        pendingCommit = null
        commit?.invoke()
    }

    private fun cancelPendingCommit() {
        pendingCommit = null
    }
}