package com.davide.seddio.easygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.FaceGroupSummary
import com.davide.seddio.easygallery.ui.components.PersonFaceImage
import java.util.Locale

private val PERSON_AVATAR_SIZE = 80.dp

// The People tab's body. Its states are its own: the screen-wide status governs the lists and the count line, not this.
@Composable
internal fun PeopleTab(
    people: PeoplePickerUi,
    indexBand: AutomaticAlbumIndexBand,
    statusAreaExpanded: Boolean,
    onToggleStatusArea: () -> Unit,
    onIndexNow: () -> Unit,
    locale: Locale,
    onTogglePerson: (FaceGroupSummary) -> Unit,
    onRetry: () -> Unit
) {
    when (people.status) {
        // NOT_LOADED lasts only until the reveal that starts the load, so it reads as loading.
        PeopleListStatus.NOT_LOADED, PeopleListStatus.LOADING -> ProgressState(
            message = stringResource(R.string.automatic_album_people_loading),
            testTag = "new_automatic_album_people_loading",
            modifier = Modifier.fillMaxSize()
        )

        PeopleListStatus.READY -> PeopleGrid(
            people = people,
            indexBand = indexBand,
            statusAreaExpanded = statusAreaExpanded,
            onToggleStatusArea = onToggleStatusArea,
            onIndexNow = onIndexNow,
            locale = locale,
            onTogglePerson = onTogglePerson
        )

        PeopleListStatus.EMPTY -> PeopleEmpty(
            indexBand = indexBand,
            statusAreaExpanded = statusAreaExpanded,
            onToggleStatusArea = onToggleStatusArea,
            onIndexNow = onIndexNow
        )

        PeopleListStatus.FAILED -> FailedState(
            message = stringResource(R.string.automatic_album_people_failed),
            testTag = "new_automatic_album_people_failed",
            retryTag = "new_automatic_album_people_retry",
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PeopleGrid(
    people: PeoplePickerUi,
    indexBand: AutomaticAlbumIndexBand,
    statusAreaExpanded: Boolean,
    onToggleStatusArea: () -> Unit,
    onIndexNow: () -> Unit,
    locale: Locale,
    onTogglePerson: (FaceGroupSummary) -> Unit
) {
    // The 96 dp at the bottom is the Create button's clearance: it lives here so the last row can scroll clear of it.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("new_automatic_album_people_grid"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (indexBand != AutomaticAlbumIndexBand.Hidden) {
            item(key = "status-area", span = { GridItemSpan(maxLineSpan) }) {
                // The area sits at the screen's edge on the other tabs, so it undoes the grid's side padding.
                StatusArea(
                    band = indexBand,
                    expanded = statusAreaExpanded,
                    runningMessage = R.string.automatic_album_people_index_running,
                    onToggleExpanded = onToggleStatusArea,
                    onIndexNow = onIndexNow,
                    modifier = Modifier.bleedHorizontally(16.dp)
                )
            }
        }
        item(key = "people-hint", span = { GridItemSpan(maxLineSpan) }) {
            PeopleHint(atCap = people.atCap)
        }
        itemsIndexed(people.groups, key = { _, person -> person.groupId }) { index, person ->
            PersonTile(
                index = index,
                total = people.groups.size,
                person = person,
                selected = people.isChosen(person),
                disabled = people.isDisabled(person),
                locale = locale,
                onToggle = { onTogglePerson(person) }
            )
        }
    }
}

// "Pick up to N", or at N why nothing else can be picked; the limit is the state's constant, never typed into a string.
@Composable
private fun PeopleHint(atCap: Boolean) {
    Text(
        text = stringResource(
            if (atCap) R.string.automatic_album_people_limit else R.string.automatic_album_people_hint,
            MAX_PEOPLE_PER_ALBUM
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("new_automatic_album_people_hint")
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

// One person: a face and how many photos they are in. The whole cell is the one checkbox target.
@Composable
private fun PersonTile(
    index: Int,
    total: Int,
    person: FaceGroupSummary,
    selected: Boolean,
    disabled: Boolean,
    locale: Locale,
    onToggle: () -> Unit
) {
    val description = pluralStringResource(
        R.plurals.automatic_album_person_description,
        person.imageCount,
        index + 1,
        total,
        person.imageCount
    )
    // A disabled tile offers no click at all, as the disabled Create button does.
    val interaction = if (disabled) {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Checkbox
            toggleableState = ToggleableState.Off
            disabled()
        }
    } else {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
    }
    val ringColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (disabled) 0.38f else 1f)
            .testTag("new_automatic_album_person_$index")
            .then(interaction)
            .semantics { contentDescription = description }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(PERSON_AVATAR_SIZE)) {
            PersonFaceImage(
                mediaUri = person.representative.identity.mediaUri,
                box = person.representative.box,
                modifier = Modifier.fillMaxSize()
            )
            if (selected) {
                // A ring and a badge, and no scrim: a scrim would dim the very face being recognised.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, ringColor, CircleShape)
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ringColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                        .testTag("new_automatic_album_person_selected_$index")
                )
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = countText(person.imageCount, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Nobody to pick: the message, the Status area when it has something to say, else a way to start indexing.
@Composable
private fun PeopleEmpty(
    indexBand: AutomaticAlbumIndexBand,
    statusAreaExpanded: Boolean,
    onToggleStatusArea: () -> Unit,
    onIndexNow: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        if (indexBand != AutomaticAlbumIndexBand.Hidden) {
            item(key = "status-area") {
                StatusArea(
                    band = indexBand,
                    expanded = statusAreaExpanded,
                    runningMessage = R.string.automatic_album_people_index_running,
                    onToggleExpanded = onToggleStatusArea,
                    onIndexNow = onIndexNow
                )
            }
        }
        item(key = "empty") {
            EmptyMessage(
                text = stringResource(R.string.automatic_album_people_empty),
                testTag = "new_automatic_album_people_empty"
            )
        }
        // With the band hidden nothing else offers indexing, and Index now runs the face pass too.
        if (indexBand == AutomaticAlbumIndexBand.Hidden) {
            item(key = "index-now") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onIndexNow,
                        modifier = Modifier.testTag("new_automatic_album_people_index_now")
                    ) {
                        Text(text = stringResource(R.string.settings_index_now), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// Widens a full-span item by [inset] on each side, for content that already carries its own side padding.
private fun Modifier.bleedHorizontally(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = (inset * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
