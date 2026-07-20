package com.devterm.terminal.core.screen

data class CursorState(
    val row: Int = 0,
    val col: Int = 0,
    val visible: Boolean = true,
    val style: CursorStyle = CursorStyle.BLOCK,
    val blink: Boolean = true
) {
    enum class CursorStyle { BLOCK, UNDERLINE, BAR }

    fun withRow(row: Int) = copy(row = row)
    fun withCol(col: Int) = copy(col = col)
    fun movedBy(dr: Int, dc: Int) = copy(
        row = (row + dr).coerceAtLeast(0),
        col = (col + dc).coerceAtLeast(0)
    )
}
