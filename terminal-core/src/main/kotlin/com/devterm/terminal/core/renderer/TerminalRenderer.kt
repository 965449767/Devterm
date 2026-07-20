package com.devterm.terminal.core.renderer

interface TerminalRenderer {
    fun setSize(width: Int, height: Int)
    fun invalidateAll()
}

data class RenderFrame(
    val dirtyRows: List<Int>,
    val chars: CharArray,
    val fg: IntArray,
    val bg: IntArray,
    val flags: ByteArray,
    val cols: Int,
    val rows: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean
)
