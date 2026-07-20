package com.devterm.terminal.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.devterm.terminal.core.renderer.RenderFrame
import com.devterm.terminal.core.screen.CellFlags

data class CharMetrics(
    val width: Float,
    val height: Float
)

class ComposeTerminalRenderer(
    private val textMeasurer: TextMeasurer,
    private val textStyle: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        color = Color.White
    ),
    private val defaultBg: Int = 0xFF1A1A2E.toInt(),
    private val cursorColor: Int = 0xFF89B4FA.toInt()
) {
    lateinit var charMetrics: CharMetrics; private set
    private val glyphCache = GlyphCache(textMeasurer)

    fun initMetrics() {
        val result = textMeasurer.measure(text = "M", style = textStyle)
        charMetrics = CharMetrics(
            width = result.size.width.toFloat(),
            height = result.size.height.toFloat() * 1.2f
        )
    }

    fun draw(drawScope: DrawScope, frame: RenderFrame) {
        if (!::charMetrics.isInitialized) initMetrics()
        for (row in frame.dirtyRows) {
            drawRow(drawScope, row, frame)
        }
        drawCursor(drawScope, frame)
    }

    private fun drawRow(drawScope: DrawScope, row: Int, frame: RenderFrame) {
        with(drawScope) {
            if (row >= frame.rows) return
            val y = row * charMetrics.height
            var x = 0f

            for (col in 0 until frame.cols) {
                val index = row * frame.cols + col
                if (index >= frame.chars.size) break
                val c = frame.chars[index]
                val fgColor = frame.fg[index]
                val bgColor = frame.bg[index]
                val cellFlags = frame.flags[index]
                val w = CellFlags.width(cellFlags).coerceAtLeast(1)
                val cellWidth = charMetrics.width * w

                val reverse = (cellFlags.toInt() and CellFlags.REVERSE) != 0
                val drawBg = if (reverse) fgColor else bgColor
                val drawFg = if (reverse) bgColor else fgColor

                if (drawBg.toLong() != defaultBg.toLong()) {
                    drawRect(
                        color = Color(drawBg),
                        topLeft = Offset(x, y),
                        size = Size(cellWidth, charMetrics.height)
                    )
                }

                if (c != ' ') {
                    val glyph = glyphCache.get(c, drawFg, cellFlags, textStyle)
                    val charX = if (w > 1) {
                        x + (cellWidth - glyph.size.width) / 2f
                    } else x
                    val vy = y + (charMetrics.height - glyph.size.height) / 2f
                    drawText(
                        textLayoutResult = glyph,
                        topLeft = Offset(charX, vy)
                    )
                }

                if ((cellFlags.toInt() and CellFlags.UNDERLINE) != 0) {
                    val underlineY = y + charMetrics.height - 2f
                    drawLine(
                        color = Color(drawFg),
                        start = Offset(x, underlineY),
                        end = Offset(x + cellWidth, underlineY),
                        strokeWidth = 1f
                    )
                }

                x += cellWidth
            }
        }
    }

    private fun drawCursor(drawScope: DrawScope, frame: RenderFrame) {
        with(drawScope) {
            if (!frame.cursorVisible) return
            val x = frame.cursorCol * charMetrics.width
            val y = frame.cursorRow * charMetrics.height
            drawRect(
                color = Color(cursorColor),
                topLeft = Offset(x, y),
                size = Size(charMetrics.width, charMetrics.height),
                alpha = 0.5f
            )
        }
    }

    fun invalidateAll() {
        glyphCache.invalidate()
    }
}
