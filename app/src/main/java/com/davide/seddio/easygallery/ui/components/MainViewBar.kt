package com.davide.seddio.easygallery.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.DisplayMode
import com.davide.seddio.easygallery.ui.theme.BrandBlue
import com.davide.seddio.easygallery.ui.theme.BrandBlueLight
import com.davide.seddio.easygallery.ui.theme.MainViewBarBorder
import com.davide.seddio.easygallery.ui.theme.MainViewBarContainer
import com.davide.seddio.easygallery.ui.theme.MainViewBarIndicator
import com.davide.seddio.easygallery.ui.theme.MainViewBarUnselected

/** The three tabs of the [MainViewBar]. Declared in bar order, which is not [DisplayMode] order. */
enum class MainViewTab { FOLDERS, ALBUMS, TIMELINE }

fun DisplayMode.toMainViewTab(): MainViewTab = when (this) {
    DisplayMode.FOLDERS -> MainViewTab.FOLDERS
    DisplayMode.ALBUMS -> MainViewTab.ALBUMS
    DisplayMode.TIMELINE -> MainViewTab.TIMELINE
}

/**
 * Stateless: the caller derives [selectedTab] and decides what a tap does, including a re-tap.
 * Settings is a button after the tabs, not a tab: it never shows as selected.
 */
@Composable
fun MainViewBar(
    selectedTab: MainViewTab,
    onTabSelected: (MainViewTab) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .testTag("main_view_bar")
            .background(MainViewBarContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MainViewBarBorder)
        )
        Row(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
                .height(IntrinsicSize.Min)
                .fillMaxWidth()
                .selectableGroup()
        ) {
            // Explicit list: the bar's order is not DisplayMode.entries order.
            MainViewTabItem(
                tab = MainViewTab.FOLDERS,
                icon = Icons.Filled.Folder,
                label = R.string.folders_title,
                tag = "main_view_tab_folders",
                selected = selectedTab == MainViewTab.FOLDERS,
                onTabSelected = onTabSelected
            )
            MainViewTabItem(
                tab = MainViewTab.ALBUMS,
                icon = Icons.Filled.PhotoLibrary,
                label = R.string.albums_title,
                tag = "main_view_tab_albums",
                selected = selectedTab == MainViewTab.ALBUMS,
                onTabSelected = onTabSelected
            )
            MainViewTabItem(
                tab = MainViewTab.TIMELINE,
                icon = Icons.Filled.Schedule,
                label = R.string.timeline_title,
                tag = "main_view_tab_timeline",
                selected = selectedTab == MainViewTab.TIMELINE,
                onTabSelected = onTabSelected
            )
            MainViewBarItem(
                icon = Icons.Filled.Settings,
                label = R.string.settings_title,
                tag = "main_view_settings_button",
                selected = false,
                actionModifier = Modifier.clickable(role = Role.Button, onClick = onSettingsClick)
            )
        }
    }
}

@Composable
private fun RowScope.MainViewTabItem(
    tab: MainViewTab,
    icon: ImageVector,
    @StringRes label: Int,
    tag: String,
    selected: Boolean,
    onTabSelected: (MainViewTab) -> Unit
) {
    MainViewBarItem(
        icon = icon,
        label = label,
        tag = tag,
        selected = selected,
        actionModifier = Modifier.selectable(
            selected = selected,
            role = Role.Tab,
            onClick = { onTabSelected(tab) }
        )
    )
}

@Composable
private fun RowScope.MainViewBarItem(
    icon: ImageVector,
    @StringRes label: Int,
    tag: String,
    selected: Boolean,
    actionModifier: Modifier
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 56.dp)
            .testTag(tag)
            .then(actionModifier)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) MainViewBarIndicator else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) BrandBlue else MainViewBarUnselected
            )
        }
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) BrandBlueLight else MainViewBarUnselected,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
