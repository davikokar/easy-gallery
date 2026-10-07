package com.davide.seddio.easygallery.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.ui.theme.AppBackground

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyGridItemScope.AlbumGridItem(
    albumId: Long,
    name: String,
    itemCount: Int,
    hiddenMemberCount: Int,
    coverUri: Uri?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    AlbumGridItemContent(
        testTag = "album_tile_$albumId",
        name = name,
        itemCount = itemCount,
        hiddenMemberCount = hiddenMemberCount,
        coverUri = coverUri,
        isFavourite = false,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun LazyGridItemScope.FavouritesAlbumGridItem(
    itemCount: Int,
    coverUri: Uri?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    AlbumGridItemContent(
        testTag = "album_tile_favourites",
        name = stringResource(R.string.favourites_title),
        itemCount = itemCount,
        hiddenMemberCount = 0,
        coverUri = coverUri,
        isFavourite = true,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = null
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.AlbumListItem(
    albumId: Long,
    name: String,
    itemCount: Int,
    hiddenMemberCount: Int,
    coverUri: Uri?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    AlbumListItemContent(
        testTag = "album_tile_$albumId",
        name = name,
        itemCount = itemCount,
        hiddenMemberCount = hiddenMemberCount,
        coverUri = coverUri,
        isFavourite = false,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun LazyItemScope.FavouritesAlbumListItem(
    itemCount: Int,
    coverUri: Uri?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    AlbumListItemContent(
        testTag = "album_tile_favourites",
        name = stringResource(R.string.favourites_title),
        itemCount = itemCount,
        hiddenMemberCount = 0,
        coverUri = coverUri,
        isFavourite = true,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = null
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyGridItemScope.AlbumGridItemContent(
    testTag: String,
    name: String,
    itemCount: Int,
    hiddenMemberCount: Int,
    coverUri: Uri?,
    isFavourite: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val interactionModifier = if (onLongClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .animateItem()
            .testTag(testTag)
            .semantics { contentDescription = name }
            .then(interactionModifier),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = AppBackground)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlbumCover(coverUri = coverUri)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 200f
                        )
                    )
            )

            if (isFavourite) {
                FavouriteBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                SelectionCheckmark(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = name,
                    color = Color.White,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = albumItemCount(itemCount),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hiddenMemberCount > 0) {
                    Text(
                        text = stringResource(R.string.album_hidden_items_notice),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("album_hidden_items_notice")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.AlbumListItemContent(
    testTag: String,
    name: String,
    itemCount: Int,
    hiddenMemberCount: Int,
    coverUri: Uri?,
    isFavourite: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val interactionModifier = if (onLongClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateItem()
            .testTag(testTag)
            .semantics { contentDescription = name }
            .then(interactionModifier),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.White.copy(alpha = 0.05f)
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AlbumCover(coverUri = coverUri)

                if (isFavourite) {
                    FavouriteBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    SelectionCheckmark(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = albumItemCount(itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hiddenMemberCount > 0) {
                    Text(
                        text = stringResource(R.string.album_hidden_items_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("album_hidden_items_notice")
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCover(
    coverUri: Uri?,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (coverUri == null) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(36.dp)
            )
        } else {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun FavouriteBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier
            .size(24.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .padding(3.dp)
    )
}

@Composable
private fun SelectionCheckmark(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = stringResource(R.string.cd_selected),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(24.dp)
            .testTag("selected_checkmark")
            .background(Color.White, CircleShape)
    )
}

@Composable
private fun albumItemCount(itemCount: Int): String = pluralStringResource(
    id = R.plurals.album_item_count,
    count = itemCount,
    itemCount
)