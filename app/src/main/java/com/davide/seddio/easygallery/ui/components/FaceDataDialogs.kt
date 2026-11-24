package com.davide.seddio.easygallery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.davide.seddio.easygallery.R
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val FACE_DATA_DISCLOSURE_TAG = "face_data_disclosure"
internal const val FACE_DATA_DISCLOSURE_OK_TAG = "face_data_disclosure_ok"
internal const val FACE_DATA_DISCLOSURE_TURN_OFF_TAG = "face_data_disclosure_turn_off"
internal const val FACE_DATA_DISCLOSURE_CLOSE_TAG = "face_data_disclosure_close"
internal const val FACE_DATA_DELETE_DIALOG_TAG = "face_data_delete_dialog"
internal const val FACE_DATA_DELETE_CONFIRM_TAG = "face_data_delete_confirm"
internal const val FACE_DATA_DELETE_CANCEL_TAG = "face_data_delete_cancel"
internal const val FACE_LICENCES_NOTICE_TAG = "face_licences_notice"
internal const val FACE_LICENCES_LIST_TAG = "face_licences_list"

/** Only the info mode may be dismissed by Back or an outside tap: "shown" must mean a button was pressed. */
internal fun faceDataDisclosureProperties(isInfoMode: Boolean) =
    DialogProperties(dismissOnBackPress = isInfoMode, dismissOnClickOutside = isInfoMode)

/**
 * The Face data disclosure (ADR-0024 Am. 1 §1). In the disclosure mode it has OK and Turn off and neither Back nor a tap
 * outside closes it, because "shown" must mean a button was pressed. In the info mode, opened from Settings, it has one
 * Close and no side effect.
 */
@Composable
fun FaceDataDisclosureDialog(
    isInfoMode: Boolean,
    onOk: () -> Unit,
    onTurnOff: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (isInfoMode) onClose() },
        properties = faceDataDisclosureProperties(isInfoMode),
        title = { Text(stringResource(R.string.face_data_disclosure_title)) },
        text = {
            // Scrolls so a large font size cannot push the buttons off screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag(FACE_DATA_DISCLOSURE_TAG),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    R.string.face_data_disclosure_p1,
                    R.string.face_data_disclosure_p2,
                    R.string.face_data_disclosure_p3,
                    R.string.face_data_disclosure_p4,
                    R.string.face_data_disclosure_p5
                ).forEach { paragraph ->
                    Text(text = stringResource(paragraph), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (isInfoMode) {
                TextButton(onClick = onClose, modifier = Modifier.testTag(FACE_DATA_DISCLOSURE_CLOSE_TAG)) {
                    Text(stringResource(R.string.action_close))
                }
            } else {
                TextButton(onClick = onOk, modifier = Modifier.testTag(FACE_DATA_DISCLOSURE_OK_TAG)) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        },
        dismissButton = if (isInfoMode) {
            null
        } else {
            {
                TextButton(onClick = onTurnOff, modifier = Modifier.testTag(FACE_DATA_DISCLOSURE_TURN_OFF_TAG)) {
                    Text(stringResource(R.string.face_data_turn_off))
                }
            }
        }
    )
}

/** The only gate before face data is deleted; Cancel changes nothing (ADR-0024 Am. 1 §4). */
@Composable
fun DeleteFaceDataDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.face_data_delete_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag(FACE_DATA_DELETE_DIALOG_TAG),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.face_data_delete_message_1), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.face_data_delete_message_2), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(FACE_DATA_DELETE_CONFIRM_TAG)
            ) {
                Text(stringResource(R.string.face_data_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(FACE_DATA_DELETE_CANCEL_TAG)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** The notices for what finds faces, in the order they are shown; the text is verbatim and never translated. */
internal val FACE_LICENCE_FILE_NAMES = listOf(
    "NOTICE.txt",
    "LICENSE-OpenCV-Apache-2.0.txt",
    "LICENSE-SFace-Apache-2.0.txt",
    "LICENSE-YuNet-MIT.txt",
    "THIRD_PARTY_NOTICES.txt"
)

internal const val FACE_LICENCE_ASSET_DIRECTORY = "face"

internal class FaceLicenceText(val fileName: String, val paragraphs: List<String>)

internal sealed interface FaceLicencesLoad {
    data object Loading : FaceLicencesLoad
    data object Failed : FaceLicencesLoad
    class Loaded(val files: List<FaceLicenceText>) : FaceLicencesLoad
}

/** Groups the lines between blank lines, each line kept exactly as written, so hard-wrapped text lists lazily. */
internal fun splitNoticeParagraphs(text: String): List<String> {
    val paragraphs = mutableListOf<String>()
    val current = mutableListOf<String>()
    for (line in text.replace("\r\n", "\n").split('\n')) {
        if (line.isBlank()) {
            if (current.isNotEmpty()) paragraphs += current.joinToString("\n")
            current.clear()
        } else {
            current += line
        }
    }
    if (current.isNotEmpty()) paragraphs += current.joinToString("\n")
    return paragraphs
}

/** Blocking: reads every notice through [open], which takes a file name; throws if any cannot be read. */
internal fun readFaceLicences(open: (String) -> InputStream): List<FaceLicenceText> =
    FACE_LICENCE_FILE_NAMES.map { name ->
        val text = open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        FaceLicenceText(name, splitNoticeParagraphs(text))
    }

/** The notices for OpenCV, YuNet and SFace, read from the app's assets off the main thread. */
@Composable
fun FaceLicencesDialog(onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val load by produceState<FaceLicencesLoad>(initialValue = FaceLicencesLoad.Loading, assets) {
        value = withContext(Dispatchers.IO) {
            try {
                FaceLicencesLoad.Loaded(readFaceLicences { name -> assets.open("$FACE_LICENCE_ASSET_DIRECTORY/$name") })
            } catch (_: Exception) {
                FaceLicencesLoad.Failed
            }
        }
    }
    FaceLicencesDialogContent(load = load, onDismiss = onDismiss)
}

@Composable
internal fun FaceLicencesDialogContent(
    load: FaceLicencesLoad,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_face_licences)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().testTag(FACE_LICENCES_NOTICE_TAG)) {
                when (load) {
                    FaceLicencesLoad.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.face_licences_loading), style = MaterialTheme.typography.bodyMedium)
                    }

                    FaceLicencesLoad.Failed ->
                        Text(stringResource(R.string.face_licences_failed), style = MaterialTheme.typography.bodyMedium)

                    is FaceLicencesLoad.Loaded -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().testTag(FACE_LICENCES_LIST_TAG),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(stringResource(R.string.face_licences_intro), style = MaterialTheme.typography.bodyMedium)
                        }
                        load.files.forEach { file ->
                            item {
                                Text(
                                    text = file.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.semantics { heading() }
                                )
                            }
                            items(file.paragraphs) { paragraph ->
                                Text(
                                    text = paragraph,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        }
    )
}
