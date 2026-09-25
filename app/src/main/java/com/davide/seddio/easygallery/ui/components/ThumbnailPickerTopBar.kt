package com.davide.seddio.easygallery.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailPickerTopBar(
    hasDraft: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.thumbnail_picker_title), color = Color.White) },
        navigationIcon = {
            IconButton(onClick = if (hasDraft) onConfirm else onCancel) {
                Icon(
                    imageVector = if (hasDraft) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                    contentDescription = stringResource(if (hasDraft) R.string.cd_back else R.string.cd_exit_selection),
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BrandBlue,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}
