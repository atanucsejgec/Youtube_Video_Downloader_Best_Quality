package com.example.youtubedownloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF0A1929),
    primaryContainer = Color(0xFF004D61),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF0A1929),
    tertiary = Color(0xFF80DEEA),
    background = Color(0xFF0A1929),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF101D2E),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF1A2A3A),
    onSurfaceVariant = Color(0xFFA0AEC0),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A1A1A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF0288D1),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF00838F),
    background = Color(0xFFF8FAFE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFECF0F4),
    onSurfaceVariant = Color(0xFF42474E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun YoutubeDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}