package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CreateFolderDialog(
    currentPath: String,
    folders: List<com.davide.seddio.easygallery.data.Folder>,
    error: String?,
    onPathChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Folder") },
        text = {
            Column {
                FolderBrowser(
                    currentPath = currentPath,
                    folders = folders,
                    onFolderClick = { onPathChange(it.path) },
                    onBreadcrumbClick = { onPathChange(it) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("New Folder Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_field"),
                    isError = error != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        errorTextColor = Color.Red,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        errorBorderColor = Color.Red
                    )
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("folder_name_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(folderName) },
                modifier = Modifier.testTag("confirm_create_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_create_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
