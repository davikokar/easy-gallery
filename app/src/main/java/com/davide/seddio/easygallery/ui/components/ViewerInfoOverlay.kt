package com.davide.seddio.easygallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaCoordinates
import com.davide.seddio.easygallery.data.ResolvedDateTaken
import com.davide.seddio.easygallery.data.formatCoordinatesForDisplay
import com.davide.seddio.easygallery.ui.theme.BrandBlueLight
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Contrast of the white text and BrandBlueLight link is at least 4.5:1 even over a pure white image.
internal const val VIEWER_INFO_SCRIM_ALPHA = 0.75f

/** Same style as the other date displays: medium date, short time, in the given locale. */
internal fun formatMediaDateTime(
    epochMillis: Long,
    locale: Locale,
    timeZone: TimeZone = TimeZone.getDefault()
): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    .apply { this.timeZone = timeZone }
    .format(Date(epochMillis))

/** A date that is not a positive instant is no date: the row is hidden rather than print 1970. */
internal fun displayableDateTaken(resolved: ResolvedDateTaken): ResolvedDateTaken? =
    resolved.takeIf { it.epochMillis > 0L }

/**
 * The media with the info panel drawn over its bottom edge. The panel takes no layout space, so
 * showing or hiding it never moves or resizes [content].
 */
@Composable
fun ViewerMediaArea(
    showInfo: Boolean,
    infoContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.testTag("viewer_media_area")) {
        content()
        // Composed only while shown, so an info source such as the location read runs only then.
        AnimatedVisibility(
            visible = showInfo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            infoContent()
        }
    }
}

/** Name, path, date taken (or date added when no capture date is known) and GPS on a dark scrim, readable over any image. */
@Composable
fun ViewerInfoPanel(
    name: String,
    path: String,
    dateTaken: ResolvedDateTaken?,
    coordinates: MediaCoordinates?,
    onOpenLocation: (MediaCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("viewer_info_overlay")
            .background(Color.Black.copy(alpha = VIEWER_INFO_SCRIM_ALPHA), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.properties_name, name),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("viewer_info_name")
        )
        Text(
            text = stringResource(R.string.properties_path, path),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("viewer_info_path")
        )
        if (dateTaken != null) {
            val locale = LocalConfiguration.current.locales[0]
            val dateText = remember(dateTaken.epochMillis, locale) { formatMediaDateTime(dateTaken.epochMillis, locale) }
            Text(
                text = stringResource(
                    if (dateTaken.isCaptureDate) R.string.properties_date_taken else R.string.properties_date_added,
                    dateText
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(
                    if (dateTaken.isCaptureDate) "viewer_info_date_taken" else "viewer_info_date_added"
                )
            )
        }
        if (coordinates != null) {
            val openInMapsDescription = stringResource(R.string.cd_open_in_maps)
            Text(
                text = stringResource(R.string.properties_gps, formatCoordinatesForDisplay(coordinates)),
                color = BrandBlueLight,
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("viewer_info_gps")
                    .clickable { onOpenLocation(coordinates) }
                    .semantics { contentDescription = openInMapsDescription }
            )
        }
    }
}
