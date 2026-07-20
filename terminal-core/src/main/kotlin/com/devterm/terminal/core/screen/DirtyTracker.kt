package com.devterm.terminal.core.screen

import java.util.BitSet

class DirtyTracker(private var rows: Int = 24) {
    private val dirty = BitSet(rows)

    fun resize(newRows: Int) {
        if (newRows > rows) {
            dirty.clear()
        }
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
