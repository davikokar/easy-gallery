package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.davide.seddio.easygallery.ui.DisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    title: String,
    searchQuery: String,
    isSearchActive: Boolean,
    displayMode: DisplayMode? = null,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onToggleDisplayMode: (() -> Unit)? = null,
    onSortClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    if (isSearchActive) {
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel Search")
                }
            }
        )
    } else {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon ?: {},
            actions = {
                if (onToggleDisplayMode != null && displayMode != null) {
                    IconButton(onClick = onToggleDisplayMode) {
                        Icon(
                            imageVector = if (displayMode == DisplayMode.GALLERY) Icons.Default.CalendarMonth else Icons.Default.Image,
                            contentDescription = "Toggle Display Mode"
                        )
                    }
                }
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                
                // Overflow Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by") },
                            onClick = {
                                showMenu = false
                                onSortClick?.invoke()
                            }
                        )
                        DropdownMenuItem(text = { Text("Column count") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Temporarily show excluded") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Filter media") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Change view type") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { showMenu = false })
                    }
                }
                actions?.invoke(this)
            }
        )
    }
}
