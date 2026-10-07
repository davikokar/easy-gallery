package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onDisplayModeChange: ((DisplayMode) -> Unit)? = null,
    onSortClick: (() -> Unit)? = null,
    onColumnCountClick: (() -> Unit)? = null,
    onGroupByClick: (() -> Unit)? = null,
    onViewTypeClick: (() -> Unit)? = null,
    onChangeThumbnailClick: (() -> Unit)? = null,
    onFilterMediaClick: (() -> Unit)? = null,
    onShowExcludedClick: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onCreateAlbumClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }

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
            title = {
                if (displayMode != null && onDisplayModeChange != null) {
                    val switchViewDescription = stringResource(R.string.cd_switch_view)
                    Box {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = switchViewDescription }
                                .clickable { showViewMenu = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_title_logo),
                                contentDescription = null,
                                // Asset carries alpha only; the cream is the app icon's own colour.
                                tint = Color(0xFFF7EFDD),
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("main_view_app_icon")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showViewMenu,
                            onDismissRequest = { showViewMenu = false }
                        ) {
                            DisplayMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (mode) {
                                                DisplayMode.FOLDERS -> stringResource(R.string.folders_title)
                                                DisplayMode.TIMELINE -> stringResource(R.string.timeline_title)
                                                DisplayMode.ALBUMS -> stringResource(R.string.albums_title)
                                            }
                                        )
                                    },
                                    trailingIcon = if (mode == displayMode) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        showViewMenu = false
                                        onDisplayModeChange(mode)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = navigationIcon ?: {},
            actions = {
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
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onSortClick?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.column_count_title)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ViewColumn,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onColumnCountClick?.invoke()
                            }
                        )
                        if (onGroupByClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_by_title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onGroupByClick.invoke()
                                }
                            )
                        }
                        if (onShowExcludedClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_show_excluded)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onShowExcludedClick.invoke()
                                }
                            )
                        }
                        if (onFilterMediaClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_media_title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FilterAlt,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.testTag("filter_media_button"),
                                onClick = {
                                    showMenu = false
                                    onFilterMediaClick.invoke()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_view_type_title)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onViewTypeClick?.invoke()
                            }
                        )
                        if (onChangeThumbnailClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_change_folder_thumbnail)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onChangeThumbnailClick.invoke()
                                }
                            )
                        }
                        if (onCreateFolderClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_create_folder)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CreateNewFolder,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.testTag("create_folder_button"),
                                onClick = {
                                    showMenu = false
                                    onCreateFolderClick()
                                }
                            )
                        }
                        if (onCreateAlbumClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_new_album)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PhotoAlbum,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.testTag("create_album_button"),
                                onClick = {
                                    showMenu = false
                                    onCreateAlbumClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )
                            },
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
