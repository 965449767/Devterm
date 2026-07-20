package com.devterm.terminal

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface TerminalEmulatorCallbacks {
    fun onKeyboardOutput(data: ByteArray)
}

class TerminalEmulator(
    private val callbacks: TerminalEmulatorCallbacks,
    initialRows: Int = 24,
    initialCols: Int = 80
) {
    var rows = initialRows; private set
    var cols = initialCols; private set
    private var scrollbackSize = 1000

    private var screen = Array(rows) { Array(cols) { Cell() } }
    private val scrollback = ArrayDeque<Array<Cell>>()
    private var savedRow = 0
    private var savedCol = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    private var tabStops = BooleanArray(cols)
    private var maxSeenCols = initialCols
    private var applicationCursorKeys = false
    private var originMode = false
    private var autoWrap = true
    private var insertMode = false

    private val _snapshot = MutableStateFlow(buildSnapshot())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val debounceExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "SnapshotDebounce").also { it.isDaemon = true }
    }
    private val debouncePending = AtomicBoolean(false)
    private val debounceMillis = 16L

    var cursorRow = 0; private set
    var cursorCol = 0; private set
    var localEchoBoundaryCol = 0
    var localEchoBoundaryRow = 0
    var cursorVisible = true; private set
    var cursorStyle = 0; private set
    var bold = false; private set
    var dim = false; private set
    var italic = false; private set
    var underline = 0; private set
    var blink = false; private set
    var reverse = false; private set
    var conceal = false; private set
    var strike = false; private set
    var fgColor = 0xFFFFFFFFL
    var bgColor = 0xFF000000L

    init {
        tabStops.fill(false)
        for (i in 0 until cols step 8) tabStops[i] = true
    }

    fun writeInput(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        val snippet = data.copyOfRange(offset, (offset + length).coerceAtMost(data.size))
        val hex = snippet.joinToString("") { "%02x".format(it) }
        val ascii = snippet.map { if (it in 0x20..0x7E) it.toInt().toChar().toString() else "." }.joinToString("")
        Log.v(TAG, "writeInput len=$length hex=[$hex] ascii=[$ascii]")
        if (hex.contains("1b5b")) {
            Log.i(TAG, "writeInput contains CSI sequence!")
        }
        synchronized(this) {
            for (i in offset until offset + length) {
                val b = data[i].toInt() and 0xFF
                processByte(b)
            }
            localEchoBoundaryCol = cursorCol
            localEchoBoundaryRow = cursorRow
        }
        scheduleSnapshot()
    }

    private fun scheduleSnapshot() {
        if (debouncePending.compareAndSet(false, true)) {
            Log.v(TAG, "scheduleSnapshot: scheduled")
            debounceExecutor.schedule({
                synchronized(this) {
                    _snapshot.value = buildSnapshot()
                }
                debouncePending.set(false)
                Log.v(TAG, "scheduleSnapshot: fired")
            }, debounceMillis, TimeUnit.MILLISECONDS)
        }
    }

    var localEcho = true

    fun dispatchCharacter(modifiers: Int, char: Int): Boolean {
        synchronized(this) {
            if (char < 0 || char > 0x10FFFF) return false
            if (modifiers and MOD_CTRL != 0 && char in 0x61..0x7A) {
                sendByte((char - 0x60).toByte())
                return true
            }
            if (char == 0x0D) {
                sendByte(0x0A)
                if (localEcho) {
                    cursorCol = 0; lineFeed()
                    emitSnapshot()
                }
                return true
            }
            if (char == 0x09) {
                sendByte(0x09)
                return true
            }
            if (char in 0x20..0x7E || char >= 0xA0) {
                val buf = encodeUtf8(char)
                sendBytes(buf)
                if (localEcho) {
                    putChar(char.toChar())
                    emitSnapshot()
                }
                return true
            }
            return false
        }
    }

    fun dispatchKey(modifiers: Int, keyCode: Int): Boolean {
        synchronized(this) {
            if (keyCode == KEY_BACKSPACE) {
                sendByte(0x7F)
                if (localEcho) {
                    if (cursorRow > localEchoBoundaryRow ||
                        (cursorRow == localEchoBoundaryRow && cursorCol > localEchoBoundaryCol)) {
                        cursorCol--
                        val cell = screen[cursorRow][cursorCol]
                        if (cell.width > 1) {
                            screen[cursorRow][cursorCol] = Cell()
                            if (cursorCol + 1 < cols) screen[cursorRow][cursorCol + 1] = Cell()
                        } else if (cell.char == ' ' && cursorCol > 0 &&
                            screen[cursorRow][cursorCol - 1].width > 1) {
                            screen[cursorRow][cursorCol - 1] = Cell()
                            screen[cursorRow][cursorCol] = Cell()
                        } else {
                            screen[cursorRow][cursorCol] = Cell()
                        }
                        emitSnapshot()
                    }
                }
                return true
            }
            val seq = when (keyCode) {
                KEY_UP -> if (applicationCursorKeys) "\u001bOA" else "\u001b[A"
                KEY_DOWN -> if (applicationCursorKeys) "\u001bOB" else "\u001b[B"
                KEY_RIGHT -> if (applicationCursorKeys) "\u001bOC" else "\u001b[C"
                KEY_LEFT -> if (applicationCursorKeys) "\u001bOD" else "\u001b[D"
                KEY_HOME -> if (applicationCursorKeys) "\u001bOH" else "\u001b[H"
                KEY_END -> if (applicationCursorKeys) "\u001bOF" else "\u001b[F"
                KEY_PAGEUP -> "\u001b[5~"
                KEY_PAGEDOWN -> "\u001b[6~"
                KEY_INSERT -> "\u001b[2~"
                KEY_DELETE -> "\u001b[3~"
                KEY_ESCAPE -> "\u001b"
                KEY_FUNCTION(1) -> "\u001bOP"
                KEY_FUNCTION(2) -> "\u001bOQ"
                KEY_FUNCTION(3) -> "\u001bOR"
                KEY_FUNCTION(4) -> "\u001bOS"
                KEY_FUNCTION(5) -> "\u001b[15~"
                KEY_FUNCTION(6) -> "\u001b[17~"
                KEY_FUNCTION(7) -> "\u001b[18~"
                KEY_FUNCTION(8) -> "\u001b[19~"
                KEY_FUNCTION(9) -> "\u001b[20~"
                KEY_FUNCTION(10) -> "\u001b[21~"
                KEY_FUNCTION(11) -> "\u001b[23~"
                KEY_FUNCTION(12) -> "\u001b[24~"
                else -> return false
            }
            sendBytes(seq.encodeToByteArray())
            return true
        }
    }

    fun resize(newRows: Int, newCols: Int) {
        synchronized(this) {
            if (newRows == rows && newCols == cols) return
            val oldRows = rows
            val oldCols = cols
            val oldScreen = screen

            maxSeenCols = maxOf(maxSeenCols, oldCols, newCols)

            rows = newRows
            cols = newCols
            scrollTop = 0
            scrollBottom = rows - 1

            screen = Array(rows) { r ->
                if (r < oldRows) {
                    val oldRow = oldScreen[r]
                    if (oldRow.size < maxSeenCols) {
                        Array(maxSeenCols) { c ->
                            if (c < oldRow.size) oldRow[c].copy() else Cell()
                        }
                    } else {
                        oldRow.copyOf()
                    }
                } else {
                    Array(maxSeenCols) { Cell() }
                }
            }

            cursorRow = cursorRow.coerceIn(0, rows - 1)
            cursorCol = cursorCol.coerceIn(0, cols - 1)

            localEchoBoundaryCol = localEchoBoundaryCol.coerceIn(0, cols - 1)
            localEchoBoundaryRow = localEchoBoundaryRow.coerceIn(0, rows - 1)

            tabStops = BooleanArray(cols)
            for (i in 0 until cols step 8) tabStops[i] = true

            _snapshot.value = buildSnapshot()
        }
    }

    fun reset() {
        for (row in 0 until rows) for (col in 0 until cols) screen[row][col] = Cell()
        if (screen.size != rows) {
            for (i in 0 until rows) screen[i] = Array(maxSeenCols) { Cell() }
        }
        cursorRow = 0; cursorCol = 0
        cursorVisible = true; cursorStyle = 0
        scrollTop = 0; scrollBottom = rows - 1
        applicationCursorKeys = false; originMode = false
        autoWrap = true; insertMode = false
        savedRow = 0; savedCol = 0
        resetAttrs()
        tabStops.fill(false)
        for (i in 0 until cols step 8) tabStops[i] = true
        scrollback.clear()
        _snapshot.value = buildSnapshot()
    }

    private fun resetAttrs() {
        bold = false; dim = false; italic = false; underline = 0
        blink = false; reverse = false; conceal = false; strike = false
        fgColor = 0xFFFFFFFFL; bgColor = 0xFF000000L
    }

    private fun processByte(b: Int) {
        when (state) {
            ParserState.GROUND -> processGround(b)
            ParserState.ESCAPE -> processEscape(b)
            ParserState.CSI -> processCsi(b)
            ParserState.OSC -> processOsc(b)
            ParserState.DCS -> processString(b)
            ParserState.PM, ParserState.APC -> processString(b)
            else -> processGround(b)
        }
    }

    private var state = ParserState.GROUND
    private val csiParams = mutableListOf<Int>()
    private var csiParam = 0
    private var csiIntermediate = 0
    private var csiPrivate = ' '
    private val oscBuffer = StringBuilder()
    private var stringDepth = 0

    private fun processGround(b: Int) {
        when (b) {
            0x00 -> {}
            0x07 -> {}
            0x08 -> { if (cursorCol > 0) cursorCol-- }
            0x09 -> {
                var t = cursorCol + 1
                while (t < cols && !tabStops[t]) t++
                if (t < cols) cursorCol = t
            }
            0x0A, 0x0B, 0x0C -> { cursorCol = 0; lineFeed() }
            0x0D -> cursorCol = 0
            0x0E -> {}
            0x0F -> {}
            0x1B -> state = ParserState.ESCAPE
            in 0x20..0x7E -> putChar(b.toChar())
            0x7F -> {}
            else -> putChar(b.toChar())
        }
    }

    private fun processEscape(b: Int) {
        when (b) {
            '['.code -> { state = ParserState.CSI; return }
            ']'.code -> { state = ParserState.OSC; oscBuffer.clear(); return }
            'P'.code -> { state = ParserState.DCS; stringDepth = 0; return }
            '^'.code -> { state = ParserState.PM; return }
            '_'.code -> { state = ParserState.APC; return }
            '7'.code -> { savedRow = cursorRow; savedCol = cursorCol }
            '8'.code -> { cursorRow = savedRow.coerceIn(0, rows - 1); cursorCol = savedCol.coerceIn(0, cols - 1) }
            'c'.code -> reset()
            'D'.code -> lineFeed()
            'E'.code -> { cursorCol = 0; lineFeed() }
            'M'.code -> reverseIndex()
            'H'.code -> { if (cursorCol < cols) tabStops[cursorCol] = true }
            '>'.code -> applicationCursorKeys = false
            '='.code -> applicationCursorKeys = true
            '6'.code -> sendBytes("\u001b[${cursorRow + 1};${cursorCol + 1}R".encodeToByteArray())
            '5'.code -> sendBytes("\u001b[0n".encodeToByteArray())
            ' '.code -> { csiIntermediate = b }
            '#'.code -> { csiIntermediate = b }
            '('.code, ')'.code, '*'.code, '+'.code -> {}
            else -> {}
        }
        state = ParserState.GROUND
    }

    private fun processCsi(b: Int) {
        when {
            b in 0x30..0x39 -> {
                csiParam = csiParam * 10 + (b - 0x30)
            }
            b == ';'.code -> {
                csiParams.add(csiParam)
                csiParam = 0
            }
            b == ':'.code -> {
                csiParams.add(csiParam)
                csiParam = 0
            }
            b in 0x3C..0x3F -> {
                csiPrivate = b.toChar()
            }
            b in 0x20..0x2F -> {
                csiIntermediate = b
            }
            b in 0x40..0x7E -> {
                csiParams.add(csiParam)
                executeCsi(b.toChar())
                state = ParserState.GROUND
                csiParam = 0
                csiIntermediate = 0
                csiPrivate = ' '
                csiParams.clear()
            }
            else -> {
                state = ParserState.GROUND
                csiParam = 0
                csiIntermediate = 0
                csiPrivate = ' '
                csiParams.clear()
            }
        }
    }

    private fun processOsc(b: Int) {
        when {
            b == 0x07 -> {
                executeOsc(oscBuffer.toString())
                state = ParserState.GROUND
            }
            b == 0x1B -> {
                stringDepth = 1
                state = ParserState.STRING_EXIT
            }
            else -> oscBuffer.append(b.toChar())
        }
    }

    private fun processString(b: Int) {
        when {
            b == 0x1B -> stringDepth = 1
            b == '\\'.code && stringDepth == 1 -> {
                state = ParserState.GROUND
                stringDepth = 0
            }
            else -> stringDepth = 0
        }
    }

    private fun executeCsi(ch: Char) {
        val p0 = csiParams.getOrElse(0) { 0 }
        val p1 = csiParams.getOrElse(1) { 0 }
        val p2 = csiParams.getOrElse(2) { 0 }

        when (csiPrivate) {
            '?' -> executePrivateCsi(ch)
            '>' -> {}
            ' ' -> {}
            else -> when (ch) {
                'A' -> cursorUp(if (p0 == 0) 1 else p0)
                'B' -> cursorDown(if (p0 == 0) 1 else p0)
                'C' -> cursorForward(if (p0 == 0) 1 else p0)
                'D' -> cursorBack(if (p0 == 0) 1 else p0)
                'E' -> { cursorDown(if (p0 == 0) 1 else p0); cursorCol = 0 }
                'F' -> { cursorUp(if (p0 == 0) 1 else p0); cursorCol = 0 }
                'G' -> cursorCol = (p0 - 1).coerceIn(0, cols - 1)
                'H', 'f' -> {
                    val r = (p0 - 1).coerceIn(0, rows - 1)
                    val c = (p1 - 1).coerceIn(0, cols - 1)
                    if (originMode) {
                        cursorRow = (scrollTop + r).coerceIn(scrollTop, scrollBottom)
                    } else {
                        cursorRow = r
                    }
                    cursorCol = c
                }
                'J' -> eraseDisplay(p0)
                'K' -> eraseLine(p0)
                'L' -> insertLines(if (p0 == 0) 1 else p0)
                'M' -> deleteLines(if (p0 == 0) 1 else p0)
                'P' -> deleteChars(if (p0 == 0) 1 else p0)
                '@' -> insertChars(if (p0 == 0) 1 else p0)
                'X' -> eraseChars(if (p0 == 0) 1 else p0)
                'd' -> {
                    val r = (p0 - 1).coerceIn(0, rows - 1)
                    cursorRow = if (originMode) (scrollTop + r).coerceIn(scrollTop, scrollBottom) else r
                }
                'e' -> cursorDown(if (p0 == 0) 1 else p0)
                'm' -> executeSgr()
                'r' -> {
                    val top = (p0 - 1).coerceIn(0, rows - 1)
                    val bottom = (p1 - 1).coerceIn(0, rows - 1)
                    scrollTop = minOf(top, bottom)
                    scrollBottom = maxOf(top, bottom)
                    cursorRow = scrollTop
                    cursorCol = 0
                }
                'g' -> {
                    when (p0) {
                        0 -> if (cursorCol < cols) tabStops[cursorCol] = false
                        3 -> tabStops.fill(false)
                    }
                }
                'h' -> setMode(p0)
                'l' -> resetMode(p0)
                'q' -> cursorStyle = p0
                'n' -> {
                    if (p0 == 6) {
                        sendBytes("\u001b[${cursorRow + 1};${cursorCol + 1}R".encodeToByteArray())
                    }
                }
                's' -> { savedRow = cursorRow; savedCol = cursorCol }
                'u' -> { cursorRow = savedRow.coerceIn(0, rows - 1); cursorCol = savedCol.coerceIn(0, cols - 1) }
                't' -> {}
                'b' -> {}
                'S' -> scrollUp(if (p0 == 0) 1 else p0)
                'T' -> scrollDown(if (p0 == 0) 1 else p0)
            }
        }
    }

    private fun executePrivateCsi(ch: Char) {
        val p0 = csiParams.getOrElse(0) { 0 }
        when (ch) {
            'h' -> {
                when (p0) {
                    1 -> applicationCursorKeys = true
                    25 -> cursorVisible = true
                    1047, 1049 -> {}
                    2004 -> {}
                }
            }
            'l' -> {
                when (p0) {
                    1 -> applicationCursorKeys = false
                    25 -> cursorVisible = false
                    1047, 1049 -> {}
                    2004 -> {}
                }
            }
        }
    }

    private fun executeOsc(text: String) {
        val semi = text.indexOf(';')
        if (semi < 0) return
        val cmd = text.substring(0, semi)
        val data = text.substring(semi + 1)
        when (cmd) {
            "0", "2" -> {}
            "4" -> {}
            "10", "11", "12" -> {}
            "52" -> {}
        }
    }

    private fun executeSgr() {
        if (csiParams.isEmpty() || csiParams[0] == 0) {
            resetAttrs()
            return
        }
        var i = 0
        while (i < csiParams.size) {
            val p = csiParams[i]
            when (p) {
                0 -> resetAttrs()
                1 -> bold = true
                2 -> dim = true
                3 -> italic = true
                4 -> underline = if (i + 1 < csiParams.size && csiParams[i + 1] in 0..5) csiParams[++i] else 1
                5 -> blink = true
                6 -> {}
                7 -> reverse = true
                8 -> conceal = true
                9 -> strike = true
                10, 11, 12, 13, 14, 15, 16, 17, 18, 19 -> {}
                20 -> {}
                21 -> bold = false
                22 -> { bold = false; dim = false }
                23 -> italic = false
                24 -> underline = 0
                25 -> blink = false
                26 -> {}
                27 -> reverse = false
                28 -> conceal = false
                29 -> strike = false
                30, 31, 32, 33, 34, 35, 36, 37 -> fgColor = ansiColor4(p - 30, bold)
                38 -> {
                    if (i + 2 < csiParams.size && csiParams[i + 1] == 5) {
                        fgColor = ansiColor8(csiParams[i + 2])
                        i += 2
                    } else if (i + 4 < csiParams.size && csiParams[i + 1] == 2) {
                        val r = csiParams[i + 2].coerceIn(0, 255)
                        val g = csiParams[i + 3].coerceIn(0, 255)
                        val b = csiParams[i + 4].coerceIn(0, 255)
                        fgColor = packColor(r, g, b)
                        i += 4
                    }
                }
                39 -> fgColor = 0xFFFFFFFFL
                40, 41, 42, 43, 44, 45, 46, 47 -> bgColor = ansiColor4(p - 40, false)
                48 -> {
                    if (i + 2 < csiParams.size && csiParams[i + 1] == 5) {
                        bgColor = ansiColor8(csiParams[i + 2])
                        i += 2
                    } else if (i + 4 < csiParams.size && csiParams[i + 1] == 2) {
                        val r = csiParams[i + 2].coerceIn(0, 255)
                        val g = csiParams[i + 3].coerceIn(0, 255)
                        val b = csiParams[i + 4].coerceIn(0, 255)
                        bgColor = packColor(r, g, b)
                        i += 4
                    }
                }
                49 -> bgColor = 0xFF000000L
                90, 91, 92, 93, 94, 95, 96, 97 -> fgColor = ansiColor4Bright(p - 90)
                100, 101, 102, 103, 104, 105, 106, 107 -> bgColor = ansiColor4Bright(p - 100)
            }
            i++
        }
    }

    private fun putChar(ch: Char) {
        if (cursorRow >= rows || cursorCol >= cols) return
        val charWidth = if (isCjk(ch)) 2 else 1
        if (cursorCol + charWidth > cols && autoWrap) {
            cursorCol = 0
            lineFeed()
            if (cursorRow >= rows) return
        }
        val cell = screen[cursorRow][cursorCol]
        cell.char = ch
        cell.fgColor = fgColor
        cell.bgColor = bgColor
        cell.bold = bold
        cell.dim = dim
        cell.italic = italic
        cell.underline = underline
        cell.blink = blink
        cell.reverse = reverse
        cell.conceal = conceal
        cell.strike = strike
        cell.width = charWidth
        cursorCol += charWidth
        if (cursorCol >= cols && autoWrap) {
            cursorCol = 0
            lineFeed()
        }
        cursorCol = cursorCol.coerceAtMost(cols - 1)
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollUp(1)
        } else {
            cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) {
            scrollDown(1)
        } else {
            cursorRow = (cursorRow - 1).coerceAtLeast(0)
        }
    }

    private fun scrollUp(n: Int) {
        val count = n.coerceIn(1, scrollBottom - scrollTop + 1)
        for (i in 0 until count) {
            scrollback.addLast(screen[scrollTop + i].copyOf())
        }
        if (scrollback.size > scrollbackSize) {
            repeat(scrollback.size - scrollbackSize) { scrollback.removeFirst() }
        }
        for (r in scrollTop until scrollBottom - count + 1) {
            screen[r] = screen[r + count].copyOf()
        }
        for (r in (scrollBottom - count + 1)..scrollBottom) {
            screen[r] = Array(maxSeenCols) { Cell() }
        }
    }

    private fun scrollDown(n: Int) {
        val count = n.coerceIn(1, scrollBottom - scrollTop + 1)
        for (r in scrollBottom downTo scrollTop + count) {
            screen[r] = screen[r - count].copyOf()
        }
        for (r in scrollTop until scrollTop + count) {
            screen[r] = Array(maxSeenCols) { Cell() }
        }
    }

    private fun cursorUp(n: Int) {
        cursorRow = (cursorRow - n.coerceAtLeast(1)).coerceAtLeast(if (originMode) scrollTop else 0)
    }

    private fun cursorDown(n: Int) {
        cursorRow = (cursorRow + n.coerceAtLeast(1)).coerceAtMost(if (originMode) scrollBottom else rows - 1)
    }

    private fun cursorForward(n: Int) {
        cursorCol = (cursorCol + n.coerceAtLeast(1)).coerceAtMost(cols - 1)
    }

    private fun cursorBack(n: Int) {
        cursorCol = (cursorCol - n.coerceAtLeast(1)).coerceAtLeast(0)
    }

    private fun eraseDisplay(param: Int) {
        Log.i(TAG, "eraseDisplay param=$param rows=$rows cols=$cols")
        when (param) {
            0 -> eraseRange(cursorRow, cursorCol, rows - 1, cols - 1)
            1 -> eraseRange(0, 0, cursorRow, cursorCol)
            2 -> {
                eraseRange(0, 0, rows - 1, cols - 1)
                cursorRow = 0
                cursorCol = 0
                _snapshot.value = buildSnapshot()
                Log.i(TAG, "eraseDisplay(2) done, snapshot emitted immediately")
            }
            3 -> {
                eraseRange(0, 0, rows - 1, cols - 1)
                cursorRow = 0
                cursorCol = 0
                scrollback.clear()
                _snapshot.value = buildSnapshot()
                Log.i(TAG, "eraseDisplay(3) done, scrollback cleared, snapshot emitted")
            }
        }
    }

    private fun eraseLine(param: Int) {
        when (param) {
            0 -> eraseRange(cursorRow, cursorCol, cursorRow, cols - 1)
            1 -> eraseRange(cursorRow, 0, cursorRow, cursorCol)
            2 -> eraseRange(cursorRow, 0, cursorRow, cols - 1)
        }
    }

    private fun eraseRange(r1: Int, c1: Int, r2: Int, c2: Int) {
        for (r in r1..r2) for (c in c1..c2) {
            if (r < rows && c < cols) screen[r][c] = Cell()
        }
    }

    private fun insertLines(n: Int) {
        val count = n.coerceIn(1, scrollBottom - cursorRow + 1)
        for (r in scrollBottom downTo cursorRow + count) {
            screen[r] = screen[r - count].copyOf()
        }
        for (r in cursorRow until cursorRow + count) {
            screen[r] = Array(maxSeenCols) { Cell() }
        }
    }

    private fun deleteLines(n: Int) {
        val count = n.coerceIn(1, scrollBottom - cursorRow + 1)
        for (r in cursorRow..scrollBottom - count) {
            screen[r] = screen[r + count].copyOf()
        }
        for (r in (scrollBottom - count + 1)..scrollBottom) {
            screen[r] = Array(maxSeenCols) { Cell() }
        }
    }

    private fun deleteChars(n: Int) {
        val count = n.coerceIn(1, cols - cursorCol)
        for (r in cursorRow..cursorRow) {
            for (c in cursorCol until cols - count) {
                screen[r][c] = screen[r][c + count].copy()
            }
            for (c in (cols - count) until cols) {
                screen[r][c] = Cell()
            }
        }
    }

    private fun insertChars(n: Int) {
        val count = n.coerceIn(1, cols - cursorCol)
        for (c in cols - 1 downTo cursorCol + count) {
            screen[cursorRow][c] = screen[cursorRow][c - count].copy()
        }
        for (c in cursorCol until cursorCol + count) {
            screen[cursorRow][c] = Cell()
        }
    }

    private fun eraseChars(n: Int) {
        val count = n.coerceIn(1, cols - cursorCol)
        for (c in cursorCol until cursorCol + count) {
            screen[cursorRow][c] = Cell()
        }
    }

    private fun setMode(p: Int) {
        when (p) {
            4 -> insertMode = true
            20 -> {}
        }
    }

    private fun resetMode(p: Int) {
        when (p) {
            4 -> insertMode = false
            20 -> {}
        }
    }

    private fun sendByte(b: Byte) {
        callbacks.onKeyboardOutput(byteArrayOf(b))
    }

    private fun sendBytes(data: ByteArray) {
        callbacks.onKeyboardOutput(data)
    }

    private fun dimColor(color: Long): Long {
        val r = ((color shr 16) and 0xFF).toInt() / 2
        val g = ((color shr 8) and 0xFF).toInt() / 2
        val b = (color and 0xFF).toInt() / 2
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    private fun buildSnapshot(): TerminalSnapshot {
        val lines = List(rows) { r ->
            TerminalLine(
                cells = List(cols) { c ->
                    val cell = screen[r][c]
                    TerminalCell(
                        char = cell.char,
                        fgColor = when {
                            cell.conceal -> cell.bgColor
                            cell.dim -> dimColor(cell.fgColor)
                            else -> cell.fgColor
                        },
                        bgColor = cell.bgColor,
                        bold = cell.bold,
                        italic = cell.italic,
                        underline = cell.underline,
                        reverse = cell.reverse,
                        strike = cell.strike,
                        width = cell.width,
                        dim = cell.dim
                    )
                }
            )
        }
        return TerminalSnapshot(
            lines = lines,
            cursorRow = cursorRow.coerceIn(0, rows - 1),
            cursorCol = cursorCol.coerceIn(0, cols - 1),
            cursorVisible = cursorVisible,
            rows = rows,
            cols = cols
        )
    }

    private fun emitSnapshot() {
        _snapshot.value = buildSnapshot()
    }

    fun setScrollbackSize(n: Int) { scrollbackSize = n }

    fun paste(text: String) {
        if (text.isEmpty()) return
        callbacks.onKeyboardOutput(text.encodeToByteArray())
    }

    fun getScreenText(startRow: Int, startCol: Int, endRow: Int, endCol: Int): String {
        val sb = StringBuilder()
        val r1 = minOf(startRow, endRow).coerceIn(0, rows - 1)
        val r2 = maxOf(startRow, endRow).coerceIn(0, rows - 1)
        val c1 = if (startRow <= endRow) startCol.coerceIn(0, cols - 1) else endCol.coerceIn(0, cols - 1)
        val c2 = if (startRow <= endRow) endCol.coerceIn(0, cols - 1) else startCol.coerceIn(0, cols - 1)
        for (r in r1..r2) {
            val startC = if (r == r1) c1 else 0
            val endC = if (r == r2) c2 else cols - 1
            for (c in startC..endC) {
                sb.append(screen[r][c].char)
            }
            if (r < r2) sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    fun getWordBoundary(row: Int, col: Int): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val r = row.coerceIn(0, rows - 1)
        val c = col.coerceIn(0, cols - 1)
        if (screen[r][c].char == ' ') return Pair(Pair(r, c), Pair(r, c))
        var startCol = c
        while (startCol > 0 && screen[r][startCol - 1].char != ' ') startCol--
        var endCol = c
        while (endCol < cols - 1 && screen[r][endCol + 1].char != ' ') endCol++
        return Pair(Pair(r, startCol), Pair(r, endCol))
    }



    fun captureCheckpoint(): CheckpointState {
        return CheckpointState(
            rows = rows, cols = cols,
            cursorRow = cursorRow, cursorCol = cursorCol,
            cursorVisible = cursorVisible, cursorStyle = cursorStyle,
            scrollTop = scrollTop, scrollBottom = scrollBottom,
            savedRow = savedRow, savedCol = savedCol,
            applicationCursorKeys = applicationCursorKeys,
            originMode = originMode, autoWrap = autoWrap, insertMode = insertMode,
            bold = bold, dim = dim, italic = italic, underline = underline,
            blink = blink, reverse = reverse, conceal = conceal, strike = strike,
            fgColor = fgColor, bgColor = bgColor,
            screen = screen.map { row -> row.map { it.toCellState() } },
            scrollback = scrollback.map { row -> row.map { it.toCellState() } }
        )
    }

    fun restoreFromCheckpoint(state: CheckpointState) {
        rows = state.rows
        cols = state.cols
        screen = Array(rows) { r ->
            Array(cols) { c ->
                if (r < state.screen.size && c < state.screen[r].size) {
                    state.screen[r][c].toCell()
                } else Cell()
            }
        }
        scrollback.clear()
        for (row in state.scrollback) {
            scrollback.addLast(Array(cols) { c ->
                if (c < row.size) row[c].toCell() else Cell()
            })
        }
        cursorRow = state.cursorRow.coerceIn(0, rows - 1)
        cursorCol = state.cursorCol.coerceIn(0, cols - 1)
        cursorVisible = state.cursorVisible
        cursorStyle = state.cursorStyle
        scrollTop = state.scrollTop.coerceIn(0, rows - 1)
        scrollBottom = state.scrollBottom.coerceIn(0, rows - 1)
        savedRow = state.savedRow.coerceIn(0, rows - 1)
        savedCol = state.savedCol.coerceIn(0, cols - 1)
        applicationCursorKeys = state.applicationCursorKeys
        originMode = state.originMode
        autoWrap = state.autoWrap
        insertMode = state.insertMode
        bold = state.bold; dim = state.dim; italic = state.italic
        underline = state.underline; blink = state.blink
        reverse = state.reverse; conceal = state.conceal; strike = state.strike
        fgColor = state.fgColor; bgColor = state.bgColor
        tabStops = BooleanArray(cols)
        for (i in 0 until cols step 8) tabStops[i] = true
        _snapshot.value = buildSnapshot()
    }

    private fun Cell.toCellState() = CellState(
        char = char, fgColor = fgColor, bgColor = bgColor,
        bold = bold, dim = dim, italic = italic, underline = underline,
        blink = blink, reverse = reverse, conceal = conceal, strike = strike,
        width = width
    )

    private fun CellState.toCell() = Cell().also {
        it.char = char; it.fgColor = fgColor; it.bgColor = bgColor
        it.bold = bold; it.dim = dim; it.italic = italic
        it.underline = underline; it.blink = blink; it.reverse = reverse
        it.conceal = conceal; it.strike = strike; it.width = width
    }

    private enum class ParserState {
        GROUND, ESCAPE, CSI, OSC, DCS, PM, APC, STRING_EXIT
    }

    private class Cell {
        var char: Char = ' '
        var fgColor: Long = 0xFFFFFFFFL
        var bgColor: Long = 0xFF000000L
        var bold: Boolean = false
        var dim: Boolean = false
        var italic: Boolean = false
        var underline: Int = 0
        var blink: Boolean = false
        var reverse: Boolean = false
        var conceal: Boolean = false
        var strike: Boolean = false
        var width: Int = 1

        fun copy() = Cell().also {
            it.char = char; it.fgColor = fgColor; it.bgColor = bgColor
            it.bold = bold; it.dim = dim; it.italic = italic
            it.underline = underline; it.blink = blink; it.reverse = reverse
            it.conceal = conceal; it.strike = strike; it.width = width
        }
    }

    private fun isCjk(ch: Char): Boolean {
        val cp = ch.code
        return cp in 0x1100..0x11FF ||   // Hangul Jamo
                cp in 0x2E80..0x2FFF ||   // CJK Radicals
                cp in 0x3000..0x303F ||   // CJK Symbols and Punctuation
                cp in 0x3040..0x309F ||   // Hiragana
                cp in 0x30A0..0x30FF ||   // Katakana
                cp in 0x3100..0x312F ||   // Bopomofo
                cp in 0x3130..0x318F ||   // Hangul Compatibility Jamo
                cp in 0x3190..0x31FF ||   // CJK Strokes
                cp in 0x3200..0x32FF ||   // Enclosed CJK
                cp in 0x3400..0x4DBF ||   // CJK Unified Extension A
                cp in 0x4E00..0x9FFF ||   // CJK Unified
                cp in 0xA000..0xA4CF ||   // Yi
                cp in 0xAC00..0xD7AF ||   // Hangul Syllables
                cp in 0xF900..0xFAFF ||   // CJK Compatibility Ideographs
                cp in 0xFE30..0xFE4F ||   // CJK Compatibility Forms
                cp in 0xFF01..0xFF60 ||   // Fullwidth Forms
                cp in 0xFFE0..0xFFE6 ||   // Fullwidth Signs
                cp in 0x1F000..0x1F02F || // Mahjong
                cp in 0x1F030..0x1F09F || // Domino
                cp in 0x20000..0x2FA1F || // CJK Extension B, C, D, E, F
                cp in 0x30000..0x3134F    // CJK Extension G, H
    }

    companion object {
        private const val TAG = "DevTerm.TermEmu"
        const val MOD_NONE = 0
        const val MOD_SHIFT = 1
        const val MOD_ALT = 2
        const val MOD_CTRL = 4

        const val KEY_ENTER = 13
        const val KEY_TAB = 9
        const val KEY_BACKSPACE = 127
        const val KEY_ESCAPE = 27
        const val KEY_UP = 0x100 + 65
        const val KEY_DOWN = 0x100 + 66
        const val KEY_LEFT = 0x100 + 67
        const val KEY_RIGHT = 0x100 + 68
        const val KEY_HOME = 0x100 + 72
        const val KEY_END = 0x100 + 73
        const val KEY_PAGEUP = 0x100 + 74
        const val KEY_PAGEDOWN = 0x100 + 75
        const val KEY_DELETE = 0x100 + 76
        const val KEY_INSERT = 0x100 + 77

        fun KEY_FUNCTION(n: Int) = 0x100 + 100 + n

        private fun packColor(r: Int, g: Int, b: Int): Long {
            return (0xFFL shl 24) or ((r.toLong() and 0xFF) shl 16) or
                    ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
        }

        private val ansiColors4 = arrayOf(
            0x000000, 0xCC0000, 0x00CC00, 0xCCCC00,
            0x0000CC, 0xCC00CC, 0x00CCCC, 0xCCCCCC
        )
        private val ansiColors4Bright = arrayOf(
            0x666666, 0xFF0000, 0x00FF00, 0xFFFF00,
            0x0000FF, 0xFF00FF, 0x00FFFF, 0xFFFFFF
        )
        private val ansiColors8 = Array(256) { i ->
            if (i < 16) {
                val c = if (i < 8) ansiColors4[i] else ansiColors4Bright[i - 8]
                packColor((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            } else if (i < 232) {
                val idx = i - 16
                val r = (idx / 36) * 42 + 55
                val g = ((idx % 36) / 6) * 42 + 55
                val b = (idx % 6) * 42 + 55
                packColor(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
            } else {
                val grey = (i - 232) * 10 + 8
                packColor(grey, grey, grey)
            }
        }

        private fun ansiColor4(index: Int, bold: Boolean): Long {
            val c = ansiColors4[index.coerceIn(0, 7)]
            val r = ((c shr 16) and 0xFF).coerceAtMost(if (bold) 255 else 192)
            val g = ((c shr 8) and 0xFF).coerceAtMost(if (bold) 255 else 192)
            val b = (c and 0xFF).coerceAtMost(if (bold) 255 else 192)
            return packColor(r, g, b)
        }

        private fun ansiColor4Bright(index: Int): Long {
            val c = ansiColors4Bright[index.coerceIn(0, 7)]
            return packColor((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }

        private fun ansiColor8(index: Int): Long {
            return ansiColors8[index.coerceIn(0, 255)]
        }

        private fun encodeUtf8(codepoint: Int): ByteArray {
            return when {
                codepoint < 0x80 -> byteArrayOf(codepoint.toByte())
                codepoint < 0x800 -> byteArrayOf(
                    (0xC0 or (codepoint shr 6)).toByte(),
                    (0x80 or (codepoint and 0x3F)).toByte()
                )
                codepoint < 0x10000 -> byteArrayOf(
                    (0xE0 or (codepoint shr 12)).toByte(),
                    (0x80 or ((codepoint shr 6) and 0x3F)).toByte(),
                    (0x80 or (codepoint and 0x3F)).toByte()
                )
                else -> byteArrayOf(
                    (0xF0 or (codepoint shr 18)).toByte(),
                    (0x80 or ((codepoint shr 12) and 0x3F)).toByte(),
                    (0x80 or ((codepoint shr 6) and 0x3F)).toByte(),
                    (0x80 or (codepoint and 0x3F)).toByte()
                )
            }
        }
    }
}
