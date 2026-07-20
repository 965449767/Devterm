package com.devterm.terminal

import android.view.KeyEvent

class KeyboardHandler(private val terminalEmulator: TerminalEmulator) {

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val keyCode = event.keyCode
        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed

        val modifiers = buildModifiers(ctrl, alt)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_RIGHT)
            KeyEvent.KEYCODE_DEL -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_BACKSPACE)
            KeyEvent.KEYCODE_FORWARD_DEL -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_DELETE)
            KeyEvent.KEYCODE_ENTER -> terminalEmulator.dispatchCharacter(modifiers, 0x0D)
            KeyEvent.KEYCODE_TAB -> terminalEmulator.dispatchCharacter(modifiers, 0x09)
            KeyEvent.KEYCODE_ESCAPE -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_ESCAPE)
            KeyEvent.KEYCODE_MOVE_HOME -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_HOME)
            KeyEvent.KEYCODE_MOVE_END -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_END)
            KeyEvent.KEYCODE_PAGE_UP -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_PAGEUP)
            KeyEvent.KEYCODE_PAGE_DOWN -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_PAGEDOWN)

            KeyEvent.KEYCODE_F1 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(1))
            KeyEvent.KEYCODE_F2 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(2))
            KeyEvent.KEYCODE_F3 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(3))
            KeyEvent.KEYCODE_F4 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(4))
            KeyEvent.KEYCODE_F5 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(5))
            KeyEvent.KEYCODE_F6 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(6))
            KeyEvent.KEYCODE_F7 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(7))
            KeyEvent.KEYCODE_F8 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(8))
            KeyEvent.KEYCODE_F9 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(9))
            KeyEvent.KEYCODE_F10 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(10))
            KeyEvent.KEYCODE_F11 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(11))
            KeyEvent.KEYCODE_F12 -> terminalEmulator.dispatchKey(modifiers, TerminalEmulator.KEY_FUNCTION(12))

            else -> {
                val char = event.getUnicodeChar(event.metaState)
                if (char > 0) {
                    terminalEmulator.dispatchCharacter(modifiers, char)
                    true
                } else false
            }
        }
    }

    private fun buildModifiers(ctrl: Boolean, alt: Boolean): Int {
        var mod = TerminalEmulator.MOD_NONE
        if (ctrl) mod = mod or TerminalEmulator.MOD_CTRL
        if (alt) mod = mod or TerminalEmulator.MOD_ALT
        return mod
    }
}
