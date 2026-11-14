package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.AlbumRuleCondition
import com.davide.seddio.easygallery.data.AlbumRuleSummary
import com.davide.seddio.easygallery.data.DatePeriod
import com.davide.seddio.easygallery.data.PlaceScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The Album info dialog: what defines the open album, from a summary the caller built in the current locale. */
@Composable
fun AlbumInfoDialog(
    summary: AlbumRuleSummary,
    onDismiss: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_album_info)) },
        text = {
            // Scrolls so a large font size cannot push the Close button off screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("album_info_content"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (summary) {
                    AlbumRuleSummary.Manual -> {
                        Column(
                            modifier = Modifier.testTag("album_info_manual"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AlbumInfoHeading(stringResource(R.string.album_type_manual_title))
                            Text(
                                text = stringResource(R.string.album_type_manual_description),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    AlbumRuleSummary.Unreadable -> {
                        Text(
                            text = stringResource(R.string.album_rule_unevaluatable_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("album_info_unreadable")
                        )
                    }

                    is AlbumRuleSummary.Automatic -> {
                        AlbumInfoHeading(
                            text = stringResource(R.string.album_type_automatic_title),
                            modifier = Modifier.testTag("album_info_automatic_heading")
                        )
                        val places = summary.conditions.filterIsInstance<AlbumRuleCondition.Place>()
                        val dates = summary.conditions.filterIsInstance<AlbumRuleCondition.Dates>()
                        if (places.isNotEmpty()) {
                            AlbumInfoEntry(
                                label = stringResource(R.string.automatic_album_section_place),
                                values = places.map { placeText(it) },
                                testTag = "album_info_place"
                            )
                        }
                        if (dates.isNotEmpty()) {
                            AlbumInfoEntry(
                                label = stringResource(R.string.automatic_album_section_date),
                                values = dates.map { dateText(it.period, locale) },
                                testTag = "album_info_date"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("album_info_close_button")
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun AlbumInfoHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.semantics { heading() }
    )
}

// The label sits above its values so no per-locale "label: value" string is needed.
@Composable
private fun AlbumInfoEntry(label: String, values: List<String>, testTag: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        values.forEach { value ->
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// Names run most specific first, so a place reads "Milan, Lombardy, Italy" and a whole region "All of Lombardy, Italy".
@Composable
private fun placeText(place: AlbumRuleCondition.Place): String {
    val path = place.names.joinToString(", ")
    return if (place.scope == PlaceScope.PLACE) {
        path
    } else {
        stringResource(R.string.automatic_album_all_of, path)
    }
}

@Composable
private fun dateText(period: DatePeriod, locale: Locale): String = when (period) {
    is DatePeriod.Year -> yearText(period.year, locale)
    is DatePeriod.Month -> monthYearText(period.year, period.month, locale)
    is DatePeriod.Day -> formatAlbumInfoDate(period.date, locale)
    is DatePeriod.Between -> stringResource(
        R.string.album_info_date_range,
        formatAlbumInfoDate(period.from, locale),
        formatAlbumInfoDate(period.to, locale)
    )
    is DatePeriod.Since -> stringResource(R.string.album_info_date_from, formatAlbumInfoDate(period.from, locale))
    is DatePeriod.Until -> stringResource(R.string.album_info_date_until, formatAlbumInfoDate(period.to, locale))
}

private fun formatAlbumInfoDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
