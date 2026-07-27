package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.ui.components.MediaGridItem
import com.davide.seddio.easygallery.ui.components.MediaListItem
import com.davide.seddio.easygallery.ui.theme.BottomGrey

@Composable
fun CalendarGrid(
    viewModel: GalleryViewModel,
    groupedPhotos: Map<String, List<MediaItem>>,
    columns: Int,
    showInfo: Boolean
) {
    val pictureViewType by viewModel.pictureViewType.collectAsState()
    val selectedMediaItems by viewModel.selectedMediaItems.collectAsState()

    if (pictureViewType == ViewType.GRID) {
        var cumulativeScale by remember { mutableFloatStateOf(1f) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(BottomGrey)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                cumulativeScale *= zoom
                                if (cumulativeScale > 1.25f) {
                                    viewModel.decreaseColumns(forPictures = true)
                                    cumulativeScale = 1f
                                } else if (cumulativeScale < 0.75f) {
                                    viewModel.increaseColumns(forPictures = true)
                                    cumulativeScale = 1f
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        cumulativeScale = 1f
                    }
                }
        ) {
            groupedPhotos.forEach { (header, items) ->
                if (header.isNotEmpty()) {
                    item(span = { GridItemSpan(columns) }, key = header) {
                        DateHeader(header)
                    }
                }
                items(items, key = { it.uri.toString() }) { item ->
                    MediaGridItem(
                        item = item,
                        showInfo = showInfo,
                        isSelected = selectedMediaItems.contains(item.uri),
                        onClick = { viewModel.selectMedia(item) },
                        onLongClick = { viewModel.enterMediaSelectionMode(item) }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BottomGrey)
        ) {
            groupedPhotos.forEach { (header, items) ->
                if (header.isNotEmpty()) {
                    item(key = header) {
                        DateHeaderList(header)
                    }
                }
                items(items, key = { it.uri.toString() }) { item ->
                    MediaListItem(
                        item = item,
                        showInfo = showInfo,
                        isSelected = selectedMediaItems.contains(item.uri),
                        onClick = { viewModel.selectMedia(item) },
                        onLongClick = { viewModel.enterMediaSelectionMode(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyGridItemScope.DateHeader(date: String) {
    Text(
        text = date,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .animateItem()
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .fillMaxWidth()
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.DateHeaderList(date: String) {
    Text(
        text = date,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .animateItem()
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .fillMaxWidth()
    )
}
