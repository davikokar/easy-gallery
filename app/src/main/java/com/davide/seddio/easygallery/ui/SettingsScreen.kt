package com.davide.seddio.easygallery.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.LocaleHelper
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue
import com.davide.seddio.easygallery.data.MediaIndexRunOutcome
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    billingViewModel: BillingViewModel,
    mediaIndexingViewModel: MediaIndexingViewModel,
    onContactSupportClick: (Context) -> Unit = { contactSupport(it) }
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val indexingState by mediaIndexingViewModel.uiState.collectAsState()

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentTag = LocaleHelper.getPersistedLanguageTag(context),
            onLanguageSelected = { tag ->
                showLanguageDialog = false
                if (tag != LocaleHelper.getPersistedLanguageTag(context)) {
                    LocaleHelper.persistLanguageTag(context, tag)
                    viewModel.onLocaleChanged()
                    activity?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setSettingsMode(false) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = AppBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = stringResource(R.string.settings_general))
            SettingsItem(
                title = stringResource(R.string.settings_manage_excluded),
                icon = Icons.Default.Block,
                onClick = { viewModel.setManageExcludedMode(true) }
            )
            SettingsItem(
                title = stringResource(R.string.settings_change_language),
                icon = Icons.Default.Language,
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.Gray.copy(alpha = 0.5f)
            )

            MediaIndexSettingsArea(
                state = indexingState,
                onIndexNow = mediaIndexingViewModel::indexNow
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.Gray.copy(alpha = 0.5f)
            )

            SettingsSection(title = stringResource(R.string.settings_app))
            SettingsItem(
                title = stringResource(R.string.settings_share_app),
                icon = Icons.Default.Share,
                onClick = { shareApp(context) }
            )
            SettingsItem(
                title = stringResource(R.string.settings_rate_app),
                icon = Icons.Default.Star,
                onClick = { rateApp(context) }
            )
            SettingsItem(
                title = stringResource(R.string.settings_customer_support),
                icon = Icons.Default.SupportAgent,
                onClick = { onContactSupportClick(context) }
            )
            SettingsItem(
                title = stringResource(R.string.settings_about),
                icon = Icons.Default.Info,
                onClick = { showAboutDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.Gray.copy(alpha = 0.5f)
            )

            SettingsSection(title = stringResource(R.string.settings_community_support))
            SettingsItem(
                title = stringResource(R.string.settings_buy_coffee),
                icon = Icons.Default.Coffee,
                onClick = { 
                    activity?.let { billingViewModel.buyCoffee(it) }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.Gray.copy(alpha = 0.5f)
            )

            SettingsSection(title = stringResource(R.string.settings_legal))
            SettingsItem(
                title = stringResource(R.string.settings_privacy),
                icon = Icons.Default.PrivacyTip,
                onClick = { openUrl(context, "https://davikokar.github.io/android-docs/easy-gallery/privacy.html") }
            )
            SettingsItem(
                title = stringResource(R.string.settings_terms),
                icon = Icons.Default.Description,
                onClick = { openUrl(context, "https://davikokar.github.io/android-docs/easy-gallery/terms.html") }
            )
        }
    }
}

@Composable
fun MediaIndexSettingsArea(
    state: MediaIndexingUiState,
    onIndexNow: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_media_index))
    SettingsItem(
        title = if (state.isRunning) {
            stringResource(R.string.settings_index_running)
        } else {
            stringResource(R.string.settings_index_now)
        },
        icon = Icons.AutoMirrored.Filled.ManageSearch,
        onClick = onIndexNow,
        enabled = !state.isRunning,
        modifier = Modifier.testTag("media_index_now_action")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 4.dp)
            .testTag("media_index_status_area"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isRunning) {
            val outstandingRows = (state.selectedRows - state.processedRows).coerceAtLeast(0)
            if (state.selectedRows > 0) {
                LinearProgressIndicator(
                    progress = { state.processedRows.toFloat() / state.selectedRows },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    pluralStringResource(
                        R.plurals.settings_index_remaining_rows,
                        outstandingRows,
                        outstandingRows
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Text(
            pluralStringResource(
                R.plurals.settings_index_with_date,
                state.indexedWithDateCount,
                state.indexedWithDateCount
            ),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            pluralStringResource(
                R.plurals.settings_index_without_date,
                state.indexedWithoutDateCount,
                state.indexedWithoutDateCount
            ),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            pluralStringResource(
                R.plurals.settings_index_not_yet_indexed,
                state.notYetIndexedCount,
                state.notYetIndexedCount
            ),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )

        if (state.skippedRows > 0) {
            Text(
                pluralStringResource(
                    R.plurals.settings_index_skipped_rows,
                    state.skippedRows,
                    state.skippedRows
                ),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val lastIndexedText = state.lastSuccessfulRunFinishedAtMillis?.let { timestamp ->
            stringResource(
                R.string.settings_index_last_success,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
            )
        } ?: stringResource(R.string.settings_index_never)
        Text(
            lastIndexedText,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )

        when (state.lastRunOutcome) {
            MediaIndexRunOutcome.FAILURE -> Text(
                stringResource(R.string.settings_index_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            MediaIndexRunOutcome.CANCELLED -> Text(
                stringResource(R.string.settings_index_cancelled),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium
            )
            else -> Unit
        }
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    Surface(color = AppBackground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SettingsSection(title = stringResource(R.string.settings_general))
            SettingsItem(
                title = stringResource(R.string.settings_manage_excluded),
                icon = Icons.Default.Block,
                onClick = {}
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.Gray.copy(alpha = 0.5f)
            )

            SettingsSection(title = stringResource(R.string.settings_app))
            SettingsItem(
                title = stringResource(R.string.settings_share_app),
                icon = Icons.Default.Share,
                onClick = {}
            )
            SettingsItem(
                title = stringResource(R.string.settings_rate_app),
                icon = Icons.Default.Star,
                onClick = {}
            )
            SettingsItem(
                title = stringResource(R.string.settings_privacy),
                icon = Icons.Default.PrivacyTip,
                onClick = {}
            )
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        color = BrandBlue,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.semantics { disabled() }
                }
            ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSelectionDialog(
    currentTag: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var temporaryTag by remember { mutableStateOf(currentTag) }
    // "" represents "follow the system language".
    val options = listOf("") + LocaleHelper.supportedLanguageTags

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_change_language)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                options.forEach { tag ->
                    val label = if (tag.isEmpty()) {
                        stringResource(R.string.language_system_default)
                    } else {
                        val locale = Locale.forLanguageTag(tag)
                        locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = tag == temporaryTag,
                                onClick = { temporaryTag = tag },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = tag == temporaryTag, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onLanguageSelected(temporaryTag) }) {
                Text(stringResource(R.string.action_ok))
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
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { getAppVersionName(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_about)) },
        text = {
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        }
    )
}

private fun getAppVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun shareApp(context: Context) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            context.getString(R.string.share_app_text, context.packageName)
        )
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}

private fun rateApp(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=${context.packageName}")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val webIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
        }
        context.startActivity(webIntent)
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
    }
    context.startActivity(intent)
}

internal fun createSupportEmailIntent(context: Context): Intent {
    val email = context.getString(com.davide.seddio.easygallery.R.string.support_email_address)
    val subject = context.getString(com.davide.seddio.easygallery.R.string.support_email_subject)
    return Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$email?subject=${Uri.encode(subject)}")
    }
}

private fun contactSupport(context: Context) {
    val intent = createSupportEmailIntent(context)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            context.getString(com.davide.seddio.easygallery.R.string.error_no_email_app),
            Toast.LENGTH_SHORT
        ).show()
    }
}
