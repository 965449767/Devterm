package com.devterm.terminal

data class TerminalLine(
    val cells: List<TerminalCell>
)

data class TerminalCell(
    val char: Char,
    val fgColor: Long,
    val bgColor: Long,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Int,
    val reverse: Boolean,
    val strike: Boolean,
    val width: Int,
    val dim: Boolean = false
)

data class TerminalSnapshot(
    val lines: List<TerminalLine>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val rows: Int,
    val cols: Int
)
