package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun androidx.compose.foundation.lazy.LazyItemScope.MediaListItem(
    item: MediaItem,
    showInfo: Boolean,
    isSelected: Boolean = false,
    isFavourite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateItem()
            .testTag("media_tile_${item.uri}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(if (isSelected) Modifier.background(selectedRowColor()) else Modifier)
            .listRowDivider(listRowDividerColor())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                        .testTag("selected_checkmark")
                        .background(Color.White, CircleShape)
                )
            }

            if (isFavourite) {
                FavouriteIndicator(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )

            if (showInfo) {
                val dateStr = remember(item.dateAdded) {
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, Locale.getDefault()).format(Date(item.dateAdded * 1000))
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.folderPath,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Fill for a selected flat list row. The thumbnail scrim and checkmark remain the primary cue. */
@Composable
internal fun selectedRowColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

@Composable
internal fun listRowDividerColor(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

/** A 1dp full-width line along the bottom edge; apply before `padding` so it spans the row. */
internal fun Modifier.listRowDivider(color: Color): Modifier = drawBehind {
    val thickness = 1.dp.toPx()
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - thickness),
        size = Size(size.width, thickness)
    )
}
