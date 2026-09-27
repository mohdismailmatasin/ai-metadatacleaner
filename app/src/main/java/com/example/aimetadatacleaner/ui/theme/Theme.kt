package com.example.aimetadatacleaner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccentLight,
    onPrimary = Slate950,
    primaryContainer = Slate850,
    onPrimaryContainer = CyanAccentLight,
    secondary = IndigoLight,
    onSecondary = Slate950,
    secondaryContainer = Slate800,
    onSecondaryContainer = IndigoLight,
    tertiary = EmeraldLight,
    onTertiary = Slate950,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate200,
    outline = Slate700,
    outlineVariant = Slate800
)

private val LightColorScheme = lightColorScheme(
    primary = CyanAccentDark,
    onPrimary = Slate100,
    primaryContainer = Slate200,
    onPrimaryContainer = Slate900,
    secondary = IndigoAccent,
    onSecondary = Slate100,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = EmeraldSuccess,
    onTertiary = Slate100,
    background = Slate100,
    onBackground = Slate900,
    surface = Slate200,
    onSurface = Slate900,
    surfaceVariant = Slate200,
    onSurfaceVariant = Slate700,
    outline = Slate400,
    outlineVariant = Slate200
)

@Composable
fun AIMetadataCleanerTheme(
    darkTheme: Boolean = true, // Default to sleek privacy dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
