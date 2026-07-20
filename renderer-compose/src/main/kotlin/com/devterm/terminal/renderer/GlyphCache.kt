package com.devterm.terminal.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.devterm.terminal.core.screen.CellFlags

class GlyphCache(private val textMeasurer: TextMeasurer) {
    private val cache = object : LinkedHashMap<String, TextLayoutResult>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextLayoutResult>?): Boolean {
            return size > 2048
        }
    }

    fun get(
        char: Char, fgColor: Int, flags: Byte, style: TextStyle
    ): TextLayoutResult {
        val bold = (flags.toInt() and CellFlags.BOLD) != 0
        val italic = (flags.toInt() and CellFlags.ITALIC) != 0
        val dim = (flags.toInt() and CellFlags.DIM) != 0

        val displayColor = if (dim) dimColor(fgColor) else fgColor
        val key = "${char}_${displayColor}_${bold}_${italic}"

        return cache.getOrPut(key) {
            val cellStyle = style.copy(
                color = Color(displayColor),
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
            )
            textMeasurer.measure(char.toString(), cellStyle)
        }
    }

    private fun dimColor(color: Int): Int {
        val r = ((color shr 16) and 0xFF) / 2
        val g = ((color shr 8) and 0xFF) / 2
        val b = (color and 0xFF) / 2
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    fun invalidate() {
        cache.clear()
    }
}
