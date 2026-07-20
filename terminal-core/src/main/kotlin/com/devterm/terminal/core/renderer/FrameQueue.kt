package com.devterm.terminal.core.renderer

import java.util.concurrent.atomic.AtomicInteger

class FrameQueue {
    private val pending = AtomicInteger(0)

    fun notifyDirty() {
        pending.incrementAndGet()
    }

    fun consume(): Boolean {
        val v = pending.getAndSet(0)
        return v > 0
    }
}
