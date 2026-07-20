package com.devterm.terminal

import android.view.KeyEvent
import com.devterm.terminal.core.backend.BackendCapabilities

/**
 * 键盘事件处理器。
 *
 * 职责：
 * 1. 把 Android KeyEvent 转换为终端转义序列
 * 2. 处理 Ctrl 组合键 → 发送控制字符（\x01-\x1A）
 * 3. 根据 Backend 能力调整行为（如 PTY 模式下不需要 localEcho）
 *
 * 常见控制字符：
 * - Ctrl+C → \x03 (ETX, 中断)
 * - Ctrl+D → \x04 (EOT, 文件结束)
 * - Ctrl+Z → \x1A (SUB, 挂起)
 * - Ctrl+A → \x01 (SOH, 行首)
 * - Ctrl+E → \x05 (ENQ, 行尾)
 * - Ctrl+K → \x0B (VT, 删到行尾)
 * - Ctrl+L → \x0C (FF, 清屏)
 * - Ctrl+U → \x15 (NAK, 删到行首)
 * - Ctrl+W → \x17 (ETB, 删一个词)
 */
class KeyboardHandlerNew(
    private val core: DevTermCore,
    private val capabilities: BackendCapabilities = BackendCapabilities.PIPE
) {

    /**
     * 当前 Backend 是否需要 App 层本地回显。
     * PTY 模式下 shell 会自行回显，返回 false；管道模式返回 true。
     */
    val needsLocalEcho: Boolean get() = capabilities.needsLocalEcho

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val ctrl = event.isCtrlPressed

        // 优先处理 Ctrl+字母（控制字符）
        if (ctrl) {
            val ctrlChar = mapCtrlKey(event.keyCode)
            if (ctrlChar != null) {
                core.write(byteArrayOf(ctrlChar))
                return true
            }
        }

        // 处理特殊功能键（方向键、F1-F12 等）
        val seq = mapFunctionKey(event.keyCode)
        if (seq != null) {
            core.write(seq.encodeToByteArray())
            return true
        }

        // 处理普通字符输入
        val char = event.getUnicodeChar(event.metaState)
        if (char > 0) {
            core.write(String(Character.toChars(char)).encodeToByteArray())
            return true
        }

        return false
    }

    /**
     * 映射 Ctrl+字母 → 控制字符。
     * Ctrl+A → \x01, Ctrl+B → \x02, ..., Ctrl+Z → \x1A
     */
    private fun mapCtrlKey(keyCode: Int): Byte? {
        val c = when (keyCode) {
            KeyEvent.KEYCODE_A -> 'a'
            KeyEvent.KEYCODE_B -> 'b'
            KeyEvent.KEYCODE_C -> 'c'
            KeyEvent.KEYCODE_D -> 'd'
            KeyEvent.KEYCODE_E -> 'e'
            KeyEvent.KEYCODE_F -> 'f'
            KeyEvent.KEYCODE_G -> 'g'
            KeyEvent.KEYCODE_H -> 'h'
            KeyEvent.KEYCODE_I -> 'i'
            KeyEvent.KEYCODE_J -> 'j'
            KeyEvent.KEYCODE_K -> 'k'
            KeyEvent.KEYCODE_L -> 'l'
            KeyEvent.KEYCODE_M -> 'm'
            KeyEvent.KEYCODE_N -> 'n'
            KeyEvent.KEYCODE_O -> 'o'
            KeyEvent.KEYCODE_P -> 'p'
            KeyEvent.KEYCODE_Q -> 'q'
            KeyEvent.KEYCODE_R -> 'r'
            KeyEvent.KEYCODE_S -> 's'
            KeyEvent.KEYCODE_T -> 't'
            KeyEvent.KEYCODE_U -> 'u'
            KeyEvent.KEYCODE_V -> 'v'
            KeyEvent.KEYCODE_W -> 'w'
            KeyEvent.KEYCODE_X -> 'x'
            KeyEvent.KEYCODE_Y -> 'y'
            KeyEvent.KEYCODE_Z -> 'z'
            else -> return null
        }
        // Ctrl+字母 → 1-26
        return (c.code - 'a'.code + 1).toByte()
    }

    /**
     * 映射功能键 → 终端转义序列。
     */
    private fun mapFunctionKey(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
        KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
        KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
        KeyEvent.KEYCODE_DEL -> "\u007F"
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

    fun commitText(text: String) {
        core.write(text)
    }

    fun deleteSurrounding(before: Int, after: Int) {
        repeat(before.coerceIn(0, 100)) {
            core.write("\u007F".encodeToByteArray())
        }
        repeat(after.coerceIn(0, 100)) {
            core.write("\u001b[3~".encodeToByteArray())
        }
    }
}
