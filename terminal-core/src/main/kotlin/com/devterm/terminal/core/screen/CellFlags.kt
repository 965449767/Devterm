package com.devterm.terminal.core.screen

/**
 * 单元格位标记常量。
 * 位分配：
 *   bit 0 — BOLD
 *   bit 1 — DIM
 *   bit 2 — ITALIC
 *   bit 3 — UNDERLINE
 *   bit 4 — BLINK
 *   bit 5 — REVERSE
 *   bit 6 — CONCEAL（隐藏文本）
 *   bit 7 — WIDTH（0=宽度1, 1=宽度2）
 */
object CellFlags {
    const val BOLD      = 0x01
    const val DIM       = 0x02
    const val ITALIC    = 0x04
    const val UNDERLINE = 0x08
    const val BLINK     = 0x10
    const val REVERSE   = 0x20
    const val CONCEAL   = 0x40

    private const val WIDTH_SHIFT = 7
    private const val WIDTH_MASK  = 0x80
    const val WIDTH_1   = (0 shl WIDTH_SHIFT).toByte()
    const val WIDTH_2   = (1 shl WIDTH_SHIFT).toByte()

    /** 从 flags 中解析字符显示宽度 */
    fun width(flags: Byte): Int = when ((flags.toInt() and WIDTH_MASK) shr WIDTH_SHIFT) {
        1 -> 2
        else -> 1
    }

    /** 设置字符宽度到 flags 中 */
    fun setWidth(flags: Byte, w: Int): Byte {
        val base = flags.toInt() and WIDTH_MASK.inv()
        val wbits = if (w >= 2) 1 else 0
        return ((base or (wbits shl WIDTH_SHIFT)).toByte())
    }
}
