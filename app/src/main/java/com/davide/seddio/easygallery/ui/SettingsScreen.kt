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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.ui.theme.AppBackground
import com.davide.seddio.easygallery.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    billingViewModel: BillingViewModel,
    onContactSupportClick: (Context) -> Unit = { contactSupport(it) }
) {
    val context = LocalContext.current
    val activity = context.findActivity()
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
                title = stringResource(R.string.settings_privacy),
                icon = Icons.Default.PrivacyTip,
                onClick = { openUrl(context, "https://davikokar.github.io/android-docs/easy-gallery/privacy.html") }
            )
            SettingsItem(
                title = stringResource(R.string.settings_terms),
                icon = Icons.Default.Description,
                onClick = { openUrl(context, "https://davikokar.github.io/android-docs/easy-gallery/terms.html") }
            )
            SettingsItem(
                title = stringResource(R.string.settings_customer_support),
                icon = Icons.Default.SupportAgent,
                onClick = { onContactSupportClick(context) }
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
    subtitle: String? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
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
            "Check out Easy Gallery: https://play.google.com/store/apps/details?id=${context.packageName}"
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
