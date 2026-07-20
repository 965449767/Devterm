package com.devterm.terminal.core.parser

class VtParser {

    private var state: State = State.GROUND
    private val params = mutableListOf<Int>()
    private var currentParam = 0
    private val intermediates = StringBuilder()
    private var privateMarker: Char = '\u0000'

    private val commandQueue = mutableListOf<ScreenCommand>()
    private var oscString = StringBuilder()

    fun reset() {
        state = State.GROUND
        params.clear()
        currentParam = 0
        intermediates.clear()
        privateMarker = '\u0000'
        commandQueue.clear()
        oscString = StringBuilder()
    }

    fun consume(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): List<ScreenCommand> {
        commandQueue.clear()
        val end = offset + length
        for (i in offset until end) {
            val b = data[i].toInt() and 0xFF
            consumeByte(b)
        }
        return commandQueue.toList()
    }

    fun consume(data: ByteArray): List<ScreenCommand> = consume(data, 0, data.size)

    private fun consumeByte(b: Int) {
        when (state) {
            State.GROUND -> handleGround(b)
            State.ESC -> handleEsc(b)
            State.CSI_PARAM -> handleCsiParam(b)
            State.CSI_INTERMEDIATE -> handleCsiIntermediate(b)
            State.CSI_IGNORE -> {}
            State.OSC_STRING -> handleOsc(b)
            State.DCS_ENTRY -> handleDcsEntry(b)
            State.SOS_PM_APC_STRING -> {}
        }
    }

    private fun handleGround(b: Int) {
        when {
            b == 0x1B -> state = State.ESC
            b == 0x9B -> state = State.CSI_PARAM // 8-bit CSI
            b == 0x9D -> { state = State.OSC_STRING; oscString = StringBuilder() }
            b == 0x90 -> state = State.DCS_ENTRY
            b == 0x98 || b == 0x9E || b == 0x9F -> state = State.SOS_PM_APC_STRING
            b == 0x0A -> { commandQueue.add(ScreenCommand.CarriageReturn); commandQueue.add(ScreenCommand.LineFeed) }
            b == 0x0D -> commandQueue.add(ScreenCommand.CarriageReturn)
            b == 0x07 -> commandQueue.add(ScreenCommand.Bell(1))
            b == 0x08 -> commandQueue.add(ScreenCommand.CursorBack(1))
            b == 0x09 -> {
                val tabStop = 8
                val nextTab = ((cursorCol / tabStop) + 1) * tabStop
                commandQueue.add(ScreenCommand.CursorForward(nextTab - cursorCol))
            }
            b == 0x0B || b == 0x0C -> { commandQueue.add(ScreenCommand.CarriageReturn); commandQueue.add(ScreenCommand.LineFeed) }
            b in 0x20..0x7E -> {
                val c = b.toChar()
                commandQueue.add(ScreenCommand.WriteGlyph(c, 1))
            }
            b in 0x80..0x8F -> {}
            b in 0x91..0x97 -> {}
            b in 0xA0..0xFF -> {
                val c = b.toChar()
                commandQueue.add(ScreenCommand.WriteGlyph(c, 1))
            }
        }
    }

    private var cursorCol = 0
    private var cursorRow = 0

    private fun handleEsc(b: Int) {
        state = State.GROUND
        when (b) {
            '['.code -> state = State.CSI_PARAM
            ']'.code -> { state = State.OSC_STRING; oscString = StringBuilder() }
            'P'.code -> state = State.DCS_ENTRY
            '\\'.code -> {} // ST
            'D'.code -> commandQueue.add(ScreenCommand.LineFeed) // IND
            'M'.code -> commandQueue.add(ScreenCommand.ReverseLineFeed) // RI
            'E'.code -> { commandQueue.add(ScreenCommand.CarriageReturn); commandQueue.add(ScreenCommand.LineFeed) } // NEL
            'H'.code -> commandQueue.add(ScreenCommand.SetCursorCol(0)) // HTS
            '7'.code -> commandQueue.add(ScreenCommand.SaveCursor) // DECSC
            '8'.code -> commandQueue.add(ScreenCommand.RestoreCursor) // DECRC
            'F'.code -> commandQueue.add(ScreenCommand.WriteGlyph('\\', 1)) // SGR replacement
            'c'.code -> commandQueue.add(ScreenCommand.Reset) // RIS
            in 0x20..0x2F -> intermediates.append(b.toChar()) // intermediate bytes
        }
        if (b == 0x1B) state = State.ESC
    }

    private fun handleCsiParam(b: Int) {
        when {
            b in 0x30..0x39 -> { // digit 0-9
                currentParam = currentParam * 10 + (b - 0x30)
            }
            b == 0x3A -> { // colon separator (used in some extended sequences)
                params.add(currentParam)
                currentParam = 0
            }
            b == 0x3B -> { // semicolon
                params.add(currentParam)
                currentParam = 0
            }
            b in 0x3C..0x3F -> { // private marker < = > ?
                privateMarker = b.toChar()
            }
            b in 0x20..0x2F -> { // intermediate bytes
                intermediates.append(b.toChar())
                state = State.CSI_INTERMEDIATE
            }
            b in 0x40..0x7E -> { // final byte
                params.add(currentParam)
                dispatchCsi(b.toChar())
                state = State.GROUND
            }
            b == 0x1B -> state = State.ESC
            else -> state = State.CSI_IGNORE
        }
    }

    private fun handleCsiIntermediate(b: Int) {
        when {
            b in 0x20..0x2F -> intermediates.append(b.toChar())
            b in 0x40..0x7E -> {
                params.add(currentParam)
                dispatchCsi(b.toChar())
                state = State.GROUND
            }
            b == 0x1B -> state = State.ESC
            else -> state = State.CSI_IGNORE
        }
    }

    private fun dispatchCsi(finalChar: Char) {
        val p = if (params.isEmpty()) listOf(0) else params.toList()
        val p0 = p.getOrElse(0) { 0 }

        when {
            finalChar == 'A' -> commandQueue.add(ScreenCommand.CursorUp(p0.coerceAtLeast(1)))
            finalChar == 'B' -> commandQueue.add(ScreenCommand.CursorDown(p0.coerceAtLeast(1)))
            finalChar == 'C' -> commandQueue.add(ScreenCommand.CursorForward(p0.coerceAtLeast(1)))
            finalChar == 'D' -> commandQueue.add(ScreenCommand.CursorBack(p0.coerceAtLeast(1)))
            finalChar == 'E' -> commandQueue.add(ScreenCommand.CursorDown(p0.coerceAtLeast(1)))
            finalChar == 'F' -> commandQueue.add(ScreenCommand.CursorUp(p0.coerceAtLeast(1)))
            finalChar == 'G' -> commandQueue.add(ScreenCommand.SetCursorCol(p0.coerceAtLeast(1) - 1))
            finalChar == 'H' || finalChar == 'f' -> {
                val row = p.getOrElse(0) { 1 }.coerceAtLeast(1) - 1
                val col = p.getOrElse(1) { 1 }.coerceAtLeast(1) - 1
                commandQueue.add(ScreenCommand.MoveCursor(row, col))
            }
            finalChar == 'J' -> commandQueue.add(ScreenCommand.EraseDisplay(p0))
            finalChar == 'K' -> commandQueue.add(ScreenCommand.EraseLine(p0))
            finalChar == 'L' -> commandQueue.add(ScreenCommand.InsertLines(p0.coerceAtLeast(1)))
            finalChar == 'M' -> commandQueue.add(ScreenCommand.DeleteLines(p0.coerceAtLeast(1)))
            finalChar == 'P' -> commandQueue.add(ScreenCommand.DeleteChars(p0.coerceAtLeast(1)))
            finalChar == '@' -> commandQueue.add(ScreenCommand.InsertChars(p0.coerceAtLeast(1)))
            finalChar == 'X' -> commandQueue.add(ScreenCommand.EraseLine(0)) // Erase Characters
            finalChar == 'S' -> commandQueue.add(ScreenCommand.ScrollUp)
            finalChar == 'T' -> commandQueue.add(ScreenCommand.ScrollDown)
            finalChar == 'd' -> {
                val row = p0.coerceAtLeast(1) - 1
                commandQueue.add(ScreenCommand.MoveCursor(row, cursorCol))
            }
            finalChar == 'm' -> commandQueue.add(ScreenCommand.SetSgr(p))
            finalChar == 'r' -> {
                val top = p.getOrElse(0) { 1 } - 1
                val bottom = p.getOrElse(1) { 0 }
                commandQueue.add(ScreenCommand.SetScrollRegion(top, bottom))
            }
            finalChar == 'h' -> {
                val mode = if (privateMarker == '?' && p0 > 0) p0 else p0
                commandQueue.add(ScreenCommand.SetMode(mode, true))
            }
            finalChar == 'l' -> {
                val mode = if (privateMarker == '?' && p0 > 0) p0 else p0
                commandQueue.add(ScreenCommand.SetMode(mode, false))
            }
            finalChar == 's' -> commandQueue.add(ScreenCommand.SaveCursor)
            finalChar == 'u' -> commandQueue.add(ScreenCommand.RestoreCursor)
            finalChar == 'n' -> {
                when (p0) {
                    5 -> commandQueue.add(ScreenCommand.DeviceStatusReport(5))
                    6 -> commandQueue.add(ScreenCommand.RequestCursorPosition)
                }
            }
            finalChar == 'c' -> { /* DA - Device Attributes, ignore */ }
        }

        params.clear()
        currentParam = 0
        intermediates.clear()
        privateMarker = '\u0000'
    }

    private fun handleOsc(b: Int) {
        when {
            b == 0x1B -> state = State.ESC // may be followed by \
            b == 0x07 -> { // ST via BEL
                dispatchOsc(oscString.toString())
                state = State.GROUND
            }
            b == 0x9C -> { // 8-bit ST
                dispatchOsc(oscString.toString())
                state = State.GROUND
            }
            else -> oscString.append(b.toChar())
        }
        if (state == State.ESC) {
            // If we get ESC in OSC, the next byte should be \ (ST)
            // For now, just dispatch on ESC
            dispatchOsc(oscString.toString())
            state = State.GROUND
        }
    }

    private fun handleDcsEntry(b: Int) {
        // DCS passthrough — just consume until ST
        when (b) {
            0x1B -> state = State.ESC
            0x9C -> state = State.GROUND
        }
    }

    private fun dispatchOsc(s: String) {
        val semicolon = s.indexOf(';')
        val oscNum = if (semicolon > 0) {
            s.substring(0, semicolon).toIntOrNull() ?: 0
        } else {
            s.toIntOrNull() ?: 0
        }
        val value = if (semicolon > 0) s.substring(semicolon + 1) else ""
        when (oscNum) {
            0 -> commandQueue.add(ScreenCommand.SetTitle(value))
            1 -> commandQueue.add(ScreenCommand.SetIconName(value))
            2 -> commandQueue.add(ScreenCommand.SetTitle(value))
        }
    }

    private enum class State {
        GROUND, ESC, CSI_PARAM, CSI_INTERMEDIATE, CSI_IGNORE,
        OSC_STRING, DCS_ENTRY, SOS_PM_APC_STRING
    }
}
