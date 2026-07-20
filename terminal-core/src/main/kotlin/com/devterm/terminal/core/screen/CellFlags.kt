package com.devterm.terminal.core.screen

object CellFlags {
    const val BOLD      = 0x01
    const val DIM       = 0x02
    const val ITALIC    = 0x04
    const val UNDERLINE = 0x08
    const val BLINK     = 0x10
    const val REVERSE   = 0x20

    private const val WIDTH_SHIFT = 6
    private const val WIDTH_MASK  = 0xC0
    const val WIDTH_1   = (0 shl WIDTH_SHIFT).toByte()
    const val WIDTH_2   = (1 shl WIDTH_SHIFT).toByte()
    const val WIDTH_4   = (2 shl WIDTH_SHIFT).toByte()

    fun width(flags: Byte): Int = when ((flags.toInt() and WIDTH_MASK) shr WIDTH_SHIFT) {
        1 -> 2
        2 -> 4
        else -> 1
    }

    fun setWidth(flags: Byte, w: Int): Byte {
        val base = flags.toInt() and WIDTH_MASK.inv()
        val wbits = when (w) {
            2 -> 1
            4 -> 2
            else -> 0
        }
        return ((base or (wbits shl WIDTH_SHIFT)).toByte())
    }
}
