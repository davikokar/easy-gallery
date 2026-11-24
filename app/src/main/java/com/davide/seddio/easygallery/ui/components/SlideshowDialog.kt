package com.davide.seddio.easygallery.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.MediaItem
import com.davide.seddio.easygallery.data.SLIDESHOW_MAX_INTERVAL_SECONDS
import com.davide.seddio.easygallery.data.SLIDESHOW_MIN_INTERVAL_SECONDS
import com.davide.seddio.easygallery.data.SlideshowOptions
import com.davide.seddio.easygallery.data.SlideshowOrder
import com.davide.seddio.easygallery.data.SlideshowOrientation
import com.davide.seddio.easygallery.data.SlideshowPlaylist
import com.davide.seddio.easygallery.data.SlideshowTransition
import kotlin.random.Random

private val SlideshowOptionsSaver = listSaver<SlideshowOptions, Int>(
    save = {
        listOf(
            it.intervalSeconds,
            it.transition.ordinal,
            if (it.includeVideos) 1 else 0,
            if (it.includeGifs) 1 else 0,
            it.order.ordinal,
            if (it.loop) 1 else 0,
            it.orientation.ordinal
        )
    },
    restore = {
        SlideshowOptions(
            intervalSeconds = it[0],
            transition = SlideshowTransition.entries[it[1]],
            includeVideos = it[2] == 1,
            includeGifs = it[3] == 1,
            order = SlideshowOrder.entries[it[4]],
            loop = it[5] == 1,
            orientation = SlideshowOrientation.entries[it[6]]
        )
    }
)

/**
 * Holds the draft and the collapsed/expanded state of [SlideshowDialog] across rotation and counts
 * what the draft would play over [baseOrder]. Compose it only while the dialog is open: leaving
 * composition discards the draft, which is how Cancel and dismissing discard it.
 */
@Composable
fun SlideshowDialogHost(
    savedOptions: SlideshowOptions,
    baseOrder: List<MediaItem>,
    onPlay: (SlideshowOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(stateSaver = SlideshowOptionsSaver) { mutableStateOf(savedOptions) }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    // Order does not change how many items play, so the count never pays for a shuffle.
    val count = remember(baseOrder, draft) {
        SlideshowPlaylist.build(baseOrder, draft.copy(order = SlideshowOrder.ASCENDING), Random.Default).size
    }

    SlideshowDialog(
        options = draft,
        count = count,
        optionsExpanded = optionsExpanded,
        onOptionsChange = { draft = it },
        onOptionsExpandedChange = { optionsExpanded = it },
        onPlay = { onPlay(draft) },
        onDismiss = onDismiss
    )
}

@Composable
fun SlideshowDialog(
    options: SlideshowOptions,
    count: Int,
    optionsExpanded: Boolean,
    onOptionsChange: (SlideshowOptions) -> Unit,
    onOptionsExpandedChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val optionsDescription = stringResource(R.string.slideshow_options)
    val canPlay = count > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("slideshow_dialog"),
        title = { Text(stringResource(R.string.slideshow_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (canPlay) {
                            pluralStringResource(R.plurals.slideshow_count, count, count)
                        } else {
                            stringResource(R.string.slideshow_nothing_to_play)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (canPlay) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slideshow_count")
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    IconToggleButton(
                        checked = optionsExpanded,
                        onCheckedChange = onOptionsExpandedChange,
                        modifier = Modifier
                            .testTag("slideshow_options_toggle")
                            .semantics { contentDescription = optionsDescription }
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                    }
                }
                if (optionsExpanded) {
                    SlideshowOptionsArea(options = options, onOptionsChange = onOptionsChange)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPlay,
                enabled = canPlay,
                modifier = Modifier.testTag("slideshow_play_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.slideshow_play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun SlideshowOptionsArea(
    options: SlideshowOptions,
    onOptionsChange: (SlideshowOptions) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("slideshow_options_area"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IntervalStepper(options = options, onOptionsChange = onOptionsChange)

        ChoiceGroup(
            label = R.string.slideshow_transition,
            selected = options.transition,
            choices = listOf(
                Choice(SlideshowTransition.FADE, R.string.slideshow_transition_fade, "slideshow_transition_fade"),
                Choice(SlideshowTransition.NONE, R.string.slideshow_transition_none, "slideshow_transition_none")
            ),
            onSelect = { onOptionsChange(options.copy(transition = it)) }
        )

        SwitchRow(
            label = R.string.slideshow_include_videos,
            checked = options.includeVideos,
            tag = "slideshow_include_videos",
            onCheckedChange = { onOptionsChange(options.copy(includeVideos = it)) }
        )
        SwitchRow(
            label = R.string.slideshow_include_gifs,
            checked = options.includeGifs,
            tag = "slideshow_include_gifs",
            onCheckedChange = { onOptionsChange(options.copy(includeGifs = it)) }
        )

        ChoiceGroup(
            label = R.string.slideshow_order,
            selected = options.order,
            choices = listOf(
                Choice(SlideshowOrder.ASCENDING, R.string.slideshow_order_ascending, "slideshow_order_ascending"),
                Choice(SlideshowOrder.DESCENDING, R.string.slideshow_order_descending, "slideshow_order_descending"),
                Choice(SlideshowOrder.RANDOM, R.string.slideshow_order_random, "slideshow_order_random")
            ),
            onSelect = { onOptionsChange(options.copy(order = it)) }
        )

        SwitchRow(
            label = R.string.slideshow_loop,
            checked = options.loop,
            tag = "slideshow_loop",
            onCheckedChange = { onOptionsChange(options.copy(loop = it)) }
        )

        ChoiceGroup(
            label = R.string.slideshow_orientation,
            selected = options.orientation,
            choices = listOf(
                Choice(SlideshowOrientation.ALL, R.string.slideshow_orientation_all, "slideshow_orientation_all"),
                Choice(
                    SlideshowOrientation.LANDSCAPE_ONLY,
                    R.string.slideshow_orientation_landscape,
                    "slideshow_orientation_landscape"
                ),
                Choice(
                    SlideshowOrientation.PORTRAIT_ONLY,
                    R.string.slideshow_orientation_portrait,
                    "slideshow_orientation_portrait"
                )
            ),
            onSelect = { onOptionsChange(options.copy(orientation = it)) }
        )
    }
}

@Composable
private fun IntervalStepper(
    options: SlideshowOptions,
    onOptionsChange: (SlideshowOptions) -> Unit
) {
    val seconds = options.intervalSeconds
    val decreaseDescription = stringResource(R.string.slideshow_interval_decrease)
    val increaseDescription = stringResource(R.string.slideshow_interval_increase)

    Column {
        SectionLabel(R.string.slideshow_interval)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    onOptionsChange(options.copy(intervalSeconds = (seconds - 1).coerceAtLeast(SLIDESHOW_MIN_INTERVAL_SECONDS)))
                },
                enabled = seconds > SLIDESHOW_MIN_INTERVAL_SECONDS,
                modifier = Modifier
                    .testTag("slideshow_interval_decrease")
                    .semantics { contentDescription = decreaseDescription }
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }
            Text(
                text = pluralStringResource(R.plurals.slideshow_interval_seconds, seconds, seconds),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .testTag("slideshow_interval_value")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            IconButton(
                onClick = {
                    onOptionsChange(options.copy(intervalSeconds = (seconds + 1).coerceAtMost(SLIDESHOW_MAX_INTERVAL_SECONDS)))
                },
                enabled = seconds < SLIDESHOW_MAX_INTERVAL_SECONDS,
                modifier = Modifier
                    .testTag("slideshow_interval_increase")
                    .semantics { contentDescription = increaseDescription }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        Text(
            text = stringResource(R.string.slideshow_interval_videos_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class Choice<T>(val value: T, @StringRes val label: Int, val tag: String)

@Composable
private fun <T> ChoiceGroup(
    @StringRes label: Int,
    selected: T,
    choices: List<Choice<T>>,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.selectableGroup()) {
        SectionLabel(label)
        choices.forEach { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(choice.tag)
                    .selectable(
                        selected = choice.value == selected,
                        onClick = { onSelect(choice.value) },
                        role = Role.RadioButton
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = choice.value == selected, onClick = null)
                Text(
                    text = stringResource(choice.label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    @StringRes label: Int,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionLabel(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}
