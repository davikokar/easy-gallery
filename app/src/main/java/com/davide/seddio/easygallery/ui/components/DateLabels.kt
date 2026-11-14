package com.davide.seddio.easygallery.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.davide.seddio.easygallery.R
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// The one formatter for years and months in the album pickers and in anything that restates a date rule.

fun yearText(year: Int, locale: Locale): String = String.format(locale, "%d", year)

fun monthName(month: Int, locale: Locale): String {
    return Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) }
}

@Composable
fun monthYearText(year: Int, month: Int, locale: Locale): String =
    stringResource(R.string.automatic_album_month_year, monthName(month, locale), year)
