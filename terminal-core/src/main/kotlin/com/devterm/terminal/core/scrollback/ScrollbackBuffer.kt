package com.devterm.terminal.core.scrollback

import com.devterm.terminal.core.screen.ScreenLine

class ScrollbackBuffer(capacity: Int = 50000) {
    private val lines = arrayOfNulls<ScreenLine>(capacity)
    private var head = 0
    private var count = 0
    val capacity: Int get() = lines.size

    fun push(line: ScreenLine) {
        lines[head] = line
        head = (head + 1) % lines.size
        if (count < lines.size) count++
    }

    fun get(index: Int): ScreenLine? {
        if (index < 0 || index >= count) return null
        val pos = (head - count + index).mod(lines.size)
        return lines[pos]
    }

    val size: Int get() = count

    fun clear() {
        lines.fill(null)
        head = 0
        count = 0
    }
}
