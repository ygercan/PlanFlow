package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppAccentTheme
import com.example.model.AppThemeMode

@Composable
fun PlanFlowTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentTheme: AppAccentTheme = AppAccentTheme.CORAL,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accentTheme.primary,
            onPrimary = Color.White,
            primaryContainer = accentTheme.mutedDark,
            onPrimaryContainer = accentTheme.primary,
            secondary = accentTheme.primary,
            onSecondary = Color.White,
            secondaryContainer = accentTheme.mutedDark,
            onSecondaryContainer = accentTheme.mutedLight,
            tertiary = AccentGreen,
            background = DarkBg,
            onBackground = DarkTextPrimary,
            surface = DarkCard,
            onSurface = DarkTextPrimary,
            surfaceVariant = DarkCardMuted,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkBorder,
            outlineVariant = Color(0xFF242938)
        )
    } else {
        lightColorScheme(
            primary = accentTheme.primary,
            onPrimary = Color.White,
            primaryContainer = accentTheme.mutedLight,
            onPrimaryContainer = accentTheme.darkAccent,
            secondary = accentTheme.primary,
            onSecondary = Color.White,
            secondaryContainer = accentTheme.mutedLight,
            onSecondaryContainer = accentTheme.darkAccent,
            tertiary = AccentGreen,
            background = LightBg,
            onBackground = LightTextPrimary,
            surface = LightCard,
            onSurface = LightTextPrimary,
            surfaceVariant = LightCardMuted,
            onSurfaceVariant = LightTextSecondary,
            outline = LightBorder,
            outlineVariant = Color(0xFFE0E4EC)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
