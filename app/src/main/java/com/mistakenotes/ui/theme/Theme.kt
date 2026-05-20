package com.mistakenotes.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AmberGold,
    secondary = TextCream,
    background = InkStoneBlack,
    surface = CardDark,
    onPrimary = InkStoneBlack,
    onSecondary = InkStoneBlack,
    onBackground = TextCream,
    onSurface = TextCream,
    error = ErrorRed
)

@Composable
fun MistakeNotesTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = InkStoneBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}