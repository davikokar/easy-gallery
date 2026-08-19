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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.ui.theme.BrandBlue

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
    onColumnCountClick: (() -> Unit)? = null,
    onGroupByClick: (() -> Unit)? = null,
    onViewTypeClick: (() -> Unit)? = null,
    onFilterMediaClick: (() -> Unit)? = null,
    onShowExcludedClick: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
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
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_cancel_search),
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
    } else {
        TopAppBar(
            title = { Text(title, color = Color.White) },
            navigationIcon = navigationIcon ?: {},
            actions = {
                if (onToggleDisplayMode != null && displayMode != null) {
                    IconButton(onClick = onToggleDisplayMode) {
                        Icon(
                            imageVector = if (displayMode == DisplayMode.GALLERY) Icons.Default.CalendarMonth else Icons.Default.Image,
                            contentDescription = stringResource(R.string.cd_toggle_display_mode),
                            tint = Color.White
                        )
                    }
                }
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), tint = Color.White)
                }
                
                actions?.invoke(this)

                // Overflow Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options), tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_title)) },
                            onClick = {
                                showMenu = false
                                onSortClick?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.column_count_title)) },
                            onClick = {
                                showMenu = false
                                onColumnCountClick?.invoke()
                            }
                        )
                        if (onGroupByClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_by_title)) },
                                onClick = {
                                    showMenu = false
                                    onGroupByClick.invoke()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_show_excluded)) },
                            onClick = {
                                showMenu = false
                                onShowExcludedClick?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.filter_media_title)) },
                            modifier = Modifier.testTag("filter_media_button"),
                            onClick = {
                                showMenu = false
                                onFilterMediaClick?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_view_type_title)) },
                            onClick = {
                                showMenu = false
                                onViewTypeClick?.invoke()
                            }
                        )
                        if (onCreateFolderClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_create_folder)) },
                                modifier = Modifier.testTag("create_folder_button"),
                                onClick = {
                                    showMenu = false
                                    onCreateFolderClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            onClick = {
                                showMenu = false
                                onSettingsClick?.invoke()
                            }
                        )
                    }
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
}
