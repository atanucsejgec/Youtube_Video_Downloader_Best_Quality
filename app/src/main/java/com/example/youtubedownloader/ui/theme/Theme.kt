package com.example.youtubedownloader.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DarkBlueColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkNavy,
    primaryContainer = CyanDark,
    onPrimaryContainer = TextWhite,

    secondary = CyanMuted,
    onSecondary = TextWhite,
    secondaryContainer = CardMedium,
    onSecondaryContainer = CyanLight,

    tertiary = ProgressTeal,
    onTertiary = DarkNavy,
    tertiaryContainer = WarningTertiaryContainer,
    onTertiaryContainer = WarningAmber,

    background = DarkNavy,
    onBackground = TextWhite,

    surface = CardDark,
    onSurface = TextWhite,
    surfaceVariant = CardMedium,
    onSurfaceVariant = TextLight,

    error = ErrorRed,
    onError = TextWhite,
    errorContainer = ErrorSurface,
    onErrorContainer = ErrorRed,

    outline = CardBorder,
    outlineVariant = Color(0xFF1E3654),

    inverseSurface = TextWhite,
    inverseOnSurface = DarkNavy,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 1.sp,
        color = CyanPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TextWhite
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = TextLight
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = TextWhite
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TextLight
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = TextMuted
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = CyanPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = TextMuted
    ),
)

@Composable
fun YoutubeDownloaderTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkNavy.toArgb()
            window.navigationBarColor = DarkNavy.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkBlueColorScheme,
        typography = AppTypography,
        content = content
    )
}