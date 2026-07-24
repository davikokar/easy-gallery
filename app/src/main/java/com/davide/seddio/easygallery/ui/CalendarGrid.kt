package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davide.seddio.easygallery.data.Photo
import com.davide.seddio.easygallery.ui.components.PhotoItem

@Composable
fun CalendarGrid(
    groupedPhotos: Map<String, List<Photo>>,
    columns: Int,
    showInfo: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        groupedPhotos.forEach { (date, photos) ->
            item(span = { GridItemSpan(columns) }) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .fillMaxWidth()
                )
            }
            items(photos) { photo ->
                PhotoItem(photo = photo, showInfo = showInfo)
            }
        }
    }
}
