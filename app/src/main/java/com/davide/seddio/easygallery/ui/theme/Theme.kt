package com.davide.seddio.easygallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandedDarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = BrandCyan,
    tertiary = BrandAmber,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceHigh,
    outline = AppOutline,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f)
)

// The app intentionally uses a dark-themed UI for both light and dark system settings
private val BrandedLightColorScheme = BrandedDarkColorScheme

@Composable
fun EasyGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color by default to maintain consistent branding
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> BrandedDarkColorScheme
        else -> BrandedLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}