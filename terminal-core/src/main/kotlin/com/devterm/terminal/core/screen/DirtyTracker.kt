package com.devterm.terminal.core.screen

import java.util.BitSet

class DirtyTracker(private var rows: Int = 24) {
    private val dirty = BitSet(rows)

    /**
     * 调整脏行追踪容量。
     * 无论变大变小都清空标记，由调用方负责重新 markAll()。
     */
    fun resize(newRows: Int) {
        dirty.clear()
        rows = newRows
    }

    fun mark(row: Int) {
        if (row in 0 until rows) {
            dirty.set(row)
        }
    }

    fun markAll() {
        dirty.set(0, rows)
    }

    fun consume(): List<Int> {
        val list = mutableListOf<Int>()
        var i = dirty.nextSetBit(0)
        while (i >= 0) {
            list.add(i)
            i = dirty.nextSetBit(i + 1)
        }
        dirty.clear()
        return list
    }

    fun isEmpty(): Boolean = dirty.isEmpty()
}
