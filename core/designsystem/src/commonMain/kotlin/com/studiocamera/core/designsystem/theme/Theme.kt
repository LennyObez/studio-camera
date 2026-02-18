package com.studiocamera.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.studiocamera.core.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = StudioColors.Primary,
    onPrimary = StudioColors.OnPrimary,
    secondary = StudioColors.Secondary,
    onSecondary = StudioColors.OnSecondary,
    background = StudioColors.Background,
    surface = StudioColors.Surface,
    surfaceVariant = StudioColors.SurfaceVariant,
    onBackground = StudioColors.OnBackground,
    onSurface = StudioColors.OnSurface,
    onSurfaceVariant = StudioColors.OnSurfaceVariant,
    error = StudioColors.Error,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = StudioColors.Primary,
    onPrimary = StudioColors.OnPrimary,
    secondary = StudioColors.Secondary,
    onSecondary = StudioColors.OnSecondary,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    error = StudioColors.Error,
    onError = Color.White
)

@Composable
fun StudioCameraTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudioTypography,
        content = content
    )
}
