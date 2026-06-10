package com.klvw.wallpaper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KLVWColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Gray900,
    primaryContainer = SurfaceGlassStrong,
    onPrimaryContainer = White,
    secondary = Gray400,
    onSecondary = Gray900,
    secondaryContainer = Gray700,
    onSecondaryContainer = Gray200,
    tertiary = AccentBlue,
    onTertiary = White,
    tertiaryContainer = Gray700,
    onTertiaryContainer = Gray200,
    background = Gray900,
    onBackground = White,
    surface = Gray800,
    onSurface = White,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray200,
    surfaceTint = Gray800,
    inverseSurface = Gray200,
    inverseOnSurface = Gray900,
    inversePrimary = Gray800,
    outline = Gray600,
    outlineVariant = Gray700,
    error = androidx.compose.ui.graphics.Color(0xFFFF453A),
    onError = White,
    errorContainer = androidx.compose.ui.graphics.Color(0xFF7F1D1D),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    scrim = Gray900
)

@Composable
fun KLVWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KLVWColorScheme,
        typography = KLVWTypography,
        content = content
    )
}
