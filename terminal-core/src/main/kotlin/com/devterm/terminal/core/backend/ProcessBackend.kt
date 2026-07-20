package com.devterm.terminal.core.backend

import java.io.OutputStream

class ProcessBackend(
    private val command: List<String>,
    private val environment: Map<String, String> = emptyMap(),
    private val workingDir: String? = null
) : Backend {

    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var callback: BackendCallback? = null
    private var running = false
    private val buffer = ByteArray(8192)

    override fun start(callback: BackendCallback) {
        this.callback = callback
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        if (workingDir != null) {
            pb.directory(java.io.File(workingDir))
        }
        environment.forEach { (k, v) -> pb.environment()[k] = v }
        process = pb.start()
        stdin = process!!.outputStream
        running = true

        Thread { readerLoop() }.apply {
            name = "backend-reader"
            isDaemon = true
            start()
        }

        Thread { waiterLoop() }.apply {
            name = "backend-waiter"
            isDaemon = true
            start()
        }
    }

    override fun write(data: ByteArray) {
        try {
            stdin?.write(data)
            stdin?.flush()
        } catch (e: Exception) {
            // process may have exited
        }
    }

    override fun resize(cols: Int, rows: Int) {
        // no-op for ProcessBackend (no PTY)
    }

    override fun stop() {
        running = false
        process?.destroy()
        process = null
        stdin = null
    }

    private fun readerLoop() {
        val inputStream = process?.inputStream ?: return
        try {
            while (running) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    callback?.onOutput(chunk)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun waiterLoop() {
        try {
            val exitCode = process?.waitFor() ?: 0
            running = false
            callback?.onExit(exitCode)
        } catch (_: Exception) {
        }
    }
}
