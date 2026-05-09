package com.mistakenotes.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val InkStoneColorScheme = darkColorScheme(
    primary = InkStoneAccent,
    secondary = InkStoneAccentDark,
    tertiary = InkStoneWarning,
    background = InkStoneBg,
    surface = InkStoneSurface,
    surfaceVariant = InkStoneSurfaceHover,
    onPrimary = InkStoneBg,
    onSecondary = InkStoneBg,
    onTertiary = InkStoneBg,
    onBackground = InkStoneText,
    onSurface = InkStoneText,
    onSurfaceVariant = InkStoneTextDim,
    error = InkStoneError,
    onError = InkStoneText
)

@Composable
fun MistakeNotesTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = InkStoneColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = InkStoneBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}