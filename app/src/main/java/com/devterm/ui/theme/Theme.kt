package com.devterm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF89B4FA),
    secondary = Color(0xFFA6E3A1),
    tertiary = Color(0xFFF5C2E7),
    background = Color(0xFF1E1E2E),
    surface = Color(0xFF181825),
    onPrimary = Color(0xFF1E1E2E),
    onSecondary = Color(0xFF1E1E2E),
    onBackground = Color(0xFFCDD6F4),
    onSurface = Color(0xFFCDD6F4),
)

@Composable
fun DevTermTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun DevTermTheme(
    terminalTheme: TerminalTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = terminalTheme.primary,
        background = terminalTheme.surface,
        surface = terminalTheme.surface,
        onSurface = terminalTheme.onSurface
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
