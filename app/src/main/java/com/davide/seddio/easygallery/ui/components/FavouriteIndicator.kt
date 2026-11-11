package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R

@Composable
fun FavouriteIndicator(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = stringResource(R.string.cd_favourite_indicator),
        // Brighter than pure red, which loses legibility at this size on the dark disc.
        tint = Color(0xFFFF5252),
        modifier = modifier
            .size(24.dp)
            .testTag("favourite_indicator")
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .padding(4.dp)
    )
}