package com.devterm.terminal

import com.devterm.terminal.core.TerminalCore
import com.devterm.terminal.core.backend.Backend
import com.devterm.terminal.core.backend.BackendCallback
import com.devterm.terminal.core.backend.TerminalSession
import kotlinx.coroutines.flow.StateFlow

class DevTermCore(
    cols: Int = 80,
    rows: Int = 24
) {
    val core = TerminalCore(cols, rows)
    val frame: StateFlow<com.devterm.terminal.core.renderer.RenderFrame> get() = core.frame

    private var session: TerminalSession? = null

    fun attachBackend(backend: Backend) {
        val terminalSession = TerminalSession(
            backend = backend,
            outputCallback = { data -> core.onBackendOutput(data) }
        )
        core.setSession(terminalSession)
        session = terminalSession
    }

    fun start() {
        core.startRenderLoop()
        core.startSession()
    }

    fun stop() {
        core.stopSession()
    }

    fun write(text: String) {
        core.writeInput(text)
    }

    fun write(data: ByteArray) {
        core.writeInput(data)
    }

    fun resize(cols: Int, rows: Int) {
        core.resize(cols, rows)
    }

    fun reset() {
        core.reset()
    }
}

class AppBackend(
    private val command: List<String>,
    private val environment: Map<String, String> = emptyMap(),
    private val workingDir: String? = null
) : Backend {

    private var process: Process? = null
    private var stdin: java.io.OutputStream? = null
    private var callback: BackendCallback? = null
    private val buffer = ByteArray(8192)
    private var running = false

    override fun start(callback: BackendCallback) {
        this.callback = callback
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        if (workingDir != null) pb.directory(java.io.File(workingDir))
        environment.forEach { (k, v) -> pb.environment()[k] = v }
        process = pb.start()
        stdin = process!!.outputStream
        running = true

        Thread {
            val input = process!!.inputStream
            try {
                while (running) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    callback.onOutput(buffer.copyOf(n))
                }
            } catch (_: Exception) {}
        }.apply { name = "AppBackend-Reader"; isDaemon = true; start() }

        Thread {
            try {
                val code = process!!.waitFor()
                running = false
                callback.onExit(code)
            } catch (_: Exception) {}
        }.apply { name = "AppBackend-Waiter"; isDaemon = true; start() }
    }

    override fun write(data: ByteArray) {
        try {
            stdin?.write(data)
            stdin?.flush()
        } catch (_: Exception) {}
    }

    override fun resize(cols: Int, rows: Int) {}

    override fun stop() {
        running = false
        process?.destroy()
        process = null
        stdin = null
    }
}
