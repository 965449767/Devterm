package com.devterm.terminal

import android.view.KeyEvent
import com.devterm.terminal.core.parser.ScreenCommand

class KeyboardHandlerNew(private val core: DevTermCore) {

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed

        val seq = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
            KeyEvent.KEYCODE_DEL -> "\u001b\u007F"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
            KeyEvent.KEYCODE_F1 -> "\u001bOP"
            KeyEvent.KEYCODE_F2 -> "\u001bOQ"
            KeyEvent.KEYCODE_F3 -> "\u001bOR"
            KeyEvent.KEYCODE_F4 -> "\u001bOS"
            KeyEvent.KEYCODE_F5 -> "\u001b[15~"
            KeyEvent.KEYCODE_F6 -> "\u001b[17~"
            KeyEvent.KEYCODE_F7 -> "\u001b[18~"
            KeyEvent.KEYCODE_F8 -> "\u001b[19~"
            KeyEvent.KEYCODE_F9 -> "\u001b[20~"
            KeyEvent.KEYCODE_F10 -> "\u001b[21~"
            KeyEvent.KEYCODE_F11 -> "\u001b[23~"
            KeyEvent.KEYCODE_F12 -> "\u001b[24~"
            else -> null
        }

        if (seq != null) {
            if (ctrl) {
                // Ctrl+key: send raw control byte
                val c = seq.firstOrNull()
                if (c != null && c in 'a'..'z') {
                    core.write(byteArrayOf((c.code - 'a'.code + 1).toByte()))
                    return true
                }
            }
            core.write(seq.encodeToByteArray())
            return true
        }

        val char = event.getUnicodeChar(event.metaState)
        if (char > 0) {
            val ch = char.toChar()
            if (ctrl && ch in 'a'..'z') {
                core.write(byteArrayOf((char - 'a'.code + 1).toByte()))
            } else if (ctrl && ch in 'A'..'Z') {
                core.write(byteArrayOf((char - 'A'.code + 1).toByte()))
            } else {
                core.write(String(Character.toChars(char)).encodeToByteArray())
            }
            return true
        }

        return false
    }

    fun commitText(text: String) {
        core.write(text)
    }

    fun deleteSurrounding(before: Int, after: Int) {
        repeat(before.coerceIn(0, 100)) {
            core.write("\u001b\u007F".encodeToByteArray())
        }
        repeat(after.coerceIn(0, 100)) {
            core.write("\u001b[3~".encodeToByteArray())
        }
    }
}
