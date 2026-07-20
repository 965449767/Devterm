package com.devterm.terminal.core.backend

import java.util.concurrent.ConcurrentLinkedQueue

class TerminalSession(
    private val backend: Backend,
    private val outputCallback: (ByteArray) -> Unit
) : BackendCallback {

    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    private var running = false

    fun start() {
        running = true
        backend.start(this)

        Thread { writerLoop() }.apply {
            name = "session-writer"
            isDaemon = true
            start()
        }
    }

    fun write(data: ByteArray) {
        writeQueue.add(data)
    }

    fun resize(cols: Int, rows: Int) {
        backend.resize(cols, rows)
    }

    fun stop() {
        running = false
        backend.stop()
    }

    override fun onOutput(data: ByteArray) {
        outputCallback(data)
    }

    override fun onExit(exitCode: Int) {
        running = false
    }

    override fun onTitleChanged(title: String) {}

    private fun writerLoop() {
        while (running) {
            val data = writeQueue.poll()
            if (data != null) {
                backend.write(data)
            } else {
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TERM = "xterm-256color"
    }
}
