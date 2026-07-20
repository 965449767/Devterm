package com.devterm.ui.theme

import androidx.compose.ui.graphics.Color

data class TerminalTheme(
    val name: String,
    val defaultBg: Int,
    val defaultFg: Int,
    val cursorColor: Int,
    val primary: Color,
    val surface: Color,
    val onSurface: Color
)

val CatppuccinThemes = listOf(
    TerminalTheme(
        name = "Mocha",
        defaultBg = 0xFF1E1E2E.toInt(),
        defaultFg = 0xFFCDD6F4.toInt(),
        cursorColor = 0xFF89B4FA.toInt(),
        primary = Color(0xFF89B4FA),
        surface = Color(0xFF181825),
        onSurface = Color(0xFFCDD6F4)
    ),
    TerminalTheme(
        name = "Macchiato",
        defaultBg = 0xFF24273A.toInt(),
        defaultFg = 0xFFCAD3F5.toInt(),
        cursorColor = 0xFF8AADF4.toInt(),
        primary = Color(0xFF8AADF4),
        surface = Color(0xFF1E2030),
        onSurface = Color(0xFFCAD3F5)
    ),
    TerminalTheme(
        name = "Frappe",
        defaultBg = 0xFF303446.toInt(),
        defaultFg = 0xFFC6D0F5.toInt(),
        cursorColor = 0xFF8CAAEE.toInt(),
        primary = Color(0xFF8CAAEE),
        surface = Color(0xFF292C3C),
        onSurface = Color(0xFFC6D0F5)
    ),
    TerminalTheme(
        name = "Latte",
        defaultBg = 0xFFEFF1F5.toInt(),
        defaultFg = 0xFF4C4F69.toInt(),
        cursorColor = 0xFF4C4F69.toInt(),
        primary = Color(0xFF1E66F5),
        surface = Color(0xFFE6E9EF),
        onSurface = Color(0xFF4C4F69)
    )
)