package com.davide.seddio.easygallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue
import java.text.NumberFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private const val TAB_PLACE = 0
private const val TAB_DATE = 1

/**
 * The automatic branch of album creation: name the album, describe it by place and date, and see
 * how many items that matches before it exists. The draft lives in `AlbumsViewModel`'s picker state;
 * nothing is written until Create (ADR-0016 Amendment 2).
 */
@Composable
fun NewAutomaticAlbumScreen(
    albumsViewModel: AlbumsViewModel,
    mediaIndexingViewModel: MediaIndexingViewModel,
    onFinished: () -> Unit
) {
    val picker = albumsViewModel.automaticAlbumPicker
    val ui by picker.uiState.collectAsState()
    val albums by albumsViewModel.albums.collectAsState()
    val indexingState by mediaIndexingViewModel.uiState.collectAsState()

    // Typed text is held here as well: a field fed only by a flow that hops threads drops keystrokes.
    var typedName by rememberSaveable { mutableStateOf<String?>(null) }
    val name = typedName ?: ui.name

    val isNameDuplicate = remember(albums, name) {
        albums.any { it.name.trim().equals(name.trim(), ignoreCase = true) }
    }

    // A restored process reopens this screen without open(), and a recreated Activity keeps the
    // draft but not the locale its lists were resolved in.
    LaunchedEffect(Unit) {
        typedName?.let(picker::setName)
        if (typedName == null && !picker.hasDraftName) albumsViewModel.openAutomaticAlbumPicker()
        picker.refresh()
        picker.retryLoadingNames()
    }

    NewAutomaticAlbumContent(
        ui = ui.copy(name = name),
        indexBand = automaticAlbumIndexBand(indexingState),
        isNameDuplicate = isNameDuplicate,
        onNameChange = {
            typedName = it
            picker.setName(it)
        },
        onSelectRegion = picker::selectRegion,
        onSelectTime = picker::selectTime,
        onBrowseCountry = picker::browseCountry,
        onBrowseRegion = picker::browseRegion,
        onBrowseYear = picker::browseYear,
        onRetryLoadingNames = picker::retryLoadingNames,
        onIndexNow = mediaIndexingViewModel::indexNow,
        onCreate = {
            ui.rule?.let { rule ->
                if (albumsViewModel.createAutomaticAlbum(name, rule)) {
                    picker.close()
                    onFinished()
                }
            }
        },
        onCancel = {
            picker.close()
            onFinished()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAutomaticAlbumContent(
    ui: AutomaticAlbumPickerUi,
    indexBand: AutomaticAlbumIndexBand,
    isNameDuplicate: Boolean,
    onNameChange: (String) -> Unit,
    onSelectRegion: (AlbumRuleClause.Region?) -> Unit,
    onSelectTime: (TimeSelection?) -> Unit,
    onBrowseCountry: (String?) -> Unit,
    onBrowseRegion: (String?) -> Unit,
    onBrowseYear: (Int?) -> Unit,
    onRetryLoadingNames: () -> Unit,
    onIndexNow: () -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    var selectedTab by rememberSaveable { mutableStateOf(TAB_PLACE) }
    // One flag for both pickers, above every drill level's list state, so it survives tab and level changes.
    var statusAreaExpanded by rememberSaveable { mutableStateOf(true) }

    val selection = ui.selection
    val isReady = ui.status == AutomaticAlbumPickerStatus.READY
    val placeDrilled = selection.browsedCountry != null
    val dateDrilled = selection.browsedYear != null
    // The top-bar icon follows the selected tab; X always closes. System Back also reaches the other tab's drill.
    val canStepOut = isReady && if (selectedTab == TAB_PLACE) placeDrilled else dateDrilled
    val canBackStepOut = isReady && (placeDrilled || dateDrilled)
    // The selected tab's drill goes first; the other tab's is shown rather than discarded by closing.
    val stepOut: () -> Unit = {
        val tab = when {
            selectedTab == TAB_PLACE && placeDrilled -> TAB_PLACE
            selectedTab == TAB_DATE && dateDrilled -> TAB_DATE
            placeDrilled -> TAB_PLACE
            else -> TAB_DATE
        }
        selectedTab = tab
        if (tab == TAB_PLACE) {
            if (selection.browsedAdmin1 != null) onBrowseRegion(null) else onBrowseCountry(null)
        } else {
            onBrowseYear(null)
        }
    }
    BackHandler {
        if (canBackStepOut) stepOut() else onCancel()
    }

    val nameError = when {
        ui.name.isBlank() -> stringResource(R.string.album_name_blank)
        isNameDuplicate -> stringResource(R.string.album_name_duplicate)
        else -> null
    }
    val canCreate = ui.canCreate && nameError == null
    val createLabel = stringResource(R.string.action_create)
    val closeLabel = stringResource(R.string.action_close)
    val backLabel = stringResource(R.string.cd_back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = ui.name,
                        onValueChange = onNameChange,
                        singleLine = true,
                        isError = nameError != null,
                        label = { Text(text = stringResource(R.string.album_name_hint), color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_automatic_album_name_field"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.White,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (canStepOut) stepOut() else onCancel() },
                        modifier = Modifier
                            .testTag(
                                if (canStepOut) "new_automatic_album_back_button" else "new_automatic_album_close_button"
                            )
                            .semantics { contentDescription = if (canStepOut) backLabel else closeLabel }
                    ) {
                        Icon(
                            imageVector = if (canStepOut) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBlue,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier
                        .testTag("new_automatic_album_create_button")
                        .semantics { contentDescription = createLabel }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // Material gives a FAB no disabled look, and a disabled one must not offer a click.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("new_automatic_album_create_button")
                        .semantics {
                            contentDescription = createLabel
                            role = Role.Button
                            disabled()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        containerColor = AppBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppBackground)
        ) {
            StatusLine(ui = ui, nameError = nameError)
            ChoiceChips(
                ui = ui,
                locale = locale,
                onClearPlace = { onSelectRegion(null) },
                onClearDate = { onSelectTime(null) }
            )

            when (ui.status) {
                AutomaticAlbumPickerStatus.LOADING_NAMES -> ProgressState(
                    message = stringResource(R.string.automatic_album_loading),
                    testTag = "new_automatic_album_loading",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                AutomaticAlbumPickerStatus.WAITING_FOR_MEDIA -> ProgressState(
                    message = stringResource(R.string.automatic_album_waiting_for_media),
                    testTag = "new_automatic_album_waiting",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                AutomaticAlbumPickerStatus.NAMES_FAILED -> FailedState(
                    onRetry = onRetryLoadingNames,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                AutomaticAlbumPickerStatus.READY -> {
                    PickerTabs(selectedTab = selectedTab, onSelect = { selectedTab = it })
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (selectedTab == TAB_PLACE) {
                            PlaceTab(
                                ui = ui,
                                indexBand = indexBand,
                                statusAreaExpanded = statusAreaExpanded,
                                onToggleStatusArea = { statusAreaExpanded = !statusAreaExpanded },
                                onIndexNow = onIndexNow,
                                locale = locale,
                                onSelectRegion = onSelectRegion,
                                onBrowseCountry = onBrowseCountry,
                                onBrowseRegion = onBrowseRegion
                            )
                        } else {
                            DateTab(
                                ui = ui,
                                indexBand = indexBand,
                                statusAreaExpanded = statusAreaExpanded,
                                onToggleStatusArea = { statusAreaExpanded = !statusAreaExpanded },
                                onIndexNow = onIndexNow,
                                locale = locale,
                                onSelectTime = onSelectTime,
                                onBrowseYear = onBrowseYear
                            )
                        }
                    }
                }
            }
        }
    }
}

// One line, in priority order: a name problem, the prompt to choose, or the match count.
@Composable
private fun StatusLine(ui: AutomaticAlbumPickerUi, nameError: String?) {
    val lineModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)

    if (nameError != null) {
        Text(
            text = nameError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = lineModifier.testTag("new_automatic_album_hint")
        )
    } else if (ui.status == AutomaticAlbumPickerStatus.READY) {
        if (ui.rule == null) {
            Text(
                text = stringResource(R.string.automatic_album_hint_choose),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = lineModifier.testTag("new_automatic_album_hint")
            )
        } else {
            val count = ui.content.matchCount
            Text(
                text = pluralStringResource(R.plurals.automatic_album_match_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = lineModifier
                    .testTag("new_automatic_album_match_count")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    ui: AutomaticAlbumPickerUi,
    locale: Locale,
    onClearPlace: () -> Unit,
    onClearDate: () -> Unit
) {
    val region = ui.selection.region
    val time = ui.selection.time
    if (region == null && time == null) return

    val placeLabel = region?.let { placeChoiceLabel(it, ui.content.countries) }
    val timeLabel = when (time) {
        null -> null
        is TimeSelection.Year -> yearText(time.year, locale)
        is TimeSelection.Month -> stringResource(
            R.string.automatic_album_month_year,
            monthName(time.month, locale),
            time.year
        )
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (placeLabel != null) {
            ChoiceChip(
                icon = Icons.Default.Place,
                label = placeLabel,
                clearDescription = stringResource(R.string.automatic_album_clear_place, placeLabel),
                testTag = "new_automatic_album_place_chip",
                onClear = onClearPlace
            )
        }
        if (timeLabel != null) {
            ChoiceChip(
                icon = Icons.Default.CalendarMonth,
                label = timeLabel,
                clearDescription = stringResource(R.string.automatic_album_clear_date, timeLabel),
                testTag = "new_automatic_album_date_chip",
                onClear = onClearDate
            )
        }
    }
}

// The most specific name the clause carries; a bare country falls back to its listed display name.
private fun placeChoiceLabel(region: AlbumRuleClause.Region, countries: List<CountryEntry>): String {
    return region.place
        ?: region.admin1Name
        ?: countries.firstOrNull { it.countryCode == region.country }?.displayName
        ?: region.country
}

@Composable
private fun ChoiceChip(
    icon: ImageVector,
    label: String,
    clearDescription: String,
    testTag: String,
    onClear: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics { contentDescription = clearDescription }
            .clickable(role = Role.Button, onClick = onClear),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PickerTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .selectableGroup()
    ) {
        PickerTab(
            label = stringResource(R.string.automatic_album_section_place),
            selected = selectedTab == TAB_PLACE,
            testTag = "new_automatic_album_place_tab",
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TAB_PLACE) }
        )
        PickerTab(
            label = stringResource(R.string.automatic_album_section_date),
            selected = selectedTab == TAB_DATE,
            testTag = "new_automatic_album_date_tab",
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TAB_DATE) }
        )
    }
}

@Composable
private fun PickerTab(
    label: String,
    selected: Boolean,
    testTag: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

@Composable
private fun PlaceTab(
    ui: AutomaticAlbumPickerUi,
    indexBand: AutomaticAlbumIndexBand,
    statusAreaExpanded: Boolean,
    onToggleStatusArea: () -> Unit,
    onIndexNow: () -> Unit,
    locale: Locale,
    onSelectRegion: (AlbumRuleClause.Region?) -> Unit,
    onBrowseCountry: (String?) -> Unit,
    onBrowseRegion: (String?) -> Unit
) {
    val browsedCountry = ui.selection.browsedCountry
    val browsedAdmin1 = ui.selection.browsedAdmin1
    val emptyText = stringResource(
        when {
            indexBand.emptyListMayStillFill() -> R.string.automatic_album_not_indexed_empty
            ui.selection.time != null -> R.string.automatic_album_places_empty_for_date
            else -> R.string.automatic_album_places_empty
        }
    )

    // A new list state per level, so a drill-down opens at its top rather than at the parent's offset.
    key(browsedCountry, browsedAdmin1) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("new_automatic_album_place_list"),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            if (placeStatusAreaHasContent(indexBand, ui.coverage, ui.locationAccessGranted)) {
                item(key = "status-area") {
                    StatusArea(
                        band = indexBand,
                        expanded = statusAreaExpanded,
                        onToggleExpanded = onToggleStatusArea,
                        onIndexNow = onIndexNow
                    ) {
                        LocationCoverageNotice(
                            coverage = ui.coverage,
                            locationAccessGranted = ui.locationAccessGranted,
                            testTag = "new_automatic_album_location_coverage",
                            announce = false
                        )
                    }
                }
            }

            if (browsedCountry == null) {
                val countries = ui.content.countries
                if (countries.isEmpty()) {
                    item(key = "empty") {
                        EmptyMessage(text = emptyText, testTag = "new_automatic_album_place_empty")
                    }
                } else {
                    items(countries, key = { "country:${it.countryCode}" }) { country ->
                        DrillRow(
                            label = country.displayName,
                            count = country.count,
                            locale = locale,
                            testTag = "new_automatic_album_country_${country.countryCode}",
                            onClick = { onBrowseCountry(country.countryCode) }
                        )
                    }
                }
            } else {
                val breakdown = ui.content.browsedCountry
                val countryName = ui.content.countries.firstOrNull { it.countryCode == browsedCountry }?.displayName
                    ?: browsedCountry
                val selected = ui.selection.region

                if (browsedAdmin1 == null) {
                    item(key = "back") {
                        BackRow(
                            label = countryName,
                            testTag = "new_automatic_album_place_back",
                            onBack = { onBrowseCountry(null) }
                        )
                    }
                    if (breakdown == null || breakdown.allCount == 0) {
                        item(key = "empty") {
                            EmptyMessage(text = emptyText, testTag = "new_automatic_album_place_empty")
                        }
                    } else {
                        val countryClause = AlbumRuleClause.Region(country = browsedCountry)
                        item(key = "all") {
                            ChoiceRow(
                                label = stringResource(R.string.automatic_album_all_of, countryName),
                                count = breakdown.allCount,
                                locale = locale,
                                selected = selected == countryClause,
                                testTag = "new_automatic_album_all_of_country",
                                onClick = { onSelectRegion(countryClause) }
                            )
                        }
                        items(breakdown.regions, key = { "region:${it.admin1Code}" }) { region ->
                            DrillRow(
                                label = region.admin1Name,
                                count = region.count,
                                locale = locale,
                                testTag = "new_automatic_album_region_${region.admin1Code}",
                                onClick = { onBrowseRegion(region.admin1Code) }
                            )
                        }
                        val unregioned = breakdown.placesWithoutRegion.places
                        if (unregioned.isNotEmpty()) {
                            // Only the places are selectable: a clause has no form for "no region" as a whole.
                            item(key = "no-region-header") {
                                GroupHeader(text = stringResource(R.string.automatic_album_places_without_region))
                            }
                            items(unregioned, key = { "place:${it.placeName}" }) { place ->
                                val placeClause = AlbumRuleClause.Region(country = browsedCountry, place = place.placeName)
                                ChoiceRow(
                                    label = place.placeName,
                                    count = place.count,
                                    locale = locale,
                                    selected = selected == placeClause,
                                    testTag = "new_automatic_album_place_${place.placeName}",
                                    onClick = { onSelectRegion(placeClause) }
                                )
                            }
                        }
                    }
                } else {
                    val regionFacet = breakdown?.regions?.firstOrNull { it.admin1Code == browsedAdmin1 }
                    item(key = "back") {
                        BackRow(
                            label = regionFacet?.admin1Name ?: browsedAdmin1,
                            testTag = "new_automatic_album_region_back",
                            onBack = { onBrowseRegion(null) }
                        )
                    }
                    if (regionFacet == null) {
                        item(key = "empty") {
                            EmptyMessage(text = emptyText, testTag = "new_automatic_album_place_empty")
                        }
                    } else {
                        val regionClause = AlbumRuleClause.Region(
                            country = browsedCountry,
                            admin1 = browsedAdmin1,
                            admin1Name = regionFacet.admin1Name
                        )
                        item(key = "all") {
                            ChoiceRow(
                                label = stringResource(R.string.automatic_album_all_of, regionFacet.admin1Name),
                                count = regionFacet.count,
                                locale = locale,
                                selected = selected == regionClause,
                                testTag = "new_automatic_album_all_of_region",
                                onClick = { onSelectRegion(regionClause) }
                            )
                        }
                        items(ui.content.browsedRegionPlaces, key = { "place:${it.placeName}" }) { place ->
                            val placeClause = regionClause.copy(place = place.placeName)
                            ChoiceRow(
                                label = place.placeName,
                                count = place.count,
                                locale = locale,
                                selected = selected == placeClause,
                                testTag = "new_automatic_album_place_${place.placeName}",
                                onClick = { onSelectRegion(placeClause) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTab(
    ui: AutomaticAlbumPickerUi,
    indexBand: AutomaticAlbumIndexBand,
    statusAreaExpanded: Boolean,
    onToggleStatusArea: () -> Unit,
    onIndexNow: () -> Unit,
    locale: Locale,
    onSelectTime: (TimeSelection?) -> Unit,
    onBrowseYear: (Int?) -> Unit
) {
    val browsedYear = ui.selection.browsedYear
    val emptyText = stringResource(
        when {
            indexBand.emptyListMayStillFill() -> R.string.automatic_album_not_indexed_empty
            ui.selection.region != null -> R.string.automatic_album_dates_empty_for_place
            else -> R.string.automatic_album_dates_empty
        }
    )

    key(browsedYear) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("new_automatic_album_date_list"),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            if (dateStatusAreaHasContent(indexBand, ui.coverage)) {
                item(key = "status-area") {
                    StatusArea(
                        band = indexBand,
                        expanded = statusAreaExpanded,
                        onToggleExpanded = onToggleStatusArea,
                        onIndexNow = onIndexNow
                    ) {
                        DateCoverageNotice(
                            coverage = ui.coverage,
                            testTag = "new_automatic_album_date_coverage",
                            announce = false
                        )
                    }
                }
            }

            if (browsedYear == null) {
                val years = ui.content.years
                if (years.isEmpty()) {
                    item(key = "empty") {
                        EmptyMessage(text = emptyText, testTag = "new_automatic_album_date_empty")
                    }
                } else {
                    items(years, key = { "year:${it.year}" }) { year ->
                        DrillRow(
                            label = yearText(year.year, locale),
                            count = year.count,
                            locale = locale,
                            testTag = "new_automatic_album_year_${year.year}",
                            onClick = { onBrowseYear(year.year) }
                        )
                    }
                }
            } else {
                val yearLabel = yearText(browsedYear, locale)
                val yearFacet = ui.content.years.firstOrNull { it.year == browsedYear }
                val months = ui.content.browsedYearMonths
                val selected = ui.selection.time

                item(key = "back") {
                    BackRow(
                        label = yearLabel,
                        testTag = "new_automatic_album_date_back",
                        onBack = { onBrowseYear(null) }
                    )
                }
                if (yearFacet == null && months.isEmpty()) {
                    item(key = "empty") {
                        EmptyMessage(text = emptyText, testTag = "new_automatic_album_date_empty")
                    }
                } else {
                    item(key = "all") {
                        ChoiceRow(
                            label = stringResource(R.string.automatic_album_all_of, yearLabel),
                            count = yearFacet?.count ?: months.sumOf { it.count },
                            locale = locale,
                            selected = selected == TimeSelection.Year(browsedYear),
                            testTag = "new_automatic_album_all_of_year",
                            onClick = { onSelectTime(TimeSelection.Year(browsedYear)) }
                        )
                    }
                    items(months, key = { "month:${it.month}" }) { month ->
                        ChoiceRow(
                            label = monthName(month.month, locale),
                            count = month.count,
                            locale = locale,
                            selected = selected == TimeSelection.Month(browsedYear, month.month),
                            testTag = "new_automatic_album_month_${month.month}",
                            onClick = { onSelectTime(TimeSelection.Month(browsedYear, month.month)) }
                        )
                    }
                }
            }
        }
    }
}

// The header is always there; collapsed, the band and the coverage notice are not composed at all.
@Composable
private fun StatusArea(
    band: AutomaticAlbumIndexBand,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onIndexNow: () -> Unit,
    coverageNotice: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("new_automatic_album_status_area")
    ) {
        StatusAreaHeader(expanded = expanded, onToggle = onToggleExpanded)
        if (expanded) {
            IndexBand(band = band, onIndexNow = onIndexNow)
            coverageNotice()
        }
    }
}

@Composable
private fun StatusAreaHeader(expanded: Boolean, onToggle: () -> Unit) {
    val actionLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand)
    val stateLabel = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("new_automatic_album_status_toggle")
            .semantics { stateDescription = stateLabel }
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onToggle)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.automatic_album_status_area_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Shows the plain count of media not yet indexed; progress is measured only in Settings.
@Composable
private fun IndexBand(band: AutomaticAlbumIndexBand, onIndexNow: () -> Unit) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .testTag("new_automatic_album_index_now")

    when (band) {
        AutomaticAlbumIndexBand.Hidden -> Unit

        // No click action: Index now would replace the running pass.
        AutomaticAlbumIndexBand.Running -> IndexBandFrame {
            Row(
                modifier = rowModifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                }
                IndexBandText(text = stringResource(R.string.automatic_album_index_running))
            }
        }

        is AutomaticAlbumIndexBand.NotIndexed -> IndexBandFrame {
            Row(
                modifier = rowModifier.clickable(
                    onClickLabel = stringResource(R.string.settings_index_now),
                    role = Role.Button,
                    onClick = onIndexNow
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IndexBandText(
                    text = pluralStringResource(R.plurals.automatic_album_not_indexed_count, band.count, band.count)
                )
            }
        }
    }
}

@Composable
private fun IndexBandFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp)
            .testTag("new_automatic_album_index_band")
    ) {
        content()
    }
}

@Composable
private fun RowScope.IndexBandText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

// A row that opens a list: country, region or year.
@Composable
private fun DrillRow(
    label: String,
    count: Int,
    locale: Locale,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = countText(count, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

// A row that picks the choice for its section: "All of ...", a place, or a month.
@Composable
private fun ChoiceRow(
    label: String,
    count: Int,
    locale: Locale,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = countText(count, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(modifier = Modifier.size(24.dp)) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BackRow(label: String, testTag: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = stringResource(R.string.cd_back), role = Role.Button, onClick = onBack)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun EmptyMessage(text: String, testTag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag(testTag)
    )
}

@Composable
private fun ProgressState(message: String, testTag: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .testTag(testTag)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FailedState(onRetry: () -> Unit, modifier: Modifier) {
    val retryLabel = stringResource(R.string.action_retry)

    Column(
        modifier = modifier
            .testTag("new_automatic_album_names_failed")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.automatic_album_names_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .testTag("new_automatic_album_retry")
                .semantics { contentDescription = retryLabel }
        ) {
            Text(retryLabel)
        }
    }
}

// Counts are for people, so they group thousands in the app's locale.
private fun countText(count: Int, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(count)

private fun yearText(year: Int, locale: Locale): String = String.format(locale, "%d", year)

private fun monthName(month: Int, locale: Locale): String {
    return Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) }
}
