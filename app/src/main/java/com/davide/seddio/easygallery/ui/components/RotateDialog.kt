package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RotateDialog(
    onRotate: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rotate Images") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onRotate(-90) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("90° Left")
                }
                TextButton(
                    onClick = { onRotate(90) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("90° Right")
                }
                TextButton(
                    onClick = { onRotate(180) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("180°")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
