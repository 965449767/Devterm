package com.devterm.terminal.core.screen

import com.devterm.terminal.core.parser.ScreenCommand
import com.devterm.terminal.core.scrollback.ScrollbackBuffer
import com.devterm.terminal.core.unicode.UnicodeWidthCache

class ScreenBuffer(
    var cols: Int = 80,
    var rows: Int = 24
) {
    var chars: CharArray
    var fg: IntArray
    var bg: IntArray
    var flags: ByteArray
    val dirty = DirtyTracker(rows)
    val scrollback = ScrollbackBuffer(50000)

    var cursor = CursorState()
    private var savedCursor = CursorState()

    var defaultFg: Int = 0xFFE0E0E0.toInt()
    var defaultBg: Int = 0xFF1A1A2E.toInt()
    var currentFg: Int = defaultFg
    var currentBg: Int = defaultBg
    var currentFlags: Byte = 0

    var scrollRegionTop: Int = 0
    var scrollRegionBottom: Int = rows - 1
    var originMode: Boolean = false
    var autoWrap: Boolean = true

    private var wrapPending: Boolean = false

    init {
        val total = cols * rows
        chars = CharArray(total) { ' ' }
        fg = IntArray(total) { defaultFg }
        bg = IntArray(total) { defaultBg }
        flags = ByteArray(total) { 0 }
    }

    fun resize(newCols: Int, newRows: Int) {
        val oldCols = cols
        val oldRows = rows
        val newTotal = newCols * newRows

        val newChars = CharArray(newTotal) { ' ' }
        val newFg = IntArray(newTotal) { defaultFg }
        val newBg = IntArray(newTotal) { defaultBg }
        val newFlags = ByteArray(newTotal) { 0 }

        val copyRows = minOf(oldRows, newRows)
        val copyCols = minOf(oldCols, newCols)
        for (r in 0 until copyRows) {
            val srcStart = r * oldCols
            val dstStart = r * newCols
            for (c in 0 until copyCols) {
                val si = srcStart + c
                val di = dstStart + c
                newChars[di] = chars[si]
                newFg[di] = fg[si]
                newBg[di] = bg[si]
                newFlags[di] = flags[si]
            }
        }

        chars = newChars
        fg = newFg
        bg = newBg
        flags = newFlags

        cols = newCols
        rows = newRows
        scrollRegionBottom = rows - 1
        scrollRegionTop = 0
        dirty.resize(newRows)
        dirty.markAll()
    }

    fun execute(command: ScreenCommand) = when (command) {
        is ScreenCommand.WriteGlyph -> putChar(command.c, command.width)
        is ScreenCommand.WriteString -> command.s.forEach { c -> putChar(c, 1) }
        is ScreenCommand.MoveCursor -> setCursor(command.row, command.col)
        is ScreenCommand.CursorUp -> cursorUp(command.n)
        is ScreenCommand.CursorDown -> cursorDown(command.n)
        is ScreenCommand.CursorForward -> cursorForward(command.n)
        is ScreenCommand.CursorBack -> cursorBack(command.n)
        is ScreenCommand.SetCursorCol -> setCursorCol(command.col)
        is ScreenCommand.CarriageReturn -> carriageReturn()
        is ScreenCommand.LineFeed -> lineFeed()
        is ScreenCommand.ReverseLineFeed -> reverseLineFeed()
        is ScreenCommand.ScrollUp -> scrollUp()
        is ScreenCommand.ScrollDown -> scrollDown()
        is ScreenCommand.EraseDisplay -> eraseDisplay(command.mode)
        is ScreenCommand.EraseLine -> eraseLine(command.mode)
        is ScreenCommand.DeleteChars -> deleteChars(command.n)
        is ScreenCommand.InsertLines -> insertLines(command.n)
        is ScreenCommand.DeleteLines -> deleteLines(command.n)
        is ScreenCommand.InsertChars -> insertChars(command.n)
        is ScreenCommand.SaveCursor -> saveCursor()
        is ScreenCommand.RestoreCursor -> restoreCursor()
        is ScreenCommand.SetSgr -> setSgr(command.params)
        is ScreenCommand.SetScrollRegion -> setScrollRegion(command.top, command.bottom)
        is ScreenCommand.SetMode -> setMode(command.mode, command.set)
        is ScreenCommand.Reset -> reset()
        is ScreenCommand.Bell -> {}
        else -> {}
    }

    private fun index(row: Int, col: Int) = row * cols + col

    private fun putChar(c: Char, width: Int) {
        if (wrapPending && autoWrap && c != '\n') {
            if (cursor.col > 0 || cursor.row < rows - 1) {
                if (cursor.row < rows - 1) {
                    cursor = cursor.withRow(cursor.row + 1)
                }
                cursor = cursor.withCol(0)
            }
            wrapPending = false
        }

        val row = cursor.row.coerceIn(0, rows - 1)
        val col = cursor.col.coerceIn(0, cols - 1)
        val i = index(row, col)

        chars[i] = c
        fg[i] = currentFg
        bg[i] = currentBg
        flags[i] = CellFlags.setWidth(currentFlags, width)

        if (c == ' ' && width == 1) {
            flags[i] = currentFlags
        }

        dirty.mark(row)

        val nextCol = cursor.col + width
        if (nextCol >= cols) {
            if (autoWrap) {
                wrapPending = true
            }
            cursor = cursor.withCol(0)
        } else {
            cursor = cursor.withCol(nextCol)
        }
    }

    private fun carriageReturn() {
        cursor = cursor.withCol(0)
        wrapPending = false
    }

    fun lineFeed() {
        val row = cursor.row + 1
        if (row > scrollRegionBottom) {
            scrollUp()
        } else {
            cursor = cursor.withRow(row)
        }
    }

    private fun reverseLineFeed() {
        val row = cursor.row - 1
        if (row < scrollRegionTop) {
            scrollDown()
        } else {
            cursor = cursor.withRow(row)
        }
    }

    fun scrollUp() {
        val top = scrollRegionTop
        val bottom = scrollRegionBottom
        val lineCount = bottom - top + 1
        if (lineCount <= 0) return

        if (top == 0) {
            val scrollLine = ScreenLine(
                chars.copyOfRange(0, cols),
                fg.copyOfRange(0, cols),
                bg.copyOfRange(0, cols),
                flags.copyOfRange(0, cols)
            )
            scrollback.push(scrollLine)
        }

        val lineLength = cols
        val srcStart = index(top + 1, 0)
        val dstStart = index(top, 0)
        val copyLen = (lineCount - 1) * lineLength

        System.arraycopy(chars, srcStart, chars, dstStart, copyLen)
        System.arraycopy(fg, srcStart, fg, dstStart, copyLen)
        System.arraycopy(bg, srcStart, bg, dstStart, copyLen)
        System.arraycopy(flags, srcStart, flags, dstStart, copyLen)

        val lastRowStart = index(bottom, 0)
        for (c in 0 until cols) {
            val li = lastRowStart + c
            chars[li] = ' '
            fg[li] = defaultFg
            bg[li] = defaultBg
            flags[li] = 0
        }

        dirty.mark(top)
        dirty.mark(bottom)
    }

    private fun scrollDown() {
        val top = scrollRegionTop
        val bottom = scrollRegionBottom
        val lineCount = bottom - top + 1
        if (lineCount <= 0) return

        val lineLength = cols
        val srcStart = index(top, 0)
        val dstStart = index(top + 1, 0)
        val copyLen = (lineCount - 1) * lineLength

        System.arraycopy(chars, srcStart, chars, dstStart, copyLen)
        System.arraycopy(fg, srcStart, fg, dstStart, copyLen)
        System.arraycopy(bg, srcStart, bg, dstStart, copyLen)
        System.arraycopy(flags, srcStart, flags, dstStart, copyLen)

        for (c in 0 until cols) {
            val li = index(top, c)
            chars[li] = ' '
            fg[li] = defaultFg
            bg[li] = defaultBg
            flags[li] = 0
        }

        dirty.mark(top)
        dirty.mark(bottom)
    }

    fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                val start = index(cursor.row, cursor.col)
                for (i in start until cols * rows) {
                    chars[i] = ' '; fg[i] = defaultFg; bg[i] = defaultBg; flags[i] = 0
                }
                dirty.mark(cursor.row)
            }
            1 -> {
                val end = index(cursor.row, cursor.col) + 1
                for (i in 0 until end.coerceAtMost(chars.size)) {
                    chars[i] = ' '; fg[i] = defaultFg; bg[i] = defaultBg; flags[i] = 0
                }
                dirty.mark(cursor.row)
            }
            2 -> {
                chars.fill(' ')
                fg.fill(defaultFg)
                bg.fill(defaultBg)
                flags.fill(0)
                cursor = cursor.withRow(0).withCol(0)
                dirty.markAll()
                wrapPending = false
            }
            3 -> {
                scrollback.clear()
                eraseDisplay(2)
            }
        }
    }

    fun eraseLine(mode: Int) {
        val row = cursor.row
        val start: Int
        val end: Int
        when (mode) {
            0 -> { start = cursor.col; end = cols }
            1 -> { start = 0; end = cursor.col + 1 }
            2 -> { start = 0; end = cols }
            else -> return
        }
        for (c in start until end) {
            val i = index(row, c)
            if (i in chars.indices) {
                chars[i] = ' '
                fg[i] = defaultFg
                bg[i] = defaultBg
                flags[i] = 0
            }
        }
        dirty.mark(row)
    }

    private fun deleteChars(n: Int) {
        val row = cursor.row
        val col = cursor.col
        val count = n.coerceAtMost(cols - col)
        val srcPos = index(row, col + count)
        val dstPos = index(row, col)
        val len = cols - col - count
        if (len > 0) {
            System.arraycopy(chars, srcPos, chars, dstPos, len)
            System.arraycopy(fg, srcPos, fg, dstPos, len)
            System.arraycopy(bg, srcPos, bg, dstPos, len)
            System.arraycopy(flags, srcPos, flags, dstPos, len)
        }
        for (c in (cols - count) until cols) {
            val i = index(row, c)
            chars[i] = ' '
            fg[i] = defaultFg
            bg[i] = defaultBg
            flags[i] = 0
        }
        dirty.mark(row)
    }

    private fun insertLines(n: Int) {
        val row = cursor.row
        val bottom = scrollRegionBottom
        if (row > bottom || row < scrollRegionTop) return
        val count = n.coerceAtMost(bottom - row + 1)
        val lineLen = cols
        val srcStart = index(row, 0)
        val dstStart = index(row + count, 0)
        val copyLen = (bottom - row - count + 1) * lineLen
        if (copyLen > 0) {
            System.arraycopy(chars, srcStart, chars, dstStart, copyLen)
            System.arraycopy(fg, srcStart, fg, dstStart, copyLen)
            System.arraycopy(bg, srcStart, bg, dstStart, copyLen)
            System.arraycopy(flags, srcStart, flags, dstStart, copyLen)
        }
        val clearEnd = (row + count).coerceAtMost(bottom + 1)
        for (r in row until clearEnd) {
            for (c in 0 until cols) {
                val i = index(r, c)
                chars[i] = ' '
                fg[i] = defaultFg
                bg[i] = defaultBg
                flags[i] = 0
            }
        }
        dirty.mark(row)
        dirty.mark(bottom)
    }

    private fun deleteLines(n: Int) {
        val row = cursor.row
        val bottom = scrollRegionBottom
        if (row > bottom || row < scrollRegionTop) return
        val count = n.coerceAtMost(bottom - row + 1)
        val lineLen = cols
        val srcStart = index(row + count, 0)
        val dstStart = index(row, 0)
        val copyLen = (bottom - row - count + 1) * lineLen
        if (copyLen > 0) {
            System.arraycopy(chars, srcStart, chars, dstStart, copyLen)
            System.arraycopy(fg, srcStart, fg, dstStart, copyLen)
            System.arraycopy(bg, srcStart, bg, dstStart, copyLen)
            System.arraycopy(flags, srcStart, flags, dstStart, copyLen)
        }
        for (r in (bottom - count + 1)..bottom) {
            for (c in 0 until cols) {
                val i = index(r, c)
                chars[i] = ' '
                fg[i] = defaultFg
                bg[i] = defaultBg
                flags[i] = 0
            }
        }
        dirty.mark(row)
        dirty.mark(bottom)
    }

    private fun insertChars(n: Int) {
        val row = cursor.row
        val col = cursor.col
        val count = n.coerceAtMost(cols - col)
        val srcStart = index(row, col)
        val dstStart = index(row, col + count)
        val len = cols - col - count
        if (len > 0) {
            System.arraycopy(chars, srcStart, chars, dstStart, len)
            System.arraycopy(fg, srcStart, fg, dstStart, len)
            System.arraycopy(bg, srcStart, bg, dstStart, len)
            System.arraycopy(flags, srcStart, flags, dstStart, len)
        }
        for (c in col until (col + count).coerceAtMost(cols)) {
            val i = index(row, c)
            chars[i] = ' '
            fg[i] = defaultFg
            bg[i] = defaultBg
            flags[i] = 0
        }
        dirty.mark(row)
    }

    private fun setCursor(row: Int, col: Int) {
        val r = if (originMode) (row + scrollRegionTop).coerceIn(scrollRegionTop, scrollRegionBottom) else row.coerceIn(0, rows - 1)
        val c = col.coerceIn(0, cols - 1)
        cursor = cursor.withRow(r).withCol(c)
        wrapPending = false
    }

    private fun cursorUp(n: Int) {
        val target = cursor.row - n
        val minRow = if (originMode) scrollRegionTop else 0
        cursor = cursor.withRow(target.coerceAtLeast(minRow))
    }

    private fun cursorDown(n: Int) {
        val target = cursor.row + n
        val maxRow = if (originMode) scrollRegionBottom else rows - 1
        cursor = cursor.withRow(target.coerceAtMost(maxRow))
    }

    private fun cursorForward(n: Int) {
        cursor = cursor.withCol((cursor.col + n).coerceAtMost(cols - 1))
    }

    private fun cursorBack(n: Int) {
        cursor = cursor.withCol((cursor.col - n).coerceAtLeast(0))
    }

    private fun setCursorCol(col: Int) {
        cursor = cursor.withCol(col.coerceIn(0, cols - 1))
    }

    private fun saveCursor() {
        savedCursor = cursor
    }

    private fun restoreCursor() {
        cursor = savedCursor
    }

    private fun setSgr(params: List<Int>) {
        if (params.isEmpty()) {
            currentFg = defaultFg
            currentBg = defaultBg
            currentFlags = 0
            return
        }
        var i = 0
        while (i < params.size) {
            val p = params[i]
            when (p) {
                0 -> { currentFg = defaultFg; currentBg = defaultBg; currentFlags = 0 }
                1 -> currentFlags = (currentFlags.toInt() or CellFlags.BOLD).toByte()
                2 -> currentFlags = (currentFlags.toInt() or CellFlags.DIM).toByte()
                3 -> currentFlags = (currentFlags.toInt() or CellFlags.ITALIC).toByte()
                4 -> currentFlags = (currentFlags.toInt() or CellFlags.UNDERLINE).toByte()
                5, 6 -> currentFlags = (currentFlags.toInt() or CellFlags.BLINK).toByte()
                7 -> currentFlags = (currentFlags.toInt() or CellFlags.REVERSE).toByte()
                22 -> currentFlags = (currentFlags.toInt() and CellFlags.BOLD.inv() and CellFlags.DIM.inv()).toByte()
                23 -> currentFlags = (currentFlags.toInt() and CellFlags.ITALIC.inv()).toByte()
                24 -> currentFlags = (currentFlags.toInt() and CellFlags.UNDERLINE.inv()).toByte()
                25 -> currentFlags = (currentFlags.toInt() and CellFlags.BLINK.inv()).toByte()
                27 -> currentFlags = (currentFlags.toInt() and CellFlags.REVERSE.inv()).toByte()
                30, 31, 32, 33, 34, 35, 36, 37 -> currentFg = ansiColor(p - 30)
                38 -> {
                    val (color, skip) = parseExtendedColor(params, i)
                    if (color != null) currentFg = color
                    i += skip
                }
                39 -> currentFg = defaultFg
                40, 41, 42, 43, 44, 45, 46, 47 -> currentBg = ansiColor(p - 40)
                48 -> {
                    val (color, skip) = parseExtendedColor(params, i)
                    if (color != null) currentBg = color
                    i += skip
                }
                49 -> currentBg = defaultBg
                90, 91, 92, 93, 94, 95, 96, 97 -> currentFg = ansiBrightColor(p - 90)
                100, 101, 102, 103, 104, 105, 106, 107 -> currentBg = ansiBrightColor(p - 100)
            }
            i++
        }
    }

    private fun parseExtendedColor(params: List<Int>, start: Int): Pair<Int?, Int> {
        if (start + 1 >= params.size) return null to 0
        return when (params[start + 1]) {
            2 -> {
                if (start + 4 >= params.size) null to 0
                else {
                    val r = params[start + 2].coerceIn(0, 255)
                    val g = params[start + 3].coerceIn(0, 255)
                    val b = params[start + 4].coerceIn(0, 255)
                    0xFF000000.toInt() or (r shl 16) or (g shl 8) or b to 4
                }
            }
            5 -> {
                if (start + 2 >= params.size) null to 0
                else ansi256Color(params[start + 2]) to 2
            }
            else -> null to 0
        }
    }

    private fun ansiColor(n: Int): Int = when (n) {
        0 -> 0xFF000000.toInt()
        1 -> 0xFFCC0000.toInt()
        2 -> 0xFF00CC00.toInt()
        3 -> 0xFFCCCC00.toInt()
        4 -> 0xFF0000CC.toInt()
        5 -> 0xFFCC00CC.toInt()
        6 -> 0xFF00CCCC.toInt()
        7 -> 0xFFE0E0E0.toInt()
        else -> 0xFFE0E0E0.toInt()
    }

    private fun ansiBrightColor(n: Int): Int = when (n) {
        0 -> 0xFF666666.toInt()
        1 -> 0xFFFF3333.toInt()
        2 -> 0xFF33FF33.toInt()
        3 -> 0xFFFFFF33.toInt()
        4 -> 0xFF3333FF.toInt()
        5 -> 0xFFFF33FF.toInt()
        6 -> 0xFF33FFFF.toInt()
        7 -> 0xFFFFFFFF.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    private fun ansi256Color(n: Int): Int {
        if (n < 16) return ansiColor(n)
        if (n < 232) {
            val idx = n - 16
            val r = (idx / 36) * 51
            val g = ((idx % 36) / 6) * 51
            val b = (idx % 6) * 51
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val gray = (n - 232) * 10 + 8
        val g = gray.coerceIn(0, 255)
        return 0xFF000000.toInt() or (g shl 16) or (g shl 8) or g
    }

    private fun setScrollRegion(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1)
        val b = bottom.coerceIn(t, rows - 1)
        scrollRegionTop = t
        scrollRegionBottom = b
        cursor = cursor.withRow(0).withCol(0)
    }

    private fun setMode(mode: Int, set: Boolean) {
        when (mode) {
            6 -> originMode = set
            7 -> autoWrap = set
            25 -> cursor = cursor.copy(visible = set)
        }
    }

    private fun reset() {
        val total = cols * rows
        chars = CharArray(total) { ' ' }
        fg = IntArray(total) { defaultFg }
        bg = IntArray(total) { defaultBg }
        flags = ByteArray(total) { 0 }
        cursor = CursorState()
        currentFg = defaultFg
        currentBg = defaultBg
        currentFlags = 0
        scrollRegionTop = 0
        scrollRegionBottom = rows - 1
        originMode = false
        autoWrap = true
        wrapPending = false
        dirty.markAll()
    }
}
